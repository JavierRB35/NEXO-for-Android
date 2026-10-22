package com.jruiz.nexo.connect.vault;

import java.util.UUID;

/**
 * LAB/DEBUG contract for transferring ONE explicitly selected Nexo record
 * to the already-registered, separate pilot app.
 *
 * The Intent contains ONLY a random, one-time opaque ticket, never a note
 * body, QR trust data, JWT, files or private Android Keystore material.
 * Payload is obtained in memory via a signature-protected local Binder.
 */
public final class NexoConnectBridgeProtocol {
    public static final String HOST_PACKAGE="com.jruiz.nexo";
    public static final String PILOT_PACKAGE="com.jruiz.nexo.connect.pilot";
    public static final String HOST_SERVICE=
            "com.jruiz.nexo.connect.host.NexoConnectShareBridgeService";
    public static final String PILOT_ACTIVITY=
            "com.jruiz.nexo.connect.vault.NexoConnectPilotBridgeActivity";
    public static final String SIGNATURE_PERMISSION=
            "com.jruiz.nexo.permission.NEXO_CONNECT_DEBUG_BRIDGE";
    public static final String EXTRA_TICKET=
            "com.jruiz.nexo.connect.debug.ONE_TIME_SHARE_TICKET";
    public static final String BINDER_DESCRIPTOR=
            "com.jruiz.nexo.connect.debug.SHARE_BRIDGE_V1";
    public static final int TRANSACTION_PULL=android.os.IBinder.FIRST_CALL_TRANSACTION;
    public static final int MAX_METADATA_BYTES=128*1024;
    private NexoConnectBridgeProtocol(){}

    public static boolean validTicket(String ticket){
        if(ticket==null||!ticket.matches(
                "[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}"))
            return false;
        return UUID.fromString(ticket).toString().equals(ticket);
    }
}
