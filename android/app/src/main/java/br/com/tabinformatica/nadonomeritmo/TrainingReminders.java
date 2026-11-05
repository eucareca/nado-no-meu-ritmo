/* SPDX-License-Identifier: Apache-2.0 */
package br.com.tabinformatica.nadonomeritmo;

import android.Manifest;
import android.app.Activity;
import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.provider.CalendarContract;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.util.Calendar;
import java.util.TimeZone;
import java.util.UUID;

/** On-device reminders. No calendar permissions, network service or exact-alarm privilege. */
final class TrainingReminders {
    private static final String PREFS = "training.reminders.v1";
    private static final String CHANNEL = "swim_training";
    private static final int ALARM_ID = 710;
    private static SharedPreferences preferences(Context context) { return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE); }
    static JSONObject validate(JSONObject raw) throws JSONException {
        JSONArray source = raw.getJSONArray("days");
        if (source.length() < 1 || source.length() > 7) throw new JSONException("Dias inválidos");
        boolean[] seen = new boolean[7]; JSONArray days = new JSONArray();
        for (int i=0;i<source.length();i++) {
            double value=source.getDouble(i); int day=(int)value;
            if (day!=value || day<0 || day>6 || seen[day]) throw new JSONException("Dias inválidos");
            seen[day]=true; days.put(day);
        }
        String time=raw.getString("time");
        if (!time.matches("(?:[01][0-9]|2[0-3]):[0-5][0-9]")) throw new JSONException("Horário inválido");
        int duration=integer(raw,"durationMinutes",1,240), advance=integer(raw,"reminderMinutes",0,1440);
        JSONObject result=new JSONObject(); result.put("days",days);result.put("time",time);result.put("durationMinutes",duration);result.put("reminderMinutes",advance);
        result.put("enabled",raw.optBoolean("enabled",false));
        long notBefore=raw.optLong("notBefore",0);
        if(notBefore<0 || notBefore>32503680000000L)throw new JSONException("Início inválido");
        if(notBefore>0)result.put("notBefore",notBefore);
        return result;
    }
    private static int integer(JSONObject raw,String key,int min,int max) throws JSONException {
        double value=raw.getDouble(key);int n=(int)value;
        if(n!=value || n<min || n>max) throw new JSONException("Valor inválido");return n;
    }
    private static JSONObject stored(Context context) {
        try { return new JSONObject(preferences(context).getString("config","{}")); }
        catch(JSONException error) { return new JSONObject(); }
    }
    static boolean enabled(Context context) { return stored(context).optBoolean("enabled",false); }
    static boolean notificationsAllowed(Context context) {
        if(Build.VERSION.SDK_INT>=33 && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED) return false;
        NotificationManager manager=context.getSystemService(NotificationManager.class);
        if(manager==null) return false;
        if(Build.VERSION.SDK_INT>=24 && !manager.areNotificationsEnabled()) return false;
        if(Build.VERSION.SDK_INT>=26) {
            NotificationChannel channel=manager.getNotificationChannel(CHANNEL);
            if(channel!=null && channel.getImportance()==NotificationManager.IMPORTANCE_NONE) return false;
        }
        return true;
    }
    static void disable(Context context) {
        cancel(context);
        preferences(context).edit().remove("config").apply();
        NotificationManager notifications=context.getSystemService(NotificationManager.class);
        if(notifications!=null)notifications.cancel(712);
    }
    static void apply(Context context,JSONObject input) throws JSONException {
        JSONObject config=validate(input);config.put("token",UUID.randomUUID().toString());
        cancel(context);
        if(!preferences(context).edit().putString("config",config.toString()).commit()) throw new JSONException("Não foi possível salvar lembretes");
        schedule(context);
    }
    private static PendingIntent alarmIntent(Context context,Intent intent,int flags) {
        return PendingIntent.getBroadcast(context,ALARM_ID,intent,flags|PendingIntent.FLAG_IMMUTABLE);
    }
    private static void cancel(Context context) {
        Intent intent=new Intent(context,TrainingReminderReceiver.class).setAction("br.com.tabinformatica.nadonomeritmo.TRAINING_REMINDER");
        PendingIntent pending=alarmIntent(context,intent,PendingIntent.FLAG_NO_CREATE);
        if(pending!=null) { AlarmManager manager=context.getSystemService(AlarmManager.class);if(manager!=null)manager.cancel(pending);pending.cancel(); }
    }
    static Calendar nextClass(JSONObject config,long after) throws JSONException {
        after=Math.max(after,config.optLong("notBefore",0)-1);
        String[] time=config.getString("time").split(":");JSONArray days=config.getJSONArray("days");
        for(int offset=0;offset<=8;offset++) {
            Calendar day=Calendar.getInstance();day.setTimeInMillis(after);day.add(Calendar.DATE,offset);
            int weekday=day.get(Calendar.DAY_OF_WEEK)-1;boolean selected=false;
            for(int i=0;i<days.length();i++)if(days.getInt(i)==weekday)selected=true;
            if(!selected) continue;
            day.set(Calendar.HOUR_OF_DAY,Integer.parseInt(time[0]));day.set(Calendar.MINUTE,Integer.parseInt(time[1]));day.set(Calendar.SECOND,0);day.set(Calendar.MILLISECOND,0);
            if(day.getTimeInMillis()>after)return day;
        }
        throw new JSONException("Nenhum próximo treino");
    }
    static void schedule(Context context) {
        JSONObject config=stored(context);
        if(!config.optBoolean("enabled",false))return;
        try {
            long now=System.currentTimeMillis();int advance=config.getInt("reminderMinutes");
            Calendar next=nextClass(config,now+advance*60000L);
            long start=next.getTimeInMillis(),trigger=start-advance*60000L;
            Intent intent=new Intent(context,TrainingReminderReceiver.class).setAction("br.com.tabinformatica.nadonomeritmo.TRAINING_REMINDER")
                .putExtra("start",start).putExtra("token",config.getString("token")).putExtra("time",config.getString("time"));
            PendingIntent pending=alarmIntent(context,intent,PendingIntent.FLAG_UPDATE_CURRENT);
            AlarmManager manager=context.getSystemService(AlarmManager.class);
            if(manager!=null)manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,trigger,pending);
        }catch(JSONException | RuntimeException error) { disable(context); }
    }
    static void received(Context context,Intent intent) {
        JSONObject config=stored(context);
        long start=intent.getLongExtra("start",0);
        if(config.optBoolean("enabled",false) && config.optString("token").equals(intent.getStringExtra("token")) && start+config.optInt("durationMinutes",45)*60000L>=System.currentTimeMillis()) {
            show(context,"Natação às "+intent.getStringExtra("time"),"Seu treino está previsto. Depois, registre ou importe a sessão no diário.");
        }
        schedule(context);
    }
    static void show(Context context,String title,String message) {
        if(!notificationsAllowed(context))return;
        NotificationManager manager=context.getSystemService(NotificationManager.class);
        if(manager==null)return;
        if(Build.VERSION.SDK_INT>=26) manager.createNotificationChannel(new NotificationChannel(CHANNEL,"Dias de natação",NotificationManager.IMPORTANCE_DEFAULT));
        Intent open=new Intent(context,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP).putExtra("openTraining",true);
        PendingIntent pending=PendingIntent.getActivity(context,711,open,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder builder=Build.VERSION.SDK_INT>=26?new Notification.Builder(context,CHANNEL):new Notification.Builder(context);
        builder.setSmallIcon(R.drawable.ic_training_notification).setContentTitle(title).setContentText(message).setContentIntent(pending).setAutoCancel(true).setVisibility(Notification.VISIBILITY_PRIVATE);
        if(Build.VERSION.SDK_INT<26)builder.setDefaults(Notification.DEFAULT_SOUND);
        try { manager.notify(712,builder.build()); }catch(SecurityException ignored) { }
    }
    static void calendar(Activity activity,JSONObject raw) throws JSONException {
        JSONObject config=validate(raw);Calendar next=nextClass(config,System.currentTimeMillis());
        String[] codes={"SU","MO","TU","WE","TH","FR","SA"};JSONArray days=config.getJSONArray("days");StringBuilder rule=new StringBuilder("FREQ=WEEKLY;BYDAY=");
        for(int i=0;i<days.length();i++){if(i>0)rule.append(',');rule.append(codes[days.getInt(i)]);}rule.append(";WKST=MO");
        Intent event=new Intent(Intent.ACTION_INSERT).setData(CalendarContract.Events.CONTENT_URI)
            .putExtra(CalendarContract.Events.TITLE,"Natação")
            .putExtra(CalendarContract.Events.DESCRIPTION,"Treino planejado no Nado no meu ritmo. Após nadar, registre ou importe sua sessão para acompanhar a frequência. Confira o lembrete e a conta escolhida antes de salvar.")
            .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME,next.getTimeInMillis())
            .putExtra(CalendarContract.EXTRA_EVENT_END_TIME,next.getTimeInMillis()+config.getInt("durationMinutes")*60000L)
            .putExtra(CalendarContract.Events.EVENT_TIMEZONE,TimeZone.getDefault().getID()).putExtra(CalendarContract.Events.RRULE,rule.toString());
        try { activity.startActivity(new Intent(event).setPackage("com.google.android.calendar")); }
        catch(android.content.ActivityNotFoundException error) { activity.startActivity(event); }
    }
}
