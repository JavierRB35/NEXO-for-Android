package com.jruiz.nexo.connectui;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

/**
 * Nexo conversation row. Horizontal swipe reveals a red delete affordance;
 * holding for one second opens the contextual action. Destruction itself is
 * always performed by a separately confirmed, local-only callback.
 */
final class NexoConnectChatRowActions {
    private NexoConnectChatRowActions(){}
    static FrameLayout wrap(Context context, LinearLayout row,
            Runnable open, Runnable options, Runnable requestDelete) {
        final float density=context.getResources().getDisplayMetrics().density;
        final int width=Math.round(112*density), slop=Math.round(12*density);
        FrameLayout frame=new FrameLayout(context);
        frame.setClipChildren(true);
        Button delete=new Button(context);
        delete.setText("🗑 Eliminar");
        delete.setAllCaps(false);
        delete.setTextColor(Color.WHITE);
        GradientDrawable red=new GradientDrawable();
        red.setColor(0xffbc2840);
        red.setCornerRadius(14*density);
        delete.setBackground(red);
        delete.setContentDescription("Eliminar conversación");
        frame.addView(delete,new FrameLayout.LayoutParams(
                width,FrameLayout.LayoutParams.MATCH_PARENT,Gravity.END));
        delete.setOnClickListener(v->requestDelete.run());
        frame.addView(row,new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,FrameLayout.LayoutParams.WRAP_CONTENT));
        final Handler handler=new Handler(Looper.getMainLooper());
        final float[] start={0,0};
        final boolean[] moved={false},held={false};
        final Runnable hold=()->{
            held[0]=true;
            row.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS);
            options.run();
        };
        row.setOnLongClickListener(v->{options.run();return true;});
        row.setOnTouchListener((v,event)->{
            switch(event.getActionMasked()){
                case MotionEvent.ACTION_DOWN:
                    start[0]=event.getX();start[1]=event.getY();
                    moved[0]=false;held[0]=false;
                    handler.postDelayed(hold,1000);
                    return true;
                case MotionEvent.ACTION_MOVE:
                    float x=event.getX()-start[0],y=event.getY()-start[1];
                    if(Math.abs(x)>slop||Math.abs(y)>slop)handler.removeCallbacks(hold);
                    if(Math.abs(x)>slop&&Math.abs(x)>Math.abs(y)){
                        moved[0]=true;
                        v.getParent().requestDisallowInterceptTouchEvent(true);
                        row.setTranslationX(Math.max(-width,Math.min(0,x)));
                        return true;
                    }
                    return false;
                case MotionEvent.ACTION_CANCEL:
                    handler.removeCallbacks(hold);
                    return false;
                case MotionEvent.ACTION_UP:
                    handler.removeCallbacks(hold);
                    if(held[0])return true;
                    if(moved[0]){
                        float target=row.getTranslationX() < -width/2f ? -width:0;
                        row.animate().translationX(target).setDuration(180).start();
                    }else if(row.getTranslationX()!=0){
                        row.animate().translationX(0).setDuration(180).start();
                    }else open.run();
                    return true;
            }
            return false;
        });
        return frame;
    }
}
