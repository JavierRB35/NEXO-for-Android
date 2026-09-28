package com.jruiz.nexo.reminders;
import android.content.Context;
import java.util.*;
/** Captures service scheduling requests; does not exercise AlarmManager. */
public final class ReminderScheduler {
    public static final Map<Long,Long> scheduled = new LinkedHashMap<>();
    public static final List<Long> cancelled = new ArrayList<>();
    public static void schedule(Context c,long id,String title,long at) { scheduled.put(id,at); }
    public static void cancel(Context c,long id) { cancelled.add(id); scheduled.remove(id); }
    public static void reset() { scheduled.clear(); cancelled.clear(); }
}
