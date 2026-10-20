/**
 * Secure device enrollment server-side core, detached from Deno and Supabase.
 * A bearer JWT ALONE NEVER authorizes phone replacement. A freshly redeemed
 * one-time email code must resolve to THE SAME authenticated user.
 */
const UUID=/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/;
const hex32=x=>typeof x==="string"&&/^[a-f0-9]{64}$/.test(x);
const MAX_BYTES=2048;
const SKEW=90000;
const te=new TextEncoder();
const json=(status,body)=>new Response(JSON.stringify(body),{status,
  headers:{"content-type":"application/json","cache-control":"no-store",
           "x-content-type-options":"nosniff"}});
function route(url){
  const path=new URL(url).pathname;
  return path==="/device/enroll"||
         path==="/nexo-connect-enroll/device/enroll"||
         path==="/functions/v1/nexo-connect-enroll/device/enroll";
}
async function boundedJson(request){
  const reader=request.body?.getReader();
  if(!reader)throw Error("empty-body");
  let total=0;const chunks=[];
  try{for(;;){const {done,value}=await reader.read();if(done)break;
    total+=value.length;if(total>MAX_BYTES)throw RangeError("too-large");
    chunks.push(value);
  }}finally{reader.releaseLock();}
  const bytes=new Uint8Array(total);let i=0;
  for(const x of chunks){bytes.set(x,i);i+=x.length;}
  return JSON.parse(new TextDecoder("utf-8",{fatal:true}).decode(bytes));
}
export function canonicalEnrollment({userId,deviceId,mode,signingKeyHex,encryptionKeyHex,issuedAt}){
 if(!UUID.test(userId)||!UUID.test(deviceId)||!["register","replace"].includes(mode)||
    !hex32(signingKeyHex)||!hex32(encryptionKeyHex)||
    !Number.isSafeInteger(issuedAt)||issuedAt<=0)
   throw Error("invalid-enrollment-context");
 return te.encode(["NEXO-CONNECT-DEVICE-ENROLL-V1",userId,deviceId,mode,
                    signingKeyHex,encryptionKeyHex,String(issuedAt),""].join("\n"));
}
function b64url(x){
 if(typeof x!=="string"||!/^[A-Za-z0-9_-]{86}$/.test(x))throw Error("invalid-signature");
 let padded=x.replace(/-/g,"+").replace(/_/g,"/");
 padded=padded.padEnd(88,"=");
 const bytes=Uint8Array.from(atob(padded),c=>c.charCodeAt(0));
 if(bytes.length!==64)throw Error("invalid-signature");
 return bytes;
}
function parseHex(s){return Uint8Array.from(s.match(/.{2}/g),b=>parseInt(b,16));}
export async function verifyNewDeviceKey(userId,data,now){
 const signed=canonicalEnrollment({...data,userId});
 if(Math.abs(now-data.issuedAt)>SKEW)throw Error("expired-attestation");
 const publicKey=await crypto.subtle.importKey("raw",parseHex(data.signingKeyHex),
   {name:"Ed25519"},false,["verify"]);
 if(!await crypto.subtle.verify("Ed25519",publicKey,b64url(data.proofSignature),signed))
   throw Error("invalid-attestation");
 return true;
}
export function createEnrollmentGateway({authenticate,verifyFreshOtp,enroll,clock=()=>Date.now()}){
 if(![authenticate,verifyFreshOtp,enroll,clock].every(x=>typeof x==="function"))
   throw Error("Missing trusted dependencies");
 return async request=>{
   if(request.method!=="POST")return json(405,{error:"method-not-allowed"});
   if(!route(request.url))return json(404,{error:"not-found"});
   const bearer=request.headers.get("authorization");
   if(!bearer||!bearer.startsWith("Bearer ")||bearer.length<=7||
      /\s/.test(bearer.slice(7)))return json(401,{error:"unauthorized"});
   // Verify session BEFORE consuming body or performing privileged operations.
   let user;
   try{user=await authenticate(bearer.slice(7));}catch{return json(401,{error:"unauthorized"});}
   if(!user||!UUID.test(user.id)||typeof user.email!=="string"||!user.email.includes("@"))
     return json(401,{error:"unauthorized"});
   let data;
   try{
     const len=request.headers.get("content-length");
     if(len!==null&&(!/^\d+$/.test(len)||Number(len)>MAX_BYTES))throw RangeError();
     data=await boundedJson(request);
   }catch(e){return json(e instanceof RangeError?413:400,{error:"invalid-body"});}
   if(!data||typeof data!=="object"||Array.isArray(data)||
      Object.keys(data).sort().join(",")!==
       "deviceId,encryptionKeyHex,issuedAt,mode,otp,proofSignature,signingKeyHex"
      ||!UUID.test(data.deviceId)||!hex32(data.signingKeyHex)||!hex32(data.encryptionKeyHex)
      ||!["register","replace"].includes(data.mode)||
      typeof data.otp!=="string"||!/^\d{6}$/.test(data.otp)||
      !Number.isSafeInteger(data.issuedAt))
      return json(400,{error:"invalid-enrollment"});
   try{await verifyNewDeviceKey(user.id,data,clock());}
   catch{return json(403,{error:"invalid-key-proof"});}
   // VerifyOtp MUST call Supabase Auth.verifyOtp({email:user.email,type:"email",token:data.otp})
   // on a new no-persist Auth client, and verify returned user.id and email.
   let fresh;
   try{fresh=await verifyFreshOtp({email:user.email,otp:data.otp});}
   catch{return json(403,{error:"otp-not-verified"});}
   if(!fresh||fresh.id!==user.id||typeof fresh.email!=="string"||
      fresh.email.toLowerCase()!==user.email.toLowerCase())
       return json(403,{error:"otp-not-verified"});
   // OTP is one-use; the SQL transaction is the only authority on generation.
   try{
     const generation=await enroll({userId:user.id,deviceId:data.deviceId,
       signingKeyHex:data.signingKeyHex,encryptionKeyHex:data.encryptionKeyHex,
       replace:data.mode==="replace"});
     if(!Number.isSafeInteger(Number(generation))||Number(generation)<1)
       throw Error("invalid-generation");
     return json(201,{registered:true,deviceId:data.deviceId,generation:Number(generation),
       contactsNeedReverification:data.mode==="replace"});
   }catch{return json(409,{error:"device-enrollment-rejected"});}
 };
}
