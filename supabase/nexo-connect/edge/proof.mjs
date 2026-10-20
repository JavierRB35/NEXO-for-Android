/**
 * Nexo Connect — proof of possession; portable WebCrypto for Deno/Node tests.
 * This module DOES NOT authenticate email/OTP/JWT, and MUST be called only
 * AFTER a trusted Supabase Auth validation has yielded the caller's UUID.
 * Never derive userId or publicKey from the request body itself.
 */
const UUID_RE = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/;
const ACTIONS = Object.freeze([
  "contact/invite", "contact/accept", "contact/permissions",
  "contact/unlink", "key/verify", "delivery/send",
  "delivery/resolve", "delivery/request-resend"
]);
const MAX_BODY = 524288;
const CLOCK_SKEW_MS = 90000;
const te = new TextEncoder();

function checkUuid(text) {
  if (typeof text !== "string" || !UUID_RE.test(text)) throw Error("invalid-uuid");
  return text;
}
function numberHeader(value) {
  if (typeof value !== "string" || !/^[1-9][0-9]{0,15}$/.test(value)) throw Error("invalid-number");
  const n=Number(value);
  if (!Number.isSafeInteger(n)) throw Error("unsafe-number");
  return n;
}
function toBytes(value) {
  if (!(value instanceof Uint8Array) || value.byteLength > MAX_BODY)
    throw Error("invalid-body");
  return value;
}
function encodeHex(bytes) {
  return [...bytes].map(b=>b.toString(16).padStart(2,"0")).join("");
}
export async function buildDeviceCanonicalRequest({
  userId, deviceId, generation, challengeId, issuedAt,
  method, path, body
}) {
  checkUuid(userId); checkUuid(deviceId); checkUuid(challengeId);
  if (!Number.isSafeInteger(generation) || generation<=0) throw Error("invalid-generation");
  if (!Number.isSafeInteger(issuedAt) || issuedAt<=0) throw Error("invalid-time");
  if (method!=="POST" || typeof path!=="string" ||
      !path.startsWith("/nexo-connect/") ||
      !ACTIONS.includes(path.substring("/nexo-connect/".length)))
    throw Error("invalid-route");
  const digest=new Uint8Array(await crypto.subtle.digest("SHA-256",toBytes(body)));
  const canonical=[
    "NEXO-CONNECT-PROOF-V1",method,path,userId,deviceId,
    String(generation),challengeId,String(issuedAt),encodeHex(digest),""
  ].join("\n");
  return te.encode(canonical);
}
export function decodeBase64Url(text,expectedSize) {
  if (typeof text!=="string" || !/^[A-Za-z0-9_-]+$/.test(text) ||
    text.length!==Math.ceil(expectedSize*8/6)) throw Error("bad-signature");
  const b64=text.replace(/-/g,"+").replace(/_/g,"/");
  const decoded=atob(b64.padEnd(Math.ceil(b64.length/4)*4,"="));
  const bytes=Uint8Array.from(decoded,c=>c.charCodeAt(0));
  if (bytes.length!==expectedSize) throw Error("bad-signature");
  return bytes;
}
export function encodeBase64Url(bytes) {
  if (!(bytes instanceof Uint8Array)) throw Error("invalid-bytes");
  let binary="";
  for (const b of bytes) binary+=String.fromCharCode(b);
  return btoa(binary).replace(/\+/g,"-").replace(/\//g,"_").replace(/=/g,"");
}
function requiredHeader(headers,key) {
  const value=headers.get(key);
  if (!value) throw Error("missing-header");
  return value;
}
/** Returns only after verifying AND atomically claiming an unused nonce.
 * Any error MUST abort the corresponding privileged database operation.
 */
export async function verifyDeviceProof({
  headers,userId,method,path,body,activeDevice,nowMillis,consumeChallenge
}) {
  if (!(headers instanceof Headers) ||
      typeof consumeChallenge!=="function" || !activeDevice)
    throw Error("invalid-server-context");
  // Do not trust the client-provided key even if it has a matching fingerprint.
  checkUuid(userId);
  const deviceId=checkUuid(requiredHeader(headers,"x-nexo-device"));
  const generation=numberHeader(requiredHeader(headers,"x-nexo-generation"));
  const challengeId=checkUuid(requiredHeader(headers,"x-nexo-challenge"));
  const issuedAt=numberHeader(requiredHeader(headers,"x-nexo-issued-at"));
  if (!Number.isSafeInteger(nowMillis) || Math.abs(nowMillis-issuedAt)>CLOCK_SKEW_MS)
    throw Error("expired-proof");
  if (activeDevice.user_id!==userId ||
      activeDevice.device_id!==deviceId ||
      activeDevice.generation!==generation)
    throw Error("device-revoked");
  const key=activeDevice.signing_public_key;
  if (!(key instanceof Uint8Array) || key.length!==32)
    throw Error("invalid-public-key");
  const signed=await buildDeviceCanonicalRequest({
    userId,deviceId,generation,challengeId,issuedAt,method,path,body
  });
  const signature=decodeBase64Url(requiredHeader(headers,"x-nexo-signature"),64);
  const publicKey=await crypto.subtle.importKey(
    "raw",key,{name:"Ed25519"},false,["verify"]);
  if (!await crypto.subtle.verify("Ed25519",publicKey,signature,signed))
    throw Error("invalid-signature");
  // Service-only function must consume nonce with one atomic UPDATE and
  // verify user/device/generation/action/TTL. Any duplicate returns false.
  const action=path.substring("/nexo-connect/".length);
  if (!await consumeChallenge({userId,deviceId,generation,challengeId,action}))
    throw Error("challenge-used-or-expired");
  return Object.freeze({userId,deviceId,generation,action});
}
