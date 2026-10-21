#!/usr/bin/env node
/**
 * Nexo Connect — manually-authorized LIVE HTTP control-plane integration.
 *
 * TWO distinct disposable email accounts, FOUR real Supabase Auth email OTPs:
 * login A, login B, FRESH device-enrollment A, FRESH device-enrollment B.
 * No administrator/service-role key. Private Ed25519/X25519 keys and JWTs
 * remain in process memory only. Never logs secrets or QR invitation payloads.
 *
 * Scope: Auth OTP -> verified user/JWT -> signed enrollment -> signed one-time
 * challenge -> invite -> negative fingerprint -> accept -> replay rejection ->
 * explicit reciprocal key verification -> permission toggle -> empty inbox ->
 * unlink/revocation. There is NO encrypted note delivery in this version;
 * it must NOT be called a full E2EE message test.
 *
 * WARNING: creates two persistent Auth users and device records in the
 * dedicated LAB project. Unlink is attempted in finally, but account cleanup
 * requires a subsequent, separately authorized admin step. Not a staging DB.
 */
import {webcrypto, randomUUID} from "node:crypto";
import {createInterface} from "node:readline/promises";
import {stdin as input,stdout as output} from "node:process";
import {pathToFileURL} from "node:url";
import {buildDeviceCanonicalRequest,encodeBase64Url} from "./proof.mjs";
import {buildEnrollmentCanonical} from "./enrollment-proof.mjs";

if (!globalThis.crypto) globalThis.crypto=webcrypto;
const PROJECT="fwvmoqrsqtpvpbyfclld";
const ORIGIN="https://"+PROJECT+".supabase.co";
const PUBLIC_KEY="sb_publishable_rsDUyUEyuXcjkyKsSxZFUg_RezHhNUk";
const PREFIX="/functions/v1/nexo-connect-gateway/";
const encoder=new TextEncoder();
const UUID=/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/;
const MAIL=/^[a-z0-9._%+-]{1,64}@[a-z0-9-]+(?:\.[a-z0-9-]+)+$/;
const log=(name)=>process.stdout.write("OK  "+name+"\n");
const insist=(ok,description)=>{if(!ok)throw Error(description);};
const sha=async bytes=>new Uint8Array(await crypto.subtle.digest("SHA-256",bytes));
const shaHex=async bytes=>Buffer.from(await sha(bytes)).toString("hex");
const b64=bytes=>encodeBase64Url(bytes);
const sleep=milliseconds=>new Promise(resolve=>setTimeout(resolve,milliseconds));

