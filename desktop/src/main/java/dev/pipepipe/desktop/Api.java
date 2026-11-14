package dev.pipepipe.desktop;

import org.json.JSONArray;
import org.json.JSONObject;
import org.schabi.newpipe.extractor.InfoItem;
import org.schabi.newpipe.extractor.ListExtractor.InfoItemsPage;
import org.schabi.newpipe.extractor.NewPipe;
import org.schabi.newpipe.extractor.Page;
import org.schabi.newpipe.extractor.StreamingService;
import org.schabi.newpipe.extractor.channel.ChannelInfo;
import org.schabi.newpipe.extractor.channel.ChannelInfoItem;
import org.schabi.newpipe.extractor.comments.CommentsInfo;
import org.schabi.newpipe.extractor.comments.CommentsInfoItem;
import org.schabi.newpipe.extractor.feed.FeedExtractor;
import org.schabi.newpipe.extractor.feed.FeedInfo;
import org.schabi.newpipe.extractor.kiosk.KioskInfo;
import org.schabi.newpipe.extractor.linkhandler.SearchQueryHandler;
import org.schabi.newpipe.extractor.localization.DateWrapper;
import org.schabi.newpipe.extractor.playlist.PlaylistInfo;
import org.schabi.newpipe.extractor.playlist.PlaylistInfoItem;
import org.schabi.newpipe.extractor.search.SearchInfo;
import org.schabi.newpipe.extractor.search.filter.FilterItem;
import org.schabi.newpipe.extractor.ServiceList;
import org.schabi.newpipe.extractor.stream.AudioStream;
import org.schabi.newpipe.extractor.stream.DeliveryMethod;
import org.schabi.newpipe.extractor.stream.Stream;
import org.schabi.newpipe.extractor.stream.Description;
import org.schabi.newpipe.extractor.stream.StreamInfo;
import org.schabi.newpipe.extractor.stream.StreamInfoItem;
import org.schabi.newpipe.extractor.stream.StreamSegment;
import org.schabi.newpipe.extractor.stream.StreamType;
import org.schabi.newpipe.extractor.stream.SubtitlesStream;
import org.schabi.newpipe.extractor.stream.VideoStream;
import org.schabi.newpipe.extractor.MediaFormat;

import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/** Maps PipePipeExtractor results to the JSON consumed by the web UI. */
final class Api {
    private final MediaProxy proxy;
    private final SecureRandom random = new SecureRandom();
    private final ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor();

    /** Opaque continuation tokens: Pages stay server side, the UI only sees a random id. */
    private record Cont(String kind, int service, String url, Object handler, Page page) { }

    private final Map<String, Cont> continuations = lru(2000);
    private final Map<String, Timed<StreamInfo>> streams = lru(64);
    private final Map<String, Timed<List<StreamInfoItem>>> feedCache = new ConcurrentHashMap<>();

    private record Timed<T>(T value, long at) {
        boolean fresh(final long maxAgeMs) {
            return System.currentTimeMillis() - at < maxAgeMs;
        }
    }

    Api(final MediaProxy proxy) {
        this.proxy = proxy;
    }

    private static <K, V> Map<K, V> lru(final int max) {
        return Collections.synchronizedMap(new LinkedHashMap<>(16, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(final Map.Entry<K, V> e) {
                return size() > max;
            }
        });
    }

    private String cont(final String kind, final int service, final String url, final Object handler,
                        final Page page) {
        if (!Page.isValid(page)) {
            return null;
        }
        final byte[] b = new byte[9];
        random.nextBytes(b);
        final String id = HexFormat.of().formatHex(b);
        continuations.put(id, new Cont(kind, service, url, handler, page));
        return id;
    }

    private static StreamingService service(final String id) throws Exception {
        return NewPipe.getService(id == null || id.isBlank() ? 0 : Integer.parseInt(id));
    }

    // ---------------------------------------------------------------- serialisation

    private static String best(final List<org.schabi.newpipe.extractor.Image> images, final String fallback) {
        if (images == null || images.isEmpty()) {
            return fallback;
        }
        org.schabi.newpipe.extractor.Image pick = images.get(images.size() - 1);
        for (final var img : images) {
            if (img.getHeight() >= 180 && img.getHeight() <= 720) {
                pick = img;
                break;
            }
        }
        return pick.getUrl();
    }

