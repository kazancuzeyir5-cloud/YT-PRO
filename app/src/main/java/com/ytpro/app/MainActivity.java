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
        registerReceiver(mediaControlReceiver,
            new IntentFilter("com.ytpro.MEDIA_CONTROL"));
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

    @Override
    public void onBackPressed() {
        if (webView.canGoBack()) webView.goBack(); else super.onBackPressed();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mediaServiceBound) { unbindService(mediaConn); mediaServiceBound = false; }
        try { unregisterReceiver(mediaControlReceiver); } catch (Exception ignored) {}
    }

    // ══════════════════════════════════════
    // JavaScript Köprüsü
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

        // ── NewPipe: Önizleme stream URL'si al ───────────
        @JavascriptInterface
        public void getPreviewUrl(String videoUrl, String cb) {
            executor.execute(() -> {
                try {
                    // Önce NewPipe dene (daha hızlı)
                    org.json.JSONObject info = NewPipeService.getStreamInfo(videoUrl);
                    if (info != null && (info.has("bestAudioUrl") || info.has("previewVideoUrl"))) {
                        String json = info.toString();
                        runOnUiThread(() -> webView.evaluateJavascript(cb + "(" + json + ")", null));
                        return;
                    }
                } catch (Exception e) {
                    android.util.Log.w("YTPROBridge", "NewPipe preview failed, fallback: " + e.getMessage());
                }
                // NewPipe başarısız → null döndür, HTML5 audio denesin
                runOnUiThread(() -> webView.evaluateJavascript(cb + "(null)", null));
            });
        }

        // ── NewPipe: Arama ───────────────────────────────
        @JavascriptInterface
        public void searchWithNewPipe(String query, String cb) {
            executor.execute(() -> {
                try {
                    org.json.JSONArray results = NewPipeService.search(query);
                    if (results.length() > 0) {
                        String json = results.toString();
                        runOnUiThread(() -> webView.evaluateJavascript(cb + "(" + json + ")", null));
                        return;
                    }
                } catch (Exception e) {
                    android.util.Log.w("YTPROBridge", "NewPipe search failed: " + e.getMessage());
                }
                // Fallback: yt-dlp ile ara
                runOnUiThread(() -> webView.evaluateJavascript(
                    "onNewPipeSearchFailed('" + query.replace("'","\'") + "','" + cb + "')", null));
            });
        }

        // ── NewPipe: Trend videolar ──────────────────────
        @JavascriptInterface
        public void getTrendingWithNewPipe(String cb) {
            executor.execute(() -> {
                try {
                    org.json.JSONArray results = NewPipeService.getTrending("TR");
                    if (results.length() > 0) {
                        String json = results.toString();
                        runOnUiThread(() -> webView.evaluateJavascript(cb + "(" + json + ")", null));
                        return;
                    }
                } catch (Exception e) {
                    android.util.Log.w("YTPROBridge", "NewPipe trending failed: " + e.getMessage());
                }
                // Fallback: yt-dlp
                runOnUiThread(() -> webView.evaluateJavascript(
                    "onNewPipeTrendingFailed('" + cb + "')", null));
            });
        }

        // ── searchVideos / searchSongs (HTML'den çağrılan) ──
        @JavascriptInterface
        public void searchVideos(String query, String cb) {
            // NewPipe önce, sonra yt-dlp fallback
            executor.execute(() -> {
                try {
                    org.json.JSONArray r = NewPipeService.search(query);
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
                    org.json.JSONArray r = NewPipeService.search(query + " müzik");
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
        public void fetchVideoInfo(String url, String cb) {
            executor.execute(() -> {
                try {
                    String raw = runYtDlp("--dump-json","--no-warnings","--socket-timeout","15",url);
                    runOnUiThread(() -> webView.evaluateJavascript(cb+"("+raw+")", null));
                } catch (Exception e) {
                    runOnUiThread(() -> webView.evaluateJavascript(cb+"(null)", null));
                }
            });
        }

        // KİLİT EKRANI + ARKA PLAN OYNATMA
        @JavascriptInterface
        public void updateMediaSession(String title, String artist, String action) {
            if (mediaServiceBound && mediaService != null) {
                // Thumbnail URL'yi playerArt'tan al
                String thumbUrl = "";
                mediaService.updateSession(title, artist, action, thumbUrl);
            }
        }

        @JavascriptInterface
        public void updateMediaSessionWithThumb(String title, String artist, String action, String thumbUrl) {
            if (mediaServiceBound && mediaService != null) {
                mediaService.updateSession(title, artist, action, thumbUrl);
            }
        }

        @JavascriptInterface
        public void startDownload(String url, String quality, String type, String title) {
            Intent intent = new Intent(activity, DownloadService.class);
            intent.putExtra("url",    url);
            intent.putExtra("quality",quality);
            intent.putExtra("type",   type);
            intent.putExtra("title",  title);
            intent.putExtra("ytdlp",  ytDlpPath);
            activity.startForegroundService(intent);
        }

        @JavascriptInterface
        public String getDownloadPath() {
            return Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                .getAbsolutePath() + "/YT-PRO";
        }

        @JavascriptInterface
        public boolean isDarkMode() {
            int m = activity.getResources().getConfiguration().uiMode
                & android.content.res.Configuration.UI_MODE_NIGHT_MASK;
            return m == android.content.res.Configuration.UI_MODE_NIGHT_YES;
        }

        @JavascriptInterface
        public void showNativeToast(String msg) {
            activity.runOnUiThread(() -> Toast.makeText(activity, msg, Toast.LENGTH_SHORT).show());
        }

        @JavascriptInterface
        public void openFile(String path) {
            Intent i = new Intent(Intent.ACTION_VIEW);
            i.setDataAndType(Uri.parse("file://" + path), "*/*");
            i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            activity.startActivity(Intent.createChooser(i, "Aç"));
        }

        @JavascriptInterface
        public String getDeviceInfo() {
            try {
                JSONObject info = new JSONObject();
                info.put("model",   Build.MODEL);
                info.put("android", Build.VERSION.RELEASE);
                info.put("sdk",     Build.VERSION.SDK_INT);
                return info.toString();
            } catch (Exception e) { return "{}"; }
        }

        public void notifyProgress(String tid, int pct, String speed) {
            activity.runOnUiThread(() -> webView.evaluateJavascript(
                String.format("updateDownloadProgress('%s',%d,'%s')",tid,pct,speed), null));
        }
        public void notifyComplete(String tid, String path) {
            activity.runOnUiThread(() -> webView.evaluateJavascript(
                String.format("downloadComplete('%s','%s')",tid,path.replace("'","\\'")), null));
        }
        public void notifyError(String tid, String err) {
            activity.runOnUiThread(() -> webView.evaluateJavascript(
                String.format("downloadError('%s','%s')",tid,err.replace("'","\\'")), null));
        }
    }

    private String formatDuration(int s) {
        if (s <= 0) return "0:00";
        return (s/60) + ":" + (s%60 < 10 ? "0" : "") + (s%60);
    }
    private String formatViews(long v) {
        if (v >= 1_000_000_000) return String.format("%.1fB", v/1_000_000_000.0);
        if (v >= 1_000_000)     return String.format("%.1fM", v/1_000_000.0);
        if (v >= 1_000)         return String.format("%.1fK", v/1_000.0);
        return String.valueOf(v);
    }
}
