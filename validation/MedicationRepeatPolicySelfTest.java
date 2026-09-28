import com.jruiz.nexo.medications.MedicationRepeatPolicy;

public final class MedicationRepeatPolicySelfTest {
    private static int tests=0;
    private static void eq(long actual,long expected,String label){tests++;if(actual!=expected)throw new AssertionError(label+": expected="+expected+" actual="+actual);}
    private static void ok(boolean value,String label){tests++;if(!value)throw new AssertionError(label);}
    public static void main(String[] args){
        long m=60_000L,s=1_000_000_000L;
        eq(MedicationRepeatPolicy.windowEnd(s),s+20*m,"window end");
        ok(MedicationRepeatPolicy.withinRepeatWindow(s,s),"at dose time");
        ok(MedicationRepeatPolicy.withinRepeatWindow(s,s+20*m),"+20 inside");
        ok(!MedicationRepeatPolicy.withinRepeatWindow(s,s+20*m+1),"after +20 outside");
        eq(MedicationRepeatPolicy.nextRepeatAt(s,s-5*m),s,"-5 -> 0");
        eq(MedicationRepeatPolicy.nextRepeatAt(s,s),s+5*m,"0 -> +5");
        eq(MedicationRepeatPolicy.nextRepeatAt(s,s+5*m),s+10*m,"+5 -> +10");
        eq(MedicationRepeatPolicy.nextRepeatAt(s,s+10*m),s+15*m,"+10 -> +15");
        eq(MedicationRepeatPolicy.nextRepeatAt(s,s+15*m),s+20*m,"+15 -> +20");
        eq(MedicationRepeatPolicy.nextRepeatAt(s,s+20*m),0,"+20 -> fin");
        eq(MedicationRepeatPolicy.snoozeAt(s,s,10),0,"snooze disabled");
        System.out.println("MedicationRepeatPolicy: "+tests+" tests OK");
    }
}
