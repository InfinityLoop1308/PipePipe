package dev.pipepipe.desktop;

import com.sun.net.httpserver.HttpExchange;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.json.JSONObject;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Streams remote media through the local server so the browser never needs CORS or special
 * headers (Referer for BiliBili etc). Targets are HMAC-signed so the proxy cannot be abused as
 * an open relay when the server is exposed on a LAN.
 */
final class MediaProxy {
    /** YouTube throttles very long open-ended range requests; serve them in chunks instead. */
    private static final long CHUNK = 8L * 1024 * 1024;
    private static final Pattern URI_ATTR = Pattern.compile("URI=\"([^\"]+)\"");

    private final OkHttpClient client;
    private final byte[] key = new byte[32];

    MediaProxy(final OkHttpClient client) {
        this.client = client.newBuilder().followRedirects(true).build();
        new SecureRandom().nextBytes(key);
    }

    /** @return local path for proxied media */
    String media(final String url, final Map<String, String> headers) {
        return "/px/" + sign(url, headers);
    }

    /** @return local path for a proxied (rewritten) HLS playlist */
    String hls(final String url, final Map<String, String> headers) {
        return "/hx/" + sign(url, headers) + "/index.m3u8";
    }

    private String sign(final String url, final Map<String, String> headers) {
        final JSONObject o = new JSONObject().put("u", url);
        if (headers != null && !headers.isEmpty()) {
            o.put("h", new JSONObject(headers));
        }
        final String payload = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(o.toString().getBytes(StandardCharsets.UTF_8));
        return payload + "." + mac(payload);
    }

    private String mac(final String payload) {
        try {
            final Mac m = Mac.getInstance("HmacSHA256");
            m.init(new SecretKeySpec(key, "HmacSHA256"));
            final byte[] d = m.doFinal(payload.getBytes(StandardCharsets.US_ASCII));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(d).substring(0, 22);
        } catch (final Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private JSONObject verify(final String token) {
        final int dot = token.lastIndexOf('.');
        if (dot < 0) {
            throw new Http.HttpError(403, "bad token");
        }
        final String payload = token.substring(0, dot);
        if (!MessageDigest.isEqual(mac(payload).getBytes(StandardCharsets.US_ASCII),
                token.substring(dot + 1).getBytes(StandardCharsets.US_ASCII))) {
            throw new Http.HttpError(403, "bad signature");
        }
        return new JSONObject(new String(Base64.getUrlDecoder().decode(payload),
                StandardCharsets.UTF_8));
    }

    private Request.Builder request(final JSONObject target) {
        final Request.Builder b = new Request.Builder().url(target.getString("u"))
                .header("User-Agent", DesktopDownloader.USER_AGENT);
        final JSONObject h = target.optJSONObject("h");
        if (h != null) {
            for (final String k : h.keySet()) {
                b.header(k, h.getString(k));
            }
        }
        return b;
    }

    void handleMedia(final HttpExchange ex, final String token) throws IOException {
        final JSONObject target = verify(token);
        final Request.Builder rb = request(target);
        String range = ex.getRequestHeaders().getFirst("Range");
        final boolean youtube = target.getString("u").contains("googlevideo.com");
        if (youtube) {
            // open ended "bytes=N-" -> bounded chunk, the browser asks for the rest itself
            final Matcher m = Pattern.compile("bytes=(\\d+)-$").matcher(range == null ? "bytes=0-" : range);
            if (m.matches()) {
                final long start = Long.parseLong(m.group(1));
                range = "bytes=" + start + "-" + (start + CHUNK - 1);
            }
        }
        if (range != null) {
            rb.header("Range", range);
        }
        if ("HEAD".equals(ex.getRequestMethod())) {
            rb.head();
        }
        try (Response r = client.newCall(rb.build()).execute()) {
            final var out = ex.getResponseHeaders();
            for (final String h : new String[]{"Content-Type", "Content-Length", "Content-Range",
                    "Accept-Ranges", "Last-Modified", "ETag"}) {
                final String v = r.header(h);
                if (v != null) {
                    out.set(h, v);
                }
            }
            out.set("Accept-Ranges", "bytes");
            out.set("Cache-Control", "private, max-age=3600");
            out.set("Access-Control-Allow-Origin", "*");
            final String len = r.header("Content-Length");
            final long length = len == null ? 0 : Long.parseLong(len);
            if ("HEAD".equals(ex.getRequestMethod())) {
                ex.sendResponseHeaders(r.code(), -1);
                ex.close();
                return;
            }
            ex.sendResponseHeaders(r.code(), length > 0 ? length : 0);
            try (InputStream in = r.body().byteStream(); OutputStream os = ex.getResponseBody()) {
                in.transferTo(os);
            } catch (final IOException ignored) {
                // client went away (seek / quality switch) - normal
            }
        } finally {
            ex.close();
        }
    }

    void handleHls(final HttpExchange ex, final String token) throws IOException {
        final JSONObject target = verify(token);
        final String base = target.getString("u");
        final Map<String, String> headers = target.optJSONObject("h") == null ? null
                : target.getJSONObject("h").toMap().entrySet().stream().collect(
                java.util.stream.Collectors.toMap(Map.Entry::getKey, e -> String.valueOf(e.getValue())));
        final String text;
        try (Response r = client.newCall(request(target).build()).execute()) {
            if (!r.isSuccessful()) {
                throw new Http.HttpError(502, "upstream " + r.code());
            }
            text = r.body().string();
        }
        final URI baseUri = URI.create(base);
        final StringBuilder sb = new StringBuilder(text.length() * 2);
        for (final String line : text.split("\r?\n")) {
            if (line.isBlank()) {
                sb.append('\n');
            } else if (line.startsWith("#")) {
                final Matcher m = URI_ATTR.matcher(line);
                final StringBuilder rewritten = new StringBuilder();
                while (m.find()) {
                    m.appendReplacement(rewritten, Matcher.quoteReplacement(
                            "URI=\"" + rewrite(baseUri, m.group(1), headers) + "\""));
                }
                m.appendTail(rewritten);
                sb.append(rewritten).append('\n');
            } else {
                sb.append(rewrite(baseUri, line.trim(), headers)).append('\n');
            }
        }
        ex.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        Http.send(ex, 200, "application/vnd.apple.mpegurl", sb.toString().getBytes(StandardCharsets.UTF_8));
    }

    private String rewrite(final URI base, final String ref, final Map<String, String> headers) {
        final String abs = base.resolve(ref).toString();
        final String path = URI.create(abs).getPath();
        final boolean playlist = path.endsWith(".m3u8") || abs.contains("/hls_playlist/")
                || abs.contains("/hls_variant/") || abs.contains("playlist/index.m3u8");
        return playlist ? hls(abs, headers) : media(abs, headers);
    }
}
