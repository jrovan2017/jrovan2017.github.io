package br.com.rovan.campo;

import android.Manifest;
import android.app.Activity;
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
import android.webkit.GeolocationPermissions;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.ProgressBar;
import android.widget.Toast;
import android.content.ClipData;
import android.provider.Settings;
import androidx.core.content.FileProvider;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class MainActivity extends Activity {
    private static final String APP_URL="https://jrovan2017.github.io/campo/?app=android";
    private static final int FILE_CHOOSER=2201, PERMISSIONS=2202;
    private WebView webView; private ProgressBar progress; private ValueCallback<Uri[]> fileCallback; private Uri cameraUri;
    private String pendingName,pendingJson; private GeolocationPermissions.Callback geoCallback; private String geoOrigin;

    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        FrameLayout root=new FrameLayout(this); webView=new WebView(this); progress=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal); progress.setMax(100);
        root.addView(webView,new FrameLayout.LayoutParams(-1,-1)); root.addView(progress,new FrameLayout.LayoutParams(-1,8)); setContentView(root);
        WebSettings s=webView.getSettings(); s.setJavaScriptEnabled(true); s.setDomStorageEnabled(true); s.setDatabaseEnabled(true); s.setAllowFileAccess(true); s.setUserAgentString(s.getUserAgentString()+" ROVAN-Campo-Android/1.0");
        webView.setBackgroundColor(Color.rgb(238,243,247)); webView.addJavascriptInterface(new AppBridge(),"Android");
        webView.setWebViewClient(new WebViewClient(){@Override public boolean shouldOverrideUrlLoading(WebView v,WebResourceRequest r){Uri u=r.getUrl();if("jrovan2017.github.io".equals(u.getHost()))return false;startActivity(new Intent(Intent.ACTION_VIEW,u));return true;}@Override public void onPageFinished(WebView v,String u){progress.setVisibility(View.GONE);}});
        webView.setWebChromeClient(new WebChromeClient(){
            @Override public void onProgressChanged(WebView v,int n){progress.setProgress(n);progress.setVisibility(n<100?View.VISIBLE:View.GONE);}
            @Override public void onGeolocationPermissionsShowPrompt(String origin,GeolocationPermissions.Callback cb){if(checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED)cb.invoke(origin,true,false);else{geoOrigin=origin;geoCallback=cb;requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION,Manifest.permission.ACCESS_COARSE_LOCATION},PERMISSIONS);}}
            @Override public boolean onShowFileChooser(WebView v,ValueCallback<Uri[]> cb,FileChooserParams p){if(fileCallback!=null)fileCallback.onReceiveValue(null);fileCallback=cb;openChooser(p);return true;}
        });
        if(state==null)webView.loadUrl(APP_URL);else webView.restoreState(state);
    }

    private void openChooser(WebChromeClient.FileChooserParams params){
        Intent content=params.createIntent(); Intent camera=new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        try{File dir=getExternalFilesDir(Environment.DIRECTORY_PICTURES);File photo=File.createTempFile("ROVAN_"+new SimpleDateFormat("yyyyMMdd_HHmmss",Locale.US).format(new Date()),".jpg",dir);cameraUri=FileProvider.getUriForFile(this,getPackageName()+".fileprovider",photo);camera.putExtra(MediaStore.EXTRA_OUTPUT,cameraUri);camera.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION|Intent.FLAG_GRANT_READ_URI_PERMISSION);Intent chooser=Intent.createChooser(content,"Foto ou arquivo");chooser.putExtra(Intent.EXTRA_INITIAL_INTENTS,new Intent[]{camera});startActivityForResult(chooser,FILE_CHOOSER);}catch(Exception e){try{startActivityForResult(content,FILE_CHOOSER);}catch(Exception x){fileCallback.onReceiveValue(null);fileCallback=null;Toast.makeText(this,"Não foi possível abrir a câmera.",Toast.LENGTH_LONG).show();}}
    }

    @Override protected void onActivityResult(int req,int result,Intent data){super.onActivityResult(req,result,data);if(req==FILE_CHOOSER&&fileCallback!=null){Uri[] out=null;if(result==RESULT_OK){if(data==null||data.getData()==null){if(cameraUri!=null)out=new Uri[]{cameraUri};}else out=WebChromeClient.FileChooserParams.parseResult(result,data);}fileCallback.onReceiveValue(out);fileCallback=null;cameraUri=null;}}
    @Override protected void onSaveInstanceState(Bundle out){webView.saveState(out);super.onSaveInstanceState(out);}
    @Override public void onRequestPermissionsResult(int req,String[] p,int[] r){super.onRequestPermissionsResult(req,p,r);if(req==PERMISSIONS&&geoCallback!=null){boolean ok=r.length>0&&r[0]==PackageManager.PERMISSION_GRANTED;geoCallback.invoke(geoOrigin,ok,false);geoCallback=null;}if(req==PERMISSIONS&&pendingJson!=null&&(Build.VERSION.SDK_INT>28||checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)==PackageManager.PERMISSION_GRANTED))savePending();}

    private void requestSave(String name,String json){pendingName=sanitize(name);pendingJson=json;if(Build.VERSION.SDK_INT<=28&&checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)!=PackageManager.PERMISSION_GRANTED)requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},PERMISSIONS);else savePending();}
    private void savePending(){try{OutputStream o;if(Build.VERSION.SDK_INT>=29){ContentValues v=new ContentValues();v.put(MediaStore.Downloads.DISPLAY_NAME,pendingName);v.put(MediaStore.Downloads.MIME_TYPE,"application/json");v.put(MediaStore.Downloads.RELATIVE_PATH,Environment.DIRECTORY_DOWNLOADS);Uri u=getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI,v);o=getContentResolver().openOutputStream(u);}else{File d=Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);if(!d.exists())d.mkdirs();o=new FileOutputStream(new File(d,pendingName));}o.write(pendingJson.getBytes(StandardCharsets.UTF_8));o.close();Toast.makeText(this,"Backup salvo em Downloads: "+pendingName,Toast.LENGTH_LONG).show();pendingJson=null;}catch(Exception e){Toast.makeText(this,"Não foi possível salvar o backup.",Toast.LENGTH_LONG).show();}}
    private String sanitize(String n){String c=n==null?"ROVAN-Campo.json":n.replaceAll("[^a-zA-Z0-9._-]","_");return c.endsWith(".json")?c:c+".json";}
    private void printPage(){PrintManager m=(PrintManager)getSystemService(Context.PRINT_SERVICE);m.print("ROVAN Relatório",webView.createPrintDocumentAdapter("ROVAN-Relatorio"),new PrintAttributes.Builder().build());}
    public class AppBridge{@JavascriptInterface public void saveJson(String n,String j){runOnUiThread(()->requestSave(n,j));}@JavascriptInterface public void printPage(){runOnUiThread(()->MainActivity.this.printPage());}}
    @Override public void onBackPressed(){if(webView.canGoBack())webView.goBack();else super.onBackPressed();}
}
