/* SPDX-License-Identifier: Apache-2.0 */
package br.com.tabinformatica.nadonomeritmo;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Calendar recurrence/conflict rules, independent of Android or account data. */
final class CalendarSyncRules {
    enum Decision { CONFLICT, PULL, PUSH, NONE }
    static Decision decide(boolean localChanged,boolean remoteChanged,boolean pending,boolean matchesPending,String action){
        if("push".equals(action))return Decision.PUSH;
        if("pull".equals(action))return Decision.PULL;
        if(pending)return localChanged && !matchesPending?Decision.CONFLICT:Decision.PULL;
        if(localChanged && remoteChanged)return Decision.CONFLICT;
        if(remoteChanged)return Decision.PULL;
        return localChanged?Decision.PUSH:Decision.NONE;
    }
    static int[] weeklyDays(String rule){
        if(rule==null)throw new IllegalArgumentException("Use repetição semanal para sincronizar esta série.");
        String[] codes={"SU","MO","TU","WE","TH","FR","SA"};List<Integer> days=new ArrayList<>();boolean weekly=false;
        for(String part:rule.split(";")){
            if(part.equals("FREQ=WEEKLY")){weekly=true;continue;}
            if(part.equals("INTERVAL=1") || part.equals("WKST=MO") || part.equals("WKST=SU"))continue;
            if(part.startsWith("BYDAY=")){
                for(String code:part.substring(6).split(",")){int day=-1;for(int i=0;i<codes.length;i++)if(codes[i].equals(code))day=i;
                    if(day<0 || days.contains(day))throw new IllegalArgumentException("Dia de recorrência inválido ou repetido.");days.add(day);}
                continue;
            }
            throw new IllegalArgumentException("Somente repetição semanal simples é suportada. Nenhum dado foi sobrescrito.");
        }
        if(!weekly || days.isEmpty())throw new IllegalArgumentException("Use uma série semanal com dias definidos.");
        int[] result=new int[days.size()];for(int i=0;i<result.length;i++)result[i]=days.get(i);return result;
    }
    static long durationMinutes(String value,long start,long end){
        if(value==null || value.isEmpty()){
            long difference=end-start;if(difference<=0 || difference%60000!=0)throw new IllegalArgumentException("Duração inválida.");return difference/60000;
        }
        Matcher m=Pattern.compile("^P(?:(\\d+)D)?T?(?:(\\d+)H)?(?:(\\d+)M)?(?:(\\d+)S)?$").matcher(value);
        if(!m.matches())throw new IllegalArgumentException("Duração não suportada.");
        long[] units={86400,3600,60,1};long seconds=0;
        for(int i=1;i<=4;i++)if(m.group(i)!=null){long n=Long.parseLong(m.group(i));if(n>86400)throw new IllegalArgumentException("Duração inválida.");seconds+=n*units[i-1];}
        if(seconds<=0 || seconds%60!=0)throw new IllegalArgumentException("Use duração em minutos inteiros.");return seconds/60;
    }
}
