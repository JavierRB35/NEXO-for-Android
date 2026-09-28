package com.jruiz.nexo.medications;

/** Política fija: -5, 0, +5, +10, +15 y +20 min; después, fin. */
public final class MedicationRepeatPolicy {
    private MedicationRepeatPolicy() {}
    public static final int REPEAT_MINUTES=5;
    public static final int MAX_AFTER_MINUTES=20;
    private static final long MINUTE_MS=60_000L;
    public static long windowEnd(long scheduledAt){return scheduledAt<=0?0:scheduledAt+MAX_AFTER_MINUTES*MINUTE_MS;}
    public static boolean withinRepeatWindow(long scheduledAt,long now){return scheduledAt>0&&now<=windowEnd(scheduledAt);}
    /** Siguiente slot fijo posterior al momento actual: 0,+5,+10,+15,+20. */
    public static long nextRepeatAt(long scheduledAt,long now){
        if(scheduledAt<=0)return 0;
        int[] offsets={0,5,10,15,20};
        for(int min:offsets){long at=scheduledAt+min*MINUTE_MS;if(at>now+500)return at;}
        return 0;
    }
    public static long snoozeAt(long scheduledAt,long now,int minutes){return 0;}
}
