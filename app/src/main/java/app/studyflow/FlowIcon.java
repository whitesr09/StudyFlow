package app.studyflow;

import android.graphics.*;
import android.graphics.drawable.Drawable;

/** Resolution-independent line icons, with accessible names supplied by their buttons. */
final class FlowIcon extends Drawable {
    private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);private final String name;
    FlowIcon(String name,int color){this.name=name;p.setColor(color);p.setStrokeWidth(1.8f);p.setStrokeCap(Paint.Cap.ROUND);p.setStrokeJoin(Paint.Join.ROUND);}
    @Override public void draw(Canvas canvas){canvas.save();Rect b=getBounds();canvas.translate(b.left,b.top);canvas.scale(b.width()/24f,b.height()/24f);p.setStyle(Paint.Style.STROKE);
        switch(name){
            case "settings":
                Path gear=new Path();for(int i=0;i<32;i++){double a=i*Math.PI/16;float r=i%4==0||i%4==3?10:8;float x=12+(float)Math.cos(a)*r,y=12+(float)Math.sin(a)*r;if(i==0)gear.moveTo(x,y);else gear.lineTo(x,y);}gear.close();canvas.drawPath(gear,p);canvas.drawCircle(12,12,3.2f,p);break;
            case "instagram":
                canvas.drawRoundRect(3,3,21,21,5,5,p);canvas.drawCircle(12,12,4,p);p.setStyle(Paint.Style.FILL);canvas.drawCircle(17.5f,6.5f,1.1f,p);break;
            case "whatsapp":
                Path bubble=new Path();bubble.moveTo(4,17);bubble.cubicTo(-1,8,6,1,13,2);bubble.cubicTo(23,2,26,16,17,21);bubble.cubicTo(12,24,8,21,7,21);bubble.lineTo(2,22);bubble.close();canvas.drawPath(bubble,p);
                Path phone=new Path();phone.moveTo(8,6);phone.lineTo(6.5f,8);phone.cubicTo(6,13,12,18,16,17);phone.lineTo(18,15);phone.lineTo(14.5f,13);phone.lineTo(13,14);phone.cubicTo(11,13,10,12,9.5f,10);phone.lineTo(10.5f,9);phone.close();canvas.drawPath(phone,p);break;
            case "back":canvas.drawLine(19,12,5,12,p);canvas.drawLine(5,12,11,6,p);canvas.drawLine(5,12,11,18,p);break;
            default:p.setStyle(Paint.Style.FILL);for(int i=0;i<3;i++)canvas.drawCircle(12,5+i*7,1.8f,p);
        }canvas.restore();
    }
    @Override public void setAlpha(int alpha){p.setAlpha(alpha);invalidateSelf();}
    @Override public void setColorFilter(ColorFilter filter){p.setColorFilter(filter);invalidateSelf();}
    @Override public int getOpacity(){return PixelFormat.TRANSLUCENT;}
}
