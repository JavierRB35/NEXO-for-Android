// Dependence-injected HTTP handler: pure JS usable in Node and Deno.
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
const equal=(a,b)=>a===b;
function bad(msg){throw new TypeError(msg);}
export function createGateway({authenticate,getDevice,issueChallenge}) {
  if (![authenticate,getDevice,issueChallenge].every(x=>typeof x==="function"))
    throw new TypeError("Missing trusted backend dependencies");
  return async (request)=>{
    try {
      if(request.method!=="POST")return reply(405,{error:"method-not-allowed"});
      const path=new URL(request.url).pathname;
      if(path!=="/status" && path!=="/challenge")
        return reply(404,{error:"unknown-action"});
      const bearer=request.headers.get("authorization");
      if(!bearer||!/^Bearer [^\s]+$/.test(bearer))
        return reply(401,{error:"unauthorized"});
      // authenticate MUST call Supabase Auth getUser(token), not decode JWT.
      const user=await authenticate(bearer.slice(7));
      if(!user || !UUID.test(user.id))return reply(401,{error:"unauthorized"});
      const claimedLength=request.headers.get("content-length");
      if(claimedLength!==null && (!/^\d+$/.test(claimedLength) ||
          Number(claimedLength)>MAX_BODY))
        return reply(413,{error:"payload-too-large"});
      const bytes=new Uint8Array(await request.arrayBuffer());
      if(bytes.length>MAX_BODY)return reply(413,{error:"payload-too-large"});
      let body;
      try {body=JSON.parse(new TextDecoder("utf-8",{fatal:true}).decode(bytes));}
      catch{return reply(400,{error:"invalid-json"});}
      if(!body||typeof body!=="object"||Array.isArray(body))
        return reply(400,{error:"invalid-json"});
      const device=await getDevice(user.id);
      if(path==="/status"){
        if(Object.keys(body).length) return reply(400,{error:"unexpected-fields"});
        // No public keys or PII are emitted by this endpoint.
        return reply(200,{registered:Boolean(device),
          deviceId:device?.device_id??null,generation:device?.generation??null});
      }
      if(Object.keys(body).sort().join(",")!=="action,deviceId,generation" ||
        !ACTIONS.has(body.action) || typeof body.deviceId!=="string" ||
        !UUID.test(body.deviceId) ||
        !Number.isSafeInteger(body.generation) || body.generation<=0)
        return reply(400,{error:"invalid-request"});
      if(!device || !equal(device.device_id,body.deviceId) ||
        !equal(device.generation,body.generation))
        return reply(403,{error:"device-revoked"});
      // DB enforces max five outstanding and TTL; no public access to RPC.
      const nonce=await issueChallenge({
        userId:user.id,deviceId:device.device_id,
        generation:device.generation,action:body.action
      });
      if(typeof nonce!=="string"||!UUID.test(nonce))
        throw Error("Unexpected challenge ID");
      return reply(200,{challengeId:nonce,expiresInSeconds:120});
    }catch(error){
      if(error instanceof TypeError)return reply(400,{error:"invalid-request"});
      // Do not disclose DB, Auth or private identifiers to clients.
      return reply(503,{error:"temporarily-unavailable"});
    }
  };
}
