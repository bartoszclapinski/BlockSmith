package com.blocksmith.demo;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * What a node answered during a recorded session (Sprint 28a): the file the
 * demo on the project site is played from.
 *
 * THEORY: RECORD AND REPLAY AT THE API BOUNDARY
 *
 * The dashboard talks to the node through one function (fetchJson). A
 * recording keeps, for every moment of a session ("frame"), the answer to
 * every path the dashboard can ask at that moment. Replaying is a lookup:
 * frame + path -> answer. Nothing about blocks or transactions is computed
 * again, so the demo cannot disagree with the node: it is the node's words.
 *
 * THEORY: STORING REPEATED PARTS ONCE
 *
 * The same transaction is in its block, on its own page and on the pages of
 * two addresses, in every frame after it was mined. Written out, ten frames
 * of a 33-block chain are 3 MB. Here every object or list of at least
 * {@link #MIN_SHARED} characters is stored once in a pool and replaced by a
 * reference:
 *
 *   pool[17]  = {"transactionId":"9d8b...","sender":"0x85fb...", ...}
 *   pool[212] = [{"$":16},{"$":17}]
 *   frame 4:  "/api/blocks/31" -> {"index":31, ..., "transactions":{"$":212}}
 *
 * A part is added to the pool after its own parts, so a reference always
 * points backwards. Reading follows "$" until none is left. The same 3 MB
 * become about 0.7 MB (90 KB gzipped).
 *
 * @param recordedAt    when the session ended (ms)
 * @param activeAddress the wallet the dashboard shows as "yours"
 * @param misses        what the node says for something it does not have,
 *                      by the start of the path ("/api/transactions/" ...)
 * @param frames        the session, in order
 */
public record DemoRecording(long recordedAt, String activeAddress, Map<String, Miss> misses, List<Frame> frames) {

    /** The file format; the player refuses another one. */
    public static final int FORMAT = 1;

    /** Objects and lists shorter than this are written where they are. */
    public static final int MIN_SHARED = 80;

    private static final String REF = "$";
    private static final String SCRIPT_PREFIX = "window.BLOCKSMITH_RECORDING = ";

    /**
     * One moment of the session.
     *
     * @param title   what just happened, a few words
     * @param detail  one or two sentences for the viewer
     * @param at      when the answers were read (ms)
     * @param answers path (decoded, with its query) -> the node's JSON, status 200
     */
    public record Frame(String title, String detail, long at, Map<String, JsonElement> answers) {}

    /**
     * An answer that is not 200: the node's own words for "not found".
     *
     * @param probe what was asked for when this was recorded; the node repeats
     *              it in its message ("No block at index 999999"), so the
     *              player puts what the visitor asked for in its place
     */
    public record Miss(int status, JsonElement body, String probe) {}

    /** The whole recording as one JSON object, repeated parts stored once. */
    public String toJson() {
        Map<String, Integer> index = new LinkedHashMap<>();
        JsonArray pool = new JsonArray();
        JsonArray frameList = new JsonArray();
        for (Frame f : frames) {
            JsonObject answers = new JsonObject();
            f.answers().forEach((path, body) -> answers.add(path, share(body, index, pool)));
            JsonObject frame = new JsonObject();
            frame.addProperty("title", f.title());
            frame.addProperty("detail", f.detail());
            frame.addProperty("at", f.at());
            frame.add("answers", answers);
            frameList.add(frame);
        }
        JsonObject missList = new JsonObject();
        misses.forEach((prefix, miss) -> {
            JsonObject m = new JsonObject();
            m.addProperty("status", miss.status());
            m.add("body", miss.body());
            m.addProperty("probe", miss.probe());
            missList.add(prefix, m);
        });
        JsonObject out = new JsonObject();
        out.addProperty("format", FORMAT);
        out.addProperty("recordedAt", recordedAt);
        out.addProperty("activeAddress", activeAddress);
        out.add("misses", missList);
        out.add("pool", pool);
        out.add("frames", frameList);
        return out.toString();
    }

    /** Reads {@link #toJson()} back: every answer whole again. */
    public static DemoRecording fromJson(String json) {
        JsonObject in = JsonParser.parseString(json).getAsJsonObject();
        int format = in.get("format").getAsInt();
        if (format != FORMAT) {
            throw new IllegalArgumentException("Recording format " + format + ", expected " + FORMAT);
        }
        JsonArray pool = in.getAsJsonArray("pool");
        List<Frame> frames = new ArrayList<>();
        for (JsonElement e : in.getAsJsonArray("frames")) {
            JsonObject f = e.getAsJsonObject();
            Map<String, JsonElement> answers = new LinkedHashMap<>();
            for (var a : f.getAsJsonObject("answers").entrySet()) answers.put(a.getKey(), whole(a.getValue(), pool));
            frames.add(new Frame(f.get("title").getAsString(), f.get("detail").getAsString(),
                    f.get("at").getAsLong(), answers));
        }
        Map<String, Miss> misses = new LinkedHashMap<>();
        for (var m : in.getAsJsonObject("misses").entrySet()) {
            JsonObject miss = m.getValue().getAsJsonObject();
            misses.put(m.getKey(), new Miss(miss.get("status").getAsInt(), miss.get("body"), miss.get("probe").getAsString()));
        }
        return new DemoRecording(in.get("recordedAt").getAsLong(), in.get("activeAddress").getAsString(),
                misses, frames);
    }

    /** The recording as a script the demo page loads before the dashboard: one assignment. */
    public String toScript() {
        return SCRIPT_PREFIX + toJson() + ";\n";
    }

    /** Reads {@link #toScript()} back. */
    public static DemoRecording fromScript(String script) {
        String text = script.strip();
        if (!text.startsWith(SCRIPT_PREFIX) || !text.endsWith(";")) {
            throw new IllegalArgumentException("Not a recording script");
        }
        return fromJson(text.substring(SCRIPT_PREFIX.length(), text.length() - 1));
    }

    /** {@code e} with its big parts moved to the pool, children before parents. */
    private static JsonElement share(JsonElement e, Map<String, Integer> index, JsonArray pool) {
        JsonElement copy;
        if (e.isJsonObject()) {
            JsonObject o = new JsonObject();
            for (var entry : e.getAsJsonObject().entrySet()) o.add(entry.getKey(), share(entry.getValue(), index, pool));
            copy = o;
        } else if (e.isJsonArray()) {
            JsonArray a = new JsonArray();
            for (JsonElement x : e.getAsJsonArray()) a.add(share(x, index, pool));
            copy = a;
        } else {
            return e;
        }
        String text = copy.toString();
        if (text.length() < MIN_SHARED) return copy;
        Integer id = index.get(text);
        if (id == null) {
            id = pool.size();
            index.put(text, id);
            pool.add(copy);
        }
        JsonObject ref = new JsonObject();
        ref.addProperty(REF, id);
        return ref;
    }

    /** The opposite of {@link #share}: follows every reference. */
    private static JsonElement whole(JsonElement e, JsonArray pool) {
        if (e.isJsonObject()) {
            JsonObject o = e.getAsJsonObject();
            if (o.size() == 1 && o.has(REF)) return whole(pool.get(o.get(REF).getAsInt()), pool);
            JsonObject out = new JsonObject();
            for (var entry : o.entrySet()) out.add(entry.getKey(), whole(entry.getValue(), pool));
            return out;
        }
        if (e.isJsonArray()) {
            JsonArray out = new JsonArray();
            for (JsonElement x : e.getAsJsonArray()) out.add(whole(x, pool));
            return out;
        }
        return e;
    }
}
