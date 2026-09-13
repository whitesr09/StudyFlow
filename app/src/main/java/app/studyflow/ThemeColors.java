package app.studyflow;

/** Color math is independent of Android so arbitrary user colors can be contrast-tested. */
final class ThemeColors {
    static double luminance(int c){return .2126*linear((c>>16)&255)+.7152*linear((c>>8)&255)+.0722*linear(c&255);}
    private static double linear(int n){double s=n/255d;return s<=.04045?s/12.92:Math.pow((s+.055)/1.055,2.4);}
    static double contrast(int a,int b){double x=luminance(a),y=luminance(b);return (Math.max(x,y)+.05)/(Math.min(x,y)+.05);}
    static int blend(int a,int b,float t){int r=Math.round(((a>>16)&255)*(1-t)+((b>>16)&255)*t),g=Math.round(((a>>8)&255)*(1-t)+((b>>8)&255)*t),bl=Math.round((a&255)*(1-t)+(b&255)*t);return 0xff000000|(r<<16)|(g<<8)|bl;}
    static int readable(int seed,int background,int surface){int destination=luminance(background)>.4?0xff000000:0xffffffff;for(int i=0;i<=100;i++){int c=blend(seed,destination,i/100f);if(contrast(c,background)>=4.5&&contrast(c,surface)>=4.5)return c;}return destination;}
    static int on(int background){return contrast(0xff000000,background)>=contrast(0xffffffff,background)?0xff000000:0xffffffff;}
}
