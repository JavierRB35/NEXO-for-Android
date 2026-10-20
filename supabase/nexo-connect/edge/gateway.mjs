// Supabase Auth protected gateway; does not expose administrative credentials.
// Signed invite requests use bytes EXACTLY as transmitted, preventing altered JSON.
import {verifyDeviceProof} from "./proof.mjs";
const UUID=/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/;
const ACTIONS=new Set([
  "contact/invite","contact/accept","contact/permissions",
  "contact/unlink","key/verify","delivery/send",
  "delivery/resolve","delivery/request-resend"
]);
const MAX_BODY=2048;
const reply=(status,data)=>new Response(JSON.stringify(data),{
  status,headers:{"content-type":"application/json; charset=utf-8","cache-control":"no-store",
    "x-content-type-options":"nosniff"}
});
const isHex64=x=>typeof x==="string"&&/^[a-f0-9]{64}$/.test(x);
async function readLimitedJson(request) {
  if (!request.body) throw new SyntaxError("empty-body");
  const reader=request.body.getReader(),chunks=[];let total=0;
  try {
    for (;;) {
      const {done,value}=await reader.read();
      if(done)break;
      total+=value.byteLength;
      if(total>MAX_BODY)throw new RangeError("payload-too-large");
      chunks.push(value);
    }
  }finally{reader.releaseLock();}
  const bytes=new Uint8Array(total);
  let offset=0;
  for(const part of chunks){bytes.set(part,offset);offset+=part.byteLength;}
  const data=JSON.parse(new TextDecoder("utf-8",{fatal:true}).decode(bytes));
  return {data,bytes};
}
function normalizePath(pathname){
  for(const prefix of ["/functions/v1/nexo-connect-gateway","/nexo-connect-gateway"]){
    if(pathname.startsWith(prefix+"/")) return pathname.substring(prefix.length);
  }
  return pathname;
}
function validDevice(device,userId) {
 return Boolean(device&&device.user_id===userId&&UUID.test(device.device_id)
    &&Number.isSafeInteger(device.generation)&&device.generation>0);
}
export function createGateway({
  authenticate,getDevice,issueChallenge,consumeChallenge,createInvite,acceptInvite,verifyPeer,clock=()=>Date.now()
}) {
  if (![authenticate,getDevice,issueChallenge,consumeChallenge,createInvite,acceptInvite,clock]
      .every(x=>typeof x==="function"))
    throw new TypeError("Missing trusted backend dependencies");
  return async request=>{
    try{
      if(request.method!=="POST")return reply(405,{error:"method-not-allowed"});
      const path=normalizePath(new URL(request.url).pathname);
      if(!["/status","/challenge","/contact/invite","/contact/accept","/key/verify"].includes(path))
        return reply(404,{error:"unknown-action"});
      const bearer=request.headers.get("authorization");
      if(!bearer||!bearer.startsWith("Bearer ")||
          bearer.slice(7).length===0||/\s/.test(bearer.slice(7)))
        return reply(401,{error:"unauthorized"});
      // Do not rely on decoded JWT claims: actual Supabase getUser verification.
      const user=await authenticate(bearer.slice(7));
      if(!user||!UUID.test(user.id))return reply(401,{error:"unauthorized"});
      const claimedLength=request.headers.get("content-length");
      if(claimedLength!==null&&(!/^\d+$/.test(claimedLength)||Number(claimedLength)>MAX_BODY))
        return reply(413,{error:"payload-too-large"});
      let data,bytes;
      try {({data,bytes}=await readLimitedJson(request));}
      catch(error){
        if(error instanceof RangeError)return reply(413,{error:"payload-too-large"});
        return reply(400,{error:"invalid-json"});
      }
      if(!data||typeof data!=="object"||Array.isArray(data))
        return reply(400,{error:"invalid-json"});
      const device=await getDevice(user.id);
      if(path==="/status"){
        if(Object.keys(data).length)return reply(400,{error:"unexpected-fields"});
        return reply(200,{registered:Boolean(device),
          deviceId:device?.device_id??null,generation:device?.generation??null});
      }
      if(path==="/challenge"){
        if(Object.keys(data).sort().join(",")!=="action,deviceId,generation"
          ||!ACTIONS.has(data.action)||!UUID.test(data.deviceId)
          ||!Number.isSafeInteger(data.generation)||data.generation<=0)
          return reply(400,{error:"invalid-request"});
        if(!validDevice(device,user.id)||device.device_id!==data.deviceId
          ||device.generation!==data.generation)
          return reply(403,{error:"device-revoked"});
        const nonce=await issueChallenge({
          userId:user.id,deviceId:device.device_id,
          generation:device.generation,action:data.action
        });
        if(typeof nonce!=="string"||!UUID.test(nonce))throw Error("Invalid challenge");
        return reply(200,{challengeId:nonce,expiresInSeconds:120});
      }
      // Both contact operations require a signed, one-use phone proof.
      // QR verification is additionally enforced by a locked DB transaction.
      const creating=path==="/contact/invite";
      const verifying=path==="/key/verify";
      if(creating){
        if(Object.keys(data).sort().join(",")!=="expiresInSeconds,tokenSha256"
          ||!isHex64(data.tokenSha256)
          ||!Number.isSafeInteger(data.expiresInSeconds)
          ||data.expiresInSeconds<300||data.expiresInSeconds>86400)
          return reply(400,{error:"invalid-request"});
      }else if(verifying){
        if(Object.keys(data).sort().join(",")!==
            "peerEncryptionFingerprint,peerGeneration,peerId,peerSigningFingerprint"
          ||!UUID.test(data.peerId)||data.peerId===user.id
          ||!Number.isSafeInteger(data.peerGeneration)||data.peerGeneration<=0
          ||!isHex64(data.peerSigningFingerprint)
          ||!isHex64(data.peerEncryptionFingerprint))
          return reply(400,{error:"invalid-request"});
      }else{
        if(Object.keys(data).sort().join(",")!==
            "creatorEncryptionFingerprint,creatorGeneration,creatorId,creatorSigningFingerprint,tokenSha256"
          ||!UUID.test(data.creatorId)||data.creatorId===user.id
          ||!Number.isSafeInteger(data.creatorGeneration)||data.creatorGeneration<=0
          ||!isHex64(data.creatorSigningFingerprint)
          ||!isHex64(data.creatorEncryptionFingerprint)
          ||!isHex64(data.tokenSha256))
          return reply(400,{error:"invalid-request"});
      }
      if(!validDevice(device,user.id))
        return reply(403,{error:"device-revoked"});
      const key=device.signing_public_key;
      if(!(key instanceof Uint8Array)||key.length!==32)
        return reply(503,{error:"temporarily-unavailable"});
      try {
        await verifyDeviceProof({
          headers:request.headers,userId:user.id,method:"POST",
          path:"/nexo-connect"+path,body:bytes,
          activeDevice:device,nowMillis:clock(),
          consumeChallenge:async({userId,deviceId,generation,challengeId,action})=>
            consumeChallenge({userId,deviceId,generation,challengeId,action})
        });
      }catch(error){
        return reply(403,{error:"invalid-device-proof"});
      }
      if(creating){
        const inviteId=await createInvite({
          userId:user.id,tokenSha256:data.tokenSha256,
          expiresInSeconds:data.expiresInSeconds
        });
        if(typeof inviteId!=="string"||!UUID.test(inviteId))
          throw Error("Invalid invite creation response");
        // Server stores ONLY token hash, not the clear QR token.
        return reply(201,{inviteId,expiresInSeconds:data.expiresInSeconds});
      }
      if(verifying){
        await verifyPeer({
          ownerId:user.id,ownerDeviceId:device.device_id,
          ownerGeneration:device.generation,
          peerId:data.peerId,peerGeneration:data.peerGeneration,
          peerSigningFingerprint:data.peerSigningFingerprint,
          peerEncryptionFingerprint:data.peerEncryptionFingerprint
        });
        return reply(200,{verified:true,peerId:data.peerId,
          generation:data.peerGeneration});
      }
      const requestId=await acceptInvite({
        recipientId:user.id,recipientDeviceId:device.device_id,
        recipientGeneration:device.generation,
        creatorId:data.creatorId,creatorGeneration:data.creatorGeneration,
        creatorSigningFingerprint:data.creatorSigningFingerprint,
        creatorEncryptionFingerprint:data.creatorEncryptionFingerprint,
        tokenSha256:data.tokenSha256
      });
      if(typeof requestId!=="string"||!UUID.test(requestId))
        throw Error("Invalid acceptance result");
      return reply(200,{linked:true,requestId,creatorId:data.creatorId,
        reciprocalKeyVerificationRequired:true});
    }catch(error) {
      return reply(503,{error:"temporarily-unavailable"});
    }
  };
}
