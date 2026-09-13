package app.studyflow;
public class ThemeColorsTest {
 public static void main(String[] args){int[][] surfaces={{0xfffaf9fc,0xffffffff},{0xff111116,0xff1b1b23},{0xff0b1220,0xff152132},{0xff000000,0xff152132},{0xfff4f6f2,0xffffffff}};for(int[] pair:surfaces)for(int r=0;r<256;r+=17)for(int g=0;g<256;g+=17)for(int b=0;b<256;b+=17){int c=ThemeColors.readable(0xff000000|(r<<16)|(g<<8)|b,pair[0],pair[1]);if(ThemeColors.contrast(c,pair[0])<4.5||ThemeColors.contrast(c,pair[1])<4.5||ThemeColors.contrast(c,ThemeColors.on(c))<4.5)throw new AssertionError("Unreadable accent");}System.out.println("20,480 accent/theme combinations passed contrast checks.");}
}
