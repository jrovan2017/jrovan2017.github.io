package br.com.rovan.orcamentos;

import android.Manifest;
import android.app.Activity;
import android.app.DownloadManager;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.print.PrintAttributes;
import android.print.PrintManager;
import android.provider.MediaStore;
import android.view.View;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ProgressBar;
import android.widget.Toast;
import android.widget.FrameLayout;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

public class MainActivity extends Activity {
    private static final String APP_URL = "https://jrovan2017.github.io/orcamentos/?app=android";
    private static final int FILE_CHOOSER = 1201;
    private static final int STORAGE_PERMISSION = 1202;
    private WebView webView;
    private ProgressBar progress;
    private ValueCallback<Uri[]> fileCallback;
    private String pendingName;
    private String pendingJson;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        FrameLayout root = new FrameLayout(this);
        webView = new WebView(this);
        progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progress.setMax(100);
        root.addView(webView, new FrameLayout.LayoutParams(-1, -1));
        FrameLayout.LayoutParams bar = new FrameLayout.LayoutParams(-1, 8);
        root.addView(progress, bar);
        setContentView(root);

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setAllowFileAccess(true);
        settings.setBuiltInZoomControls(false);
        settings.setUserAgentString(settings.getUserAgentString() + " ROVAN-Android/1.0");
        webView.setBackgroundColor(Color.rgb(237, 243, 247));
        webView.addJavascriptInterface(new AppBridge(), "Android");

        webView.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri uri = request.getUrl();
                if ("jrovan2017.github.io".equals(uri.getHost())) return false;
                startActivity(new Intent(Intent.ACTION_VIEW, uri));
                return true;
            }
            @Override public void onPageFinished(WebView view, String url) {
                progress.setVisibility(View.GONE);
            }
        });

        webView.setWebChromeClient(new WebChromeClient() {
            @Override public void onProgressChanged(WebView view, int newProgress) {
                progress.setProgress(newProgress);
                progress.setVisibility(newProgress < 100 ? View.VISIBLE : View.GONE);
            }
            @Override public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> callback, FileChooserParams params) {
                if (fileCallback != null) fileCallback.onReceiveValue(null);
                fileCallback = callback;
                Intent pick = params.createIntent();
                try { startActivityForResult(pick, FILE_CHOOSER); }
                catch (Exception e) { fileCallback = null; Toast.makeText(MainActivity.this, "Não foi possível abrir os arquivos.", Toast.LENGTH_LONG).show(); }
                return true;
            }
        });

        webView.setDownloadListener((url, userAgent, disposition, mime, length) -> {
            if (url.startsWith("http")) {
                DownloadManager.Request request = new DownloadManager.Request(Uri.parse(url));
                request.setMimeType(mime);
                request.addRequestHeader("User-Agent", userAgent);
                request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
                request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "ROVAN-download");
                ((DownloadManager)getSystemService(DOWNLOAD_SERVICE)).enqueue(request);
            }
        });

        if (state == null) webView.loadUrl(APP_URL); else webView.restoreState(state);
    }

    @Override protected void onSaveInstanceState(Bundle out) {
        webView.saveState(out);
        super.onSaveInstanceState(out);
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == FILE_CHOOSER && fileCallback != null) {
            Uri[] result = resultCode == RESULT_OK ? WebChromeClient.FileChooserParams.parseResult(resultCode, data) : null;
            fileCallback.onReceiveValue(result);
            fileCallback = null;
        }
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        if (requestCode == STORAGE_PERMISSION && results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED) savePending();
        else if (requestCode == STORAGE_PERMISSION) Toast.makeText(this, "Permissão necessária para salvar o backup.", Toast.LENGTH_LONG).show();
    }

    private void requestSave(String name, String json) {
        pendingName = sanitize(name);
        pendingJson = json;
        if (Build.VERSION.SDK_INT <= 28 && checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, STORAGE_PERMISSION);
        } else savePending();
    }

    private void savePending() {
        try {
            OutputStream output;
            if (Build.VERSION.SDK_INT >= 29) {
                ContentValues values = new ContentValues();
                values.put(MediaStore.Downloads.DISPLAY_NAME, pendingName);
                values.put(MediaStore.Downloads.MIME_TYPE, "application/json");
                values.put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);
                Uri uri = getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
                output = getContentResolver().openOutputStream(uri);
            } else {
                File dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
                if (!dir.exists()) dir.mkdirs();
                output = new FileOutputStream(new File(dir, pendingName));
            }
            output.write(pendingJson.getBytes(StandardCharsets.UTF_8));
            output.close();
            Toast.makeText(this, "Backup salvo em Downloads: " + pendingName, Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            Toast.makeText(this, "Não foi possível salvar o backup.", Toast.LENGTH_LONG).show();
        }
    }

    private String sanitize(String name) {
        String clean = name == null ? "ROVAN-Orcamento.json" : name.replaceAll("[^a-zA-Z0-9._-]", "_");
        return clean.endsWith(".json") ? clean : clean + ".json";
    }

    private void printQuote() {
        PrintManager manager = (PrintManager)getSystemService(Context.PRINT_SERVICE);
        manager.print("ROVAN Orçamento", webView.createPrintDocumentAdapter("ROVAN-Orcamento"), new PrintAttributes.Builder().build());
    }

    public class AppBridge {
        @JavascriptInterface public void saveJson(String name, String json) { runOnUiThread(() -> requestSave(name, json)); }
        @JavascriptInterface public void printPage() { runOnUiThread(() -> printQuote()); }
    }

    @Override public void onBackPressed() {
        if (webView.canGoBack()) webView.goBack(); else super.onBackPressed();
    }
}
