package app.studyflow;

import android.app.*;
import android.content.*;
import android.graphics.Bitmap;
import android.os.*;
import android.view.*;
import android.widget.*;
import org.json.*;
import java.io.*;
import java.lang.reflect.*;
import java.time.LocalDate;

/** Device smoke checks without a third-party test runner. Data belongs to the CI emulator only. */
public class UiChecks extends Instrumentation {
    private MainActivity activity;
    @Override public void onCreate(Bundle args){super.onCreate(args);start();}
    private void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    private Object field(String name)throws Exception{Field f=MainActivity.class.getDeclaredField(name);f.setAccessible(true);return f.get(activity);}
    private void invoke(String name,Class<?>[] types,Object... args){runOnMainSync(()->{try{Method m=MainActivity.class.getDeclaredMethod(name,types);m.setAccessible(true);m.invoke(activity,args);}catch(Exception e){throw new RuntimeException(e);}});waitForIdleSync();}
    private void screen(String name){invoke("go",new Class[]{String.class},name);}
    private View find(View root,String value){if(root instanceof TextView&&((TextView)root).getText().toString().equals(value))return root;if(root instanceof ViewGroup)for(int i=0;i<((ViewGroup)root).getChildCount();i++){View match=find(((ViewGroup)root).getChildAt(i),value);if(match!=null)return match;}return null;}
    private View visibleRoot(){return activity.getWindow().getDecorView();}
    private void click(View root,String text){runOnMainSync(()->{View v=find(root,text);check(v!=null,"Missing button: "+text);v.performClick();});waitForIdleSync();}
    private void fill(View root,String... values){runOnMainSync(()->{java.util.List<EditText> fields=new java.util.ArrayList<>();collectFields(root,fields);check(fields.size()>=values.length,"Not enough form fields");for(int i=0;i<values.length;i++)fields.get(i).setText(values[i]);});}
    private void collectFields(View v,java.util.List<EditText> fields){if(v instanceof EditText)fields.add((EditText)v);if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++)collectFields(((ViewGroup)v).getChildAt(i),fields);}
    private View sheetRoot()throws Exception{return ((AlertDialog)field("latestSheet")).getWindow().getDecorView();}
    private void settle()throws Exception{waitForIdleSync();Thread.sleep(300);}
    private void screenshot(String name)throws Exception{settle();Bitmap bitmap=getUiAutomation().takeScreenshot();check(bitmap!=null,"Screenshot unavailable");File dir=new File(getTargetContext().getExternalFilesDir(null),"ui-checks");dir.mkdirs();try(FileOutputStream out=new FileOutputStream(new File(dir,name+".png"))){bitmap.compress(Bitmap.CompressFormat.PNG,100,out);}bitmap.recycle();}
    private void geometry()throws Exception{runOnMainSync(()->{try{LinearLayout body=(LinearLayout)field("body");int bottom=0;for(int i=0;i<body.getChildCount();i++){View v=body.getChildAt(i);if(v.getVisibility()==View.GONE)continue;check(v.getTop()>=bottom,"Overlapping body children "+i);bottom=v.getBottom();}LinearLayout root=(LinearLayout)field("root");View nav=(View)field("nav");check(root.getChildAt(0).getBottom()<=nav.getTop(),"Navigation overlays scroll content");}catch(Exception e){throw new RuntimeException(e);}});}
    private void theme(String value){runOnMainSync(()->{try{Store store=(Store)field("store");store.setting("theme",value);store.save();}catch(Exception e){throw new RuntimeException(e);}});screen("Today");}
    private void seed()throws Exception{
        JSONObject root=new JSONObject();root.put("theme","Minimal Dark");root.put("goal",30);root.put("reduceMotion",false);
        root.put("subjects",new JSONArray().put(Store.object("id","subject","name","Pharmacology","exam",LocalDate.now().plusDays(30).toString())));
        root.put("chapters",new JSONArray().put(Store.object("id","chapter","subject","subject","name","Unit 1","remaining",60,"confidence",0)));
        root.put("cards",new JSONArray().put(Store.object("id","card1","chapter","chapter","question","What is active recall?","answer","Retrieving an answer from memory before checking it.","due",LocalDate.now().toString())).put(Store.object("id","card2","chapter","chapter","question","What is spaced review?","answer","Revisiting material at increasing intervals.","due",LocalDate.now().toString())));
        root.put("logs",new JSONArray());root.put("notes",new JSONArray());
        try(FileOutputStream out=getTargetContext().openFileOutput("studyflow.json",Context.MODE_PRIVATE)){out.write(root.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));}
    }
    @Override public void onStart(){Bundle result=new Bundle();try{
        seed();Intent intent=new Intent(getTargetContext(),MainActivity.class);intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);activity=(MainActivity)startActivitySync(intent);settle();geometry();check(find(visibleRoot(),"Your week is a blank page.")!=null,"Missing chart empty state");screenshot("01-minimal-empty");
        theme("Midnight");settle();geometry();screenshot("02-midnight-spacing");
        theme("Minimal Dark");screen("Settings");settle();geometry();runOnMainSync(()->{try{LinearLayout body=(LinearLayout)field("body");TextView credit=(TextView)find(body,"MADE  BY  N S H D");check(credit!=null&&(credit.getGravity()&Gravity.CENTER_HORIZONTAL)!=0,"Credit is not centered");((ScrollView)body.getParent()).fullScroll(View.FOCUS_DOWN);}catch(Exception e){throw new RuntimeException(e);}});screenshot("03-centered-footer");
        screen("Cards");invoke("reviewNext",new Class[]{int.class},0);settle();AlertDialog dialog=(AlertDialog)field("reviewDialog");check(dialog!=null&&dialog.isShowing(),"Review did not open");View decor=dialog.getWindow().getDecorView();int height=decor.getHeight();check(height>0&&height<=getTargetContext().getResources().getDisplayMetrics().heightPixels,"Sheet exceeds screen");click(decor,"Reveal answer");settle();click(decor,"Good · 2 days");settle();check(field("reviewDialog")==dialog,"Review replaced the window");check(find(decor,"What is spaced review?")!=null,"Second card missing");screenshot("04-persistent-review");runOnMainSync(dialog::dismiss);settle();
        screen("Inbox");invoke("inboxForm",new Class[]{JSONObject.class},(Object)null);settle();fill(sheetRoot(),"Quick thought","Study the receptor mechanism.");click(sheetRoot(),"Save note");settle();check(find(visibleRoot(),"Quick thought")!=null,"Inbox save failed");
        click(visibleRoot(),"Move into a chapter");settle();click(sheetRoot(),"Pharmacology · Unit 1");settle();check(((Store)field("store")).array("inbox").length()==0,"Inbox move did not remove original");check(((Store)field("store")).array("notes").length()==1,"Inbox move did not create chapter note");
        screen("Assignments");invoke("assignmentForm",new Class[]{JSONObject.class},(Object)null);settle();fill(sheetRoot(),"Lab report","Pharmaceutics","Include observations");click(sheetRoot(),"Save");settle();geometry();check(find(visibleRoot(),"DUE IN 7 DAYS")!=null,"Deadline label incorrect");click(visibleRoot(),"Mark completed");settle();click(visibleRoot(),"Show completed assignments");settle();check(find(visibleRoot(),"Lab report")!=null,"Completion not retained");screenshot("05-assignments");
        screen("Revision");check(find(visibleRoot(),"Unit 1")!=null,"Weak chapter missing");
        invoke("weeklyGoalForm",new Class[]{});settle();fill(sheetRoot(),"240");click(sheetRoot(),"Save target");settle();check(((Store)field("store")).root.optInt("weeklyGoal")==240,"Weekly target not saved");
        invoke("quizStart",new Class[]{});settle();fill(sheetRoot(),"2");click(sheetRoot(),"Begin");settle();AlertDialog quiz=(AlertDialog)field("latestSheet");click(sheetRoot(),"Reveal answer");click(sheetRoot(),"I recalled it");settle();check(field("latestSheet")==quiz,"Quiz replaced its window");click(sheetRoot(),"Reveal answer");click(sheetRoot(),"Needs practice");settle();check(((Store)field("store")).array("quizzes").length()==1,"Quiz result was not saved exactly once");check(((Store)field("store")).array("quizzes").optJSONObject(0).optInt("correct")==1,"Quiz score incorrect");check(((Store)field("store")).array("logs").length()==0,"Quiz silently logged study minutes");screenshot("07-quiz-result");runOnMainSync(quiz::dismiss);settle();
        theme("Minimal");runOnMainSync(()->{try{Store store=(Store)field("store");store.array("logs").put(Store.object("id","log","chapter","chapter","date",LocalDate.now().toString(),"minutes",25));store.setting("customAccent","#FFFF00");store.setting("accentMode","Custom");store.save();}catch(Exception e){throw new RuntimeException(e);}});screen("Today");settle();geometry();check(find(visibleRoot(),"25 minutes across 7 days")!=null,"Chart total missing");check(((Integer)field("accent"))==0xffffff00,"HEX was altered");screenshot("06-light-activity");
        screen("Library");screen("Settings");invoke("navigateBack",new Class[]{});check(field("tab").equals("Library"),"Back lost previous section");
        JSONObject office=Store.object("id","office-route","name","Lecture.pptx","file","missing","type","application/vnd.openxmlformats-officedocument.presentationml.presentation");
        invoke("readFile",new Class[]{JSONObject.class},office);check(find(visibleRoot(),"Open original document")!=null,"Office original route missing");check(find(visibleRoot(),"Attach exported PDF")!=null,"Layout PDF route missing");invoke("navigateBack",new Class[]{});check(field("tab").equals("Library")&&!((Boolean)field("reading")),"Reader back failed");
        // Re-measure at a narrower width using the real view hierarchy.
        runOnMainSync(()->{try{LinearLayout root=(LinearLayout)field("root");int w=(int)(320*getTargetContext().getResources().getDisplayMetrics().density);root.measure(View.MeasureSpec.makeMeasureSpec(w,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(root.getHeight(),View.MeasureSpec.EXACTLY));root.layout(0,0,w,root.getMeasuredHeight());}catch(Exception e){throw new RuntimeException(e);}});geometry();
        result.putString("stream","STUDYFLOW_UI_OK: spacing, footer, persistent review, empty/populated charts, assignments, inbox, revision and exact accent passed.\n");finish(Activity.RESULT_OK,result);
    }catch(Throwable e){result.putString("stream","STUDYFLOW_UI_FAILED: "+android.util.Log.getStackTraceString(e));finish(Activity.RESULT_CANCELED,result);}}
}