async function http(method,path,body=null,jwt=null,extraHeaders={}){
  insist(path.startsWith("/")&&!path.startsWith("//"),"Invalid local path");
  const data=body===null?undefined:JSON.stringify(body);
  const response=await fetch(ORIGIN+path,{
    method,redirect:"error",signal:AbortSignal.timeout(20000),
    headers:{
      "apikey":PUBLIC_KEY,"accept":"application/json",
      "cache-control":"no-store","content-type":"application/json",
      ...(jwt?{"authorization":"Bearer "+jwt}:{}),...extraHeaders
    },
    ...(data===undefined?{}:{body:data})
  });
  let value={};
  const bytes=await response.text();
  insist(bytes.length<8192,"Unexpected large HTTP response");
  try {value=JSON.parse(bytes);}catch{value={};}
  return {status:response.status,value};
}
function success(reply,status,action){
  insist(reply.status===status,action+" returned HTTP "+reply.status);
  insist(reply.value&&typeof reply.value==="object",action+" invalid JSON");
  return reply.value;
}
async function sendOtp(email,createUser){
  const reply=await http("POST","/auth/v1/otp",
    {email,create_user:createUser});
  insist(reply.status>=200&&reply.status<300,
    "Email OTP request blocked (HTTP "+reply.status+"). Stop: no automatic retries.");
  return Date.now();
}
async function login(email,otp){
  const session=success(await http("POST","/auth/v1/verify",
    {email,token:otp,type:"email"}),200,"OTP verification");
  insist(typeof session.access_token==="string"&&session.access_token.length>100,
    "Invalid verified session");
  const user=success(await http("GET","/auth/v1/user",null,session.access_token),
    200,"Auth getUser");
  insist(UUID.test(user.id)&&user.email?.toLowerCase()===email,
    "Auth account mismatch");
  return {email,id:user.id,jwt:session.access_token};
}
async function makeKeys(){
  const signing=await crypto.subtle.generateKey(
    {name:"Ed25519"},false,["sign","verify"]);
  const encryption=await crypto.subtle.generateKey(
    {name:"X25519"},false,["deriveBits"]);
  const signingRaw=new Uint8Array(await crypto.subtle.exportKey("raw",signing.publicKey));
  const encryptionRaw=new Uint8Array(await crypto.subtle.exportKey("raw",encryption.publicKey));
  insist(signingRaw.length===32&&encryptionRaw.length===32,"Unexpected public keys");
  return {signing,signingRaw,encryption,encryptionRaw,deviceId:randomUUID(),generation:0};
}
async function status(user){
  const s=success(await http("POST",PREFIX+"status",{},user.jwt),200,"status");
  return s;
}
async function enroll(user,otp){
  const keys=user.keys;
  const data={
    deviceId:keys.deviceId,signingKey:b64(keys.signingRaw),
    encryptionKey:b64(keys.encryptionRaw),otp,replace:false
  };
  const bytes=encoder.encode(JSON.stringify(data)),issuedAt=Date.now();
  const canon=await buildEnrollmentCanonical({
    userId:user.id,issuedAt,body:bytes
  });
  const signature=new Uint8Array(await crypto.subtle.sign(
    "Ed25519",keys.signing.privateKey,canon));
  const response=success(await http(
    "POST",PREFIX+"device/enroll",data,user.jwt,{
      "x-nexo-issued-at":String(issuedAt),
      "x-nexo-enroll-signature":b64(signature)
    }),201,"device enrollment");
  insist(response.deviceId===keys.deviceId&&response.generation===1
    &&response.replacement===false,"Invalid enrollment receipt");
  keys.generation=1;
  const live=await status(user);
  insist(live.registered===true&&live.deviceId===keys.deviceId
    &&live.generation===1,"Server did not persist correct device");
}
async function action(user,path,payload,{expected=200}={}){
  const k=user.keys;
  insist(k?.generation===1,"Test device not enrolled");
  const challenge=success(await http("POST",PREFIX+"challenge",
    {action:path,deviceId:k.deviceId,generation:k.generation},user.jwt),
    200,"challenge "+path);
  insist(UUID.test(challenge.challengeId),"Invalid server nonce");
  const body=encoder.encode(JSON.stringify(payload));
  const issuedAt=Date.now();
  const canonical=await buildDeviceCanonicalRequest({
    userId:user.id,deviceId:k.deviceId,generation:k.generation,
    challengeId:challenge.challengeId,issuedAt,method:"POST",
    path:"/nexo-connect/"+path,body
  });
  const signature=new Uint8Array(await crypto.subtle.sign(
    "Ed25519",k.signing.privateKey,canonical));
  const response=await http("POST",PREFIX+path,payload,user.jwt,{
    "x-nexo-device":k.deviceId,"x-nexo-generation":String(k.generation),
    "x-nexo-challenge":challenge.challengeId,
    "x-nexo-issued-at":String(issuedAt),
    "x-nexo-signature":b64(signature)
  });
  if(expected!==null)success(response,expected,path);
  return response;
}
function denied(reply,label){
  // A rejection is meaningful only when later valid signed calls succeed.
  insist([400,403,404,409,503].includes(reply.status),
    label+" unexpectedly succeeded or server unavailable (HTTP "+reply.status+")");
}
async function secondCode(rl,user,firstSent){
  const wait=Math.max(0,65000-(Date.now()-firstSent));
  if(wait){
    process.stdout.write("Esperando "+Math.ceil(wait/1000)+
      " segundos para respetar el límite OTP...\n");
    await sleep(wait);
  }
  await sendOtp(user.email,false);
  const code=(await rl.question(
    "Nuevo código OTP de REGISTRO para "+user.email+": ")).trim();
  insist(/^[0-9]{6,8}$/.test(code),"Invalid fresh enrollment OTP");
  return code;
}
function getEmails(base,other,session){
  const a=base.toLowerCase().trim();
  if(other){
    const b=other.toLowerCase().trim();
    insist(MAIL.test(a)&&MAIL.test(b)&&a!==b,"Use two distinct controlled emails");
    return [a,b];
  }
  const match=a.match(/^([a-z0-9._-]+)@gmail\.com$/);
  insist(match,"For another provider, pass --two-emails");
  const suffix=randomUUID().slice(0,8);
  return [match[1]+"+nexoconnect-a-"+suffix+"@gmail.com",
          match[1]+"+nexoconnect-b-"+suffix+"@gmail.com"];
}

