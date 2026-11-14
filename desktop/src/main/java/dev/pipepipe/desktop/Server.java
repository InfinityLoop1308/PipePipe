package dev.pipepipe.desktop;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.json.JSONArray;
import org.json.JSONObject;
import org.schabi.newpipe.extractor.NewPipe;
import org.schabi.newpipe.extractor.exceptions.ContentNotAvailableException;
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException;
import org.schabi.newpipe.extractor.localization.ContentCountry;
import org.schabi.newpipe.extractor.localization.Localization;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.Executors;

/** HTTP server: JSON API, media proxy and the static web UI. */
final class Server {
    private static final Map<String, String> TYPES = Map.of(
            "html", "text/html; charset=utf-8", "js", "text/javascript; charset=utf-8",
            "css", "text/css; charset=utf-8", "svg", "image/svg+xml", "png", "image/png",
            "ico", "image/x-icon", "json", "application/json", "woff2", "font/woff2");

    private final HttpServer http;
    private final Api api;
    private final MediaProxy proxy;
    private final Store store;
    private final Path webDir;

    Server(final String host, final int port, final DesktopDownloader downloader, final Store store,
           final Path webDir) throws IOException {
        this.proxy = new MediaProxy(downloader.client());
        this.api = new Api(proxy);
        this.store = store;
        this.webDir = webDir;
        this.http = HttpServer.create(new InetSocketAddress(host, port), 256);
        http.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
        http.createContext("/", this::handle);
        applySettings();
    }

    int port() {
        return http.getAddress().getPort();
    }

    void start() {
        http.start();
    }

    void stop() {
        http.stop(0);
    }

    private void applySettings() {
        try {
            final String raw = store.read("settings");
            if (raw == null || "null".equals(raw.trim())) {
                return;
            }
            final JSONObject s = new JSONObject(raw);
            final String lang = s.optString("language", "");
            final String country = s.optString("country", "");
            if (!lang.isEmpty() || !country.isEmpty()) {
                final String l = lang.isEmpty() ? "en" : lang;
                final String c = country.isEmpty() ? "US" : country;
                NewPipe.setupLocalization(new Localization(l, c), new ContentCountry(c));
            }
        } catch (final Exception e) {
            System.err.println("settings: " + e);
        }
    }

    private void handle(final HttpExchange ex) {
        final String path = ex.getRequestURI().getPath();
        try {
            if (path.startsWith("/px/")) {
                proxy.handleMedia(ex, path.substring(4));
            } else if (path.startsWith("/hx/")) {
                final String rest = path.substring(4);
                proxy.handleHls(ex, rest.contains("/") ? rest.substring(0, rest.indexOf('/')) : rest);
            } else if (path.startsWith("/api/")) {
                api(ex, path.substring(5));
            } else {
                serveStatic(ex, path);
            }
        } catch (final Http.HttpError e) {
            error(ex, e.status, e.getMessage());
        } catch (final ReCaptchaException e) {
            error(ex, 429, "The service is rate limiting this IP (captcha). Try again later.");
        } catch (final ContentNotAvailableException e) {
            error(ex, 404, e.getMessage() == null ? "Content not available" : e.getMessage());
        } catch (final Throwable e) {
            e.printStackTrace();
            error(ex, 500, e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    private static void error(final HttpExchange ex, final int status, final String message) {
        try {
            Http.json(ex, status, new JSONObject().put("error", message == null ? "error" : message));
        } catch (final IOException ignored) {
            ex.close();
        }
    }

    private void api(final HttpExchange ex, final String route) throws Exception {
        final Map<String, String> q = Http.query(ex);
        switch (route) {
            case "ping" -> Http.json(ex, 200, new JSONObject().put("app", "pipepipe-desktop"));
            case "services" -> Http.json(ex, 200, api.services());
            case "kiosk" -> Http.json(ex, 200, api.kiosk(q));
            case "search" -> Http.json(ex, 200, api.search(q));
            case "suggest" -> Http.json(ex, 200, api.suggest(q));
            case "more" -> Http.json(ex, 200, api.more(q));
            case "stream" -> Http.json(ex, 200, api.stream(q));
            case "comments" -> Http.json(ex, 200, api.commentsFor(q));
            case "channel" -> Http.json(ex, 200, api.channel(q));
            case "playlist" -> Http.json(ex, 200, api.playlist(q));
            case "mpd" -> Http.send(ex, 200, "application/dash+xml",
                    api.mpd(q).getBytes(StandardCharsets.UTF_8));
            case "hls" -> {
                ex.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
                Http.send(ex, 200, "application/vnd.apple.mpegurl", api.hlsMaster(q).getBytes(StandardCharsets.UTF_8));
            }
            case "feed" -> {
                final String raw = store.read("subscriptions");
                final JSONArray subs = raw == null || "null".equals(raw.trim()) ? new JSONArray() : new JSONArray(raw);
                Http.json(ex, 200, api.feed(subs, "1".equals(q.get("refresh"))));
            }
            default -> {
                if (route.startsWith("store/")) {
                    final String name = route.substring(6);
                    if ("PUT".equals(ex.getRequestMethod()) || "POST".equals(ex.getRequestMethod())) {
                        final String body = new String(Http.body(ex), StandardCharsets.UTF_8);
                        // validate before persisting
                        if (body.trim().startsWith("[")) {
                            new JSONArray(body);
                        } else {
                            new JSONObject(body);
                        }
                        store.write(name, body);
                        if ("settings".equals(name)) {
                            applySettings();
                        }
                        Http.json(ex, 200, new JSONObject().put("ok", true));
                    } else {
                        Http.send(ex, 200, "application/json; charset=utf-8",
                                store.read(name).getBytes(StandardCharsets.UTF_8));
                    }
                } else {
                    throw new Http.HttpError(404, "no such endpoint");
                }
            }
        }
    }

    private void serveStatic(final HttpExchange ex, final String rawPath) throws IOException {
        String path = rawPath.equals("/") || !rawPath.contains(".") ? "/index.html" : rawPath;
        if (path.contains("..")) {
            throw new Http.HttpError(400, "bad path");
        }
        final byte[] data;
        if (webDir != null) {
            final Path f = webDir.resolve(path.substring(1));
            data = Files.exists(f) ? Files.readAllBytes(f) : null;
        } else {
            try (InputStream in = Server.class.getResourceAsStream("/web" + path)) {
                data = in == null ? null : in.readAllBytes();
            }
        }
        if (data == null) {
            throw new Http.HttpError(404, "not found");
        }
        final String ext = path.substring(path.lastIndexOf('.') + 1);
        ex.getResponseHeaders().set("Cache-Control", webDir != null || path.endsWith(".html")
                ? "no-cache" : "public, max-age=86400");
        Http.send(ex, 200, TYPES.getOrDefault(ext, "application/octet-stream"), data);
    }
}
