/* SPDX-License-Identifier: Apache-2.0 */
package br.com.tabinformatica.nadonomeritmo;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.provider.AlarmClock;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.Toast;

import org.json.JSONObject;
import org.json.JSONTokener;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.UUID;

public final class MainActivity extends Activity {
    private static final String ORIGIN = "https://appassets.androidplatform.net";
    private static final String PREFIX = "/assets/www/";
    private static final int SAVE_BACKUP = 10;
    private static final int OPEN_BACKUP = 11;
    private static final int BACKUP_LIMIT = 5 * 1024 * 1024;
    private WebView webView;
    private ValueCallback<Uri[]> fileChooser;
    private String pendingBackup;
    private FrameLayout rootLayout;
    private PhotoExporter photoExporter;
    private File pendingPhotoFile;
    private String pendingPhotoId;
    private static final int SAVE_PHOTO = 12;
    private static final int SELECT_AUTO_BACKUP = 13;
    private String autoSelectId;
    private AutoBackupTransfer autoTransfer;
    private HealthImporter healthImporter;
    private String healthRequestId;
    private static final int HEALTH_PERMISSIONS = 30;
    private static final int TRAINING_NOTIFICATION_PERMISSION = 31;
    private String pendingTrainingId;
    private String pendingTrainingAction;
    private JSONObject pendingTrainingConfig;
    private static final int CALENDAR_PERMISSION = 32;
    private String pendingCalendarId;
    private String pendingCalendarAction;
    private JSONObject pendingCalendarConfig;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        FrameLayout root = new FrameLayout(this);
        rootLayout = root;
        root.setBackgroundColor(0xFF071B2A);
        root.setOnApplyWindowInsetsListener((view, insets) -> {
            if (android.os.Build.VERSION.SDK_INT >= 30) {
                android.graphics.Insets bars = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.ime());
                view.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            } else {
                view.setPadding(insets.getSystemWindowInsetLeft(), insets.getSystemWindowInsetTop(),
                        insets.getSystemWindowInsetRight(), insets.getSystemWindowInsetBottom());
            }
            return insets;
        });
        webView = new WebView(this);
        root.addView(webView, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        settings.setUserAgentString(settings.getUserAgentString() + " NadoFoss/1");
        webView.setWebViewClient(new WebViewClient() {
            @Override public void onPageFinished(WebView view, String url) {
                if ((ORIGIN+PREFIX+"index.html").equals(url)) {
                    updateNativeColorScheme(true);
                }
                if (getIntent().getBooleanExtra("openTraining",false)) openTrainingPanel();
            }
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return !request.isForMainFrame() || handleNavigation(request.getUrl());
            }
            @Override public boolean shouldOverrideUrlLoading(WebView view, String url) {
                return handleNavigation(Uri.parse(url));
            }
            @Override public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                return bundledAsset(request.getUrl());
            }
        });
        webView.setWebChromeClient(new WebChromeClient() {
            @Override public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> callback, FileChooserParams params) {
                if (fileChooser != null) fileChooser.onReceiveValue(null);
                fileChooser = callback;
                boolean image = false;
                String[] accepted = params.getAcceptTypes();
                if (accepted != null) for (String type : accepted) if (type != null && type.startsWith("image/")) image = true;
                Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType(image ? "image/*" : "application/json");
                try { startActivityForResult(intent, OPEN_BACKUP); }
                catch (ActivityNotFoundException error) { fileChooser.onReceiveValue(null); fileChooser = null; showMessage(R.string.no_file_app); }
                return true;
            }
            @Override public boolean onJsConfirm(WebView view, String url, String message, android.webkit.JsResult result) {
                new android.app.AlertDialog.Builder(MainActivity.this).setMessage(message)
                    .setPositiveButton(android.R.string.ok, (dialog, which) -> result.confirm())
                    .setNegativeButton(android.R.string.cancel, (dialog, which) -> result.cancel())
                    .setOnCancelListener(dialog -> result.cancel()).show();
                return true;
            }
        });
        setContentView(root);
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            getOnBackInvokedDispatcher().registerOnBackInvokedCallback(android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT, this::handleBack);
        }
        webView.loadUrl(ORIGIN + PREFIX + "index.html");
        TrainingReminders.schedule(this);
    }

    private WebResourceResponse bundledAsset(Uri uri) {
        if (!ORIGIN.equals(uri.getScheme() + "://" + uri.getAuthority())) return response(403, "Forbidden");
        String path = uri.getPath();
        if (path == null || !path.startsWith(PREFIX)) return response(404, "Not Found");
        String name = path.substring(PREFIX.length());
        if (name.isEmpty() || name.contains("..") || !name.matches("[A-Za-z0-9._/-]+")) return response(404, "Not Found");
        try {
            InputStream stream = getAssets().open("www/" + name);
            String lower = name.toLowerCase(Locale.ROOT);
            String mime = lower.endsWith(".html") ? "text/html" : lower.endsWith(".js") ? "application/javascript" :
                    lower.endsWith(".css") ? "text/css" : lower.endsWith(".png") ? "image/png" :
                    lower.endsWith(".svg") ? "image/svg+xml" : lower.endsWith(".webmanifest") ? "application/manifest+json" : "application/octet-stream";
            return new WebResourceResponse(mime, "UTF-8", stream);
        } catch (java.io.IOException error) { return response(404, "Not Found"); }
    }

    private WebResourceResponse response(int status, String reason) {
        return new WebResourceResponse("text/plain", "UTF-8", status, reason, null,
                new ByteArrayInputStream(reason.getBytes(StandardCharsets.UTF_8)));
    }

    private boolean handleNavigation(Uri uri) {
        if ("nado".equals(uri.getScheme())) {
            if (!(ORIGIN + PREFIX + "index.html").equals(webView.getUrl())) return true;
            if ("appearance".equals(uri.getHost())) applyNativeAppearance(uri.getQueryParameter("theme"));
            else if ("photo".equals(uri.getHost())) handlePhoto(uri);
            else if ("auto-backup".equals(uri.getHost())) handleAutoBackup(uri);
            else if ("calendar-sync".equals(uri.getHost())) handleCalendarSync(uri);
            else if ("training".equals(uri.getHost())) handleTraining(uri);
            else if ("health".equals(uri.getHost())) handleHealth(uri);
            else if ("timer".equals(uri.getHost())) startTimer(uri);
            else if ("save-backup".equals(uri.getHost())) exportBackup(false);
            else if ("share-backup".equals(uri.getHost())) exportBackup(true);
            return true;
        }
        return !ORIGIN.equals(uri.getScheme() + "://" + uri.getAuthority());
    }

    @Override public void onConfigurationChanged(android.content.res.Configuration configuration) {
        super.onConfigurationChanged(configuration);
        updateNativeColorScheme(false);
    }
    private void updateNativeColorScheme(boolean pageReady) {
        if(webView==null)return;
        boolean dark=(getResources().getConfiguration().uiMode & android.content.res.Configuration.UI_MODE_NIGHT_MASK)==android.content.res.Configuration.UI_MODE_NIGHT_YES;
        String script=(pageReady?"window.nadoNativeReady=true;window.nadoAutoBackupReady=true;":"")+"window.setNativeColorScheme && window.setNativeColorScheme("+dark+");"+(pageReady?"window.syncCalendarOnResume && window.syncCalendarOnResume();":"")+"document.documentElement.dataset.theme;";
        webView.evaluateJavascript(script,value->{try{Object theme=new JSONTokener(value).nextValue();if(theme instanceof String)applyNativeAppearance((String)theme);}catch(Exception ignored){}});
    }
    private void applyNativeAppearance(String theme) {
        boolean light="light".equals(theme);if(!light && !"dark".equals(theme))return;
        int background=light?0xFFF1F7FA:0xFF071B2A;
        if(rootLayout!=null)rootLayout.setBackgroundColor(background);
        int flags=getWindow().getDecorView().getSystemUiVisibility();
        if(light)flags|=android.view.View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;else flags&=~android.view.View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
        if(android.os.Build.VERSION.SDK_INT>=26){if(light)flags|=android.view.View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;else flags&=~android.view.View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;}
        getWindow().getDecorView().setSystemUiVisibility(flags);
        getWindow().setNavigationBarColor(background);
    }
    private void photoReply(String id,String message) {
        if(webView==null || !(ORIGIN+PREFIX+"index.html").equals(webView.getUrl()))return;
        JSONObject payload=new JSONObject();try{payload.put("message",message);}catch(org.json.JSONException ignored){}
        webView.evaluateJavascript("window.receivePhotoExport && window.receivePhotoExport("+JSONObject.quote(id)+","+payload+");",null);
    }
    private void handlePhoto(Uri uri) {
        String id=uri.getQueryParameter("id"),action=uri.getQueryParameter("action");
        if(id==null || !id.matches("[a-fA-F0-9-]{36}"))return;
        if("cancel".equals(action)){
            if(photoExporter!=null)photoExporter.cancel();
            if(id.equals(pendingPhotoId)){if(pendingPhotoFile!=null)pendingPhotoFile.delete();pendingPhotoFile=null;pendingPhotoId=null;}
            return;
        }
        if(!"save".equals(action) && !"share".equals(action))return;
        if(pendingPhotoFile!=null){photoReply(id,"Conclua o salvamento de foto já aberto.");return;}
        if(photoExporter==null)photoExporter=new PhotoExporter(this,webView);
        photoExporter.export(id,new PhotoExporter.Callback(){
            @Override public void failed(){photoReply(id,"Não foi possível preparar a imagem. Tente novamente com outra foto.");}
            @Override public void ready(File file){
                if(isFinishing() || isDestroyed()){file.delete();return;}
                try{
                    if("save".equals(action)){
                        pendingPhotoFile=file;pendingPhotoId=id;
                        Intent save=new Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("image/png").putExtra(Intent.EXTRA_TITLE,"nado-minha-natacao.png");
                        startActivityForResult(save,SAVE_PHOTO);
                    }else{
                        Uri photo=Uri.parse("content://"+getPackageName()+".backup/photo/"+file.getName());
                        Intent share=new Intent(Intent.ACTION_SEND).setType("image/png").putExtra(Intent.EXTRA_STREAM,photo).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                        share.setClipData(ClipData.newRawUri("Foto da natação",photo));
                        startActivity(Intent.createChooser(share,"Compartilhar foto da natação"));
                        photoReply(id,"Escolha a rede social ou aplicativo e confirme o envio nele. Nada é publicado automaticamente.");
                    }
                }catch(RuntimeException error){file.delete();pendingPhotoFile=null;pendingPhotoId=null;photoReply(id,"Nenhum aplicativo compatível foi encontrado para salvar ou compartilhar a imagem.");}
            }
        });
    }


    private void autoReply(String id,JSONObject result){
        runOnUiThread(()->{
            if(webView==null || !(ORIGIN+PREFIX+"index.html").equals(webView.getUrl()))return;
            webView.evaluateJavascript("window.receiveAutoBackup && window.receiveAutoBackup("+JSONObject.quote(id)+","+result+");",null);
        });
    }
    private void autoError(String id,String message){
        JSONObject result=new JSONObject();try{result.put("error",true);result.put("message",message==null?"Não foi possível concluir o backup automático.":message);}catch(Exception ignored){}autoReply(id,result);
    }
    private void handleAutoBackup(Uri uri){
        String id=uri.getQueryParameter("id"),action=uri.getQueryParameter("action");
        if(id==null || !id.matches("[a-f0-9-]{36}"))return;
        if("status".equals(action)){autoReply(id,AutoBackupStore.status(getApplicationContext()));AutoBackupStore.WORK.execute(()->AutoBackupStore.resume(getApplicationContext()));return;}
        if("disable".equals(action)){
            if(autoTransfer!=null)autoTransfer.cancel();
            AutoBackupStore.WORK.execute(()->{try{AutoBackupStore.disconnect(getApplicationContext());autoReply(id,AutoBackupStore.status(getApplicationContext()));}catch(Exception error){autoError(id,error.getMessage());}});return;
        }
        if("select".equals(action)){
            if(autoSelectId!=null || !AutoBackupStore.target(this).isEmpty()){autoError(id,"Conclua ou desative o destino atual antes de escolher outro.");return;}
            Intent select=new Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("application/json")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_WRITE_URI_PERMISSION|Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
                .putExtra(Intent.EXTRA_TITLE,"nado-automatico-"+UUID.randomUUID().toString().substring(0,8)+".json");
            autoSelectId=id;try{startActivityForResult(select,SELECT_AUTO_BACKUP);}catch(RuntimeException error){autoSelectId=null;autoError(id,"Nenhum seletor compatível encontrado. Instale e configure o Drive.");}return;
        }
        if("stage".equals(action)){
            String transfer=uri.getQueryParameter("transfer"),target=uri.getQueryParameter("target");
            if(transfer==null || !transfer.matches("[a-f0-9-]{36}") || target==null || !target.equals(AutoBackupStore.target(this))){autoError(id,"Destino não autorizado.");return;}
            int length;try{length=Integer.parseInt(uri.getQueryParameter("length"));}catch(RuntimeException error){autoError(id,"Backup inválido.");return;}
            if(autoTransfer==null)autoTransfer=new AutoBackupTransfer(webView);
            autoTransfer.read(transfer,length,new AutoBackupTransfer.Callback(){
                @Override public void failed(){autoError(id,"Não foi possível preparar a cópia criptografada. Tente atualizar novamente.");}
                @Override public void ready(String text){AutoBackupStore.WORK.execute(()->{
                    try{AutoBackupStore.stage(getApplicationContext(),target,text);autoReply(id,AutoBackupStore.status(getApplicationContext()));}
                    catch(Exception error){autoError(id,"Não foi possível guardar a cópia criptografada. Seus registros foram mantidos.");}
                });}
            });
        }
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent); setIntent(intent);
        if (intent.getBooleanExtra("openTraining",false)) openTrainingPanel();
    }
    private void openTrainingPanel() {
        if (webView != null && (ORIGIN+PREFIX+"index.html").equals(webView.getUrl())) {
            webView.evaluateJavascript("document.getElementById('trainingPlanner') && document.getElementById('trainingPlanner').scrollIntoView({block:'start'});",null);
            getIntent().removeExtra("openTraining");
        }
    }
    @Override protected void onResume() {
        super.onResume();
        if(webView!=null && (ORIGIN+PREFIX+"index.html").equals(webView.getUrl())) webView.evaluateJavascript("window.syncCalendarOnResume && window.syncCalendarOnResume();setTimeout(function(){window.resumeAutoBackup && window.resumeAutoBackup();},500);",null);
    }
    private void calendarReply(String id,JSONObject result) {
        if(webView==null || !(ORIGIN+PREFIX+"index.html").equals(webView.getUrl()))return;
        webView.evaluateJavascript("window.receiveCalendarSync && window.receiveCalendarSync("+JSONObject.quote(id)+","+result+");",null);
    }
    private void calendarMessage(String id,String state,String message) {
        JSONObject result=new JSONObject();try{result.put("state",state);result.put("message",message);}catch(org.json.JSONException ignored){}
        calendarReply(id,result);
    }
    private void handleCalendarSync(Uri uri) {
        String id=uri.getQueryParameter("id"),action=uri.getQueryParameter("action");
        if(id==null || !id.matches("[a-zA-Z0-9-]{1,64}"))return;
        if("disconnect".equals(action)){CalendarSync.unlink(this);calendarMessage(id,"disconnected","Agenda desconectada. A série externa foi mantida e não será mais atualizada pelo diário.");return;}
        if("status".equals(action)) {
            JSONObject result=new JSONObject();try{result.put("state","status");result.put("connected",CalendarSync.connected(this));result.put("granted",CalendarSync.granted(this));
                result.put("message",CalendarSync.connected(this)?(CalendarSync.granted(this)?"Agenda conectada. Conferindo alterações…":"Agenda conectada, mas o acesso foi revogado. Toque em Sincronizar para autorizar novamente."):"Agenda desconectada. Conecte somente se desejar criar e sincronizar uma série de natação.");}catch(org.json.JSONException ignored){}
            calendarReply(id,result);return;
        }
        if(!java.util.Arrays.asList("connect","sync","push","pull","ack").contains(action))return;
        try{
            JSONObject config=new JSONObject(uri.getQueryParameter("data"));
            if(!CalendarSync.granted(this)) {
                if(pendingCalendarId!=null){calendarMessage(id,"error","Conclua a autorização de calendário já aberta.");return;}
                pendingCalendarId=id;pendingCalendarAction=action;pendingCalendarConfig=config;
                requestPermissions(new String[]{android.Manifest.permission.READ_CALENDAR,android.Manifest.permission.WRITE_CALENDAR},CALENDAR_PERMISSION);
            }else finishCalendarAction(id,action,config);
        }catch(RuntimeException | org.json.JSONException error){calendarMessage(id,"error","Não foi possível abrir a sincronização. Confira seu planejamento e tente novamente.");}
    }
    private void finishCalendarAction(String id,String action,JSONObject config) {
        if(!CalendarSync.granted(this)){calendarMessage(id,"error","Acesso ao calendário não autorizado. As outras funções do diário continuam disponíveis.");return;}
        CalendarSync.Reply reply=result->calendarReply(id,result);
        if("connect".equals(action))CalendarSync.connect(this,config,reply);else CalendarSync.sync(this,config,action,reply);
    }

    private void trainingReply(String id,String message,Boolean enabled) {
        if (webView==null || !(ORIGIN+PREFIX+"index.html").equals(webView.getUrl())) return;
        JSONObject result=new JSONObject();
        try { result.put("message",message);if(enabled!=null)result.put("enabled",enabled); }catch(org.json.JSONException ignored) { }
        webView.evaluateJavascript("window.receiveTrainingResult && window.receiveTrainingResult("+JSONObject.quote(id)+","+result+");",null);
    }
    private void handleTraining(Uri uri) {
        String id=uri.getQueryParameter("id"), action=uri.getQueryParameter("action");
        if(id==null || !id.matches("[a-zA-Z0-9-]{1,64}"))return;
        try {
            if("disable".equals(action)) {TrainingReminders.disable(this);trainingReply(id,"Lembretes locais desativados. Eventos já salvos na agenda devem ser removidos nela.",false);return;}
            if("status".equals(action)) {
                boolean enabled=TrainingReminders.enabled(this), allowed=TrainingReminders.notificationsAllowed(this);
                trainingReply(id,enabled?(allowed?"Lembretes ativos neste aparelho. O Android pode atrasar os avisos por economia de bateria.":"O plano de lembretes está salvo, mas as notificações estão bloqueadas nas configurações do Android."):"Lembretes desativados neste aparelho.",enabled);return;
            }
            if("calendar".equals(action)) {
                TrainingReminders.calendar(this,new JSONObject(uri.getQueryParameter("data")));
                trainingReply(id,"Agenda aberta. Confira a conta Google, o horário, a repetição e o lembrete; salve o evento na agenda. Alterações feitas lá não voltam ao diário.",null);return;
            }
            if(!"apply".equals(action) && !"test".equals(action))return;
            if(pendingTrainingId!=null){trainingReply(id,"Conclua a autorização de notificações já aberta.",null);return;}
            JSONObject config="apply".equals(action)?TrainingReminders.validate(new JSONObject(uri.getQueryParameter("data"))):null;
            if(android.os.Build.VERSION.SDK_INT>=33 && checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)!=android.content.pm.PackageManager.PERMISSION_GRANTED) {
                pendingTrainingId=id;pendingTrainingAction=action;pendingTrainingConfig=config;
                requestPermissions(new String[]{android.Manifest.permission.POST_NOTIFICATIONS},TRAINING_NOTIFICATION_PERMISSION);
            } else finishTrainingAction(id,action,config);
        }catch(android.content.ActivityNotFoundException error) {trainingReply(id,"Não foi encontrado aplicativo de calendário. Instale ou ative a Agenda Google; no navegador você também pode baixar o evento .ics.",null);}
        catch(RuntimeException | org.json.JSONException error) {trainingReply(id,"Não foi possível concluir. Confira os dias, horários e permissões do Android.",null);}
    }
    private void finishTrainingAction(String id,String action,JSONObject config) {
        if(!TrainingReminders.notificationsAllowed(this)) {
            if("apply".equals(action))TrainingReminders.disable(this);
            trainingReply(id,"Notificações bloqueadas. Autorize-as nas configurações do Android e tente ativar os lembretes novamente.","apply".equals(action)?false:null);return;
        }
        if("test".equals(action)) {TrainingReminders.show(this,"Teste de lembrete","O aviso de natação está funcionando. Este teste não cria um compromisso.");trainingReply(id,"Notificação de teste enviada. Confira a área de notificações do celular.",null);return;}
        try {
            TrainingReminders.apply(this,config);
            boolean enabled=TrainingReminders.enabled(this);
            trainingReply(id,enabled?"Lembretes locais ativados. Você será avisado antes dos dias e horários salvos. O Android pode atrasar os avisos.":"Não foi possível programar os lembretes. Tente novamente.",enabled);
        }catch(org.json.JSONException error){trainingReply(id,"Não foi possível guardar os lembretes neste aparelho.",false);}
    }

    private void healthReply(String id, JSONObject payload) {
        if (webView == null || !(ORIGIN + PREFIX + "index.html").equals(webView.getUrl())) return;
        webView.evaluateJavascript("window.receiveHealthConnect && window.receiveHealthConnect(" + JSONObject.quote(id) + "," + payload.toString() + ");", null);
    }
    private void healthMessage(String id, String message) {
        JSONObject payload = new JSONObject();
        try { payload.put("error", message); } catch (org.json.JSONException ignored) { }
        healthReply(id, payload);
    }
    private void handleHealth(Uri uri) {
        String id = uri.getQueryParameter("id");
        if (id == null || !id.matches("[a-zA-Z0-9-]{1,64}")) return;
        if (android.os.Build.VERSION.SDK_INT < 34) {
            healthMessage(id, "Esta importação requer Android 14 ou superior com Health Connect disponível. O diário continua funcionando normalmente.");
            return;
        }
        handleHealth34(uri, id);
    }
    @android.annotation.TargetApi(34)
    private void handleHealth34(Uri uri, String id) {
        String action = uri.getQueryParameter("action");
        try {
            if (healthImporter == null) healthImporter = new HealthImporter(this, this::healthReply);
            if ("cancel".equals(action)) { healthImporter.cancel(); healthRequestId = null; return; }
            if ("settings".equals(action)) {
                startActivity(new Intent("android.health.connect.action.HEALTH_HOME_SETTINGS"));
                return;
            }
            if (!"read".equals(action)) return;
            if (healthRequestId != null) { healthMessage(id, "Já existe uma solicitação de autorização. Conclua a janela do Android."); return; }
            healthRequestId = id;
            java.util.ArrayList<String> needed = new java.util.ArrayList<>();
            for (String permission : HealthImporter.PERMISSIONS) {
                if (checkSelfPermission(permission) != android.content.pm.PackageManager.PERMISSION_GRANTED) needed.add(permission);
            }
            if (!needed.isEmpty()) requestPermissions(needed.toArray(new String[0]), HEALTH_PERMISSIONS);
            else { healthRequestId = null; healthImporter.read(id); }
        } catch (RuntimeException error) {
            healthRequestId = null;
            healthMessage(id, "Não foi possível abrir o Health Connect. Confira se está disponível e atualizado neste aparelho.");
        }
    }
    @Override public void onRequestPermissionsResult(int code, String[] permissions, int[] grants) {
        super.onRequestPermissionsResult(code, permissions, grants);
        if(code==CALENDAR_PERMISSION && pendingCalendarId!=null){
            String id=pendingCalendarId,action=pendingCalendarAction;JSONObject config=pendingCalendarConfig;
            pendingCalendarId=null;pendingCalendarAction=null;pendingCalendarConfig=null;
            finishCalendarAction(id,action,config);
        }
        if (code==TRAINING_NOTIFICATION_PERMISSION && pendingTrainingId!=null) {
            String id=pendingTrainingId, action=pendingTrainingAction;JSONObject config=pendingTrainingConfig;
            pendingTrainingId=null;pendingTrainingAction=null;pendingTrainingConfig=null;
            finishTrainingAction(id,action,config);
        }
        if (code == HEALTH_PERMISSIONS && android.os.Build.VERSION.SDK_INT >= 34 && healthImporter != null && healthRequestId != null) {
            String id = healthRequestId; healthRequestId = null;
            healthImporter.read(id);
        }
    }

    private void exportBackup(boolean share) {
        if (!(ORIGIN + PREFIX + "index.html").equals(webView.getUrl())) return;
        webView.evaluateJavascript("JSON.stringify(takePreparedBackup())", encoded -> {
            try {
                Object decoded = new JSONTokener(encoded).nextValue();
                if (!(decoded instanceof String)) throw new IllegalArgumentException();
                JSONObject payload = new JSONObject((String) decoded);
                String text = payload.getString("text");
                String email = payload.optString("email");
                if (text.getBytes(StandardCharsets.UTF_8).length > BACKUP_LIMIT) throw new IllegalArgumentException();
                if (share) shareBackup(text, email);
                else {
                    pendingBackup = text;
                    Intent save = new Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE)
                            .setType("application/json").putExtra(Intent.EXTRA_TITLE, "nado-backup.json");
                    startActivityForResult(save, SAVE_BACKUP);
                }
            } catch (Exception error) { pendingBackup = null; showMessage(R.string.backup_failed); }
        });
    }

    private void shareBackup(String text, String email) throws java.io.IOException {
        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) { showMessage(R.string.invalid_email); return; }
        File directory = new File(getCacheDir(), "backups");
        if (!directory.exists() && !directory.mkdirs()) throw new java.io.IOException();
        File[] oldFiles = directory.listFiles();
        if (oldFiles != null) for (File old : oldFiles) {
            if (System.currentTimeMillis() - old.lastModified() > 86400000L) old.delete();
        }
        String name = UUID.randomUUID().toString() + ".json";
        File file = new File(directory, name);
        try (OutputStream stream = new FileOutputStream(file)) { stream.write(text.getBytes(StandardCharsets.UTF_8)); }
        Uri uri = Uri.parse("content://" + getPackageName() + ".backup/backup/" + name);
        Intent send = new Intent(Intent.ACTION_SEND).setType("application/json")
                .putExtra(Intent.EXTRA_EMAIL, new String[]{email})
                .putExtra(Intent.EXTRA_SUBJECT, getString(R.string.backup_subject))
                .putExtra(Intent.EXTRA_TEXT, getString(R.string.backup_body))
                .putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        send.setClipData(ClipData.newRawUri("Backup", uri));
        try { startActivity(Intent.createChooser(send, getString(R.string.share_backup))); }
        catch (ActivityNotFoundException error) { showMessage(R.string.no_share_app); }
    }

    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if(request==SELECT_AUTO_BACKUP){
            String id=autoSelectId;autoSelectId=null;if(id==null)return;
            if(result!=RESULT_OK || data==null || data.getData()==null){autoError(id,"Escolha de destino cancelada. Backup automático não ativado.");return;}
            Uri destination=data.getData();int flags=data.getFlags();
            AutoBackupStore.WORK.execute(()->{
                try{AutoBackupStore.connect(getApplicationContext(),destination,flags);autoReply(id,AutoBackupStore.status(getApplicationContext()));}
                catch(Exception error){autoError(id,error.getMessage());}
            });
        } else if(request==SAVE_PHOTO){
            File file=pendingPhotoFile;String id=pendingPhotoId;pendingPhotoFile=null;pendingPhotoId=null;
            if(file==null || id==null)return;
            if(result!=RESULT_OK || data==null || data.getData()==null){file.delete();photoReply(id,"Salvamento cancelado.");return;}
            try(InputStream input=new java.io.FileInputStream(file);OutputStream stream=getContentResolver().openOutputStream(data.getData(),"wt")){
                if(stream==null)throw new java.io.IOException();byte[] buffer=new byte[32768];int count;while((count=input.read(buffer))!=-1)stream.write(buffer,0,count);
                photoReply(id,"Foto com logo salva no local escolhido. Você pode publicá-la na sua rede social.");
            }catch(java.io.IOException | SecurityException error){photoReply(id,"Não foi possível salvar a imagem no local escolhido.");}finally{file.delete();}
        } else if (request == OPEN_BACKUP) {
            if (fileChooser != null) {
                Uri uri = result == RESULT_OK && data != null ? data.getData() : null;
                fileChooser.onReceiveValue(uri == null ? null : new Uri[]{uri}); fileChooser = null;
            }
        } else if (request == SAVE_BACKUP) {
            String text = pendingBackup; pendingBackup = null;
            if (result != RESULT_OK || data == null || data.getData() == null || text == null) return;
            try (OutputStream stream = getContentResolver().openOutputStream(data.getData(), "wt")) {
                if (stream == null) throw new java.io.IOException();
                stream.write(text.getBytes(StandardCharsets.UTF_8)); showMessage(R.string.backup_saved);
            } catch (java.io.IOException error) { showMessage(R.string.backup_failed); }
        }
    }

    private void startTimer(Uri uri) {
        try {
            int seconds = Integer.parseInt(uri.getQueryParameter("seconds"));
            if (seconds < 1 || seconds > 10800) { showMessage(R.string.invalid_timer); return; }
            String label = uri.getQueryParameter("label");
            if (label == null || label.trim().isEmpty()) label = getString(R.string.activity);
            Intent timer = new Intent(AlarmClock.ACTION_SET_TIMER).putExtra(AlarmClock.EXTRA_LENGTH, seconds)
                    .putExtra(AlarmClock.EXTRA_MESSAGE, label).putExtra(AlarmClock.EXTRA_SKIP_UI, true);
            startActivity(timer);
        } catch (RuntimeException error) { showMessage(R.string.timer_failed); }
    }
    private void showMessage(int message) { Toast.makeText(this, message, Toast.LENGTH_LONG).show(); }
    @Override public void onBackPressed() { handleBack(); }
    private void handleBack() {
        webView.evaluateJavascript("(function(){var d=document.querySelector('dialog[open]');if(d){d.close();return true;}var s=document.getElementById('settingsPage');if(s&&s.classList.contains('open')){document.getElementById('closeSettings').click();return true;}var m=document.getElementById('modalBackdrop');if(m&&m.classList.contains('open')){m.classList.remove('open');return true;}return false;})()", handled -> { if (!"true".equals(handled)) finish(); });
    }
    @Override protected void onStop() {
        if (android.os.Build.VERSION.SDK_INT >= 34 && healthImporter != null && healthRequestId == null) {
            healthImporter.cancel();
            if (webView != null) webView.evaluateJavascript("window.onNativeHealthPaused && window.onNativeHealthPaused();", null);
        }
        if (webView != null) webView.evaluateJavascript("document.getElementById('backupPassword').value='';", null);
        super.onStop();
    }
    @Override protected void onDestroy() {
        if(photoExporter!=null)photoExporter.cancel();
        if(autoTransfer!=null)autoTransfer.cancel();
        if(pendingPhotoFile!=null)pendingPhotoFile.delete();pendingPhotoFile=null;pendingPhotoId=null;
        if (android.os.Build.VERSION.SDK_INT >= 34 && healthImporter != null) healthImporter.cancel();
        if (fileChooser != null) { fileChooser.onReceiveValue(null); fileChooser = null; }
        pendingBackup = null;
        if (webView != null) { webView.stopLoading(); webView.destroy(); webView = null; }
        super.onDestroy();
    }
}
