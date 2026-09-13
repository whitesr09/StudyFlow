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
    private int bg,card,ink,muted,line,accent,onAccent;
    private boolean minimal,lightTheme;
    private String pendingExportNote;
    private static final int EXPORT_NOTE=42;
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private final Handler handler=new Handler(Looper.getMainLooper());
    private boolean reading=false;
    private int renderToken=0;
    private Bitmap displayedBitmap;
    private FocusClock focusClock;
    private String readerNoteId;
    private static final int IMPORT=41;
    private final DateTimeFormatter shortDate=DateTimeFormatter.ofPattern("EEE, d MMM",Locale.getDefault());

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        try { store=new Store(this); }
        catch(Exception e) {
            colors();LinearLayout f=form();label(f,"Your saved file has been kept. Close the app and try again; no data has been reset.",16,ink,false);AlertDialog error=sheet("Could not open study data",f,"Close",this::finish);error.setCancelable(false);return;
        }
        if(state!=null) { tab=state.getString("tab","Today"); selectedSubject=state.getString("subject"); pendingChapter=state.getString("pending");pendingExportNote=state.getString("exportNote"); }
        show();
        if(state!=null){String id=state.getString("reader");JSONObject n=id==null?null:store.find("notes",id);if(n!=null)readFile(n);}
    }
    @Override public void onSaveInstanceState(Bundle state) {
        super.onSaveInstanceState(state); state.putString("tab",tab); state.putString("subject",selectedSubject); state.putString("pending",pendingChapter);state.putString("exportNote",pendingExportNote);if(reading)state.putString("reader",readerNoteId);
    }
    private int dp(float n) { return Math.round(n*getResources().getDisplayMetrics().density); }
    private void colors() {
        String theme=store==null?"Minimal":store.root.optString("theme","Midnight");minimal=theme.startsWith("Minimal");lightTheme=theme.equals("Paper")||theme.equals("Minimal");
        bg=Color.parseColor(lightTheme?(minimal?"#FAF9FC":"#F4F6F2"):theme.equals("AMOLED")?"#000000":minimal?"#111116":"#0B1220");
        card=Color.parseColor(lightTheme?"#FFFFFF":minimal?"#1B1B23":"#152132");ink=Color.parseColor(lightTheme?"#20212A":"#F2F1F7");muted=Color.parseColor(lightTheme?"#636371":"#B3B2C3");line=Color.parseColor(lightTheme?"#E3E1EB":"#363540");
        int seed=Color.parseColor(minimal?"#A374EC":lightTheme?"#236C57":"#A6E8CD");
        if(store!=null){String mode=store.root.optString("accentMode","Theme");if(mode.equals("Custom"))try{seed=Color.parseColor(store.root.optString("customAccent","#A374EC"));}catch(Exception ignored){}
            if(mode.equals("Android")&&Build.VERSION.SDK_INT>=31)seed=getColor(lightTheme?android.R.color.system_accent1_600:android.R.color.system_accent1_200);}
        accent=ThemeColors.readable(seed,bg,card);onAccent=ThemeColors.on(accent);
    }
    @Override protected void onResume(){super.onResume();if(store!=null&&root!=null&&store.root.optString("accentMode").equals("Android")){int before=accent;colors();if(before!=accent){String id=readerNoteId;show();JSONObject n=id==null?null:store.find("notes",id);if(n!=null)readFile(n);}}}
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
        TextView t=text(title,14,primary?onAccent:ink,true); t.setGravity(Gravity.CENTER); t.setMinHeight(dp(50)); t.setPadding(dp(14),dp(12),dp(14),dp(12));
        t.setBackground(new RippleDrawable(ColorStateList.valueOf(0x33808080),shape(primary?accent:card,16),null));
        t.setOnClickListener(v->action.run());t.setStateListAnimator(pressAnimator());t.setFocusable(true);return t;
    }
    private android.animation.StateListAnimator pressAnimator() {
        android.animation.StateListAnimator states=new android.animation.StateListAnimator();if((store!=null&&store.root.optBoolean("reduceMotion")))return states;
        android.animation.AnimatorSet pressed=new android.animation.AnimatorSet();pressed.playTogether(android.animation.ObjectAnimator.ofFloat(null,"scaleX",.97f),android.animation.ObjectAnimator.ofFloat(null,"scaleY",.97f));pressed.setDuration(100);
        android.animation.AnimatorSet rest=new android.animation.AnimatorSet();rest.playTogether(android.animation.ObjectAnimator.ofFloat(null,"scaleX",1f),android.animation.ObjectAnimator.ofFloat(null,"scaleY",1f));rest.setDuration(160);
        states.addState(new int[]{android.R.attr.state_pressed},pressed);states.addState(new int[]{},rest);return states;
    }
    private void button(LinearLayout target,String title,boolean primary,Runnable action) { gap(target,10); target.addView(button(title,primary,action)); }
    private LinearLayout panel(LinearLayout target) {
        LinearLayout p=column(); p.setPadding(dp(20),dp(20),dp(20),dp(20));
        GradientDrawable g=minimal?shape(card,20):new GradientDrawable(GradientDrawable.Orientation.TL_BR,new int[]{card,bg});g.setCornerRadius(dp(minimal?20:24));g.setStroke(dp(1),line);p.setBackground(g);p.setElevation(dp(minimal?0:2));
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2); lp.bottomMargin=dp(14); target.addView(p,lp); return p;
    }
    private void show() {
        reading=false;readerNoteId=null; renderToken++; colors();
        if(displayedBitmap!=null){displayedBitmap.recycle();displayedBitmap=null;}
        getWindow().setStatusBarColor(bg); getWindow().setNavigationBarColor(bg);
        getWindow().getDecorView().setSystemUiVisibility(lightTheme?View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR|View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR:0);
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
        top.addView(iconButton("settings","Settings",()->{tab="Settings";show();})); body.addView(top); gap(body,22);
        if(tab.equals("Today")) today(); else if(tab.equals("Library")) library(); else if(tab.equals("Plan")) plan();else if(tab.equals("Appearance"))appearance();else if(tab.equals("Insights"))insights();else if(tab.equals("Search"))workspaceSearch();else if(tab.equals("Cards"))flashcards();else if(tab.equals("History"))history();else if(tab.equals("Exams"))exams();else settings();
        nav=new LinearLayout(this); nav.setPadding(dp(14),dp(10),dp(14),dp(10)); nav.setBackgroundColor(bg);
        for(String item:new String[]{"Today","Library","Plan"}) {
            TextView b=button(item,tab.equals(item),()->{tab=item;selectedSubject=null;show();});
            LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,-2,1); lp.setMargins(dp(3),0,dp(3),0); nav.addView(b,lp);
        }
        root.addView(nav);
        if(!(store!=null&&store.root.optBoolean("reduceMotion"))) { body.setAlpha(0); body.setTranslationY(dp(9)); body.animate().alpha(1).translationY(0).setDuration(220).setInterpolator(new DecelerateInterpolator()).start(); }
    }
    private void title(String eyebrow,String title,String subtitle) {
        label(body,eyebrow.toUpperCase(Locale.ROOT),11,accent,true); gap(body,8);
        label(body,minimal?title.replace("\n"," "):title,minimal?28:32,ink,true); gap(body,8); label(body,subtitle,14,muted,false); gap(body,24);
    }
    private void today() {
        title(LocalDate.now().format(shortDate),"Make room\nfor progress.","Your notes. Your pace. A clearer next step.");
        goalSummary();
        button(body,"Review flashcards",false,()->{tab="Cards";show();});
        button(body,"Exam countdowns",false,()->{tab="Exams";show();});
        JSONObject active=store.find("chapters",store.root.optString("focusChapter"));
        if(active!=null)button(body,"Resume focus · "+active.optString("name"),true,()->openFocus(active));
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
        button(hero,"Adjust today's time",false,()->budget(true));button(hero,"Study insights  →",false,()->{tab="Insights";show();});
        recentDocuments();
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
        button(body,"Search workspace",false,()->{tab="Search";show();});
        button(body,"Flashcards & review",false,()->{tab="Cards";show();});
        recentDocuments();
        JSONArray a=store.array("subjects");List<JSONObject> ordered=new ArrayList<>();for(int i=0;i<a.length();i++)ordered.add(a.optJSONObject(i));ordered.sort((x,y)->Boolean.compare(y.optBoolean("pinned"),x.optBoolean("pinned")));
        if(a.length()==0) label(body,"Your library is ready for its first subject.",16,muted,false);
        for(int i=0;i<a.length();i++) {
            JSONObject s=ordered.get(i); int total=0,done=0;
            JSONArray cs=store.array("chapters"); for(int j=0;j<cs.length();j++) { JSONObject c=cs.optJSONObject(j); if(c.optString("subject").equals(s.optString("id"))) {total++;if(c.optInt("remaining")==0)done++;} }
            LinearLayout p=panel(body); label(p,"EXAM · "+LocalDate.parse(s.optString("exam")).format(shortDate),11,accent,true);gap(p,10);
            label(p,(s.optBoolean("pinned")?"★  ":"")+s.optString("name"),23,ink,true); gap(p,6); label(p,done+" / "+total+" chapters studied",13,muted,false);
            button(p,"Open subject  →",false,()->{selectedSubject=s.optString("id");show();});button(p,s.optBoolean("pinned")?"Unpin subject":"Pin to top",false,()->{try{s.put("pinned",!s.optBoolean("pinned"));}catch(JSONException ignored){}saveAndShow();});
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
            button(p,"Flashcards",false,()->cardList(c));button(p,"Open notes",true,()->notes(c));button(p,"Focus on this chapter",false,()->focus(c,25)); button(p,"Edit chapter / add revision",false,()->chapterForm(c));
        }
        if(count==0) label(body,"Add chapters with estimated study minutes to generate your plan.",15,muted,false);
        button(body,"Delete subject",false,()->confirm("Delete subject?","Its chapters and attached notes will also be deleted from this app.","Delete","Keep",()->{
                JSONArray cs=store.array("chapters");for(int i=cs.length()-1;i>=0;i--)if(cs.optJSONObject(i).optString("subject").equals(selectedSubject))deleteChapter(cs.optJSONObject(i).optString("id"));
                store.remove("subjects","id",selectedSubject);selectedSubject=null;saveAndShow();
            }));
    }
    private final class Choice extends TextView {
        int selected;
        Choice(){super(MainActivity.this);setTextSize(16);setTextColor(ink);setPadding(dp(16),dp(16),dp(16),dp(16));setBackground(inputBackground(false));setMinHeight(dp(54));setSelection(0);setOnClickListener(v->{LinearLayout f=form();final AlertDialog[] d={null};for(int i=0;i<3;i++){final int n=i;button(f,confidence(i)+(i==selected?"  ✓":""),i==selected,()->{setSelection(n);d[0].dismiss();});}d[0]=sheet("Your confidence",f,"Close",null);});}
        int getSelectedItemPosition(){return selected;}
        void setSelection(int n){selected=Math.max(0,Math.min(2,n));setText(confidence(selected)+"   ⌄");setContentDescription("Confidence: "+confidence(selected));}
    }
    private String confidence(int n) { return new String[]{"Need help","Okay","Confident"}[Math.max(0,Math.min(2,n))]; }
    private LinearLayout form() { LinearLayout f=column();f.setPadding(dp(20),dp(8),dp(20),dp(16));return f; }
    private void styleInput(EditText e) {
        e.setTextSize(16);e.setTextColor(ink);e.setHintTextColor(muted);
        e.setPadding(dp(16),dp(15),dp(16),dp(15));e.setMinHeight(dp(54));
        e.setBackground(inputBackground(false));
        e.setOnFocusChangeListener((v,focused)->e.setBackground(inputBackground(focused)));
    }
    private Drawable inputBackground(boolean focused) {
        GradientDrawable g=shape(bg,16);g.setStroke(dp(focused?2:1),focused?accent:line);return g;
    }
    private void entrance(View v) {
        if((store!=null&&store.root.optBoolean("reduceMotion")))return;
        v.setAlpha(0);v.setTranslationY(dp(12));v.animate().alpha(1).translationY(0).setDuration(260).setInterpolator(new DecelerateInterpolator()).start();
    }
    private AlertDialog sheet(String title,LinearLayout f,String positive,Runnable save) {return sheet(title,f,positive,save,"Cancel");}
    private AlertDialog sheet(String title,LinearLayout f,String positive,Runnable save,String cancelLabel) {
        LinearLayout shell=column();GradientDrawable surface=shape(card,28);surface.setStroke(dp(1),line);shell.setBackground(surface);shell.setPadding(0,dp(22),0,dp(16));
        TextView heading=text(title,24,ink,true);heading.setPadding(dp(22),0,dp(22),dp(16));shell.addView(heading);
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(false);scroll.addView(f);
        shell.addView(scroll,new LinearLayout.LayoutParams(-1,-2,1));
        TextView validation=text("",13,ThemeColors.readable(0xffc5374c,bg,card),false);validation.setPadding(dp(22),dp(8),dp(22),0);validation.setVisibility(View.GONE);shell.addView(validation);
        LinearLayout actions=new LinearLayout(this);actions.setPadding(dp(16),dp(12),dp(16),0);shell.addView(actions);
        AlertDialog d=new AlertDialog.Builder(this).create();d.setView(shell,0,0,0,0);
        if(save!=null) {TextView cancel=button(cancelLabel,false,d::dismiss);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,-2,1);lp.setMargins(0,0,dp(8),0);actions.addView(cancel,lp);}
        TextView confirm=button(positive,true,()->{try{validation.setVisibility(View.GONE);if(save!=null)save.run();d.dismiss();}catch(IllegalArgumentException ex){validation.setText(ex.getMessage());validation.setVisibility(View.VISIBLE);validation.announceForAccessibility(ex.getMessage());}});
        actions.addView(confirm,new LinearLayout.LayoutParams(0,-2,1));
        d.setOnShowListener(v->{Window w=d.getWindow();if(w!=null){w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));w.setDimAmount(.65f);w.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
            w.setLayout(Math.min(getResources().getDisplayMetrics().widthPixels-dp(28),dp(540)),WindowManager.LayoutParams.WRAP_CONTENT);
            shell.post(()->{int max=(int)(getResources().getDisplayMetrics().heightPixels*.84);if(shell.getHeight()>max)w.setLayout(Math.min(getResources().getDisplayMetrics().widthPixels-dp(28),dp(540)),max);});}
            entrance(shell);});d.show();return d;
    }
    private EditText field(LinearLayout f,String hint,String value,boolean numeric) {
        label(f,hint,12,muted,true);gap(f,7);
        EditText e=new EditText(this);e.setHint(numeric?"Enter a number":hint);e.setText(value);e.setSingleLine(true);
        if(numeric)e.setInputType(InputType.TYPE_CLASS_NUMBER);styleInput(e);f.addView(e,new LinearLayout.LayoutParams(-1,-2));gap(f,16);return e;
    }
    private void confirm(String title,String message,String positive,String keep,Runnable action) {LinearLayout f=form();label(f,message,16,muted,false);gap(f,10);sheet(title,f,positive,action,keep);}
    private AlertDialog formDialog(String title,LinearLayout f,String positive,Runnable save) { return sheet(title,f,positive,save); }
    private int number(EditText e,int min,int max) {
        try { int n=Integer.parseInt(e.getText().toString().trim());if(n<min||n>max)throw new Exception();return n; }
        catch(Exception ex){throw new IllegalArgumentException("Enter a number from "+min+" to "+max+".");}
    }
    private String required(EditText e) { String s=e.getText().toString().trim();if(s.isEmpty())throw new IllegalArgumentException("Please enter a name.");return s; }
    private void subjectForm(JSONObject existing) {
        LinearLayout f=form();EditText name=field(f,"Subject name",existing==null?"":existing.optString("name"),false);
        final LocalDate[] date={existing==null?LocalDate.now().plusDays(14):LocalDate.parse(existing.optString("exam"))};
        TextView dateButton=button("Exam: "+date[0].format(shortDate),false,()->{});
        dateButton.setOnClickListener(v->chooseDate(date[0],chosen->{date[0]=chosen;dateButton.setText("Exam: "+chosen.format(shortDate));}));f.addView(dateButton);
        formDialog(existing==null?"New subject":"Edit subject",f,"Save",()->{
            String n=required(name);if(!date[0].isAfter(LocalDate.now())||date[0].isAfter(LocalDate.now().plusDays(730)))throw new IllegalArgumentException("Choose an exam date from tomorrow to two years ahead.");
            if(existing==null)store.array("subjects").put(Store.object("id",Store.id(),"name",n,"exam",date[0].toString()));
            else {try{existing.put("name",n);existing.put("exam",date[0].toString());}catch(JSONException e){throw new IllegalArgumentException(e);}}
            saveAndShow();
        });
    }
    private void chooseDate(LocalDate current,java.util.function.Consumer<LocalDate> selected) {
        LinearLayout f=form();final java.time.YearMonth[] month={java.time.YearMonth.from(current)};final LocalDate[] choice={current};
        LinearLayout navigation=new LinearLayout(this);navigation.setGravity(Gravity.CENTER_VERTICAL);f.addView(navigation);TextView heading=text("",18,ink,true);heading.setGravity(Gravity.CENTER);
        TextView back=button("‹",false,()->{}),next=button("›",false,()->{});back.setContentDescription("Previous month");next.setContentDescription("Next month");navigation.addView(back,new LinearLayout.LayoutParams(dp(48),dp(48)));navigation.addView(heading,new LinearLayout.LayoutParams(0,-2,1));navigation.addView(next,new LinearLayout.LayoutParams(dp(48),dp(48)));gap(f,12);
        LinearLayout grid=column();f.addView(grid);TextView dateLabel=text("",14,accent,true);gap(f,12);f.addView(dateLabel);
        final Runnable[] redraw={null};Runnable draw=()->{heading.setText(month[0].format(DateTimeFormatter.ofPattern("MMMM yyyy",Locale.getDefault())));grid.removeAllViews();LinearLayout weekdays=new LinearLayout(this);grid.addView(weekdays);for(String day:new String[]{"M","T","W","T","F","S","S"}){TextView t=text(day,12,muted,true);t.setGravity(Gravity.CENTER);weekdays.addView(t,new LinearLayout.LayoutParams(0,dp(32),1));}
            int offset=month[0].atDay(1).getDayOfWeek().getValue()-1;for(int week=0;week<6;week++){LinearLayout row=new LinearLayout(this);grid.addView(row);for(int col=0;col<7;col++){int n=week*7+col-offset+1;TextView day=text("",14,ink,true);day.setGravity(Gravity.CENTER);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,dp(44),1);lp.setMargins(dp(1),dp(1),dp(1),dp(1));row.addView(day,lp);if(n<1||n>month[0].lengthOfMonth())continue;LocalDate value=month[0].atDay(n);day.setText(String.valueOf(n));day.setContentDescription(value.toString());boolean enabled=value.isAfter(LocalDate.now())&&!value.isAfter(LocalDate.now().plusDays(730));day.setEnabled(enabled);day.setAlpha(enabled?1:.25f);if(value.equals(choice[0])){day.setBackground(shape(accent,12));day.setTextColor(onAccent);}day.setOnClickListener(v->{choice[0]=value;redraw[0].run();});}}
            dateLabel.setText("Selected · "+choice[0].format(shortDate));};
        // The redraw callback is scoped to this dialog, avoiding any Activity-wide calendar state.
        redraw[0]=draw;
        back.setOnClickListener(v->{java.time.YearMonth previous=month[0].minusMonths(1);if(!previous.isBefore(java.time.YearMonth.from(LocalDate.now()))){month[0]=previous;draw.run();}});next.setOnClickListener(v->{java.time.YearMonth following=month[0].plusMonths(1);if(!following.isAfter(java.time.YearMonth.from(LocalDate.now().plusDays(730)))){month[0]=following;draw.run();}});draw.run();
        formDialog("Choose exam date",f,"Use date",()->{if(!choice[0].isAfter(LocalDate.now())||choice[0].isAfter(LocalDate.now().plusDays(730)))throw new IllegalArgumentException("Choose a future date within two years.");selected.accept(choice[0]);});
    }
    private void chapterForm(JSONObject existing) {
        LinearLayout f=form();EditText name=field(f,"Chapter name",existing==null?"":existing.optString("name"),false);
        EditText mins=field(f,"Minutes remaining (include revision)",existing==null?"60":existing.optString("remaining"),true);
        label(f,"Confidence",13,muted,false);Choice confidence=new Choice();

        confidence.setSelection(existing==null?0:existing.optInt("confidence"));f.addView(confidence);
        if(existing!=null)button(f,"Delete chapter",false,()->confirm("Delete chapter?","Attached notes will also be removed.","Delete","Keep",()->{deleteChapter(existing.optString("id"));saveAndShow();}));
        formDialog(existing==null?"New chapter":"Edit chapter",f,"Save",()->{
            String n=required(name);int m=number(mins,0,10000);
            if(existing!=null && store.find("chapters",existing.optString("id"))==null) throw new IllegalArgumentException("This chapter has been deleted. Close this form.");
            if(existing==null)store.array("chapters").put(Store.object("id",Store.id(),"subject",selectedSubject,"name",n,"remaining",m,"confidence",confidence.getSelectedItemPosition()));
            else try{existing.put("name",n);existing.put("remaining",m);existing.put("confidence",confidence.getSelectedItemPosition());}catch(JSONException e){throw new IllegalArgumentException(e);}
            saveAndShow();
        });
    }
    private void deleteChapter(String id) {
        if(store.root.optString("focusChapter").equals(id))store.setting("focusChapter","");
        JSONArray ns=store.array("notes");for(int i=ns.length()-1;i>=0;i--) {JSONObject n=ns.optJSONObject(i);if(n.optString("chapter").equals(id)){deleteAttachment(n);ns.remove(i);}}
        store.remove("cards","chapter",id);
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
        LinearLayout f=form();label(f,"Import documents, slides, spreadsheets, books or images. Originals are copied for offline access.",14,muted,false);
        button(f,"Import document  +",true,()->{
            if(dialog[0]!=null)dialog[0].dismiss();pendingChapter=id;Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.setType("*/*");i.addCategory(Intent.CATEGORY_OPENABLE);startActivityForResult(i,IMPORT);
        });
        button(f,"Write a note",false,()->{dialog[0].dismiss();editNote(chapter,null);});gap(f,14);
        EditText search=field(f,"Search these notes","",false);
        java.util.List<View> rows=new ArrayList<>();java.util.List<String> searchable=new ArrayList<>();
        JSONArray a=store.array("notes");int count=0;
        for(int i=0;i<a.length();i++){
            JSONObject n=a.optJSONObject(i);if(!n.optString("chapter").equals(id))continue;count++;
            LinearLayout p=panel(f);rows.add(p);searchable.add((n.optString("name")+" "+n.optString("content")).toLowerCase(Locale.ROOT));label(p,n.optString("name"),17,ink,true);
            label(p,n.optString("type").equals("text")?"Written note":"Offline attachment · page "+n.optInt("page",1),12,muted,false);
            button(p,"Open",true,()->{dialog[0].dismiss();if(n.optString("type").equals("text"))editNote(chapter,n);else readFile(n);});
            if(n.optString("type").equals("text"))button(p,"Export as text",false,()->{dialog[0].dismiss();exportNote(n);});
            button(p,"Delete note",false,()->confirm("Delete note?","This removes the copy saved in StudyFlow.","Delete","Keep",()->{deleteAttachment(n);store.remove("notes","id",n.optString("id"));save();dialog[0].dismiss();notes(chapter);}));
        }
        TextView empty=text(count==0?"No notes attached yet.":"No matching notes.",14,muted,false);empty.setVisibility(count==0?View.VISIBLE:View.GONE);f.addView(empty);
        search.addTextChangedListener(new android.text.TextWatcher(){public void beforeTextChanged(CharSequence s,int a,int c,int n){}public void onTextChanged(CharSequence s,int st,int before,int count){String q=s.toString().toLowerCase(Locale.ROOT).trim();int visible=0;for(int i=0;i<rows.size();i++){boolean match=searchable.get(i).contains(q);rows.get(i).setVisibility(match?View.VISIBLE:View.GONE);if(match)visible++;}empty.setVisibility(visible==0?View.VISIBLE:View.GONE);}public void afterTextChanged(android.text.Editable e){}});
        dialog[0]=sheet(chapter.optString("name"),f,"Close",null);
    }
    private void editNote(JSONObject chapter,JSONObject note) {
        if(note!=null){try{note.put("lastOpened",System.currentTimeMillis());}catch(JSONException ignored){}save();}
        LinearLayout f=form();EditText name=field(f,"Note title",note==null?"":note.optString("name"),false);
        EditText content=new EditText(this);styleInput(content);content.setHint("Write a summary, key points, or questions…");content.setText(note==null?"":note.optString("content"));content.setMinLines(6);content.setMaxLines(12);content.setGravity(Gravity.TOP);content.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_MULTI_LINE|InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);f.addView(content);
        formDialog("Written note",f,"Save",()->{
            String title=required(name);
            if(content.getText().length()>200000)throw new IllegalArgumentException("Keep written notes below 200,000 characters.");
            if(store.find("chapters",chapter.optString("id"))==null)throw new IllegalArgumentException("This chapter was deleted.");
            String snapshot=store.root.toString();
            JSONObject target=note==null?null:store.find("notes",note.optString("id"));
            if(note!=null&&target==null)throw new IllegalArgumentException("This note was deleted.");
            if(target==null)store.array("notes").put(Store.object("id",Store.id(),"chapter",chapter.optString("id"),"name",title,"type","text","content",content.getText().toString()));
            else try{target.put("name",title);target.put("content",content.getText().toString());}catch(JSONException e){throw new IllegalArgumentException(e);}
            if(!save()){try{store.root=new JSONObject(snapshot);}catch(JSONException ignored){}throw new IllegalArgumentException("Note could not be saved. Try again.");}toast("Note saved.");
        });
    }
    @Override protected void onActivityResult(int request,int result,Intent data) {
        super.onActivityResult(request,result,data);
        if(request==EXPORT_NOTE){if(result!=RESULT_OK||data==null||data.getData()==null)return;JSONObject note=store.find("notes",pendingExportNote);pendingExportNote=null;if(note==null){toast("This note is no longer available.");return;}Uri destination=data.getData();String content=note.optString("name")+"\n\n"+note.optString("content");worker.execute(()->{try(OutputStream out=getContentResolver().openOutputStream(destination,"wt")){if(out==null)throw new IOException();out.write(content.getBytes(java.nio.charset.StandardCharsets.UTF_8));runOnUiThread(()->toast("Note exported."));}catch(Exception e){runOnUiThread(()->toast("Could not export the note. Choose another location."));}});return;}
        if(request!=IMPORT||result!=RESULT_OK||data==null||data.getData()==null)return;
        Uri uri=data.getData();String chapterId=pendingChapter;
        if(chapterId==null||store.find("chapters",chapterId)==null){toast("Choose a chapter and import again.");return;}
        String mime=getContentResolver().getType(uri);if(mime==null)mime="application/octet-stream";
        String name="Imported note";
        try(android.database.Cursor c=getContentResolver().query(uri,new String[]{OpenableColumns.DISPLAY_NAME},null,null,null)){if(c!=null&&c.moveToFirst())name=c.getString(0);}catch(Exception ignored){}
        if(name==null||name.trim().isEmpty())name="Imported note";
        final String type=mime, title=name, filename=Store.id();toast("Copying note for offline access…");
        worker.execute(()->{
            File file=new File(getFilesDir(),filename);
            try(InputStream in=getContentResolver().openInputStream(uri);OutputStream out=new FileOutputStream(file)){
                if(in==null)throw new IOException("File unavailable");byte[] buffer=new byte[32768];int n;long total=0;
                while((n=in.read(buffer))!=-1){total+=n;if(total>100L*1024*1024)throw new IOException("Use a file smaller than 100 MB.");out.write(buffer,0,n);}
                runOnUiThread(()->{if(isDestroyed()){file.delete();return;}
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
        try{note.put("lastOpened",System.currentTimeMillis());}catch(JSONException ignored){}save();
        reading=true;readerNoteId=note.optString("id");renderToken++;root.removeAllViews();
        LinearLayout reader=column();reader.setPadding(dp(16),dp(8),dp(16),dp(8));root.addView(reader,new LinearLayout.LayoutParams(-1,-1));
        LinearLayout header=new LinearLayout(this);header.setGravity(Gravity.CENTER_VERTICAL);reader.addView(header);
        header.addView(iconButton("back","Back to StudyFlow",this::show));
        TextView title=text(note.optString("name"),17,ink,true);title.setMaxLines(2);title.setEllipsize(android.text.TextUtils.TruncateAt.END);title.setPadding(dp(10),0,dp(8),0);header.addView(title,new LinearLayout.LayoutParams(0,-2,1));
        TextView status=text("Loading…",12,muted,false);status.setPadding(0,dp(10),0,dp(10));reader.addView(status);
        PageScroll sc=new PageScroll();sc.setFillViewport(true);sc.setClipToPadding(false);reader.addView(sc,new LinearLayout.LayoutParams(-1,0,1));
        LinearLayout content=column();sc.addView(content,new ScrollView.LayoutParams(-1,-2));
        ZoomImageView image=new ZoomImageView(this);sc.raster=image;image.setAdjustViewBounds(true);sc.addOnLayoutChangeListener((v,l,t,r,b,ol,ot,or,ob)->{if(b-t>0&&b-t!=ob-ot)image.setMaxHeight(b-t);});image.setContentDescription("Page of "+note.optString("name"));content.addView(image,new LinearLayout.LayoutParams(-1,-2));
        TextView prose=text("",store.root.optInt("readerTextSize",18),ink,false);prose.setPadding(dp(18),dp(18),dp(18),dp(18));prose.setTextIsSelectable(true);sc.selectable=prose;prose.setLineSpacing(dp(7),1);prose.setBackground(shape(card,20));prose.setVisibility(View.GONE);content.addView(prose,new LinearLayout.LayoutParams(-1,-2));
        String type=note.optString("type"),name=note.optString("name");
        boolean pdf=type.equals("application/pdf")||DocumentText.extension(name).equals("pdf");
        boolean isText=!pdf&&DocumentText.supports(name,type);
        final int[] page={Math.max(0,note.optInt("page",1)-1)},count={0};
        final boolean[] busy={false};final DocumentText.Document[] document={null};
        final String[] query={""};
        LinearLayout controls=new LinearLayout(this);controls.setPadding(0,dp(10),0,0);reader.addView(controls);
        TextView prev=button("← Previous",false,()->{}),next=button("Next →",false,()->{});
        LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(0,-2,1);cp.setMargins(0,0,dp(8),0);controls.addView(prev,cp);controls.addView(next,new LinearLayout.LayoutParams(0,-2,1));
        Runnable updateControls=()->{prev.setEnabled(!busy[0]&&page[0]>0);next.setEnabled(!busy[0]&&page[0]<count[0]-1);prev.setAlpha(prev.isEnabled()?1:.4f);next.setAlpha(next.isEnabled()?1:.4f);};
        final Runnable[] render={null};
        Runnable finished=()->{
            busy[0]=false;updateControls.run();sc.scrollTo(0,0);entrance(content);
            status.setText((isText?"Text section ":"Page ")+(page[0]+1)+" / "+count[0]+"  ·  "+(bookmarked(note,page[0]+1)?"★ Saved":"Swipe to turn"));
            try{note.put("page",page[0]+1);}catch(JSONException ignored){}save();
        };
        render[0]=()->{
            if(busy[0])return;busy[0]=true;updateControls.run();
            final int token=++renderToken,index=page[0];status.setText("Opening document…");
            worker.execute(()->{
                try {
                    if(isText) {
                        if(document[0]==null)document[0]=DocumentText.read(file,name);
                        int pages=document[0].pages.size(),safe=Math.min(index,pages-1);String value=document[0].pages.get(safe);
                        runOnUiThread(()->{if(token!=renderToken||isDestroyed())return;count[0]=pages;page[0]=safe;image.setVisibility(View.GONE);prose.setVisibility(View.VISIBLE);showSearchText(prose,value,query[0]);finished.run();});
                    } else if(pdf) {
                        try(ParcelFileDescriptor fd=ParcelFileDescriptor.open(file,ParcelFileDescriptor.MODE_READ_ONLY);PdfRenderer r=new PdfRenderer(fd)){
                            int pages=r.getPageCount();if(pages<1)throw new IOException("Empty PDF");int safe=Math.min(index,pages-1);Bitmap bitmap;
                            try(PdfRenderer.Page p=r.openPage(safe)){float scale=Math.min(2f,1600f/Math.max(p.getWidth(),p.getHeight()));bitmap=Bitmap.createBitmap(Math.max(1,(int)(p.getWidth()*scale)),Math.max(1,(int)(p.getHeight()*scale)),Bitmap.Config.ARGB_8888);bitmap.eraseColor(Color.WHITE);p.render(bitmap,null,null,PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);}
                            runOnUiThread(()->{if(token!=renderToken||isDestroyed()){bitmap.recycle();return;}count[0]=pages;page[0]=safe;image.setVisibility(View.VISIBLE);prose.setVisibility(View.GONE);setPageImage(image,bitmap);finished.run();});
                        }
                    } else {
                        BitmapFactory.Options opts=new BitmapFactory.Options();opts.inJustDecodeBounds=true;BitmapFactory.decodeFile(file.getPath(),opts);
                        if(opts.outWidth<=0||opts.outHeight<=0)throw new IOException("This format needs a compatible app. Tap the menu, then Open with.");
                        opts.inSampleSize=1;while(Math.max(opts.outWidth,opts.outHeight)/opts.inSampleSize>1800)opts.inSampleSize*=2;
                        opts.inJustDecodeBounds=false;Bitmap bitmap=BitmapFactory.decodeFile(file.getPath(),opts);if(bitmap==null)throw new IOException("Image cannot be decoded.");
                        runOnUiThread(()->{if(token!=renderToken||isDestroyed()){bitmap.recycle();return;}count[0]=1;page[0]=0;image.setVisibility(View.VISIBLE);prose.setVisibility(View.GONE);setPageImage(image,bitmap);finished.run();});
                    }
                }catch(Exception|OutOfMemoryError e){runOnUiThread(()->{if(token!=renderToken||isDestroyed())return;busy[0]=false;updateControls.run();status.setText("Preview unavailable");image.setVisibility(View.GONE);prose.setVisibility(View.VISIBLE);prose.setText("This document could not be read here.\n\n"+(e.getMessage()==null?"Try a smaller, unlocked file.":e.getMessage()));button(content,"Open with another app",true,()->openExternal(note));});}
            });
        };
        Runnable previous=()->{if(!busy[0]&&page[0]>0){page[0]--;render[0].run();}},following=()->{if(!busy[0]&&page[0]<count[0]-1){page[0]++;render[0].run();}};
        prev.setOnClickListener(v->previous.run());next.setOnClickListener(v->following.run());sc.previous=previous;sc.next=following;
        header.addView(iconButton("more","Reader tools",()->{
            LinearLayout f=form();final AlertDialog[] menu={null};
            if(count[0]>0&&!busy[0]) {
                label(f,isText?"Reflowed text · original layout and images available through Open with.":"Swipe left/right, or swipe up at the bottom to turn a page.",13,muted,false);
                button(f,"Go to "+(isText?"section":"page"),true,()->{menu[0].dismiss();LinearLayout jump=form();EditText input=field(jump,"Number · 1 to "+count[0],String.valueOf(page[0]+1),true);formDialog("Jump to position",jump,"Open",()->{page[0]=number(input,1,count[0])-1;render[0].run();});});
                button(f,bookmarked(note,page[0]+1)?"★ Remove bookmark":"☆ Bookmark this position",false,()->{toggleBookmark(note,page[0]+1);status.setText((isText?"Text section ":"Page ")+(page[0]+1)+" / "+count[0]+"  ·  "+(bookmarked(note,page[0]+1)?"★ Saved":"Swipe to turn"));menu[0].dismiss();});
                JSONArray marks=note.optJSONArray("bookmarks");if(marks!=null&&marks.length()>0)button(f,"Saved bookmarks ("+marks.length()+")",false,()->{menu[0].dismiss();LinearLayout list=form();final AlertDialog[] bookmarks={null};for(int i=0;i<marks.length();i++){final int at=marks.optInt(i);if(at>0&&at<=count[0])button(list,"★  "+(isText?"Section ":"Page ")+at,false,()->{page[0]=at-1;render[0].run();bookmarks[0].dismiss();});}bookmarks[0]=sheet("Bookmarks",list,"Close",null);});
                if(!isText){label(f,"Pinch or double-tap to zoom. Drag to pan; reset zoom to swipe pages.",13,muted,false);button(f,"Zoom in +",false,()->{image.zoomBy(1.5f);menu[0].dismiss();});button(f,"Zoom out −",false,()->{image.zoomBy(1/1.5f);menu[0].dismiss();});button(f,"Fit page",false,()->{image.resetZoom();menu[0].dismiss();});}
                if(isText) {
                    button(f,"Find in document",false,()->{menu[0].dismiss();LinearLayout search=form();EditText input=field(search,"Find text",query[0],false);formDialog("Find in document",search,"Find next section",()->{String q=required(input);int found=-1;int start=q.equals(query[0])?page[0]+1:page[0];for(int offset=0;offset<count[0];offset++){int at=(start+offset)%count[0];if(document[0].pages.get(at).toLowerCase(Locale.ROOT).contains(q.toLowerCase(Locale.ROOT))){found=at;break;}}if(found<0)throw new IllegalArgumentException("No matches found.");query[0]=q;page[0]=found;render[0].run();});});
                    button(f,"Text size",false,()->{menu[0].dismiss();LinearLayout size=form();EditText input=field(size,"Text size (14–28)",String.valueOf(store.root.optInt("readerTextSize",18)),true);formDialog("Reading comfort",size,"Apply",()->{int n=number(input,14,28);store.setting("readerTextSize",n);save();prose.setTextSize(n);});});
                }
            }
            button(f,"Add written note",false,()->{menu[0].dismiss();JSONObject c=store.find("chapters",note.optString("chapter"));if(c!=null)editNote(c,null);});
            button(f,"Open with another app",false,()->{menu[0].dismiss();openExternal(note);});
            menu[0]=sheet("Reader tools",f,"Close",null);
        }));
        render[0].run();
    }
    private void showSearchText(TextView view,String value,String query) {
        android.text.SpannableString styled=new android.text.SpannableString(value);
        if(!query.isEmpty()) {java.util.regex.Matcher m=java.util.regex.Pattern.compile(java.util.regex.Pattern.quote(query),java.util.regex.Pattern.CASE_INSENSITIVE|java.util.regex.Pattern.UNICODE_CASE).matcher(value);while(m.find()){styled.setSpan(new android.text.style.BackgroundColorSpan(accent),m.start(),m.end(),0);styled.setSpan(new android.text.style.ForegroundColorSpan(bg),m.start(),m.end(),0);}}
        view.setText(styled);
    }
    private boolean bookmarked(JSONObject note,int page) {JSONArray a=note.optJSONArray("bookmarks");if(a!=null)for(int i=0;i<a.length();i++)if(a.optInt(i)==page)return true;return false;}
    private void toggleBookmark(JSONObject note,int page) {
        JSONArray a=note.optJSONArray("bookmarks");if(a==null){a=new JSONArray();try{note.put("bookmarks",a);}catch(JSONException ignored){}}
        for(int i=0;i<a.length();i++)if(a.optInt(i)==page){a.remove(i);save();return;}a.put(page);save();
    }
    private final class PageScroll extends ScrollView {
        TextView selectable;ZoomImageView raster;Runnable previous=()->{},next=()->{};float x,y;long down;boolean top,bottom,multi;
        PageScroll(){super(MainActivity.this);}
        @Override public boolean dispatchTouchEvent(MotionEvent event) {
            if(event.getActionMasked()==MotionEvent.ACTION_DOWN){x=event.getX();y=event.getY();down=event.getEventTime();top=!canScrollVertically(-1);bottom=!canScrollVertically(1);multi=false;}
            if(event.getPointerCount()>1)multi=true;
            if(event.getActionMasked()==MotionEvent.ACTION_UP&&!multi&&(raster==null||!raster.isZoomed())&&(selectable==null||!selectable.hasSelection())&&event.getEventTime()-down<650){
                float dx=event.getX()-x,dy=event.getY()-y;
                boolean horizontal=Math.abs(dx)>dp(72)&&Math.abs(dx)>Math.abs(dy)*1.6f;
                boolean edge=Math.abs(dy)>dp(100)&&Math.abs(dy)>Math.abs(dx)*2&&((dy<0&&bottom)||(dy>0&&top));
                if(horizontal||edge){MotionEvent cancel=MotionEvent.obtain(event);cancel.setAction(MotionEvent.ACTION_CANCEL);super.dispatchTouchEvent(cancel);cancel.recycle();if(horizontal?dx<0:dy<0)next.run();else previous.run();return true;}
            }
            return super.dispatchTouchEvent(event);
        }
    }
    private void openExternal(JSONObject note) {
        Uri uri=Uri.parse("content://"+getPackageName()+".attachments/"+note.optString("file"));
        String mime=note.optString("type","application/octet-stream");
        if(mime.equals("application/octet-stream")||mime.isEmpty()){String guessed=android.webkit.MimeTypeMap.getSingleton().getMimeTypeFromExtension(DocumentText.extension(note.optString("name")));if(guessed!=null)mime=guessed;}
        Intent intent=new Intent(Intent.ACTION_VIEW).setDataAndType(uri,mime).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        intent.setClipData(ClipData.newRawUri(note.optString("name"),uri));
        try{startActivity(intent);}catch(ActivityNotFoundException e){toast("No compatible app is installed for this format. The original stays saved in StudyFlow.");}catch(SecurityException e){toast("This app could not open the document.");}
    }
    private void setPageImage(ImageView image,Bitmap bitmap){Bitmap old=displayedBitmap;image.setImageBitmap(bitmap);displayedBitmap=bitmap;if(old!=null&&old!=bitmap&&!old.isRecycled())old.recycle();}
    private void startSession(Planner.Session s) {
        JSONObject chapter=store.find("chapters",s.topic.id);if(chapter==null)return;
        final AlertDialog[] dialog={null};
        LinearLayout f=form();label(f,s.topic.subject+" · "+s.minutes+" planned minutes",14,muted,false);
        label(f,"Open your notes, then log the minutes you actually studied. Your plan updates only when you save progress.",15,ink,false);
        button(f,"Open chapter notes",true,()->{dialog[0].dismiss();notes(chapter);});
        button(f,"Log study progress",false,()->{dialog[0].dismiss();logProgress(s);});
        button(f,"Start focus timer",true,()->{dialog[0].dismiss();focus(chapter,s.minutes);});
        dialog[0]=sheet(s.topic.title,f,"Close",null);
    }
    private void logProgress(Planner.Session s) {
        LinearLayout f=form();EditText minutes=field(f,"Minutes actually studied","",true);
        label(f,"How well do you understand this chapter?",14,muted,false);Choice confidence=new Choice();f.addView(confidence);
        CheckBox complete=new CheckBox(this);complete.setTextColor(ink);complete.setButtonTintList(ColorStateList.valueOf(accent));complete.setText("I have finished this chapter's planned work");f.addView(complete);
        formDialog("Save your progress",f,"Save progress",()->{
            JSONObject c=store.find("chapters",s.topic.id);if(c==null)throw new IllegalArgumentException("This chapter no longer exists.");int actual=number(minutes,1,720);
            try{c.put("remaining",complete.isChecked()?0:Math.max(0,c.optInt("remaining")-actual));c.put("confidence",confidence.getSelectedItemPosition());}catch(JSONException e){throw new IllegalArgumentException(e);}
            store.array("logs").put(Store.object("id",Store.id(),"chapter",s.topic.id,"date",LocalDate.now().toString(),"minutes",actual));saveAndShow();
        });
    }
    private void settings() {
        title("Make it yours","Quietly powerful.","A focused workspace, tuned to your preferences.");
        LinearLayout appearance=panel(body);label(appearance,"Appearance",21,ink,true);gap(appearance,8);label(appearance,store.root.optString("theme","Midnight")+" · "+store.root.optString("accentMode","Theme")+" colors",14,muted,false);
        button(appearance,"Theme & accent colors  →",false,()->{tab="Appearance";show();});
        LinearLayout rhythm=panel(body);label(rhythm,"Study rhythm",21,ink,true);button(rhythm,"Weekly availability",false,()->budget(false));button(rhythm,"Study insights",false,()->{tab="Insights";show();});
        button(rhythm,"Daily goal",false,this::goalForm);button(rhythm,"Session history",false,()->{tab="History";show();});
        LinearLayout privacy=panel(body);label(privacy,"Your space stays yours",20,ink,true);gap(privacy,8);label(privacy,"No account, ads, analytics or internet permission. Notes are copied into private app storage. Uninstalling removes your data; keep your original files.",14,muted,false);
        LinearLayout follow=panel(body);label(follow,"Follow",21,ink,true);gap(follow,6);label(follow,"Connect with the creator",13,muted,false);gap(follow,16);
        LinearLayout socials=new LinearLayout(this);socials.setGravity(Gravity.CENTER);follow.addView(socials);
        socials.addView(iconButton("instagram","Instagram · __nshd.__",()->openLink("https://www.instagram.com/__nshd.__?stkn=emExd3hxZndzN21o")));
        View spacer=new View(this);socials.addView(spacer,new LinearLayout.LayoutParams(dp(20),1));
        socials.addView(iconButton("whatsapp","WhatsApp · N S H D",()->openLink("https://wa.me/918590455801")));
        gap(body,12);label(body,"STUDYFLOW  /  0.4.0",12,accent,true);gap(body,6);label(body,"Minimal edition · your space, your colors",13,muted,false);
        gap(body,26);TextView credit=text("MADE  BY  N S H D",12,muted,true);credit.setLetterSpacing(.16f);credit.setGravity(Gravity.CENTER);body.addView(credit);gap(body,12);
    }
    private void appearance() {
        button(body,"← Settings",false,()->{tab="Settings";show();});gap(body,20);
        title("Appearance","A space that feels like you.","Choose the surface. Set the color. Keep your focus.");
        LinearLayout preview=panel(body);label(preview,"LIVE PREVIEW",11,accent,true);gap(preview,12);label(preview,"Less noise. More clarity.",23,ink,true);gap(preview,8);label(preview,"Your subjects, notes and progress in one calm workspace.",14,muted,false);
        button(preview,"This is your accent",true,()->toast("Changes apply across StudyFlow."));
        label(body,"Theme",20,ink,true);gap(body,12);
        String[] themes={"Minimal","Minimal Dark","Paper","Midnight","AMOLED"};
        for(String name:themes){LinearLayout p=panel(body);boolean selected=store.root.optString("theme","Midnight").equals(name);label(p,name+(selected?"  ✓":""),18,ink,true);gap(p,5);label(p,name.equals("Minimal")?"Soft white · lavender · fine outlines":name.equals("Minimal Dark")?"Charcoal · quiet contrast":name.equals("AMOLED")?"True black background":name.equals("Paper")?"Warm white workspace":"Deep blue surfaces",13,muted,false);button(p,selected?"Selected":"Use "+name,selected,()->{store.setting("theme",name);saveAndShow();});}
        LinearLayout palette=panel(body);label(palette,"Accent color",20,ink,true);gap(palette,8);label(palette,"Text and controls adjust the shade for readable contrast.",13,muted,false);
        button(palette,"Theme default"+(store.root.optString("accentMode","Theme").equals("Theme")?"  ✓":""),false,()->{store.setting("accentMode","Theme");saveAndShow();});
        String[] colors={"#A374EC","#4263EB","#00866A","#D45B35","#C0447C","#756047","#16858F","#555555"};
        String[] names={"Lavender","Blue","Jade","Terracotta","Rose","Sand","Teal","Graphite"};
        for(int row=0;row<2;row++){LinearLayout swatches=new LinearLayout(this);gap(palette,12);palette.addView(swatches);for(int col=0;col<4;col++){int i=row*4+col;String hex=colors[i];int color=Color.parseColor(hex);TextView swatch=text(store.root.optString("customAccent").equalsIgnoreCase(hex)&&store.root.optString("accentMode").equals("Custom")?"✓":"",20,ThemeColors.on(color),true);swatch.setGravity(Gravity.CENTER);swatch.setContentDescription(names[i]+" accent");swatch.setTooltipText(names[i]);swatch.setBackground(shape(color,16));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,dp(52),1);lp.setMargins(dp(4),0,dp(4),0);swatches.addView(swatch,lp);swatch.setOnClickListener(v->{store.setting("customAccent",hex);store.setting("accentMode","Custom");saveAndShow();});}}
        button(palette,"Custom color · HEX",false,()->{LinearLayout f=form();EditText hex=field(f,"Hex color · #RRGGBB",store.root.optString("customAccent","#A374EC"),false);TextView chip=text("Color preview",16,ink,true);chip.setGravity(Gravity.CENTER);chip.setPadding(0,dp(20),0,dp(20));f.addView(chip);
            Runnable refresh=()->{String value=hex.getText().toString().trim();if(value.matches("#[0-9a-fA-F]{6}")){int c=Color.parseColor(value);chip.setBackground(shape(c,16));chip.setTextColor(ThemeColors.on(c));}};refresh.run();
            hex.addTextChangedListener(new android.text.TextWatcher(){public void beforeTextChanged(CharSequence s,int a,int c,int n){}public void onTextChanged(CharSequence s,int st,int before,int count){refresh.run();}public void afterTextChanged(android.text.Editable e){}});
            formDialog("Choose your accent",f,"Apply color",()->{String value=hex.getText().toString().trim();if(!value.matches("#[0-9a-fA-F]{6}"))throw new IllegalArgumentException("Use six hexadecimal digits, such as #A374EC.");store.setting("customAccent",value);store.setting("accentMode","Custom");saveAndShow();});});
        LinearLayout android=panel(body);label(android,"Match Android",20,ink,true);gap(android,8);label(android,Build.VERSION.SDK_INT>=31?"Use Android's accent palette, based on the wallpaper or colors selected in your phone's system settings. Changes refresh when you return to StudyFlow.":"System wallpaper colors require Android 12 or newer. Custom colors work on this phone.",14,muted,false);
        if(Build.VERSION.SDK_INT>=31)button(android,store.root.optString("accentMode").equals("Android")?"Following Android  ✓":"Use Android / wallpaper colors",true,()->{store.setting("accentMode","Android");saveAndShow();});
        LinearLayout motion=panel(body);label(motion,"Motion",20,ink,true);button(motion,store.root.optBoolean("reduceMotion")?"Reduced motion  ✓":"Smooth animations  ✓",false,()->{store.setting("reduceMotion",!store.root.optBoolean("reduceMotion"));saveAndShow();});
    }
    private void recentDocuments() {
        List<JSONObject> recent=new ArrayList<>();JSONArray notes=store.array("notes");for(int i=0;i<notes.length();i++){JSONObject n=notes.optJSONObject(i);if(n.optLong("lastOpened")>0)recent.add(n);}
        recent.sort((a,b)->Long.compare(b.optLong("lastOpened"),a.optLong("lastOpened")));if(recent.isEmpty())return;
        gap(body,18);label(body,"Pick up where you left off",20,ink,true);gap(body,12);
        for(int i=0;i<Math.min(3,recent.size());i++){JSONObject n=recent.get(i);LinearLayout p=panel(body);label(p,n.optString("name"),17,ink,true);gap(p,4);label(p,n.optString("type").equals("text")?"Written note":"Position "+n.optInt("page",1)+" saved",12,muted,false);button(p,"Continue reading  →",false,()->{JSONObject c=store.find("chapters",n.optString("chapter"));if(c==null)return;if(n.optString("type").equals("text"))editNote(c,n);else readFile(n);});}
    }
    private void insights() {
        button(body,"← Today",false,()->{tab="Today";show();});gap(body,18);title("Study insights","Small steps add up.","Only minutes you actually logged appear here.");
        button(body,"Browse session history",false,()->{tab="History";show();});
        LocalDate today=LocalDate.now();long total=0;int sessions=0;Map<LocalDate,Long> days=new HashMap<>();Map<String,Long> subjects=new LinkedHashMap<>();
        JSONArray logs=store.array("logs");for(int i=0;i<logs.length();i++){JSONObject log=logs.optJSONObject(i);try{LocalDate date=LocalDate.parse(log.optString("date"));long mins=Math.max(0,log.optInt("minutes"));if(date.isAfter(today))continue;days.put(date,days.getOrDefault(date,0L)+mins);total+=mins;sessions++;JSONObject chapter=store.find("chapters",log.optString("chapter"));JSONObject subject=chapter==null?null:store.find("subjects",chapter.optString("subject"));String id=subject==null?"Removed subjects":subject.optString("name");subjects.put(id,subjects.getOrDefault(id,0L)+mins);}catch(Exception ignored){}}
        LinearLayout hero=panel(body);label(hero,total+" minutes",32,accent,true);gap(hero,4);label(hero,sessions+" logged sessions · all time",14,muted,false);
        if(sessions==0){label(body,"Your first logged study session starts your history.",16,muted,false);return;}
        LinearLayout week=panel(body);label(week,"Last 7 days",20,ink,true);gap(week,14);long max=1;for(int i=0;i<7;i++)max=Math.max(max,days.getOrDefault(today.minusDays(i),0L));
        for(int i=6;i>=0;i--){LocalDate day=today.minusDays(i);long minutes=days.getOrDefault(day,0L);label(week,day.format(DateTimeFormatter.ofPattern("EEE d",Locale.getDefault()))+"  ·  "+minutes+" min",13,muted,false);ProgressBar bar=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);bar.setMax(1000);bar.setProgress((int)(minutes*1000/max));bar.setProgressTintList(ColorStateList.valueOf(accent));bar.setProgressBackgroundTintList(ColorStateList.valueOf(line));bar.setContentDescription(minutes+" minutes on "+day);week.addView(bar,new LinearLayout.LayoutParams(-1,dp(10)));gap(week,12);}
        LinearLayout breakdown=panel(body);label(breakdown,"By subject · all time",20,ink,true);gap(breakdown,12);for(Map.Entry<String,Long> entry:subjects.entrySet()){label(breakdown,entry.getKey()+"  ·  "+entry.getValue()+" min",15,ink,false);gap(breakdown,12);}
    }
    // Save each new feature transaction atomically; failed writes restore the in-memory state.
    private void change(Runnable action) {
        String before=store.root.toString();
        try { action.run();store.save(); }
        catch(Exception e) {try{store.root=new JSONObject(before);}catch(JSONException ignored){}throw new IllegalArgumentException("Could not save. Free some storage and try again.");}
    }
    private Map<LocalDate,Integer> studyDays() {
        Map<LocalDate,Integer> days=new HashMap<>();JSONArray logs=store.array("logs");
        for(int i=0;i<logs.length();i++){JSONObject l=logs.optJSONObject(i);try{LocalDate d=LocalDate.parse(l.optString("date"));if(!d.isAfter(LocalDate.now()))days.put(d,days.getOrDefault(d,0)+Math.max(0,l.optInt("minutes")));}catch(Exception ignored){}}
        return days;
    }
    private void goalSummary() {
        int goal=store.root.optInt("goal",30),done=store.usedToday();
        LinearLayout p=panel(body);label(p,"DAILY GOAL",11,accent,true);gap(p,8);
        label(p,done+" / "+goal+" min",24,ink,true);
        ProgressBar progress=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);progress.setMax(goal);progress.setProgress(Math.min(goal,done));progress.setProgressTintList(ColorStateList.valueOf(accent));progress.setProgressBackgroundTintList(ColorStateList.valueOf(line));p.addView(progress,new LinearLayout.LayoutParams(-1,dp(12)));
        label(p,StudyTools.streak(studyDays(),LocalDate.now())+" day study streak · any logged study counts",12,muted,false);
        button(p,done>=goal?"Goal reached · edit goal":"Set daily goal",false,this::goalForm);
    }
    private void goalForm() {
        LinearLayout f=form();EditText value=field(f,"Daily goal in minutes",String.valueOf(store.root.optInt("goal",30)),true);
        label(f,"Your personal target is separate from planning availability. Streaks count consecutive days with logged study; today can still be in progress.",14,muted,false);
        formDialog("A small daily commitment",f,"Save goal",()->{int n=number(value,1,720);change(()->store.setting("goal",n));show();});
    }
    private void exams() {
        title("Exam countdowns","See what is ahead.","Days remaining and chapter completion, ordered by exam date.");
        List<JSONObject> subjects=new ArrayList<>();JSONArray a=store.array("subjects");for(int i=0;i<a.length();i++)subjects.add(a.optJSONObject(i));subjects.sort(Comparator.comparing(x->x.optString("exam")));
        if(subjects.isEmpty())label(body,"Add a subject and exam date in Library to begin.",16,muted,false);
        for(JSONObject s:subjects){long days=java.time.temporal.ChronoUnit.DAYS.between(LocalDate.now(),LocalDate.parse(s.optString("exam")));int total=0,done=0,remaining=0;
            JSONArray cs=store.array("chapters");for(int i=0;i<cs.length();i++){JSONObject c=cs.optJSONObject(i);if(c.optString("subject").equals(s.optString("id"))){total++;remaining+=c.optInt("remaining");if(c.optInt("remaining")==0)done++;}}
            LinearLayout p=panel(body);label(p,days==0?"EXAM TODAY":days<0?"EXAM PASSED":days+" DAYS TO GO",12,accent,true);gap(p,10);label(p,s.optString("name"),23,ink,true);label(p,s.optString("exam")+" · "+done+" / "+total+" chapters studied",14,muted,false);label(p,remaining+" planned minutes remaining",14,muted,false);
            button(p,"Open subject",false,()->{selectedSubject=s.optString("id");tab="Library";show();});
        }
    }
    private void workspaceSearch() {
        title("Workspace search","Find your next thought.","Search subjects, chapters, written notes and attachment names. Document contents are searchable inside their reader.");
        EditText query=field(body,"Search your workspace","",false);LinearLayout results=column();body.addView(results);
        Runnable update=()->{results.removeAllViews();String q=query.getText().toString().trim().toLowerCase(Locale.ROOT);if(q.isEmpty()){label(results,"Type to find something in your workspace.",14,muted,false);return;}int count=0;
            for(String type:new String[]{"subjects","chapters","notes"}){JSONArray a=store.array(type);for(int i=0;i<a.length();i++){JSONObject item=a.optJSONObject(i);String searchable=item.optString("name")+(type.equals("notes")&&item.optString("type").equals("text")?" "+item.optString("content"):"");if(!searchable.toLowerCase(Locale.ROOT).contains(q))continue;count++;if(count>50)continue;
                LinearLayout p=panel(results);label(p,type.toUpperCase(Locale.ROOT),11,accent,true);label(p,item.optString("name"),18,ink,true);button(p,"Open",false,()->{if(type.equals("subjects")){selectedSubject=item.optString("id");tab="Library";show();}else if(type.equals("chapters"))notes(item);else{JSONObject c=store.find("chapters",item.optString("chapter"));if(c!=null){if(item.optString("type").equals("text"))editNote(c,item);else readFile(item);}}});
            }}label(results,count==0?"No matches. Try another word.":count>50?"Showing 50 matches. Refine your search.":count+" matches",14,muted,false);
        };
        query.addTextChangedListener(new android.text.TextWatcher(){public void beforeTextChanged(CharSequence s,int a,int c,int n){}public void onTextChanged(CharSequence s,int st,int before,int count){update.run();}public void afterTextChanged(android.text.Editable e){}});update.run();
    }
    private int historyLimit=30;
    private void history() {
        title("Session history","Your effort, remembered.","Actual logged minutes. Flashcard reviews are kept separate from study-time logs.");
        List<JSONObject> logs=new ArrayList<>();JSONArray a=store.array("logs");for(int i=0;i<a.length();i++)logs.add(a.optJSONObject(i));logs.sort((x,y)->y.optString("date").compareTo(x.optString("date")));
        if(logs.isEmpty())label(body,"Log a study session to start your history.",16,muted,false);
        for(int i=0;i<Math.min(historyLimit,logs.size());i++){JSONObject l=logs.get(i),c=store.find("chapters",l.optString("chapter"));LinearLayout p=panel(body);label(p,l.optString("date")+" · "+l.optInt("minutes")+" MIN",12,accent,true);label(p,c==null?"Removed chapter":c.optString("name"),20,ink,true);if(c!=null){JSONObject s=store.find("subjects",c.optString("subject"));if(s!=null)label(p,s.optString("name"),13,muted,false);button(p,"Open chapter notes",false,()->notes(c));}}
        if(logs.size()>historyLimit)button(body,"Show 30 more",false,()->{historyLimit+=30;show();});
    }
    private void flashcards() {
        title("Active recall","Make knowledge stick.","Write a question, reveal the answer, then choose when to revisit it. Reviews do not log study minutes.");
        int due=0;JSONArray cards=store.array("cards");for(int i=0;i<cards.length();i++)if(cardDue(cards.optJSONObject(i)))due++;
        LinearLayout p=panel(body);label(p,due+" cards ready",26,accent,true);label(p,cards.length()+" cards in your workspace",14,muted,false);if(due>0)button(p,"Start due review",true,()->reviewNext(0));
        JSONArray cs=store.array("chapters");if(cs.length()==0)label(body,"Create a chapter in Library, then add your first flashcard.",16,muted,false);
        for(int i=0;i<cs.length();i++){JSONObject c=cs.optJSONObject(i);int count=0;for(int j=0;j<cards.length();j++)if(cards.optJSONObject(j).optString("chapter").equals(c.optString("id")))count++;button(body,c.optString("name")+" · "+count+" cards",false,()->cardList(c));}
    }
    private boolean cardDue(JSONObject c) {return c.optString("due",LocalDate.now().toString()).compareTo(LocalDate.now().toString())<=0;}
    private void cardList(JSONObject chapter) {
        LinearLayout f=form();final AlertDialog[] d={null};button(f,"Add flashcard",true,()->{d[0].dismiss();cardForm(chapter,null);});JSONArray a=store.array("cards");int count=0;
        for(int i=0;i<a.length();i++){JSONObject c=a.optJSONObject(i);if(!c.optString("chapter").equals(chapter.optString("id")))continue;count++;LinearLayout p=panel(f);label(p,c.optString("question"),18,ink,true);label(p,cardDue(c)?"Ready for review":"Next review · "+c.optString("due"),12,accent,false);button(p,"Edit card",false,()->{d[0].dismiss();cardForm(chapter,c);});}
        if(count==0)label(f,"Try a definition, a mechanism, or a question you keep forgetting.",14,muted,false);d[0]=sheet(chapter.optString("name"),f,"Close",null);
    }
    private void cardForm(JSONObject chapter,JSONObject existing) {
        LinearLayout f=form();EditText question=field(f,"Question",existing==null?"":existing.optString("question"),false),answer=field(f,"Answer",existing==null?"":existing.optString("answer"),false);answer.setSingleLine(false);answer.setMinLines(3);answer.setGravity(Gravity.TOP);
        final AlertDialog[] dialog={null};
        if(existing!=null)button(f,"Delete flashcard",false,()->confirm("Delete flashcard?","Its review schedule will also be removed.","Delete","Keep",()->{change(()->store.remove("cards","id",existing.optString("id")));dialog[0].dismiss();show();}));
        dialog[0]=formDialog(existing==null?"New flashcard":"Edit flashcard",f,"Save card",()->{String q=required(question),a=required(answer);if(q.length()>2000||a.length()>10000)throw new IllegalArgumentException("Keep questions under 2,000 and answers under 10,000 characters.");if(store.find("chapters",chapter.optString("id"))==null)throw new IllegalArgumentException("This chapter no longer exists.");if(existing!=null&&store.find("cards",existing.optString("id"))==null)throw new IllegalArgumentException("This card no longer exists.");
            change(()->{if(existing==null)store.array("cards").put(Store.object("id",Store.id(),"chapter",chapter.optString("id"),"question",q,"answer",a,"due",LocalDate.now().toString(),"interval",0));else try{JSONObject current=store.find("cards",existing.optString("id"));current.put("question",q);current.put("answer",a);}catch(JSONException e){throw new IllegalArgumentException(e);}});show();
        });
    }
    private void reviewNext(int reviewed) {
        JSONObject next=null;JSONArray a=store.array("cards");for(int i=0;i<a.length();i++){JSONObject c=a.optJSONObject(i);if(cardDue(c)&&(next==null||c.optString("due").compareTo(next.optString("due"))<0))next=c;}
        if(next==null){LinearLayout f=form();label(f,reviewed+" cards reviewed. You are up to date for today.",18,ink,true);sheet("A little stronger",f,"Done",null);show();return;}
        final JSONObject c=next;LinearLayout f=form();label(f,reviewed+" reviewed this session",12,accent,true);gap(f,16);label(f,c.optString("question"),23,ink,true);gap(f,16);LinearLayout reveal=column();reveal.setVisibility(View.GONE);label(reveal,c.optString("answer"),18,ink,false);gap(reveal,16);label(reveal,"When should this card return?",14,muted,false);final AlertDialog[] dialog={null};
        for(int rating=0;rating<3;rating++){int days=StudyTools.interval(c.optInt("interval"),rating);String name=new String[]{"Again","Good","Easy"}[rating];button(reveal,name+" · "+days+(days==1?" day":" days"),rating==1,()->{try{change(()->{try{JSONObject current=store.find("cards",c.optString("id"));current.put("interval",days);current.put("due",LocalDate.now().plusDays(days).toString());current.put("reviews",current.optInt("reviews")+1);}catch(JSONException e){throw new IllegalArgumentException(e);}});dialog[0].dismiss();reviewNext(reviewed+1);}catch(IllegalArgumentException e){toast(e.getMessage());}});}
        TextView flip=button("Reveal answer",true,()->{reveal.setVisibility(View.VISIBLE);entrance(reveal);});f.addView(flip);f.addView(reveal);flip.setOnClickListener(v->{flip.setVisibility(View.GONE);reveal.setVisibility(View.VISIBLE);entrance(reveal);});dialog[0]=sheet("Recall before revealing",f,"Finish for now",()->{tab="Cards";show();});
    }

    private void exportNote(JSONObject note) {
        pendingExportNote=note.optString("id");Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT);i.setType("text/plain");i.addCategory(Intent.CATEGORY_OPENABLE);i.putExtra(Intent.EXTRA_TITLE,note.optString("name","StudyFlow note").replaceAll("[/\\\\:*?\"<>|]","_")+".txt");
        try{startActivityForResult(i,EXPORT_NOTE);}catch(ActivityNotFoundException e){toast("No file-saving app is available on this phone.");}
    }
    private View iconButton(String symbol,String description,Runnable action) {
        ImageButton b=new ImageButton(this);b.setImageDrawable(new FlowIcon(symbol,accent));b.setContentDescription(description);b.setTooltipText(description);
        b.setPadding(dp(13),dp(13),dp(13),dp(13));b.setBackground(new RippleDrawable(ColorStateList.valueOf(0x33808080),shape(card,16),null));
        b.setLayoutParams(new LinearLayout.LayoutParams(dp(50),dp(50)));b.setOnClickListener(v->action.run());return b;
    }
    private void openLink(String url) {try{startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(url)));}catch(ActivityNotFoundException e){toast("Install a browser or the corresponding app to open this link.");}}
    private void focus(JSONObject chapter,int suggested) {
        String active=store.root.optString("focusChapter");JSONObject existing=store.find("chapters",active);
        if(!active.isEmpty()&&existing!=null){openFocus(existing);return;}
        LinearLayout f=form();label(f,"Choose a quiet stretch for "+chapter.optString("name")+". The timer pauses when you close it or leave the app.",14,muted,false);gap(f,14);
        EditText duration=field(f,"Focus minutes (1–120)",String.valueOf(store.root.optInt("focusMinutes",suggested)),true);
        formDialog("Make time to focus",f,"Start focus",()->{int minutes=number(duration,1,120);store.setting("focusMinutes",minutes);store.setting("focusChapter",chapter.optString("id"));store.setting("focusTotal",minutes*60000L);store.setting("focusRemaining",minutes*60000L);if(!save())throw new IllegalArgumentException("Could not save timer.");handler.post(()->{openFocus(chapter);if(focusClock!=null)focusClock.start();});});
    }
    private void openFocus(JSONObject chapter) {
        if(focusClock!=null)focusClock.pause();
        LinearLayout f=form();label(f,chapter.optString("name"),18,ink,true);gap(f,20);
        TextView clock=text("",54,accent,true);clock.setGravity(Gravity.CENTER);f.addView(clock);gap(f,8);
        TextView caption=text("",13,muted,false);caption.setGravity(Gravity.CENTER);f.addView(caption);gap(f,18);
        FocusClock session=new FocusClock(clock,caption);focusClock=session;
        TextView toggle=button("Start / resume",true,()->{});f.addView(toggle);session.toggle=toggle;
        toggle.setOnClickListener(v->{if(session.running)session.pause();else session.start();});
        final AlertDialog[] dialog={null};
        button(f,"Finish and log progress",false,()->{
            session.pause();long studied=store.root.optLong("focusTotal")-session.remaining;
            if(studied<60000){toast("Study for at least one minute before logging this timer.");return;}
            dialog[0].dismiss();LinearLayout log=form();
            EditText actual=field(log,"Minutes studied",String.valueOf(studied/60000),true);
            label(log,"Confidence after studying",13,muted,false);Choice confidence=new Choice();confidence.setSelection(chapter.optInt("confidence"));log.addView(confidence);
            formDialog("Save focused study",log,"Save progress",()->{
                JSONObject c=store.find("chapters",chapter.optString("id"));if(c==null)throw new IllegalArgumentException("Chapter no longer exists.");
                if(!store.root.optString("focusChapter").equals(c.optString("id")))throw new IllegalArgumentException("This session was already saved.");
                int minutes=number(actual,1,720);String snapshot=store.root.toString();
                try{c.put("remaining",Math.max(0,c.optInt("remaining")-minutes));c.put("confidence",confidence.getSelectedItemPosition());}catch(JSONException e){throw new IllegalArgumentException(e);}
                store.array("logs").put(Store.object("id",Store.id(),"chapter",c.optString("id"),"date",LocalDate.now().toString(),"minutes",minutes));store.setting("focusChapter","");
                if(!save()){try{store.root=new JSONObject(snapshot);}catch(JSONException ignored){}throw new IllegalArgumentException("Could not save progress. Please try again.");}show();
            });
        });
        button(f,"Discard timer",false,()->{session.pause();confirm("Discard focus session?","No study minutes will be logged. Keep the timer to resume later.","Discard","Keep timer",()->{store.setting("focusChapter","");save();dialog[0].dismiss();show();});});
        dialog[0]=sheet("Focus time",f,"Pause & close",null);dialog[0].setOnDismissListener(d->{session.pause();if(focusClock==session)focusClock=null;});session.update();
    }
    private final class FocusClock {
        final TextView clock,caption;TextView toggle;long remaining,started;boolean running;CountDownTimer timer;
        FocusClock(TextView clock,TextView caption){this.clock=clock;this.caption=caption;remaining=Math.max(0,store.root.optLong("focusRemaining"));}
        void start(){if(running||remaining<=0)return;running=true;started=SystemClock.elapsedRealtime();long duration=remaining;timer=new CountDownTimer(duration,250){public void onTick(long left){update();}public void onFinish(){remaining=0;running=false;store.setting("focusRemaining",0);save();update();}}.start();update();}
        long left(){return running?Math.max(0,remaining-(SystemClock.elapsedRealtime()-started)):remaining;}
        void pause(){if(running){remaining=left();running=false;if(timer!=null)timer.cancel();store.setting("focusRemaining",remaining);save();}update();}
        void update(){long seconds=(left()+999)/1000;clock.setText(String.format(Locale.getDefault(),"%02d:%02d",seconds/60,seconds%60));caption.setText(seconds==0?"Session complete · log your progress":running?"One chapter. One focused step.":"Paused · resume when you are ready");if(toggle!=null){toggle.setText(running?"Pause":"Resume focus");toggle.setEnabled(seconds>0);toggle.setAlpha(seconds>0?1:.4f);}}
    }
    @Override protected void onStop(){if(focusClock!=null)focusClock.pause();super.onStop();}
    private boolean save() { try{store.save();return true;}catch(IOException e){toast("Could not save. Free some device storage and try again before closing.");return false;} }
    private void saveAndShow() { if(save())show(); }
    private void toast(String value) { Toast.makeText(this,value,Toast.LENGTH_LONG).show(); }
    @Override public void onBackPressed(){if(reading){show();}else if(selectedSubject!=null){selectedSubject=null;show();}else if(!tab.equals("Today")){tab="Today";show();}else super.onBackPressed();}
    @Override protected void onDestroy(){renderToken++;handler.removeCallbacksAndMessages(null);worker.shutdown();super.onDestroy();}
    private final class Ring extends View {
        private final Paint p=new Paint(3);private final float fraction;private final int percent;private float shown;private android.animation.ValueAnimator animation;
        Ring(int done,int goal){super(MainActivity.this);fraction=Math.min(1,done/(float)goal);percent=Math.round(fraction*100);setContentDescription(percent+" percent of daily study target");}
        @Override protected void onAttachedToWindow(){super.onAttachedToWindow();if((store!=null&&store.root.optBoolean("reduceMotion"))){shown=fraction;return;}animation=android.animation.ValueAnimator.ofFloat(0,fraction);animation.setDuration(650);animation.setInterpolator(new DecelerateInterpolator());animation.addUpdateListener(a->{shown=(float)a.getAnimatedValue();invalidate();});animation.start();}
        @Override protected void onDetachedFromWindow(){if(animation!=null)animation.cancel();super.onDetachedFromWindow();}
        @Override protected void onDraw(Canvas c){super.onDraw(c);float w=getWidth(),h=getHeight();p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(dp(6));p.setStrokeCap(Paint.Cap.ROUND);p.setColor(line);RectF r=new RectF(dp(6),dp(6),w-dp(6),h-dp(6));c.drawArc(r,0,360,false,p);p.setColor(accent);c.drawArc(r,-90,360*shown,false,p);p.setStyle(Paint.Style.FILL);p.setTextSize(dp(18));p.setTypeface(Typeface.DEFAULT_BOLD);p.setTextAlign(Paint.Align.CENTER);c.drawText(percent+"%",w/2,h/2-(p.ascent()+p.descent())/2,p);}
    }
}
