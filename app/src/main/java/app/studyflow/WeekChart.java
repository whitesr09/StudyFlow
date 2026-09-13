package app.studyflow;
import android.content.Context;
import android.graphics.*;
import android.view.View;
import android.animation.ValueAnimator;
import java.time.LocalDate;
import java.util.*;
/** Seven real daily totals, rendered as a restrained animated chart. */
final class WeekChart extends View {
    java.util.function.Consumer<LocalDate> onDay;
    private float downX,downY;
    @Override public boolean performClick(){super.performClick();return true;}
    @Override public boolean onTouchEvent(android.view.MotionEvent event){if(event.getActionMasked()==android.view.MotionEvent.ACTION_DOWN){downX=event.getX();downY=event.getY();return true;}if(event.getActionMasked()==android.view.MotionEvent.ACTION_UP){if(Math.abs(event.getX()-downX)<20&&Math.abs(event.getY()-downY)<20){performClick();int index=Math.max(0,Math.min(6,(int)(event.getX()/(getWidth()/7f))));if(onDay!=null)onDay.accept(LocalDate.now().minusDays(6-index));}return true;}return true;}
    private final Paint p=new Paint(3);private final int[] values=new int[7];private final int ink,accent,line;private final boolean reduced;private float progress=1;private ValueAnimator animation;
    WeekChart(Context context,Map<LocalDate,Integer> days,int ink,int accent,int line,boolean reduced){super(context);this.ink=ink;this.accent=accent;this.line=line;this.reduced=reduced;StringBuilder description=new StringBuilder("Logged study minutes. ");for(int i=0;i<7;i++){LocalDate day=LocalDate.now().minusDays(6-i);values[i]=days.getOrDefault(day,0);description.append(day).append(": ").append(values[i]).append(" minutes. ");}setContentDescription(description);}
    protected void onAttachedToWindow(){super.onAttachedToWindow();if(!reduced){animation=ValueAnimator.ofFloat(0,1);animation.setDuration(500);animation.addUpdateListener(a->{progress=(float)a.getAnimatedValue();invalidate();});animation.start();}}
    protected void onDetachedFromWindow(){if(animation!=null)animation.cancel();super.onDetachedFromWindow();}
    protected void onDraw(Canvas c){super.onDraw(c);float d=getResources().getDisplayMetrics().density,w=getWidth()/7f,h=getHeight()-30*d;int max=1;for(int v:values)max=Math.max(max,v);p.setStrokeWidth(d);p.setColor(line);for(int i=0;i<3;i++)c.drawLine(0,h*i/2,getWidth(),h*i/2,p);for(int i=0;i<7;i++){float x=w*(i+.5f),top=h-18*d-(h-36*d)*values[i]/max*progress;p.setColor(accent);p.setStyle(Paint.Style.FILL);if(values[i]>0)c.drawRoundRect(x-6*d,top,x+6*d,h,4*d,4*d,p);p.setColor(ink);p.setTextAlign(Paint.Align.CENTER);p.setTextSize(10*getResources().getDisplayMetrics().scaledDensity);c.drawText(String.valueOf(values[i]),x,Math.max(12*d,top-6*d),p);c.drawText(LocalDate.now().minusDays(6-i).format(java.time.format.DateTimeFormatter.ofPattern("EEE",Locale.getDefault())),x,h+20*d,p);}}
}
