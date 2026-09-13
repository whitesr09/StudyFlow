package app.studyflow;
import java.time.LocalDate;
import java.util.*;
public class PlannerTest {
    static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
    public static void main(String[] args) {
        LocalDate day=LocalDate.of(2026,9,14);
        List<Planner.Topic> topics=Arrays.asList(
            new Planner.Topic("a","Later","A",day.plusDays(3),100,2),
            new Planner.Topic("b","Urgent","B",day.plusDays(1),80,0));
        Planner.Result r=Planner.make(topics,day,60,20,Collections.emptySet());
        check(r.sessions.get(0).topic.id.equals("b"),"Earliest exam first");
        check(r.unscheduledMinutes==40,"Report infeasible workload");
        Map<LocalDate,Integer> totals=new HashMap<>();
        for(Planner.Session s:r.sessions) {
            check(s.date.isBefore(s.topic.exam),"No exam-day scheduling");
            check(s.minutes>0 && s.minutes<=45,"Bounded sessions");
            totals.merge(s.date,s.minutes,Integer::sum);
        }
        check(totals.get(day)<=40,"Respect already studied minutes");
        for(int n:totals.values()) check(n<=60,"Never overbook");
        r=Planner.make(topics,day,60,0,Set.of(1,2,3,4,5,6,7));
        check(r.sessions.isEmpty() && r.unscheduledMinutes==180,"Blocked days remain free");
        r=Planner.make(topics,day,0,0,Collections.emptySet());
        check(r.unscheduledMinutes==180,"Zero capacity is safe");
        r=Planner.make(Arrays.asList(new Planner.Topic("x","Past","A",day,90,0)),day,60,0,Set.of());
        check(r.sessions.isEmpty() && r.unscheduledMinutes==90,"Past exams are flagged");
        r=Planner.make(Arrays.asList(new Planner.Topic("x","Done","A",day.plusDays(1),0,0)),day,60,0,Set.of());
        check(r.sessions.isEmpty(),"Completed chapters do not reappear");
        r=Planner.make(Arrays.asList(new Planner.Topic("x","Work","A",day.plusDays(2),90,0)),day,60,0,Set.of(),30);
        check(r.unscheduledMinutes==0,"Today override must not reduce tomorrow capacity");
        check(r.sessions.stream().filter(s->s.date.equals(day)).mapToInt(s->s.minutes).sum()==30,"Respect today override");
        System.out.println("All scheduling invariants passed.");
    }
}
