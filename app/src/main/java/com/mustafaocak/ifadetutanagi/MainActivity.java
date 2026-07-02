package com.mustafaocak.ifadetutanagi;

import android.app.Activity;
import android.os.Bundle;
import android.os.Environment;
import android.webkit.*;
import android.view.*;
import android.widget.Toast;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import androidx.core.content.FileProvider;
import java.io.*;

public class MainActivity extends Activity {

    private WebView webView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Tam ekran
        getWindow().setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN,
            WindowManager.LayoutParams.FLAG_FULLSCREEN
        );
        requestWindowFeature(Window.FEATURE_NO_TITLE);

        webView = new WebView(this);
        setContentView(webView);

        WebSettings ws = webView.getSettings();
        ws.setJavaScriptEnabled(true);
        ws.setDomStorageEnabled(true);
        ws.setAllowFileAccessFromFileURLs(true);
        ws.setAllowUniversalAccessFromFileURLs(true);
        ws.setSupportZoom(false);
        ws.setBuiltInZoomControls(false);
        ws.setDisplayZoomControls(false);
        ws.setLoadWithOverviewMode(true);
        ws.setUseWideViewPort(true);
        ws.setCacheMode(WebSettings.LOAD_DEFAULT);

        // DOCX indirme için JavaScript arayüzü
        webView.addJavascriptInterface(new DocxBridge(), "AndroidBridge");

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                return false;
            }
        });

        webView.setWebChromeClient(new WebChromeClient());

        // Download listener - blob URL'lerini yakala
        webView.setDownloadListener((url, userAgent, contentDisposition, mimetype, contentLength) -> {
            if (url.startsWith("blob:") || url.startsWith("data:")) {
                // JavaScript tarafında AndroidBridge.saveDocx() çağrılacak
                return;
            }
            try {
                Intent intent = new Intent(Intent.ACTION_VIEW);
                intent.setData(Uri.parse(url));
                startActivity(intent);
            } catch (Exception e) {
                Toast.makeText(this, "İndirme başarısız: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });

        webView.loadUrl("file:///android_asset/index.html");
    }

    // JavaScript → Android köprüsü: Base64 DOCX → Dosyaya yaz → Aç
    public class DocxBridge {
        @JavascriptInterface
        public void saveDocx(String base64Data, String fileName) {
            try {
                byte[] bytes = android.util.Base64.decode(base64Data, android.util.Base64.DEFAULT);

                File dir;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    dir = getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
                } else {
                    dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
                }
                if (dir != null && !dir.exists()) dir.mkdirs();

                File outFile = new File(dir, fileName);
                FileOutputStream fos = new FileOutputStream(outFile);
                fos.write(bytes);
                fos.close();

                runOnUiThread(() -> {
                    Toast.makeText(MainActivity.this,
                        "Kaydedildi: " + outFile.getAbsolutePath(), Toast.LENGTH_LONG).show();

                    // Dosyayı Word ile aç
                    try {
                        Uri fileUri;
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                            fileUri = FileProvider.getUriForFile(MainActivity.this,
                                getPackageName() + ".provider", outFile);
                        } else {
                            fileUri = Uri.fromFile(outFile);
                        }
                        Intent intent = new Intent(Intent.ACTION_VIEW);
                        intent.setDataAndType(fileUri,
                            "application/vnd.openxmlformats-officedocument.wordprocessingml.document");
                        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                        startActivity(Intent.createChooser(intent, "Word ile aç"));
                    } catch (Exception e2) {
                        Toast.makeText(MainActivity.this,
                            "Word uygulaması bulunamadı. Dosya kaydedildi: Downloads/" + fileName,
                            Toast.LENGTH_LONG).show();
                    }
                });

            } catch (Exception e) {
                runOnUiThread(() ->
                    Toast.makeText(MainActivity.this,
                        "Kayıt hatası: " + e.getMessage(), Toast.LENGTH_LONG).show()
                );
            }
        }
    }

    @Override
    public void onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }
}
