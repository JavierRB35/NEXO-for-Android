// Supabase Edge gateway: JWT validated through Auth.getUser.
// Restricted to status, challenge, and signed contact invitation creation.
import {createClient} from "npm:@supabase/supabase-js@2";
import {createGateway} from "./gateway.mjs";
const url=Deno.env.get("SUPABASE_URL");
const anon=Deno.env.get("SUPABASE_ANON_KEY");
const service=Deno.env.get("SUPABASE_SERVICE_ROLE_KEY");
if(!url||!anon||!service)throw Error("Missing Supabase configuration");
const auth=createClient(url,anon,{auth:{persistSession:false,autoRefreshToken:false}});
const admin=createClient(url,service,{auth:{persistSession:false,autoRefreshToken:false}});
const hexToRaw=hex=>{
  if(typeof hex!=="string"||!/^[a-fA-F0-9]{64}$/.test(hex))
    throw Error("Unexpected signing key");
  return Uint8Array.from(hex.match(/.{2}/g),x=>parseInt(x,16));
};
const hexToBytea=hex=>{
  if(typeof hex!=="string"||!/^[a-f0-9]{64}$/.test(hex))
    throw Error("Unexpected hash");
  return "\\x"+hex;
};
const rpc=async(name,args)=>{
  const {data,error}=await admin.rpc(name,args);
  if(error)throw Error("Service operation failed");
  return data;
};
const gateway=createGateway({
  authenticate:async token=>{
    const {data,error}=await auth.auth.getUser(token);
    if(error||!data?.user)return null;
    return {id:data.user.id};
  },
  getDevice:async userId=>{
    const data=await rpc("nexo_connect_proof_device",{p_user:userId});
    const device=Array.isArray(data)?data[0]??null:null;
    if(!device)return null;
    return {
      user_id:device.user_id,device_id:device.device_id,
      generation:Number(device.generation),
      signing_public_key:hexToRaw(device.signing_key_hex)
    };
  },
  issueChallenge:async({userId,deviceId,generation,action})=>
    rpc("nexo_connect_issue_challenge",{
      p_user:userId,p_device:deviceId,p_generation:generation,p_action:action
    }),
  consumeChallenge:async({userId,deviceId,generation,challengeId,action})=>
    rpc("nexo_connect_consume_proof",{
      p_user:userId,p_device:deviceId,p_generation:generation,
      p_action:action,p_challenge:challengeId
    }),
  createInvite:async({userId,tokenSha256,expiresInSeconds})=>
    rpc("nexo_connect_create_invite",{
      p_user:userId,p_token_sha256:hexToBytea(tokenSha256),p_seconds:expiresInSeconds
    }),
  acceptInvite:async({recipientId,recipientDeviceId,recipientGeneration,
                      creatorId,creatorGeneration,creatorSigningFingerprint,
                      creatorEncryptionFingerprint,tokenSha256})=>
    rpc("nexo_connect_accept_invite",{
      p_recipient:recipientId,p_recipient_device:recipientDeviceId,
      p_recipient_generation:recipientGeneration,p_creator:creatorId,
      p_creator_generation:creatorGeneration,p_token_hash:hexToBytea(tokenSha256),
      p_creator_signing_fp:hexToBytea(creatorSigningFingerprint),
      p_creator_encryption_fp:hexToBytea(creatorEncryptionFingerprint)
    })
});
Deno.serve(gateway);
