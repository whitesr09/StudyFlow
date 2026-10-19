package app.studyflow;
import java.util.*;
/** Bounded navigation history; refreshes never add duplicate destinations. */
final class NavigationTrail {
    static final class Place {
        final String section,subject,document,mode;int scroll;
        Place(String section,String subject,String document,String mode){this.section=section;this.subject=subject;this.document=document;this.mode=mode;}
        boolean same(Place p){return p!=null&&Objects.equals(section,p.section)&&Objects.equals(subject,p.subject)&&Objects.equals(document,p.document)&&Objects.equals(mode,p.mode);}
    }
    final Deque<Place> previous=new ArrayDeque<>();Place current;
    void visit(Place place){if(place.same(current))return;if(current!=null){previous.addLast(current);if(previous.size()>64)previous.removeFirst();}current=place;}
    Place back(){if(previous.isEmpty())return null;current=previous.removeLast();return current;}
    boolean canBack(){return !previous.isEmpty();}
}
