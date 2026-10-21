package com.jruiz.nexo.connect.poc;

import com.google.crypto.tink.KeysetHandle;
import com.google.crypto.tink.hybrid.HpkePublicKey;
import com.google.crypto.tink.PublicKeySign;
import com.google.crypto.tink.RegistryConfiguration;
import com.google.crypto.tink.hybrid.HybridConfig;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Isolated, manually approved LIVE control+data-plane test against TWO known
 * disposable Supabase accounts. No emulator, camera, service-role/admin secret,
 * Android real phone, or persistent private key files.
 *
 * Auth login and new-device enrollment EACH require a fresh email OTP per
 * account. Existing old LAB devices are explicitly replaced with fresh keys.
 * The CLI refuses any Auth identity outside its pinned LAB UUID allowlist.
 *
 * Real Ed25519 HTTP proof -> signed invite -> explicit key fingerprints ->
 * actual Google Tink X25519/HPKE/AES256GCM NXC1 sealed notes over HTTPS ->
 * reciprocal read/decrypt/signature check -> signed accept ACK ->
 * tamper denial -> pending delivery cancelled on signed unlink.
 *
 * IMPORTANT: test verifies protocol behavior with user-controlled simulated
 * verification of BOTH known test identities. Does not replace independent
 * physical human fingerprint comparison or Android Keystore/UI checks.
 * Supabase test identities are NOT removed automatically; delete only with
 * specific, separate authorization.
 */
public final class LiveE2EEConsole {
    private static final String LAB="fwvmoqrsqtpvpbyfclld";
    private static final String ORIGIN="https://"+LAB+".supabase.co";
    private static final String PUBLIC_KEY="sb_publishable_rsDUyUEyuXcjkyKsSxZFUg_RezHhNUk";
    private static final String PREFIX="/functions/v1/nexo-connect-gateway/";
    private static final UUID A_ID=UUID.fromString("f8770874-5a2f-43c3-8d81-d57e1539e625");
    private static final UUID B_ID=UUID.fromString("a02e17b0-2c11-4d13-b390-f8af3376d80f");
    private static final String PROOF_DOMAIN="NEXO-CONNECT-PROOF-V1";
    private static final String ENROLL_DOMAIN="NEXO-CONNECT-ENROLL-V1";
    private static final String PATH_ENROLL="/nexo-connect/device/enroll";
    private static final byte[] UTF8_DOMAIN="NEXO-CONNECT-CRYPTO-POC-1".getBytes(StandardCharsets.US_ASCII);
    private static final BufferedReader IN=new BufferedReader(new InputStreamReader(System.in,StandardCharsets.UTF_8));
    private static final HttpClient HTTP=HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();
    private static final SecureRandom RNG=new SecureRandom();
    private static final Base64.Encoder B64=Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder DEC=Base64.getUrlDecoder();
    private static LabUser a,b;
    private static boolean linked,unlinked;

    private LiveE2EEConsole(){}

    private static final class LabUser {
        final UUID expected; final String email;
        UUID id,device;
        String jwt;
        long lastOtpRequested;
        int generation;
        KeysetHandle signing,hpke;
        LabUser(UUID id,String email){expected=id;this.email=email;}
        byte[] signRaw()throws Exception{return CryptoProof.rawSigningPublicKey(signing);}
        byte[] hpkeRaw()throws Exception{
            HpkePublicKey k=(HpkePublicKey)hpke.getPublicKeysetHandle().getAt(0).getKey();
            return k.getPublicKeyBytes().toByteArray();
        }
    }
    private record Reply(int status,JsonObject body){}