export async function offlineSelfTest(){
  const account=randomUUID(),deviceId=randomUUID(),challengeId=randomUUID();
  const k=await makeKeys();
  const payload=encoder.encode(JSON.stringify({peerId:randomUUID()}));
  const issuedAt=Date.now();
  const canonical=await buildDeviceCanonicalRequest({
    userId:account,deviceId,generation:1,challengeId,issuedAt,
    method:"POST",path:"/nexo-connect/contact/unlink",body:payload
  });
  const signed=await crypto.subtle.sign("Ed25519",k.signing.privateKey,canonical);
  insist(await crypto.subtle.verify("Ed25519",k.signing.publicKey,
    signed,canonical),"Invalid offline signing");
  const altered=new Uint8Array(canonical);altered[0]^=1;
  insist(!(await crypto.subtle.verify("Ed25519",k.signing.publicKey,signed,altered)),
    "Signature accepted wrong canonical request");
  const enrolled=await buildEnrollmentCanonical({
    userId:account,issuedAt,body:payload});
  const signature=await crypto.subtle.sign("Ed25519",k.signing.privateKey,enrolled);
  insist(await crypto.subtle.verify("Ed25519",k.signing.publicKey,signature,enrolled),
    "Invalid offline enrollment proof");
  insist((await shaHex(k.signingRaw)).length===64,"Wrong SHA-256 format");
}

