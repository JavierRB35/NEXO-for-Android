import test from "node:test";
import assert from "node:assert/strict";
import {webcrypto} from "node:crypto";
import {createGateway} from "../gateway.mjs";
import {buildDeviceCanonicalRequest,encodeBase64Url} from "../proof.mjs";

if(!globalThis.crypto)globalThis.crypto=webcrypto;
const UID="11111111-1111-4111-8111-111111111111";
const DEVICE="22222222-2222-4222-8222-222222222222";
const NONCE="33333333-3333-4333-8333-333333333333";
const INVITE="44444444-4444-4444-8444-444444444444";
const TIME=1780000000000;
const URL="https://example.invalid/functions/v1/nexo-connect-gateway/contact/invite";
const ACTION_PATH="/nexo-connect/contact/invite";
const payload={tokenSha256:"a".repeat(64),expiresInSeconds:3600};

async function fixture(){
 const keys=await crypto.subtle.generateKey({name:"Ed25519"},true,["sign","verify"]);
 const raw=new Uint8Array(await crypto.subtle.exportKey("raw",keys.publicKey));
 const calls={auth:0,consume:0,create:0};
 let used=false;
 const handle=createGateway({
  authenticate:async token=>{calls.auth++;return token==="verified"?{id:UID}:null},
  getDevice:async user=>{
    assert.equal(user,UID);
    return {user_id:UID,device_id:DEVICE,generation:2,signing_public_key:raw};
  },
  issueChallenge:async()=>NONCE,
  setReceivePermission:async()=>{throw Error("Unexpected contact permission")},
  unlinkContact:async()=>{throw Error("Unexpected unlink")},
  verifyPeer:async()=>{throw Error("Key verify not expected in fixture")},
  acceptInvite:async()=>{throw Error("Acceptance unsupported in fixture")},
  consumeChallenge:async details=>{
    calls.consume++;
    assert.deepEqual(details,{
      userId:UID,deviceId:DEVICE,generation:2,
      challengeId:NONCE,action:"contact/invite"
    });
    if(used)return false;
    used=true;
    return true;
  },
  createInvite:async arg=>{
    calls.create++;
    assert.deepEqual(arg,{
      userId:UID,tokenSha256:payload.tokenSha256,expiresInSeconds:3600
    });
    return INVITE;
  },
  clock:()=>TIME
 });
 async function signed(body=payload,overrides={}){
   const text=JSON.stringify(body);
   const bytes=new TextEncoder().encode(text);
   const headers=new Headers({
     "authorization":"Bearer verified",
     "content-type":"application/json",
     "x-nexo-device":DEVICE,
     "x-nexo-generation":"2",
     "x-nexo-challenge":NONCE,
     "x-nexo-issued-at":String(TIME)
   });
   const canonical=await buildDeviceCanonicalRequest({
     userId:UID,deviceId:DEVICE,generation:2,challengeId:NONCE,
     issuedAt:TIME,method:"POST",path:ACTION_PATH,body:bytes
   });
   const signature=new Uint8Array(await crypto.subtle.sign("Ed25519",keys.privateKey,canonical));
   headers.set("x-nexo-signature",encodeBase64Url(signature));
   return new Request(URL,{method:"POST",headers,body:overrides.rawBody??text});
 }
 return {handle,signed,calls};
}

test("valid OTP session, signature, unused nonce yields one invite",async()=>{
 const f=await fixture();
 const req=await f.signed();
 assert.equal(req.headers.get("authorization"),"Bearer verified","Request lost Authorization header");
 const res=await f.handle(req);
 assert.equal(f.calls.auth,1,"Auth validator was never invoked");
 assert.equal(res.status,201);
 assert.deepEqual(await res.json(),{inviteId:INVITE,expiresInSeconds:3600});
 assert.deepEqual(f.calls,{auth:1,consume:1,create:1});
 const retry=await f.handle(await f.signed());
 assert.equal(retry.status,403);
 assert.equal((await retry.json()).error,"invalid-device-proof");
 assert.deepEqual(f.calls,{auth:2,consume:2,create:1});
});
test("signature binds exact HTTP JSON bytes; tampering denied before nonce",async()=>{
 const f=await fixture();
 const req=await f.signed(payload,{rawBody:'{"tokenSha256":"'+ "b".repeat(64) +'","expiresInSeconds":3600}'});
 const res=await f.handle(req);
 assert.equal(res.status,403);
 assert.equal(f.calls.consume,0);
 assert.equal(f.calls.create,0);
});
test("malformed token hash rejected before nonce consumption",async()=>{
 const f=await fixture();
 const r=await f.handle(await f.signed({...payload,tokenSha256:"xx"}));
 assert.equal(r.status,400);
 assert.equal(f.calls.consume,0);
});
test("missing credentials rejected before privileged lookup",async()=>{
 const f=await fixture();
 const request=await f.signed();
 request.headers.delete("authorization");
 const res=await f.handle(request);
 assert.equal(res.status,401);
 assert.equal(f.calls.consume,0);
 assert.equal(f.calls.create,0);
});
test("wrong phone generation and unsigned request rejected",async()=>{
 const f=await fixture();
 const wrong=await f.signed();
 wrong.headers.set("x-nexo-generation","1");
 assert.equal((await f.handle(wrong)).status,403);
 const noSign=await f.signed();
 noSign.headers.delete("x-nexo-signature");
 assert.equal((await f.handle(noSign)).status,403);
 assert.equal(f.calls.consume,0);
});
test("signed action is route-bound and prevents replay on another path",async()=>{
 const f=await fixture();
 const r=await f.signed();
 const wrong=new Request("https://example.invalid/functions/v1/nexo-connect-gateway/challenge",{
  method:"POST",headers:r.headers,body:JSON.stringify(payload)
 });
 assert.equal((await f.handle(wrong)).status,400);
 assert.equal(f.calls.consume,0);
});

test("route works with function-relative path without weakening signature binding",async()=>{
 const f=await fixture();
 const signed=await f.signed();
 const moved=new Request("https://example.invalid/nexo-connect-gateway/contact/invite",{
   method:"POST",headers:signed.headers,body:JSON.stringify(payload)
 });
 const res=await f.handle(moved);
 assert.equal(res.status,201);
 assert.equal(f.calls.consume,1);
 assert.equal(f.calls.create,1);
});
