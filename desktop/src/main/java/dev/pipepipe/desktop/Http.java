package dev.pipepipe.desktop;

import com.sun.net.httpserver.HttpExchange;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/** Small helpers around the JDK HttpServer exchange. */
final class Http {
    private Http() { }

    static final class HttpError extends RuntimeException {
        final int status;

        HttpError(final int status, final String message) {
            super(message);
            this.status = status;
        }
    }

    static Map<String, String> query(final HttpExchange ex) {
        final Map<String, String> out = new HashMap<>();
        final String raw = ex.getRequestURI().getRawQuery();
        if (raw == null || raw.isEmpty()) {
            return out;
        }
        for (final String part : raw.split("&")) {
            final int i = part.indexOf('=');
            final String k = i < 0 ? part : part.substring(0, i);
            final String v = i < 0 ? "" : part.substring(i + 1);
            out.put(URLDecoder.decode(k, StandardCharsets.UTF_8),
                    URLDecoder.decode(v, StandardCharsets.UTF_8));
        }
        return out;
    }

    static String require(final Map<String, String> q, final String key) {
        final String v = q.get(key);
        if (v == null || v.isBlank()) {
            throw new HttpError(400, "missing parameter: " + key);
        }
        return v;
    }

    static byte[] body(final HttpExchange ex) throws IOException {
        try (InputStream in = ex.getRequestBody()) {
            return in.readAllBytes();
        }
    }

    static void json(final HttpExchange ex, final int status, final Object payload) throws IOException {
        final String text = payload instanceof JSONObject o ? o.toString()
                : payload instanceof JSONArray a ? a.toString() : String.valueOf(payload);
        send(ex, status, "application/json; charset=utf-8", text.getBytes(StandardCharsets.UTF_8));
    }

    static void send(final HttpExchange ex, final int status, final String type, final byte[] data)
            throws IOException {
        ex.getResponseHeaders().set("Content-Type", type);
        if (!ex.getResponseHeaders().containsKey("Cache-Control")) {
            ex.getResponseHeaders().set("Cache-Control", "no-store");
        }
        ex.sendResponseHeaders(status, data.length == 0 ? -1 : data.length);
        if (data.length > 0) {
            try (OutputStream os = ex.getResponseBody()) {
                os.write(data);
            }
        }
        ex.close();
    }
}
