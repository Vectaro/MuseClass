package ua.museclass.app;

import android.content.Context;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Інструменти — з єдиного довідника shared/instruments.json (лежить в assets
 * при збірці). Порядок у файлі — порядок в інтерфейсі. Це єдине місце в
 * застосунку, де живе список; сервер приймає ті самі коди.
 */
public final class Instruments {
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

    /** Усі інструменти в порядку довідника. */
    public static synchronized List<Item> list(Context c) {
        load(c);
        return Collections.unmodifiableList(all);
    }

    /** Назва за кодом (старі коди з renamed теж); невідомий — сам код. */
    public static synchronized String name(Context c, String code) {
        load(c);
        String k = renamed.containsKey(code) ? renamed.get(code) : code;
        for (Item i : all) if (i.code.equals(k)) return i.name;
        return code;
    }

    private static void load(Context c) {
        if (all != null) return;
        List<Item> items = new ArrayList<>();
        Map<String, String> ren = new HashMap<>();
        try (Reader r = new InputStreamReader(c.getAssets().open("instruments.json"), StandardCharsets.UTF_8)) {
            JsonObject root = JsonParser.parseReader(r).getAsJsonObject();
            for (JsonElement e : root.getAsJsonArray("instruments")) {
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
