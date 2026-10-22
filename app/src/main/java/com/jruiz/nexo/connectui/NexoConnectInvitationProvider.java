package com.jruiz.nexo.connectui;
/** Preserve the invitation MIME type when another app reads the attachment. */
public final class NexoConnectInvitationProvider extends androidx.core.content.FileProvider {
    @Override public String getType(android.net.Uri uri){return "application/vnd.nexo.invitation";}
}
