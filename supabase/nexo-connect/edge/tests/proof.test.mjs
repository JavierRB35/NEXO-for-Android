import test from "node:test";
import assert from "node:assert/strict";
import {webcrypto} from "node:crypto";
import {
  buildDeviceCanonicalRequest,encodeBase64Url,verifyDeviceProof
} from "../proof.mjs";

if (!globalThis.crypto) globalThis.crypto=webcrypto;
const A="11111111-1111-4111-8111-111111111111";
const B="22222222-2222-4222-8222-222222222222";
const DEVICE="33333333-3333-4333-8333-333333333333";
const CHALLENGE="44444444-4444-4444-8444-444444444444";
const NOW=1780000000000;
const BODY=new TextEncoder().encode('{"kind":"note","opaque":"ciphertext"}');
const PATH="/nexo-connect/delivery/send";
const gen=1;

async function makeFixture() {
  const {privateKey,publicKey}=await crypto.subtle.generateKey(
    {name:"Ed25519"},true,["sign","verify"]);
  const raw=new Uint8Array(await crypto.subtle.exportKey("raw",publicKey));
  const headers=new Headers();
  headers.set("x-nexo-device",DEVICE);
  headers.set("x-nexo-generation",String(gen));
  headers.set("x-nexo-challenge",CHALLENGE);
  headers.set("x-nexo-issued-at",String(NOW));
  const canonical=await buildDeviceCanonicalRequest({
    userId:A,deviceId:DEVICE,generation:gen,challengeId:CHALLENGE,
    issuedAt:NOW,method:"POST",path:PATH,body:BODY
  });
  const signature=new Uint8Array(await crypto.subtle.sign("Ed25519",privateKey,canonical));
  assert.equal(signature.length,64);
  headers.set("x-nexo-signature",encodeBase64Url(signature));
  let used=false,claims=0;
  const consumeChallenge=async ({userId,deviceId,generation,challengeId,action})=>{
    claims++;
    if (used||userId!==A||deviceId!==DEVICE||generation!==gen
      ||challengeId!==CHALLENGE||action!=="delivery/send") return false;
    used=true;
    return true;
  };
  return {
    headers,activeDevice:{user_id:A,device_id:DEVICE,
      generation:gen,signing_public_key:raw},
    consumeChallenge,claims:()=>claims
  };
}
const base=f=>({
  headers:f.headers,userId:A,method:"POST",path:PATH,body:BODY,
  activeDevice:f.activeDevice,nowMillis:NOW,consumeChallenge:f.consumeChallenge
});
test("valid signer, device, time, action and atomic nonce accepted once",async()=>{
  const f=await makeFixture();
  const result=await verifyDeviceProof(base(f));
  assert.equal(result.userId,A);
  assert.equal(f.claims(),1);
  await assert.rejects(verifyDeviceProof(base(f)),/challenge-used-or-expired/);
  assert.equal(f.claims(),2);
});
test("mutated signed body is rejected without spending nonce",async()=>{
  const f=await makeFixture();
  await assert.rejects(verifyDeviceProof({...base(f),body:new Uint8Array([8])}),/invalid-signature/);
  assert.equal(f.claims(),0);
  assert.equal((await verifyDeviceProof(base(f))).deviceId,DEVICE);
});
test("changed sender, generation, recipient route and method all rejected",async()=>{
  const f=await makeFixture();
  await assert.rejects(verifyDeviceProof({...base(f),userId:B}),/device-revoked/);
  await assert.rejects(verifyDeviceProof({...base(f),path:"/nexo-connect/delivery/resolve"}),/invalid-signature/);
  await assert.rejects(verifyDeviceProof({...base(f),method:"DELETE"}),/invalid-route/);
  const headers=new Headers(f.headers);headers.set("x-nexo-generation","2");
  await assert.rejects(verifyDeviceProof({...base(f),headers}),/device-revoked/);
  assert.equal(f.claims(),0);
});
test("wrong signing key and stale proof are rejected",async()=>{
  const f=await makeFixture();
  const foreign=(await makeFixture()).activeDevice.signing_public_key;
  await assert.rejects(verifyDeviceProof({...base(f),
    activeDevice:{...f.activeDevice,signing_public_key:foreign}}),/invalid-signature/);
  await assert.rejects(verifyDeviceProof({...base(f),nowMillis:NOW+91000}),/expired-proof/);
  assert.equal(f.claims(),0);
});
test("reject malformed signature, zero length, unknown path, huge body",async()=>{
  const f=await makeFixture();
  const headers=new Headers(f.headers);headers.set("x-nexo-signature","*");
  await assert.rejects(verifyDeviceProof({...base(f),headers}),/bad-signature/);
  await assert.rejects(verifyDeviceProof({...base(f),path:"/nexo-connect/key/delete"}),/invalid-route/);
  await assert.rejects(verifyDeviceProof({...base(f),body:new Uint8Array(524289)}),/invalid-body/);
  assert.equal(f.claims(),0);
});
