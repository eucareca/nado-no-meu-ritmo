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
    private HealthImporter healthImporter;
    private String healthRequestId;
    private static final int HEALTH_PERMISSIONS = 30;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        FrameLayout root = new FrameLayout(this);
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
                Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("application/json");
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
            if ("health".equals(uri.getHost())) handleHealth(uri);
            else if ("timer".equals(uri.getHost())) startTimer(uri);
            else if ("save-backup".equals(uri.getHost())) exportBackup(false);
            else if ("share-backup".equals(uri.getHost())) exportBackup(true);
            return true;
        }
        return !ORIGIN.equals(uri.getScheme() + "://" + uri.getAuthority());
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
        if (request == OPEN_BACKUP) {
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
        if (android.os.Build.VERSION.SDK_INT >= 34 && healthImporter != null) healthImporter.cancel();
        if (fileChooser != null) { fileChooser.onReceiveValue(null); fileChooser = null; }
        pendingBackup = null;
        if (webView != null) { webView.stopLoading(); webView.destroy(); webView = null; }
        super.onDestroy();
    }
}
