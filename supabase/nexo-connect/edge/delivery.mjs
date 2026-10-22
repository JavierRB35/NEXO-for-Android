// Strict lab verifier for an inline NXC1 envelope before service-role RPC.
// The ciphertext remains opaque to the Edge server: only the recipient
// decrypts using its locally protected private key.
const UUID=/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/;
const DOMAIN=new TextEncoder().encode("NEXO-CONNECT-CRYPTO-POC-1");
const ENTITY_CODES=Object.freeze({
  appointment:1,note:2,list:3,journal:4,book:5,movie:6,document:7
});
const MAX_CIPHER=512*1024;
const MAX_TTL_MS=30*24*60*60*1000;
function encoded(name,s,min,max){
  if(typeof s!=="string"||!/^[A-Za-z0-9_-]+$/.test(s)||s.length>Math.ceil(max*4/3)+3)
    throw Error("Invalid "+name);
  let raw;
  try{
    const b64=s.replace(/-/g,"+").replace(/_/g,"/");
    raw=Uint8Array.from(atob(b64.padEnd(Math.ceil(b64.length/4)*4,"=")),x=>x.charCodeAt(0));
  }catch(error){throw Error("Invalid base64url");}
  if(raw.length<min||raw.length>max)throw Error("Invalid "+name+" size");
  let canonical="";for(const b of raw)canonical+=String.fromCharCode(b);
  if(btoa(canonical).replace(/\+/g,"-").replace(/\//g,"_").replace(/=/g,"")!==s)
    throw Error("Noncanonical base64url");
  return raw;
}
function uuidAt(b,offset){
  const hex=Array.from(b.subarray(offset,offset+16),x=>x.toString(16).padStart(2,"0")).join("");
  return [hex.slice(0,8),hex.slice(8,12),hex.slice(12,16),hex.slice(16,20),hex.slice(20)].join("-");
}
function canonicalSigned(context,ciphertext){
  // Exactly Java DataOutputStream.writeUTF(ASCII domain), writeInt lengths.
  const output=new Uint8Array(2+DOMAIN.length+4+context.length+4+ciphertext.length);
  const view=new DataView(output.buffer);let i=0;
  view.setUint16(i,DOMAIN.length,false);i+=2;
  output.set(DOMAIN,i);i+=DOMAIN.length;
  view.setUint32(i,context.length,false);i+=4;
  output.set(context,i);i+=context.length;
  view.setUint32(i,ciphertext.length,false);i+=4;
  output.set(ciphertext,i);
  return output;
}
export async function validateSignedEnvelope({
  data,activeDevice,authenticatedUserId,nowMillis
}){
  if(!data||typeof data!=="object"||!UUID.test(authenticatedUserId)||
    !activeDevice||activeDevice.user_id!==authenticatedUserId||
    !Number.isSafeInteger(activeDevice.generation)||activeDevice.generation<1||
    !Number.isSafeInteger(nowMillis))
    throw Error("Invalid server context");
  if(!UUID.test(data.deliveryId)||!UUID.test(data.clientRequestId)||
    !UUID.test(data.recipientId)||data.recipientId===authenticatedUserId||
    !Number.isSafeInteger(data.recipientGeneration)||data.recipientGeneration<1||
    ENTITY_CODES[data.entityType]===undefined)
    throw Error("Invalid routing");
  const key=activeDevice.signing_public_key;
  if(!(key instanceof Uint8Array)||key.length!==32)throw Error("Missing signing key");
  const context=encoded("context",data.context,142,142);
  const ciphertext=encoded("ciphertext",data.ciphertext,1,MAX_CIPHER);
  const signature=encoded("signature",data.signature,64,64);
  const view=new DataView(context.buffer,context.byteOffset,context.byteLength);
  if(view.getUint32(0,false)!==0x4e584331||context[4]!==1||
    uuidAt(context,5)!==data.deliveryId||
    uuidAt(context,21)!==authenticatedUserId||
    uuidAt(context,37)!==data.recipientId||
    context[53]!==ENTITY_CODES[data.entityType]||
    view.getUint32(54,false)!==activeDevice.generation||
    view.getUint32(58,false)!==data.recipientGeneration)
    throw Error("Context routing mismatch");
  const issued=Number(view.getBigUint64(62,false));
  const expiry=Number(view.getBigUint64(70,false));
  if(!Number.isSafeInteger(issued)||!Number.isSafeInteger(expiry)||
    Math.abs(issued-nowMillis)>5*60*1000||
    expiry<=nowMillis||expiry<=issued||expiry-issued>MAX_TTL_MS)
    throw Error("Stale or invalid signed expiry");
  const signerHash=new Uint8Array(await crypto.subtle.digest("SHA-256",key));
  if(!signerHash.every((b,i)=>b===context[78+i]))
    throw Error("Sender fingerprint differs");
  const publicKey=await crypto.subtle.importKey(
    "raw",key,{name:"Ed25519"},false,["verify"]);
  if(!await crypto.subtle.verify("Ed25519",publicKey,signature,
    canonicalSigned(context,ciphertext)))
    throw Error("Invalid E2EE envelope signature");
  // Recipient key/generation/contact/permission checked transactionally by
  // the privileged RPC. Server never substitutes a new recipient key.
  return Object.freeze({context,ciphertext,signature});
}
