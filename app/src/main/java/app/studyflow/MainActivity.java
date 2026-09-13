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
    private final NavigationTrail navigation=new NavigationTrail();
    private ScrollView workspaceScroll;
    private boolean restoringNavigation;
    private String pendingLayoutNote;
    private static final int IMPORT_LAYOUT=43;
    private LinearLayout root,body,nav;
    private String tab="Today",selectedSubject=null,pendingChapter=null;
    private int bg,card,ink,muted,line,accent,onAccent,accentText;
    private android.webkit.WebView officeWeb;
    private boolean minimal,lightTheme;
    private String pendingExportNote;
    private static final int EXPORT_NOTE=42;
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private final Handler handler=new Handler(Looper.getMainLooper());
    private String renderedTab;
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
        if(state!=null) { tab=state.getString("tab","Today"); selectedSubject=state.getString("subject"); pendingChapter=state.getString("pending");pendingExportNote=state.getString("exportNote");pendingLayoutNote=state.getString("layoutNote");restoreTrail(state.getString("trail")); }
        restoringNavigation=state!=null&&navigation.current!=null;show();
        if(state!=null){String id=state.getString("reader");JSONObject n=id==null?null:store.find("notes",id);if(n!=null){String mode=state.getString("readerMode","");if(mode.equals("preview"))readOffice(n);else readFile(n,mode.equals("text"));}}restoringNavigation=false;
    }
    @Override public void onSaveInstanceState(Bundle state) {
        super.onSaveInstanceState(state); state.putString("tab",tab); state.putString("subject",selectedSubject); state.putString("pending",pendingChapter);state.putString("exportNote",pendingExportNote);state.putString("layoutNote",pendingLayoutNote);state.putString("trail",saveTrail());if(navigation.current!=null)state.putString("readerMode",navigation.current.mode);if(reading)state.putString("reader",readerNoteId);
    }
    private int dp(float n) { return Math.round(n*getResources().getDisplayMetrics().density); }
    private final class OfficeWebView extends android.webkit.WebView {
        float downX,downY,fitScale=1;boolean multi;Runnable previous=()->{},next=()->{};
        OfficeWebView(){super(MainActivity.this);}
        @Override public boolean onTouchEvent(android.view.MotionEvent event){
            if(event.getActionMasked()==android.view.MotionEvent.ACTION_DOWN){downX=event.getX();downY=event.getY();multi=false;}
            if(event.getPointerCount()>1)multi=true;
            if(event.getActionMasked()==android.view.MotionEvent.ACTION_UP&&!multi&&getScale()<=fitScale*1.05f){float dx=event.getX()-downX,dy=event.getY()-downY;if(Math.abs(dx)>dp(90)&&Math.abs(dx)>Math.abs(dy)*2){android.view.MotionEvent cancel=android.view.MotionEvent.obtain(event);cancel.setAction(android.view.MotionEvent.ACTION_CANCEL);super.onTouchEvent(cancel);cancel.recycle();if(dx<0)next.run();else previous.run();return true;}}
            return super.onTouchEvent(event);
        }
    }
    private void closeOffice(){if(officeWeb!=null){android.webkit.WebView old=officeWeb;officeWeb=null;old.stopLoading();if(old.getParent() instanceof ViewGroup)((ViewGroup)old.getParent()).removeView(old);old.destroy();}}
    private void readOffice(JSONObject note) {
        rememberLocation(note.optString("id"),"preview");
        closeOffice();reading=true;readerNoteId=note.optString("id");final int token=++renderToken;root.removeAllViews();
        LinearLayout layout=column();layout.setPadding(dp(14),dp(8),dp(14),dp(8));root.addView(layout,new LinearLayout.LayoutParams(-1,-1));
        LinearLayout header=new LinearLayout(this);header.setGravity(Gravity.CENTER_VERTICAL);header.addView(iconButton("back","Back to previous page",this::navigateBack));TextView name=text(note.optString("name"),16,ink,true);name.setMaxLines(2);header.addView(name,new LinearLayout.LayoutParams(0,-2,1));header.addView(iconButton("external","Open original in Office viewer",()->openExternal(note)));layout.addView(header);
        TextView status=text("Preparing visual preview…",12,muted,false);status.setPadding(0,dp(8),0,dp(8));layout.addView(status);
        OfficeWebView web=new OfficeWebView();officeWeb=web;web.setBackgroundColor(0xffe9e9ed);
        android.webkit.WebSettings ws=web.getSettings();ws.setJavaScriptEnabled(false);ws.setAllowFileAccess(false);ws.setAllowContentAccess(false);ws.setBlockNetworkLoads(true);ws.setBuiltInZoomControls(true);ws.setDisplayZoomControls(false);ws.setUseWideViewPort(true);ws.setLoadWithOverviewMode(true);ws.setDefaultTextEncodingName("UTF-8");
        web.setWebViewClient(new android.webkit.WebViewClient(){@Override public void onPageFinished(android.webkit.WebView v,String url){if(officeWeb==web)web.fitScale=web.getScale();}@Override public boolean shouldOverrideUrlLoading(android.webkit.WebView view,android.webkit.WebResourceRequest request){return true;}@Override public android.webkit.WebResourceResponse shouldInterceptRequest(android.webkit.WebView view,android.webkit.WebResourceRequest request){return new android.webkit.WebResourceResponse("text/plain","UTF-8",new ByteArrayInputStream(new byte[0]));}});
        layout.addView(web,new LinearLayout.LayoutParams(-1,0,1));LinearLayout controls=new LinearLayout(this);layout.addView(controls);TextView previous=button("Previous",false,()->{}),next=button("Next",false,()->{});controls.addView(previous,new LinearLayout.LayoutParams(0,-2,1));controls.addView(next,new LinearLayout.LayoutParams(0,-2,1));previous.setEnabled(false);next.setEnabled(false);
        worker.execute(()->{try{OfficePreview.Preview doc=OfficePreview.read(new File(getFilesDir(),note.optString("file")),note.optString("name"));runOnUiThread(()->{if(token!=renderToken||isDestroyed()||officeWeb!=web)return;final int[] at={Math.max(0,Math.min(doc.pages.size()-1,note.optInt("visualPage",1)-1))};
            Runnable render=()->{web.loadDataWithBaseURL("https://studyflow.invalid/",doc.pages.get(at[0]),"text/html","UTF-8",null);status.setText((DocumentText.extension(note.optString("name")).equals("pptx")?"PPTX · Slide "+(at[0]+1)+" / "+doc.pages.size():"DOCX · visual preview")+" · pinch to zoom");previous.setEnabled(at[0]>0);next.setEnabled(at[0]<doc.pages.size()-1);previous.setAlpha(previous.isEnabled()?1:.4f);next.setAlpha(next.isEnabled()?1:.4f);try{note.put("visualPage",at[0]+1);note.put("lastOpened",System.currentTimeMillis());}catch(JSONException ignored){}save();};
            previous.setOnClickListener(v->{if(at[0]>0){at[0]--;render.run();}});next.setOnClickListener(v->{if(at[0]<doc.pages.size()-1){at[0]++;render.run();}});
            web.previous=()->{if(at[0]>0){at[0]--;render.run();}};web.next=()->{if(at[0]<doc.pages.size()-1){at[0]++;render.run();}};
            if(doc.pages.size()==1)controls.setVisibility(View.GONE);
            button(layout,"Preview details / original layout",false,()->{LinearLayout f=form();label(f,"Your original file is unchanged. This is an offline visual preview; no PDF conversion is performed.",14,ink,false);gap(f,12);label(f,doc.notice,14,muted,false);final AlertDialog[] details={null};button(f,"Text view & search",false,()->{details[0].dismiss();readFile(note,true);});button(f,"Open original in Office viewer",true,()->openExternal(note));details[0]=sheet("Document fidelity",f,"Close",null);});render.run();
        });}catch(Exception|OutOfMemoryError e){runOnUiThread(()->{if(token!=renderToken||isDestroyed())return;status.setText("Visual preview unavailable. Open the original with a compatible Office viewer.");button(layout,"Open original document",true,()->openExternal(note));});}});
    }
    private String actionIcon(String title){String t=title.toLowerCase(Locale.ROOT);if(t.contains("search")||t.contains("find"))return "search";if(t.contains("back")||t.startsWith("←"))return "back";if(t.contains("add")||t.contains("create")||t.contains("new"))return "plus";if(t.contains("focus")||t.contains("time"))return "clock";if(t.contains("exam")||t.contains("plan")||t.contains("date"))return "calendar";if(t.contains("insight")||t.contains("history"))return "chart";if(t.contains("card")||t.contains("review"))return "cards";if(t.contains("theme")||t.contains("color")||t.contains("settings"))return "settings";return "arrow";}
    private TextView navItem(String item){boolean selected=tab.equals(item);TextView v=text(item,10,selected?ink:muted,true);v.setGravity(Gravity.CENTER);v.setPadding(dp(4),dp(9),dp(4),dp(8));FlowIcon icon=new FlowIcon(item.equals("Today")?"home":item.equals("Library")?"book":item.equals("Plan")?"calendar":"chart",selected?ink:muted);icon.setBounds(0,0,dp(22),dp(22));v.setCompoundDrawables(null,icon,null,null);v.setCompoundDrawablePadding(dp(5));v.setBackground(shape(selected?ThemeColors.blend(accent,card,.82f):bg,12));v.setMinHeight(dp(56));v.setFocusable(true);v.setContentDescription(item+(selected?", selected":""));v.setSelected(selected);v.setOnClickListener(x->{tab=item;selectedSubject=null;show();});v.setStateListAnimator(pressAnimator());return v;}
    private void minimalGrid(LinearLayout target,String[] names,String[] icons,Runnable[] actions){for(int i=0;i<names.length;i+=2){LinearLayout row=new LinearLayout(this);target.addView(row);for(int j=i;j<Math.min(i+2,names.length);j++){final int n=j;LinearLayout tile=column();tile.setPadding(dp(16),dp(18),dp(16),dp(18));GradientDrawable surface=shape(card,16);surface.setStroke(dp(1),line);tile.setBackground(new RippleDrawable(ColorStateList.valueOf(0x18808080),surface,null));ImageView icon=new ImageView(this);icon.setImageDrawable(new FlowIcon(icons[j],ink));tile.addView(icon,new LinearLayout.LayoutParams(dp(25),dp(25)));gap(tile,18);label(tile,names[j],15,ink,true);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,-2,1);lp.setMargins(j==i?0:dp(6),0,j==i?dp(6):0,dp(12));row.addView(tile,lp);tile.setFocusable(true);tile.setContentDescription(names[j]);tile.setOnClickListener(v->actions[n].run());tile.setStateListAnimator(pressAnimator());}}}
    private void go(String destination){tab=destination;selectedSubject=null;show();}
    private void minimalToday(){
        title(LocalDate.now().format(shortDate),"Space to grow.","A small step today. A clearer mind tomorrow.");
        LinearLayout hero=panel(body);LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);LinearLayout words=column();label(words,"YOUR DAILY PRACTICE",10,muted,true);gap(words,12);label(words,store.usedToday()+" min",36,ink,false);label(words,"of "+store.root.optInt("goal",30)+" min today",13,muted,false);row.addView(words,new LinearLayout.LayoutParams(0,-2,1));row.addView(new MinimalArt(this,ink,accent,line),new LinearLayout.LayoutParams(dp(120),dp(120)));hero.addView(row);gap(hero,10);
        label(hero,StudyTools.streak(studyDays(),LocalDate.now())+" day streak",13,accent,true);button(hero,"Set daily goal",false,this::goalForm);
        JSONObject active=store.find("chapters",store.root.optString("focusChapter"));if(active!=null)button(body,"Resume focus · "+active.optString("name"),true,()->openFocus(active));
        gap(body,14);minimalGrid(body,new String[]{"My subjects","Recall cards","Exam calendar","Find a note"},new String[]{"book","cards","calendar","search"},new Runnable[]{()->go("Library"),()->go("Cards"),()->go("Exams"),()->go("Search")});
        learningStructure();
        weeklySummary();
        studyToolEntry();
        Planner.Result plan=store.plan();if(plan.unscheduledMinutes>0)warning(plan.unscheduledMinutes+" minutes need more room before your exams.");label(body,"Up next",22,ink,true);gap(body,12);int count=0;
        for(Planner.Session session:plan.sessions)if(session.date.equals(LocalDate.now())){sessionCard(session);if(++count==3)break;}
        if(count==0){label(body,store.array("subjects").length()==0?"Your workspace starts with one subject.":"No planned sessions today. Make room for rest or a short review.",15,muted,false);button(body,store.array("subjects").length()==0?"Create a subject":"Browse your plan",true,()->{if(store.array("subjects").length()==0)subjectForm(null);else go("Plan");});}else button(body,"See the full plan",false,()->go("Plan"));
        recentDocuments();
    }
    private void minimalLibrary(){if(selectedSubject!=null&&store.find("subjects",selectedSubject)!=null){subjectDetail();return;}title("Your library","Ideas live here.","Subjects, chapters, and everything you are learning.");button(body,"Search workspace",false,()->go("Search"));gap(body,16);minimalGrid(body,new String[]{"New subject","Recall cards"},new String[]{"plus","cards"},new Runnable[]{()->subjectForm(null),()->go("Cards")});
        JSONArray a=store.array("subjects");List<JSONObject> subjects=new ArrayList<>();for(int i=0;i<a.length();i++)subjects.add(a.optJSONObject(i));subjects.sort((x,y)->Boolean.compare(y.optBoolean("pinned"),x.optBoolean("pinned")));
        if(subjects.isEmpty())label(body,"Add a subject to give your notes a home.",16,muted,false);
        for(JSONObject subject:subjects){LinearLayout p=panel(body);LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);LinearLayout info=column();label(info,subject.optString("name"),24,ink,true);label(info,"EXAM · "+subject.optString("exam"),11,muted,false);row.addView(info,new LinearLayout.LayoutParams(0,-2,1));row.addView(iconButton("pin",subject.optBoolean("pinned")?"Unpin subject":"Pin subject",()->{try{subject.put("pinned",!subject.optBoolean("pinned"));}catch(JSONException ignored){}saveAndShow();}));p.addView(row);if(subject.optBoolean("pinned"))label(p,"Pinned",11,accent,true);
            int total=0,done=0;JSONArray chapters=store.array("chapters");for(int i=0;i<chapters.length();i++){JSONObject c=chapters.optJSONObject(i);if(c.optString("subject").equals(subject.optString("id"))){total++;if(c.optInt("remaining")==0)done++;}}gap(p,14);label(p,done+" of "+total+" chapters studied",13,muted,false);ProgressBar progress=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);progress.setMax(Math.max(1,total));progress.setProgress(done);progress.setProgressTintList(ColorStateList.valueOf(accent));progress.setProgressBackgroundTintList(ColorStateList.valueOf(line));p.addView(progress,new LinearLayout.LayoutParams(-1,dp(8)));button(p,"Open subject",false,()->{selectedSubject=subject.optString("id");show();});}
        recentDocuments();
    }
    private LocalDate minimalPlanDate=LocalDate.now();
    private void minimalPlan(){title("Your rhythm","One day at a time.","Choose a day to see a focused plan.");button(body,"Weekly availability",false,()->budget(false));gap(body,16);HorizontalScrollView days=new HorizontalScrollView(this);days.setHorizontalScrollBarEnabled(false);LinearLayout dates=new LinearLayout(this);days.addView(dates);body.addView(days);for(int i=0;i<14;i++){LocalDate date=LocalDate.now().plusDays(i);boolean chosen=date.equals(minimalPlanDate);TextView day=text(date.format(DateTimeFormatter.ofPattern("EEE\nd",Locale.getDefault())),13,chosen?onAccent:ink,true);day.setTextColor(chosen?onAccent:ink);day.setGravity(Gravity.CENTER);day.setBackground(shape(chosen?accent:card,12));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(dp(56),dp(66));lp.setMargins(0,0,dp(8),0);dates.addView(day,lp);day.setFocusable(true);day.setContentDescription(date.toString());day.setOnClickListener(v->{minimalPlanDate=date;show();});if(chosen)days.post(()->days.smoothScrollTo(day.getLeft(),0));}gap(body,22);label(body,minimalPlanDate.format(shortDate),20,ink,true);gap(body,12);Planner.Result p=store.plan();if(p.unscheduledMinutes>0)warning(p.unscheduledMinutes+" minutes cannot fit before your exams.");int count=0;for(Planner.Session session:p.sessions)if(session.date.equals(minimalPlanDate)){sessionCard(session);count++;}if(count==0)label(body,"No sessions planned for this day.",15,muted,false);button(body,"Browse all upcoming sessions",false,()->{LinearLayout f=form();int shown=0;for(Planner.Session session:p.sessions){label(f,session.date+" · "+session.topic.subject+" · "+session.topic.title+" · "+session.minutes+" min",14,ink,false);gap(f,12);if(++shown>=100)break;}if(shown==0)label(f,"Your plan will appear when you add chapters.",14,muted,false);sheet("Upcoming · first 100 sessions",f,"Close",null);});}
    private void minimalSettings(){title("Your workspace","Make it personal.","Quiet tools. Thoughtful details.");label(body,"APPEARANCE",11,muted,true);gap(body,12);button(body,"Theme & accent colors",false,()->go("Appearance"));gap(body,24);label(body,"STUDY PRACTICE",11,muted,true);button(body,"Daily goal",false,this::goalForm);button(body,"Weekly availability",false,()->budget(false));button(body,"Session history",false,()->go("History"));button(body,"Study insights",false,()->go("Insights"));button(body,"Study tools",false,()->go("Tools"));gap(body,24);label(body,"ON THIS DEVICE",11,muted,true);gap(body,10);label(body,"Your notes stay private. No account or cloud service. Keep original files: uninstalling removes saved data.",14,muted,false);followFooter();}

    private void centerLabel(LinearLayout parent,String value,int size,int color,boolean bold){TextView t=text(value,size,color,bold);t.setGravity(Gravity.CENTER);parent.addView(t,new LinearLayout.LayoutParams(-1,-2));}
    private void followFooter(){
        gap(body,28);LinearLayout footer=panel(body);footer.setGravity(Gravity.CENTER_HORIZONTAL);
        centerLabel(footer,"STAY CONNECTED",11,accent,true);gap(footer,8);centerLabel(footer,"Made for your next step.",18,ink,true);gap(footer,6);centerLabel(footer,"Follow N S H D",13,muted,false);gap(footer,20);
        LinearLayout social=new LinearLayout(this);social.setGravity(Gravity.CENTER);footer.addView(social,new LinearLayout.LayoutParams(-1,-2));
        String[] names={"Instagram","WhatsApp","Telegram"},symbols={"instagram","whatsapp","telegram"},links={"https://www.instagram.com/__nshd.__?stkn=emExd3hxZndzN21o","https://wa.me/918590455801","https://t.me/nshd_0"};
        for(int i=0;i<names.length;i++){final String link=links[i];LinearLayout item=column();item.setGravity(Gravity.CENTER);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,-2,1);social.addView(item,lp);item.addView(iconButton(symbols[i],names[i],()->openLink(link)));gap(item,8);centerLabel(item,names[i],12,muted,false);}
        gap(footer,24);View divider=new View(this);divider.setBackgroundColor(line);footer.addView(divider,new LinearLayout.LayoutParams(-1,dp(1)));gap(footer,18);centerLabel(footer,"STUDYFLOW / 0.7.1",10,muted,true);gap(footer,8);TextView credit=text("MADE  BY  N S H D",12,ink,true);credit.setLetterSpacing(.13f);credit.setGravity(Gravity.CENTER);footer.addView(credit,new LinearLayout.LayoutParams(-1,-2));gap(footer,4);
    }
    private void studyToolEntry(){button(body,"Study tools",false,()->go("Tools"));gap(body,12);}
    private void weeklySummary(){
        Map<LocalDate,Integer> days=studyDays();int seven=StudyTools.total(days,LocalDate.now().minusDays(6),LocalDate.now());LinearLayout p=panel(body);
        label(p,"Study activity",22,ink,true);gap(p,6);label(p,"Minutes you logged each day · last 7 days",13,muted,false);gap(p,12);
        if(seven==0){label(p,"Your week is a blank page.",18,ink,true);gap(p,6);label(p,"Finish a focus session or log study progress to draw your first bar. Opening notes alone does not count.",14,muted,false);}
        else {label(p,seven+" minutes across 7 days",16,accent,true);gap(p,12);WeekChart chart=new WeekChart(this,days,ink,accent,line,store.root.optBoolean("reduceMotion"));chart.onDay=this::studyDay; p.addView(chart,new LinearLayout.LayoutParams(-1,dp(176)));label(p,"Tap a bar for that day's sessions.",12,muted,false);}
        button(p,"Explore daily totals",false,()->{LinearLayout f=form();final AlertDialog[] d={null};for(int i=6;i>=0;i--){LocalDate date=LocalDate.now().minusDays(i);button(f,date.format(shortDate)+" · "+days.getOrDefault(date,0)+" min",false,()->{d[0].dismiss();studyDay(date);});}d[0]=sheet("Daily study totals",f,"Close",null);});
        LocalDate monday=StudyTools.weekStart(LocalDate.now());int total=StudyTools.total(days,monday,LocalDate.now()),goal=store.root.optInt("weeklyGoal",150);gap(p,14);label(p,"Weekly target · "+total+" / "+goal+" min",15,ink,true);label(p,"Monday–Sunday · "+monday+" to "+monday.plusDays(6),12,muted,false);progressBar(p,total,goal);button(p,"Edit weekly target",false,this::weeklyGoalForm);
    }
    private void progressBar(LinearLayout p,int value,int maximum){gap(p,10);ProgressBar bar=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);bar.setMax(Math.max(1,maximum));bar.setProgress(Math.min(value,maximum));bar.setProgressTintList(ColorStateList.valueOf(accent));bar.setProgressBackgroundTintList(ColorStateList.valueOf(line));bar.setContentDescription(value+" of "+maximum);p.addView(bar,new LinearLayout.LayoutParams(-1,dp(10)));}
    private void studyDay(LocalDate date){LinearLayout f=form();int count=0;JSONArray a=store.array("logs");for(int i=0;i<a.length();i++){JSONObject l=a.optJSONObject(i);if(!l.optString("date").equals(date.toString()))continue;JSONObject c=store.find("chapters",l.optString("chapter"));label(f,(c==null?"Removed chapter":c.optString("name"))+" · "+l.optInt("minutes")+" min",16,ink,true);gap(f,12);count++;}if(count==0)label(f,"No study minutes were logged on this date.",15,muted,false);sheet(date.format(shortDate),f,"Close",null);}
    private void weeklyGoalForm(){LinearLayout f=form();EditText input=field(f,"Weekly study minutes",String.valueOf(store.root.optInt("weeklyGoal",150)),true);label(f,"Counts actual study from Monday to Sunday. This target does not change your available time or daily plan.",14,muted,false);formDialog("Your weekly commitment",f,"Save target",()->{int goal=number(input,1,5040);change(()->store.setting("weeklyGoal",goal));show();});}
    private void studyTools(){title("Your study kit","Small tools. Big progress.","Capture, plan, practise and revisit.");minimalGrid(body,new String[]{"Assignments","Quick-note inbox","Weekly target","Practice quiz","Needs revision","Session history"},new String[]{"calendar","book","chart","cards","book","clock"},new Runnable[]{()->go("Assignments"),()->go("Inbox"),this::weeklyGoalForm,this::quizStart,()->go("Revision"),()->go("History")});weeklySummary();}
    private boolean showDoneAssignments;
    private int assignmentLimit=30,inboxLimit=30;
    private void assignments(){
        title("Assignments","Keep deadlines in sight.","Track submissions separately from exam study time.");button(body,"Add assignment",true,()->assignmentForm(null));button(body,showDoneAssignments?"Show open assignments":"Show completed assignments",false,()->{showDoneAssignments=!showDoneAssignments;assignmentLimit=30;show();});gap(body,12);
        List<JSONObject> items=new ArrayList<>();JSONArray a=store.array("assignments");for(int i=0;i<a.length();i++)if(a.optJSONObject(i).optBoolean("done")==showDoneAssignments)items.add(a.optJSONObject(i));items.sort(Comparator.comparing(x->x.optString("due")));
        if(items.isEmpty())label(body,showDoneAssignments?"Completed assignments will appear here.":"No open assignments. Add a deadline when one arrives.",16,muted,false);
        for(int i=0;i<Math.min(assignmentLimit,items.size());i++){JSONObject item=items.get(i);LinearLayout p=panel(body);label(p,item.optBoolean("done")?"COMPLETED":StudyTools.deadline(LocalDate.parse(item.optString("due")),LocalDate.now()),11,accent,true);gap(p,8);label(p,item.optString("title"),22,ink,true);label(p,item.optString("course")+" · "+item.optString("due"),13,muted,false);if(!item.optString("details").isEmpty()){gap(p,10);label(p,item.optString("details"),14,ink,false);}button(p,"Edit assignment",false,()->assignmentForm(item));button(p,item.optBoolean("done")?"Reopen":"Mark completed",true,()->{try{change(()->{JSONObject current=store.find("assignments",item.optString("id"));try{current.put("done",!current.optBoolean("done"));}catch(JSONException e){throw new IllegalArgumentException(e);}});show();}catch(IllegalArgumentException e){toast(e.getMessage());}});}
        if(items.size()>assignmentLimit)button(body,"Show 30 more",false,()->{assignmentLimit+=30;show();});
    }
    private void assignmentForm(JSONObject existing){
        LinearLayout f=form();EditText title=field(f,"Assignment title",existing==null?"":existing.optString("title"),false),course=field(f,"Course / subject (optional)",existing==null?"":existing.optString("course"),false),details=field(f,"Details (optional)",existing==null?"":existing.optString("details"),false);details.setSingleLine(false);details.setMinLines(2);final LocalDate[] due={existing==null?LocalDate.now().plusDays(7):LocalDate.parse(existing.optString("due"))};TextView date=button("Due · "+due[0],false,()->{});date.setOnClickListener(v->chooseDate(due[0],chosen->{due[0]=chosen;date.setText("Due · "+chosen);}));f.addView(date);
        final AlertDialog[] dialog={null};if(existing!=null)button(f,"Delete assignment",false,()->confirm("Delete assignment?","This removes this deadline from your tracker.","Delete","Keep",()->{change(()->store.remove("assignments","id",existing.optString("id")));dialog[0].dismiss();show();}));
        dialog[0]=formDialog(existing==null?"New assignment":"Edit assignment",f,"Save",()->{String t=required(title),c=course.getText().toString().trim(),d=details.getText().toString().trim();if(t.length()>200||c.length()>200||d.length()>5000)throw new IllegalArgumentException("Use a title/course under 200 characters and details under 5,000.");change(()->{JSONObject item=existing==null?Store.object("id",Store.id(),"done",false):store.find("assignments",existing.optString("id"));if(item==null)throw new IllegalArgumentException("This assignment no longer exists.");try{item.put("title",t);item.put("course",c);item.put("details",d);item.put("due",due[0].toString());}catch(JSONException e){throw new IllegalArgumentException(e);}if(existing==null)store.array("assignments").put(item);});show();});
    }
    private void inbox(){title("Quick-note inbox","Catch the thought.","Write now. Move it into a chapter when you are ready.");button(body,"Capture a note",true,()->inboxForm(null));JSONArray a=store.array("inbox");if(a.length()==0)label(body,"A question, a reminder, a useful idea—keep it here before it slips away.",16,muted,false);for(int i=a.length()-1;i>=Math.max(0,a.length()-inboxLimit);i--){JSONObject item=a.optJSONObject(i);LinearLayout p=panel(body);label(p,item.optString("title"),21,ink,true);TextView preview=text(item.optString("content"),14,muted,false);preview.setMaxLines(3);preview.setEllipsize(android.text.TextUtils.TruncateAt.END);p.addView(preview);button(p,"Read / edit",false,()->inboxForm(item));button(p,"Move into a chapter",false,()->moveInbox(item));}if(a.length()>inboxLimit)button(body,"Show 30 more",false,()->{inboxLimit+=30;show();});}
    private void inboxForm(JSONObject existing){LinearLayout f=form();EditText title=field(f,"Title",existing==null?"":existing.optString("title"),false),content=field(f,"Your thought",existing==null?"":existing.optString("content"),false);content.setSingleLine(false);content.setMinLines(5);content.setGravity(Gravity.TOP);final AlertDialog[] d={null};if(existing!=null)button(f,"Delete captured note",false,()->confirm("Delete captured note?","This note will be removed from your inbox.","Delete","Keep",()->{change(()->store.remove("inbox","id",existing.optString("id")));d[0].dismiss();show();}));d[0]=formDialog("Quick capture",f,"Save note",()->{String t=required(title),c=required(content);if(t.length()>200||c.length()>20000)throw new IllegalArgumentException("Use a title under 200 and a note under 20,000 characters.");change(()->{JSONObject item=existing==null?Store.object("id",Store.id()):store.find("inbox",existing.optString("id"));if(item==null)throw new IllegalArgumentException("This note no longer exists.");try{item.put("title",t);item.put("content",c);}catch(JSONException e){throw new IllegalArgumentException(e);}if(existing==null)store.array("inbox").put(item);});show();});}
    private void moveInbox(JSONObject note){LinearLayout f=form();final AlertDialog[] d={null};JSONArray chapters=store.array("chapters");if(chapters.length()==0)label(f,"Add a chapter in Library first. Your captured note will stay in the inbox.",15,muted,false);for(int i=0;i<chapters.length();i++){JSONObject c=chapters.optJSONObject(i),s=store.find("subjects",c.optString("subject"));button(f,(s==null?"":s.optString("name")+" · ")+c.optString("name"),false,()->{try{change(()->{JSONObject current=store.find("inbox",note.optString("id"));if(current==null||store.find("chapters",c.optString("id"))==null)throw new IllegalArgumentException("Note or chapter unavailable.");store.array("notes").put(Store.object("id",Store.id(),"chapter",c.optString("id"),"type","text","name",current.optString("title"),"content",current.optString("content")));store.remove("inbox","id",current.optString("id"));});d[0].dismiss();show();toast("Moved to chapter notes.");}catch(IllegalArgumentException e){toast(e.getMessage());}});}d[0]=sheet("Choose a chapter",f,"Close",null);}
    private void revisionQueue(){title("Needs revision","Turn uncertainty into clarity.","Chapters marked Need help or Okay, ordered by confidence then exam date. This queue does not add work to your plan.");List<JSONObject> chapters=new ArrayList<>();JSONArray a=store.array("chapters");for(int i=0;i<a.length();i++){JSONObject c=a.optJSONObject(i);if(c.optInt("confidence")<2&&store.find("subjects",c.optString("subject"))!=null)chapters.add(c);}chapters.sort((x,y)->{int confidence=Integer.compare(x.optInt("confidence"),y.optInt("confidence"));return confidence!=0?confidence:store.find("subjects",x.optString("subject")).optString("exam").compareTo(store.find("subjects",y.optString("subject")).optString("exam"));});if(chapters.isEmpty())label(body,"No chapters need attention based on your saved confidence ratings.",16,muted,false);for(JSONObject c:chapters){JSONObject subject=store.find("subjects",c.optString("subject"));LinearLayout p=panel(body);label(p,confidence(c.optInt("confidence"))+" · "+subject.optString("name"),12,accent,true);label(p,c.optString("name"),21,ink,true);label(p,"Exam · "+subject.optString("exam"),13,muted,false);button(p,"Open notes",true,()->notes(c));button(p,"Review chapter cards",false,()->cardList(c));button(p,"Update confidence / revision time",false,()->chapterForm(c));}}
    private void quizHistory(){JSONArray a=store.array("quizzes");if(a.length()==0)return;LinearLayout p=panel(body);label(p,"Recent practice quizzes",20,ink,true);for(int i=a.length()-1;i>=Math.max(0,a.length()-5);i--){JSONObject q=a.optJSONObject(i);gap(p,10);label(p,q.optString("date")+" · "+q.optInt("correct")+" / "+q.optInt("total")+" recalled",14,muted,false);}}
    private void quizStart(){JSONArray cards=store.array("cards");if(cards.length()==0){LinearLayout f=form();label(f,"Create a few flashcards in a chapter first. Practice quizzes use your own questions and answers.",15,muted,false);sheet("Your first practice quiz",f,"Close",null);return;}LinearLayout f=form();EditText limit=field(f,"Number of questions · up to 20",String.valueOf(Math.min(10,cards.length())),true);label(f,"Recall each answer before revealing it, then assess yourself honestly. Quiz scores are self-reported and do not change spaced-review dates or study minutes.",14,muted,false);formDialog("Practice quiz",f,"Begin",()->{int count=number(limit,1,Math.min(20,cards.length()));List<JSONObject> pool=new ArrayList<>();for(int i=0;i<cards.length();i++)pool.add(cards.optJSONObject(i));Collections.shuffle(pool);List<JSONObject> selected=new ArrayList<>(pool.subList(0,count));handler.post(()->new PracticeQuiz(selected).open());});}
    private final class PracticeQuiz {
        final List<JSONObject> cards;final String id=Store.id();int index,correct;AlertDialog dialog;LinearLayout content;
        PracticeQuiz(List<JSONObject> cards){this.cards=cards;}
        void open(){content=form();dialog=sheet("Practice quiz",content,"Close quiz",null);render();}
        void render(){content.removeAllViews();if(index==cards.size()){finish();return;}JSONObject c=cards.get(index);label(content,"QUESTION "+(index+1)+" OF "+cards.size(),12,accent,true);progressBar(content,index,cards.size());gap(content,18);label(content,c.optString("question"),23,ink,true);LinearLayout answer=panel(content);label(answer,c.optString("answer"),18,ink,false);answer.setVisibility(View.INVISIBLE);LinearLayout actions=column();actions.setVisibility(View.INVISIBLE);final boolean[] answered={false};button(actions,"I recalled it",true,()->grade(true,answered));button(actions,"Needs practice",false,()->grade(false,answered));TextView reveal=button("Reveal answer",true,()->{});content.addView(reveal);content.addView(actions);reveal.setOnClickListener(v->{reveal.setVisibility(View.INVISIBLE);answer.setVisibility(View.VISIBLE);actions.setVisibility(View.VISIBLE);entrance(answer);});}
        void grade(boolean known,boolean[] answered){if(answered[0])return;answered[0]=true;if(known)correct++;index++;render();}
        void finish(){label(content,"Practice complete",25,ink,true);gap(content,12);label(content,correct+" / "+cards.size()+" recalled",24,accent,true);label(content,"Self-assessed recall. Keep practising the answers that felt uncertain.",14,muted,false);try{change(()->{if(store.find("quizzes",id)==null)store.array("quizzes").put(Store.object("id",id,"date",LocalDate.now().toString(),"correct",correct,"total",cards.size()));});label(content,"Result saved on this device.",12,muted,false);}catch(IllegalArgumentException e){label(content,"Result could not be saved.",14,muted,false);button(content,"Retry saving result",true,this::render);}}
    }

    private void rememberLocation(String document,String mode){
        if(restoringNavigation)return;
        if(navigation.current!=null&&navigation.current.document==null&&workspaceScroll!=null)navigation.current.scroll=workspaceScroll.getScrollY();
        navigation.visit(new NavigationTrail.Place(tab,selectedSubject,document,mode));
    }
    private String saveTrail(){if(navigation.current!=null&&navigation.current.document==null&&workspaceScroll!=null)navigation.current.scroll=workspaceScroll.getScrollY();JSONArray a=new JSONArray();for(NavigationTrail.Place p:navigation.previous)a.put(routeJson(p));if(navigation.current!=null)a.put(routeJson(navigation.current));return a.toString();}
    private JSONObject routeJson(NavigationTrail.Place p){return Store.object("section",p.section,"subject",p.subject,"document",p.document,"mode",p.mode,"scroll",p.scroll);}
    private void restoreTrail(String value){if(value==null)return;try{JSONArray a=new JSONArray(value);for(int i=0;i<a.length();i++){JSONObject o=a.getJSONObject(i);NavigationTrail.Place p=new NavigationTrail.Place(o.optString("section","Today"),o.isNull("subject")?null:o.optString("subject"),o.isNull("document")?null:o.optString("document"),o.optString("mode"));p.scroll=Math.max(0,o.optInt("scroll"));navigation.visit(p);}}catch(JSONException ignored){navigation.previous.clear();navigation.current=null;}}
    private void navigateBack(){
        if(latestSheet!=null&&latestSheet.isShowing()){latestSheet.dismiss();return;}
        NavigationTrail.Place place=navigation.back();
        if(place==null){if(reading||!tab.equals("Today")||selectedSubject!=null){restoringNavigation=true;tab="Today";selectedSubject=null;navigation.current=new NavigationTrail.Place("Today",null,null,"");show();restoringNavigation=false;}else super.onBackPressed();return;}
        restoringNavigation=true;tab=place.section;selectedSubject=place.subject!=null&&store.find("subjects",place.subject)!=null?place.subject:null;show();
        if(place.document!=null){JSONObject n=store.find("notes",place.document);if(n!=null){if(place.mode.equals("preview"))readOffice(n);else if(place.mode.equals("office"))officeDocument(n);else readFile(n,place.mode.equals("text"));}else navigation.current=new NavigationTrail.Place(tab,selectedSubject,null,"");}
        restoringNavigation=false;
    }
    private void officeDocument(JSONObject note){
        rememberLocation(note.optString("id"),"office");closeOffice();reading=true;readerNoteId=note.optString("id");renderToken++;root.removeAllViews();
        ScrollView scroll=new ScrollView(this);LinearLayout page=column();page.setPadding(dp(20),dp(12),dp(20),dp(24));scroll.addView(page);root.addView(scroll,new LinearLayout.LayoutParams(-1,-1));
        page.addView(iconButton("back","Back to previous page",this::navigateBack));gap(page,22);label(page,"ORIGINAL DOCUMENT",11,accent,true);gap(page,10);label(page,note.optString("name"),26,ink,true);gap(page,10);
        label(page,"Open the original in a compatible Office app to keep its layout, diagrams and formatting. StudyFlow keeps the imported file unchanged.",15,muted,false);
        button(page,"Open original document",true,()->openExternal(note));
        JSONObject pdf=store.find("notes",note.optString("layoutNote"));if(pdf!=null){button(page,"Read saved layout PDF",true,()->readFile(pdf));label(page,"Linked PDF: "+pdf.optString("name"),12,muted,false);}
        LinearLayout options=panel(page);label(options,"Read the exact static layout here",20,ink,true);gap(options,8);label(options,"Export this document as PDF in your Office app, then attach that PDF here. This preserves the exported pages for offline reading, zoom and bookmarks. Slide animations are not part of a PDF.",14,muted,false);
        button(options,pdf==null?"Attach exported PDF":"Replace linked PDF",false,()->{pendingLayoutNote=note.optString("id");Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.setType("application/pdf");i.addCategory(Intent.CATEGORY_OPENABLE);try{startActivityForResult(i,IMPORT_LAYOUT);}catch(ActivityNotFoundException e){toast("No document picker available.");}});
        if(OfficePreview.supports(note.optString("name"))){LinearLayout preview=panel(page);label(preview,"Quick preview",18,ink,true);label(preview,"A simplified preview. Complex diagrams, positioning and fonts may be missing or changed.",14,muted,false);button(preview,"Open simplified preview",false,()->readOffice(note));button(preview,"Text view & search",false,()->readFile(note,true));}
    }
    private void importLayout(String owner,Uri uri){String name="Layout.pdf";try(android.database.Cursor c=getContentResolver().query(uri,new String[]{OpenableColumns.DISPLAY_NAME},null,null,null)){if(c!=null&&c.moveToFirst())name=c.getString(0);}catch(Exception ignored){}final String filename=Store.id(),title=name==null?"Layout.pdf":name;toast("Saving layout PDF…");worker.execute(()->{File file=new File(getFilesDir(),filename);try{
        try(InputStream in=getContentResolver().openInputStream(uri);OutputStream out=new FileOutputStream(file)){if(in==null)throw new IOException("Unavailable file");byte[] b=new byte[32768];long count=0;int n;while((n=in.read(b))!=-1){count+=n;if(count>100L*1024*1024)throw new IOException("PDF exceeds 100 MB");out.write(b,0,n);}}
        try(ParcelFileDescriptor fd=ParcelFileDescriptor.open(file,ParcelFileDescriptor.MODE_READ_ONLY);PdfRenderer renderer=new PdfRenderer(fd)){if(renderer.getPageCount()<1)throw new IOException("PDF has no pages");}
        runOnUiThread(()->{if(isDestroyed()){file.delete();return;}try{change(()->{JSONObject original=store.find("notes",owner);if(original==null)throw new IllegalArgumentException("Original document removed");String id=Store.id();store.array("notes").put(Store.object("id",id,"chapter",original.optString("chapter"),"name",title,"type","application/pdf","file",filename,"page",1,"layoutFor",owner));try{original.put("layoutNote",id);}catch(JSONException e){throw new IllegalArgumentException(e);}});toast("Layout PDF saved. The original stays unchanged.");if(owner.equals(readerNoteId))officeDocument(store.find("notes",owner));}catch(IllegalArgumentException e){file.delete();toast(e.getMessage());}});
    }catch(Exception e){file.delete();runOnUiThread(()->toast("Could not save PDF. Choose an unlocked PDF under 100 MB."));}});}
    private void learningStructure(){
        LinearLayout p=panel(body);label(p,"Your learning cycle",21,ink,true);gap(p,6);label(p,"Move between understanding, practice and revision.",13,muted,false);gap(p,18);
        int chapters=store.array("chapters").length(),confident=0;JSONArray cs=store.array("chapters");for(int i=0;i<cs.length();i++)if(cs.optJSONObject(i).optInt("confidence")==2)confident++;
        String[] names={"Learn","Recall","Review"},hints={chapters+" chapters",store.array("cards").length()+" cards",(chapters-confident)+" need attention"},destinations={"Library","Cards","Revision"};
        for(int i=0;i<3;i++){final String destination=destinations[i];LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);TextView number=text("0"+(i+1),13,onAccent,true);number.setTextColor(onAccent);number.setGravity(Gravity.CENTER);number.setBackground(shape(accent,12));row.addView(number,new LinearLayout.LayoutParams(dp(38),dp(38)));LinearLayout info=column();info.setPadding(dp(14),dp(6),dp(8),dp(6));label(info,names[i],17,ink,true);label(info,hints[i],12,muted,false);row.addView(info,new LinearLayout.LayoutParams(0,-2,1));row.addView(iconButton("arrow","Open "+names[i],()->go(destination)));p.addView(row);row.setFocusable(true);row.setContentDescription(names[i]+", "+hints[i]);row.setOnClickListener(v->go(destination));row.setStateListAnimator(pressAnimator());if(i<2){View connector=new View(this);connector.setBackgroundColor(line);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(dp(2),dp(12));lp.leftMargin=dp(18);p.addView(connector,lp);}}
        gap(p,14);label(p,confident+" of "+chapters+" chapters rated confident",12,muted,false);progressBar(p,confident,chapters);
    }

    private void colors() {
        String theme=store==null?"Minimal":store.root.optString("theme","Midnight");minimal=theme.startsWith("Minimal");lightTheme=theme.equals("Paper")||theme.equals("Minimal");
        bg=Color.parseColor(lightTheme?(minimal?"#FAF9FC":"#F4F6F2"):theme.equals("AMOLED")?"#000000":minimal?"#111116":"#0B1220");
        card=Color.parseColor(lightTheme?"#FFFFFF":minimal?"#1B1B23":"#152132");ink=Color.parseColor(lightTheme?"#20212A":"#F2F1F7");muted=Color.parseColor(lightTheme?"#636371":"#B3B2C3");line=Color.parseColor(lightTheme?"#E3E1EB":"#363540");
        int seed=Color.parseColor(minimal?"#A374EC":lightTheme?"#236C57":"#A6E8CD");
        if(store!=null){String mode=store.root.optString("accentMode","Theme");if(mode.equals("Custom"))try{seed=Color.parseColor(store.root.optString("customAccent","#A374EC"));}catch(Exception ignored){}
            if(mode.equals("Android")&&Build.VERSION.SDK_INT>=31)seed=getColor(lightTheme?android.R.color.system_accent1_600:android.R.color.system_accent1_200);}
        accent=ThemeColors.exactAccent(seed);accentText=ThemeColors.readable(seed,bg,card);onAccent=ThemeColors.on(accent);
    }
    @Override protected void onResume(){super.onResume();if(store!=null&&root!=null&&store.root.optString("accentMode").equals("Android")){int before=accent;colors();if(before!=accent){String id=readerNoteId;String mode=navigation.current==null?"":navigation.current.mode;restoringNavigation=true;show();JSONObject n=id==null?null:store.find("notes",id);if(n!=null){if(mode.equals("preview"))readOffice(n);else readFile(n,mode.equals("text"));}restoringNavigation=false;}}}
    private GradientDrawable shape(int color,int radius) {
        GradientDrawable g=new GradientDrawable(); g.setColor(color); g.setCornerRadius(dp(radius)); return g;
    }
    private LinearLayout column() { LinearLayout l=new LinearLayout(this); l.setOrientation(LinearLayout.VERTICAL); return l; }
    private TextView text(String value,int size,int color,boolean bold) {
        TextView t=new TextView(this); t.setText(value); t.setTextSize(size); t.setTextColor(color==accent?accentText:color);
        t.setFontFeatureSettings("kern");
        if(minimal)t.setLetterSpacing(size>=24?-.035f:.005f); t.setLineSpacing(dp(3),1);
        t.setTypeface(Typeface.create(bold?"sans-serif-medium":"sans-serif",bold?Typeface.BOLD:Typeface.NORMAL));
        return t;
    }
    private void gap(LinearLayout target,int h) { View v=new View(this); target.addView(v,new LinearLayout.LayoutParams(1,dp(h))); }
    private void label(LinearLayout target,String value,int size,int color,boolean bold) { target.addView(text(value,size,color,bold)); }
    private TextView button(String title,boolean primary,Runnable action) {
        TextView t=text(minimal?title.replace("  →","").replace("  +",""):title,14,primary?onAccent:ink,true);t.setTextColor(primary?onAccent:ink); t.setGravity(Gravity.CENTER); t.setMinHeight(dp(50)); t.setPadding(dp(14),dp(12),dp(14),dp(12));
        GradientDrawable surface=shape(primary?accent:card,minimal?12:16);if(minimal)surface.setStroke(dp(1),primary&&ThemeColors.contrast(accent,card)<1.5?line:primary?accent:line);
        t.setBackground(new RippleDrawable(ColorStateList.valueOf(0x33808080),surface,null));
        if(minimal&&!primary){FlowIcon icon=new FlowIcon(actionIcon(title),ink);icon.setBounds(0,0,dp(18),dp(18));t.setCompoundDrawables(icon,null,null,null);t.setCompoundDrawablePadding(dp(10));t.setGravity(Gravity.CENTER_VERTICAL|Gravity.START);}
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
        GradientDrawable g=minimal?shape(card,16):new GradientDrawable(GradientDrawable.Orientation.TL_BR,new int[]{card,bg});g.setCornerRadius(dp(minimal?16:24));g.setStroke(dp(1),line);p.setBackground(g);p.setElevation(dp(minimal?0:2));
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2); lp.topMargin=dp(12);lp.bottomMargin=dp(14); target.addView(p,lp); return p;
    }
    private void show() {
        rememberLocation(null,"");
        closeOffice();reading=false;readerNoteId=null; renderToken++; colors();
        if(displayedBitmap!=null){displayedBitmap.recycle();displayedBitmap=null;}
        getWindow().setStatusBarColor(bg); getWindow().setNavigationBarColor(bg);
        getWindow().getDecorView().setSystemUiVisibility(lightTheme?View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR|View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR:0);
        root=column(); root.setBackgroundColor(bg); root.setFitsSystemWindows(true); setContentView(root);
        root.setOnApplyWindowInsetsListener((v,insets)->{
            v.setPadding(insets.getSystemWindowInsetLeft(),insets.getSystemWindowInsetTop(),insets.getSystemWindowInsetRight(),insets.getSystemWindowInsetBottom());
            return insets.consumeSystemWindowInsets();
        });
        root.requestApplyInsets();
        ScrollView scroll=new ScrollView(this);workspaceScroll=scroll; scroll.setFillViewport(true); scroll.setClipToPadding(false);
        body=column(); body.setPadding(dp(22),dp(22),dp(22),dp(22)); scroll.addView(body);
        root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        LinearLayout top=new LinearLayout(this); top.setGravity(Gravity.CENTER_VERTICAL);
        View back=iconButton("back","Back to previous page",this::navigateBack);back.setEnabled(navigation.canBack());back.setAlpha(navigation.canBack()?1:.35f);top.addView(back);
        TextView brand=text("STUDYFLOW",12,accent,true); brand.setLetterSpacing(.2f); top.addView(brand,new LinearLayout.LayoutParams(0,-2,1));
        top.addView(iconButton("settings","Settings",()->{tab="Settings";show();})); body.addView(top); gap(body,22);
        if(tab.equals("Today")){if(minimal)minimalToday();else today();} else if(tab.equals("Library")){if(minimal)minimalLibrary();else library();} else if(tab.equals("Plan")){if(minimal)minimalPlan();else plan();}else if(tab.equals("Appearance"))appearance();else if(tab.equals("Insights"))insights();else if(tab.equals("Search"))workspaceSearch();else if(tab.equals("Cards"))flashcards();else if(tab.equals("History"))history();else if(tab.equals("Exams"))exams();else if(tab.equals("Tools"))studyTools();else if(tab.equals("Assignments"))assignments();else if(tab.equals("Inbox"))inbox();else if(tab.equals("Revision"))revisionQueue();else {if(minimal)minimalSettings();else settings();}
        nav=new LinearLayout(this); nav.setPadding(dp(14),dp(10),dp(14),dp(10)); nav.setBackgroundColor(bg);
        for(String item:(minimal?new String[]{"Today","Library","Plan","Insights"}:new String[]{"Today","Library","Plan"})) {
            TextView b=minimal?navItem(item):button(item,tab.equals(item),()->{tab=item;selectedSubject=null;show();});
            LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,-2,1); lp.setMargins(dp(3),0,dp(3),0); nav.addView(b,lp);
        }
        root.addView(nav);
        if(navigation.current!=null&&navigation.current.document==null){int position=navigation.current.scroll;scroll.post(()->scroll.scrollTo(0,position));}
        boolean changed=renderedTab==null||!renderedTab.equals(tab);renderedTab=tab;
        if(changed&&!(store!=null&&store.root.optBoolean("reduceMotion"))) { body.setAlpha(0); body.setTranslationY(dp(9)); body.animate().alpha(1).translationY(0).setDuration(220).setInterpolator(new DecelerateInterpolator()).start(); }
    }
    private void title(String eyebrow,String title,String subtitle) {
        label(body,eyebrow.toUpperCase(Locale.ROOT),11,accent,true); gap(body,8);
        label(body,minimal?title.replace("\n"," "):title,minimal?34:32,ink,true); gap(body,8); label(body,subtitle,14,muted,false); gap(body,24);
    }
    private void today() {
        title(LocalDate.now().format(shortDate),"Make room\nfor progress.","Your notes. Your pace. A clearer next step.");
        goalSummary();
        studyToolEntry();
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
    private final class BoundedSheet extends LinearLayout {
        BoundedSheet(){super(MainActivity.this);setOrientation(VERTICAL);}
        @Override protected void onMeasure(int width,int height){int max=(int)(getResources().getDisplayMetrics().heightPixels*.82f);int supplied=MeasureSpec.getSize(height);if(supplied>0)max=Math.min(max,supplied);super.onMeasure(width,MeasureSpec.makeMeasureSpec(max,MeasureSpec.AT_MOST));}
    }
    private AlertDialog sheet(String title,LinearLayout f,String positive,Runnable save) {return sheet(title,f,positive,save,"Cancel");}
    private AlertDialog sheet(String title,LinearLayout f,String positive,Runnable save,String cancelLabel) {
        LinearLayout shell=new BoundedSheet();GradientDrawable surface=shape(card,28);surface.setStroke(dp(1),line);shell.setBackground(surface);shell.setPadding(0,dp(22),0,dp(16));
        LinearLayout sheetHeader=new LinearLayout(this);sheetHeader.setPadding(dp(14),0,dp(14),dp(12));sheetHeader.setGravity(Gravity.CENTER_VERTICAL);
        TextView heading=text(title,24,ink,true);heading.setPadding(dp(10),0,0,0);shell.addView(sheetHeader);
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(false);scroll.addView(f);
        shell.addView(scroll,new LinearLayout.LayoutParams(-1,-2,1));
        TextView validation=text("",13,ThemeColors.readable(0xffc5374c,bg,card),false);validation.setPadding(dp(22),dp(8),dp(22),0);validation.setVisibility(View.GONE);shell.addView(validation);
        LinearLayout actions=new LinearLayout(this);actions.setPadding(dp(16),dp(12),dp(16),0);shell.addView(actions);
        AlertDialog d=new AlertDialog.Builder(this).create();d.setView(shell,0,0,0,0);sheetHeader.addView(iconButton("back","Back / close panel",d::dismiss));sheetHeader.addView(heading,new LinearLayout.LayoutParams(0,-2,1));
        if(save!=null) {TextView cancel=button(cancelLabel,false,d::dismiss);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,-2,1);lp.setMargins(0,0,dp(8),0);actions.addView(cancel,lp);}
        TextView confirm=button(positive,true,()->{try{validation.setVisibility(View.GONE);if(save!=null)save.run();d.dismiss();}catch(IllegalArgumentException ex){validation.setText(ex.getMessage());validation.setVisibility(View.VISIBLE);validation.announceForAccessibility(ex.getMessage());}});
        actions.addView(confirm,new LinearLayout.LayoutParams(0,-2,1));
        Window w=d.getWindow();if(w!=null){w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));w.setDimAmount(.42f);w.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE|WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);w.setWindowAnimations(store!=null&&store.root.optBoolean("reduceMotion")?0:R.style.SheetMotion);}
        latestSheet=d;d.show();if(w!=null)w.setLayout(Math.min(getResources().getDisplayMetrics().widthPixels-dp(28),dp(540)),WindowManager.LayoutParams.WRAP_CONTENT);return d;
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
        if(request==IMPORT_LAYOUT){String owner=pendingLayoutNote;pendingLayoutNote=null;if(result==RESULT_OK&&data!=null&&data.getData()!=null&&owner!=null)importLayout(owner,data.getData());return;}
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
    private void readFile(JSONObject note) {readFile(note,false);}
    private void readFile(JSONObject note,boolean textOnly) {
        closeOffice();
        if(store.find("notes",note.optString("id"))==null){toast("This note was deleted.");return;}
        String extension=DocumentText.extension(note.optString("name"));
        if(!textOnly&&Arrays.asList("doc","docx","ppt","pptx","xls","xlsx","odt","odp","ods").contains(extension)){officeDocument(note);return;}
        File file=new File(getFilesDir(),note.optString("file"));if(!file.exists()){toast("Attachment unavailable. Please import it again.");return;}
        try{note.put("lastOpened",System.currentTimeMillis());}catch(JSONException ignored){}save();
        rememberLocation(note.optString("id"),textOnly?"text":"reader");
        reading=true;readerNoteId=note.optString("id");renderToken++;root.removeAllViews();
        LinearLayout reader=column();reader.setPadding(dp(16),dp(8),dp(16),dp(8));root.addView(reader,new LinearLayout.LayoutParams(-1,-1));
        LinearLayout header=new LinearLayout(this);header.setGravity(Gravity.CENTER_VERTICAL);reader.addView(header);
        header.addView(iconButton("back","Back to previous page",this::navigateBack));
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
        button(rhythm,"Study tools",false,()->go("Tools"));button(rhythm,"Daily goal",false,this::goalForm);button(rhythm,"Session history",false,()->{tab="History";show();});
        LinearLayout privacy=panel(body);label(privacy,"Your space stays yours",20,ink,true);gap(privacy,8);label(privacy,"No account, ads, analytics or internet permission. Notes are copied into private app storage. Uninstalling removes your data; keep your original files.",14,muted,false);
        followFooter();
    }
    private void appearance() {
        title("Appearance","A space that feels like you.","Choose the surface. Set the color. Keep your focus.");
        LinearLayout preview=panel(body);label(preview,"LIVE PREVIEW",11,accent,true);gap(preview,12);label(preview,"Less noise. More clarity.",23,ink,true);gap(preview,8);label(preview,"Your subjects, notes and progress in one calm workspace.",14,muted,false);
        button(preview,"This is your accent",true,()->toast("Changes apply across StudyFlow."));
        label(body,"Theme",20,ink,true);gap(body,12);
        String[] themes={"Minimal","Minimal Dark","Paper","Midnight","AMOLED"};
        for(String name:themes){LinearLayout p=panel(body);boolean selected=store.root.optString("theme","Midnight").equals(name);label(p,name+(selected?"  ✓":""),18,ink,true);gap(p,5);label(p,name.equals("Minimal")?"Editorial dashboard · icon grids · fine-line charts":name.equals("Minimal Dark")?"Charcoal · editorial layouts · quiet motion":name.equals("AMOLED")?"True black background":name.equals("Paper")?"Warm white workspace":"Deep blue surfaces",13,muted,false);button(p,selected?"Selected":"Use "+name,selected,()->{store.setting("theme",name);saveAndShow();});}
        LinearLayout palette=panel(body);label(palette,"Accent color",20,ink,true);gap(palette,8);label(palette,"Buttons, charts and highlights use your exact HEX. Small text uses a separate readable shade.",13,muted,false);
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
        title("Study insights","Small steps add up.","Only minutes you actually logged appear here.");
        button(body,"Browse session history",false,()->{tab="History";show();});
        LocalDate today=LocalDate.now();long total=0;int sessions=0;Map<LocalDate,Long> days=new HashMap<>();Map<String,Long> subjects=new LinkedHashMap<>();
        JSONArray logs=store.array("logs");for(int i=0;i<logs.length();i++){JSONObject log=logs.optJSONObject(i);try{LocalDate date=LocalDate.parse(log.optString("date"));long mins=Math.max(0,log.optInt("minutes"));if(date.isAfter(today))continue;days.put(date,days.getOrDefault(date,0L)+mins);total+=mins;sessions++;JSONObject chapter=store.find("chapters",log.optString("chapter"));JSONObject subject=chapter==null?null:store.find("subjects",chapter.optString("subject"));String id=subject==null?"Removed subjects":subject.optString("name");subjects.put(id,subjects.getOrDefault(id,0L)+mins);}catch(Exception ignored){}}
        LinearLayout hero=panel(body);label(hero,total+" minutes",32,accent,true);gap(hero,4);label(hero,sessions+" logged sessions · all time",14,muted,false);
        quizHistory();
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
    private AlertDialog latestSheet;
    private AlertDialog reviewDialog;
    private LinearLayout reviewContent;
    private void reviewNext(int reviewed) {
        if(reviewDialog==null||!reviewDialog.isShowing()){
            reviewContent=form();reviewDialog=sheet("Active recall",reviewContent,"Finish for now",null);
            reviewDialog.setOnDismissListener(d->{reviewDialog=null;reviewContent=null;if(!isFinishing()&&!isDestroyed()){show();}});
        }
        JSONObject next=null;JSONArray a=store.array("cards");for(int i=0;i<a.length();i++){JSONObject c=a.optJSONObject(i);if(cardDue(c)&&(next==null||c.optString("due").compareTo(next.optString("due"))<0))next=c;}
        LinearLayout f=reviewContent;f.removeAllViews();
        if(next==null){label(f,"All caught up",26,ink,true);gap(f,12);label(f,reviewed+" cards reviewed. Come back when the next cards are due.",16,muted,false);return;}
        final String id=next.optString("id");label(f,reviewed+" reviewed this session",12,accent,true);gap(f,16);label(f,next.optString("question"),23,ink,true);gap(f,16);
        LinearLayout answer=panel(f);answer.setVisibility(View.INVISIBLE);label(answer,next.optString("answer"),18,ink,false);answer.setMinimumHeight(dp(88));
        LinearLayout grades=column();grades.setVisibility(View.INVISIBLE);label(grades,"When should this return?",14,muted,false);final boolean[] handled={false};
        for(int rating=0;rating<3;rating++){int days=StudyTools.interval(next.optInt("interval"),rating);String name=new String[]{"Again","Good","Easy"}[rating];button(grades,name+" · "+days+(days==1?" day":" days"),rating==1,()->{if(handled[0])return;try{change(()->{try{JSONObject current=store.find("cards",id);if(current==null)throw new IllegalArgumentException("This card was removed.");current.put("interval",days);current.put("due",LocalDate.now().plusDays(days).toString());current.put("reviews",current.optInt("reviews")+1);}catch(JSONException e){throw new IllegalArgumentException(e);}});handled[0]=true;reviewNext(reviewed+1);}catch(IllegalArgumentException e){toast(e.getMessage());}});}
        TextView reveal=button("Reveal answer",true,()->{});f.addView(reveal);f.addView(grades);reveal.setOnClickListener(v->{reveal.setVisibility(View.INVISIBLE);answer.setVisibility(View.VISIBLE);grades.setVisibility(View.VISIBLE);entrance(answer);});
    }

    private void exportNote(JSONObject note) {
        pendingExportNote=note.optString("id");Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT);i.setType("text/plain");i.addCategory(Intent.CATEGORY_OPENABLE);i.putExtra(Intent.EXTRA_TITLE,note.optString("name","StudyFlow note").replaceAll("[/\\\\:*?\"<>|]","_")+".txt");
        try{startActivityForResult(i,EXPORT_NOTE);}catch(ActivityNotFoundException e){toast("No file-saving app is available on this phone.");}
    }
    private View iconButton(String symbol,String description,Runnable action) {
        ImageButton b=new ImageButton(this);b.setImageDrawable(new FlowIcon(symbol,minimal?ink:accentText));b.setContentDescription(description);b.setTooltipText(description);
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
    @Override public void onBackPressed(){navigateBack();}
    @Override protected void onDestroy(){closeOffice();renderToken++;handler.removeCallbacksAndMessages(null);worker.shutdown();super.onDestroy();}
    private final class Ring extends View {
        private final Paint p=new Paint(3);private final float fraction;private final int percent;private float shown;private android.animation.ValueAnimator animation;
        Ring(int done,int goal){super(MainActivity.this);fraction=Math.min(1,done/(float)goal);percent=Math.round(fraction*100);setContentDescription(percent+" percent of daily study target");}
        @Override protected void onAttachedToWindow(){super.onAttachedToWindow();if((store!=null&&store.root.optBoolean("reduceMotion"))){shown=fraction;return;}animation=android.animation.ValueAnimator.ofFloat(0,fraction);animation.setDuration(650);animation.setInterpolator(new DecelerateInterpolator());animation.addUpdateListener(a->{shown=(float)a.getAnimatedValue();invalidate();});animation.start();}
        @Override protected void onDetachedFromWindow(){if(animation!=null)animation.cancel();super.onDetachedFromWindow();}
        @Override protected void onDraw(Canvas c){super.onDraw(c);float w=getWidth(),h=getHeight();p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(dp(6));p.setStrokeCap(Paint.Cap.ROUND);p.setColor(line);RectF r=new RectF(dp(6),dp(6),w-dp(6),h-dp(6));c.drawArc(r,0,360,false,p);p.setColor(accent);c.drawArc(r,-90,360*shown,false,p);p.setStyle(Paint.Style.FILL);p.setColor(accentText);p.setTextSize(dp(18));p.setTypeface(Typeface.DEFAULT_BOLD);p.setTextAlign(Paint.Align.CENTER);c.drawText(percent+"%",w/2,h/2-(p.ascent()+p.descent())/2,p);}
    }
}
