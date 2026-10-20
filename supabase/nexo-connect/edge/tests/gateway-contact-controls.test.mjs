import test from "node:test";
import assert from "node:assert/strict";
import {webcrypto} from "node:crypto";
import {createGateway} from "../gateway.mjs";
import {buildDeviceCanonicalRequest,encodeBase64Url} from "../proof.mjs";
if(!globalThis.crypto)globalThis.crypto=webcrypto;
const USER="11111111-1111-4111-8111-111111111111";
const PHONE="12121212-1212-4212-8212-121212121212";
const PEER="66666666-6666-4666-8666-666666666666";
const NONCE="13131313-1313-4313-8313-131313131313";
const TIME=1780000000000;
async function fixture(){
 const pair=await crypto.subtle.generateKey({name:"Ed25519"},true,["sign","verify"]);
 const pub=new Uint8Array(await crypto.subtle.exportKey("raw",pair.publicKey));
 let used=false;const calls={permissions:[],unlinks:[],consumed:0};
 const gateway=createGateway({
  authenticate:async token=>token==="ok"?{id:USER}:null,
  getDevice:async()=>({user_id:USER,device_id:PHONE,generation:1,signing_public_key:pub}),
  issueChallenge:async()=>NONCE,
  consumeChallenge:async({userId,action})=>{
   calls.consumed++;assert.equal(userId,USER);
   assert.ok(action==="contact/permissions"||action==="contact/unlink");
   if(used)return false;used=true;return true;
  },
  createInvite:async()=>{throw Error("Unexpected invite")},
  acceptInvite:async()=>{throw Error("Unexpected accept")},
  verifyPeer:async()=>{throw Error("Unexpected peer verification")},
  setReceivePermission:async data=>{calls.permissions.push(data)},
  unlinkContact:async data=>{calls.unlinks.push(data)},
  clock:()=>TIME
 });
 async function signed(route,data,rawBody=JSON.stringify(data)){
  const headers=new Headers({
    "authorization":"Bearer ok","x-nexo-device":PHONE,
    "x-nexo-generation":"1","x-nexo-challenge":NONCE,
    "x-nexo-issued-at":String(TIME),"content-type":"application/json"
  });
  const signedBytes=await buildDeviceCanonicalRequest({
   userId:USER,deviceId:PHONE,generation:1,challengeId:NONCE,issuedAt:TIME,
   method:"POST",path:"/nexo-connect"+route,body:new TextEncoder().encode(JSON.stringify(data))
  });
  const sig=new Uint8Array(await crypto.subtle.sign("Ed25519",pair.privateKey,signedBytes));
  headers.set("x-nexo-signature",encodeBase64Url(sig));
  return new Request("https://x.invalid/functions/v1/nexo-connect-gateway"+route,{
   method:"POST",headers,body:rawBody
  });
 }
 return{gateway,signed,calls};
}
test("directional receive setting requires one signed device challenge",async()=>{
 const f=await fixture();const r=await f.gateway(await f.signed(
  "/contact/permissions",{peerId:PEER,allowReceive:false}));
 assert.equal(r.status,200);
 assert.deepEqual(await r.json(),{peerId:PEER,receivingAllowed:false});
 assert.deepEqual(f.calls.permissions,[{
  userId:USER,deviceId:PHONE,generation:1,peerId:PEER,allowReceive:false
 }]);
 assert.equal((await f.gateway(await f.signed(
  "/contact/permissions",{peerId:PEER,allowReceive:false}))).status,403);
 assert.equal(f.calls.permissions.length,1);
});
test("unlink requires one signed device challenge",async()=>{
 const f=await fixture();const r=await f.gateway(await f.signed(
  "/contact/unlink",{peerId:PEER}));
 assert.equal(r.status,200);
 assert.deepEqual(await r.json(),{peerId:PEER,unlinked:true});
 assert.deepEqual(f.calls.unlinks,[{userId:USER,deviceId:PHONE,generation:1,peerId:PEER}]);
 assert.equal((await f.gateway(await f.signed("/contact/unlink",{peerId:PEER}))).status,403);
 assert.equal(f.calls.unlinks.length,1);
});
test("changed signed content is rejected before nonce consumption",async()=>{
 const f=await fixture();
 const original={peerId:PEER,allowReceive:false};
 const altered={peerId:PEER,allowReceive:true};
 const r=await f.gateway(await f.signed("/contact/permissions",original,JSON.stringify(altered)));
 assert.equal(r.status,403);assert.equal(f.calls.consumed,0);
 assert.equal(f.calls.permissions.length,0);
});
test("refuse arbitrary peer, self-unlink, wrong body schema",async()=>{
 const f=await fixture();
 for(const [route,data] of [
  ["/contact/unlink",{peerId:USER}],
  ["/contact/unlink",{peerId:PEER,other:true}],
  ["/contact/permissions",{peerId:PEER,allowReceive:"false"}],
  ["/contact/permissions",{peerId:PEER,allowReceive:false,userId:USER}]
 ]){
  const r=await f.gateway(await f.signed(route,data));
  assert.equal(r.status,400);
 }
 assert.equal(f.calls.consumed,0);
});
test("wrong device generation or missing signature cannot change permissions",async()=>{
 const f=await fixture();const req=await f.signed("/contact/permissions",{peerId:PEER,allowReceive:true});
 req.headers.set("x-nexo-generation","2");
 assert.equal((await f.gateway(req)).status,403);
 const req2=await f.signed("/contact/unlink",{peerId:PEER});
 req2.headers.delete("x-nexo-signature");
 assert.equal((await f.gateway(req2)).status,403);
 assert.equal(f.calls.consumed,0);
});
