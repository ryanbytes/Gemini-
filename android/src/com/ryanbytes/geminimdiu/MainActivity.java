package com.ryanbytes.geminimdiu;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

public class MainActivity extends Activity {
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN |
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
        setContentView(new MDIUView(this));
    }

    static final class MDIUView extends View {
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final List<Hit> hits = new ArrayList<>();
        private final Handler handler = new Handler(Looper.getMainLooper());
        private final MDIUModel model;
        private int activeAction = -1;
        private int readGeneration = 0;
        private int displayMode = MODE_MDIU;
        private boolean dimmed = false;
        private float sx = 1f, sy = 1f, ox = 0f, oy = 0f;

        private static final int PWR=100, READ=101, CLEAR=102, ENTER=103;
        private static final int MODE_MDIU=110, MODE_CLOCK=111, MODE_DREAM=112, DIM=113;
        private static final int W = 1200, H = 720;
        private static final String[] DREAM_PATTERNS = {
                "1234567", "7654321", "0123456", "9876543", "2468135", "1357924", "0000000"
        };

        private final Runnable ticker = new Runnable() {
            @Override public void run() {
                if (displayMode != MODE_MDIU) invalidate();
                handler.postDelayed(this, 100L);
            }
        };

        MDIUView(Context context) {
            super(context);
            setBackgroundColor(Color.rgb(20,20,19));
            setFocusable(true);
            setFocusableInTouchMode(true);
            SharedPreferences prefs = context.getSharedPreferences("gemini_mdiu", Context.MODE_PRIVATE);
            model = new MDIUModel(new MDIUModel.Store() {
                @Override public String get(String address) { return prefs.getString("addr_" + address, "00000"); }
                @Override public void put(String address, String message) { prefs.edit().putString("addr_" + address, message).apply(); }
            });
            stroke.setStyle(Paint.Style.STROKE);
            stroke.setStrokeWidth(2f);
            handler.post(ticker);
        }

        private int c(String hex) { return Color.parseColor(hex); }
        private float x(float v){ return ox + v*sx; }
        private float y(float v){ return oy + v*sy; }
        private RectF r(float l,float t,float rr,float b){ return new RectF(x(l),y(t),x(rr),y(b)); }

        @Override protected void onDetachedFromWindow() {
            handler.removeCallbacksAndMessages(null);
            super.onDetachedFromWindow();
        }

        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float scale = Math.min(getWidth()/(float)W, getHeight()/(float)H);
            sx = sy = scale;
            ox = (getWidth()-W*scale)/2f;
            oy = (getHeight()-H*scale)/2f;
            canvas.drawColor(c("#151514"));
            hits.clear();
            drawBackdrop(canvas);
            drawMdr(canvas);
            drawMdk(canvas);
            drawModeBar(canvas);
            if (dimmed) {
                p.setStyle(Paint.Style.FILL);
                p.setColor(Color.argb(180, 0, 0, 0));
                canvas.drawRect(0,0,getWidth(),getHeight(),p);
            }
        }

        private void drawBackdrop(Canvas canvas) {
            p.setStyle(Paint.Style.FILL); p.setColor(c("#262623"));
            canvas.drawRoundRect(r(10,20,1190,630), 18*sx,18*sy,p);
            p.setColor(c("#0a0a09"));
            stroke.setColor(c("#3f3f39"));
            canvas.drawRoundRect(r(25,35,1175,615), 12*sx,12*sy,stroke);
        }

        private String displayText() {
            if (displayMode == MODE_CLOCK) {
                Calendar d = Calendar.getInstance();
                return String.format(Locale.US, "%02d%02d%02d%d",
                        d.get(Calendar.HOUR_OF_DAY), d.get(Calendar.MINUTE), d.get(Calendar.SECOND),
                        d.get(Calendar.MILLISECOND)/100);
            }
            if (displayMode == MODE_DREAM) {
                int i = (int)((System.currentTimeMillis()/1200L) % DREAM_PATTERNS.length);
                return DREAM_PATTERNS[i];
            }
            return model.getDisplay();
        }

        private void drawMdr(Canvas canvas) {
            RectF panel = r(65,145,760,520);
            p.setStyle(Paint.Style.FILL); p.setColor(c("#10100f"));
            canvas.drawRoundRect(panel, 12*sx,12*sy,p);
            stroke.setColor(c("#6e6e65")); stroke.setStrokeWidth(3*sx);
            canvas.drawRoundRect(panel,12*sx,12*sy,stroke);
            screw(canvas,82,163); screw(canvas,742,163); screw(canvas,82,502); screw(canvas,742,502);

            text(canvas,"ADDRESS",215,195,27,c("#e6dfc9"),Paint.Align.CENTER,false);
            text(canvas,"MESSAGE",505,195,27,c("#e6dfc9"),Paint.Align.CENTER,false);

            p.setColor(Color.BLACK); p.setStyle(Paint.Style.FILL);
            canvas.drawRect(r(130,212,695,330),p);
            stroke.setColor(c("#5d5d57")); stroke.setStrokeWidth(2*sx); canvas.drawRect(r(130,212,695,330),stroke);

            String d = displayText();
            float dw=68, gap=4, start=148;
            for(int i=0;i<7;i++) {
                float left=start+i*(dw+gap)+(i>=2?8:0);
                RectF win=r(left,229,left+dw,314);
                p.setColor(c("#151515")); canvas.drawRect(win,p);
                stroke.setColor(c("#4a4a46")); stroke.setStrokeWidth(1*sx); canvas.drawRect(win,stroke);
                int alpha=(model.isPowered() || displayMode != MODE_MDIU)?255:110;
                int col=Color.argb(alpha,238,238,232);
                text(canvas,String.valueOf(d.charAt(i)),left+dw/2,300,66,col,Paint.Align.CENTER,true);
            }
            p.setColor(c("#b3ad9d")); canvas.drawRect(r(294,223,297,320),p);

            drawMultiline(canvas,"P\nW\nR",105,399,17,c("#ded8c4"));
            text(canvas,"ON",198,372,18,c("#ded8c4"),Paint.Align.CENTER,false);
            text(canvas,"OFF",198,460,18,c("#ded8c4"),Paint.Align.CENTER,false);
            p.setColor(c("#676760")); canvas.drawOval(r(143,380,188,445),p);
            p.setColor(c("#bfbdb2"));
            float ly = model.isPowered()?388:427;
            canvas.drawRoundRect(r(161,ly,171,ly+50),5*sx,5*sy,p);
            addHit(PWR,135,365,205,466);

            command(canvas,READ,"READ\nOUT",250,375,365,475);
            command(canvas,CLEAR,"CLEAR",405,375,520,475);
            command(canvas,ENTER,"ENTER",560,375,675,475);
        }

        private void command(Canvas canvas,int action,String label,float l,float t,float rr,float b) {
            boolean down=activeAction==action;
            RectF shadow=r(l,t+7,rr,b+7); p.setColor(c("#60605a")); canvas.drawRoundRect(shadow,7*sx,7*sy,p);
            RectF face=r(l,t+(down?5:0),rr,b+(down?5:0)); p.setColor(c("#e6e3d8")); canvas.drawRoundRect(face,7*sx,7*sy,p);
            stroke.setColor(c("#77776f")); stroke.setStrokeWidth(2*sx); canvas.drawRoundRect(face,7*sx,7*sy,stroke);
            drawMultiline(canvas,label,(l+rr)/2,(t+b)/2+(label.contains("\n")?-11:8)+(down?5:0),22,c("#151515"));
            addHit(action,l,t,rr,b+8);
        }

        private void drawMdk(Canvas canvas) {
            RectF panel=r(820,85,1125,565); p.setColor(c("#0d0d0c")); canvas.drawRoundRect(panel,10*sx,10*sy,p);
            stroke.setColor(c("#707068")); stroke.setStrokeWidth(3*sx); canvas.drawRoundRect(panel,10*sx,10*sy,stroke);
            screw(canvas,838,103); screw(canvas,1107,103); screw(canvas,838,547); screw(canvas,1107,547);
            int[][] nums={{7,8,9},{4,5,6},{1,2,3}};
            float startX=855,startY=130,kw=72,kh=82,gx=14,gy=18;
            for(int row=0;row<3;row++) for(int col=0;col<3;col++) {
                int digit=nums[row][col];
                key(canvas,digit,String.valueOf(digit),startX+col*(kw+gx),startY+row*(kh+gy),kw,kh);
            }
            key(canvas,0,"ZERO",855,430,244,83);
        }

        private void key(Canvas canvas,int action,String label,float l,float t,float ww,float hh) {
            boolean down=activeAction==action;
            p.setColor(c("#60605a")); canvas.drawRoundRect(r(l,t+7,l+ww,t+hh+7),6*sx,6*sy,p);
            p.setColor(c("#e8e5da")); RectF face=r(l,t+(down?5:0),l+ww,t+hh+(down?5:0)); canvas.drawRoundRect(face,6*sx,6*sy,p);
            stroke.setColor(c("#7a7a73")); stroke.setStrokeWidth(2*sx); canvas.drawRoundRect(face,6*sx,6*sy,stroke);
            text(canvas,label,l+ww/2,t+hh*.68f+(down?5:0),label.equals("ZERO")?30:43,c("#111111"),Paint.Align.CENTER,false);
            addHit(action,l,t,l+ww,t+hh+8);
        }

        private void drawModeBar(Canvas canvas) {
            modeButton(canvas, MODE_MDIU, "MDIU", 330, 650, 445, 700, displayMode==MODE_MDIU);
            modeButton(canvas, MODE_CLOCK, "CLOCK", 455, 650, 570, 700, displayMode==MODE_CLOCK);
            modeButton(canvas, MODE_DREAM, "DREAM", 580, 650, 695, 700, displayMode==MODE_DREAM);
            modeButton(canvas, DIM, "DIM", 705, 650, 820, 700, dimmed);
        }

        private void modeButton(Canvas canvas,int action,String label,float l,float t,float rr,float b,boolean selected) {
            boolean down=activeAction==action;
            p.setStyle(Paint.Style.FILL); p.setColor(selected?c("#34342f"):c("#1b1b19"));
            RectF face=r(l,t+(down?2:0),rr,b+(down?2:0)); canvas.drawRoundRect(face,5*sx,5*sy,p);
            stroke.setColor(selected?c("#989487"):c("#4e4e49")); stroke.setStrokeWidth(1.5f*sx); canvas.drawRoundRect(face,5*sx,5*sy,stroke);
            text(canvas,label,(l+rr)/2,(t+b)/2+6+(down?2:0),14,selected?c("#eee8d7"):c("#a9a79d"),Paint.Align.CENTER,false);
            addHit(action,l,t,rr,b);
        }

        private void screw(Canvas canvas,float cx,float cy) {
            p.setColor(c("#8b8b83")); canvas.drawCircle(x(cx),y(cy),9*sx,p);
            stroke.setColor(c("#282826")); stroke.setStrokeWidth(2*sx); canvas.drawLine(x(cx-6),y(cy+1),x(cx+6),y(cy-1),stroke);
        }

        private void text(Canvas canvas,String s,float xx,float baseline,float size,int color,Paint.Align align,boolean mono) {
            p.setColor(color); p.setTextSize(size*sx); p.setTextAlign(align); p.setStyle(Paint.Style.FILL); p.setTypeface(mono?android.graphics.Typeface.MONOSPACE:android.graphics.Typeface.DEFAULT);
            canvas.drawText(s,x(xx),y(baseline),p);
        }

        private void drawMultiline(Canvas canvas,String s,float xx,float centerY,float size,int color) {
            String[] lines=s.split("\\n"); float step=size*1.05f; float first=centerY-(lines.length-1)*step/2f;
            for(int i=0;i<lines.length;i++) text(canvas,lines[i],xx,first+i*step,size,color,Paint.Align.CENTER,false);
        }

        private void addHit(int action,float l,float t,float rr,float b){ hits.add(new Hit(action,r(l,t,rr,b))); }

        @Override public boolean onTouchEvent(MotionEvent event) {
            if(event.getAction()==MotionEvent.ACTION_DOWN) {
                activeAction=findAction(event.getX(),event.getY());
                if(activeAction>=0) { performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP); invalidate(); return true; }
            } else if(event.getAction()==MotionEvent.ACTION_UP) {
                int action=findAction(event.getX(),event.getY());
                int was=activeAction; activeAction=-1; invalidate();
                if(action>=0 && action==was) invoke(action);
                return true;
            } else if(event.getAction()==MotionEvent.ACTION_CANCEL) { activeAction=-1; invalidate(); return true; }
            return true;
        }

        private int findAction(float px,float py){ for(Hit h:hits) if(h.rect.contains(px,py)) return h.action; return -1; }

        private void useMDIU() { displayMode = MODE_MDIU; }

        private void invoke(int action) {
            long now=SystemClock.uptimeMillis();
            if(action==MODE_MDIU) { displayMode=MODE_MDIU; invalidate(); return; }
            if(action==MODE_CLOCK) { readGeneration++; displayMode=MODE_CLOCK; invalidate(); return; }
            if(action==MODE_DREAM) { readGeneration++; displayMode=MODE_DREAM; invalidate(); return; }
            if(action==DIM) { dimmed=!dimmed; invalidate(); return; }

            useMDIU();
            if(action>=0 && action<=9) {
                if(model.pressDigit(action,now)) invalidate();
            } else if(action==PWR) {
                readGeneration++; model.togglePower(); invalidate();
            } else if(action==CLEAR) {
                readGeneration++; model.clear(now); invalidate();
            } else if(action==ENTER) {
                readGeneration++; model.enter(now); invalidate();
            } else if(action==READ) {
                int gen=++readGeneration;
                String[] result=model.readOut(now); invalidate();
                if(result!=null) {
                    String message=result[1];
                    for(int i=0;i<5;i++) {
                        final int idx=i; final char ch=message.charAt(i);
                        handler.postDelayed(() -> { if(gen==readGeneration && model.isPowered() && displayMode==MODE_MDIU) { model.setReadoutDigit(idx+2,ch); invalidate(); } }, (i+1)*500L);
                    }
                }
            }
        }

        private static final class Hit { final int action; final RectF rect; Hit(int action,RectF rect){this.action=action;this.rect=rect;} }
    }
}
