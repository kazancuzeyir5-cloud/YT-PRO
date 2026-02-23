package com.ytpro.app;

import android.util.Log;

import org.schabi.newpipe.extractor.InfoItem;
import org.schabi.newpipe.extractor.ListExtractor;
import org.schabi.newpipe.extractor.NewPipe;
import org.schabi.newpipe.extractor.ServiceList;
import org.schabi.newpipe.extractor.StreamingService;
import org.schabi.newpipe.extractor.kiosk.KioskExtractor;
import org.schabi.newpipe.extractor.kiosk.KioskList;
import org.schabi.newpipe.extractor.localization.Localization;
import org.schabi.newpipe.extractor.search.SearchExtractor;
import org.schabi.newpipe.extractor.stream.AudioStream;
import org.schabi.newpipe.extractor.stream.StreamInfo;
import org.schabi.newpipe.extractor.stream.StreamInfoItem;
import org.schabi.newpipe.extractor.stream.VideoStream;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Collections;
import java.util.List;
import java.util.Locale;

public class NewPipeService {

    private static final String TAG = "NewPipeService";
    private static volatile boolean initialized = false;

    public static synchronized void init() {
        if (initialized) return;
        try {
            NewPipe.init(NewPipeDownloader.getInstance());
            initialized = true;
            Log.d(TAG, "NewPipe initialized OK");
        } catch (Exception e) {
            Log.e(TAG, "init error: " + e.getMessage());
        }
    }

    public static JSONArray search(String query) {
        JSONArray results = new JSONArray();
        try {
            init();
            StreamingService yt = NewPipe.getService(ServiceList.YouTube.getServiceId());
            SearchExtractor extractor = yt.getSearchExtractor(
                yt.getSearchQHFactory().fromQuery(query, Collections.singletonList("videos"), "")
            );
            extractor.fetchPage();
            // SearchExtractor raw type kullan - generic sorundan kaçın
            ListExtractor.InfoItemsPage page = extractor.getInitialPage();
            for (Object obj : page.getItems()) {
                if (obj instanceof StreamInfoItem) {
                    results.put(toJson((StreamInfoItem) obj));
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "search error: " + e.getMessage());
        }
        return results;
    }

    public static JSONArray getTrending(String countryCode) {
        JSONArray results = new JSONArray();
        try {
            init();
            StreamingService yt = NewPipe.getService(ServiceList.YouTube.getServiceId());
            KioskList kioskList = yt.getKioskList();
            // Raw type kullan - generic type uyumsuzluğundan kaçın
            KioskExtractor kiosk = kioskList.getExtractorById(
                kioskList.getDefaultKioskId(), null);
            kiosk.forceLocalization(new Localization("tr", countryCode));
            kiosk.fetchPage();
            // Raw type page - Object olarak alıp cast et
            ListExtractor.InfoItemsPage page = kiosk.getInitialPage();
            for (Object obj : page.getItems()) {
                if (obj instanceof StreamInfoItem) {
                    results.put(toJson((StreamInfoItem) obj));
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "trending error: " + e.getMessage());
        }
        return results;
    }

    public static JSONObject getStreamInfo(String videoUrl) {
        try {
            init();
            StreamInfo info = StreamInfo.getInfo(
                NewPipe.getService(ServiceList.YouTube.getServiceId()), videoUrl);

            JSONObject result = new JSONObject();
            result.put("title",   info.getName());
            result.put("channel", info.getUploaderName());
            result.put("duration", fmt((int) info.getDuration()));
            result.put("thumb", info.getThumbnails().isEmpty() ? ""
                : info.getThumbnails().get(0).getUrl());

            // En iyi audio stream
            List<AudioStream> audioStreams = info.getAudioStreams();
            if (!audioStreams.isEmpty()) {
                AudioStream best = audioStreams.get(0);
                for (AudioStream a : audioStreams) {
                    if (a.getAverageBitrate() > best.getAverageBitrate()) best = a;
                }
                result.put("bestAudioUrl", best.getContent());
            }

            // Video stream - getVideoStreams() kullan (ses ayrı olabilir, ama URL var)
            List<VideoStream> videoStreams = info.getVideoStreams();
            if (!videoStreams.isEmpty()) {
                // İlk 720p veya daha düşük bul
                for (VideoStream vs : videoStreams) {
                    String res = vs.getResolution();
                    if (res.contains("720") || res.contains("480") || res.contains("360")) {
                        result.put("previewVideoUrl", vs.getContent());
                        break;
                    }
                }
                // Bulamazsa ilkini al
                if (!result.has("previewVideoUrl")) {
                    result.put("previewVideoUrl", videoStreams.get(0).getContent());
                }
            }

            return result;
        } catch (Exception e) {
            Log.e(TAG, "getStreamInfo error: " + e.getMessage());
            return null;
        }
    }

    private static JSONObject toJson(StreamInfoItem s) throws Exception {
        JSONObject v = new JSONObject();
        String url = s.getUrl();
        String id  = extractId(url);
        v.put("id",       id);
        v.put("title",    s.getName());
        v.put("channel",  s.getUploaderName());
        v.put("duration", fmt((int) s.getDuration()));
        v.put("views",    fmtViews(s.getViewCount()));
        v.put("thumb",    s.getThumbnails().isEmpty() ? "" : s.getThumbnails().get(0).getUrl());
        v.put("url",      url);
        return v;
    }

    private static String extractId(String url) {
        if (url == null) return "";
        int v = url.indexOf("v=");
        if (v != -1) {
            String id = url.substring(v + 2);
            int amp = id.indexOf('&');
            return amp != -1 ? id.substring(0, amp) : id;
        }
        int slash = url.lastIndexOf('/');
        return slash != -1 ? url.substring(slash + 1) : url;
    }

    private static String fmt(int s) {
        if (s <= 0) return "0:00";
        return (s / 60) + ":" + String.format("%02d", s % 60);
    }

    private static String fmtViews(long v) {
        if (v < 0)              return "";
        if (v >= 1_000_000_000) return String.format(Locale.US, "%.1fB", v / 1e9);
        if (v >= 1_000_000)     return String.format(Locale.US, "%.1fM", v / 1e6);
        if (v >= 1_000)         return String.format(Locale.US, "%.1fK", v / 1e3);
        return String.valueOf(v);
    }
}
