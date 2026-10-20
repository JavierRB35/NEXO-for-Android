// Supabase Edge Function, read-only authentication and challenge service.
// No delivery, invitation mutation or device replacement is enabled.
import {createClient} from "npm:@supabase/supabase-js@2";
import {createGateway} from "./gateway.mjs";
const url=Deno.env.get("SUPABASE_URL");
const anon=Deno.env.get("SUPABASE_ANON_KEY");
const service=Deno.env.get("SUPABASE_SERVICE_ROLE_KEY");
if(!url||!anon||!service)throw Error("Required Supabase server config missing");
const auth=createClient(url,anon,{auth:{persistSession:false,autoRefreshToken:false}});
const admin=createClient(url,service,{auth:{persistSession:false,autoRefreshToken:false}});
const gateway=createGateway({
  authenticate:async token=>{
    const {data,error}=await auth.auth.getUser(token);
    if(error||!data?.user)return null;
    return {id:data.user.id};
  },
  getDevice:async userId=>{
    const {data,error}=await admin.rpc("nexo_connect_device_status",{p_user:userId});
    if(error)throw Error("device-lookup-failed");
    return Array.isArray(data)?data[0]??null:null;
  },
  issueChallenge:async({userId,deviceId,generation,action})=>{
    const {data,error}=await admin.rpc("nexo_connect_issue_challenge",{
      p_user:userId,p_device:deviceId,p_generation:generation,p_action:action
    });
    if(error)throw Error("issue-challenge-failed");
    return data;
  }
});
Deno.serve(gateway);
