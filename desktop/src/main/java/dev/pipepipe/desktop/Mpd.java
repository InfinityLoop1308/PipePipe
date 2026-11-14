package dev.pipepipe.desktop;

import org.schabi.newpipe.extractor.stream.AudioStream;
import org.schabi.newpipe.extractor.stream.Stream;
import org.schabi.newpipe.extractor.stream.StreamInfo;
import org.schabi.newpipe.extractor.stream.VideoStream;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Builds a static DASH manifest out of the progressive (byte-range indexed) streams the
 * extractor returns, so the browser can do adaptive playback up to 4K via MSE.
 */
final class Mpd {
    private Mpd() { }

    static boolean indexed(final Stream s) {
        if (!s.isUrl() || s.getDeliveryMethod() != org.schabi.newpipe.extractor.stream.DeliveryMethod.PROGRESSIVE_HTTP) {
            return false;
        }
        if (s instanceof VideoStream v) {
            return v.getIndexEnd() > 0 && v.getInitEnd() > 0;
        }
        if (s instanceof AudioStream a) {
            return a.getIndexEnd() > 0 && a.getInitEnd() > 0;
        }
        return false;
    }

    static boolean canBuild(final StreamInfo info) {
        return info.getVideoOnlyStreams().stream().anyMatch(Mpd::indexed)
                && info.getAudioStreams().stream().anyMatch(Mpd::indexed);
    }

