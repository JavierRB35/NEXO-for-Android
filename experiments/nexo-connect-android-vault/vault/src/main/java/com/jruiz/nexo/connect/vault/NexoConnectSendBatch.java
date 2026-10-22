package com.jruiz.nexo.connect.vault;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * LAB-only multi-recipient dispatcher. A single user action approves one
 * independent encrypted delivery per distinct recipient. The caller must
 * seal each copy to that recipient's current pinned HPKE public key.
 *
 * Stop immediately after ANY failure, including an uncertain HTTP response.
 * No automatic retries, no misleading blanket 'sent' confirmation.
 */
public final class NexoConnectSendBatch {
    public static final int MAX_RECIPIENTS = 20;

    private NexoConnectSendBatch(){}

    public interface Sender {
        void sendOnce(UUID recipientId) throws Exception;
    }

    public static final class Result {
        public final int requested, confirmed, attempted;
        public final boolean interrupted;
        private Result(int requested,int confirmed,int attempted,boolean interrupted) {
            this.requested=requested;
            this.confirmed=confirmed;
            this.attempted=attempted;
            this.interrupted=interrupted;
        }
    }

    public static List<UUID> distinctRecipients(List<UUID> requested,UUID ownAccount) {
        if(requested==null || requested.isEmpty() || ownAccount==null)
            throw new IllegalArgumentException("Choose at least one recipient");
        if(requested.size()>MAX_RECIPIENTS)
            throw new IllegalArgumentException("Too many recipients");
        Set<UUID> ids=new HashSet<>();
        ArrayList<UUID> result=new ArrayList<>();
        for(UUID id:requested) {
            if(id==null || id.equals(ownAccount) || !ids.add(id))
                throw new IllegalArgumentException("Invalid or duplicate recipient");
            result.add(id);
        }
        return Collections.unmodifiableList(result);
    }

    /**
     * Any unconfirmed call is considered ambiguous. Stop without trying more
     * recipients or retrying the failed one. Result carries counts only.
     */
    public static Result sendOnce(List<UUID> recipients,UUID ownAccount,
                                  boolean userApproved,Sender sender) {
        if(!userApproved || sender==null)
            throw new SecurityException("Explicit one-shot approval required");
        List<UUID> ids=distinctRecipients(recipients,ownAccount);
        int confirmed=0;
        for(UUID id:ids){
            try {
                sender.sendOnce(id);
                confirmed++;
            }catch(Exception notConfirmed) {
                return new Result(ids.size(),confirmed,confirmed+1,true);
            }
        }
        return new Result(ids.size(),confirmed,confirmed,false);
    }
}
