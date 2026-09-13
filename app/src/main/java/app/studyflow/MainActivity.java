package app.studyflow;

import android.app.*;
import android.os.*;
import android.content.*;
import android.content.res.ColorStateList;
import android.graphics.*;
import android.graphics.drawable.*;
import android.graphics.pdf.PdfRenderer;
import android.net.Uri;
import android.provider.OpenableColumns;
import android.view.*;
import android.view.animation.DecelerateInterpolator;
import android.widget.*;
import android.text.InputType;
import org.json.*;
import java.io.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;

public class MainActivity extends Activity {
    private Store store;
    private LinearLayout root,body,nav;
    private String tab="Today",selectedSubject=null,pendingChapter=null;
    private int bg,card,ink,muted,line,accent;
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private final Handler handler=new Handler(Looper.getMainLooper());
    private boolean reading=false;
    private int renderToken=0;
    private Bitmap displayedBitmap;
    private static final int IMPORT=41;
    private final DateTimeFormatter shortDate=DateTimeFormatter.ofPattern("EEE, d MMM",Locale.getDefault());

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        try { store=new Store(this); }
        catch(Exception e) {
            new AlertDialog.Builder(this).setTitle("Could not open your study data")
                .setMessage("Your saved file has been kept. Close the app and try again; no data has been reset.")
                .setPositiveButton("Close",(d,w)->finish()).setCancelable(false).show(); return;
        }
        if(state!=null) { tab=state.getString("tab","Today"); selectedSubject=state.getString("subject"); pendingChapter=state.getString("pending"); }
        show();
    }
    @Override public void onSaveInstanceState(Bundle state) {
        super.onSaveInstanceState(state); state.putString("tab",tab); state.putString("subject",selectedSubject); state.putString("pending",pendingChapter);
    }
    private int dp(float n) { return Math.round(n*getResources().getDisplayMetrics().density); }
    private void colors() {
        String theme=store.root.optString("theme","Midnight"); boolean light=theme.equals("Paper");
        bg=Color.parseColor(light?"#F4F6F2":theme.equals("AMOLED")?"#000000":"#0B1220");
        card=Color.parseColor(light?"#FFFFFF":"#152132");
        ink=Color.parseColor(light?"#172B2B":"#F0F5F4");
        muted=Color.parseColor(light?"#526567":"#A5B4C5");
        line=Color.parseColor(light?"#DFE6E1":"#2B3C50");
        accent=Color.parseColor(light?"#236C57":"#A6E8CD");
    }
    private GradientDrawable shape(int color,int radius) {
        GradientDrawable g=new GradientDrawable(); g.setColor(color); g.setCornerRadius(dp(radius)); return g;
    }
    private LinearLayout column() { LinearLayout l=new LinearLayout(this); l.setOrientation(LinearLayout.VERTICAL); return l; }
    private TextView text(String value,int size,int color,boolean bold) {
        TextView t=new TextView(this); t.setText(value); t.setTextSize(size); t.setTextColor(color);
        t.setFontFeatureSettings("kern"); t.setLineSpacing(dp(3),1);
        t.setTypeface(Typeface.create(bold?"sans-serif-medium":"sans-serif",bold?Typeface.BOLD:Typeface.NORMAL));
        return t;
    }
    private void gap(LinearLayout target,int h) { View v=new View(this); target.addView(v,new LinearLayout.LayoutParams(1,dp(h))); }
    private void label(LinearLayout target,String value,int size,int color,boolean bold) { target.addView(text(value,size,color,bold)); }
    private TextView button(String title,boolean primary,Runnable action) {
        TextView t=text(title,14,primary?bg:ink,true); t.setGravity(Gravity.CENTER); t.setMinHeight(dp(50)); t.setPadding(dp(14),dp(12),dp(14),dp(12));
        t.setBackground(new RippleDrawable(ColorStateList.valueOf(0x33808080),shape(primary?accent:card,16),null));
        t.setOnClickListener(v->action.run()); t.setFocusable(true); return t;
    }
    private void button(LinearLayout target,String title,boolean primary,Runnable action) { gap(target,10); target.addView(button(title,primary,action)); }
    private LinearLayout panel(LinearLayout target) {
        LinearLayout p=column(); p.setPadding(dp(20),dp(20),dp(20),dp(20));
        GradientDrawable g=shape(card,24); g.setStroke(dp(1),line); p.setBackground(g);
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2); lp.bottomMargin=dp(14); target.addView(p,lp); return p;
    }
    private void show() {
        reading=false; renderToken++; colors();
        getWindow().setStatusBarColor(bg); getWindow().setNavigationBarColor(bg);
        getWindow().getDecorView().setSystemUiVisibility(store.root.optString("theme").equals("Paper")?View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR|View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR:0);
        root=column(); root.setBackgroundColor(bg); root.setFitsSystemWindows(true); setContentView(root);
        root.setOnApplyWindowInsetsListener((v,insets)->{
            v.setPadding(insets.getSystemWindowInsetLeft(),insets.getSystemWindowInsetTop(),insets.getSystemWindowInsetRight(),insets.getSystemWindowInsetBottom());
            return insets.consumeSystemWindowInsets();
        });
        root.requestApplyInsets();
        ScrollView scroll=new ScrollView(this); scroll.setFillViewport(true); scroll.setClipToPadding(false);
        body=column(); body.setPadding(dp(22),dp(22),dp(22),dp(22)); scroll.addView(body);
        root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        LinearLayout top=new LinearLayout(this); top.setGravity(Gravity.CENTER_VERTICAL);
        TextView brand=text("STUDYFLOW",12,accent,true); brand.setLetterSpacing(.2f); top.addView(brand,new LinearLayout.LayoutParams(0,-2,1));
        top.addView(button("Settings",false,()->{tab="Settings";show();})); body.addView(top); gap(body,22);
        if(tab.equals("Today")) today(); else if(tab.equals("Library")) library(); else if(tab.equals("Plan")) plan(); else settings();
        nav=new LinearLayout(this); nav.setPadding(dp(14),dp(10),dp(14),dp(10)); nav.setBackgroundColor(bg);
        for(String item:new String[]{"Today","Library","Plan"}) {
            TextView b=button(item,tab.equals(item),()->{tab=item;selectedSubject=null;show();});
            LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,-2,1); lp.setMargins(dp(3),0,dp(3),0); nav.addView(b,lp);
        }
        root.addView(nav);
        if(!store.root.optBoolean("reduceMotion")) { body.setAlpha(0); body.setTranslationY(dp(9)); body.animate().alpha(1).translationY(0).setDuration(220).setInterpolator(new DecelerateInterpolator()).start(); }
    }
    private void title(String eyebrow,String title,String subtitle) {
        label(body,eyebrow.toUpperCase(Locale.ROOT),11,accent,true); gap(body,8);
        label(body,title,32,ink,true); gap(body,8); label(body,subtitle,14,muted,false); gap(body,24);
    }
    private void today() {
        title(LocalDate.now().format(shortDate),"Make room\nfor progress.","Your notes. Your pace. A clearer next step.");
        Planner.Result p=store.plan();
        LinearLayout hero=panel(body);
        int studied=store.usedToday();
        LinearLayout row=new LinearLayout(this); row.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout words=column(); label(words,"TODAY'S MOMENTUM",11,accent,true); gap(words,10); label(words,studied+" min",32,ink,true); label(words,"of focused study logged",12,muted,false);
        row.addView(words,new LinearLayout.LayoutParams(0,-2,1)); row.addView(new Ring(studied,Math.max(1,store.root.optInt("daily",90))),new LinearLayout.LayoutParams(dp(84),dp(84))); hero.addView(row);
        if(store.array("subjects").length()==0) {
            button(hero,"Create your first subject  +",true,()->subjectForm(null));
            label(body,"Start with one subject",21,ink,true); gap(body,8);
            label(body,"Add an exam date, break the syllabus into chapters, then attach your notes. Your daily plan will appear here.",15,muted,false);
            return;
        }
        button(hero,"Adjust today's time",false,()->budget(true));
        if(p.unscheduledMinutes>0) warning(p.unscheduledMinutes+" minutes do not fit before your exams. Adjust time, chapter estimates, or exam dates in Library.");
        label(body,"Your next steps",21,ink,true); gap(body,14);
        int count=0;
        for(Planner.Session s:p.sessions) if(s.date.equals(LocalDate.now())) { sessionCard(s); count++; }
        if(count==0) {
            LinearLayout empty=panel(body); label(empty,"A little breathing room",20,ink,true);
            label(empty,"No sessions are scheduled today. You can add a chapter or check upcoming work in Plan.",14,muted,false);
            button(empty,"Open your subjects",true,()->{tab="Library";show();});
        }
    }
    private void warning(String message) { LinearLayout p=panel(body); label(p,"Plan needs attention",16,accent,true); gap(p,6); label(p,message,14,muted,false); }
    private void sessionCard(Planner.Session s) {
        LinearLayout p=panel(body); label(p,s.topic.subject.toUpperCase(Locale.ROOT)+"  ·  "+s.minutes+" MIN",11,accent,true); gap(p,8);
        label(p,s.topic.title,21,ink,true); gap(p,4); label(p,"Exam "+s.topic.exam.format(shortDate),12,muted,false);
        button(p,"Start studying  →",true,()->startSession(s));
    }
    private void library() {
        if(selectedSubject!=null && store.find("subjects",selectedSubject)!=null) { subjectDetail();return; }
        title("Your workspace","Everything,\nin its place.","Subjects, chapters and notes. Saved on this device.");
        button(body,"Add subject  +",true,()->subjectForm(null)); gap(body,20);
        JSONArray a=store.array("subjects");
        if(a.length()==0) label(body,"Your library is ready for its first subject.",16,muted,false);
        for(int i=0;i<a.length();i++) {
            JSONObject s=a.optJSONObject(i); int total=0,done=0;
            JSONArray cs=store.array("chapters"); for(int j=0;j<cs.length();j++) { JSONObject c=cs.optJSONObject(j); if(c.optString("subject").equals(s.optString("id"))) {total++;if(c.optInt("remaining")==0)done++;} }
            LinearLayout p=panel(body); label(p,"EXAM · "+LocalDate.parse(s.optString("exam")).format(shortDate),11,accent,true);gap(p,10);
            label(p,s.optString("name"),23,ink,true); gap(p,6); label(p,done+" / "+total+" chapters studied",13,muted,false);
            button(p,"Open subject  →",false,()->{selectedSubject=s.optString("id");show();});
        }
    }
    private void subjectDetail() {
        JSONObject s=store.find("subjects",selectedSubject);
        button(body,"← All subjects",false,()->{selectedSubject=null;show();}); gap(body,18);
        title("Subject workspace",s.optString("name"),"Exam · "+LocalDate.parse(s.optString("exam")).format(shortDate));
        button(body,"Add chapter  +",true,()->chapterForm(null)); button(body,"Edit subject / exam date",false,()->subjectForm(s)); gap(body,18);
        JSONArray a=store.array("chapters"); int count=0;
        for(int i=0;i<a.length();i++) {
            JSONObject c=a.optJSONObject(i); if(!c.optString("subject").equals(selectedSubject))continue;count++;
            LinearLayout p=panel(body); label(p,c.optString("name"),21,ink,true);gap(p,5);
            label(p,c.optInt("remaining")==0?"Studied · add revision time when needed":c.optInt("remaining")+" min remaining · "+confidence(c.optInt("confidence")),13,muted,false);
            button(p,"Open notes",true,()->notes(c)); button(p,"Edit chapter / add revision",false,()->chapterForm(c));
        }
        if(count==0) label(body,"Add chapters with estimated study minutes to generate your plan.",15,muted,false);
        button(body,"Delete subject",false,()->new AlertDialog.Builder(this).setTitle("Delete this subject?").setMessage("Its chapters and attached notes will also be deleted from this app.")
            .setNegativeButton("Keep",null).setPositiveButton("Delete",(d,w)->{
                JSONArray cs=store.array("chapters");for(int i=cs.length()-1;i>=0;i--)if(cs.optJSONObject(i).optString("subject").equals(selectedSubject))deleteChapter(cs.optJSONObject(i).optString("id"));
                store.remove("subjects","id",selectedSubject);selectedSubject=null;saveAndShow();
            }).show());
    }
    private ArrayAdapter<String> confidenceAdapter() {
        return new ArrayAdapter<String>(this,android.R.layout.simple_spinner_dropdown_item,new String[]{"Need help","Okay","Confident"}) {
            @Override public View getView(int p,View v,ViewGroup parent) { TextView t=(TextView)super.getView(p,v,parent);t.setTextColor(ink);t.setBackgroundColor(card);return t; }
            @Override public View getDropDownView(int p,View v,ViewGroup parent) { TextView t=(TextView)super.getDropDownView(p,v,parent);t.setTextColor(ink);t.setBackgroundColor(card);t.setMinHeight(dp(48));return t; }
        };
    }
    private String confidence(int n) { return new String[]{"Need help","Okay","Confident"}[Math.max(0,Math.min(2,n))]; }
    private LinearLayout form() { LinearLayout f=column();f.setBackgroundColor(card);f.setPadding(dp(24),dp(10),dp(24),dp(10));return f; }
    private EditText field(LinearLayout f,String hint,String value,boolean numeric) {
        EditText e=new EditText(this);e.setHint(hint);e.setText(value);e.setTextSize(16);e.setTextColor(ink);e.setHintTextColor(muted);e.setBackgroundTintList(ColorStateList.valueOf(accent));e.setSingleLine(true);
        if(numeric)e.setInputType(InputType.TYPE_CLASS_NUMBER);
        label(f,hint,13,muted,false);f.addView(e);gap(f,12);return e;
    }
    private AlertDialog formDialog(String title,LinearLayout f,String positive,Runnable save) {
        ScrollView scroll=new ScrollView(this);scroll.addView(f);
        AlertDialog d=new AlertDialog.Builder(this).setTitle(title).setView(scroll).setNegativeButton("Cancel",null).setPositiveButton(positive,null).create();
        d.setOnShowListener(v->d.getButton(-1).setOnClickListener(b->{try{save.run();d.dismiss();}catch(IllegalArgumentException e){toast(e.getMessage());}}));d.show();return d;
    }
    private int number(EditText e,int min,int max) {
        try { int n=Integer.parseInt(e.getText().toString().trim());if(n<min||n>max)throw new Exception();return n; }
        catch(Exception ex){throw new IllegalArgumentException("Enter a number from "+min+" to "+max+".");}
    }
    private String required(EditText e) { String s=e.getText().toString().trim();if(s.isEmpty())throw new IllegalArgumentException("Please enter a name.");return s; }
    private void subjectForm(JSONObject existing) {
        LinearLayout f=form();EditText name=field(f,"Subject name",existing==null?"":existing.optString("name"),false);
        final LocalDate[] date={existing==null?LocalDate.now().plusDays(14):LocalDate.parse(existing.optString("exam"))};
        TextView dateButton=button("Exam: "+date[0].format(shortDate),false,()->{});
        dateButton.setOnClickListener(v->new DatePickerDialog(this,(p,y,m,d)->{date[0]=LocalDate.of(y,m+1,d);dateButton.setText("Exam: "+date[0].format(shortDate));},date[0].getYear(),date[0].getMonthValue()-1,date[0].getDayOfMonth()).show());f.addView(dateButton);
        formDialog(existing==null?"New subject":"Edit subject",f,"Save",()->{
            String n=required(name);if(!date[0].isAfter(LocalDate.now())||date[0].isAfter(LocalDate.now().plusDays(730)))throw new IllegalArgumentException("Choose an exam date from tomorrow to two years ahead.");
            if(existing==null)store.array("subjects").put(Store.object("id",Store.id(),"name",n,"exam",date[0].toString()));
            else {try{existing.put("name",n);existing.put("exam",date[0].toString());}catch(JSONException e){throw new IllegalArgumentException(e);}}
            saveAndShow();
        });
    }
    private void chapterForm(JSONObject existing) {
        LinearLayout f=form();EditText name=field(f,"Chapter name",existing==null?"":existing.optString("name"),false);
        EditText mins=field(f,"Minutes remaining (include revision)",existing==null?"60":existing.optString("remaining"),true);
        label(f,"Confidence",13,muted,false);Spinner confidence=new Spinner(this);
        confidence.setAdapter(confidenceAdapter());
        confidence.setSelection(existing==null?0:existing.optInt("confidence"));f.addView(confidence);
        if(existing!=null)button(f,"Delete chapter",false,()->new AlertDialog.Builder(this).setTitle("Delete chapter and notes?").setNegativeButton("Keep",null).setPositiveButton("Delete",(d,w)->{deleteChapter(existing.optString("id"));saveAndShow();}).show());
        formDialog(existing==null?"New chapter":"Edit chapter",f,"Save",()->{
            String n=required(name);int m=number(mins,0,10000);
            if(existing!=null && store.find("chapters",existing.optString("id"))==null) throw new IllegalArgumentException("This chapter has been deleted. Close this form.");
            if(existing==null)store.array("chapters").put(Store.object("id",Store.id(),"subject",selectedSubject,"name",n,"remaining",m,"confidence",confidence.getSelectedItemPosition()));
            else try{existing.put("name",n);existing.put("remaining",m);existing.put("confidence",confidence.getSelectedItemPosition());}catch(JSONException e){throw new IllegalArgumentException(e);}
            saveAndShow();
        });
    }
    private void deleteChapter(String id) {
        JSONArray ns=store.array("notes");for(int i=ns.length()-1;i>=0;i--) {JSONObject n=ns.optJSONObject(i);if(n.optString("chapter").equals(id)){deleteAttachment(n);ns.remove(i);}}
        store.remove("chapters","id",id);
    }
    private void deleteAttachment(JSONObject n) { String file=n.optString("file");if(!file.isEmpty())new File(getFilesDir(),file).delete(); }
    private void plan() {
        title("A realistic rhythm","Your study\nroadmap.","Nearest exams first. Lower-confidence chapters next. Sessions stay within your available time.");
        button(body,"Set weekly availability",true,()->budget(false));gap(body,18);
        Planner.Result p=store.plan();if(p.unscheduledMinutes>0)warning(p.unscheduledMinutes+" minutes could not be scheduled. No extra time has been silently added.");
        if(p.sessions.isEmpty())label(body,"Add chapters and future exam dates to see your plan.",16,muted,false);
        LocalDate last=null;int shown=0;
        for(Planner.Session s:p.sessions) {
            if(shown++>=100){label(body,"Showing the next 100 sessions. Later sessions update as you study.",13,muted,false);break;}
            if(!s.date.equals(last)){gap(body,12);label(body,s.date.format(shortDate),18,ink,true);gap(body,12);last=s.date;}
            sessionCard(s);
        }
    }
    private void budget(boolean today) {
        LinearLayout f=form();EditText minutes=field(f,today?"Total minutes available today":"Daily study minutes",String.valueOf(today&&store.root.optString("overrideDate").equals(LocalDate.now().toString())?store.root.optInt("todayBudget",90):store.root.optInt("daily",90)),true);
        CheckBox[] off=new CheckBox[7];
        if(!today){label(f,"Days off (no sessions)",14,muted,false);for(int i=0;i<7;i++){off[i]=new CheckBox(this);off[i].setTextColor(ink);off[i].setButtonTintList(ColorStateList.valueOf(accent));off[i].setText(DayOfWeek.of(i+1).toString());off[i].setChecked(store.root.optBoolean("off"+(i+1)));f.addView(off[i]);}}
        formDialog(today?"Adjust today":"Weekly availability",f,"Update plan",()->{
            int n=number(minutes,0,720);
            if(today){store.setting("overrideDate",LocalDate.now().toString());store.setting("todayBudget",n);}
            else{store.setting("daily",n);for(int i=0;i<7;i++)store.setting("off"+(i+1),off[i].isChecked());}
            saveAndShow();
        });
    }
    private void notes(JSONObject chapter) {
        final String id=chapter.optString("id");
        final AlertDialog[] dialog={null};
        LinearLayout f=form();label(f,"Notes stay on this device. Attach a PDF/image or write your own summary.",14,muted,false);
        button(f,"Import PDF or image",true,()->{
            if(dialog[0]!=null)dialog[0].dismiss();pendingChapter=id;Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.setType("*/*");i.putExtra(Intent.EXTRA_MIME_TYPES,new String[]{"application/pdf","image/jpeg","image/png","image/webp"});i.addCategory(Intent.CATEGORY_OPENABLE);startActivityForResult(i,IMPORT);
        });
        button(f,"Write a note",false,()->{dialog[0].dismiss();editNote(chapter,null);});gap(f,14);
        JSONArray a=store.array("notes");int count=0;
        for(int i=0;i<a.length();i++){
            JSONObject n=a.optJSONObject(i);if(!n.optString("chapter").equals(id))continue;count++;
            LinearLayout p=panel(f);label(p,n.optString("name"),17,ink,true);
            label(p,n.optString("type").equals("text")?"Written note":"Offline attachment · page "+n.optInt("page",1),12,muted,false);
            button(p,"Open",true,()->{dialog[0].dismiss();if(n.optString("type").equals("text"))editNote(chapter,n);else readFile(n);});
            button(p,"Delete note",false,()->new AlertDialog.Builder(this).setTitle("Delete this note?").setNegativeButton("Keep",null).setPositiveButton("Delete",(d,w)->{deleteAttachment(n);store.remove("notes","id",n.optString("id"));save();dialog[0].dismiss();notes(chapter);}).show());
        }
        if(count==0)label(f,"No notes attached yet.",14,muted,false);
        ScrollView sc=new ScrollView(this);sc.addView(f);dialog[0]=new AlertDialog.Builder(this).setTitle(chapter.optString("name")).setView(sc).setPositiveButton("Close",null).show();
    }
    private void editNote(JSONObject chapter,JSONObject note) {
        LinearLayout f=form();EditText name=field(f,"Note title",note==null?"":note.optString("name"),false);
        EditText content=new EditText(this);content.setTextColor(ink);content.setHintTextColor(muted);content.setBackgroundTintList(ColorStateList.valueOf(accent));content.setHint("Write a summary, key points, or questions…");content.setText(note==null?"":note.optString("content"));content.setMinLines(8);content.setGravity(Gravity.TOP);content.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_MULTI_LINE|InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);f.addView(content);
        formDialog("Written note",f,"Save",()->{
            String title=required(name);
            if(store.find("chapters",chapter.optString("id"))==null)throw new IllegalArgumentException("This chapter was deleted.");
            if(note==null)store.array("notes").put(Store.object("id",Store.id(),"chapter",chapter.optString("id"),"name",title,"type","text","content",content.getText().toString()));
            else try{note.put("name",title);note.put("content",content.getText().toString());}catch(JSONException e){throw new IllegalArgumentException(e);}
            save();toast("Note saved. Reopen the note list to refresh.");
        });
    }
    @Override protected void onActivityResult(int request,int result,Intent data) {
        super.onActivityResult(request,result,data);if(request!=IMPORT||result!=RESULT_OK||data==null||data.getData()==null)return;
        Uri uri=data.getData();String chapterId=pendingChapter;
        if(chapterId==null||store.find("chapters",chapterId)==null){toast("Choose a chapter and import again.");return;}
        String mime=getContentResolver().getType(uri);if(mime==null)mime="application/octet-stream";
        String name="Imported note";
        try(android.database.Cursor c=getContentResolver().query(uri,new String[]{OpenableColumns.DISPLAY_NAME},null,null,null)){if(c!=null&&c.moveToFirst())name=c.getString(0);}catch(Exception ignored){}
        final String type=mime, title=name, filename=Store.id();toast("Copying note for offline access…");
        worker.execute(()->{
            File file=new File(getFilesDir(),filename);
            try(InputStream in=getContentResolver().openInputStream(uri);OutputStream out=new FileOutputStream(file)){
                if(in==null)throw new IOException("File unavailable");byte[] buffer=new byte[32768];int n;long total=0;
                while((n=in.read(buffer))!=-1){total+=n;if(total>100L*1024*1024)throw new IOException("Use a file smaller than 100 MB.");out.write(buffer,0,n);}
                runOnUiThread(()->{if(isDestroyed())return;
                    if(store.find("chapters",chapterId)==null){file.delete();return;}
                    store.array("notes").put(Store.object("id",Store.id(),"chapter",chapterId,"name",title,"type",type,"file",filename,"page",1));
                    if(save())toast("Saved offline. Reopen notes to see the attachment.");
                });
            }catch(Exception e){file.delete();runOnUiThread(()->toast("Import failed. Check the file and available storage. "+e.getMessage()));}
        });
    }
    private void readFile(JSONObject note) {
        if(store.find("notes",note.optString("id"))==null){toast("This note was deleted.");return;}
        File file=new File(getFilesDir(),note.optString("file"));if(!file.exists()){toast("Attachment unavailable. Please import it again.");return;}
        reading=true;root.removeAllViews();LinearLayout reader=column();reader.setPadding(dp(18),dp(12),dp(18),dp(12));root.addView(reader,new LinearLayout.LayoutParams(-1,-1));
        reader.addView(button("← Back to StudyFlow",false,this::show));gap(reader,10);label(reader,note.optString("name"),18,ink,true);gap(reader,10);
        LinearLayout controls=new LinearLayout(this);reader.addView(controls);
        ScrollView sc=new ScrollView(this);reader.addView(sc,new LinearLayout.LayoutParams(-1,0,1));
        ImageView image=new ImageView(this);image.setAdjustViewBounds(true);image.setContentDescription("Page of "+note.optString("name"));sc.addView(image,new ScrollView.LayoutParams(-1,-2));
        TextView status=text("Loading…",13,muted,false);reader.addView(status);
        boolean pdf=note.optString("type").equals("application/pdf")||note.optString("name").toLowerCase(Locale.ROOT).endsWith(".pdf");
        final int[] page={Math.max(0,note.optInt("page",1)-1)},count={1};
        Runnable render=()->{
            final int token=++renderToken,index=page[0];status.setText("Loading page…");
            worker.execute(()->{
                try {
                    Bitmap bitmap;
                    if(pdf) {
                        try(ParcelFileDescriptor fd=ParcelFileDescriptor.open(file,ParcelFileDescriptor.MODE_READ_ONLY);PdfRenderer r=new PdfRenderer(fd)){
                            int pages=r.getPageCount();if(pages<1)throw new IOException("Empty PDF");int safe=Math.min(index,pages-1);
                            try(PdfRenderer.Page p=r.openPage(safe)){
                                float scale=Math.min(2f,1600f/Math.max(p.getWidth(),p.getHeight()));
                                bitmap=Bitmap.createBitmap(Math.max(1,(int)(p.getWidth()*scale)),Math.max(1,(int)(p.getHeight()*scale)),Bitmap.Config.ARGB_8888);bitmap.eraseColor(Color.WHITE);p.render(bitmap,null,null,PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
                            }
                            final Bitmap result=bitmap;
                            runOnUiThread(()->{if(token!=renderToken||isDestroyed()){result.recycle();return;}count[0]=pages;page[0]=safe;setPageImage(image,result);status.setText("Page "+(safe+1)+" of "+pages+" · position saved");sc.scrollTo(0,0);try{note.put("page",safe+1);}catch(JSONException ignored){}save();});
                        }
                    } else {
                        BitmapFactory.Options opts=new BitmapFactory.Options();opts.inJustDecodeBounds=true;BitmapFactory.decodeFile(file.getPath(),opts);opts.inSampleSize=1;
                        while(Math.max(opts.outWidth,opts.outHeight)/opts.inSampleSize>1800)opts.inSampleSize*=2;
                        opts.inJustDecodeBounds=false;bitmap=BitmapFactory.decodeFile(file.getPath(),opts);if(bitmap==null)throw new IOException("Unsupported image");final Bitmap result=bitmap;
                        runOnUiThread(()->{if(token!=renderToken||isDestroyed()){result.recycle();return;}setPageImage(image,result);status.setText("Offline image");});
                    }
                }catch(Exception|OutOfMemoryError e){runOnUiThread(()->{if(token==renderToken)status.setText("Cannot open this file. Try an unlocked PDF or a smaller image.");});}
            });
        };
        if(pdf){
            TextView prev=button("Previous",false,()->{if(page[0]>0){page[0]--;render.run();}}),next=button("Next",false,()->{if(page[0]<count[0]-1){page[0]++;render.run();}});
            controls.addView(prev,new LinearLayout.LayoutParams(0,-2,1));controls.addView(next,new LinearLayout.LayoutParams(0,-2,1));
            button(reader,"Go to page",false,()->{LinearLayout f=form();EditText input=field(f,"Page number",String.valueOf(page[0]+1),true);formDialog("Go to page",f,"Open",()->{page[0]=number(input,1,count[0])-1;render.run();});});
        }
        button(reader,"Add a written note",false,()->{JSONObject c=store.find("chapters",note.optString("chapter"));if(c!=null)editNote(c,null);});
        render.run();
    }
    private void setPageImage(ImageView image,Bitmap bitmap){Bitmap old=displayedBitmap;image.setImageBitmap(bitmap);displayedBitmap=bitmap;if(old!=null&&old!=bitmap&&!old.isRecycled())old.recycle();}
    private void startSession(Planner.Session s) {
        JSONObject chapter=store.find("chapters",s.topic.id);if(chapter==null)return;
        final AlertDialog[] dialog={null};
        LinearLayout f=form();label(f,s.topic.subject+" · "+s.minutes+" planned minutes",14,muted,false);
        label(f,"Open your notes, then log the minutes you actually studied. Your plan updates only when you save progress.",15,ink,false);
        button(f,"Open chapter notes",true,()->{dialog[0].dismiss();notes(chapter);});
        button(f,"Log study progress",false,()->{dialog[0].dismiss();logProgress(s);});
        ScrollView sc=new ScrollView(this);sc.addView(f);dialog[0]=new AlertDialog.Builder(this).setTitle(s.topic.title).setView(sc).setNegativeButton("Close",null).show();
    }
    private void logProgress(Planner.Session s) {
        LinearLayout f=form();EditText minutes=field(f,"Minutes actually studied","",true);
        label(f,"How well do you understand this chapter?",14,muted,false);Spinner confidence=new Spinner(this);confidence.setAdapter(confidenceAdapter());f.addView(confidence);
        CheckBox complete=new CheckBox(this);complete.setTextColor(ink);complete.setButtonTintList(ColorStateList.valueOf(accent));complete.setText("I have finished this chapter's planned work");f.addView(complete);
        formDialog("Save your progress",f,"Save progress",()->{
            JSONObject c=store.find("chapters",s.topic.id);if(c==null)throw new IllegalArgumentException("This chapter no longer exists.");int actual=number(minutes,1,720);
            try{c.put("remaining",complete.isChecked()?0:Math.max(0,c.optInt("remaining")-actual));c.put("confidence",confidence.getSelectedItemPosition());}catch(JSONException e){throw new IllegalArgumentException(e);}
            store.array("logs").put(Store.object("id",Store.id(),"chapter",s.topic.id,"date",LocalDate.now().toString(),"minutes",actual));saveAndShow();
        });
    }
    private void settings() {
        title("Make it yours","Quietly powerful.","A focused workspace, tuned to your preferences.");
        LinearLayout appearance=panel(body);label(appearance,"Appearance",21,ink,true);
        for(String theme:new String[]{"Midnight","Paper","AMOLED"})button(appearance,theme+(store.root.optString("theme","Midnight").equals(theme)?"  ✓":""),false,()->{store.setting("theme",theme);saveAndShow();});
        button(appearance,store.root.optBoolean("reduceMotion")?"Animations: reduced":"Animations: enabled",false,()->{store.setting("reduceMotion",!store.root.optBoolean("reduceMotion"));saveAndShow();});
        LinearLayout rhythm=panel(body);label(rhythm,"Study rhythm",21,ink,true);button(rhythm,"Weekly availability",false,()->budget(false));
        LinearLayout privacy=panel(body);label(privacy,"Your space stays yours",20,ink,true);gap(privacy,8);label(privacy,"No account, ads, analytics or internet permission. Notes are copied into private app storage. Uninstalling removes your data; keep your original files.",14,muted,false);
        gap(body,12);label(body,"STUDYFLOW  /  0.1.0",12,accent,true);gap(body,6);label(body,"Foundation edition · offline notes + adaptive planning",13,muted,false);
    }
    private boolean save() { try{store.save();return true;}catch(IOException e){toast("Could not save. Free some device storage and try again before closing.");return false;} }
    private void saveAndShow() { if(save())show(); }
    private void toast(String value) { Toast.makeText(this,value,Toast.LENGTH_LONG).show(); }
    @Override public void onBackPressed(){if(reading){show();}else if(selectedSubject!=null){selectedSubject=null;show();}else if(!tab.equals("Today")){tab="Today";show();}else super.onBackPressed();}
    @Override protected void onDestroy(){renderToken++;handler.removeCallbacksAndMessages(null);worker.shutdown();super.onDestroy();}
    private final class Ring extends View {
        private final Paint p=new Paint(3);private final float fraction;private final int percent;
        Ring(int done,int goal){super(MainActivity.this);fraction=Math.min(1,done/(float)goal);percent=Math.round(fraction*100);setContentDescription(percent+" percent of daily study target");}
        @Override protected void onDraw(Canvas c){super.onDraw(c);float w=getWidth(),h=getHeight();p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(dp(6));p.setStrokeCap(Paint.Cap.ROUND);p.setColor(line);RectF r=new RectF(dp(6),dp(6),w-dp(6),h-dp(6));c.drawArc(r,0,360,false,p);p.setColor(accent);c.drawArc(r,-90,360*fraction,false,p);p.setStyle(Paint.Style.FILL);p.setTextSize(dp(18));p.setTypeface(Typeface.DEFAULT_BOLD);p.setTextAlign(Paint.Align.CENTER);c.drawText(percent+"%",w/2,h/2-(p.ascent()+p.descent())/2,p);}
    }
}
