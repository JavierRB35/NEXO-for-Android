// Supabase Edge device enrollment: JWT + second fresh email OTP + key possession.
// No browser CORS and no public access to service_role. Not yet user-facing.
import {createClient} from "npm:@supabase/supabase-js@2";
import {createEnrollmentGateway} from "./enrollment.mjs";
const url=Deno.env.get("SUPABASE_URL"),pub=Deno.env.get("SUPABASE_ANON_KEY"),
      adminKey=Deno.env.get("SUPABASE_SERVICE_ROLE_KEY");
if(!url||!pub||!adminKey)throw Error("Missing Supabase environment");
const client=()=>createClient(url,pub,{auth:{
  persistSession:false,autoRefreshToken:false,detectSessionInUrl:false
}});
const admin=createClient(url,adminKey,{auth:{
  persistSession:false,autoRefreshToken:false,detectSessionInUrl:false
}});
const rpc=async(args)=>{
 const {data,error}=await admin.rpc("nexo_connect_enroll_device",args);
 if(error)throw Error("Enrollment rejected");
 return data;
};
const gateway=createEnrollmentGateway({
 authenticate:async token=>{
  const {data,error}=await client().auth.getUser(token);
  if(error||!data?.user||!data.user.email)return null;
  return {id:data.user.id,email:data.user.email};
 },
 verifyFreshOtp:async({email,otp})=>{
  const {data,error}=await client().auth.verifyOtp({
    email,token:otp,type:"email"
  });
  if(error||!data?.user)return null;
  return {id:data.user.id,email:data.user.email};
 },
 enroll:async({userId,deviceId,signingKeyHex,encryptionKeyHex,replace})=>
  rpc({p_user:userId,p_device:deviceId,
      p_signing_raw:"\\x"+signingKeyHex,
      p_encryption_raw:"\\x"+encryptionKeyHex,p_replace:replace})
});
Deno.serve(gateway);
