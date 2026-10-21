import test from "node:test";
import assert from "node:assert/strict";
import {webcrypto} from "node:crypto";
import {createGateway} from "../gateway.mjs";
import {buildEnrollmentCanonical} from "../enrollment.mjs";

if(!globalThis.crypto)globalThis.crypto=webcrypto;
const USER="11111111-1111-4111-8111-111111111111";
const DEVICE="22222222-2222-4222-8222-222222222222";
const OTHER="33333333-3333-4333-8333-333333333333";
const NOW=1810000000000;
const TOKEN="123456";
const b64=x=>Buffer.from(x).toString("base64url");
const ENC=b64(new Uint8Array(32).fill(7));
async function fixture(){
 const pair=await crypto.subtle.generateKey("Ed25519",true,["sign","verify"]);
 const sign=b64(new Uint8Array(await crypto.subtle.exportKey("raw",pair.publicKey)));
 const calls={otp:0,rpc:0};
 let otpUsed=false;
 let authIdentity={id:USER,email:"user@example.org",emailConfirmed:true};
 const handler=createGateway({
   authenticate:async jwt=>jwt==="valid"?authIdentity:null,
   getDevice:async()=>{throw Error("Device should not be needed for OTP enrollment")},
   issueChallenge:async()=>{throw Error("Not needed")},
   consumeChallenge:async()=>{throw Error("Not needed")},
   createInvite:async()=>{throw Error("Not needed")},
   acceptInvite:async()=>{throw Error("Not needed")},
   verifyPeer:async()=>{throw Error("Not needed")},
   setReceivePermission:async()=>{throw Error("Not needed")},
   unlinkContact:async()=>{throw Error("Not needed")},
   verifyFreshOtp:async({userId,email,otp})=>{
      calls.otp++;assert.equal(userId,USER);
      assert.equal(email,"user@example.org");
      assert.equal(otp,TOKEN);
      if(otpUsed)return false;
      otpUsed=true;return true;
   },
   enrollDevice:async args=>{
     calls.rpc++;
     assert.equal(args.userId,USER);
     assert.equal(args.deviceId,DEVICE);
     assert.deepEqual(args.signingRaw,Buffer.from(sign,"base64url"));
     assert.equal(args.encryptionRaw.length,32);
     return args.replace?2:1;
   },
   clock:()=>NOW
 });
 const body={deviceId:DEVICE,signingKey:sign,encryptionKey:ENC,otp:TOKEN,replace:false};
 async function request({payload=body,issuedAt=NOW,jwt="valid",signingKey=pair.privateKey,
                          mutateBody=null}={}){
   const raw=JSON.stringify(payload);
   const data=new TextEncoder().encode(raw);
   const canonical=await buildEnrollmentCanonical({
     userId:USER,issuedAt,body:data
   });
   const signature=b64(new Uint8Array(await crypto.subtle.sign(
       "Ed25519",signingKey,canonical)));
   return new Request("https://nexo.test/functions/v1/nexo-connect-gateway/device/enroll",{
     method:"POST",
     headers:{"authorization":"Bearer "+jwt,"content-type":"application/json",
       "x-nexo-issued-at":String(issuedAt),
       "x-nexo-enroll-signature":signature},
     body:mutateBody??raw
   });
 }
 return {handler,request,body,calls,setIdentity:value=>authIdentity=value};
}

test("first phone enrollment requires signed public-key ownership AND fresh OTP",async()=>{
 const f=await fixture();
 const res=await f.handler(await f.request());
 assert.equal(res.status,201);
 assert.deepEqual(await res.json(),{deviceId:DEVICE,generation:1,replacement:false});
 assert.deepEqual(f.calls,{otp:1,rpc:1});
 // Existing access JWT and same signed JSON are insufficient: OTP already consumed.
 const replay=await f.handler(await f.request());
 assert.equal(replay.status,403);
 assert.deepEqual(f.calls,{otp:2,rpc:1});
});
test("explicit replacement registers new generation ONLY after new OTP",async()=>{
 const f=await fixture();
 const res=await f.handler(await f.request({
   payload:{...f.body,replace:true}
 }));
 assert.equal(res.status,201);
 assert.deepEqual(await res.json(),{deviceId:DEVICE,generation:2,replacement:true});
 assert.equal(f.calls.rpc,1);
});
test("invalid signatures, changed body and changed timestamp never consume OTP",async()=>{
 const f=await fixture();
 const outsider=await crypto.subtle.generateKey("Ed25519",true,["sign","verify"]);
 assert.equal((await f.handler(await f.request({
   signingKey:outsider.privateKey
 }))).status,403);
 assert.equal((await f.handler(await f.request({
   mutateBody:JSON.stringify({...f.body,replace:true})
 }))).status,403);
 assert.equal((await f.handler(await f.request({issuedAt:NOW-100000}))).status,403);
 assert.deepEqual(f.calls,{otp:0,rpc:0});
});
test("missing, unconfirmed, forged or other-account JWT refuses enrollment",async()=>{
 const f=await fixture();
 assert.equal((await f.handler(await f.request({jwt:"other"}))).status,401);
 f.setIdentity({id:OTHER,email:"user@example.org",emailConfirmed:true});
 assert.equal((await f.handler(await f.request())).status,403);
 f.setIdentity({id:USER,email:"user@example.org",emailConfirmed:false});
 assert.equal((await f.handler(await f.request())).status,403);
 assert.deepEqual(f.calls,{otp:0,rpc:0});
});
test("body-supplied actor, invalid keys and OTP fail before mail and RPC",async()=>{
 for(const change of [
   {userId:USER},{otp:"wrong"},{signingKey:"a"},{encryptionKey:"x"},
   {replace:"yes"},{deviceId:"incorrect"}
 ]){
   const f=await fixture();
   const res=await f.handler(await f.request({payload:{...f.body,...change}}));
   assert.equal(res.status,403);
   assert.deepEqual(f.calls,{otp:0,rpc:0});
 }
});
test("request cannot register without configured OTP verifier",async()=>{
 const f=await fixture();
 const noOtp=createGateway({
   authenticate:async()=>({id:USER,email:"user@example.org",emailConfirmed:true}),
   getDevice:async()=>null,issueChallenge:async()=>OTHER,
   consumeChallenge:async()=>false,createInvite:async()=>OTHER,
   acceptInvite:async()=>OTHER,verifyPeer:async()=>{},
   setReceivePermission:async()=>{},unlinkContact:async()=>{}
 });
 assert.equal((await noOtp(await f.request())).status,503);
});
