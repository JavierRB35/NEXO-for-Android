import test from "node:test";
import assert from "node:assert/strict";
import {webcrypto} from "node:crypto";
import {createGateway} from "../gateway.mjs";
import {buildDeviceCanonicalRequest,encodeBase64Url} from "../proof.mjs";
if(!globalThis.crypto)globalThis.crypto=webcrypto;
const OWNER="11111111-1111-4111-8111-111111111111";
const PHONE="12121212-1212-4212-8212-121212121212";
const ID="f5555555-5555-4555-8555-555555555555";
const CHALLENGE="13131313-1313-4313-8313-131313131313";
const NOW=1780000000000;
async function fixture(){
 const keys=await crypto.subtle.generateKey({name:"Ed25519"},true,["sign","verify"]);
 const pub=new Uint8Array(await crypto.subtle.exportKey("raw",keys.publicKey));
 const calls=[];const used=new Set();
 const gateway=createGateway({
   authenticate:async token=>token==="verified"?{id:OWNER}:null,
   getDevice:async()=>({user_id:OWNER,device_id:PHONE,generation:1,signing_public_key:pub}),
   issueChallenge:async()=>CHALLENGE,
   consumeChallenge:async({action})=>{
     assert.ok(["delivery/inbox","delivery/open"].includes(action));
     if(used.has(action))return false;used.add(action);return true;
   },
   createInvite:async()=>{throw Error("Unexpected invite")},
   acceptInvite:async()=>{throw Error("Unexpected accept")},
   verifyPeer:async()=>{throw Error("Unexpected verify")},
   setReceivePermission:async()=>{throw Error("Unexpected permission")},
   unlinkContact:async()=>{throw Error("Unexpected unlink")},
   listInbox:async args=>{
     calls.push({type:"inbox",...args});
     return [{deliveryId:ID,senderId:"22222222-2222-4222-8222-222222222222",
       recipientGeneration:1,entityType:"note",expiresAtMs:NOW+3600000}];
   },
   openDelivery:async args=>{
     calls.push({type:"open",...args});
     return {deliveryId:args.deliveryId,contextHex:"00".repeat(142),
       ciphertextHex:"ab".repeat(32),signatureHex:"cd".repeat(64)};
   },
   clock:()=>NOW
 });
 async function signed(path,body,mutatedRaw=JSON.stringify(body)){
   const raw=JSON.stringify(body);
   const headers=new Headers({authorization:"Bearer verified",
      "x-nexo-device":PHONE,"x-nexo-generation":"1",
      "x-nexo-challenge":CHALLENGE,"x-nexo-issued-at":String(NOW),
      "content-type":"application/json"});
   const canonical=await buildDeviceCanonicalRequest({
      userId:OWNER,deviceId:PHONE,generation:1,challengeId:CHALLENGE,
      issuedAt:NOW,method:"POST",path:"/nexo-connect/"+path,
      body:new TextEncoder().encode(raw)
   });
   headers.set("x-nexo-signature",encodeBase64Url(
      new Uint8Array(await crypto.subtle.sign("Ed25519",keys.privateKey,canonical))));
   return new Request("https://test.invalid/functions/v1/nexo-connect-gateway/"+path,{
     method:"POST",headers,body:mutatedRaw
   });
 }
 return {gateway,signed,calls};
}
test("signed page is bound to verified phone and cannot be replayed",async()=>{
 const f=await fixture();
 const data={limit:15};
 const response=await f.gateway(await f.signed("delivery/inbox",data));
 assert.equal(response.status,200);
 assert.equal((await response.json()).items.length,1);
 assert.deepEqual(f.calls[0],{type:"inbox",actorId:OWNER,deviceId:PHONE,
   generation:1,limit:15,afterId:null});
 assert.equal((await f.gateway(await f.signed("delivery/inbox",data))).status,403);
 assert.equal(f.calls.length,1);
});
test("signed single-item retrieval never accepts a body claimed owner",async()=>{
 const f=await fixture();
 const response=await f.gateway(await f.signed("delivery/open",{deliveryId:ID}));
 assert.equal(response.status,200);
 const json=await response.json();
 assert.equal(json.deliveryId,ID);
 assert.equal(json.contextHex.length,284);
 assert.deepEqual(f.calls[0],{type:"open",actorId:OWNER,deviceId:PHONE,
   generation:1,deliveryId:ID});
 assert.equal((await f.gateway(await f.signed("delivery/open",{deliveryId:ID}))).status,403);
});
test("unknown fields, excessive limits, arbitrary cursors and invalid IDs are rejected",async()=>{
 const pageBodies=[{limit:21},{limit:0},{limit:1,userId:OWNER},
    {limit:1,afterId:"not-a-uuid"},{limit:2.5}];
 for(const body of pageBodies){
   const f=await fixture();
   assert.equal((await f.gateway(await f.signed("delivery/inbox",body))).status,400);
   assert.equal(f.calls.length,0);
 }
 for(const body of [{deliveryId:"wrong"},{deliveryId:ID,recipientId:OWNER},{}]){
   const f=await fixture();
   assert.equal((await f.gateway(await f.signed("delivery/open",body))).status,400);
   assert.equal(f.calls.length,0);
 }
});
test("body tampering fails signature before the inbox is opened",async()=>{
 const f=await fixture();
 const response=await f.gateway(await f.signed(
    "delivery/open",{deliveryId:ID},JSON.stringify({deliveryId:ID,caller:"other"})));
 assert.equal(response.status,400);
 assert.equal(f.calls.length,0);
 const page=await f.gateway(await f.signed("delivery/inbox",{limit:2},
    JSON.stringify({limit:3})));
 assert.equal(page.status,403);
 assert.equal(f.calls.length,0);
});
test("signed cursor stays server-bound; forged signature or generation fails",async()=>{
 const f=await fixture();
 const afterId="33333333-3333-4333-8333-333333333333";
 const response=await f.gateway(await f.signed("delivery/inbox",{limit:4,afterId}));
 assert.equal(response.status,200);
 assert.equal(f.calls[0].afterId,afterId);
 const another=await fixture();
 const request=await another.signed("delivery/open",{deliveryId:ID});
 request.headers.set("x-nexo-generation","2");
 assert.equal((await another.gateway(request)).status,403);
 assert.equal(another.calls.length,0);
});
