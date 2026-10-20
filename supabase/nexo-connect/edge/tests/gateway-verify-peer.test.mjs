import test from "node:test";
import assert from "node:assert/strict";
import {webcrypto} from "node:crypto";
import {createGateway} from "../gateway.mjs";
import {buildDeviceCanonicalRequest,encodeBase64Url} from "../proof.mjs";
if(!globalThis.crypto)globalThis.crypto=webcrypto;
const OWNER="11111111-1111-4111-8111-111111111111";
const PHONE="12121212-1212-4212-8212-121212121212";
const PEER="66666666-6666-4666-8666-666666666666";
const CHALLENGE="13131313-1313-4313-8313-131313131313";
const TIME=1780000000000;
const QR=Object.freeze({
 peerId:PEER,peerGeneration:1,
 peerSigningFingerprint:"a".repeat(64),
 peerEncryptionFingerprint:"b".repeat(64)
});
async function fixture(){
 const key=await crypto.subtle.generateKey({name:"Ed25519"},true,["sign","verify"]);
 const pub=new Uint8Array(await crypto.subtle.exportKey("raw",key.publicKey));
 let consumed=false, verified=0;
 const gateway=createGateway({
  authenticate:async token=>token==="verified"?{id:OWNER}:null,
  getDevice:async()=>({user_id:OWNER,device_id:PHONE,generation:1,signing_public_key:pub}),
  issueChallenge:async()=>CHALLENGE,
  consumeChallenge:async ({action})=>{
    assert.equal(action,"key/verify");
    if(consumed)return false;
    consumed=true;return true;
  },
  createInvite:async()=>{throw Error("Unexpected invite")},
  acceptInvite:async()=>{throw Error("Unexpected acceptance")},
  verifyPeer:async args=>{
    verified++;
    assert.deepEqual(args,{ownerId:OWNER,ownerDeviceId:PHONE,ownerGeneration:1,
      peerId:PEER,peerGeneration:1,
      peerSigningFingerprint:QR.peerSigningFingerprint,
      peerEncryptionFingerprint:QR.peerEncryptionFingerprint});
  },
  clock:()=>TIME
 });
 async function request(body=QR,rawBody=JSON.stringify(body)){
  const headers=new Headers({
    authorization:"Bearer verified","x-nexo-device":PHONE,
    "x-nexo-generation":"1","x-nexo-challenge":CHALLENGE,
    "x-nexo-issued-at":String(TIME),"content-type":"application/json"
  });
  const canonical=await buildDeviceCanonicalRequest({
    userId:OWNER,deviceId:PHONE,generation:1,challengeId:CHALLENGE,issuedAt:TIME,
    method:"POST",path:"/nexo-connect/key/verify",
    body:new TextEncoder().encode(JSON.stringify(body))
  });
  const sig=new Uint8Array(await crypto.subtle.sign("Ed25519",key.privateKey,canonical));
  headers.set("x-nexo-signature",encodeBase64Url(sig));
  return new Request("https://example.invalid/functions/v1/nexo-connect-gateway/key/verify",{
    method:"POST",headers,body:rawBody
  });
 }
 return {gateway,request,verifications:()=>verified};
}
test("mutual QR fingerprint approval requires signed one-time device challenge",async()=>{
 const f=await fixture();
 const r=await f.gateway(await f.request());
 assert.equal(r.status,200);
 assert.deepEqual(await r.json(),{verified:true,peerId:PEER,generation:1});
 assert.equal(f.verifications(),1);
 assert.equal((await f.gateway(await f.request())).status,403);
 assert.equal(f.verifications(),1);
});
test("tampering with peer fingerprint rejects before key approval",async()=>{
 const f=await fixture();
 const data={...QR,peerSigningFingerprint:"c".repeat(64)};
 const r=await f.gateway(await f.request(QR,JSON.stringify(data)));
 assert.equal(r.status,403);assert.equal(f.verifications(),0);
});
test("self-verification or unexpected fields are rejected",async()=>{
 const f=await fixture();
 assert.equal((await f.gateway(await f.request({...QR,peerId:OWNER}))).status,400);
 assert.equal((await f.gateway(await f.request({...QR,verified:true}))).status,400);
 assert.equal(f.verifications(),0);
});
