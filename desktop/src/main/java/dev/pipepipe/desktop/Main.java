package dev.pipepipe.desktop;

import org.schabi.newpipe.extractor.NewPipe;

import java.awt.Desktop;
import java.io.File;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * PipePipe Desktop entry point.
 * <pre>
 *   pipepipe-desktop                 start + open an app window (Chrome/Edge/Chromium app mode)
 *   pipepipe-desktop --server        headless web server only
 *   options: --host 127.0.0.1  --port 47615  --data DIR  --web DIR (serve UI from disk, dev)
 * </pre>
 */
public final class Main {
    static final int DEFAULT_PORT = 47615;

    private Main() { }

    public static void main(final String[] args) throws Exception {
        String host = "127.0.0.1";
        int port = DEFAULT_PORT;
        boolean serverOnly = false;
        Path data = defaultDataDir();
        Path web = null;
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--server" -> serverOnly = true;
                case "--host" -> host = args[++i];
                case "--port" -> port = Integer.parseInt(args[++i]);
                case "--data" -> data = Path.of(args[++i]);
                case "--web" -> web = Path.of(args[++i]);
                case "-h", "--help" -> {
                    System.out.println("usage: pipepipe-desktop [--server] [--host H] [--port P] [--data DIR]");
                    return;
                }
                default -> System.err.println("unknown option " + args[i]);
            }
        }
        Files.createDirectories(data);

        final String local = "http://127.0.0.1:" + port + "/";
        if (!serverOnly && isOurs(local)) {
            // already running: just bring up another window
            openWindow(local, data);
            return;
        }

        final DesktopDownloader downloader = new DesktopDownloader();
        NewPipe.init(downloader);
        silenceExtractorLogs();
        Server server;
        try {
            server = new Server(host, port, downloader, new Store(data), web);
        } catch (final java.net.BindException e) {
            server = new Server(host, 0, downloader, new Store(data), web);
        }
        server.start();
        final String url = "http://127.0.0.1:" + server.port() + "/";
        System.out.println("PipePipe Desktop running at " + url + "  (data: " + data + ")");
        if (!"127.0.0.1".equals(host) && !"localhost".equals(host)) {
            System.out.println("WARNING: listening on " + host + " - anyone on this network can use it.");
        }
        if (serverOnly) {
            return; // HTTP server threads keep the JVM alive
        }
        final Process window = openWindow(url, data);
        if (window != null) {
            // dedicated browser profile: when the window closes, the app quits.
            // A process that exits right away handed off to an already running browser
            // instance, so we cannot tell when the window closes -> keep running with a tray icon.
            final long started = System.currentTimeMillis();
            window.waitFor();
            if (System.currentTimeMillis() - started > 4000) {
                server.stop();
                System.exit(0);
            }
        }
        final Server s = server;
        tray(url, data, () -> {
            s.stop();
            System.exit(0);
        });
    }

    /** Tray icon with open / quit, used when we do not own the window process. */
    private static void tray(final String url, final Path data, final Runnable quit) {
        try {
            if (java.awt.GraphicsEnvironment.isHeadless() || !java.awt.SystemTray.isSupported()) {
                System.out.println("Running. Press Ctrl+C to quit.");
                return;
            }
            final java.awt.Image img;
            try (var in = Main.class.getResourceAsStream("/web/icon.png")) {
                img = javax.imageio.ImageIO.read(in);
            }
            final java.awt.PopupMenu menu = new java.awt.PopupMenu();
            final java.awt.MenuItem open = new java.awt.MenuItem("Open PipePipe");
            open.addActionListener(e -> openWindow(url, data));
            final java.awt.MenuItem exit = new java.awt.MenuItem("Quit");
            exit.addActionListener(e -> quit.run());
            menu.add(open);
            menu.add(exit);
            final java.awt.TrayIcon icon = new java.awt.TrayIcon(img, "PipePipe", menu);
            icon.setImageAutoSize(true);
            icon.addActionListener(e -> openWindow(url, data));
            java.awt.SystemTray.getSystemTray().add(icon);
        } catch (final Exception e) {
            System.out.println("Running at " + url + " - press Ctrl+C to quit.");
        }
    }

    /** The extractor prints a YT_PERF timing line per request; hide them unless debugging. */
    private static void silenceExtractorLogs() {
        if (!"1".equals(System.getenv("PIPEPIPE_DEBUG"))) {
            final var original = System.out;
            System.setOut(new java.io.PrintStream(original, true) {
                @Override
                public void println(final String x) {
                    if (x == null || !x.startsWith("YT_PERF")) {
                        super.println(x);
                    }
                }
            });
        }
    }

    private static boolean isOurs(final String base) {
        try {
            final HttpURLConnection c = (HttpURLConnection) URI.create(base + "api/ping").toURL().openConnection();
            c.setConnectTimeout(300);
            c.setReadTimeout(600);
            return c.getResponseCode() == 200
                    && new String(c.getInputStream().readAllBytes()).contains("pipepipe-desktop");
        } catch (final Exception e) {
            return false;
        }
    }

    static Path defaultDataDir() {
        final String os = System.getProperty("os.name").toLowerCase(Locale.ROOT);
        final String home = System.getProperty("user.home");
        if (os.contains("win")) {
            final String appData = System.getenv("APPDATA");
            return Path.of(appData != null ? appData : home, "PipePipe Desktop");
        }
        if (os.contains("mac")) {
            return Path.of(home, "Library", "Application Support", "PipePipe Desktop");
        }
        final String xdg = System.getenv("XDG_DATA_HOME");
        return Path.of(xdg != null && !xdg.isBlank() ? xdg : home + "/.local/share", "pipepipe-desktop");
    }

    private static List<String> browserCandidates() {
        final String os = System.getProperty("os.name").toLowerCase(Locale.ROOT);
        final List<String> c = new ArrayList<>();
        final String custom = System.getenv("PIPEPIPE_BROWSER");
        if (custom != null && !custom.isBlank()) {
            c.add(custom);
        }
        if (os.contains("win")) {
            for (final String env : new String[]{"ProgramFiles(x86)", "ProgramFiles", "LOCALAPPDATA"}) {
                final String base = System.getenv(env);
                if (base == null) {
                    continue;
                }
                c.add(base + "\\Microsoft\\Edge\\Application\\msedge.exe");
                c.add(base + "\\Google\\Chrome\\Application\\chrome.exe");
                c.add(base + "\\BraveSoftware\\Brave-Browser\\Application\\brave.exe");
                c.add(base + "\\Chromium\\Application\\chrome.exe");
            }
        } else if (os.contains("mac")) {
            for (final String app : new String[]{"Google Chrome", "Microsoft Edge", "Chromium", "Brave Browser", "Vivaldi"}) {
                c.add("/Applications/" + app + ".app/Contents/MacOS/" + app);
                c.add(System.getProperty("user.home") + "/Applications/" + app + ".app/Contents/MacOS/" + app);
            }
        } else {
            final String path = System.getenv("PATH") == null ? "/usr/bin" : System.getenv("PATH");
            for (final String name : new String[]{"google-chrome", "google-chrome-stable", "chromium",
                    "chromium-browser", "microsoft-edge", "microsoft-edge-stable", "brave-browser", "vivaldi"}) {
                for (final String dir : path.split(File.pathSeparator)) {
                    c.add(dir + "/" + name);
                }
            }
            c.add("/var/lib/flatpak/exports/bin/com.google.Chrome");
            c.add("/var/lib/flatpak/exports/bin/org.chromium.Chromium");
            c.add("/snap/bin/chromium");
        }
        return c;
    }

    /** @return the window process when launched in a dedicated app-mode profile, else null */
    static Process openWindow(final String url, final Path data) {
        for (final String exe : browserCandidates()) {
            final File f = new File(exe);
            if (!f.isFile() || !f.canExecute()) {
                continue;
            }
            try {
                return new ProcessBuilder(exe,
                        "--app=" + url,
                        "--user-data-dir=" + data.resolve("window"),
                        "--window-size=1440,900",
                        "--no-first-run", "--no-default-browser-check",
                        "--disable-features=Translate,MediaRouter",
                        "--autoplay-policy=no-user-gesture-required",
                        "--class=PipePipe")
                        .redirectErrorStream(true)
                        .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                        .start();
            } catch (final Exception e) {
                System.err.println("could not start " + exe + ": " + e.getMessage());
            }
        }
        try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(URI.create(url));
            } else {
                System.out.println("Open " + url + " in your browser.");
            }
        } catch (final Exception e) {
            System.out.println("Open " + url + " in your browser.");
        }
        return null;
    }
}
