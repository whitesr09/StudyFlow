package app.studyflow;

import java.time.LocalDate;
import java.util.Map;

/** Deterministic offline review intervals and activity streaks. */
final class StudyTools {
    static LocalDate weekStart(LocalDate date){return date.minusDays(date.getDayOfWeek().getValue()-1);}
    static int total(Map<LocalDate,Integer> days,LocalDate from,LocalDate to){long sum=0;for(Map.Entry<LocalDate,Integer> e:days.entrySet())if(!e.getKey().isBefore(from)&&!e.getKey().isAfter(to))sum+=Math.max(0,e.getValue());return (int)Math.min(Integer.MAX_VALUE,sum);}
    static String deadline(LocalDate due,LocalDate today){long days=java.time.temporal.ChronoUnit.DAYS.between(today,due);return days<0?(-days)+(days==-1?" DAY OVERDUE":" DAYS OVERDUE"):days==0?"DUE TODAY":days==1?"DUE TOMORROW":"DUE IN "+days+" DAYS";}
    static int interval(int previous,int rating) {
        if(rating<0||rating>2)throw new IllegalArgumentException("Unknown review rating");
        if(rating==0)return 1;
        long base=Math.max(1,previous);
        return (int)Math.min(365,rating==1?base*2:Math.max(4,base*3));
    }
    static int streak(Map<LocalDate,Integer> days,LocalDate today) {
        LocalDate day=days.getOrDefault(today,0)>0?today:today.minusDays(1);
        int count=0;
        while(days.getOrDefault(day,0)>0){count++;day=day.minusDays(1);}
        return count;
    }
}