    private static void check(boolean ok,String why){
        if(!ok)throw new IllegalStateException(why);
    }
    private static void pass(String what){System.out.println("OK  "+what);}
    private static String prompt(String text)throws Exception{
        System.out.print(text);System.out.flush();
        String line=IN.readLine();
        if(line==null)throw new IllegalStateException("Entrada cancelada");
        return line.trim();
    }
    private static JsonObject object(Object...kvs){
        JsonObject result=new JsonObject();
        for(int i=0;i<kvs.length;i+=2){
            String key=(String)kvs[i];Object value=kvs[i+1];
            if(value instanceof String)result.addProperty(key,(String)value);
            else if(value instanceof Number)result.addProperty(key,(Number)value);
            else if(value instanceof Boolean)result.addProperty(key,(Boolean)value);
            else throw new IllegalArgumentException("Tipo de JSON no permitido");
        }
        return result;
    }
    private static String val(JsonObject o,String k){
        JsonElement e=o.get(k);
        if(e==null||e.isJsonNull()||!e.isJsonPrimitive())
            throw new IllegalStateException("Respuesta incompleta del servidor: "+k);
        return e.getAsString();
    }
    private static boolean booleanVal(JsonObject o,String k){
        JsonElement e=o.get(k);
        check(e!=null&&e.isJsonPrimitive(),"Respuesta sin "+k);
        return e.getAsBoolean();
    }
    private static long intVal(JsonObject o,String k){
        return Long.parseLong(val(o,k));
    }
    private static String b64(byte[] bytes){return B64.encodeToString(bytes);}
    private static byte[] decode(String text){return DEC.decode(text);}
    private static byte[] hash(byte[] bytes)throws Exception{
        return MessageDigest.getInstance("SHA-256").digest(bytes);
    }
    private static String hex(byte[] raw){return java.util.HexFormat.of().formatHex(raw);}
    private static String hashHex(byte[] raw)throws Exception{return hex(hash(raw));}
    private static byte[] utf(String t){return t.getBytes(StandardCharsets.UTF_8);}
    private static String canonical(String...lines){return String.join("\n",lines)+"\n";}
    private static byte[] uuidBytes(UUID id){
        return ByteBuffer.allocate(16).putLong(id.getMostSignificantBits())
            .putLong(id.getLeastSignificantBits()).array();
    }
    private static boolean isUuid(String value){
        try{return UUID.fromString(value).toString().equals(value);}
        catch(Exception ex){return false;}
    }
    private static Reply request(String path,JsonObject data,String jwt,Map<String,String> extra)
            throws Exception {
        check(path.startsWith("/")&&!path.startsWith("//"),"Ruta HTTP incorrecta");
        HttpRequest.Builder builder=HttpRequest.newBuilder(URI.create(ORIGIN+path))
            .timeout(Duration.ofSeconds(25))
            .header("apikey",PUBLIC_KEY)
            .header("accept","application/json")
            .header("content-type","application/json; charset=utf-8")
            .header("cache-control","no-store");
        if(jwt!=null)builder.header("authorization","Bearer "+jwt);
        if(extra!=null)for(var e:extra.entrySet())builder.header(e.getKey(),e.getValue());
        if(data==null)builder.GET();
        else builder.POST(HttpRequest.BodyPublishers.ofString(data.toString(),StandardCharsets.UTF_8));
        HttpResponse<String> r=HTTP.send(builder.build(),
            HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        check(r.body().length()<65536,"Respuesta HTTP excesiva");
        JsonObject parsed=new JsonObject();
        try{
            JsonElement j=JsonParser.parseString(r.body());
            if(j.isJsonObject())parsed=j.getAsJsonObject();
        }catch(Exception ignored){}
        return new Reply(r.statusCode(),parsed);
    }
    private static Reply post(String path,JsonObject data,String jwt)throws Exception{
        return request(path,data,jwt,null);
    }
    private static JsonObject expect(Reply r,int code,String step){
        check(r.status()==code,step+" HTTP "+r.status()+" (se detiene; sin reintento)");
        return r.body();
    }
    private static void reject(Reply r,String step){
        check(r.status()>=400&&r.status()<600,step+" fue aceptado indebidamente");
    }
    private static void requestOtp(LabUser u,boolean mayCreate)throws Exception{
        long wait=65000-(System.currentTimeMillis()-u.lastOtpRequested);
        if(wait>0){
            System.out.println("Esperando "+((wait+999)/1000)+" segundos por el límite OTP de "+u.email);
            Thread.sleep(wait);
        }
        Reply r=post("/auth/v1/otp",object("email",u.email,"create_user",mayCreate),null);
        check(r.status()>=200&&r.status()<300,
            "Solicitud OTP rechazada HTTP "+r.status()+"; sin reintento automático");
        u.lastOtpRequested=System.currentTimeMillis();
    }
    private static String readOtp(LabUser u,String phase)throws Exception{
        String code=prompt("Código NUEVO de "+phase+" dirigido a "+u.email+": ");
        check(code.matches("[0-9]{6,8}"),"Código OTP inválido");
        return code;
    }
    private static void login(LabUser u)throws Exception{
        requestOtp(u,false);
        String otp=readOtp(u,"INICIO");
        JsonObject s=expect(post("/auth/v1/verify",
            object("email",u.email,"token",otp,"type","email"),null),200,"Auth OTP login");
        check(s.has("access_token"),"Auth no entregó token");
        u.jwt=val(s,"access_token");
        JsonObject live=expect(request("/auth/v1/user",null,u.jwt,null),200,"Auth getUser");
        check(u.email.equalsIgnoreCase(val(live,"email")),"Auth confirmó otra dirección");
        u.id=UUID.fromString(val(live,"id"));
        check(u.expected.equals(u.id),
            "ALTO: usuario Auth no es la cuenta de laboratorio esperada");
        pass("JWT y usuario Auth reales de "+(u==a?"A":"B"));
    }
    private static void createKeys(LabUser u)throws Exception{
        u.signing=CryptoProof.newSenderSigningKeys();
        u.hpke=CryptoProof.newRecipientKeys();
        u.device=UUID.randomUUID();
        check(u.signRaw().length==32&&u.hpkeRaw().length==32,
            "Claves públicas de tamaño inesperado");
    }
    private static JsonObject status(LabUser u)throws Exception{
        return expect(post(PREFIX+"status",new JsonObject(),u.jwt),200,"Estado dispositivo");
    }
    private static void enrollReplacement(LabUser u)throws Exception{
        JsonObject old=status(u);
        check(booleanVal(old,"registered"),"Esta cuenta no tiene dispositivo previo: ALTO");
        int oldGen=(int)intVal(old,"generation");
        check(oldGen==1,"Generación inesperada: ALTO, no reemplazar dispositivos");
        createKeys(u);
        requestOtp(u,false);
        String otp=readOtp(u,"REGISTRO de nuevas claves");
        JsonObject payload=object(
            "deviceId",u.device.toString(),
            "signingKey",b64(u.signRaw()),
            "encryptionKey",b64(u.hpkeRaw()),
            "otp",otp,"replace",true);
        long now=System.currentTimeMillis();
        byte[] body=utf(payload.toString());
        byte[] canonical=utf(canonical(ENROLL_DOMAIN,"POST",PATH_ENROLL,
                    u.id.toString(),Long.toString(now),hashHex(body)));
        PublicKeySign sign=u.signing.getPrimitive(
            RegistryConfiguration.get(),PublicKeySign.class);
        Map<String,String> headers=Map.of(
            "x-nexo-issued-at",Long.toString(now),
            "x-nexo-enroll-signature",b64(sign.sign(canonical)));
        JsonObject receipt=expect(request(PREFIX+"device/enroll",payload,u.jwt,headers),
                201,"Reemplazo OTP de dispositivo");
        u.generation=(int)intVal(receipt,"generation");
        check(u.generation==oldGen+1&&u.device.toString().equals(val(receipt,"deviceId"))
           &&booleanVal(receipt,"replacement"),"Recibo de nueva identidad inválido");
        JsonObject registered=status(u);
        check(u.device.toString().equals(val(registered,"deviceId")) &&
              intVal(registered,"generation")==u.generation,"Dispositivo no coincidente");
        pass("registro de claves privadas Tink nuevas para "+(u==a?"A":"B")+
            " (generación "+u.generation+")");
    }
    private static Reply action(LabUser u,String endpoint,JsonObject payload)throws Exception{
        check(u.id!=null&&u.signing!=null&&u.generation>1,"Dispositivo no validado");
        JsonObject ch=expect(post(PREFIX+"challenge",object(
            "action",endpoint,"deviceId",u.device.toString(),
            "generation",u.generation),u.jwt),200,"Desafío de "+endpoint);
        String challenge=val(ch,"challengeId");
        check(isUuid(challenge),"Desafío incorrecto");
        long issued=System.currentTimeMillis();
        byte[] body=utf(payload.toString());
        String context=canonical(PROOF_DOMAIN,"POST","/nexo-connect/"+endpoint,
            u.id.toString(),u.device.toString(),Integer.toString(u.generation),
            challenge,Long.toString(issued),hashHex(body));
        PublicKeySign sign=u.signing.getPrimitive(
            RegistryConfiguration.get(),PublicKeySign.class);
        Map<String,String> headers=Map.of(
            "x-nexo-device",u.device.toString(),
            "x-nexo-generation",Integer.toString(u.generation),
            "x-nexo-challenge",challenge,
            "x-nexo-issued-at",Long.toString(issued),
            "x-nexo-signature",b64(sign.sign(utf(context))));
        return request(PREFIX+endpoint,payload,u.jwt,headers);
    }
    private static JsonObject approved(LabUser u,String endpoint,JsonObject body,int expected)
            throws Exception{
        return expect(action(u,endpoint,body),expected,endpoint);
    }
    private static String tokenHash()throws Exception{
        byte[] secret=new byte[32];RNG.nextBytes(secret);
        try{return hashHex(secret);}
        finally{java.util.Arrays.fill(secret,(byte)0);}
    }
    private static void pair()throws Exception{
        String token=tokenHash();
        JsonObject invite=approved(a,"contact/invite",object(
            "tokenSha256",token,"expiresInSeconds",900),201);
        check(isUuid(val(invite,"inviteId")),"Invitación no identificada");
        pass("invitación HTTPS real con desafío firmado");
        JsonObject acceptance=object(
            "creatorId",a.id.toString(),
            "creatorGeneration",a.generation,
            "creatorSigningFingerprint",hashHex(a.signRaw()),
            "creatorEncryptionFingerprint",hashHex(a.hpkeRaw()),
            "tokenSha256",token);
        JsonObject wrong=acceptance.deepCopy();
        wrong.addProperty("creatorSigningFingerprint","0".repeat(64));
        reject(action(b,"contact/accept",wrong),"Huella falsa");
        JsonObject ok=approved(b,"contact/accept",acceptance,200);
        check(booleanVal(ok,"linked")&&booleanVal(ok,"reciprocalKeyVerificationRequired"),
            "No se exigió verificar reciprocidad");
        linked=true;
        reject(action(b,"contact/accept",acceptance),"Invitación repetida");
        JsonObject checkB=approved(a,"key/verify",object(
            "peerId",b.id.toString(),"peerGeneration",b.generation,
            "peerSigningFingerprint",hashHex(b.signRaw()),
            "peerEncryptionFingerprint",hashHex(b.hpkeRaw())),200);
        check(booleanVal(checkB,"verified"),"No se verificó la clave recíproca");
        pass("vinculación, huellas, antirrepetición y reciprocidad reales");
    }
    private static JsonObject buildNote(String label){
        JsonObject data=object("title","Nexo Connect laboratorio - "+label,
            "body","Mensaje cifrado de prueba "+label+"; solo el destinatario puede leerlo.",
            "category","Otros");
        JsonObject payload=new JsonObject();
        payload.addProperty("format","nexo-connect");
        payload.addProperty("version",1);
        payload.addProperty("entity","note");
        payload.add("data",data);
        return payload;
    }
    private static JsonObject seal(LabUser sender,LabUser recipient,UUID deliveryId,
            UUID requestId,JsonObject content)throws Exception{
        long issued=System.currentTimeMillis();
        byte[] context=new DeliveryContext(deliveryId,sender.id,recipient.id,
            DeliveryContext.EntityType.NOTE,sender.generation,recipient.generation,
            issued,issued+3600000,hash(sender.signRaw()),hash(recipient.hpkeRaw())).encode();
        byte[] plaintext=utf(content.toString());
        check(plaintext.length<500*1024,"Excesivos metadatos");
        CryptoProof.SealedMessage packet=CryptoProof.seal(
            sender.signing,CryptoProof.publicKeys(recipient.hpke),plaintext,context);
        check(packet.signature().length==64,"Firma Tink incorrecta");
        JsonObject http=object(
            "deliveryId",deliveryId.toString(),
            "clientRequestId",requestId.toString(),
            "recipientId",recipient.id.toString(),
            "recipientGeneration",recipient.generation,
            "entityType","note",
            "context",b64(packet.context()),
            "ciphertext",b64(packet.ciphertext()),
            "signature",b64(packet.signature()));
        check(!http.toString().contains(val(content.getAsJsonObject("data"),"body")),
            "Se filtró texto en claro");
        return http;
    }
    private static void verifyAndDecrypt(LabUser recipient,LabUser sender,JsonObject message,
           UUID id,JsonObject expected)throws Exception{
        check(id.toString().equals(val(message,"deliveryId")),"Paquete ajeno");
        check(sender.id.toString().equals(val(message,"senderId")),"Remitente cambiado");
        check(recipient.id.toString().equals(val(message,"recipientId")),"Destinatario cambiado");
        check(intVal(message,"senderGeneration")==sender.generation &&
            intVal(message,"recipientGeneration")==recipient.generation,
            "Generación de claves alterada");
        byte[] ctx=java.util.HexFormat.of().parseHex(val(message,"contextHex"));
        byte[] cipher=java.util.HexFormat.of().parseHex(val(message,"ciphertextHex"));
        byte[] sig=java.util.HexFormat.of().parseHex(val(message,"signatureHex"));
        check(hashHex(cipher).equals(val(message,"ciphertextSha256Hex")),
            "Hash del cifrado no coincide");
        check(ByteBuffer.wrap(ctx).getLong(70)==intVal(message,"expiresAtMs"),
            "Caducidad del servidor incorrecta");
        CryptoProof.SealedMessage packet=new CryptoProof.SealedMessage(ctx,cipher,sig);
        // Sender key comes from this process's independently tracked peer identity,
        // not from network fields. Recipient key stays only in this process RAM.
        byte[] plain=CryptoProof.open(recipient.hpke,CryptoProof.publicKeys(sender.signing),
            packet,ctx);
        check(JsonParser.parseString(new String(plain,StandardCharsets.UTF_8)).equals(expected),
            "Contenido original no coincide tras HPKE");
        // Negative cryptographic tests on the *actual* received message:
        byte[] badSig=sig.clone();badSig[0]^=1;
        try{
            CryptoProof.open(recipient.hpke,CryptoProof.publicKeys(sender.signing),
                new CryptoProof.SealedMessage(ctx,cipher,badSig),ctx);
            throw new IllegalStateException("Se aceptó firma manipulada");
        }catch(java.security.GeneralSecurityException good){}
        byte[] badCipher=cipher.clone();badCipher[badCipher.length-1]^=1;
        try{
            CryptoProof.open(recipient.hpke,CryptoProof.publicKeys(sender.signing),
                new CryptoProof.SealedMessage(ctx,badCipher,sig),ctx);
            throw new IllegalStateException("Se aceptó cifrado manipulado");
        }catch(java.security.GeneralSecurityException good){}
        // This independently keyed recipient cannot decrypt even an authentic packet.
        KeysetHandle outsider=CryptoProof.newRecipientKeys();
        try{
            CryptoProof.open(outsider,CryptoProof.publicKeys(sender.signing),packet,ctx);
            throw new IllegalStateException("Una tercera clave descifró el mensaje");
        }catch(java.security.GeneralSecurityException good){}
        java.util.Arrays.fill(plain,(byte)0);
        pass("Google Tink HPKE + Ed25519: nota descifrada, contenido íntegro y ataques rechazados");
    }
    private static void sendAndAccept(LabUser from,LabUser to,String label)throws Exception{
        UUID deliveryId=UUID.randomUUID(), requestId=UUID.randomUUID();
        JsonObject original=buildNote(label);
        JsonObject packet=seal(from,to,deliveryId,requestId,original);
        JsonObject response=approved(from,"delivery/send",packet,201);
        check(deliveryId.toString().equals(val(response,"deliveryId")) &&
             "pending".equals(val(response,"status")),"Entrega no almacenada");
        JsonObject inbox=approved(to,"delivery/inbox",object("limit",20),200);
        JsonArray items=inbox.getAsJsonArray("items");
        check(items!=null&&items.toString().contains(deliveryId.toString()),
            "Mensaje no aparece en bandeja de destinatario");
        reject(action(from,"delivery/open",object("deliveryId",deliveryId.toString())),
            "Remitente no debe abrir bandeja ajena");
        JsonObject opened=approved(to,"delivery/open",
            object("deliveryId",deliveryId.toString()),200);
        check(!opened.toString().contains(val(original.getAsJsonObject("data"),"body")),
            "Servidor filtró nota en claro");
        verifyAndDecrypt(to,from,opened,deliveryId,original);
        JsonObject receipt=approved(to,"delivery/resolve",object(
            "deliveryId",deliveryId.toString(),"target","accepted"),200);
        check("accepted".equals(val(receipt,"status")),"ACK incorrecto");
        reject(action(to,"delivery/open",object("deliveryId",deliveryId.toString())),
            "Mensaje resuelto sigue descargable");
        pass("HTTPS envío y recepción real "+label+" + acuse firmado tras descifrado");
    }
    private static void rejectForgedDelivery()throws Exception{
        UUID d=UUID.randomUUID();
        JsonObject signed=seal(a,b,d,UUID.randomUUID(),buildNote("MANIPULACIÓN"));
        byte[] bytes=decode(val(signed,"ciphertext"));bytes[bytes.length-1]^=1;
        signed.addProperty("ciphertext",b64(bytes));
        reject(action(a,"delivery/send",signed),
            "El servidor admitió cifrado manipulado con firma antigua");
        JsonObject inbox=approved(b,"delivery/inbox",object("limit",20),200);
        check(!inbox.toString().contains(d.toString()),"Cifrado manipulado llegó a bandeja");
        pass("gateway real rechazó alteración de ciphertext sin insertar entrega");
    }
    private static void revokeWithPending()throws Exception{
        UUID pending=UUID.randomUUID();
        JsonObject payload=seal(a,b,pending,UUID.randomUUID(),buildNote("REVOCACIÓN"));
        approved(a,"delivery/send",payload,201);
        JsonObject unlink=approved(a,"contact/unlink",object("peerId",b.id.toString()),200);
        check(booleanVal(unlink,"unlinked"),"No se revocó el contacto");
        unlinked=true;
        reject(action(b,"delivery/open",object("deliveryId",pending.toString())),
            "Se pudo descargar entrega revocada");
        JsonObject items=approved(b,"delivery/inbox",object("limit",20),200);
        check(!items.toString().contains(pending.toString()),
            "Entrega pendiente siguió visible tras revocación");
        reject(action(a,"key/verify",object(
           "peerId",b.id.toString(),"peerGeneration",b.generation,
           "peerSigningFingerprint",hashHex(b.signRaw()),
           "peerEncryptionFingerprint",hashHex(b.hpkeRaw()))),
           "Se pudo reaprobar contacto revocado");
        pass("revocación real canceló mensaje no entregado y no restauró confianza");
    }
    private static void selfTest()throws Exception{
        LabUser sender=new LabUser(A_ID,"lab-a@example.invalid"),recipient=new LabUser(B_ID,"lab-b@example.invalid");
        sender.id=A_ID;recipient.id=B_ID;sender.generation=2;recipient.generation=2;
        createKeys(sender);createKeys(recipient);
        JsonObject data=buildNote("OFFLINE");
        UUID id=UUID.randomUUID();
        JsonObject packet=seal(sender,recipient,id,UUID.randomUUID(),data);
        byte[] ctx=decode(val(packet,"context"));
        byte[] cipher=decode(val(packet,"ciphertext"));
        byte[] sig=decode(val(packet,"signature"));
        byte[] plain=CryptoProof.open(recipient.hpke,CryptoProof.publicKeys(sender.signing),
            new CryptoProof.SealedMessage(ctx,cipher,sig),ctx);
        check(JsonParser.parseString(new String(plain,StandardCharsets.UTF_8)).equals(data),
            "Tink NXC1 no reproduce contenido");
        java.util.Arrays.fill(plain,(byte)0);
        pass("autoprueba REAL Tink HPKE X25519 + AES256GCM + Ed25519 + NXC1");
    }
    public static void main(String[]args)throws Exception{
        selfTest();
        if(args.length==1&&"--selftest".equals(args[0]))return;
        check(args.length==1&&"--live".equals(args[0]),
            "Solo se acepta --selftest o --live");

        System.out.println("\nNEXO LABORATORIO - E2EE REAL SIN CAMARAS NI EMULADORES");
        System.out.println("Exclusivamente dos usuarios LAB preexistentes: se comprobará su UUID.");
        String emailA=prompt("Correo/alias LAB A: ").toLowerCase();
        String emailB=prompt("Correo/alias LAB B: ").toLowerCase();
        check(emailA.matches("[a-z0-9._%+-]+@[a-z0-9.-]+") &&
              emailB.matches("[a-z0-9._%+-]+@[a-z0-9.-]+") &&
              !emailA.equals(emailB),"Alias de laboratorio inválidos");
        a=new LabUser(A_ID,emailA);b=new LabUser(B_ID,emailB);
        System.out.println("Se REEMPLAZARÁ el dispositivo de cada una con NUEVAS claves");
        System.out.println("y un OTP de registro adicional por cuenta, no se generarán usuarios.");
        System.out.println("Se enviarán notas sintéticas cifradas y se revocará el contacto.");
        String confirmation=prompt("Escribe REEMPLAZAR para autorizar SOLO estas dos cuentas: ");
        check("REEMPLAZAR".equals(confirmation),"Cancelado sin cambios remotos");
        try{
            login(a);
            login(b);
            enrollReplacement(a);
            enrollReplacement(b);
            pair();
            rejectForgedDelivery();
            sendAndAccept(a,b,"A → B");
            sendAndAccept(b,a,"B → A");
            revokeWithPending();
            System.out.println("\nE2EE HTTPS REAL OK: 2 mensajes Tink/HPKE bidireccionales,");
            System.out.println("firmas y contenido íntegros, ACK y revocación probados.");
            System.out.println("No es una certificación de seguridad ni prueba de interfaz Android.");
        }catch(Exception ex){
            System.err.println("FALLO EN PRUEBA: "+ex.getMessage());
            throw ex;
        }finally{
            // A failure must never trigger automatic replay of uncertain signed
            // actions. A best-effort unlink can only use in-memory live signer.
            if(linked&&!unlinked&&a.signing!=null&&a.generation>1){
                try{
                    JsonObject result=approved(a,"contact/unlink",object(
                        "peerId",b.id.toString()),200);
                    if(booleanVal(result,"unlinked")){
                        unlinked=true;pass("desvinculación de emergencia");
                    }
                }catch(Exception ex){
                    System.err.println("AVISO: desvinculación incierta, revisar cuentas de laboratorio");
                }
            }
            System.out.println("Dispositivos y cuentas LAB persistentes hasta limpieza autorizada.");
        }
    }
}
