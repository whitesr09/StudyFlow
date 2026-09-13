package app.studyflow;

import java.time.LocalDate;
import java.util.Map;

/** Deterministic offline review intervals and activity streaks. */
final class StudyTools {
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