    private static Object date(final DateWrapper d) {
        if (d == null) {
            return JSONObject.NULL;
        }
        try {
            return d.offsetDateTime().toInstant().toEpochMilli();
        } catch (final Exception e) {
            return JSONObject.NULL;
        }
    }

    static JSONObject item(final InfoItem it) {
        final JSONObject o = new JSONObject()
                .put("service", it.getServiceId())
                .put("url", it.getUrl())
                .put("name", it.getName())
                .put("thumb", it.getThumbnailUrl());
        if (it instanceof StreamInfoItem s) {
            o.put("type", "stream")
             .put("uploader", s.getUploaderName())
             .put("uploaderUrl", s.getUploaderUrl())
             .put("uploaderAvatar", s.getUploaderAvatarUrl())
             .put("verified", s.isUploaderVerified())
             .put("duration", s.getDuration())
             .put("views", s.getViewCount())
             .put("date", date(s.getUploadDate()))
             .put("dateText", s.getTextualUploadDate())
             .put("live", s.getStreamType() == StreamType.LIVE_STREAM
                     || s.getStreamType() == StreamType.AUDIO_LIVE_STREAM)
             .put("short", s.isShortFormContent());
        } else if (it instanceof ChannelInfoItem c) {
            o.put("type", "channel")
             .put("subs", c.getSubscriberCount())
             .put("streams", c.getStreamCount())
             .put("description", c.getDescription())
             .put("verified", c.isVerified());
        } else if (it instanceof PlaylistInfoItem p) {
            o.put("type", "playlist")
             .put("uploader", p.getUploaderName())
             .put("uploaderUrl", p.getUploaderUrl())
             .put("streams", p.getStreamCount());
        } else {
            o.put("type", "other");
        }
        return o;
    }

    private static JSONArray items(final List<? extends InfoItem> list) {
        final JSONArray a = new JSONArray();
        if (list != null) {
            for (final InfoItem i : list) {
                a.put(item(i));
            }
        }
        return a;
    }

    private JSONObject list(final List<? extends InfoItem> list, final String next) {
        return new JSONObject().put("items", items(list)).put("next", next == null ? JSONObject.NULL : next);
    }

    // ---------------------------------------------------------------- endpoints

    JSONArray services() {
        final JSONArray a = new JSONArray();
        for (final StreamingService s : NewPipe.getServices()) {
            final JSONArray kiosks = new JSONArray();
            try {
                for (final String k : s.getKioskList().getAvailableKiosks()) {
                    kiosks.put(k);
                }
            } catch (final Exception ignored) {
                // service without kiosks
            }
            String defaultKiosk = null;
            try {
                defaultKiosk = s.getKioskList().getDefaultKioskId();
            } catch (final Exception ignored) {
                // none
            }
            a.put(new JSONObject().put("id", s.getServiceId()).put("name", s.getServiceInfo().getName())
                    .put("kiosks", kiosks).put("defaultKiosk", defaultKiosk == null ? JSONObject.NULL : defaultKiosk));
        }
        return a;
    }

    JSONObject kiosk(final Map<String, String> q) throws Exception {
        final StreamingService s = service(q.get("service"));
        final String id = q.getOrDefault("id", s.getKioskList().getDefaultKioskId());
        final var extractor = s.getKioskList().getExtractorById(id, null);
        extractor.fetchPage();
        final KioskInfo info = KioskInfo.getInfo(extractor);
        return list(info.getRelatedItems(), cont("kiosk", s.getServiceId(), info.getUrl(), null, info.getNextPage()))
                .put("title", info.getName());
    }

