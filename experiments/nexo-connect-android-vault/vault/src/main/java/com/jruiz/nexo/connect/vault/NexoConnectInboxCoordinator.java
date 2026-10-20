package com.jruiz.nexo.connect.vault;

import android.database.sqlite.SQLiteDatabase;

import com.google.crypto.tink.KeysetHandle;
import com.jruiz.nexo.connect.NexoConnectAtomicImporter;

import org.json.JSONObject;

import java.util.List;
import java.util.UUID;

/**
 * Experimental orchestrator: explicit approval -> signed HTTPS open ->
 * pinned-key signature verification -> HPKE decryption -> atomic local copy
 * -> signed server acknowledgement -> durable "acked" receipt.
 *
 * NEVER schedules repeat downloads, network retries or automatic imports.
 * No user interface/automatic consent is implemented. This class does not run
 * in the released Nexo app.
 */
public final class NexoConnectInboxCoordinator {
    private final Gateway gateway;
    private final VerifiedImporter importer;

    /** Uses the actual signed HTTPS transport and Tink verifier. */
    public NexoConnectInboxCoordinator(NexoConnectSignedGatewayClient client) {
        if(client==null)throw new IllegalArgumentException("Gateway is missing");
        gateway=new Gateway(){
            @Override public JSONObject open(UUID id)throws Exception{return client.open(id);}
            @Override public String accept(UUID id)throws Exception {
                return client.resolve(id,"accepted");
            }
        };
        importer=NexoConnectInboxProcessor::receiveAndImport;
    }

    /** Package-private test seam: never expose bypass verification in app APIs. */
    NexoConnectInboxCoordinator(Gateway gateway,VerifiedImporter importer) {
        if(gateway==null||importer==null)throw new IllegalArgumentException("Dependencies required");
        this.gateway=gateway;
        this.importer=importer;
    }

    /**
     * userApproved represents explicit UI confirmation of the particular
     * delivery (never derive this from the server's pending status).
     * A local commit always precedes the acknowledgement POST. Failure after
     * commit returns ackPending=true and the local copy remains safely stored.
     *
     * SECURITY: sender UUID/generation and signing PUBLIC KEY must come from
     * the QR-confirmed on-device trust store, NEVER from server JSON.
     */
    public ImportOutcome importApproved(SQLiteDatabase db,UUID deliveryId,
            boolean userApproved,KeysetHandle recipientPrivate,
            KeysetHandle pinnedSenderPublic,UUID pinnedSenderId,
            int pinnedSenderGeneration,UUID currentAccount,
            int currentGeneration,long nowMillis)throws Exception {
        if(!userApproved)throw new SecurityException("Explicit import consent required");
        if(db==null||!db.isOpen()||deliveryId==null)
            throw new IllegalArgumentException("Invalid destination or delivery");
        JSONObject encrypted=gateway.open(deliveryId);
        if(encrypted==null||!deliveryId.toString().equals(
                encrypted.optString("deliveryId","")))
            throw new SecurityException("Opened delivery ID mismatch");
        NexoConnectAtomicImporter.Result imported=importer.importVerified(
                db,encrypted,recipientPrivate,pinnedSenderPublic,
                pinnedSenderId,pinnedSenderGeneration,currentAccount,
                currentGeneration,nowMillis);
        // This line runs only AFTER the SQLite commit has returned successfully.
        // If the server request fails, keep the durable receipt unacknowledged.
        try {
            String status=gateway.accept(deliveryId);
            if("accepted".equals(status)){
                boolean found=NexoConnectAtomicImporter.markServerAccepted(
                        db,deliveryId.toString());
                if(!found)throw new IllegalStateException("Imported receipt lost");
                return new ImportOutcome(imported.localId,imported.alreadyImported,
                        true,"accepted");
            }
            return new ImportOutcome(imported.localId,imported.alreadyImported,
                    false,status);
        } catch(Exception networkOrResponseFailure) {
            return new ImportOutcome(imported.localId,imported.alreadyImported,
                    false,"ack-pending");
        }
    }

    /**
     * Manually retry at most limit *existing* committed receipts, with one
     * freshly signed challenge per POST. NEVER repeat HPKE or native INSERT.
     * Stops on network failure rather than spinning. Terminal non-accepted
     * server states are counted as conflicts; leave the local receipt pending
     * for explicit reconciliation.
     */
    public RetryOutcome retryAcknowledgements(SQLiteDatabase db,int limit)
            throws Exception {
        if(db==null||!db.isOpen())throw new IllegalArgumentException("Database missing");
        List<String> outstanding=NexoConnectAtomicImporter.pendingAcknowledgements(db,limit);
        int accepted=0,conflicts=0;
        for(String value:outstanding){
            final String result;
            try{
                result=gateway.accept(UUID.fromString(value));
            }catch(Exception offline){
                return new RetryOutcome(accepted,conflicts,true);
            }
            if("accepted".equals(result)){
                if(!NexoConnectAtomicImporter.markServerAccepted(db,value))
                    throw new IllegalStateException("Receipt vanished during acknowledgement");
                accepted++;
            } else {
                conflicts++;
            }
        }
        return new RetryOutcome(accepted,conflicts,false);
    }

    public static final class ImportOutcome {
        public final long localId;
        public final boolean alreadyImported;
        public final boolean acknowledged;
        public final String serverStatus;
        private ImportOutcome(long localId,boolean alreadyImported,
                boolean acknowledged,String serverStatus) {
            this.localId=localId;
            this.alreadyImported=alreadyImported;
            this.acknowledged=acknowledged;
            this.serverStatus=serverStatus;
        }
    }
    public static final class RetryOutcome {
        public final int acceptedCount;
        public final int conflictCount;
        public final boolean stoppedOffline;
        private RetryOutcome(int acceptedCount,int conflictCount,boolean stoppedOffline){
            this.acceptedCount=acceptedCount;
            this.conflictCount=conflictCount;
            this.stoppedOffline=stoppedOffline;
        }
    }

    interface Gateway {
        JSONObject open(UUID deliveryId)throws Exception;
        String accept(UUID deliveryId)throws Exception;
    }
    @FunctionalInterface interface VerifiedImporter {
        NexoConnectAtomicImporter.Result importVerified(SQLiteDatabase db,
                JSONObject envelope,KeysetHandle receiver,KeysetHandle senderPublic,
                UUID senderId,int senderGeneration,UUID localAccount,
                int generation,long nowMillis)throws Exception;
    }
}
