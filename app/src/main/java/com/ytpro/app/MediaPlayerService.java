package com.ytpro.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.media.app.NotificationCompat.MediaStyle;
import androidx.media.session.MediaButtonReceiver;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MediaPlayerService extends Service {

    public static final String ACTION_PLAY  = "com.ytpro.PLAY";
    public static final String ACTION_PAUSE = "com.ytpro.PAUSE";
    public static final String ACTION_NEXT  = "com.ytpro.NEXT";
    public static final String ACTION_PREV  = "com.ytpro.PREV";
    public static final String ACTION_STOP  = "com.ytpro.STOP";

    private static final String CHANNEL_ID = "ytpro_media";
    private static final int    NOTIF_ID   = 42;

    private MediaSessionCompat mediaSession;
    private AudioManager audioManager;
    private AudioFocusRequest audioFocusRequest;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    private String  currentTitle  = "YT-PRO";
    private String  currentArtist = "";
    private boolean isPlaying     = false;
    private Bitmap  currentArt    = null;

    private final IBinder binder = new LocalBinder();
    public class LocalBinder extends Binder {
        MediaPlayerService getService() { return MediaPlayerService.this; }
    }

    @Nullable @Override
    public IBinder onBind(Intent intent) { return binder; }

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        setupMediaSession();
        setupAudioFocus();
        IntentFilter filter = new IntentFilter();
        filter.addAction(ACTION_PLAY);
        filter.addAction(ACTION_PAUSE);
        filter.addAction(ACTION_NEXT);
        filter.addAction(ACTION_PREV);
        filter.addAction(ACTION_STOP);
        registerReceiver(mediaActionReceiver, filter);
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel ch = new NotificationChannel(
                CHANNEL_ID, "YT-PRO Oynatıcı",
                NotificationManager.IMPORTANCE_LOW);
            ch.setSound(null, null);
            ((NotificationManager) getSystemService(NOTIFICATION_SERVICE))
                .createNotificationChannel(ch);
        }
    }

    private void setupMediaSession() {
        mediaSession = new MediaSessionCompat(this, "YTPROSession");
        mediaSession.setFlags(
            MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS |
            MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS);
        mediaSession.setCallback(new MediaSessionCompat.Callback() {
            @Override public void onPlay()              { broadcastAction(ACTION_PLAY); }
            @Override public void onPause()             { broadcastAction(ACTION_PAUSE); }
            @Override public void onSkipToNext()        { broadcastAction(ACTION_NEXT); }
            @Override public void onSkipToPrevious()    { broadcastAction(ACTION_PREV); }
            @Override public void onStop()              { broadcastAction(ACTION_STOP); }
        });
        mediaSession.setActive(true);
    }

    private void setupAudioFocus() {
        audioManager = (AudioManager) getSystemService(AUDIO_SERVICE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioFocusRequest = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build())
                .setOnAudioFocusChangeListener(change -> {
                    if (change == AudioManager.AUDIOFOCUS_LOSS) broadcastAction(ACTION_PAUSE);
                })
                .build();
            audioManager.requestAudioFocus(audioFocusRequest);
        }
    }

    public void updateSession(String title, String artist, String action, String thumbUrl) {
        currentTitle  = title;
        currentArtist = artist;
        isPlaying     = "play".equals(action);

        MediaMetadataCompat.Builder meta = new MediaMetadataCompat.Builder()
            .putString(MediaMetadataCompat.METADATA_KEY_TITLE,  title)
            .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, artist);
        if (currentArt != null)
            meta.putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, currentArt);
        mediaSession.setMetadata(meta.build());

        PlaybackStateCompat state = new PlaybackStateCompat.Builder()
            .setActions(
                PlaybackStateCompat.ACTION_PLAY |
                PlaybackStateCompat.ACTION_PAUSE |
                PlaybackStateCompat.ACTION_SKIP_TO_NEXT |
                PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS)
            .setState(
                isPlaying ? PlaybackStateCompat.STATE_PLAYING : PlaybackStateCompat.STATE_PAUSED,
                PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN, 1f)
            .build();
        mediaSession.setPlaybackState(state);

        if (thumbUrl != null && !thumbUrl.isEmpty()) {
            executor.execute(() -> {
                try {
                    HttpURLConnection conn = (HttpURLConnection) new URL(thumbUrl).openConnection();
                    conn.setConnectTimeout(5000); conn.setReadTimeout(5000);
                    InputStream is = conn.getInputStream();
                    currentArt = BitmapFactory.decodeStream(is);
                    is.close();
                    updateSession(title, artist, action, "");
                } catch (Exception ignored) {}
            });
        }
        showNotification();
    }

    private void showNotification() {
        Intent actIntent = new Intent(this, MainActivity.class);
        actIntent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent contentPi = PendingIntent.getActivity(this, 0, actIntent,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        PendingIntent prevPi  = buildPi(ACTION_PREV,  3);
        PendingIntent playPi  = buildPi(isPlaying ? ACTION_PAUSE : ACTION_PLAY, 1);
        PendingIntent nextPi  = buildPi(ACTION_NEXT,  2);

        NotificationCompat.Builder nb = new NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_play_arrow)
            .setContentTitle(currentTitle)
            .setContentText(currentArtist)
            .setContentIntent(contentPi)
            .setOngoing(isPlaying)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(R.drawable.ic_play_arrow, "Önceki", prevPi)
            .addAction(
                isPlaying ? android.R.drawable.ic_media_pause : android.R.drawable.ic_media_play,
                isPlaying ? "Duraklat" : "Oynat", playPi)
            .addAction(R.drawable.ic_play_arrow, "Sonraki", nextPi)
            .setStyle(new MediaStyle()
                .setMediaSession(mediaSession.getSessionToken())
                .setShowActionsInCompactView(0, 1, 2));

        if (currentArt != null) nb.setLargeIcon(currentArt);
        startForeground(NOTIF_ID, nb.build());
    }

    private PendingIntent buildPi(String action, int req) {
        Intent i = new Intent(action).setPackage(getPackageName());
        return PendingIntent.getBroadcast(this, req, i,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private void broadcastAction(String action) {
        sendBroadcast(new Intent("com.ytpro.MEDIA_CONTROL")
            .putExtra("action", action));
    }

    private final BroadcastReceiver mediaActionReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context ctx, Intent intent) {
            String a = intent.getAction();
            if (a != null) broadcastAction(a);
        }
    };

    @Override
    public void onDestroy() {
        super.onDestroy();
        try { unregisterReceiver(mediaActionReceiver); } catch (Exception ignored) {}
        if (mediaSession != null) { mediaSession.setActive(false); mediaSession.release(); }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && audioFocusRequest != null)
            audioManager.abandonAudioFocusRequest(audioFocusRequest);
    }
}
