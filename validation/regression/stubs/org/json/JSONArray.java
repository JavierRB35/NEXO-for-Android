package org.json;
import java.util.*;
public class JSONArray {
  private final List<Object> a=new ArrayList<>();
  public JSONArray(){}
  public JSONArray(Collection<?> c){if(c!=null)a.addAll(c);}
  public JSONArray put(Object v){a.add(v);return this;}
  public int length(){return a.size();}
  public Object get(int i){return a.get(i);}
  public String optString(int i){return optString(i,"");}
  public String optString(int i,String d){if(i<0||i>=a.size()||a.get(i)==null)return d;return String.valueOf(a.get(i));}
  public int optInt(int i,int d){if(i<0||i>=a.size())return d;Object v=a.get(i);if(v instanceof Number)return ((Number)v).intValue();try{return Integer.parseInt(String.valueOf(v));}catch(Exception e){return d;}}
  public long optLong(int i,long d){if(i<0||i>=a.size())return d;Object v=a.get(i);if(v instanceof Number)return ((Number)v).longValue();try{return Long.parseLong(String.valueOf(v));}catch(Exception e){return d;}}
  public long optLong(int i){return optLong(i,0);}
  public JSONObject optJSONObject(int i){if(i<0||i>=a.size())return null;Object v=a.get(i);return v instanceof JSONObject?(JSONObject)v:null;}
  public String toString(){return a.toString();}
}
