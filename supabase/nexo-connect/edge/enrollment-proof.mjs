/**
 * Nexo Connect: NEW DEVICE enrollment proof. This protocol is separate from
 * the signed proof of an already enrolled device.
 *
 * A JWT alone is never sufficient: require a freshly consumed email OTP for
 * the same verified Supabase Auth user AND an Ed25519 proof of possession of
 * the NEW device's public signing key. Only the backend can call the RPC.
 *
 * The email OTP is the one-time anti-replay resource. Do not automatically
 * retry after an uncertain RPC result; request a new OTP if needed.
 */
const UUID=/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/;
const EMAIL=/^[^\s@]{1,64}@[^\s@]{1,190}$/;
const CANONICAL_DOMAIN="NEXO-CONNECT-ENROLL-V1";
const ENROLL_PATH="/nexo-connect/device/enroll";
const encoder=new TextEncoder();

export function decodeCanonicalPublicKey(value){
  if(typeof value!=="string" || !/^[A-Za-z0-9_-]{43}$/.test(value))
    throw Error("invalid-public-key-encoding");
  const raw=atob(value.replace(/-/g,"+").replace(/_/g,"/")+"=");
  if(raw.length!==32)throw Error("invalid-public-key-length");
  const bytes=Uint8Array.from(raw,ch=>ch.charCodeAt(0));
  let binary="";
  for(const b of bytes)binary+=String.fromCharCode(b);
  if(btoa(binary).replace(/\+/g,"-").replace(/\//g,"_").replace(/=/g,"")!==value)
    throw Error("noncanonical-key");
  return bytes;
}
const b64Signature=value=>{
  if(typeof value!=="string"||!/^[A-Za-z0-9_-]{86}$/.test(value))
    throw Error("invalid-enroll-signature");
  const raw=atob(value.replace(/-/g,"+").replace(/_/g,"/")+"==");
  if(raw.length!==64)throw Error("invalid-enroll-signature");
  return Uint8Array.from(raw,ch=>ch.charCodeAt(0));
};
const encodeHex=bytes=>[...bytes].map(b=>b.toString(16).padStart(2,"0")).join("");

export async function buildEnrollmentCanonical({userId,issuedAt,body}){
  if(!UUID.test(userId)||!Number.isSafeInteger(issuedAt)||issuedAt<=0||
    !(body instanceof Uint8Array)||body.byteLength===0||body.byteLength>2048)
    throw Error("invalid-enrollment-canonical");
  const digest=new Uint8Array(await crypto.subtle.digest("SHA-256",body));
  return encoder.encode([
    CANONICAL_DOMAIN,"POST",ENROLL_PATH,userId,String(issuedAt),encodeHex(digest),""
  ].join("\n"));
}

/** Verify BEFORE consuming OTP, so invalid public-key signatures do not burn it. */
export async function verifyEnrollmentRequest({userId,data,body,headers,nowMillis}){
  if(!headers||typeof headers.get!=="function"||
      !data||typeof data!=="object"||Array.isArray(data)||
      Object.keys(data).sort().join(",")!==
        "deviceId,encryptionKey,otp,replace,signingKey"||
      !UUID.test(userId)||!UUID.test(data.deviceId)||
      typeof data.replace!=="boolean"||typeof data.otp!=="string"||
      !/^[0-9]{6,8}$/.test(data.otp))
    throw Error("invalid-enrollment-input");
  const sign=decodeCanonicalPublicKey(data.signingKey);
  const encrypt=decodeCanonicalPublicKey(data.encryptionKey);
  const rawTime=headers.get("x-nexo-issued-at");
  if(typeof rawTime!=="string"||!/^[1-9][0-9]{0,15}$/.test(rawTime))
    throw Error("invalid-enrollment-timestamp");
  const issuedAt=Number(rawTime);
  if(!Number.isSafeInteger(nowMillis)||!Number.isSafeInteger(issuedAt)||
      Math.abs(nowMillis-issuedAt)>90000)
    throw Error("expired-enrollment-proof");
  const signed=await buildEnrollmentCanonical({userId,issuedAt,body});
  const signature=b64Signature(headers.get("x-nexo-enroll-signature"));
  const publicKey=await crypto.subtle.importKey(
      "raw",sign,{name:"Ed25519"},false,["verify"]);
  if(!await crypto.subtle.verify("Ed25519",publicKey,signature,signed))
    throw Error("invalid-enrollment-signature");
  return Object.freeze({
    deviceId:data.deviceId,replace:data.replace,signingRaw:sign,
    encryptionRaw:encrypt,otp:data.otp
  });
}

/**
 * Both verifiers must be backend-controlled. Supabase Auth verification must
 * consume the code using verifyOtp(email, token, type="email"), check the
 * resulting user ID + email against the bearer-authenticated account and
 * discard the returned new session; no client can supply user/email fields.
 */
export async function completeEnrollment({
    user,parsed,verifyFreshOtp,enrollDevice
}){
  if(!user||!UUID.test(user.id)||typeof user.email!=="string"||
      !EMAIL.test(user.email)||!user.emailConfirmed||
      typeof verifyFreshOtp!=="function"||typeof enrollDevice!=="function")
    throw Error("unverified-enrollment-identity");
  const otpVerified=await verifyFreshOtp({
    userId:user.id,email:user.email,otp:parsed.otp
  });
  if(otpVerified!==true)throw Error("otp-not-fresh-or-invalid");
  // NEVER pass OTP or a new Auth session to service_role RPC.
  const generation=await enrollDevice({
    userId:user.id,deviceId:parsed.deviceId,replace:parsed.replace,
    signingRaw:parsed.signingRaw,encryptionRaw:parsed.encryptionRaw
  });
  const g=Number(generation);
  if(!Number.isSafeInteger(g)||g<=0)
    throw Error("bad-enrollment-result");
  return {deviceId:parsed.deviceId,generation:g,
    replacement:parsed.replace};
}
