import com.jruiz.nexo.core.*;
import com.jruiz.nexo.core.title.AppointmentTitleEngine;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Frozen V8.1 output contract plus non-title invariants of production capture. */
public final class IntegratedTitleSelfTest {
    // Pre-integration appointment contract, retained only as an independent regression oracle.
    private static RuleClassifier.Result baseline(String raw) {
        String text=raw==null?"":raw.trim();
        var r=RuleClassifier.classify(text);r.type="event";
        if("Tareas".equals(r.category)||"Recordatorios".equals(r.category))r.category="Personal";
        if(r.dueAt<=0&&!r.invalidDate&&!r.invalidTime) {
            r.needsReview=true;
            if(r.reason==null||r.reason.isBlank())r.reason="Falta la fecha de la cita";
        }
        if(r.description.isBlank())r.description=text;
        r.title=LocalTitleEngine.derive(text,"event",r.category,r.locationText,r.description);
        return r;
    }
    public static void main(String[] args) throws Exception {
        var lines=Files.readAllLines(Path.of(args[0]),StandardCharsets.UTF_8);
        int count=0;
        for(String line:lines.subList(1,lines.size())) {
            if(line.isBlank())continue;
            String[] c=line.split("\t",-1);
            var expected=baseline(c[1]);var actual=AppointmentParser.parse(c[1]);
            if(!c[2].equals(actual.title))throw new AssertionError(c[0]+": "+actual.title+" != "+c[2]);
            if(Boolean.parseBoolean(c[3])) {
                expected.needsReview=true;expected.warnings.add(AppointmentTitleEngine.REVIEW_WARNING);
            }
            for(Field f:RuleClassifier.Result.class.getFields()) {
                if(f.getName().equals("title"))continue;
                if(!Objects.equals(f.get(expected),f.get(actual)))
                    throw new AssertionError(c[0]+": non-title field changed: "+f.getName());
            }
            count++;
        }
        if(count!=568)throw new AssertionError("Incomplete frozen corpus: "+count);
        for(String raw:new String[]{null,"","   "}) {
            var r=AppointmentParser.parse(raw);
            if(r.title.isBlank()||!r.needsReview||r.dueAt!=0)throw new AssertionError("Empty input contract");
        }
        System.out.println("Integrated title engine: "+count+" exact titles and all non-title fields preserved; empty inputs OK");
    }
}
