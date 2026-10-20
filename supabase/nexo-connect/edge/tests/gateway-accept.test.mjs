import test from "node:test";
import assert from "node:assert/strict";
import {webcrypto} from "node:crypto";
import {createGateway} from "../gateway.mjs";
import {buildDeviceCanonicalRequest,encodeBase64Url} from "../proof.mjs";
if(!globalThis.crypto)globalThis.crypto=webcrypto;
const USER="66666666-6666-4666-8666-666666666666",
      PHONE="67676767-6767-4767-8767-676767676767",
      CREATOR="11111111-1111-4111-8111-111111111111",
      NONCE="68686868-6868-4686-8686-686868686868",
      REQUEST_ID="69696969-6969-4696-8696-696969696969",
      TIME=1780000000000;
const QR=Object.freeze({
 creatorId:CREATOR,creatorGeneration:1,
 creatorSigningFingerprint:"e".repeat(64),
 creatorEncryptionFingerprint:"f".repeat(64),
 tokenSha256:"a".repeat(64)
});
async function fixture(){
 const pair=await crypto.subtle.generateKey({name:"Ed25519"},true,["sign","verify"]);
 const pub=new Uint8Array(await crypto.subtle.exportKey("raw",pair.publicKey));
 let consumed=false,accepted=0;
 const calls={otp:0,consume:0,accept:0};
 const handle=createGateway({
  authenticate:async token=>token==="verified"?{id:USER}:null,
  getDevice:async()=>({user_id:USER,device_id:PHONE,
    generation:1,signing_public_key:pub}),
  issueChallenge:async()=>NONCE,
  consumeChallenge:async({userId,action})=>{
    calls.consume++;
    assert.equal(userId,USER);assert.equal(action,"contact/accept");
    if(consumed)return false;
    consumed=true;return true;
  },
  createInvite:async()=>{throw Error("Invite not expected")},
  acceptInvite:async data=>{
    calls.accept++;assert.equal(data.recipientId,USER);
    assert.equal(data.creatorId,CREATOR);
    assert.equal(data.creatorSigningFingerprint,QR.creatorSigningFingerprint);
    assert.equal(data.creatorEncryptionFingerprint,QR.creatorEncryptionFingerprint);
    accepted++;return REQUEST_ID;
  },
  clock:()=>TIME
 });
 async function request(data=QR, overrides={}){
   const bytes=new TextEncoder().encode(JSON.stringify(data));
   const headers=new Headers({
     "authorization":"Bearer verified","x-nexo-device":PHONE,
     "x-nexo-generation":"1","x-nexo-challenge":NONCE,
     "x-nexo-issued-at":String(TIME),"content-type":"application/json"
   });
   const signing=await buildDeviceCanonicalRequest({
     userId:USER,deviceId:PHONE,generation:1,challengeId:NONCE,issuedAt:TIME,
     method:"POST",path:"/nexo-connect/contact/accept",body:bytes
   });
   const signature=new Uint8Array(await crypto.subtle.sign("Ed25519",pair.privateKey,signing));
   headers.set("x-nexo-signature",encodeBase64Url(signature));
   return new Request("https://example.invalid/functions/v1/nexo-connect-gateway/contact/accept",{
    method:"POST",headers,body:overrides.rawBody??JSON.stringify(data)
   });
 }
 return {handle,request,calls};
}
test("accept invitation only once with signed device and QR-bound keys",async()=>{
 const f=await fixture();
 const r=await f.handle(await f.request());
 assert.equal(r.status,200);assert.deepEqual(await r.json(),{
  linked:true,requestId:REQUEST_ID,creatorId:CREATOR,
  reciprocalKeyVerificationRequired:true
 });
 assert.equal(f.calls.accept,1);
 const again=await f.handle(await f.request());
 assert.equal(again.status,403);assert.equal(f.calls.accept,1);
});
test("tampered QR key is rejected before nonce consumption",async()=>{
 const f=await fixture();
 const res=await f.handle(await f.request(QR,{
  rawBody:JSON.stringify({...QR,creatorSigningFingerprint:"a".repeat(64)})
 }));
 assert.equal(res.status,403);
 assert.equal(f.calls.consume,0);
});
test("self-accept and malformed fingerprint rejected before signature",async()=>{
 const f=await fixture();
 const self=await f.handle(await f.request({...QR,creatorId:USER}));
 assert.equal(self.status,400);
 const bad=await f.handle(await f.request({...QR,creatorEncryptionFingerprint:"oops"}));
 assert.equal(bad.status,400);
 assert.equal(f.calls.accept,0);
});
test("creator details cannot be supplied as unsigned userId or extra field",async()=>{
 const f=await fixture();
 const r=await f.handle(await f.request({...QR,recipientId:USER}));
 assert.equal(r.status,400);assert.equal(f.calls.consume,0);
});
