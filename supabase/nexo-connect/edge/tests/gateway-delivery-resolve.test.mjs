import test from "node:test";
import assert from "node:assert/strict";
import {webcrypto} from "node:crypto";
import {createGateway} from "../gateway.mjs";
import {buildDeviceCanonicalRequest,encodeBase64Url} from "../proof.mjs";
if(!globalThis.crypto)globalThis.crypto=webcrypto;
const USER="11111111-1111-4111-8111-111111111111";
const DEVICE="12121212-1212-4212-8212-121212121212";
const DELIVERY="f1111111-1111-4111-8111-111111111111";
const CHALLENGE="13131313-1313-4313-8313-131313131313";
const NOW=1780000000000;
async function fixture(){
 const pair=await crypto.subtle.generateKey({name:"Ed25519"},true,["sign","verify"]);
 const pub=new Uint8Array(await crypto.subtle.exportKey("raw",pair.publicKey));
 const calls=[];let used=false,consumed=0;
 const gateway=createGateway({
  authenticate:async token=>token==="ok"?{id:USER}:null,
  getDevice:async()=>({user_id:USER,device_id:DEVICE,generation:1,signing_public_key:pub}),
  issueChallenge:async()=>CHALLENGE,
  consumeChallenge:async({action,userId,deviceId,generation})=>{
   assert.equal(action,"delivery/resolve");assert.equal(userId,USER);
   assert.equal(deviceId,DEVICE);assert.equal(generation,1);
   consumed++;if(used)return false;used=true;return true;
  },
  createInvite:async()=>{throw Error("Unexpected invite")},
  acceptInvite:async()=>{throw Error("Unexpected accept")},
  verifyPeer:async()=>{throw Error("Unexpected verify")},
  setReceivePermission:async()=>{throw Error("Unexpected permissions")},
  unlinkContact:async()=>{throw Error("Unexpected unlink")},
  resolveDelivery:async args=>{calls.push(args);return args.target},
  clock:()=>NOW
 });
 async function signed(data={deliveryId:DELIVERY,target:"accepted"},raw=JSON.stringify(data)){
  const headers=new Headers({authorization:"Bearer ok", "x-nexo-device":DEVICE,
   "x-nexo-generation":"1","x-nexo-challenge":CHALLENGE,
   "x-nexo-issued-at":String(NOW),"content-type":"application/json"});
  const canonical=await buildDeviceCanonicalRequest({userId:USER,
   deviceId:DEVICE,generation:1,challengeId:CHALLENGE,issuedAt:NOW,
   method:"POST",path:"/nexo-connect/delivery/resolve",
   body:new TextEncoder().encode(JSON.stringify(data))});
  headers.set("x-nexo-signature",encodeBase64Url(
   new Uint8Array(await crypto.subtle.sign("Ed25519",pair.privateKey,canonical))));
  return new Request("https://test.invalid/functions/v1/nexo-connect-gateway/delivery/resolve",{
   method:"POST",headers,body:raw
  });
 }
 return {gateway,signed,calls,get consumed(){return consumed}};
}
test("signed recipient resolution dispatches verified identity and is single use",async()=>{
 const f=await fixture();
 const r=await f.gateway(await f.signed());
 assert.equal(r.status,200);
 assert.deepEqual(await r.json(),{deliveryId:DELIVERY,status:"accepted"});
 assert.deepEqual(f.calls,[{actorId:USER,deviceId:DEVICE,generation:1,
   deliveryId:DELIVERY,target:"accepted"}]);
 assert.equal((await f.gateway(await f.signed())).status,403);
 assert.equal(f.calls.length,1);
});
test("signed cancellation and rejection keep their requested status",async()=>{
 for(const target of ["cancelled","rejected"]){
  const f=await fixture(),r=await f.gateway(await f.signed({deliveryId:DELIVERY,target}));
  assert.equal(r.status,200);assert.equal((await r.json()).status,target);
  assert.equal(f.calls.length,1);
 }
});
test("tampered status cannot be resolved and does not consume challenge",async()=>{
 const f=await fixture();
 const r=await f.gateway(await f.signed(
  {deliveryId:DELIVERY,target:"accepted"},
  JSON.stringify({deliveryId:DELIVERY,target:"cancelled"})));
 assert.equal(r.status,403);assert.equal(f.consumed,0);assert.equal(f.calls.length,0);
});
test("forged actor, malformed UUID, arbitrary status and unexpected fields rejected",async()=>{
 const f=await fixture();
 for(const data of [
  {deliveryId:DELIVERY,target:"accepted",actorId:USER},
  {deliveryId:DELIVERY,target:"pending"},
  {deliveryId:"not-a-uuid",target:"accepted"},
  {deliveryId:DELIVERY,target:"accepted",deviceId:DEVICE},
  {deliveryId:DELIVERY,target:"ACCEPTED"}
 ]){
  const r=await f.gateway(await f.signed(data));assert.equal(r.status,400);
 }
 assert.equal(f.consumed,0);assert.equal(f.calls.length,0);
});
test("wrong phone generation and missing signature are denied",async()=>{
 const f=await fixture();
 const a=await f.signed();a.headers.set("x-nexo-generation","2");
 assert.equal((await f.gateway(a)).status,403);
 const b=await f.signed();b.headers.delete("x-nexo-signature");
 assert.equal((await f.gateway(b)).status,403);
 assert.equal(f.consumed,0);assert.equal(f.calls.length,0);
});
