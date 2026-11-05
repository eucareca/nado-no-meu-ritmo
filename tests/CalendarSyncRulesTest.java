/* SPDX-License-Identifier: Apache-2.0 */
package br.com.tabinformatica.nadonomeritmo;

import java.util.Arrays;

public final class CalendarSyncRulesTest {
    private static void check(boolean condition){if(!condition)throw new AssertionError();}
    private static void rejects(Runnable action){try{action.run();}catch(IllegalArgumentException expected){return;}throw new AssertionError("Accepted unsupported recurrence/duration");}
    public static void main(String[] args){
        check(Arrays.equals(CalendarSyncRules.weeklyDays("FREQ=WEEKLY;BYDAY=MO,WE,FR;WKST=MO"),new int[]{1,3,5}));
        check(Arrays.equals(CalendarSyncRules.weeklyDays("FREQ=WEEKLY;INTERVAL=1;BYDAY=SU"),new int[]{0}));
        rejects(()->CalendarSyncRules.weeklyDays("FREQ=WEEKLY;BYDAY=MO;UNTIL=20261201T000000Z"));
        rejects(()->CalendarSyncRules.weeklyDays("FREQ=DAILY;BYDAY=MO"));
        rejects(()->CalendarSyncRules.weeklyDays("FREQ=WEEKLY;BYDAY=1MO"));
        rejects(()->CalendarSyncRules.weeklyDays("FREQ=WEEKLY;BYDAY=MO,MO"));
        rejects(()->CalendarSyncRules.weeklyDays("FREQ=WEEKLY;INTERVAL=2;BYDAY=MO"));
        check(CalendarSyncRules.durationMinutes("PT45M",0,0)==45);
        check(CalendarSyncRules.durationMinutes("P2700S",0,0)==45);
        check(CalendarSyncRules.durationMinutes(null,1000,2701000)==45);
        rejects(()->CalendarSyncRules.durationMinutes("PT45S",0,0));
        rejects(()->CalendarSyncRules.durationMinutes("P",0,0));
        rejects(()->CalendarSyncRules.durationMinutes(null,1000,500));
        check(CalendarSyncRules.decide(false,false,false,false,"sync")==CalendarSyncRules.Decision.NONE);
        check(CalendarSyncRules.decide(true,false,false,false,"sync")==CalendarSyncRules.Decision.PUSH);
        check(CalendarSyncRules.decide(false,true,false,false,"sync")==CalendarSyncRules.Decision.PULL);
        check(CalendarSyncRules.decide(true,true,false,false,"sync")==CalendarSyncRules.Decision.CONFLICT);
        check(CalendarSyncRules.decide(false,false,true,false,"sync")==CalendarSyncRules.Decision.PULL);
        check(CalendarSyncRules.decide(true,false,true,false,"sync")==CalendarSyncRules.Decision.CONFLICT);
        check(CalendarSyncRules.decide(true,false,true,true,"sync")==CalendarSyncRules.Decision.PULL);
        check(CalendarSyncRules.decide(true,true,true,true,"sync")==CalendarSyncRules.Decision.PULL);
        check(CalendarSyncRules.decide(true,true,true,false,"push")==CalendarSyncRules.Decision.PUSH);
        check(CalendarSyncRules.decide(true,true,true,false,"pull")==CalendarSyncRules.Decision.PULL);
        System.out.println("PASS: native weekly recurrence/duration parsing, unsupported patterns, push/pull/conflict handling and pending local acknowledgement.");
    }
}
