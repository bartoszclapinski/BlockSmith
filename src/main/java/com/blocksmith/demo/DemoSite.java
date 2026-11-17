package com.blocksmith.demo;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import com.blocksmith.core.ChainParams;
import com.blocksmith.util.Target;

/**
 * Builds the recorded demo of the project site (Sprint 28b): the folder
 * {@code docs/demo}, served by GitHub Pages next to the site.
 *
 * <pre>
 *  src/main/resources/public/index.html --page()--> docs/demo/index.html
 *  src/main/resources/public/app.js, style.css ---> docs/demo/   (unchanged)
 *  src/main/demo/demo.js, demo.css ---------------> docs/demo/   (the player)
 *  DemoRecorder ----------------------------------> docs/demo/recording.js
 * </pre>
 *
 * The demo page is the dashboard's own page and script. The only difference
 * is two more script tags before {@code app.js}: the recording, then the
 * player, which sets {@code window.BlockSmithDemo}; the dashboard then asks
 * the player instead of a node. Pages serves {@code docs/} from master, so
 * the folder is committed; a test compares it with the sources, and the
 * build is this class:
 *
 * <pre>
 *  mvn compile exec:java -Dexec.mainClass=com.blocksmith.demo.DemoSite                 (record again, about 5 min)
 *  mvn compile exec:java -Dexec.mainClass=com.blocksmith.demo.DemoSite -Dexec.args=--keep   (same recording, new page)
 * </pre>
 */
public final class DemoSite {

    /** The dashboard's files, copied as they are. */
    public static final List<String> DASHBOARD_FILES = List.of("app.js", "style.css");

    /** The player's files, copied as they are. */
    public static final List<String> PLAYER_FILES = List.of("demo.js", "demo.css");

    public static final String RECORDING_FILE = "recording.js";

    private static final Path DASHBOARD = Path.of("src", "main", "resources", "public");
    private static final Path PLAYER = Path.of("src", "main", "demo");
    private static final Path SITE = Path.of("docs", "demo");

    private DemoSite() {}

    /**
     * The demo page from the dashboard's page: its title, the player's style
     * after the dashboard's, the recording and the player before the
     * dashboard's script. Fails if the dashboard's page no longer has the
     * three lines this relies on.
     */
    public static String page(String dashboardPage) {
        String page = dashboardPage;
        page = replaceOnce(page, "<title>BlockSmith Dashboard</title>",
                "<title>BlockSmith Demo: a recorded node</title>\n"
                + "    <meta name=\"description\" content=\"The BlockSmith dashboard replaying what a real node answered:"
                + " blocks, transactions, contracts, a second node and a reorganisation.\">");
        page = replaceOnce(page, "<link rel=\"stylesheet\" href=\"style.css\">",
                "<link rel=\"stylesheet\" href=\"style.css\">\n    <link rel=\"stylesheet\" href=\"demo.css\">");
        page = replaceOnce(page, "<script src=\"app.js\"></script>",
                "<!-- The recorded demo (Sprint 28): built by DemoSite, do not edit. recording.js is what a real node\n"
                + "     answered; demo.js answers the dashboard from it. app.js is the node's dashboard, unchanged. -->\n"
                + "<script src=\"" + RECORDING_FILE + "\"></script>\n"
                + "<script src=\"demo.js\"></script>\n"
                + "<script src=\"app.js\"></script>");
        return page;
    }

    private static String replaceOnce(String text, String old, String replacement) {
        int at = text.indexOf(old);
        if (at < 0 || text.indexOf(old, at + 1) >= 0) {
            throw new IllegalStateException("The dashboard page should have exactly one: " + old);
        }
        return text.substring(0, at) + replacement + text.substring(at + old.length());
    }

    /**
     * Writes the demo into {@code out}.
     *
     * @param project   the repository's root
     * @param recording the recording to publish, or null to keep the one already in {@code out}
     * @param out       the folder to write, {@code project/docs/demo} for the site
     */
    public static void build(Path project, DemoRecording recording, Path out) throws IOException {
        Files.createDirectories(out);
        String dashboardPage = text(project.resolve(DASHBOARD).resolve("index.html"));
        Files.writeString(out.resolve("index.html"), page(dashboardPage), StandardCharsets.UTF_8);
        for (String name : DASHBOARD_FILES) {
            Files.writeString(out.resolve(name), text(project.resolve(DASHBOARD).resolve(name)), StandardCharsets.UTF_8);
        }
        for (String name : PLAYER_FILES) {
            Files.writeString(out.resolve(name), text(project.resolve(PLAYER).resolve(name)), StandardCharsets.UTF_8);
        }
        if (recording != null) {
            Files.writeString(out.resolve(RECORDING_FILE), recording.toScript(), StandardCharsets.UTF_8);
        } else if (!Files.exists(out.resolve(RECORDING_FILE))) {
            throw new IllegalStateException("No recording to keep in " + out);
        }
    }

    /** A source file with Unix line ends, whatever the checkout made of them. */
    static String text(Path file) throws IOException {
        return Files.readString(file, StandardCharsets.UTF_8).replace("\r\n", "\n");
    }

    /**
     * Records a session on a real node (default parameters, real pace) and
     * builds {@code docs/demo}. Run from the repository's root. Options:
     * {@code --keep} rebuilds the page around the recording already there;
     * {@code --fast} records in seconds on an easy target (to work on the
     * player, not to publish); {@code --out dir} writes somewhere else.
     */
    public static void main(String[] args) throws Exception {
        List<String> options = List.of(args);
        boolean keep = options.contains("--keep");
        boolean fast = options.contains("--fast");
        Path project = Path.of("").toAbsolutePath();
        int at = options.indexOf("--out");
        Path out = at >= 0 && at + 1 < options.size() ? Path.of(options.get(at + 1)).toAbsolutePath() : project.resolve(SITE);

        DemoRecording recording = null;
        if (!keep) {
            ChainParams params = fast ? new ChainParams(Target.fromLeadingZeros(1), 10_000, 1_000_000, 4, Target.MAX,
                    ChainParams.DEFAULT_MAX_FUTURE_DRIFT_MS) : ChainParams.DEFAULT;
            try (DemoRecorder recorder = new DemoRecorder(params,
                    fast ? DemoRecorder.Pace.FAST : DemoRecorder.Pace.REAL, DemoRecorder.Ports.DEFAULT)) {
                recording = recorder.record();
            }
        }
        build(project, recording, out);
        System.out.println("[demo] " + out + " built; " + RECORDING_FILE + " is "
                + Files.size(out.resolve(RECORDING_FILE)) + " bytes" + (keep ? " (kept)" : fast ? " (fast: not for publishing)" : ""));
        System.exit(0);
    }
}
