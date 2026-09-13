package app.studyflow;

import java.time.LocalDate;
import java.util.*;

/** Deterministic, offline planner. Exam days are excluded; no invented completion. */
public final class Planner {
    public static final class Topic {
        public final String id, title, subject;
        public final LocalDate exam;
        public final int minutes, confidence;
        public Topic(String id, String title, String subject, LocalDate exam, int minutes, int confidence) {
            this.id=id; this.title=title; this.subject=subject; this.exam=exam;
            this.minutes=Math.max(0, minutes); this.confidence=confidence;
        }
    }
    public static final class Session {
        public final Topic topic;
        public final LocalDate date;
        public final int minutes;
        Session(Topic topic, LocalDate date, int minutes) { this.topic=topic; this.date=date; this.minutes=minutes; }
    }
    public static final class Result {
        public final List<Session> sessions = new ArrayList<>();
        public int unscheduledMinutes;
    }
    public static Result make(List<Topic> topics, LocalDate today, int dailyMinutes,
                              int usedToday, Set<Integer> unavailableDays) {
        return make(topics, today, dailyMinutes, usedToday, unavailableDays, dailyMinutes);
    }
    public static Result make(List<Topic> topics, LocalDate today, int dailyMinutes,
                              int usedToday, Set<Integer> unavailableDays, int todayMinutes) {
        Result result = new Result();
        List<Topic> ordered = new ArrayList<>(topics);
        ordered.sort(Comparator.comparing((Topic t)->t.exam).thenComparingInt(t->t.confidence).thenComparing(t->t.id));
        Map<LocalDate,Integer> used = new HashMap<>();
        used.put(today, Math.max(0, usedToday));
        int capacity = Math.max(0, Math.min(720, dailyMinutes));
        for (Topic topic : ordered) {
            int left = topic.minutes;
            // Bound work even for accidentally distant exam dates.
            LocalDate end = topic.exam.isBefore(today.plusDays(730)) ? topic.exam : today.plusDays(730);
            for (LocalDate day=today; day.isBefore(end) && left>0; day=day.plusDays(1)) {
                if (unavailableDays.contains(day.getDayOfWeek().getValue())) continue;
                int limit = day.equals(today) ? Math.max(0, Math.min(720, todayMinutes)) : capacity;
                int room = Math.max(0, limit-used.getOrDefault(day, 0));
                while (room>0 && left>0) {
                    int chunk = Math.min(45, Math.min(room,left));
                    result.sessions.add(new Session(topic,day,chunk));
                    left-=chunk; room-=chunk;
                    used.put(day, used.getOrDefault(day,0)+chunk);
                }
            }
            result.unscheduledMinutes+=left;
        }
        result.sessions.sort(Comparator.comparing(s->s.date));
        return result;
    }
}