    private static String esc(final String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    static String build(final StreamInfo info, final MediaProxy proxy,
                        final java.util.function.Function<Stream, Map<String, String>> headers) {
        long durationMs = info.getDuration() * 1000L;
        for (final VideoStream v : info.getVideoOnlyStreams()) {
            if (v.getItagItem() != null && v.getItagItem().getApproxDurationMs() > 0) {
                durationMs = v.getItagItem().getApproxDurationMs();
                break;
            }
        }
        if (durationMs <= 0) {
            durationMs = 1000L * 60 * 60 * 3;
        }
        final StringBuilder sb = new StringBuilder(8192);
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
          .append("<MPD xmlns=\"urn:mpeg:dash:schema:mpd:2011\" profiles=\"urn:mpeg:dash:profile:isoff-on-demand:2011\"")
          .append(" type=\"static\" minBufferTime=\"PT1.5S\" mediaPresentationDuration=\"PT")
          .append(String.format(Locale.ROOT, "%.3f", durationMs / 1000.0)).append("S\">\n<Period>\n");

        // video: group by container + codec family
        final Map<String, List<VideoStream>> videoSets = new LinkedHashMap<>();
        for (final VideoStream v : info.getVideoOnlyStreams()) {
            if (!indexed(v) || v.getFormat() == null) {
                continue;
            }
            final String codec = v.getCodec() == null ? "" : v.getCodec();
            final String family = codec.contains(".") ? codec.substring(0, codec.indexOf('.')) : codec;
            videoSets.computeIfAbsent(v.getFormat().getMimeType() + "|" + family, k -> new ArrayList<>()).add(v);
        }
        int setId = 0;
        for (final Map.Entry<String, List<VideoStream>> e : videoSets.entrySet()) {
            final String mime = e.getKey().split("\\|")[0];
            sb.append("<AdaptationSet id=\"").append(setId++).append("\" contentType=\"video\" mimeType=\"")
              .append(mime).append("\" subsegmentAlignment=\"true\">\n");
            for (final VideoStream v : e.getValue()) {
                sb.append("<Representation id=\"v").append(v.getItag()).append('_').append(setId)
                  .append("\" codecs=\"").append(esc(v.getCodec())).append('"')
                  .append(" bandwidth=\"").append(Math.max(1, v.getBitrate() > 0 ? v.getBitrate() : 1000000)).append('"');
                if (v.getWidth() > 0) {
                    sb.append(" width=\"").append(v.getWidth()).append("\" height=\"").append(v.getHeight()).append('"');
                }
                if (v.getFps() > 0) {
                    sb.append(" frameRate=\"").append(v.getFps()).append('"');
                }
                sb.append(">\n<BaseURL>").append(esc(proxy.media(v.getContent(), headers.apply(v))))
                  .append("</BaseURL>\n<SegmentBase indexRange=\"").append(v.getIndexStart()).append('-')
                  .append(v.getIndexEnd()).append("\"><Initialization range=\"").append(v.getInitStart())
                  .append('-').append(v.getInitEnd()).append("\"/></SegmentBase>\n</Representation>\n");
            }
            sb.append("</AdaptationSet>\n");
        }

        // audio: group by track (language / dubbing) + container
        final Map<String, List<AudioStream>> audioSets = new LinkedHashMap<>();
        for (final AudioStream a : info.getAudioStreams()) {
            if (!indexed(a) || a.getFormat() == null) {
                continue;
            }
            final String track = a.getAudioTrackId() == null ? "" : a.getAudioTrackId();
            audioSets.computeIfAbsent(track + "|" + a.getFormat().getMimeType(), k -> new ArrayList<>()).add(a);
        }
        for (final Map.Entry<String, List<AudioStream>> e : audioSets.entrySet()) {
            final AudioStream first = e.getValue().get(0);
            final String mime = e.getKey().substring(e.getKey().indexOf('|') + 1);
            sb.append("<AdaptationSet id=\"").append(setId++).append("\" contentType=\"audio\" mimeType=\"")
              .append(mime).append("\" subsegmentAlignment=\"true\"");
            final String locale = first.getAudioLocale() != null ? first.getAudioLocale()
                    : first.getAudioTrackId() != null && first.getAudioTrackId().contains(".")
                    ? first.getAudioTrackId().substring(0, first.getAudioTrackId().indexOf('.')) : null;
            if (locale != null && !locale.isBlank()) {
                sb.append(" lang=\"").append(esc(locale)).append('"');
            }
            if (first.getAudioTrackName() != null) {
                sb.append(" label=\"").append(esc(first.getAudioTrackName())).append('"');
            }
            sb.append(">\n");
            final String name = first.getAudioTrackName() == null ? "" : first.getAudioTrackName().toLowerCase(Locale.ROOT);
            if (audioSets.size() > 2 && (name.contains("original") || name.contains("원본"))) {
                sb.append("<Role schemeIdUri=\"urn:mpeg:dash:role:2011\" value=\"main\"/>\n");
            }
            for (final AudioStream a : e.getValue()) {
                final var it = a.getItagItem();
                sb.append("<Representation id=\"a").append(a.getItag()).append('_').append(setId)
                  .append("\" codecs=\"").append(esc(a.getCodec() == null ? "mp4a.40.2" : a.getCodec()))
                  .append("\" bandwidth=\"").append(Math.max(1, a.getBitrate() > 0 ? a.getBitrate() : a.getAverageBitrate() * 1000)).append('"');
                if (it != null && it.getSampleRate() > 0) {
                    sb.append(" audioSamplingRate=\"").append(it.getSampleRate()).append('"');
                }
                sb.append(">\n");
                final int ch = it == null || it.getAudioChannels() <= 0 ? 2 : it.getAudioChannels();
                sb.append("<AudioChannelConfiguration schemeIdUri=\"urn:mpeg:dash:23003:3:audio_channel_configuration:2011\" value=\"")
                  .append(ch).append("\"/>\n");
                sb.append("<BaseURL>").append(esc(proxy.media(a.getContent(), headers.apply(a))))
                  .append("</BaseURL>\n<SegmentBase indexRange=\"").append(a.getIndexStart()).append('-')
                  .append(a.getIndexEnd()).append("\"><Initialization range=\"").append(a.getInitStart())
                  .append('-').append(a.getInitEnd()).append("\"/></SegmentBase>\n</Representation>\n");
            }
            sb.append("</AdaptationSet>\n");
        }
        sb.append("</Period>\n</MPD>\n");
        return sb.toString();
    }
}
