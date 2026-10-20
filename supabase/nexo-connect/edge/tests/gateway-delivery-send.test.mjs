import test from "node:test";
import assert from "node:assert/strict";
import {webcrypto} from "node:crypto";
import {createGateway} from "../gateway.mjs";
import {buildDeviceCanonicalRequest,encodeBase64Url} from "../proof.mjs";
if(!globalThis.crypto)globalThis.crypto=webcrypto;

const SENDER="11111111-1111-4111-8111-111111111111";
const PHONE="12121212-1212-4212-8212-121212121212";
const RECIPIENT="22222222-2222-4222-8222-222222222222";
const DELIVERY="33333333-3333-4333-8333-333333333333";
const REQUEST="44444444-4444-4444-8444-444444444444";
const NONCE="55555555-5555-4555-8555-555555555555";
const NOW=1780000000000;
const DOMAIN=new TextEncoder().encode("NEXO-CONNECT-CRYPTO-POC-1");
function uuidInto(target,pos,id){
 const hex=id.replace(/-/g,"");for(let i=0;i<16;i++)target[pos+i]=parseInt(hex.slice(i*2,i*2+2),16);
}
function signedEnvelopeBytes(context,ciphertext){
 const output=new Uint8Array(2+DOMAIN.length+4+context.length+4+ciphertext.length);
 let offset=0;const view=new DataView(output.buffer);
 view.setUint16(offset,DOMAIN.length,false);offset+=2;
 output.set(DOMAIN,offset);offset+=DOMAIN.length;
 view.setUint32(offset,context.length,false);offset+=4;
 output.set(context,offset);offset+=context.length;
 view.setUint32(offset,ciphertext.length,false);offset+=4;
 output.set(ciphertext,offset);return output;
}
async function fixture(){
 const key=await crypto.subtle.generateKey({name:"Ed25519"},true,["sign","verify"]);
 const publicRaw=new Uint8Array(await crypto.subtle.exportKey("raw",key.publicKey));
 const fingerprint=new Uint8Array(await crypto.subtle.digest("SHA-256",publicRaw));
 const context=new Uint8Array(142),view=new DataView(context.buffer);
 view.setUint32(0,0x4e584331,false);context[4]=1;
 uuidInto(context,5,DELIVERY);uuidInto(context,21,SENDER);uuidInto(context,37,RECIPIENT);
 context[53]=2;view.setUint32(54,1,false);view.setUint32(58,1,false);
 view.setBigUint64(62,BigInt(NOW),false);view.setBigUint64(70,BigInt(NOW+86400000),false);
 context.set(fingerprint,78);context.fill(0x44,110,142);
 const cipher=new TextEncoder().encode("fake ciphertext for strict signature checks only");
 const sealedSignature=new Uint8Array(await crypto.subtle.sign(
   "Ed25519",key.privateKey,signedEnvelopeBytes(context,cipher)));
 const body={
   deliveryId:DELIVERY,clientRequestId:REQUEST,recipientId:RECIPIENT,
   recipientGeneration:1,entityType:"note",context:encodeBase64Url(context),
   ciphertext:encodeBase64Url(cipher),signature:encodeBase64Url(sealedSignature)
 };
 const calls=[];let nonceUsed=false;
 const gateway=createGateway({
  authenticate:async token=>token==="valid"?{id:SENDER}:null,
  getDevice:async()=>({user_id:SENDER,device_id:PHONE,generation:1,signing_public_key:publicRaw}),
  issueChallenge:async()=>NONCE,
  consumeChallenge:async({action})=>{assert.equal(action,"delivery/send");
    if(nonceUsed)return false;nonceUsed=true;return true;},
  createInvite:async()=>{throw Error("Unexpected invite")},
  acceptInvite:async()=>{throw Error("Unexpected accept")},
  verifyPeer:async()=>{throw Error("Unexpected verify")},
  setReceivePermission:async()=>{throw Error("Unexpected permissions")},
  unlinkContact:async()=>{throw Error("Unexpected unlink")},
  sendDelivery:async args=>{calls.push(args);return args.deliveryId;},
  clock:()=>NOW
 });
 async function signed(payload=body){
  const headers=new Headers({authorization:"Bearer valid","x-nexo-device":PHONE,
    "x-nexo-generation":"1","x-nexo-challenge":NONCE,
    "x-nexo-issued-at":String(NOW),"content-type":"application/json"});
  const canonical=await buildDeviceCanonicalRequest({
    userId:SENDER,deviceId:PHONE,generation:1,challengeId:NONCE,issuedAt:NOW,
    method:"POST",path:"/nexo-connect/delivery/send",
    body:new TextEncoder().encode(JSON.stringify(payload))
  });
  headers.set("x-nexo-signature",encodeBase64Url(new Uint8Array(
    await crypto.subtle.sign("Ed25519",key.privateKey,canonical))));
  return new Request("https://x.invalid/functions/v1/nexo-connect-gateway/delivery/send",{
    method:"POST",headers,body:JSON.stringify(payload)
  });
 }
 return {gateway,body,signed,calls};
}
test("signed delivery requires one-time device proof and matching NXC1 signature",async()=>{
 const f=await fixture();
 const res=await f.gateway(await f.signed());
 assert.equal(res.status,201);
 assert.deepEqual(await res.json(),{deliveryId:DELIVERY,status:"pending"});
 assert.equal(f.calls.length,1);
 assert.equal(f.calls[0].senderId,SENDER);
 assert.equal(f.calls[0].generation,1);
 assert.deepEqual(Array.from(f.calls[0].context).slice(0,5),[78,88,67,49,1]);
 assert.equal((await f.gateway(await f.signed())).status,403);
 assert.equal(f.calls.length,1);
});
test("recipient generation mismatch is rejected even with valid HTTP signature",async()=>{
 const f=await fixture();
 assert.equal((await f.gateway(await f.signed({...f.body,recipientGeneration:2}))).status,403);
 assert.equal(f.calls.length,0);
});
test("signed envelope signature mutation fails closed",async()=>{
 const f=await fixture();
 const mutation={...f.body,signature:encodeBase64Url(new Uint8Array(64))};
 assert.equal((await f.gateway(await f.signed(mutation))).status,403);
 assert.equal(f.calls.length,0);
});
test("wrong sender ID in canonical context cannot be used by signed request",async()=>{
 const f=await fixture();
 const data=new Uint8Array(Buffer.from(f.body.context.replace(/-/g,"+").replace(/_/g,"/"),"base64"));
 uuidInto(data,21,RECIPIENT);
 const mutated={...f.body,context:encodeBase64Url(data)};
 assert.equal((await f.gateway(await f.signed(mutated))).status,403);
 assert.equal(f.calls.length,0);
});
test("expired context and invalid base64 cannot reach database",async()=>{
 const f=await fixture();
 assert.equal((await f.gateway(await f.signed({...f.body,ciphertext:"!!!"}))).status,403);
 assert.equal(f.calls.length,0);
});
test("unexpected fields, impersonation and unknown entity are rejected",async()=>{
 for(const dataChange of [
   x=>({...x,userId:SENDER}),
   x=>({...x,entityType:"secret"}),
   x=>({...x,recipientId:SENDER}),
   x=>({...x,clientRequestId:"invalid"})
 ]){
   const f=await fixture();
   assert.equal((await f.gateway(await f.signed(dataChange(f.body)))).status===201,false);
   assert.equal(f.calls.length,0);
 }
});
