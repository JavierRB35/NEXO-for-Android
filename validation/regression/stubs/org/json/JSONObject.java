package org.json;
import java.util.*;
public class JSONObject {
  private final Map<String,Object> m=new LinkedHashMap<>();
  public JSONObject(){}
  // This minimal QA stub supports object access, not JSON text parsing.
  public JSONObject(String raw){throw new UnsupportedOperationException("JSON text parsing is outside this fixture");}
  public int length(){return m.size();}
  public Iterator<String> keys(){return m.keySet().iterator();}
  public JSONObject put(String k,Object v){m.put(k,v);return this;}
  public boolean has(String k){return m.containsKey(k);}
  public Object get(String k){return m.get(k);}
  public String optString(String k){return optString(k,"");}
  public String optString(String k,String d){Object v=m.get(k);return v==null?d:String.valueOf(v);}
  public int optInt(String k){return optInt(k,0);} public int optInt(String k,int d){Object v=m.get(k);if(v instanceof Number)return ((Number)v).intValue();try{return v==null?d:Integer.parseInt(String.valueOf(v));}catch(Exception e){return d;}}
  public long optLong(String k){return optLong(k,0);} public long optLong(String k,long d){Object v=m.get(k);if(v instanceof Number)return ((Number)v).longValue();try{return v==null?d:Long.parseLong(String.valueOf(v));}catch(Exception e){return d;}}
  public double optDouble(String k,double d){Object v=m.get(k);if(v instanceof Number)return ((Number)v).doubleValue();try{return v==null?d:Double.parseDouble(String.valueOf(v));}catch(Exception e){return d;}}
  public boolean optBoolean(String k){return optBoolean(k,false);} public boolean optBoolean(String k,boolean d){Object v=m.get(k);if(v instanceof Boolean)return (Boolean)v;if(v==null)return d;return Boolean.parseBoolean(String.valueOf(v));}
  public JSONArray optJSONArray(String k){Object v=m.get(k);return v instanceof JSONArray?(JSONArray)v:null;}
  public JSONObject optJSONObject(String k){Object v=m.get(k);return v instanceof JSONObject?(JSONObject)v:null;}
  public String toString(){return m.toString();}
}
