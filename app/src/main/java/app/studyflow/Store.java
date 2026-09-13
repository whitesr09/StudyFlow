package app.studyflow;

import android.content.Context;
import android.util.AtomicFile;
import org.json.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.*;

final class Store {
    JSONObject root;
    private final AtomicFile file;
    Store(Context context) throws Exception {
        file=new AtomicFile(new File(context.getFilesDir(),"studyflow.json"));
        if(file.getBaseFile().exists()) root=new JSONObject(new String(file.readFully(),StandardCharsets.UTF_8));
        else root=new JSONObject();
        for(String key:new String[]{"subjects","chapters","notes","logs","cards"})
            if(!root.has(key)) root.put(key,new JSONArray());
    }
    JSONArray array(String key) { return root.optJSONArray(key); }
    static JSONObject object(Object... pairs) {
        JSONObject o=new JSONObject();
        try { for(int i=0;i<pairs.length;i+=2) o.put((String)pairs[i],pairs[i+1]); }
        catch(JSONException e) { throw new IllegalArgumentException(e); }
        return o;
    }
    void setting(String key,Object value) { try { root.put(key,value); } catch(JSONException e) { throw new IllegalArgumentException(e); } }
    void save() throws IOException {
        FileOutputStream out=null;
        try { out=file.startWrite(); out.write(root.toString().getBytes(StandardCharsets.UTF_8)); file.finishWrite(out); }
        catch(IOException e) { if(out!=null) file.failWrite(out); throw e; }
    }
    JSONObject find(String type,String id) {
        JSONArray a=array(type);
        for(int i=0;i<a.length();i++) if(a.optJSONObject(i).optString("id").equals(id)) return a.optJSONObject(i);
        return null;
    }
    void remove(String type,String key,String value) {
        JSONArray a=array(type);
        for(int i=a.length()-1;i>=0;i--) if(a.optJSONObject(i).optString(key).equals(value)) a.remove(i);
    }
    int usedToday() {
        int n=0; JSONArray a=array("logs");
        for(int i=0;i<a.length();i++) { JSONObject o=a.optJSONObject(i); if(o.optString("date").equals(LocalDate.now().toString())) n+=o.optInt("minutes"); }
        return n;
    }
    List<Planner.Topic> topics() {
        List<Planner.Topic> topics=new ArrayList<>();
        JSONArray a=array("chapters");
        for(int i=0;i<a.length();i++) {
            JSONObject c=a.optJSONObject(i), s=find("subjects",c.optString("subject"));
            if(s==null) continue;
            topics.add(new Planner.Topic(c.optString("id"),c.optString("name"),s.optString("name"),
                    LocalDate.parse(s.optString("exam")),c.optInt("remaining"),c.optInt("confidence")));
        }
        return topics;
    }
    Planner.Result plan() {
        Set<Integer> off=new HashSet<>();
        for(int d=1;d<=7;d++) if(root.optBoolean("off"+d)) off.add(d);
        int budget=root.optString("overrideDate").equals(LocalDate.now().toString())
                ?root.optInt("todayBudget",90):root.optInt("daily",90);
        return Planner.make(topics(),LocalDate.now(),root.optInt("daily",90),usedToday(),off,budget);
    }
    static String id() { return UUID.randomUUID().toString(); }
}
