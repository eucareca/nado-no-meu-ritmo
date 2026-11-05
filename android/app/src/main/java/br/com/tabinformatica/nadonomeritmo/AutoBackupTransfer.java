/* SPDX-License-Identifier: Apache-2.0 */
package br.com.tabinformatica.nadonomeritmo;

import android.webkit.WebView;
import android.os.Handler;
import android.os.Looper;
import org.json.JSONObject;
import org.json.JSONTokener;

/** Bounded transfer of an encrypted ASCII JSON envelope, never a password or clear diary. */
final class AutoBackupTransfer {
    interface Callback {void ready(String text);void failed();}
    private final WebView web;
    private int generation;
    AutoBackupTransfer(WebView web){this.web=web;}
    void cancel(){generation++;}
    void read(String id,int length,Callback callback){
        cancel();int run=generation;
        if(length<=0 || length>AutoBackupStore.LIMIT){callback.failed();return;}
        new Handler(Looper.getMainLooper()).postDelayed(()->{if(run==generation){cancel();callback.failed();}},60000);
        chunk(run,id,0,length,new StringBuilder(Math.min(length,65536)),callback);
    }
    private void chunk(int run,String id,int offset,int length,StringBuilder text,Callback callback){
        if(run!=generation)return;
        if(offset==length){generation++;callback.ready(text.toString());return;}
        int count=Math.min(32768,length-offset);
        web.evaluateJavascript("window.autoBackupChunk("+JSONObject.quote(id)+","+offset+","+count+")",value->{
            if(run!=generation)return;
            try{
                Object decoded=new JSONTokener(value).nextValue();if(!(decoded instanceof String) || ((String)decoded).length()!=count)throw new Exception();
                text.append((String)decoded);chunk(run,id,offset+count,length,text,callback);
            }catch(Exception error){cancel();callback.failed();}
        });
    }
}
