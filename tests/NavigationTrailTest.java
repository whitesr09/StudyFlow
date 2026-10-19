package app.studyflow;
public class NavigationTrailTest {
 static NavigationTrail.Place p(String s){return new NavigationTrail.Place(s,null,null,"");}
 static void check(boolean b){if(!b)throw new AssertionError();}
 public static void main(String[] args){NavigationTrail n=new NavigationTrail();n.visit(p("Today"));n.current.scroll=420;n.visit(p("Library"));n.visit(p("Library"));check(n.previous.size()==1);n.visit(new NavigationTrail.Place("Library","subject","document","office"));n.visit(new NavigationTrail.Place("Library","subject","document","preview"));check(n.back().mode.equals("office"));check(n.back().section.equals("Library"));check(n.back().scroll==420);check(n.back()==null);for(int i=0;i<100;i++)n.visit(p("page"+i));check(n.previous.size()==64);System.out.println("Navigation history passed");}
}
