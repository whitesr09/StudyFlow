package app.studyflow;
import android.content.Context;
import android.graphics.*;
import android.view.*;
import android.widget.ImageView;

/** Pinch, double-tap, pan and accessible toolbar zoom for document rasters. */
final class ZoomImageView extends ImageView {
    private float zoom=1,tx,ty,lastX,lastY;private boolean scaling;
    private final ScaleGestureDetector pinch;private final GestureDetector taps;
    ZoomImageView(Context context){super(context);setScaleType(ScaleType.FIT_CENTER);setClickable(true);
        pinch=new ScaleGestureDetector(context,new ScaleGestureDetector.SimpleOnScaleGestureListener(){
            @Override public boolean onScaleBegin(ScaleGestureDetector d){scaling=true;getParent().requestDisallowInterceptTouchEvent(true);return true;}
            @Override public boolean onScale(ScaleGestureDetector d){zoomAt(zoom*d.getScaleFactor(),d.getFocusX(),d.getFocusY());return true;}
            @Override public void onScaleEnd(ScaleGestureDetector d){scaling=false;}
        });
        taps=new GestureDetector(context,new GestureDetector.SimpleOnGestureListener(){@Override public boolean onDown(MotionEvent e){return true;}@Override public boolean onDoubleTap(MotionEvent e){zoomAt(zoom>1?1:2,e.getX(),e.getY());return true;}@Override public boolean onSingleTapConfirmed(MotionEvent e){performClick();return true;}});
    }
    boolean isZoomed(){return zoom>1.01f;}
    void zoomBy(float factor){zoomAt(zoom*factor,getWidth()/2f,getHeight()/2f);}
    void resetZoom(){zoomAt(1,0,0);}
    private void zoomAt(float value,float x,float y){float next=Math.max(1,Math.min(4,value)),ratio=next/zoom;tx=x-(x-tx)*ratio;ty=y-(y-ty)*ratio;zoom=next;bound();invalidate();}
    private void bound(){tx=Math.min(0,Math.max(getWidth()*(1-zoom),tx));ty=Math.min(0,Math.max(getHeight()*(1-zoom),ty));}
    @Override public void setImageBitmap(Bitmap b){super.setImageBitmap(b);zoom=1;tx=ty=0;}
    @Override protected void onSizeChanged(int w,int h,int oldw,int oldh){super.onSizeChanged(w,h,oldw,oldh);bound();}
    @Override protected void onDraw(Canvas c){c.save();c.translate(tx,ty);c.scale(zoom,zoom);super.onDraw(c);c.restore();}
    @Override public boolean onTouchEvent(MotionEvent e){
        if(e.getActionMasked()==MotionEvent.ACTION_DOWN){lastX=e.getX();lastY=e.getY();getParent().requestDisallowInterceptTouchEvent(isZoomed());}
        if(e.getPointerCount()>1)getParent().requestDisallowInterceptTouchEvent(true);
        pinch.onTouchEvent(e);taps.onTouchEvent(e);
        if(e.getActionMasked()==MotionEvent.ACTION_MOVE&&!scaling&&e.getPointerCount()==1&&isZoomed()){tx+=e.getX()-lastX;ty+=e.getY()-lastY;bound();invalidate();}
        lastX=e.getX();lastY=e.getY();
        if(e.getActionMasked()==MotionEvent.ACTION_UP||e.getActionMasked()==MotionEvent.ACTION_CANCEL){getParent().requestDisallowInterceptTouchEvent(false);scaling=false;}
        return true;
    }
    @Override public boolean performClick(){super.performClick();return true;}
}
