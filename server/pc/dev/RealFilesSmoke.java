import ua.museclass.musicxml.*; import java.nio.file.*; import java.util.*;
public class RealFilesSmoke { public static void main(String[] a) throws Exception {
  String x="<?xml version=\"1.0\"?>\n<!DOCTYPE score-partwise [<!ENTITY xxe SYSTEM \"file:///etc/hostname\">]>\n<score-partwise><work><work-title>&xxe;</work-title></work><part-list><score-part id=\"P1\"><part-name>P</part-name></score-part></part-list><part id=\"P1\"><measure/></part></score-partwise>";
  try { System.out.println("XXE title=["+MusicXmlInspector.inspect(x.getBytes()).title()+"]"); } catch(MusicXmlException e){ System.out.println("XXE rejected: "+e.getMessage()+" / "+e.getCause()); }
  int ok=0; Map<String,Integer> errs=new TreeMap<>(); Map<String,Integer> instr=new TreeMap<>(); int unknown=0, parts=0, noTitle=0;
  List<String> samples=new ArrayList<>();
  try (var st=Files.list(Paths.get(a[0]))) { for (Path p: st.sorted().toList()) { String n=p.getFileName().toString().toLowerCase();
    if(!(n.endsWith(".xml")||n.endsWith(".musicxml")||n.endsWith(".mxl"))) continue;
    try { var i=MusicXmlInspector.inspect(Files.readAllBytes(p)); ok++; if(i.title()==null) noTitle++;
      for (var pi: i.parts()) { parts++; if(pi.instrument()==null){unknown++; if(samples.size()<40) samples.add(pi.name()+" | "+pi.instrumentName()+" | "+pi.midiProgram());} else instr.merge(pi.instrument(),1,Integer::sum);} }
    catch(MusicXmlException e){ errs.merge(e.getMessage(),1,Integer::sum); System.out.println("ERR "+p.getFileName()+": "+e.getMessage()+" / "+e.getCause()); } } }
  System.out.println("ok="+ok+" noTitle="+noTitle+" parts="+parts+" unknownInstr="+unknown+" "+instr); System.out.println(errs); samples.forEach(s->System.out.println("  ? "+s)); } }
