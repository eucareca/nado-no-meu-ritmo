/* SPDX-License-Identifier: Apache-2.0 */
package br.com.tabinformatica.nadonomeritmo;

import android.content.Context;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import android.util.Base64;
import android.webkit.WebView;
import org.json.JSONObject;
import org.json.JSONTokener;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

/** Transfers one rendered PNG from the bundled page in bounded chunks, avoiding large IPC payloads. */
final class PhotoExporter {
    interface Callback { void ready(File file); void failed(); }
    private static final int LIMIT=12*1024*1024;
    private static final int CHUNK=65536;
    private final WebView webView;
    private final Context context;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private int generation;
    private File file;
    private FileOutputStream stream;
    private int bytes;

    PhotoExporter(Context context,WebView view){this.context=context;webView=view;}
    void cancel(){
        generation++;
        if(stream!=null){try{stream.close();}catch(IOException ignored){}stream=null;}
        if(file!=null){file.delete();file=null;}
    }
    private void fail(int run,Callback callback){if(run!=generation)return;cancel();callback.failed();}
    void export(String id,Callback callback){
        cancel();final int run=generation;
        try{
            File directory=new File(context.getCacheDir(),"photos");
            if(!directory.exists() && !directory.mkdirs())throw new IOException();
            File[] old=directory.listFiles();if(old!=null)for(File previous:old)if(System.currentTimeMillis()-previous.lastModified()>86400000L)previous.delete();
            file=new File(directory,id+".png");stream=new FileOutputStream(file);bytes=0;
        }catch(IOException error){fail(run,callback);return;}
        handler.postDelayed(()->{if(run==generation)fail(run,callback);},60000);
        webView.evaluateJavascript("JSON.stringify(window.photoExportInfo("+JSONObject.quote(id)+"))",value->{
            if(run!=generation)return;
            try{
                Object decoded=new JSONTokener(value).nextValue();if(!(decoded instanceof String))throw new IOException();
                JSONObject info=new JSONObject((String)decoded);int length=info.getInt("length");
                if(length<=0 || length%4!=0 || length>(LIMIT/3)*4+4)throw new IOException();
                chunk(run,id,0,length,callback);
            }catch(Exception error){fail(run,callback);}
        });
    }
    private void chunk(int run,String id,int offset,int length,Callback callback){
        if(run!=generation)return;
        if(offset==length){
            try{
                stream.close();stream=null;
                byte[] signature=new byte[8];
                try(FileInputStream input=new FileInputStream(file)){if(input.read(signature)!=8)throw new IOException();}
                byte[] png={(byte)137,80,78,71,13,10,26,10};for(int i=0;i<8;i++)if(signature[i]!=png[i])throw new IOException();
                BitmapFactory.Options options=new BitmapFactory.Options();options.inJustDecodeBounds=true;BitmapFactory.decodeFile(file.getAbsolutePath(),options);
                if(options.outWidth!=1080 || (options.outHeight!=1080 && options.outHeight!=1350 && options.outHeight!=1920))throw new IOException();
                File ready=file;file=null;generation++;callback.ready(ready);
            }catch(IOException error){fail(run,callback);}
            return;
        }
        int count=Math.min(CHUNK,length-offset);
        webView.evaluateJavascript("window.photoExportChunk("+JSONObject.quote(id)+","+offset+","+count+")",value->{
            if(run!=generation)return;
            try{
                Object decoded=new JSONTokener(value).nextValue();if(!(decoded instanceof String) || ((String)decoded).length()!=count)throw new IOException();
                byte[] data=Base64.decode((String)decoded,Base64.DEFAULT);bytes+=data.length;if(bytes>LIMIT)throw new IOException();
                stream.write(data);chunk(run,id,offset+count,length,callback);
            }catch(Exception error){fail(run,callback);}
        });
    }
}
