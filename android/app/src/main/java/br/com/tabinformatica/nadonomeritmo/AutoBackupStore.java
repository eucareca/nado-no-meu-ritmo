/* SPDX-License-Identifier: Apache-2.0 */
package br.com.tabinformatica.nadonomeritmo;

import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.util.AtomicFile;
import org.json.JSONObject;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** One selected file, encrypted snapshots only. The cloud provider owns network synchronization. */
final class AutoBackupStore {
    static final ExecutorService WORK=Executors.newSingleThreadExecutor();
    static final int JOB=4401, LIMIT=5*1024*1024;
    private static SharedPreferences prefs(Context c){return c.getSharedPreferences("automatic-backup",Context.MODE_PRIVATE);}
    private static AtomicFile snapshot(Context c){return new AtomicFile(new File(c.getNoBackupFilesDir(),"automatic-backup.json"));}
    private static AtomicFile previous(Context c){return new AtomicFile(new File(c.getNoBackupFilesDir(),"automatic-backup-delivered.json"));}
    static String target(Context c){return prefs(c).getString("target","");}
    static JSONObject status(Context c){
        SharedPreferences p=prefs(c);JSONObject result=new JSONObject();
        try{
            boolean enabled=!p.getString("uri","").isEmpty();long written=p.getLong("written",0);
            result.put("enabled",enabled);result.put("target",target(c));result.put("written",written);
            result.put("message",!enabled?"Backup automático desativado.":p.getString("message","Destino autorizado. Preparando a primeira cópia criptografada."));
        }catch(Exception ignored){}return result;
    }
    private static void release(Context c,String value){
        if(value.isEmpty())return;
        try{c.getContentResolver().releasePersistableUriPermission(Uri.parse(value),Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_WRITE_URI_PERMISSION);}catch(RuntimeException ignored){}
    }
    static void disconnect(Context c) throws IOException {
        String uri=prefs(c).getString("uri","");
        if(!prefs(c).edit().clear().commit())throw new IOException("Não foi possível desativar o destino. Tente novamente.");
        JobScheduler scheduler=c.getSystemService(JobScheduler.class);if(scheduler!=null)scheduler.cancel(JOB);
        snapshot(c).delete();previous(c).delete();release(c,uri);
    }
    static void connect(Context c,Uri uri,int flags) throws IOException {
        if(!"content".equals(uri.getScheme()))throw new IOException("Escolha um destino no seletor de arquivos do Android.");
        int rw=Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_WRITE_URI_PERMISSION;
        if((flags&rw)!=rw || (flags&Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)==0)throw new IOException("Esse destino não permite acesso permanente. Escolha outro provedor.");
        if(!target(c).isEmpty())throw new IOException("Desative o destino atual antes de trocar de conta.");
        try{c.getContentResolver().takePersistableUriPermission(uri,rw);}catch(SecurityException error){throw new IOException("O provedor não autorizou o acesso permanente.");}
        if(!prefs(c).edit().putString("uri",uri.toString()).putString("target",UUID.randomUUID().toString()).putString("message","Destino autorizado. Preparando a primeira cópia criptografada.").commit()){
            release(c,uri.toString());throw new IOException("Não foi possível guardar a autorização.");
        }
    }
    private static void atomicWrite(AtomicFile file,byte[] data) throws IOException {
        FileOutputStream stream=null;
        try{stream=file.startWrite();stream.write(data);file.finishWrite(stream);}catch(IOException error){if(stream!=null)file.failWrite(stream);throw error;}
    }
    static void stage(Context c,String target,String text) throws Exception {
        if(target.isEmpty() || !target.equals(target(c)))throw new IOException("O destino foi desativado ou alterado. Nada foi enviado.");
        byte[] bytes=text.getBytes(StandardCharsets.UTF_8);if(bytes.length>LIMIT)throw new IOException("Backup muito grande.");
        JSONObject envelope=new JSONObject(text);
        if(!"nado-no-meu-ritmo".equals(envelope.getString("format")) || !envelope.getBoolean("encrypted") || envelope.getInt("version")!=1 || !"AES-256-GCM".equals(envelope.getString("algorithm")) || !"PBKDF2-SHA256".equals(envelope.getString("kdf")) || envelope.getInt("iterations")!=310000 || envelope.has("diary"))throw new IOException("Somente backups criptografados são aceitos.");
        if(android.util.Base64.decode(envelope.getString("salt"),0).length!=16 || android.util.Base64.decode(envelope.getString("iv"),0).length!=12 || android.util.Base64.decode(envelope.getString("data"),0).length<16)throw new IOException("Backup criptografado inválido.");
        atomicWrite(snapshot(c),bytes);
        prefs(c).edit().putString("message","Cópia criptografada preparada no aparelho. Aguardando entrega ao destino escolhido.").apply();
        schedule(c);
    }
    static void resume(Context c){
        JobScheduler scheduler=c.getSystemService(JobScheduler.class);if(scheduler!=null)for(JobInfo job:scheduler.getAllPendingJobs())if(job.getId()==JOB)return;
        schedule(c);
    }
    static void schedule(Context c){
        if(target(c).isEmpty() || !snapshot(c).getBaseFile().exists())return;
        JobScheduler scheduler=c.getSystemService(JobScheduler.class);if(scheduler==null)return;
        int result;try{result=scheduler.schedule(new JobInfo.Builder(JOB,new ComponentName(c,AutoBackupJobService.class)).setMinimumLatency(60000).setPersisted(true).setBackoffCriteria(30*60*1000L,JobInfo.BACKOFF_POLICY_EXPONENTIAL).build());}catch(RuntimeException error){result=JobScheduler.RESULT_FAILURE;}
        if(result!=JobScheduler.RESULT_SUCCESS)prefs(c).edit().putString("message","Cópia local preparada, mas o Android não agendou a entrega. Abra o app e tente novamente.").apply();
    }
    private static byte[] read(AtomicFile file) throws IOException {
        try(InputStream input=file.openRead();ByteArrayOutputStream out=new ByteArrayOutputStream()){
            byte[] buffer=new byte[32768];int count;while((count=input.read(buffer))!=-1){if(out.size()+count>LIMIT)throw new IOException();out.write(buffer,0,count);}return out.toByteArray();
        }
    }
    static boolean deliver(Context c){
        SharedPreferences p=prefs(c);String uri=p.getString("uri","");if(uri.isEmpty() || !snapshot(c).getBaseFile().exists())return true;
        try{
            byte[] bytes=read(snapshot(c));String digest=android.util.Base64.encodeToString(MessageDigest.getInstance("SHA-256").digest(bytes),android.util.Base64.NO_WRAP);
            if(digest.equals(p.getString("delivered","")))return true;
            if(Thread.currentThread().isInterrupted())return false;
            // The selected document is exclusively created for this device. Never read/merge remote diary data.
            try(OutputStream output=c.getContentResolver().openOutputStream(Uri.parse(uri),"wt")){
                if(output==null)throw new IOException();
                for(int offset=0;offset<bytes.length;offset+=32768){if(Thread.currentThread().isInterrupted())throw new IOException();output.write(bytes,offset,Math.min(32768,bytes.length-offset));}
                output.flush();
            }
            atomicWrite(previous(c),bytes);
            long now=System.currentTimeMillis();String date=java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.SHORT,java.text.DateFormat.SHORT).format(new java.util.Date(now));
            p.edit().putString("delivered",digest).putLong("written",now).putString("message","Entregue ao destino em "+date+". O Drive/provedor ainda pode estar enviando à nuvem; confira nele.").commit();return true;
        }catch(SecurityException error){p.edit().putString("message","Acesso ao destino revogado. Desative e ative novamente para autorizar um novo arquivo. Cópia local preservada.").apply();return true;}
        catch(Exception error){p.edit().putString("message","Não foi possível entregar ao destino. A cópia criptografada local foi mantida; o Android tentará novamente. Confira a conexão, o espaço e o arquivo no Drive.").apply();return false;}
    }
}
