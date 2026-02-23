package com.ytpro.app;

import android.Manifest;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.IBinder;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {

    private WebView webView;
    private static final int PERMISSION_REQUEST_CODE = 100;
    private String ytDlpPath;
    private final ExecutorService executor = Executors.newFixedThreadPool(4);

    // MediaPlayerService bağlantısı
    private MediaPlayerService mediaService;
    private boolean mediaServiceBound = false;

    private final ServiceConnection mediaConn = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            mediaService = ((MediaPlayerService.LocalBinder) service).getService();
            mediaServiceBound = true;
        }
        @Override
        public void onServiceDisconnected(ComponentName name) {
            mediaServiceBound = false;
        }
    };

    // Kilit ekranı buton komutlarını WebView'e ilet
    private final BroadcastReceiver mediaControlReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context ctx, Intent intent) {
            String action = intent.getStringExtra("action");
            if (action == null || webView == null) return;
            String js = "";
            switch (action) {
                case MediaPlayerService.ACTION_PLAY:   js = "togglePlay(true)";  break;
                case MediaPlayerService.ACTION_PAUSE:  js = "togglePlay(false)"; break;
                case MediaPlayerService.ACTION_NEXT:   js = "nextTrack()";       break;
                case MediaPlayerService.ACTION_PREV:   js = "prevTrack()";       break;
                case MediaPlayerService.ACTION_STOP:   js = "setPlay(false)";    break;
            }
            if (!js.isEmpty()) {
                final String finalJs = js;
                webView.post(() -> webView.evaluateJavascript(finalJs, null));
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(0xFF0a0a0a);
        getWindow().setNavigationBarColor(0xFF0a0a0a);
        setContentView(R.layout.activity_main);

        ytDlpPath = getFilesDir().getAbsolutePath() + "/yt-dlp";
        setupYtDlp();
        
        // NewPipe Extractor başlat (arka planda)
        executor.execute(() -> NewPipeService.init());

        webView = findViewById(R.id.webView);
        setupWebView();
        requestPermissions();
        handleIntent(getIntent());

        // MediaService başlat ve bağlan
        Intent svcIntent = new Intent(this, MediaPlayerService.class);
        startService(svcIntent);
        bindService(svcIntent, mediaConn, Context.BIND_AUTO_CREATE);

        // Kilit ekranı komut alıcısını kaydet
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(mediaControlReceiver, new IntentFilter("com.ytpro.MEDIA_CONTROL"), Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(mediaControlReceiver, new IntentFilter("com.ytpro.MEDIA_CONTROL"));
        }
    }

    private void setupYtDlp() {
        executor.execute(() -> {
            try {
                File ytdlp = new File(ytDlpPath);
                if (!ytdlp.exists()) {
                    byte[] buffer = new byte[8192]; int read;
                    try (InputStream in = getAssets().open("yt-dlp");
                         FileOutputStream out = new FileOutputStream(ytdlp)) {
                        while ((read = in.read(buffer)) != -1) out.write(buffer, 0, read);
                    }
                }
                ytdlp.setExecutable(true, false);
            } catch (Exception e) { e.printStackTrace(); }
        });
    }

    private void setupWebView() {
        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setAllowFileAccess(true);
        s.setAllowContentAccess(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setCacheMode(WebSettings.LOAD_DEFAULT);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        s.setLoadWithOverviewMode(true);
        s.setUseWideViewPort(true);
        s.setBuiltInZoomControls(false);
        webView.addJavascriptInterface(new YTPROBridge(this), "YTPROAndroid");
        webView.setWebChromeClient(new WebChromeClient());
        webView.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView v, String url) { return false; }
        });
        webView.loadUrl("file:///android_asset/index.html");
    }

    private String runYtDlp(String... args) throws Exception {
        String[] cmd = new String[args.length + 1];
        cmd[0] = ytDlpPath;
        System.arraycopy(args, 0, cmd, 1, args.length);
        Process process = Runtime.getRuntime().exec(cmd);
        BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
        StringBuilder out = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) out.append(line).append("\n");
        process.waitFor();
        return out.toString().trim();
    }

    private void requestPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ActivityCompat.requestPermissions(this, new String[]{
                Manifest.permission.READ_MEDIA_VIDEO,
                Manifest.permission.READ_MEDIA_AUDIO,
                Manifest.permission.POST_NOTIFICATIONS
            }, PERMISSION_REQUEST_CODE);
        } else {
            ActivityCompat.requestPermissions(this, new String[]{
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            }, PERMISSION_REQUEST_CODE);
        }
    }

    @Override
    protected void onNewIntent(Intent intent) { super.onNewIntent(intent); handleIntent(intent); }

    private void handleIntent(Intent intent) {
        if (intent == null) return;
        if (Intent.ACTION_SEND.equals(intent.getAction()) && "text/plain".equals(intent.getType())) {
            String text = intent.getStringExtra(Intent.EXTRA_TEXT);
            if (text != null && (text.contains("youtube.com") || text.contains("youtu.be"))) {
                webView.post(() -> webView.evaluateJavascript(
                    "handleSharedUrl('" + text.replace("'", "\\'") + "')", null));
            }
        }
    }

    // YENİ ONBACKPRESSED - XIAOMI GESTURE FIX
    @Override
    public void onBackPressed() {
        // WebView içindeki açık pencereleri kontrol et (HTML tarafındaki handleAndroidBack metodu)
        webView.evaluateJavascript("javascript:typeof handleAndroidBack === 'function' ? handleAndroidBack() : false", new ValueCallback<String>() {
            @Override
            public void onReceiveValue(String value) {
                // Eğer JS tarafı bir pencere kapatarak "true" gönderirse, uygulamayı kapatma
                if ("true".equals(value)) {
                    return; 
                } 
                // Eğer açık bir modal/pencere yoksa (false dönerse), web sayfası gerisine veya uygulamadan çıkışa git
                else if (webView.canGoBack()) {
                    webView.goBack();
                } else {
                    MainActivity.super.onBackPressed();
                }
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mediaServiceBound) { unbindService(mediaConn); mediaServiceBound = false; }
        try { unregisterReceiver(mediaControlReceiver); } catch (Exception ignored) {}
    }

    // ══════════════════════════════════════
    // JavaScript Köprüsü (HTML ile iletişim)
    // ══════════════════════════════════════
    public class YTPROBridge {
        private final Activity activity;
        YTPROBridge(Activity a) { this.activity = a; }

        @JavascriptInterface
        public void fetchTrendingVideos(String query, String cb) {
            executor.execute(() -> {
                try {
                    String q = (query==null||query.isEmpty()) ? "ytsearch20:türkçe müzik 2024" : "ytsearch20:"+query;
                    String raw = runYtDlp("--dump-json","--flat-playlist","--no-warnings","--socket-timeout","15",q);
                    JSONArray res = new JSONArray();
                    for (String line : raw.split("\n")) {
                        if (line.trim().isEmpty()) continue;
                        try {
                            JSONObject it = new JSONObject(line);
                            JSONObject v = new JSONObject();
                            v.put("id",      it.optString("id",""));
                            v.put("title",   it.optString("title","Başlıksız"));
                            v.put("channel", it.optString("uploader",it.optString("channel","Kanal")));
                            v.put("duration",formatDuration(it.optInt("duration",0)));
                            v.put("views",   formatViews(it.optLong("view_count",0)));
                            v.put("thumb",   it.optString("thumbnail",""));
                            v.put("url",     "https://youtube.com/watch?v="+it.optString("id",""));
                            res.put(v);
                        } catch (Exception ignored) {}
                    }
                    String json = res.toString();
                    runOnUiThread(() -> webView.evaluateJavascript(cb+"("+json+")", null));
                } catch (Exception e) {
                    runOnUiThread(() -> webView.evaluateJavascript(cb+"([])", null));
                }
            });
        }

        @JavascriptInterface
        public void fetchTrendingSongs(String query, String cb) {
            executor.execute(() -> {
                try {
                    String q = (query==null||query.isEmpty()) ? "ytsearch20:türkçe müzik klip 2024" : "ytsearch20:"+query+" müzik";
                    String raw = runYtDlp("--dump-json","--flat-playlist","--no-warnings","--socket-timeout","15",q);
                    JSONArray res = new JSONArray();
                    int idx = 1;
                    for (String line : raw.split("\n")) {
                        if (line.trim().isEmpty()) continue;
                        try {
                            JSONObject it = new JSONObject(line);
                            JSONObject s = new JSONObject();
                            s.put("id",       it.optString("id",""));
                            s.put("title",    it.optString("title","Başlıksız"));
                            s.put("artist",   it.optString("uploader",it.optString("channel","Sanatçı")));
                            s.put("album",    "YouTube");
                            s.put("duration", formatDuration(it.optInt("duration",0)));
                            s.put("thumb",    it.optString("thumbnail",""));
                            s.put("url",      "https://youtube.com/watch?v="+it.optString("id",""));
                            s.put("num",      idx++);
                            res.put(s);
                        } catch (Exception ignored) {}
                    }
                    String json = res.toString();
                    runOnUiThread(() -> webView.evaluateJavascript(cb+"("+json+")", null));
                } catch (Exception e) {
                    runOnUiThread(() -> webView.evaluateJavascript(cb+"([])", null));
                }
            });
        }

        @JavascriptInterface
        public void getPreviewUrl(String videoUrl, String cb) {
            executor.execute(() -> {
                try {
                    JSONObject info = NewPipeService.getStreamInfo(videoUrl);
                    if (info != null && (info.has("bestAudioUrl") || info.has("previewVideoUrl"))) {
                        String json = info.toString();
                        runOnUiThread(() -> webView.evaluateJavascript(cb + "(" + json + ")", null));
                        return;
                    }
                } catch (Exception e) {
                    android.util.Log.w("YTPROBridge", "NewPipe preview failed, fallback: " + e.getMessage());
                }
                runOnUiThread(() -> webView.evaluateJavascript(cb + "(null)", null));
            });
        }

        @JavascriptInterface
        public void searchWithNewPipe(String query, String cb) {
            executor.execute(() -> {
                try {
                    JSONArray results = NewPipeService.search(query);
                    if (results.length() > 0) {
                        String json = results.toString();
                        runOnUiThread(() -> webView.evaluateJavascript(cb + "(" + json + ")", null));
                        return;
                    }
                } catch (Exception e) {
                    android.util.Log.w("YTPROBridge", "NewPipe search failed: " + e.getMessage());
                }
                runOnUiThread(() -> webView.evaluateJavascript("onNewPipeSearchFailed('" + query.replace("'","\\'") + "','" + cb + "')", null));
            });
        }

        @JavascriptInterface
        public void getTrendingWithNewPipe(String cb) {
            executor.execute(() -> {
                try {
                    JSONArray results = NewPipeService.getTrending("TR");
                    if (results.length() > 0) {
                        String json = results.toString();
                        runOnUiThread(() -> webView.evaluateJavascript(cb + "(" + json + ")", null));
                        return;
                    }
                } catch (Exception e) {
                    android.util.Log.w("YTPROBridge", "NewPipe trending failed: " + e.getMessage());
                }
                runOnUiThread(() -> webView.evaluateJavascript("onNewPipeTrendingFailed('" + cb + "')", null));
            });
        }

        @JavascriptInterface
        public void searchVideos(String query, String cb) {
            executor.execute(() -> {
                try {
                    JSONArray r = NewPipeService.search(query);
                    if (r.length() > 0) {
                        String json = r.toString();
                        runOnUiThread(() -> webView.evaluateJavascript(cb + "(" + json + ")", null));
                        return;
                    }
                } catch (Exception ignored) {}
                fetchTrendingVideos(query, cb);
            });
        }

        @JavascriptInterface
        public void searchSongs(String query, String cb) {
            executor.execute(() -> {
                try {
                    JSONArray r = NewPipeService.search(query + " müzik");
                    if (r.length() > 0) {
                        String json = r.toString();
                        runOnUiThread(() -> webView.evaluateJavascript(cb + "(" + json + ")", null));
                        return;
                    }
                } catch (Exception ignored) {}
                fetchTrendingSongs(query, cb);
            });
        }

        @JavascriptInterface
        public void updateMediaSession(String title, String artist, String action) {
            if (mediaServiceBound && mediaService != null) {
                mediaService.updateSession(title, artist, action, "");
            }
        }

        // YENİ İNDİRME SİSTEMİ
       @JavascriptInterface
public void startDownload(final String url, final String quality, final String type, final String title, final String tid) {
    runOnUiThread(() -> webView.evaluateJavascript("showToast('⏳ Medya kaynağı çözümleniyor...');", null));

    new Thread(() -> {
        try {
            // 1. NewPipe ile arkaplanda saf linki (MP4/M4A) bul
            org.schabi.newpipe.extractor.stream.StreamInfo streamInfo = org.schabi.newpipe.extractor.stream.StreamInfo.getInfo(org.schabi.newpipe.extractor.ServiceList.YouTube, url);
            
            String downloadUrl = "";
            String ext = type.equals("audio") ? ".m4a" : ".mp4";

            if (type.equals("audio") || type.equals("mp3")) {
                if (!streamInfo.getAudioStreams().isEmpty()) {
                    downloadUrl = streamInfo.getAudioStreams().get(0).getContent();
                }
            } else {
                if (!streamInfo.getVideoStreams().isEmpty()) {
                    downloadUrl = streamInfo.getVideoStreams().get(0).getContent();
                } else if (!streamInfo.getVideoOnlyStreams().isEmpty()) {
                    downloadUrl = streamInfo.getVideoOnlyStreams().get(0).getContent();
                }
            }

            if (downloadUrl.isEmpty()) {
                runOnUiThread(() -> webView.evaluateJavascript("downloadError('" + tid + "', 'Kaynak bulunamadı');", null));
                return;
            }

            // 2. Kendi Özel İndirme Motorumuz (Bildirimsiz, Direkt Cihaza İndirir)
            java.net.URL urlObj = new java.net.URL(downloadUrl);
            java.net.HttpURLConnection conn = (java.net.HttpURLConnection) urlObj.openConnection();
            conn.connect();

            int fileLength = conn.getContentLength();
            java.io.InputStream input = new java.io.BufferedInputStream(urlObj.openStream());
            
            // Cihazın "İndirilenler" klasörüne hedef oluştur
            java.io.File dir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS);
            String safeTitle = title.replaceAll("[\\\\/:*?\"<>|]", "_");
            java.io.File file = new java.io.File(dir, safeTitle + ext);
            java.io.OutputStream output = new java.io.FileOutputStream(file);

            byte[] data = new byte[8192];
            long total = 0;
            int count;
            int lastPct = -1;
            long startTime = System.currentTimeMillis();

            // Saniyede bir HTML arayüzüne (Butona) % yüzde değerini gönder
            while ((count = input.read(data)) != -1) {
                total += count;
                int pct = (int) ((total * 100) / fileLength);
                
                if (pct > lastPct) {
                    lastPct = pct;
                    long elapsed = System.currentTimeMillis() - startTime;
                    double speed = elapsed > 0 ? ((double) total / 1024 / 1024) / (elapsed / 1000.0) : 0;
                    String speedStr = String.format(java.util.Locale.US, "%.1f MB/s", speed);
                    
                    runOnUiThread(() -> webView.evaluateJavascript("updateDownloadProgress('" + tid + "', " + pct + ", '" + speedStr + "');", null));
                }
                output.write(data, 0, count);
            }

            output.flush();
            output.close();
            input.close();

            // 3. İndirme bittiğinde HTML'e Yeşil Tik komutunu gönder
            runOnUiThread(() -> webView.evaluateJavascript("downloadComplete('" + tid + "', '" + file.getAbsolutePath() + "');", null));

        } catch (Exception e) {
            e.printStackTrace();
            runOnUiThread(() -> webView.evaluateJavascript("downloadError('" + tid + "', 'Bağlantı koptu');", null));
        }
    }).start();
}
