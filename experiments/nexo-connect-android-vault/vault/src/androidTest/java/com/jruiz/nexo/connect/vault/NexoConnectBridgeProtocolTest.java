package com.jruiz.nexo.connect.vault;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Locale;
import java.util.UUID;

import static org.junit.Assert.*;

/**
 * LAB contract tests, no network, identities, SMS, accounts or Android
 * Keystore. The actual Binder UID/permission gates are enforced by Android
 * in the installed DEBUG app and must also be checked end-to-end on Pixel_6.
 */
@RunWith(AndroidJUnit4.class)
public final class NexoConnectBridgeProtocolTest {
    @Test public void acceptsOnlyCanonicalUnpredictableV4Ticket(){
        String generated=UUID.randomUUID().toString();
        assertTrue(NexoConnectBridgeProtocol.validTicket(generated));
        assertFalse(NexoConnectBridgeProtocol.validTicket(null));
        assertFalse(NexoConnectBridgeProtocol.validTicket(""));
        assertFalse(NexoConnectBridgeProtocol.validTicket("ticket"));
        assertFalse(NexoConnectBridgeProtocol.validTicket(
                "00000000-0000-0000-0000-000000000000"));
        assertFalse(NexoConnectBridgeProtocol.validTicket(
                "00000000-0000-4000-0000-000000000000"));
        assertFalse(NexoConnectBridgeProtocol.validTicket(
                generated.toUpperCase(Locale.ROOT)));
        assertFalse(NexoConnectBridgeProtocol.validTicket(
                generated+"?entity=list&body=SECRET"));
        assertFalse(NexoConnectBridgeProtocol.validTicket(
                "{\"ticket\":\""+generated+"\",\"title\":\"SECRET\"}"));
    }

    @Test public void binderContractIsRestrictedAndNotHttpBased(){
        assertEquals("com.jruiz.nexo",NexoConnectBridgeProtocol.HOST_PACKAGE);
        assertEquals("com.jruiz.nexo.connect.pilot",
                NexoConnectBridgeProtocol.PILOT_PACKAGE);
        assertEquals("com.jruiz.nexo.permission.NEXO_CONNECT_DEBUG_BRIDGE",
                NexoConnectBridgeProtocol.SIGNATURE_PERMISSION);
        assertTrue(NexoConnectBridgeProtocol.HOST_SERVICE.contains(
                "NexoConnectShareBridgeService"));
        assertTrue(NexoConnectBridgeProtocol.PILOT_ACTIVITY.contains(
                "NexoConnectPilotBridgeActivity"));
        assertTrue(NexoConnectBridgeProtocol.MAX_METADATA_BYTES<=128*1024);
        assertEquals(android.os.IBinder.FIRST_CALL_TRANSACTION,
                NexoConnectBridgeProtocol.TRANSACTION_PULL);
    }
}