    JSONObject search(final Map<String, String> q) throws Exception {
        final StreamingService s = service(q.get("service"));
        final var factory = s.getSearchQHFactory();
        final int filterId = q.containsKey("filter") ? Integer.parseInt(q.get("filter")) : 0;
        FilterItem selected = factory.getFilterItem(filterId);
        if (selected == null) {
            selected = factory.getFilterItem(0);
        }
        final SearchQueryHandler handler = factory.fromQuery(Http.require(q, "q"), List.of(selected), null);
        final SearchInfo info = SearchInfo.getInfo(s, handler);
        final JSONArray filters = new JSONArray();
        try {
            for (final var group : factory.getAvailableContentFilter().getFilterGroups()) {
                for (final FilterItem f : group.filterItems) {
                    filters.put(new JSONObject().put("id", f.getIdentifier()).put("name", f.getName())
                            .put("group", group.groupName == null ? "" : group.groupName));
                }
            }
        } catch (final Exception ignored) {
            // service without filters
        }
        return list(info.getRelatedItems(), cont("search", s.getServiceId(), null, handler, info.getNextPage()))
                .put("filters", filters).put("filter", selected.getIdentifier())
                .put("suggestion", info.getSearchSuggestion() == null ? JSONObject.NULL : info.getSearchSuggestion())
                .put("corrected", info.isCorrectedSearch());
    }

    JSONArray suggest(final Map<String, String> q) throws Exception {
        final StreamingService s = service(q.get("service"));
        return new JSONArray(s.getSuggestionExtractor().suggestionList(Http.require(q, "q")));
    }

    /** Next page for any list produced earlier. */
    JSONObject more(final Map<String, String> q) throws Exception {
        final Cont c = continuations.get(Http.require(q, "next"));
        if (c == null) {
            throw new Http.HttpError(410, "continuation expired");
        }
        final StreamingService s = NewPipe.getService(c.service());
        final InfoItemsPage<? extends InfoItem> page = switch (c.kind()) {
            case "search" -> SearchInfo.getMoreItems(s, (SearchQueryHandler) c.handler(), c.page());
            case "kiosk" -> KioskInfo.getMoreItems(s, c.url(), c.page());
            case "channel" -> ChannelInfo.getMoreItems(s, c.url(), c.page());
            case "playlist" -> PlaylistInfo.getMoreItems(s, c.url(), c.page());
            case "comments" -> CommentsInfo.getMoreItems(s, c.url(), c.page());
            default -> throw new Http.HttpError(400, "unknown continuation");
        };
        final String next = cont(c.kind(), c.service(), c.url(), c.handler(), page.getNextPage());
        if ("comments".equals(c.kind())) {
            return comments(page.getItems(), next);
        }
        return list(page.getItems(), next);
    }

    JSONObject channel(final Map<String, String> q) throws Exception {
        final String url = Http.require(q, "url");
        final StreamingService s = NewPipe.getServiceByUrl(url);
        final ChannelInfo info = ChannelInfo.getInfo(s, url);
        return list(info.getRelatedItems(), cont("channel", s.getServiceId(), info.getUrl(), null, info.getNextPage()))
                .put("service", s.getServiceId())
                .put("url", info.getUrl())
                .put("name", info.getName())
                .put("avatar", info.getAvatarUrl())
                .put("banner", info.getBannerUrl())
                .put("subs", info.getSubscriberCount())
                .put("description", info.getDescription());
    }

    JSONObject playlist(final Map<String, String> q) throws Exception {
        final String url = Http.require(q, "url");
        final StreamingService s = NewPipe.getServiceByUrl(url);
        final PlaylistInfo info = PlaylistInfo.getInfo(s, url);
        return list(info.getRelatedItems(), cont("playlist", s.getServiceId(), info.getUrl(), null, info.getNextPage()))
                .put("service", s.getServiceId())
                .put("url", info.getUrl())
                .put("name", info.getName())
                .put("thumb", info.getThumbnailUrl())
                .put("uploader", info.getUploaderName())
                .put("uploaderUrl", info.getUploaderUrl())
                .put("count", info.getStreamCount());
    }

    private static JSONObject comments(final List<? extends InfoItem> list, final String next) {
        final JSONArray a = new JSONArray();
        for (final InfoItem i : list) {
            if (!(i instanceof CommentsInfoItem c)) {
                continue;
            }
            final Description d = c.getCommentText();
            a.put(new JSONObject()
                    .put("author", c.getUploaderName())
                    .put("authorUrl", c.getUploaderUrl())
                    .put("avatar", c.getUploaderAvatarUrl())
                    .put("text", d == null ? "" : d.getContent())
                    .put("html", d != null && d.getType() == Description.HTML)
                    .put("likes", c.getLikeCount())
                    .put("likesText", c.getTextualLikeCount())
                    .put("date", c.getTextualUploadDate())
                    .put("at", commentDate(c))
                    .put("replies", c.getReplyCount()));
        }
        return new JSONObject().put("items", a).put("next", next == null ? JSONObject.NULL : next);
    }

