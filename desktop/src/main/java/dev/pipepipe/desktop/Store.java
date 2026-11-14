package dev.pipepipe.desktop;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Set;

/** Local JSON documents (subscriptions, history, playlists, settings) in the data directory. */
final class Store {
    static final Set<String> NAMES = Set.of("subscriptions", "history", "playlists", "settings");
    private final Path dir;

    Store(final Path dataDir) throws IOException {
        this.dir = dataDir.resolve("store");
        Files.createDirectories(dir);
    }

    private Path file(final String name) {
        if (!NAMES.contains(name)) {
            throw new Http.HttpError(404, "unknown store " + name);
        }
        return dir.resolve(name + ".json");
    }

    synchronized String read(final String name) throws IOException {
        final Path f = file(name);
        return Files.exists(f) ? Files.readString(f, StandardCharsets.UTF_8) : "null";
    }

    synchronized void write(final String name, final String json) throws IOException {
        final Path f = file(name);
        final Path tmp = f.resolveSibling(name + ".json.tmp");
        Files.writeString(tmp, json, StandardCharsets.UTF_8);
        Files.move(tmp, f, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    }
}
