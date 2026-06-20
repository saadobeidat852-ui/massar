package com.masaar.app;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Base64;
import android.view.View;
import android.webkit.JavascriptInterface;
import android.webkit.PermissionRequest;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.webkit.WebViewAssetLoader;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;

public class MainActivity extends AppCompatActivity {

    // فعّل DEV_MODE أثناء التطوير لتحميل الصفحة من خادم محلي (Live Reload)
    // عطّله قبل بناء نسخة الإصدار (Release) ليرجع التطبيق يحمّل من assets المحلية
    private static final boolean DEV_MODE = false;
    private static final String DEV_URL = "http://localhost:8080/index.html";
    private static final String PROD_URL = "https://appassets.androidplatform.net/assets/index.html";
    private static final String START_URL = DEV_MODE ? DEV_URL : PROD_URL;

    private WebView webView;
    private ProgressBar progressBar;
    private ValueCallback<Uri[]> filePathCallback;
    private static final int FILE_CHOOSER_REQUEST = 5173;

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        webView = findViewById(R.id.webview);
        progressBar = findViewById(R.id.progressBar);

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);
        settings.setLoadWithOverviewMode(true);
        settings.setUseWideViewPort(true);
        settings.setSupportMultipleWindows(false);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setCacheMode(WebSettings.LOAD_DEFAULT);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE);

        // جسر JavaScript لحفظ الملفات المصدّرة (مثل تصدير Excel) في مجلد التنزيلات
        webView.addJavascriptInterface(new AndroidDownloader(this), "AndroidDownloader");

        // جسر JavaScript لعرض إشعارات حقيقية على الموبايل (تذكير حصص، تغيّر مسار طالب...)
        webView.addJavascriptInterface(new AndroidNotifier(this), "AndroidNotifier");
        createNotificationChannel();
        requestNotificationPermission();

        WebViewAssetLoader assetLoader = new WebViewAssetLoader.Builder()
                .addPathHandler("/assets/", new WebViewAssetLoader.AssetsPathHandler(this))
                .build();

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                return assetLoader.shouldInterceptRequest(request.getUrl());
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                progressBar.setVisibility(View.GONE);
            }
        });

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                progressBar.setProgress(newProgress);
                if (newProgress >= 100) progressBar.setVisibility(View.GONE);
            }

            // دعم اختيار الملفات (رفع PDF/Word من التطبيق)
            @Override
            public boolean onShowFileChooser(WebView webView, ValueCallback<Uri[]> callback,
                                              FileChooserParams params) {
                filePathCallback = callback;
                Intent intent = params.createIntent();
                try {
                    startActivityForResult(intent, FILE_CHOOSER_REQUEST);
                } catch (Exception e) {
                    filePathCallback = null;
                    return false;
                }
                return true;
            }

            // السماح بصلاحيات الكاميرا/الميكروفون إن طُلبت من صفحة الويب لاحقاً
            @Override
            public void onPermissionRequest(final PermissionRequest request) {
                runOnUiThread(() -> request.grant(request.getResources()));
            }
        });

        if (isOnline()) {
            // تحميل التطبيق من ملفات الأصول المحلية عبر مضيف افتراضي آمن
            webView.loadUrl(START_URL);
        } else {
            showOfflineDialog();
        }
    }

    public static final String NOTIF_CHANNEL_ID = "masaar_notifications";
    private static final int NOTIF_PERMISSION_REQUEST = 5174;

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    NOTIF_CHANNEL_ID,
                    "إشعارات منصة مسار",
                    NotificationManager.IMPORTANCE_HIGH);
            channel.setDescription("تذكيرات الحصص وتنبيهات تغيّر مسار الطلاب");
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) manager.createNotificationChannel(channel);
        }
    }

    private void requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.POST_NOTIFICATIONS}, NOTIF_PERMISSION_REQUEST);
            }
        }
    }

    private boolean isOnline() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) return false;
        NetworkCapabilities caps = cm.getNetworkCapabilities(cm.getActiveNetwork());
        return caps != null && (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
                || caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
                || caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET));
    }

    private void showOfflineDialog() {
        new AlertDialog.Builder(this)
                .setTitle("لا يوجد اتصال بالإنترنت")
                .setMessage("منصة مسار تحتاج اتصالاً بالإنترنت لتسجيل الدخول ومزامنة البيانات. الرجاء التحقق من الاتصال والمحاولة مجدداً.")
                .setCancelable(false)
                .setPositiveButton("إعادة المحاولة", (d, w) -> {
                    if (isOnline()) {
                        webView.loadUrl(START_URL);
                    } else {
                        showOfflineDialog();
                    }
                })
                .setNegativeButton("خروج", (d, w) -> finish())
                .show();
    }

    @Override
    public void onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == FILE_CHOOSER_REQUEST) {
            if (filePathCallback == null) return;
            Uri[] results = null;
            if (resultCode == Activity.RESULT_OK && data != null) {
                String dataString = data.getDataString();
                if (dataString != null) {
                    results = new Uri[]{Uri.parse(dataString)};
                } else if (data.getClipData() != null) {
                    int count = data.getClipData().getItemCount();
                    results = new Uri[count];
                    for (int i = 0; i < count; i++) {
                        results[i] = data.getClipData().getItemAt(i).getUri();
                    }
                }
            }
            filePathCallback.onReceiveValue(results);
            filePathCallback = null;
        } else {
            super.onActivityResult(requestCode, resultCode, data);
        }
    }

    /**
     * جسر JavaScript: يستقبل بيانات الملفات المصدّرة (Base64) من صفحة الويب
     * (مثل ملفات Excel الناتجة عن XLSX.writeFile) ويحفظها في مجلد التنزيلات.
     */
    public static class AndroidDownloader {
        private final Context context;

        AndroidDownloader(Context context) {
            this.context = context;
        }

        @JavascriptInterface
        public void saveBase64File(String base64Data, String filename, String mimeType) {
            try {
                byte[] bytes = Base64.decode(base64Data, Base64.DEFAULT);

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    ContentValues values = new ContentValues();
                    values.put(MediaStore.MediaColumns.DISPLAY_NAME, filename);
                    values.put(MediaStore.MediaColumns.MIME_TYPE, mimeType);
                    values.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);
                    Uri uri = context.getContentResolver()
                            .insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
                    if (uri != null) {
                        try (OutputStream os = context.getContentResolver().openOutputStream(uri)) {
                            if (os != null) os.write(bytes);
                        }
                    }
                } else {
                    File downloadsDir = Environment.getExternalStoragePublicDirectory(
                            Environment.DIRECTORY_DOWNLOADS);
                    if (!downloadsDir.exists()) downloadsDir.mkdirs();
                    File outFile = new File(downloadsDir, filename);
                    try (FileOutputStream fos = new FileOutputStream(outFile)) {
                        fos.write(bytes);
                    }
                }

                notifyUser("✅ تم حفظ الملف في مجلد التنزيلات: " + filename);
            } catch (Exception e) {
                notifyUser("⚠️ تعذّر حفظ الملف: " + e.getMessage());
            }
        }

        private void notifyUser(String message) {
            if (context instanceof Activity) {
                ((Activity) context).runOnUiThread(() ->
                        Toast.makeText(context, message, Toast.LENGTH_LONG).show());
            }
        }
    }

    /**
     * جسر JavaScript: يعرض إشعار نظام حقيقي على الموبايل (شريط الإشعارات).
     * يعمل طالما تطبيق منسار شغّال (بالواجهة أو بالخلفية) — أي JS قادر ينفّذ.
     * لإشعارات تصل حتى لو التطبيق مغلق تمامًا، يلزم ربط Firebase Cloud Messaging
     * (راجع ملفات functions/ المرفقة لشرح الخطوة التالية).
     */
    public static class AndroidNotifier {
        private final Context context;
        private static int notifId = 2000;

        AndroidNotifier(Context context) {
            this.context = context;
        }

        @JavascriptInterface
        public void notify(String title, String message) {
            if (!(context instanceof Activity)) return;
            ((Activity) context).runOnUiThread(() -> {
                try {
                    Intent intent = new Intent(context, MainActivity.class);
                    PendingIntent pi = PendingIntent.getActivity(context, 0, intent,
                            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

                    NotificationCompat.Builder builder = new NotificationCompat.Builder(context, NOTIF_CHANNEL_ID)
                            .setSmallIcon(android.R.drawable.ic_dialog_info)
                            .setContentTitle(title)
                            .setContentText(message)
                            .setStyle(new NotificationCompat.BigTextStyle().bigText(message))
                            .setPriority(NotificationCompat.PRIORITY_HIGH)
                            .setAutoCancel(true)
                            .setContentIntent(pi);

                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                            ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                                    == PackageManager.PERMISSION_GRANTED) {
                        NotificationManagerCompat.from(context).notify(notifId++, builder.build());
                    }
                } catch (Exception ignored) {
                }
            });
        }
    }
}
