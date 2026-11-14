package dev.pipepipe.desktop;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.ConnectionPool;
import okhttp3.Dispatcher;
import okhttp3.OkHttpClient;
import okhttp3.RequestBody;
import okhttp3.ResponseBody;
import org.schabi.newpipe.extractor.downloader.CancellableCall;
import org.schabi.newpipe.extractor.downloader.Downloader;
import org.schabi.newpipe.extractor.downloader.Request;
import org.schabi.newpipe.extractor.downloader.Response;
import org.schabi.newpipe.extractor.downloader.StreamingResponse;
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException;
import org.schabi.newpipe.extractor.localization.Localization;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Desktop port of PipePipeClient's DownloaderImpl: OkHttp based, no Android dependencies.
 */
public final class DesktopDownloader extends Downloader {
    public static final String USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:128.0) Gecko/20100101 Firefox/128.0";

    private final OkHttpClient client;

    public DesktopDownloader() {
        final Dispatcher dispatcher = new Dispatcher();
        dispatcher.setMaxRequests(64);
        dispatcher.setMaxRequestsPerHost(16);
        this.client = new OkHttpClient.Builder()
                .dispatcher(dispatcher)
                .connectionPool(new ConnectionPool(16, 5, TimeUnit.MINUTES))
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .build();
    }

    public OkHttpClient client() {
        return client;
    }

    private static boolean isChallenge(final okhttp3.Response r) {
        if (r.code() == 429 || "challenge".equalsIgnoreCase(r.header("cf-mitigated"))) {
            return true;
        }
        final String host = r.request().url().host();
        return host.endsWith("google.com") && r.request().url().encodedPath().startsWith("/sorry");
    }

    private okhttp3.Request build(final Request request) {
        final byte[] data = request.dataToSend();
        RequestBody body = null;
        if (data != null) {
            body = RequestBody.create(data);
        } else if ("POST".equals(request.httpMethod())) {
            body = RequestBody.create(new byte[0]);
        }
        final okhttp3.Request.Builder b = new okhttp3.Request.Builder()
                .method(request.httpMethod(), body).url(request.url());
        final Map<String, List<String>> headers = request.headers();
        if (!headers.containsKey("User-Agent")) {
            b.header("User-Agent", USER_AGENT);
        }
        for (final Map.Entry<String, List<String>> e : headers.entrySet()) {
            final List<String> values = e.getValue();
            if (values.size() > 1) {
                b.removeHeader(e.getKey());
                for (final String v : values) {
                    b.addHeader(e.getKey(), v);
                }
            } else if (values.size() == 1) {
                b.header(e.getKey(), values.get(0));
            }
        }
        return b.build();
    }

    private OkHttpClient clientFor(final Request request) {
        if (request.followRedirects()) {
            return client;
        }
        return client.newBuilder().followRedirects(false).followSslRedirects(false).build();
    }

    private static Response toResponse(final okhttp3.Response response) throws IOException {
        final ResponseBody body = response.body();
        final byte[] raw = body.bytes();
        return new Response(response.code(), response.message(), response.headers().toMultimap(),
                new String(raw, StandardCharsets.UTF_8), raw, response.request().url().toString());
    }

    @Override
    public Response execute(@Nonnull final Request request) throws IOException, ReCaptchaException {
        try (okhttp3.Response response = clientFor(request).newCall(build(request)).execute()) {
            if (isChallenge(response)) {
                throw new ReCaptchaException("reCaptcha Challenge requested", request.url());
            }
            return toResponse(response);
        }
    }

    @Override
    public StreamingResponse getStreaming(final String url,
                                          @Nullable final Map<String, List<String>> headers,
                                          @Nullable final Localization localization)
            throws IOException, ReCaptchaException {
        return streaming(Request.newBuilder().get(url).headers(headers)
                .localization(localization).build(), client);
    }

    @Override
    public StreamingResponse getStreaming(final String url,
                                          @Nullable final Map<String, List<String>> headers,
                                          @Nullable final Localization localization,
                                          final long timeoutMs)
            throws IOException, ReCaptchaException {
        final long t = Math.max(1, timeoutMs);
        return streaming(Request.newBuilder().get(url).headers(headers)
                .localization(localization).build(), client.newBuilder()
                .callTimeout(t, TimeUnit.MILLISECONDS)
                .readTimeout(t, TimeUnit.MILLISECONDS).build());
    }

    @Override
    public StreamingResponse postStreaming(final String url,
                                           @Nullable final Map<String, List<String>> headers,
                                           @Nullable final byte[] dataToSend,
                                           @Nullable final Localization localization)
            throws IOException, ReCaptchaException {
        return streaming(Request.newBuilder().post(url, dataToSend).headers(headers)
                .localization(localization).build(), client);
    }

    private StreamingResponse streaming(final Request request, final OkHttpClient c)
            throws IOException, ReCaptchaException {
        final okhttp3.Response response = c.newCall(build(request)).execute();
        if (isChallenge(response)) {
            response.close();
            throw new ReCaptchaException("reCaptcha Challenge requested", request.url());
        }
        final ResponseBody body = response.body();
        final InputStream in = body == null ? new ByteArrayInputStream(new byte[0]) : body.byteStream();
        return new StreamingResponse(response.code(), response.headers().toMultimap(), in);
    }

    @Override
    public CancellableCall executeAsync(@Nonnull final Request request,
                                        final AsyncCallback callback) {
        final Call call = clientFor(request).newCall(build(request));
        final CancellableCall cancellable = new CancellableCall(call);
        call.enqueue(new Callback() {
            @Override
            public void onFailure(@Nonnull final Call c, @Nonnull final IOException e) {
                try {
                    callback.onError(e);
                } finally {
                    cancellable.setFinished();
                }
            }

            @Override
            public void onResponse(@Nonnull final Call c, @Nonnull final okhttp3.Response response) {
                try (response) {
                    if (isChallenge(response)) {
                        callback.onError(new ReCaptchaException("reCaptcha Challenge requested",
                                request.url()));
                        return;
                    }
                    callback.onSuccess(toResponse(response));
                } catch (final Exception e) {
                    callback.onError(e);
                } finally {
                    cancellable.setFinished();
                }
            }
        });
        return cancellable;
    }
}
