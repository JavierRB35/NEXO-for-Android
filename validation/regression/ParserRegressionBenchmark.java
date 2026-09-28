package com.jruiz.nexo.core;
import java.io.*;import java.nio.charset.StandardCharsets;import java.nio.file.*;import java.time.*;import java.util.*;
import org.json.*;
public final class ParserRegressionBenchmark {
  static final LocalDateTime NOW=LocalDateTime.of(2026,8,23,17,15);
  static class Stats{int total,ok;Map<String,int[]> by=new LinkedHashMap<>();Map<String,int[]> split=new LinkedHashMap<>();}
  public static void main(String[] args)throws Exception{
    Path p=Paths.get(args.length>0?args[0]:"validation/regression/data/cases.tsv");
    List<String> lines=Files.readAllLines(p,StandardCharsets.UTF_8);Stats st=new Stats();List<String> fails=new ArrayList<>();
    int lineNo=0;for(String line:lines){lineNo++;if(line.isBlank()||line.startsWith("#"))continue;String[] c=line.split("\\t",-1);if(c.length<5)throw new IllegalArgumentException("TSV inválido línea "+lineNo);String split=c[0],kind=c[1],input=c[2],expected=c[3],note=c[4];String actual=eval(kind,input);boolean ok=expected.equals(actual);st.total++;if(ok)st.ok++;st.by.computeIfAbsent(kind,k->new int[2])[1]++;if(ok)st.by.get(kind)[0]++;st.split.computeIfAbsent(split,k->new int[2])[1]++;if(ok)st.split.get(split)[0]++;if(!ok)fails.add(kind+" | "+split+" | "+input+" | esperado=["+expected+"] obtenido=["+actual+"] | "+note);}
    System.out.printf(Locale.ROOT,"TOTAL %d/%d = %.2f%%\n",st.ok,st.total,100.0*st.ok/st.total);
    for(var e:st.split.entrySet())System.out.printf(Locale.ROOT,"SPLIT %-8s %d/%d = %.2f%%\n",e.getKey(),e.getValue()[0],e.getValue()[1],100.0*e.getValue()[0]/e.getValue()[1]);
    for(var e:st.by.entrySet())System.out.printf(Locale.ROOT,"%-10s %d/%d = %.2f%%\n",e.getKey(),e.getValue()[0],e.getValue()[1],100.0*e.getValue()[0]/e.getValue()[1]);
    if(!fails.isEmpty()){System.out.println("--- FALLOS (máx 80) ---");for(int i=0;i<Math.min(80,fails.size());i++)System.out.println(fails.get(i));}
    int[] target=st.split.get("holdout");
    if(target==null) target=st.split.get("blind");
    if(target!=null){double score=100.0*target[0]/target[1];if(score<90.0)System.exit(2);}
  }
  static String eval(String kind,String input)throws Exception{
    switch(kind){
      case "TEMP":{TemporalParser.Result r=TemporalParser.parse(input,NOW);return (r.hasDate?r.dateIso():"")+"|"+(r.hasTime?r.timeIso():"");}
      case "LOC":return LocationParser.extract(input);
      case "ROUTE":return UserMessageInterpreter.route(input).optString("intent","");
      case "CLASS":{RuleClassifier.Result r=RuleClassifier.classify(input);return r.type+"|"+r.category;}
      case "REC":return RecurrenceEngine.detect(input).recurrence;
      case "LIST":{JSONObject o=ListVoiceParser.parse(input);JSONArray a=o.optJSONArray("items");List<String>x=new ArrayList<>();if(a!=null)for(int i=0;i<a.length();i++)x.add(a.optString(i));return o.optString("action","")+"|"+String.join(";",x);}
      case "ALARM":{JSONObject o=AlarmIntentParser.parse(input,NOW);return o.optBoolean("isAlarm",false)+"|"+o.optBoolean("valid",false)+"|"+o.optString("time","")+"|"+o.optBoolean("repeating",false);}
      case "MED":{boolean like=MedicationParser.looksLikeMedication(input);JSONObject o=MedicationParser.parse(input);return like+"|"+o.optString("name","")+"|"+o.optString("dose","");}
      default:throw new IllegalArgumentException("kind "+kind);
    }
  }
}
