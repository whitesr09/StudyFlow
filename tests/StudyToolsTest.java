package app.studyflow;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
public class StudyToolsTest {
    static void check(boolean ok){if(!ok)throw new AssertionError();}
    public static void main(String[] args){
        check(StudyTools.interval(0,0)==1);check(StudyTools.interval(0,1)==2);check(StudyTools.interval(0,2)==4);
        check(StudyTools.interval(100,1)==200);check(StudyTools.interval(Integer.MAX_VALUE,2)==365);check(StudyTools.interval(200,0)==1);
        Map<LocalDate,Integer> days=new HashMap<>();LocalDate today=LocalDate.of(2026,1,1);
        check(StudyTools.streak(days,today)==0);days.put(today.minusDays(1),20);days.put(today.minusDays(2),5);
        check(StudyTools.streak(days,today)==2);days.put(today,1);check(StudyTools.streak(days,today)==3);
        days.put(today.minusDays(1),0);check(StudyTools.streak(days,today)==1);days.remove(today);check(StudyTools.streak(days,today)==0);
        days.put(today.plusDays(1),50);check(StudyTools.streak(days,today)==0);
        check(StudyTools.weekStart(LocalDate.of(2026,1,4)).equals(LocalDate.of(2025,12,29)));
        check(StudyTools.weekStart(LocalDate.of(2026,1,5)).equals(LocalDate.of(2026,1,5)));
        days.clear();days.put(today.minusDays(1),30);days.put(today,20);days.put(today.plusDays(1),99);
        check(StudyTools.total(days,today.minusDays(1),today)==50);
        check(StudyTools.deadline(today,today).equals("DUE TODAY"));check(StudyTools.deadline(today.minusDays(1),today).equals("1 DAY OVERDUE"));check(StudyTools.deadline(today.plusDays(1),today).equals("DUE TOMORROW"));
        System.out.println("Review intervals and streak boundaries passed.");
    }
}
