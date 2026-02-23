package com.ytpro.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.Environment;
import android.os.IBinder;
import android.util.Log;
import androidx.core.app.NotificationCompat;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class DownloadService extends Service {

    private static final String TAG = "YTPRODownload";
    private static final String CHANNEL_ID = "ytpro_downloads";
    private static final int NOTIF_ID = 1001;

    private ExecutorService executor;
    private NotificationManager notifManager;
    private String ytdlpPath;

    @Override
    public void onCreate() {
        super.onCreate();
        executor = Executors.newFixedThreadPool(3); // 3 eşzamanlı indirme
        notifManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        createNotificationChannel();
        extractYtdlp();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) return START_NOT_STICKY;

        String url     = intent.getStringExtra("url");
        String quality = intent.getStringExtra("quality");
        String type    = intent.getStringExtra("type");
        String title   = intent.getStringExtra("title");
        String taskId  = UUID.randomUUID().toString().substring(0, 8);

        startForeground(NOTIF_ID, buildNotification("YT-PRO İndiriyor...", 0));

        executor.execute(() -> runDownload(url, quality, type, title, taskId));

        return START_NOT_STICKY;
    }

    // ── yt-dlp binary'yi assets'ten kopyala ──
    private void extractYtdlp() {
        File ytdlp = new File(getFilesDir(), "yt-dlp");
        ytdlpPath = ytdlp.getAbsolutePath();

        if (ytdlp.exists()) return; // zaten var

        try {
            InputStream is = getAssets().open("yt-dlp");
            OutputStream os = new FileOutputStream(ytdlp);
            byte[] buf = new byte[8192];
            int len;
            while ((len = is.read(buf)) > 0) os.write(buf, 0, len);
            is.close();
            os.close();
            ytdlp.setExecutable(true); // çalıştırılabilir yap
            Log.d(TAG, "yt-dlp extracted: " + ytdlpPath);
        } catch (Exception e) {
            Log.e(TAG, "yt-dlp extract error: " + e.getMessage());
        }
    }

    // ── İndirme işlemi ──
    private void runDownload(String url, String quality, String type, String title, String taskId) {
        File root = new File(Environment.getExternalStorageDirectory(), "YT-PRO");
        File dlDir = new File(root, type.equals("audio") ? "MUSIC" : "VIDEOS");
        dlDir.mkdirs();

        String formatArg;
        String outputTemplate = dlDir.getAbsolutePath() + "/%(title)s.%(ext)s";

        if ("audio".equals(type)) {
            // Ses indirme: en yüksek kalite MP3/M4A
            formatArg = "bestaudio/best";
        } else {
            // Video indirme: kalite seçimine göre
            switch (quality) {
                case "2160p": formatArg = "bestvideo[height<=2160]+bestaudio/best[height<=2160]"; break;
                case "1440p": formatArg = "bestvideo[height<=1440]+bestaudio/best[height<=1440]"; break;
                case "1080p": formatArg = "bestvideo[height<=1080]+bestaudio/best[height<=1080]"; break;
                case "720p":  formatArg = "bestvideo[height<=720]+bestaudio/best[height<=720]";  break;
                case "480p":  formatArg = "bestvideo[height<=480]+bestaudio/best[height<=480]";  break;
                default:      formatArg = "bestvideo+bestaudio/best"; break;
            }
        }

        try {
            ProcessBuilder pb;
            if ("audio".equals(type)) {
                pb = new ProcessBuilder(
                    ytdlpPath,
                    "--extract-audio",
                    "--audio-format", "mp3",
                    "--audio-quality", "0",          // en iyi kalite
                    "--output", outputTemplate,
                    "--no-playlist",
                    "--progress",
                    "--newline",
                    "--ffmpeg-location", getFfmpegPath(),
                    url
                );
            } else {
                pb = new ProcessBuilder(
                    ytdlpPath,
                    "--format", formatArg,
                    "--merge-output-format", "mp4",
                    "--output", outputTemplate,
                    "--no-playlist",
                    "--progress",
                    "--newline",
                    "--ffmpeg-location", getFfmpegPath(),
                    url
                );
            }

            pb.redirectErrorStream(true);
            pb.environment().put("HOME", getFilesDir().getAbsolutePath());

            Process process = pb.start();
            BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream())
            );

            String line;
            String lastFile = "";
            float lastPct = 0;

            while ((line = reader.readLine()) != null) {
                Log.d(TAG, line);

                // İlerleme yüzdesini parse et
                // yt-dlp çıktısı: "[download]  45.3% of 128.00MiB at 4.50MiB/s ETA 00:18"
                if (line.contains("[download]") && line.contains("%")) {
                    try {
                        String cleaned = line.replaceAll("\\s+", " ").trim();
                        String[] parts = cleaned.split(" ");
                        for (int i = 0; i < parts.length; i++) {
                            if (parts[i].endsWith("%")) {
                                float pct = Float.parseFloat(parts[i].replace("%", ""));
                                String speed = "";
                                // "at X.XXMiB/s" bul
                                for (int j = 0; j < parts.length; j++) {
                                    if (parts[j].equals("at") && j + 1 < parts.length) {
                                        speed = parts[j + 1];
                                    }
                                }
                                if (Math.abs(pct - lastPct) >= 2) {
                                    lastPct = pct;
                                    updateNotification(title, (int) pct);
                                    // WebView'e bildir (broadcast)
                                    Intent bi = new Intent("com.ytpro.PROGRESS");
                                    bi.putExtra("taskId", taskId);
                                    bi.putExtra("progress", (int) pct);
                                    bi.putExtra("speed", speed);
                                    sendBroadcast(bi);
                                }
                                break;
                            }
                        }
                    } catch (Exception ignored) {}
                }

                // Tamamlanan dosya yolunu bul
                if (line.contains("[download] Destination:") || line.contains("[Merger]")) {
                    lastFile = line.contains(":") ? line.substring(line.lastIndexOf(":") + 2).trim() : "";
                }
            }

            int exitCode = process.waitFor();

            if (exitCode == 0) {
                Log.d(TAG, "Download complete: " + lastFile);
                Intent bi = new Intent("com.ytpro.COMPLETE");
                bi.putExtra("taskId", taskId);
                bi.putExtra("filePath", lastFile);
                sendBroadcast(bi);
                updateNotification("✔ Tamamlandı: " + title, 100);
            } else {
                Log.e(TAG, "Download failed with code: " + exitCode);
                Intent bi = new Intent("com.ytpro.ERROR");
                bi.putExtra("taskId", taskId);
                bi.putExtra("error", "İndirme başarısız (kod: " + exitCode + ")");
                sendBroadcast(bi);
            }

        } catch (Exception e) {
            Log.e(TAG, "Download error: " + e.getMessage());
            Intent bi = new Intent("com.ytpro.ERROR");
            bi.putExtra("taskId", taskId);
            bi.putExtra("error", e.getMessage());
            sendBroadcast(bi);
        }
    }

    private String getFfmpegPath() {
        File ffmpeg = new File(getFilesDir(), "ffmpeg");
        return ffmpeg.getAbsolutePath();
    }

    // ── Bildirim ──
    private void createNotificationChannel() {
        NotificationChannel channel = new NotificationChannel(
            CHANNEL_ID, "YT-PRO İndirmeler",
            NotificationManager.IMPORTANCE_LOW
        );
        channel.setDescription("İndirme bildirimleri");
        notifManager.createNotificationChannel(channel);
    }

    private Notification buildNotification(String text, int progress) {
        Intent notifIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
            this, 0, notifIntent,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("YT-PRO")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentIntent(pendingIntent)
            .setOngoing(progress < 100);

        if (progress > 0 && progress < 100) {
            builder.setProgress(100, progress, false);
        }

        return builder.build();
    }

    private void updateNotification(String text, int progress) {
        notifManager.notify(NOTIF_ID, buildNotification(text, progress));
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (executor != null) executor.shutdown();
    }
}
