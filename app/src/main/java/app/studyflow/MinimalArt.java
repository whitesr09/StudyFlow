package app.studyflow;
import android.content.Context;
import android.graphics.*;
import android.view.View;
/** Original vector study illustration, recolored from the exact chosen accent. */
final class MinimalArt extends View {
    private final Paint p=new Paint(3);private final int ink,accent,line;
    MinimalArt(Context c,int ink,int accent,int line){super(c);this.ink=ink;this.accent=accent;this.line=line;setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);}
    protected void onDraw(Canvas canvas){super.onDraw(canvas);canvas.save();canvas.scale(getWidth()/120f,getHeight()/120f);p.setStyle(Paint.Style.FILL);p.setColor(accent);canvas.drawCircle(77,43,30,p);p.setColor(line);canvas.drawCircle(23,26,4,p);p.setColor(ink);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(1.6f);canvas.drawOval(15,90,110,105,p);Path book=new Path();book.moveTo(18,45);book.lineTo(53,52);book.lineTo(91,42);book.lineTo(91,83);book.lineTo(53,95);book.lineTo(18,85);book.close();canvas.drawPath(book,p);canvas.drawLine(53,52,53,95,p);for(int i=0;i<4;i++){canvas.drawLine(26,57+i*7,45,61+i*7,p);canvas.drawLine(62,59+i*7,83,53+i*7,p);}canvas.drawLine(10,14,10,24,p);canvas.drawLine(5,19,15,19,p);canvas.restore();}
}