async function run(){
  await offlineSelfTest();
  log("autoprueba Ed25519 + canonicalización + SHA-256");
  const arguments_=process.argv.slice(2);
  if(arguments_.includes("--selftest")){
    insist(arguments_.length===1,"Unknown options with selftest");
    return;
  }
  insist(arguments_.length===1&&arguments_[0]==="--live",
    "Use --selftest (offline) or --live (real OTP, creates LAB users)");
  insist(Number(process.versions.node.split(".")[0])>=20,"Node 20+ required");
  const rl=createInterface({input,output});
  let a=null,b=null,linked=false,unlinked=false;
  try{
    process.stdout.write(
      "\nSOLO LABORATORIO "+PROJECT+"; jamás terminales físicos.\n"+
      "Se crearán DOS usuarios Auth persistentes y dos registros de dispositivo.\n"+
      "El script desconectará el contacto, pero NO borrará usuarios Auth.\n"+
      "Se necesitan CUATRO OTP, dos por cuenta. No enviar códigos ni QR al chat.\n");
    const mode=(await rl.question(
      "¿Usar una cuenta Gmail con dos alias (+), o dos emails independientes? [G/D]: "
    )).trim().toUpperCase();
    insist(mode==="G"||mode==="D","Cancelled");
    let emails;
    if(mode==="G"){
      const email=await rl.question("Tu correo Gmail principal (NO contraseña): ");
      emails=getEmails(email,null);
    }else{
      const ea=await rl.question("Correo NUEVO de laboratorio A: ");
      const eb=await rl.question("Correo NUEVO de laboratorio B: ");
      emails=getEmails(ea,eb,true);
    }
    process.stdout.write("Cuentas de prueba: "+emails.join(" | ")+"\n");
    const confirm=(await rl.question(
      "Para crear las cuentas de laboratorio escribe PRUEBA: "
    )).trim();
    insist(confirm==="PRUEBA","Cancelled (no network changes)");

    // Request 2 independent OTPs. Never replay an OTP to enroll a device.
    const sentA=await sendOtp(emails[0],true);
    const sentB=await sendOtp(emails[1],true);
    process.stdout.write("Enviados OTP de INICIO. Usa el código de cada alias.\n");
    const codeA=(await rl.question("OTP inicio A: ")).trim();
    insist(/^[0-9]{6,8}$/.test(codeA),"Invalid login OTP A");
    a=await login(emails[0],codeA);log("Auth JWT válido de A");
    const codeB=(await rl.question("OTP inicio B: ")).trim();
    insist(/^[0-9]{6,8}$/.test(codeB),"Invalid login OTP B");
    b=await login(emails[1],codeB);log("Auth JWT válido de B");
    insist(a.id!==b.id,"Auth assigned the same account to both testers");

    const statuses=await Promise.all([status(a),status(b)]);
    insist(statuses.every(s=>s.registered===false),
      "A test email already has an enrolled phone. STOP: never replace.");
    a.keys=await makeKeys();b.keys=await makeKeys();
    log("cuentas distintas, sin dispositivos registrados");

    const enrollA=await secondCode(rl,a,sentA);
    await enroll(a,enrollA);log("enrollment A con OTP NUEVO y Ed25519");
    const enrollB=await secondCode(rl,b,sentB);
    await enroll(b,enrollB);log("enrollment B con OTP NUEVO y Ed25519");

    const secret=new Uint8Array(32);crypto.getRandomValues(secret);
    const tokenSha256=await shaHex(secret);
    secret.fill(0); // no raw QR secret leaves this process
    const invite=(await action(a,"contact/invite",
      {tokenSha256,expiresInSeconds:900},{expected:201})).value;
    insist(UUID.test(invite.inviteId)&&invite.expiresInSeconds===900,
      "Invalid server invitation receipt");
    log("invitación HTTPS firmada, sin exponer secreto");
    const accept={
      creatorId:a.id,creatorGeneration:1,tokenSha256,
      creatorSigningFingerprint:await shaHex(a.keys.signingRaw),
      creatorEncryptionFingerprint:await shaHex(a.keys.encryptionRaw)
    };
    const corrupted={...accept,creatorSigningFingerprint:"0".repeat(64)};
    denied(await action(b,"contact/accept",corrupted,{expected:null}),
      "wrong creator fingerprint");
    log("huella del QR falsificada: denegada");

    const reply=(await action(b,"contact/accept",accept)).value;
    insist(reply.linked===true&&reply.creatorId===a.id&&
      reply.reciprocalKeyVerificationRequired===true,
      "Invalid mutual verification requirement");
    linked=true;log("vinculación legítima B → A con prueba Ed25519");
    denied(await action(b,"contact/accept",accept,{expected:null}),
      "replayed invitation");
    log("invitación repetida: denegada");

    const verify=(await action(a,"key/verify",{
      peerId:b.id,peerGeneration:1,
      peerSigningFingerprint:await shaHex(b.keys.signingRaw),
      peerEncryptionFingerprint:await shaHex(b.keys.encryptionRaw)
    })).value;
    insist(verify.verified===true&&verify.peerId===b.id,
      "Reciprocal key not confirmed");
    log("A verificó explícitamente huellas de B");

    for(const allowed of [false,true]){
      const permission=(await action(b,"contact/permissions",{
        peerId:a.id,allowReceive:allowed
      })).value;
      insist(permission.receivingAllowed===allowed,
        "Unconfirmed receive permission");
    }
    log("bloqueo y restauración de permiso de recepción");

    const items=(await action(b,"delivery/inbox",{limit:20})).value.items;
    insist(Array.isArray(items)&&items.length===0,
      "Lab account has unexpected inbox contents");
    log("bandeja de cuenta B nueva está vacía");

    const unlink=(await action(a,"contact/unlink",{peerId:b.id})).value;
    insist(unlink.unlinked===true&&unlink.peerId===b.id,"Unlink not acknowledged");
    unlinked=true;log("desvinculación firmada correcta");
    const deniedAfter=await action(a,"key/verify",{
      peerId:b.id,peerGeneration:1,
      peerSigningFingerprint:await shaHex(b.keys.signingRaw),
      peerEncryptionFingerprint:await shaHex(b.keys.encryptionRaw)
    },{expected:null});
    denied(deniedAfter,"verification of revoked relation");
    log("la relación revocada no admite nuevas aprobaciones de claves");
    process.stdout.write(
      "\nCONTROL-PLANE HTTPS OK. No se enviaron notas E2EE.\n"+
      "IDs SOLO para limpieza posterior de laboratorio:\n"+
      "LAB_A_ID="+a.id+"\nLAB_B_ID="+b.id+"\n");
  }finally{
    // At most ONE best-effort remote cleanup action, never retry uncertain POST.
    if(linked&&!unlinked&&a?.keys?.generation===1&&b){
      process.stdout.write("Intentando revocar contacto de prueba...\n");
      try{
        const answer=await action(a,"contact/unlink",{peerId:b.id});
        unlinked=answer.value.unlinked===true;
        log("desvinculación de emergencia aceptada");
      }catch{
        process.stdout.write(
          "AVISO: no se pudo confirmar revocación del contacto; revisar el laboratorio.\n");
      }
    }
    if(a?.id||b?.id)
      process.stdout.write("IDs de posibles cuentas residuales: "+
        (a?.id??"?")+" ; "+(b?.id??"?")+"\n");
    rl.close();
  }
}
const launched=Boolean(process.argv[1]&&pathToFileURL(process.argv[1]).href===import.meta.url);
if(launched){
  run().catch(error=>{
    // Never include user inputs, JWT or server response bodies in output.
    process.stderr.write("ERROR: "+String(error?.message??"test failed")+"\n");
    process.exitCode=1;
  });
}
