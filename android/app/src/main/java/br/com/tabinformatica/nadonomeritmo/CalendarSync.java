/* SPDX-License-Identifier: Apache-2.0 */
package br.com.tabinformatica.nadonomeritmo;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ContentProviderOperation;
import android.content.ContentProviderResult;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.provider.CalendarContract;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;
import java.util.TimeZone;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Explicitly authorized access to Google calendars registered in the Android Calendar Provider. */
final class CalendarSync {
    interface Reply { void send(JSONObject result); }
    private static final String PREFS="google.calendar.link.v1";
    private static final String[] CODES={"SU","MO","TU","WE","TH","FR","SA"};
    private static SharedPreferences prefs(Context c){return c.getSharedPreferences(PREFS,Context.MODE_PRIVATE);}
    static boolean granted(Context c){return c.checkSelfPermission(Manifest.permission.READ_CALENDAR)==PackageManager.PERMISSION_GRANTED && c.checkSelfPermission(Manifest.permission.WRITE_CALENDAR)==PackageManager.PERMISSION_GRANTED;}
    static boolean connected(Context c){return prefs(c).getLong("event",-1)>0;}
    static void unlink(Context c){prefs(c).edit().clear().apply();}
    private static JSONObject result(String state,String message) throws JSONException{return new JSONObject().put("state",state).put("message",message);}
    private static String text(Cursor c,String column){return c.getString(c.getColumnIndexOrThrow(column));}
    private static long number(Cursor c,String column){return c.getLong(c.getColumnIndexOrThrow(column));}
    private static String fingerprint(JSONObject config) throws JSONException{
        JSONArray values=config.getJSONArray("days");List<Integer> days=new ArrayList<>();for(int i=0;i<values.length();i++)days.add(values.getInt(i));Collections.sort(days);
        return days+"|"+config.getString("time")+"|"+config.getInt("durationMinutes")+"|"+config.optLong("notBefore",0);
    }
    static void connect(Activity activity,JSONObject input,Reply reply){
        try{
            if(!granted(activity)){reply.send(result("error","Autorize o acesso ao calendário para conectar."));return;}
            if(connected(activity)){reply.send(result("connected","Uma agenda já está conectada. Use Sincronizar ou desconecte antes de escolher outra."));return;}
            JSONObject local=TrainingReminders.validate(input);local.put("notBefore",input.optLong("notBefore",0));
            List<Long> ids=new ArrayList<>();List<String> labels=new ArrayList<>();
            String[] projection={CalendarContract.Calendars._ID,CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,CalendarContract.Calendars.ACCOUNT_NAME};
            String selection=CalendarContract.Calendars.ACCOUNT_TYPE+"=? AND "+CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL+">=? AND "+CalendarContract.Calendars.SYNC_EVENTS+"=1";
            try(Cursor cursor=activity.getContentResolver().query(CalendarContract.Calendars.CONTENT_URI,projection,selection,new String[]{"com.google",String.valueOf(CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR)},null)){
                if(cursor!=null)while(cursor.moveToNext()){ids.add(number(cursor,CalendarContract.Calendars._ID));labels.add(text(cursor,CalendarContract.Calendars.CALENDAR_DISPLAY_NAME)+" · "+text(cursor,CalendarContract.Calendars.ACCOUNT_NAME));}
            }
            if(ids.isEmpty()){reply.send(result("error","Nenhuma agenda Google editável foi encontrada. Adicione sua conta Google e ative a sincronização de calendário nas configurações do Android."));return;}
            new AlertDialog.Builder(activity).setTitle("Criar série de natação em qual agenda?")
                .setItems(labels.toArray(new String[0]),(dialog,which)->create(activity,local,ids.get(which),reply))
                .setNegativeButton("Cancelar",(dialog,which)->safeReply(reply,"cancelled","Conexão cancelada. Nenhum evento foi criado."))
                .setOnCancelListener(dialog->safeReply(reply,"cancelled","Conexão cancelada. Nenhum evento foi criado.")).show();
        }catch(Exception error){safeReply(reply,"error","Não foi possível consultar as agendas. Confira a permissão e a conta Google no Android.");}
    }
    private static void safeReply(Reply reply,String state,String message){try{reply.send(result(state,message));}catch(JSONException ignored){}}
    private static String rule(JSONObject config) throws JSONException{
        StringBuilder r=new StringBuilder("FREQ=WEEKLY;BYDAY=");JSONArray days=config.getJSONArray("days");
        for(int i=0;i<days.length();i++){if(i>0)r.append(',');r.append(CODES[days.getInt(i)]);}return r.append(";WKST=MO").toString();
    }
    private static ContentValues eventValues(Context context,JSONObject config,long first,String token) throws JSONException{
        ContentValues values=new ContentValues();values.put(CalendarContract.Events.TITLE,"Natação");
        values.put(CalendarContract.Events.DESCRIPTION,"Planejamento sincronizado com o Nado no meu ritmo. Registre a sessão no diário após nadar. Os lembretes do diário e da agenda são independentes.");
        values.put(CalendarContract.Events.DTSTART,first);values.putNull(CalendarContract.Events.DTEND);
        values.put(CalendarContract.Events.DURATION,"PT"+config.getInt("durationMinutes")+"M");values.put(CalendarContract.Events.RRULE,rule(config));
        values.put(CalendarContract.Events.EVENT_TIMEZONE,TimeZone.getDefault().getID());values.put(CalendarContract.Events.ALL_DAY,0);
        values.put(CalendarContract.Events.CUSTOM_APP_PACKAGE,context.getPackageName());values.put(CalendarContract.Events.CUSTOM_APP_URI,"nado://calendar/"+token);
        return values;
    }
    private static void create(Activity activity,JSONObject local,long calendar,Reply reply){
        Uri created=null;
        try{
            String token=UUID.randomUUID().toString();long first=TrainingReminders.nextClass(local,System.currentTimeMillis()).getTimeInMillis();
            ContentValues values=eventValues(activity,local,first,token);values.put(CalendarContract.Events.CALENDAR_ID,calendar);
            ArrayList<ContentProviderOperation> operations=new ArrayList<>();operations.add(ContentProviderOperation.newInsert(CalendarContract.Events.CONTENT_URI).withValues(values).build());
            operations.add(ContentProviderOperation.newInsert(CalendarContract.Reminders.CONTENT_URI).withValueBackReference(CalendarContract.Reminders.EVENT_ID,0)
                .withValue(CalendarContract.Reminders.MINUTES,local.getInt("reminderMinutes")).withValue(CalendarContract.Reminders.METHOD,CalendarContract.Reminders.METHOD_ALERT).build());
            ContentProviderResult[] results=activity.getContentResolver().applyBatch(CalendarContract.AUTHORITY,operations);created=results[0].uri;
            JSONObject remote=new JSONObject(local.toString()).put("notBefore",first);
            boolean saved=prefs(activity).edit().putLong("calendar",calendar).putLong("event",ContentUris.parseId(created)).putString("token",token)
                .putString("local",fingerprint(local)).putString("remote",fingerprint(remote)).commit();
            if(!saved)throw new IllegalStateException();
            reply.send(result("connected","Agenda conectada e série semanal criada. A conta Google do Android envia as alterações à nuvem quando sua sincronização está ativa."));
        }catch(Exception error){
            if(created!=null && !connected(activity)){try{activity.getContentResolver().delete(created,null,null);}catch(RuntimeException ignored){}}
            safeReply(reply,"error","Não foi possível concluir a conexão. Confira a agenda e suas permissões antes de tentar novamente.");
        }
    }
    private static JSONObject read(Context context,JSONObject local) throws Exception{
        SharedPreferences p=prefs(context);long event=p.getLong("event",-1),calendar=p.getLong("calendar",-1);
        String[] projection={CalendarContract.Events.CALENDAR_ID,CalendarContract.Events.DTSTART,CalendarContract.Events.DTEND,CalendarContract.Events.DURATION,CalendarContract.Events.RRULE,CalendarContract.Events.EXDATE,CalendarContract.Events.RDATE,CalendarContract.Events.EXRULE,CalendarContract.Events.EVENT_TIMEZONE,CalendarContract.Events.ALL_DAY,CalendarContract.Events.DELETED,CalendarContract.Events.STATUS,CalendarContract.Events.CUSTOM_APP_PACKAGE,CalendarContract.Events.CUSTOM_APP_URI,CalendarContract.Events._SYNC_ID};
        try(Cursor c=context.getContentResolver().query(ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI,event),projection,null,null,null)){
            if(c==null)throw new IllegalStateException();
            if(!c.moveToFirst() || number(c,CalendarContract.Events.DELETED)!=0 || number(c,CalendarContract.Events.STATUS)==CalendarContract.Events.STATUS_CANCELED)return null;
            if(number(c,CalendarContract.Events.CALENDAR_ID)!=calendar || !context.getPackageName().equals(text(c,CalendarContract.Events.CUSTOM_APP_PACKAGE)) || !("nado://calendar/"+p.getString("token","")).equals(text(c,CalendarContract.Events.CUSTOM_APP_URI)))throw new JSONException("O vínculo de origem mudou. Desconecte a agenda e revise o evento antes de reconectar.");
            if(number(c,CalendarContract.Events.ALL_DAY)!=0)throw new JSONException("A série virou evento de dia inteiro. Volte para um horário de natação para sincronizar.");
            for(String field:new String[]{CalendarContract.Events.EXDATE,CalendarContract.Events.RDATE,CalendarContract.Events.EXRULE})if(text(c,field)!=null && !text(c,field).isEmpty())throw new JSONException("A série tem exceções individuais. A primeira versão sincroniza apenas séries semanais simples; nenhum dado foi sobrescrito.");
            String syncId=text(c,CalendarContract.Events._SYNC_ID);
            String filter=CalendarContract.Events.ORIGINAL_ID+"=?";String[] args={String.valueOf(event)};
            if(syncId!=null && !syncId.isEmpty()){filter+=" OR "+CalendarContract.Events.ORIGINAL_SYNC_ID+"=?";args=new String[]{String.valueOf(event),syncId};}
            try(Cursor exceptions=context.getContentResolver().query(CalendarContract.Events.CONTENT_URI,new String[]{CalendarContract.Events._ID},"("+filter+") AND "+CalendarContract.Events.CALENDAR_ID+"=?",append(args,String.valueOf(calendar)),null)){
                if(exceptions!=null && exceptions.moveToFirst())throw new JSONException("Há alterações em ocorrências isoladas. Nenhuma atualização automática foi feita; ajuste a série completa ou desconecte a sincronização.");
            }
            String zone=text(c,CalendarContract.Events.EVENT_TIMEZONE);
            if(zone!=null && !TimeZone.getDefault().hasSameRules(TimeZone.getTimeZone(zone)))throw new JSONException("O fuso da série difere do celular. Ajuste o fuso na agenda antes de sincronizar.");
            String rrule=text(c,CalendarContract.Events.RRULE);
            JSONArray days=new JSONArray();for(int day:CalendarSyncRules.weeklyDays(rrule))days.put(day);
            long start=number(c,CalendarContract.Events.DTSTART);Calendar time=Calendar.getInstance();time.setTimeInMillis(start);
            if(time.get(Calendar.SECOND)!=0 || time.get(Calendar.MILLISECOND)!=0)throw new JSONException("Use horário em minutos inteiros na série.");
            long duration=CalendarSyncRules.durationMinutes(text(c,CalendarContract.Events.DURATION),start,number(c,CalendarContract.Events.DTEND));
            JSONObject config=new JSONObject().put("days",days).put("time",String.format(java.util.Locale.ROOT,"%02d:%02d",time.get(Calendar.HOUR_OF_DAY),time.get(Calendar.MINUTE)))
                .put("durationMinutes",duration).put("reminderMinutes",local.getInt("reminderMinutes"));
            config=TrainingReminders.validate(config);config.put("notBefore",start);config.put("_rawRule",rrule);config.put("_rawDuration",text(c,CalendarContract.Events.DURATION));return config;
        }
    }
    private static String[] append(String[] source,String value){String[] result=new String[source.length+1];System.arraycopy(source,0,result,0,source.length);result[source.length]=value;return result;}
    static void sync(Context context,JSONObject input,String action,Reply reply){
        try{
            if(!connected(context)){reply.send(result("disconnected","Nenhuma agenda conectada."));return;}
            if(!granted(context)){reply.send(result("error","O acesso à agenda foi revogado. Autorize novamente para sincronizar; o diário local permanece disponível."));return;}
            JSONObject local=TrainingReminders.validate(input);local.put("notBefore",input.optLong("notBefore",0));
            SharedPreferences p=prefs(context);
            // An inaccessible/removed calendar is not treated as confirmation that the event was deleted.
            try(Cursor c=context.getContentResolver().query(ContentUris.withAppendedId(CalendarContract.Calendars.CONTENT_URI,p.getLong("calendar",-1)),new String[]{CalendarContract.Calendars._ID},null,null,null)){
                if(c==null || !c.moveToFirst()){unlink(context);reply.send(result("disconnected","A agenda conectada não está mais disponível neste aparelho. O plano local foi preservado."));return;}
            }
            JSONObject remote=read(context,local);
            if(remote==null){unlink(context);TrainingReminders.disable(context);reply.send(result("deleted","A série foi excluída ou cancelada na agenda. O plano foi pausado e os lembretes locais foram desativados."));return;}
            String localFp=fingerprint(local),remoteFp=fingerprint(remote),pending=p.getString("pending",null);
            if("ack".equals(action)){
                if(!remoteFp.equals(pending)){reply.send(result("conflict","A agenda mudou novamente. Toque em Sincronizar para revisar."));return;}
                if(!localFp.equals(remoteFp)){reply.send(result("error","O plano local não corresponde ao horário recebido. Nenhum evento foi alterado."));return;}
                if(!p.edit().putString("local",localFp).putString("remote",remoteFp).remove("pending").commit())throw new IllegalStateException();
                reply.send(result("synced","Horário recebido da Agenda Google e salvo no diário."));return;
            }
            boolean localChanged=!localFp.equals(p.getString("local","")), remoteChanged=!remoteFp.equals(p.getString("remote",""));
            CalendarSyncRules.Decision decision=CalendarSyncRules.decide(localChanged,remoteChanged,pending!=null,localFp.equals(pending),action);
            if(decision==CalendarSyncRules.Decision.CONFLICT){
                reply.send(result("conflict","O horário mudou no diário e na agenda. Escolha qual versão manter; nada foi sobrescrito."));return;
            }
            if(decision==CalendarSyncRules.Decision.PULL){
                if(!p.edit().putString("pending",remoteFp).commit())throw new IllegalStateException();
                reply.send(result("pulled","Horário recebido da agenda. Salvando no diário…").put("schedule",remote));return;
            }
            if(decision==CalendarSyncRules.Decision.PUSH){
                long oldFirst=remote.getLong("notBefore"),notBefore=local.optLong("notBefore",0);
                long first=TrainingReminders.nextClass(local,notBefore>0?notBefore-1:startOfDay(oldFirst)-1).getTimeInMillis();
                ContentValues values=eventValues(context,local,first,p.getString("token",""));
                values.remove(CalendarContract.Events.TITLE);values.remove(CalendarContract.Events.DESCRIPTION);
                String selection=CalendarContract.Events.CALENDAR_ID+"=? AND "+CalendarContract.Events.CUSTOM_APP_PACKAGE+"=? AND "+CalendarContract.Events.CUSTOM_APP_URI+"=? AND "+CalendarContract.Events.DTSTART+"=? AND "+CalendarContract.Events.RRULE+"=?";
                String[] args={String.valueOf(p.getLong("calendar",-1)),context.getPackageName(),"nado://calendar/"+p.getString("token",""),String.valueOf(remote.getLong("notBefore")),remote.getString("_rawRule")};
                String rawDuration=remote.optString("_rawDuration",null);
                if(rawDuration==null)selection+=" AND "+CalendarContract.Events.DURATION+" IS NULL";
                else{selection+=" AND "+CalendarContract.Events.DURATION+"=?";args=append(args,rawDuration);}
                int count=context.getContentResolver().update(ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI,p.getLong("event",-1)),values,selection,args);
                if(count!=1)throw new IllegalStateException();
                JSONObject updated=new JSONObject(local.toString()).put("notBefore",first);
                if(!p.edit().putString("local",localFp).putString("remote",fingerprint(updated)).remove("pending").commit())throw new IllegalStateException();
                reply.send(result("synced","Plano do diário enviado para a série da Agenda Google. A sincronização da conta no Android fará o envio à nuvem."));return;
            }
            reply.send(result("synced","Diário e série da agenda estão em dia. O envio à nuvem depende da sincronização da conta Google no Android."));
        }catch(JSONException | IllegalArgumentException error){safeReply(reply,"error",error.getMessage());}
        catch(Exception error){safeReply(reply,"error","Não foi possível sincronizar. Confira a conta e as permissões do calendário e tente novamente. Nenhum outro compromisso é gerenciado pelo diário.");}
    }
    private static long startOfDay(long value){Calendar c=Calendar.getInstance();c.setTimeInMillis(value);c.set(Calendar.HOUR_OF_DAY,0);c.set(Calendar.MINUTE,0);c.set(Calendar.SECOND,0);c.set(Calendar.MILLISECOND,0);return c.getTimeInMillis();}
}
