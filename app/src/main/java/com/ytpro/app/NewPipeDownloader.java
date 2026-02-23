package com.ytpro.app;

import org.schabi.newpipe.extractor.downloader.Downloader;
import org.schabi.newpipe.extractor.downloader.Request;
import org.schabi.newpipe.extractor.downloader.Response;
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class NewPipeDownloader extends Downloader {

    private static NewPipeDownloader instance;
    private static final String USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; rv:115.0) Gecko/20100101 Firefox/115.0";

    private NewPipeDownloader() {}

    public static synchronized NewPipeDownloader getInstance() {
        if (instance == null) instance = new NewPipeDownloader();
        return instance;
    }

    @Override
    public Response execute(Request request) throws IOException, ReCaptchaException {
        final String httpMethod = request.httpMethod();
        final String url        = request.url();
        final Map<String, List<String>> headers = request.headers();
        final byte[] dataToSend = request.dataToSend();

        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setConnectTimeout(15000);
        conn.setReadTimeout(15000);
        conn.setRequestMethod(httpMethod);
        conn.setRequestProperty("User-Agent", USER_AGENT);

        if (headers != null) {
            for (Map.Entry<String, List<String>> e : headers.entrySet()) {
                for (String v : e.getValue()) {
                    conn.setRequestProperty(e.getKey(), v);
                }
            }
        }

        if (dataToSend != null && dataToSend.length > 0) {
            conn.setDoOutput(true);
            conn.getOutputStream().write(dataToSend);
        }

        final int code = conn.getResponseCode();
        if (code == 429) throw new ReCaptchaException("Rate limited", url);

        InputStream is = (code >= 200 && code < 300)
            ? conn.getInputStream() : conn.getErrorStream();

        String body = "";
        if (is != null) {
            body = new String(readBytes(is), "UTF-8");
            is.close();
        }

        Map<String, List<String>> responseHeaders = new HashMap<>(conn.getHeaderFields());
        conn.disconnect();
        return new Response(code, conn.getResponseMessage(), responseHeaders, body, url);
    }

    private byte[] readBytes(InputStream is) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] chunk = new byte[8192];
        int n;
        while ((n = is.read(chunk)) != -1) {
            buffer.write(chunk, 0, n);
        }
        return buffer.toByteArray();
    }
}
