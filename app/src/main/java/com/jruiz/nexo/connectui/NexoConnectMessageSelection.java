package com.jruiz.nexo.connectui;

import android.app.Activity;
import android.graphics.Color;
import android.os.Handler;
import android.os.Looper;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/** Message-selection toolbar: one-second hold, multiple taps, back to cancel. */
final class NexoConnectMessageSelection {
    private final Activity activity;
    private final UUID owner,peer;
    private final Set<String> chosen=new LinkedHashSet<>();
    private final LinearLayout header,toolbar;
    private final TextView counter;
    private final Runnable redraw,onDeleted;
    private int dp(int value){return Math.round(value*activity.getResources().getDisplayMetrics().density);}

    NexoConnectMessageSelection(Activity activity,LinearLayout root,UUID owner,UUID peer,
            Runnable redraw,Runnable onDeleted){
        this.activity=activity;this.owner=owner;this.peer=peer;
        this.redraw=redraw;this.onDeleted=onDeleted;
        header=(LinearLayout)root.getChildAt(0);
        toolbar=new LinearLayout(activity);toolbar.setGravity(android.view.Gravity.CENTER_VERTICAL);
        toolbar.setPadding(dp(8),dp(4),dp(8),dp(4));
        toolbar.setBackgroundColor(Color.WHITE);
        Button back=new Button(activity);back.setText("‹");back.setTextSize(26);
        back.setContentDescription("Cancelar selección");back.setAllCaps(false);
        back.setTextColor(0xff0f766e);
        back.setOnClickListener(v->clear());
        toolbar.addView(back,new LinearLayout.LayoutParams(dp(52),dp(48)));
        counter=new TextView(activity);counter.setTextColor(0xff142337);counter.setTextSize(18);
        counter.setTypeface(null,android.graphics.Typeface.BOLD);
        toolbar.addView(counter,new LinearLayout.LayoutParams(0,-2,1));
        Button trash=new Button(activity);trash.setText("Eliminar");trash.setAllCaps(false);
        trash.setContentDescription("Eliminar para mí los mensajes seleccionados");
        trash.setTextColor(0xffbc2840);
        trash.setOnClickListener(v->deleteSelected());
        toolbar.addView(trash,new LinearLayout.LayoutParams(dp(104),dp(48)));
        root.addView(toolbar,1,new LinearLayout.LayoutParams(-1,-2));
        updateToolbar();
    }

    boolean contains(String id){return chosen.contains(id);}
    boolean active(){return !chosen.isEmpty();}
    void clear(){if(chosen.isEmpty())return;chosen.clear();updateToolbar();redraw.run();}
    private void toggle(String id){
        if(!chosen.add(id))chosen.remove(id);
        updateToolbar();redraw.run();
    }
    private void updateToolbar(){
        boolean active=active();
        header.setVisibility(active?View.GONE:View.VISIBLE);
        toolbar.setVisibility(active?View.VISIBLE:View.GONE);
        if(active)counter.setText(chosen.size()==1?"1 seleccionado":chosen.size()+" seleccionados");
    }
    private void deleteSelected(){
        if(chosen.isEmpty())return;
        ArrayList<String> snapshot=new ArrayList<>(chosen);
        NexoConnectChatDeleteDialogs.confirmMessages(activity,owner,peer,snapshot,()->{
            chosen.clear();updateToolbar();onDeleted.run();
        });
    }

    void bindBubble(LinearLayout bubble,String delivery){
        // The bubble (not a selectable text child) handles the gesture, so
        // there is no conflicting Android text-selection context menu.
        final Handler handler=new Handler(Looper.getMainLooper());
        final float[] start=new float[2];
        final boolean[] moved={false},held={false};
        final Runnable longHold=()->{
            held[0]=true;
            bubble.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS);
            toggle(delivery);
        };
        bubble.setOnLongClickListener(v->{toggle(delivery);return true;});
        bubble.setOnTouchListener((v,e)->{
            switch(e.getActionMasked()){
                case MotionEvent.ACTION_DOWN:
                    start[0]=e.getX();start[1]=e.getY();moved[0]=false;held[0]=false;
                    handler.postDelayed(longHold,1000);return true;
                case MotionEvent.ACTION_MOVE:
                    if(Math.abs(e.getX()-start[0])>dp(12)||Math.abs(e.getY()-start[1])>dp(12)){
                        moved[0]=true;handler.removeCallbacks(longHold);
                    }
                    return !moved[0];
                case MotionEvent.ACTION_UP:
                    handler.removeCallbacks(longHold);
                    if(!moved[0]&&!held[0]&&active())toggle(delivery);
                    return true;
                case MotionEvent.ACTION_CANCEL:
                    handler.removeCallbacks(longHold);return false;
            }
            return false;
        });
    }
}
