package com.carcare.reminder;

import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import static com.carcare.reminder.ReminderDtos.*;

public final class ReminderRules {
    private ReminderRules() {}
    public static Reminder evaluate(String source,long id,String type,String title,LocalDate dueDate,Integer dueKm,boolean done,LocalDate today,int currentKm) {
        Long days=dueDate==null?null:ChronoUnit.DAYS.between(today,dueDate);
        Long km=dueKm==null?null:(long)dueKm-currentKm;
        Status status=done?Status.DONE:negative(days)||negative(km)?Status.OVERDUE:zero(days)||zero(km)?Status.DUE:
            (days!=null && days<=30)||(km!=null && km<=500)?Status.UPCOMING:Status.SCHEDULED;
        var stages=new ArrayList<String>();
        if(!done) {
            if(days!=null) { if(days<0) stages.add("DATE_OVERDUE"); else if(days==0) stages.add("DATE_DUE"); else if(days<=1) stages.add("D1"); else if(days<=7) stages.add("D7"); else if(days<=30) stages.add("D30"); }
            if(km!=null) { if(km<0) stages.add("KM_OVERDUE"); else if(km==0) stages.add("KM_DUE"); else if(km<=100) stages.add("K100"); else if(km<=500) stages.add("K500"); }
        }
        String key=source+":"+id+":"+dueDate+":"+dueKm;
        String stage=stages.isEmpty()?null:String.join("|",stages);
        return new Reminder(key,source,id,type,title,dueDate,dueKm,days,km,status,stage,stage==null?null:key+":"+stage);
    }
    private static boolean negative(Long value) { return value!=null && value<0; }
    private static boolean zero(Long value) { return value!=null && value==0; }
}