    private static Object commentDate(final CommentsInfoItem c) {
        try {
            return date(c.getUploadDate());
        } catch (final Exception e) {
            return JSONObject.NULL;
        }
    }

    JSONObject commentsFor(final Map<String, String> q) throws Exception {
        final String url = Http.require(q, "url");
        final StreamingService s = NewPipe.getServiceByUrl(url);
        final CommentsInfo info = CommentsInfo.getInfo(s, url);
        if (info == null) {
            return new JSONObject().put("items", new JSONArray()).put("disabled", true);
        }
        return comments(info.getRelatedItems(), cont("comments", s.getServiceId(), info.getUrl(), null, info.getNextPage()))
                .put("disabled", info.isCommentsDisabled());
    }

    private StreamInfo streamInfo(final String url) throws Exception {
        final Timed<StreamInfo> cached = streams.get(url);
        if (cached != null && cached.fresh(TimeUnit.MINUTES.toMillis(90))) {
            return cached.value();
        }
        final StreamInfo info = StreamInfo.getInfo(NewPipe.getServiceByUrl(url), url);
        streams.put(url, new Timed<>(info, System.currentTimeMillis()));
        streams.put(info.getUrl(), new Timed<>(info, System.currentTimeMillis()));
        return info;
    }

    String mpd(final Map<String, String> q) throws Exception {
        final StreamInfo info = streamInfo(Http.require(q, "url"));
        return Mpd.build(info, proxy, st -> headers(info, st));
    }

    /** Headers a service needs for media requests (see PipePipeClient MediaSourceBuilder). */
    static Map<String, String> headers(final StreamInfo info, final Stream st) {
        final Map<String, String> h = new LinkedHashMap<>();
        if (info.getServiceId() == ServiceList.BiliBili.getServiceId()) {
            h.put("Referer", "https://www.bilibili.com");
        }
        if (st != null && st.getRequestHeaders() != null) {
            h.putAll(st.getRequestHeaders());
        }
        if (st != null && st.getContent().contains(COOKIE_MARKER)) {
            final String frag = java.net.URLDecoder.decode(
                    st.getContent().substring(st.getContent().indexOf(COOKIE_MARKER) + COOKIE_MARKER.length()),
                    java.nio.charset.StandardCharsets.UTF_8);
            final int len = frag.indexOf("&length=");
            h.put("Cookie", len < 0 ? frag : frag.substring(0, len));
        }
        return h;
    }

    private static final String COOKIE_MARKER = "#cookie=";

    private static String bareUrl(final Stream st) {
        final String c = st.getContent();
        final int i = c.indexOf(COOKIE_MARKER);
        return i < 0 ? c : c.substring(0, i);
    }

    private static boolean isHls(final Stream st) {
        return st.isUrl() && (st.getDeliveryMethod() == DeliveryMethod.HLS
                || java.net.URI.create(bareUrl(st)).getPath().endsWith(".m3u8"));
    }

    private static boolean hlsSplit(final StreamInfo info) {
        return info.getVideoOnlyStreams().stream().anyMatch(Api::isHls)
                && info.getAudioStreams().stream().anyMatch(Api::isHls);
    }

