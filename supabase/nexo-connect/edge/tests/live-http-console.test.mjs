import test from "node:test";
import {offlineSelfTest} from "../live-http-console.mjs";

// Runs in CI without network, accounts, OTP, camera, or a local emulator.
// Real HTTP is opt-in only with: node edge/live-http-console.mjs --live
test("console harness signs and verifies actual Ed25519 enrollment and action proofs",async()=>{
  await offlineSelfTest();
});
