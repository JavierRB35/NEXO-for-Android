import test from "node:test";
import assert from "node:assert/strict";
import {webcrypto} from "node:crypto";
import {canonicalEnrollment,createEnrollmentGateway} from "../enrollment.mjs";
if(!globalThis.crypto)globalThis.crypto=webcrypto;
const USER="11111111-1111-4111-8111-111111111111";
const PHONE="22222222-2222-4222-8222-222222222222";
const EMAIL="person@example.test";
const NOW=1780000000000;
const hex=x=>Array.from(x,b=>b.toString(16).padStart(2,"0")).join("");
const b64=x=>Buffer.from(x).toString("base64url");
async function fixture(){
 const signer=await crypto.subtle.generateKey({name:"Ed25519"},true,["sign","verify"]);
 const signingKeyHex=hex(new Uint8Array(await crypto.subtle.exportKey("raw",signer.publicKey)));
 const data={deviceId:PHONE,signingKeyHex,encryptionKeyHex:"5a".repeat(32),
   issuedAt:NOW,mode:"register",otp:"123456"};
 async function signed(override={}){
   const full={...data,...override};
   const canonical=canonicalEnrollment({...full,userId:USER});
   full.proofSignature=b64(new Uint8Array(await crypto.subtle.sign("Ed25519",
     signer.privateKey,canonical)));
   return full;
 }
 const calls={auth:0,otp:0,enroll:0};
 let otpResult={id:USER,email:EMAIL};
 const handle=createEnrollmentGateway({
   authenticate:async token=>{calls.auth++;return token==="session"?{id:USER,email:EMAIL}:null;},
   verifyFreshOtp:async({email,otp})=>{
     calls.otp++;assert.equal(email,EMAIL);return otp==="123456"?otpResult:null;},
   enroll:async arg=>{calls.enroll++;assert.equal(arg.userId,USER);return arg.replace?2:1;},
   clock:()=>NOW
 });
 const request=(body,bearer="session")=>new Request(
  "https://test.invalid/functions/v1/nexo-connect-enroll/device/enroll",{
    method:"POST",headers:{"authorization":"Bearer "+bearer,"content-type":"application/json"},
    body:JSON.stringify(body)
  });
 return {handle,signed,request,calls,setOtpResult:v=>{otpResult=v}};
}
test("JWT+fresh OTP+proof allows first enrollment",async()=>{
 const f=await fixture();
 const resp=await f.handle(f.request(await f.signed()));
 assert.equal(resp.status,201);
 assert.deepEqual(await resp.json(),{registered:true,deviceId:PHONE,generation:1,
   contactsNeedReverification:false});
 assert.deepEqual(f.calls,{auth:1,otp:1,enroll:1});
});
test("replacement forces contact reverification",async()=>{
 const f=await fixture();
 const res=await f.handle(f.request(await f.signed({mode:"replace"})));
 assert.equal(res.status,201);
 assert.equal((await res.json()).contactsNeedReverification,true);
});
test("JWT mismatch is rejected before OTP validation or backend",async()=>{
 const f=await fixture();
 assert.equal((await f.handle(f.request(await f.signed(),"fake"))).status,401);
 assert.equal(f.calls.otp,0);assert.equal(f.calls.enroll,0);
});
test("OTP verified for different user or email cannot enroll",async()=>{
 const f=await fixture();f.setOtpResult({id:PHONE,email:EMAIL});
 assert.equal((await f.handle(f.request(await f.signed()))).status,403);
 assert.equal(f.calls.enroll,0);
});
test("tampered public keys, signature or enrollment mode refused",async()=>{
 const f=await fixture();const signed=await f.signed();
 for(const modified of [
   {...signed, encryptionKeyHex:"2a".repeat(32)},
   {...signed,deviceId:USER},
   {...signed,mode:"replace"},
   {...signed,proofSignature:"0".repeat(86)}
 ]){const r=await f.handle(f.request(modified));assert.equal(r.status,403);}
 assert.equal(f.calls.otp,0);
});
test("expiry and malformed OTP rejected before verification",async()=>{
 const f=await fixture();const signed=await f.signed();
 assert.equal((await f.handle(f.request({...signed,issuedAt:NOW-120000}))).status,403);
 assert.equal((await f.handle(f.request({...signed,otp:"1234567"}))).status,400);
 assert.equal(f.calls.enroll,0);
});
test("invalid fresh OTP rejected even with a valid JWT",async()=>{
 const f=await fixture();
 const res=await f.handle(f.request(await f.signed({otp:"999999"})));
 assert.equal(res.status,403);assert.equal(f.calls.enroll,0);
});