    /** Master playlist pairing separate video-only and audio HLS media playlists (NicoNico). */
    String hlsMaster(final Map<String, String> q) throws Exception {
        final StreamInfo info = streamInfo(Http.require(q, "url"));
        final StringBuilder sb = new StringBuilder("#EXTM3U\n#EXT-X-INDEPENDENT-SEGMENTS\n");
        boolean first = true;
        for (final AudioStream a : info.getAudioStreams()) {
            if (!isHls(a)) {
                continue;
            }
            sb.append("#EXT-X-MEDIA:TYPE=AUDIO,GROUP-ID=\"aud\",NAME=\"")
              .append(a.getAudioTrackName() == null ? "main" : a.getAudioTrackName().replace("\"", ""))
              .append("\",DEFAULT=").append(first ? "YES" : "NO").append(",AUTOSELECT=YES,URI=\"")
              .append(proxy.hls(bareUrl(a), headers(info, a))).append("\"\n");
            first = false;
        }
        for (final VideoStream v : info.getVideoOnlyStreams()) {
            if (!isHls(v)) {
                continue;
            }
            final int h = v.getHeight() > 0 ? v.getHeight() : parseHeight(v.getResolution());
            final int w = h * 16 / 9;
            sb.append("#EXT-X-STREAM-INF:BANDWIDTH=").append(v.getBitrate() > 0 ? v.getBitrate() : Math.max(1, h) * 3000)
              .append(",RESOLUTION=").append(w).append('x').append(h)
              .append(",AUDIO=\"aud\"\n").append(proxy.hls(bareUrl(v), headers(info, v))).append('\n');
        }
        return sb.toString();
    }

    private static int parseHeight(final String res) {
        final var m = java.util.regex.Pattern.compile("(\\d{3,4})").matcher(res == null ? "" : res);
        return m.find() ? Integer.parseInt(m.group(1)) : 720;
    }

    JSONObject stream(final Map<String, String> q) throws Exception {
        final String url = Http.require(q, "url");
        final StreamInfo info = streamInfo(url);
        final boolean live = info.getStreamType() == StreamType.LIVE_STREAM
                || info.getStreamType() == StreamType.AUDIO_LIVE_STREAM;

        final JSONObject player = new JSONObject();
        final Map<String, String> manifestHeaders = new LinkedHashMap<>(headers(info, null));
        if (info.getManifestRequestHeaders() != null) {
            manifestHeaders.putAll(info.getManifestRequestHeaders());
        }
        final String enc = java.net.URLEncoder.encode(info.getUrl(), java.nio.charset.StandardCharsets.UTF_8);
        if (live && info.getHlsUrl() != null && !info.getHlsUrl().isEmpty()) {
            player.put("mode", "hls").put("src", proxy.hls(info.getHlsUrl(), manifestHeaders));
        } else if (Mpd.canBuild(info)) {
            player.put("mode", "dash").put("src", "/api/mpd?url=" + enc);
        } else if (hlsSplit(info)) {
            player.put("mode", "hls").put("src", "/api/hls?url=" + enc);
        } else if (info.getHlsUrl() != null && !info.getHlsUrl().isEmpty()) {
            player.put("mode", "hls").put("src", proxy.hls(info.getHlsUrl(), manifestHeaders));
        } else {
            player.put("mode", "file");
        }
        // progressive / split sources for the fallback player and for audio-only mode
        final JSONArray muxed = new JSONArray();
        for (final VideoStream v : info.getVideoStreams()) {
            if (v.isUrl()) {
                muxed.put(new JSONObject().put("q", v.getResolution()).put("h", v.getHeight())
                        .put("mime", v.getFormat() == null ? "" : v.getFormat().getMimeType())
                        .put("src", proxy.media(bareUrl(v), headers(info, v))));
            }
        }
        final JSONArray videoOnly = new JSONArray();
        for (final VideoStream v : info.getVideoOnlyStreams()) {
            if (v.isUrl()) {
                videoOnly.put(new JSONObject().put("q", v.getResolution()).put("h", v.getHeight())
                        .put("codec", v.getCodec())
                        .put("mime", v.getFormat() == null ? "" : v.getFormat().getMimeType())
                        .put("src", proxy.media(bareUrl(v), headers(info, v))));
            }
        }
        final JSONArray audio = new JSONArray();
        for (final AudioStream a : info.getAudioStreams()) {
            if (a.isUrl()) {
                audio.put(new JSONObject().put("kbps", a.getAverageBitrate())
                        .put("mime", a.getFormat() == null ? "" : a.getFormat().getMimeType())
                        .put("track", a.getAudioTrackName() == null ? JSONObject.NULL : a.getAudioTrackName())
                        .put("src", proxy.media(bareUrl(a), headers(info, a))));
            }
        }
        player.put("muxed", muxed).put("videoOnly", videoOnly).put("audio", audio);

        final JSONArray subs = new JSONArray();
        for (final SubtitlesStream st : info.getSubtitles()) {
            if (st.getFormat() == MediaFormat.VTT && st.isUrl()) {
                subs.put(new JSONObject().put("lang", st.getLanguageTag())
                        .put("name", st.getDisplayLanguageName() + (st.isAutoGenerated() ? " (auto)" : ""))
                        .put("auto", st.isAutoGenerated())
                        .put("src", proxy.media(st.getContent(), headers(info, st))));
            }
        }
        final JSONArray chapters = new JSONArray();
        for (final StreamSegment seg : info.getStreamSegments()) {
            chapters.put(new JSONObject().put("t", seg.getStartTimeSeconds()).put("title", seg.getTitle())
                    .put("thumb", seg.getPreviewUrl() == null ? JSONObject.NULL : seg.getPreviewUrl()));
        }
        final Description d = info.getDescription();
        final JSONArray tags = new JSONArray();
        if (info.getTags() != null) {
            info.getTags().forEach(tags::put);
        }
        return new JSONObject()
                .put("service", info.getServiceId())
                .put("id", info.getId())
                .put("url", info.getUrl())
                .put("name", info.getName())
                .put("thumb", info.getThumbnailUrl())
                .put("duration", info.getDuration())
                .put("views", info.getViewCount())
                .put("likes", info.getLikeCount())
                .put("date", date(info.getUploadDate()))
                .put("dateText", info.getTextualUploadDate())
                .put("live", live)
                .put("uploader", new JSONObject()
                        .put("name", info.getUploaderName())
                        .put("url", info.getUploaderUrl())
                        .put("avatar", info.getUploaderAvatarUrl())
                        .put("subs", info.getUploaderSubscriberCount())
                        .put("verified", info.isUploaderVerified()))
                .put("description", new JSONObject()
                        .put("html", d != null && d.getType() == Description.HTML)
                        .put("text", d == null ? "" : d.getContent()))
                .put("tags", tags)
                .put("category", info.getCategory())
                .put("startAt", info.getStartPosition())
                .put("player", player)
                .put("subtitles", subs)
                .put("chapters", chapters)
                .put("related", items(info.getRelatedItems()));
    }

