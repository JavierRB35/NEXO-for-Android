import test from "node:test";
import assert from "node:assert/strict";
import {createGateway} from "../gateway.mjs";
const UID="11111111-1111-4111-8111-111111111111";
const DEVICE="22222222-2222-4222-8222-222222222222";
const NONCE="33333333-3333-4333-8333-333333333333";
const authHeader={authorization:"Bearer valid-session"};
const req=(path,body={},headers=authHeader,method="POST")=>
 new Request("https://nexo.test/"+path,{
  method,headers:{"content-type":"application/json",...headers},
  body:method==="POST"?JSON.stringify(body):undefined
 });
function fixture(){
 const calls={auth:0,device:0,challenge:0};
 const handle=createGateway({
  authenticate:async token=>{calls.auth++;return token==="valid-session"?{id:UID}:null},
  getDevice:async user=>{calls.device++;assert.equal(user,UID);
    return {user_id:UID,device_id:DEVICE,generation:2}},
  issueChallenge:async d=>{calls.challenge++;
    assert.deepEqual(d,{userId:UID,deviceId:DEVICE,generation:2,action:"delivery/send"});
    return NONCE;},
  acceptInvite:async()=>{throw Error("Acceptance unsupported in fixture")},
  consumeChallenge:async()=>{throw Error("Not enabled in these tests")},
  createInvite:async()=>{throw Error("Not enabled in these tests")}
 });
 return {handle,calls};
}
test("verified JWT before any privileged database lookup",async()=>{
 const f=fixture();const res=await f.handle(req("status",{},{}));
 assert.equal(res.status,401);
 assert.deepEqual(f.calls,{auth:0,device:0,challenge:0});
 const unknown=await f.handle(req("status",{},{"authorization":"Bearer forged"}));
 assert.equal(unknown.status,401);
 assert.equal(f.calls.device,0);
});
test("status returns only registered device from verified backend",async()=>{
 const f=fixture();const r=await f.handle(req("status"));
 assert.equal(f.calls.auth,1,"Auth validator was never invoked");
 assert.equal(r.status,200);
 assert.deepEqual(await r.json(),{registered:true,deviceId:DEVICE,generation:2});
 assert.equal(f.calls.challenge,0);
});
test("valid registered phone gets one-time challenge",async()=>{
 const f=fixture();
 const r=await f.handle(req("challenge",{deviceId:DEVICE,generation:2,action:"delivery/send"}));
 assert.equal(r.status,200);
 assert.deepEqual(await r.json(),{challengeId:NONCE,expiresInSeconds:120});
 assert.equal(f.calls.challenge,1);
});
test("wrong phone, generation, arbitrary action, injected body are rejected",async()=>{
 const f=fixture();
 for(const input of [
  {deviceId:UID,generation:2,action:"delivery/send"},
  {deviceId:DEVICE,generation:1,action:"delivery/send"},
  {deviceId:DEVICE,generation:2,action:"admin/delete"},
  {deviceId:DEVICE,generation:2,action:"delivery/send",userId:UID}
 ]){const r=await f.handle(req("challenge",input));assert.ok(r.status===400||r.status===403);}
 assert.equal(f.calls.challenge,0);
});
test("reject oversize bodies and methods other than POST",async()=>{
 const f=fixture();
 assert.equal((await f.handle(req("status",{},authHeader,"GET"))).status,405);
 const huge=new Request("https://nexo.test/status",{method:"POST",headers:authHeader,
  body:JSON.stringify({junk:"a".repeat(3000)})});
 assert.equal((await f.handle(huge)).status,413);
});

test("streamed request without content-length cannot bypass payload limit",async()=>{
 const f=fixture();
 const long=new Uint8Array(3000).fill(65);
 const stream=new ReadableStream({
   start(ctrl){ctrl.enqueue(long);ctrl.close()}
 });
 const request=new Request("https://nexo.test/functions/v1/nexo-connect-gateway/status",{
  method:"POST",headers:authHeader,body:stream,duplex:"half"
 });
 const r=await f.handle(request);
 assert.equal(r.status,413);
 assert.equal(f.calls.device,0);
});
test("Edge Function route is strict and accepts only named status path",async()=>{
 const f=fixture();
 const r=await f.handle(new Request("https://x.test/functions/v1/nexo-connect-gateway/status",{
  method:"POST",headers:authHeader,body:"{}"
 }));
 assert.equal(r.status,200);
 const other=await f.handle(new Request("https://x.test/functions/v1/nexo-connect-gateway/anything",{
  method:"POST",headers:authHeader,body:"{}"
 }));
 assert.equal(other.status,404);
});
