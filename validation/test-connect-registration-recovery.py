#!/usr/bin/env python3
"""Regression guards for Connect's recoverable email/phone registration flow.

These structural tests complement Android instrumentation. They do not
replace a real upgrade and onboarding test on an Android device.
"""
from pathlib import Path

root = Path(__file__).resolve().parents[1]
pilot = (root / "experiments/nexo-connect-android-vault/pilot/src/main/java/"
         "com/jruiz/nexo/connect/vault/NexoConnectPilotActivity.java").read_text()
host = (root / "app/src/main/java/com/jruiz/nexo/connectui/"
        "NexoConnectActivity.java").read_text()

def require(text: str, source: str, message: str):
    assert text in source, message

require('button("Volver a verificar mi correo",this::openHostEmailReverification);',
        host, "Host must expose email re-verification")
require('button("Entrar / Continuar alta",this::resumeSavedSession);',
        host, "Host must offer incomplete sign-in resume")
require('if(!resumed){resumed=true;resumeSavedSession();}', host,
        "Existing saved sessions should still auto-resume once")

start = pilot.index('protected final void openHostEmailReverification()')
stop = pilot.index('protected void showSetup()', start)
fallback = pilot[start:stop]
require('retryEmailCodeWithSavedSession=true;', fallback,
        "Fallback must allow a new OTP without deleting the session")
assert 'sessionVault.clear()' not in fallback, "Do not erase refresh token"
assert 'identity.clear' not in fallback, "Do not delete device keys"

start = pilot.index('protected final void resumeSavedSession()')
stop = pilot.index('private void activate()', start)
resume = pilot[start:stop]
require('NexoConnectSessionVault.refreshSavedSession(this,otp)', resume,
        "Restore a verified session before checking device status")
require('State.OWN_DEVICE_CONFIRMED', resume, "Verified phone remains ready")
require('State.NO_REGISTERED_DEVICE', resume, "Incomplete enrollment must resume")
require('State.ANOTHER_DEVICE', resume, "Replacing a phone requires explicit flow")
require('State.INCONCLUSIVE', resume, "Unknown server status must fail closed")
require('stage=SECOND;', resume, "Resume at second OTP stage")
require('openHostAccountSetup();', resume, "Resume must show the appropriate UI")
assert 'sendEmailCode' not in resume, "Never re-request OTP on background resume"
assert '.enroll(' not in resume, "Never automatically enroll/replace a phone"
assert 'sessionVault.clear()' not in resume, "Never erase saved refresh session"

start = pilot.index('private static String userFacingAuthError(')
stop = pilot.index('protected void showError(', start)
messages = pilot[start:stop]
require('status==429', messages, "Rate limits must be distinguished from network errors")
require('Espera unos minutos', messages, "Rate-limit message must advise waiting")
assert 'refreshToken' not in messages and 'accessToken' not in messages, (
    "Do not expose credential values")

print("Connect registration recovery structural regression OK")