    // ---------------------------------------------------------------- subscriptions feed

    private List<StreamInfoItem> channelFeed(final String url) throws Exception {
        final Timed<List<StreamInfoItem>> c = feedCache.get(url);
        if (c != null && c.fresh(TimeUnit.MINUTES.toMillis(20))) {
            return c.value();
        }
        final StreamingService s = NewPipe.getServiceByUrl(url);
        List<StreamInfoItem> result;
        final FeedExtractor fe = s.getFeedExtractor(url);
        if (fe != null) {
            result = FeedInfo.getInfo(fe).getRelatedItems();
        } else {
            result = ChannelInfo.getInfo(s, url).getRelatedItems();
        }
        if (result == null) {
            result = List.of();
        }
        feedCache.put(url, new Timed<>(result, System.currentTimeMillis()));
        return result;
    }

    JSONObject feed(final JSONArray subscriptions, final boolean refresh) throws Exception {
        if (refresh) {
            feedCache.clear();
        }
        final Semaphore gate = new Semaphore(8);
        final List<Future<List<StreamInfoItem>>> jobs = new ArrayList<>();
        for (int i = 0; i < subscriptions.length(); i++) {
            final String url = subscriptions.getJSONObject(i).getString("url");
            jobs.add(pool.submit(() -> {
                gate.acquire();
                try {
                    return channelFeed(url);
                } finally {
                    gate.release();
                }
            }));
        }
        final List<StreamInfoItem> all = new ArrayList<>();
        int failed = 0;
        for (final Future<List<StreamInfoItem>> f : jobs) {
            try {
                all.addAll(f.get(45, TimeUnit.SECONDS));
            } catch (final Exception e) {
                failed++;
            }
        }
        all.sort(Comparator.comparing((StreamInfoItem s) -> {
            try {
                return s.getUploadDate() == null ? OffsetDateTime.MIN : s.getUploadDate().offsetDateTime();
            } catch (final Exception e) {
                return OffsetDateTime.MIN;
            }
        }).reversed());
        return new JSONObject().put("items", items(all.size() > 600 ? all.subList(0, 600) : all))
                .put("failed", failed).put("next", JSONObject.NULL);
    }
}
