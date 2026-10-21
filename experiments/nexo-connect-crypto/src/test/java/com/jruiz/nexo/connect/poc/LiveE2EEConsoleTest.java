package com.jruiz.nexo.connect.poc;

import org.junit.Test;

/**
 * No account, network or OTP access. Tests real Tink X25519/HPKE
 * encryption + Ed25519 signature + canonical NXC1 format in-process.
 */
public final class LiveE2EEConsoleTest {
    @Test public void tinkSignedNoteRoundtripWithoutCamerasOrServer() throws Exception {
        LiveE2EEConsole.main(new String[]{"--selftest"});
    }
}
