package ua.museclass.app;

import android.content.Context;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Інструменти — з єдиного довідника shared/instruments.json (лежить в assets
 * при збірці). Порядок у файлі — порядок в інтерфейсі. Це єдине місце в
 * застосунку, де живе список.
 *
 * Поки сервер не перейшов на довідник (open.md), показуємо лише ті 10 кодів,
 * що він приймає, а альт-саксофон шлемо старим кодом saxophone. Коли сервер
 * перейде — прибрати SERVER_CODES і LEGACY_OUT.
 */
public final class Instruments {
    /** Коди, які сервер приймає зараз (docs/api.md, «Довідники»), у новій назві. */
    private static final Set<String> SERVER_CODES = new HashSet<>(Arrays.asList(
            "piano", "guitar", "voice", "violin", "trumpet", "flute", "bass_guitar", "drums",
            "saxophone_alto", "bandura"));
    /** Нова назва → як її поки розуміє сервер. */
    private static final Map<String, String> LEGACY_OUT = Collections.singletonMap("saxophone_alto", "saxophone");

    public static final class Item {
        public final String code;
        public final String name;

        Item(String code, String name) {
            this.code = code;
            this.name = name;
        }
    }

    private static List<Item> all;
    private static Map<String, String> renamed;

    private Instruments() {
    }

    /** Ті, що можна обрати зараз, у порядку довідника. */
    public static synchronized List<Item> list(Context c) {
        load(c);
        List<Item> out = new ArrayList<>();
        for (Item i : all) if (SERVER_CODES.contains(i.code)) out.add(i);
        return out;
    }

    /** Назва за кодом із сервера (старі коди теж); невідомий — сам код. */
    public static synchronized String name(Context c, String code) {
        load(c);
        String k = fromServer(code);
        for (Item i : all) if (i.code.equals(k)) return i.name;
        return code;
    }

    /** Код із сервера → код довідника (saxophone → saxophone_alto). */
    public static synchronized String fromServer(String code) {
        String r = renamed == null ? null : renamed.get(code);
        return r != null ? r : code;
    }

    /** Код довідника → як його поки приймає сервер. */
    public static String toServer(String code) {
        String r = LEGACY_OUT.get(code);
        return r != null ? r : code;
    }

    private static void load(Context c) {
        if (all != null) return;
        List<Item> items = new ArrayList<>();
        Map<String, String> ren = new LinkedHashMap<>();
        try (Reader r = new InputStreamReader(c.getAssets().open("instruments.json"), StandardCharsets.UTF_8)) {
            JsonObject root = JsonParser.parseReader(r).getAsJsonObject();
            JsonArray arr = root.getAsJsonArray("instruments");
            for (JsonElement e : arr) {
                JsonObject o = e.getAsJsonObject();
                items.add(new Item(o.get("code").getAsString(), o.get("name").getAsString()));
            }
            if (root.has("renamed")) {
                for (Map.Entry<String, JsonElement> e : root.getAsJsonObject("renamed").entrySet()) {
                    ren.put(e.getKey(), e.getValue().getAsString());
                }
            }
        } catch (Exception e) {
            throw new IllegalStateException("немає assets/instruments.json", e);
        }
        all = items;
        renamed = ren;
    }
}
