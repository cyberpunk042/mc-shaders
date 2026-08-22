package net.cyberpunk042.mcshaders.codec;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSyntaxException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.cyberpunk042.mcshaders.core.binding.Condition;
import net.cyberpunk042.mcshaders.core.binding.DimensionBinding;
import net.cyberpunk042.mcshaders.core.binding.DimensionId;
import net.cyberpunk042.mcshaders.core.binding.Weather;
import net.cyberpunk042.mcshaders.core.effect.BlendMode;
import net.cyberpunk042.mcshaders.core.effect.EffectKind;
import net.cyberpunk042.mcshaders.core.effect.EffectLayer;
import net.cyberpunk042.mcshaders.core.effect.EffectStack;
import net.cyberpunk042.mcshaders.core.param.EffectParams;
import net.cyberpunk042.mcshaders.core.param.ParamValue;

/**
 * Reads and writes dimension bindings as JSON.
 *
 * <p>This is what makes a pack authorable without Java. Everything the framework
 * needs to decide how a dimension looks — the layers, their parameters, the
 * conditions that switch them on — is a data structure, and this is the one place
 * that turns that structure into text and back.
 *
 * <h2>Why it is here and not in {@code core}</h2>
 *
 * <p>For the same reason the shader checker's codec is not in core: the engine
 * models and evaluates bindings, it does not parse them, so the published library
 * carries no JSON dependency and content can arrive from anywhere. Gson is used
 * because Minecraft already provides it — verified rather than assumed: Jade imports
 * {@code com.google.gson} in two files and declares no gson dependency, so on a
 * loader this costs nothing at runtime.
 *
 * <h2>The format</h2>
 *
 * <pre>{@code
 * {
 *   "id": "nether_base",
 *   "dimension": "minecraft:the_nether",
 *   "priority": 0,
 *   "condition": { "type": "time_of_day", "from": 13000, "to": 23000 },
 *   "stack": {
 *     "layers": [{
 *       "id": "heat_haze",
 *       "kind": "distort",
 *       "type": "example:shimmer",
 *       "blend": "alpha",
 *       "weight": 1.0,
 *       "priority": 0,
 *       "params": {
 *         "speed": 0.5,
 *         "on": true,
 *         "label": "wobble",
 *         "dir": [1.0, 0.0, 0.0],
 *         "tint": { "r": 1.0, "g": 0.6, "b": 0.4, "a": 1.0 }
 *       }
 *     }]
 *   }
 * }
 * }</pre>
 *
 * <p><b>Parameters are typed by their JSON shape</b>, not by a tag. A number is a
 * scalar, a boolean is a flag, a string is text, a three-element array is a vector,
 * an object with {@code r}/{@code g}/{@code b} is a colour. Tagging every value
 * would double the size of the common case for the benefit of none of them, and the
 * five shapes do not overlap, so the mapping is unambiguous in both directions.
 *
 * <p><b>What that costs:</b> a quoted number is a text parameter, not an error.
 * {@code "speed": "0.5"} is a typo a person will make, and nothing here can catch it
 * — text parameters are legitimate, and the codec does not know what {@code speed}
 * is meant to be. The layer that does know is {@code EffectSchema}, and
 * {@code SchemaAudit} is where a value of the wrong type against a declared
 * parameter gets reported. This is a real gap between the two layers rather than a
 * hidden one, and a test pins the behaviour so it cannot drift quietly.
 *
 * <p><b>What that costs:</b> a quoted number is a text parameter, not an error.
 * {@code "speed": "0.5"} is a typo a person will make, and nothing here can catch it
 * — text parameters are legitimate, and the codec does not know what {@code speed}
 * is meant to be. The layer that does know is {@code EffectSchema}, and
 * {@code SchemaAudit} is where a value of the wrong type against a declared
 * parameter gets reported. This is a real gap between the two layers rather than a
 * hidden one, and a test pins the behaviour so it cannot drift quietly.
 *
 * <p><b>Enums are written lower-case</b> and read case-insensitively. A pack author
 * writing {@code "alpha"} and one writing {@code "ALPHA"} both mean the blend mode,
 * and failing one of them teaches nothing.
 *
 * <h2>Errors</h2>
 *
 * <p>Every failure is a {@link PackException} naming the source and the path within
 * it. That is the difference between an error a pack author can act on and one that
 * sends them looking through the whole file.
 *
 * <h2>Round-tripping</h2>
 *
 * <p>{@link #write} then {@link #read} returns an equal binding. This is not a
 * decorative property: it is what lets the editor's tuning be saved back out as a
 * pack, and it is the cheapest way to test a codec, since any field the writer
 * forgets or the reader drops shows up as an inequality.
 *
 * <p>One asymmetry is deliberate. Defaults are written explicitly rather than
 * omitted, so a written file is a complete example of the format — but they may be
 * omitted on read. A hand-written pack stays short; a generated one stays legible.
 */
public final class BindingCodec {

    private BindingCodec() {
    }

    // ── reading ──────────────────────────────────────────────────────────────

    /**
     * Reads one binding.
     *
     * @param source what to call this in error messages — a file name or resource id
     * @throws PackException if the JSON is malformed or does not describe a binding
     */
    public static DimensionBinding read(Reader json, String source) {
        return readBinding(parse(json, source), source, JsonPath.root());
    }

    /** Reads one binding from a string. */
    public static DimensionBinding read(String json, String source) {
        return read(new java.io.StringReader(json), source);
    }

    /**
     * Reads a document that is either one binding or an array of them.
     *
     * <p>Both are accepted because both are natural: one file per dimension is the
     * obvious layout, and a single file listing several is the obvious way to ship a
     * small pack. Refusing either would be a rule with nothing behind it.
     */
    public static List<DimensionBinding> readAll(Reader json, String source) {
        JsonElement root = parse(json, source);
        if (!root.isJsonArray()) {
            return List.of(readBinding(root, source, JsonPath.root()));
        }
        JsonArray array = root.getAsJsonArray();
        List<DimensionBinding> out = new ArrayList<>(array.size());
        for (int i = 0; i < array.size(); i++) {
            out.add(readBinding(array.get(i), source, JsonPath.root().index(i)));
        }
        return List.copyOf(out);
    }

    private static JsonElement parse(Reader json, String source) {
        try {
            return JsonParser.parseReader(json);
        } catch (JsonSyntaxException | com.google.gson.JsonIOException e) {
            throw new PackException(source, "", "not valid JSON: " + e.getMessage(), e);
        }
    }

    private static DimensionBinding readBinding(JsonElement element, String source, JsonPath at) {
        JsonObject o = object(element, source, at);
        String id = string(o, "id", source, at, null);
        DimensionId dimension = readDimension(o, source, at);
        Condition condition = o.has("condition")
                ? readCondition(o.get("condition"), source, at.field("condition"))
                : Condition.always();
        EffectStack stack = o.has("stack")
                ? readStack(o.get("stack"), source, at.field("stack"))
                : EffectStack.empty();
        int priority = integer(o, "priority", source, at, 0);
        try {
            return new DimensionBinding(id, dimension, condition, stack, priority);
        } catch (IllegalArgumentException e) {
            // The model's own invariants, reported against the pack's coordinates
            // rather than as a bare exception from somewhere inside the engine.
            throw new PackException(source, at.toString(), e.getMessage(), e);
        }
    }

    private static DimensionId readDimension(JsonObject o, String source, JsonPath at) {
        String raw = string(o, "dimension", source, at, null);
        try {
            return DimensionId.parse(raw);
        } catch (IllegalArgumentException e) {
            throw new PackException(source, at.field("dimension").toString(),
                    "'" + raw + "' is not a dimension id: " + e.getMessage(), e);
        }
    }

    private static EffectStack readStack(JsonElement element, String source, JsonPath at) {
        JsonObject o = object(element, source, at);
        if (!o.has("layers")) {
            return EffectStack.empty();
        }
        JsonPath layersAt = at.field("layers");
        JsonElement layers = o.get("layers");
        if (!layers.isJsonArray()) {
            throw new PackException(source, layersAt.toString(),
                    "expected an array of layers, found " + describe(layers));
        }
        JsonArray array = layers.getAsJsonArray();
        List<EffectLayer> out = new ArrayList<>(array.size());
        for (int i = 0; i < array.size(); i++) {
            out.add(readLayer(array.get(i), source, layersAt.index(i)));
        }
        return EffectStack.of(out);
    }

    private static EffectLayer readLayer(JsonElement element, String source, JsonPath at) {
        JsonObject o = object(element, source, at);
        String id = string(o, "id", source, at, null);
        EffectKind kind = enumValue(EffectKind.class, o, "kind", source, at, null);
        String type = o.has("type") && !o.get("type").isJsonNull()
                ? string(o, "type", source, at, null)
                : null;
        BlendMode blend = enumValue(BlendMode.class, o, "blend", source, at, BlendMode.ALPHA);
        double weight = o.has("weight") ? number(o, "weight", source, at) : 1.0;
        int priority = integer(o, "priority", source, at, 0);
        EffectParams params = o.has("params")
                ? readParams(o.get("params"), source, at.field("params"))
                : EffectParams.empty();
        try {
            return new EffectLayer(id, type, kind, params, blend, weight, priority);
        } catch (IllegalArgumentException e) {
            throw new PackException(source, at.toString(), e.getMessage(), e);
        }
    }

    private static EffectParams readParams(JsonElement element, String source, JsonPath at) {
        JsonObject o = object(element, source, at);
        Map<String, ParamValue> values = new LinkedHashMap<>();
        for (Map.Entry<String, JsonElement> entry : o.entrySet()) {
            values.put(entry.getKey(),
                    readParam(entry.getValue(), source, at.field(entry.getKey())));
        }
        return EffectParams.of(values);
    }

    private static ParamValue readParam(JsonElement element, String source, JsonPath at) {
        if (element.isJsonArray()) {
            JsonArray a = element.getAsJsonArray();
            if (a.size() != 3) {
                throw new PackException(source, at.toString(),
                        "a vector needs exactly three numbers, found " + a.size());
            }
            return new ParamValue.Vec3(
                    element(a, 0, source, at), element(a, 1, source, at), element(a, 2, source, at));
        }
        if (element.isJsonObject()) {
            JsonObject o = element.getAsJsonObject();
            return new ParamValue.Rgba(
                    (float) number(o, "r", source, at),
                    (float) number(o, "g", source, at),
                    (float) number(o, "b", source, at),
                    o.has("a") ? (float) number(o, "a", source, at) : 1.0f);
        }
        if (!element.isJsonPrimitive()) {
            throw new PackException(source, at.toString(),
                    "expected a number, boolean, string, vector or colour, found "
                            + describe(element));
        }
        JsonPrimitive p = element.getAsJsonPrimitive();
        if (p.isBoolean()) {
            return new ParamValue.Flag(p.getAsBoolean());
        }
        if (p.isNumber()) {
            return new ParamValue.Scalar(p.getAsDouble());
        }
        return new ParamValue.Text(p.getAsString());
    }

    private static Condition readCondition(JsonElement element, String source, JsonPath at) {
        JsonObject o = object(element, source, at);
        String type = string(o, "type", source, at, null).toLowerCase(Locale.ROOT);
        return switch (type) {
            case "always" -> Condition.always();
            case "never" -> Condition.never();
            case "submerged" -> new Condition.Submerged();
            case "time_of_day" -> new Condition.TimeOfDay(
                    (long) number(o, "from", source, at), (long) number(o, "to", source, at));
            case "y_range" -> new Condition.YRange(
                    number(o, "min", source, at), number(o, "max", source, at));
            case "weather" -> new Condition.InWeather(
                    enumValue(Weather.class, o, "weather", source, at, null));
            case "biome_tag" -> new Condition.HasBiomeTag(string(o, "tag", source, at, null));
            case "all" -> new Condition.All(readConditions(o, source, at));
            case "any" -> new Condition.Any(readConditions(o, source, at));
            case "not" -> new Condition.Not(
                    readCondition(required(o, "of", source, at), source, at.field("of")));
            default -> throw new PackException(source, at.field("type").toString(),
                    "'" + type + "' is not a condition type. Known types: always, never, "
                            + "submerged, time_of_day, y_range, weather, biome_tag, all, any, not");
        };
    }

    private static List<Condition> readConditions(JsonObject o, String source, JsonPath at) {
        JsonElement of = required(o, "of", source, at);
        JsonPath ofAt = at.field("of");
        if (!of.isJsonArray()) {
            throw new PackException(source, ofAt.toString(),
                    "expected an array of conditions, found " + describe(of));
        }
        JsonArray array = of.getAsJsonArray();
        List<Condition> out = new ArrayList<>(array.size());
        for (int i = 0; i < array.size(); i++) {
            out.add(readCondition(array.get(i), source, ofAt.index(i)));
        }
        return List.copyOf(out);
    }

    // ── writing ──────────────────────────────────────────────────────────────

    /** Writes one binding, with defaults spelled out. */
    public static JsonObject write(DimensionBinding binding) {
        JsonObject o = new JsonObject();
        o.addProperty("id", binding.id());
        o.addProperty("dimension", binding.dimension().toString());
        o.addProperty("priority", binding.priority());
        o.add("condition", writeCondition(binding.condition()));
        o.add("stack", writeStack(binding.stack()));
        return o;
    }

    /** Writes several bindings as an array. */
    public static JsonArray writeAll(List<DimensionBinding> bindings) {
        JsonArray out = new JsonArray();
        bindings.forEach(b -> out.add(write(b)));
        return out;
    }

    private static JsonObject writeStack(EffectStack stack) {
        JsonArray layers = new JsonArray();
        stack.layers().forEach(layer -> layers.add(writeLayer(layer)));
        JsonObject o = new JsonObject();
        o.add("layers", layers);
        return o;
    }

    private static JsonObject writeLayer(EffectLayer layer) {
        JsonObject o = new JsonObject();
        o.addProperty("id", layer.id());
        o.addProperty("kind", name(layer.kind()));
        if (layer.type() != null) {
            o.addProperty("type", layer.type());
        }
        o.addProperty("blend", name(layer.blend()));
        o.addProperty("weight", layer.weight());
        o.addProperty("priority", layer.priority());
        o.add("params", writeParams(layer.params()));
        return o;
    }

    private static JsonObject writeParams(EffectParams params) {
        JsonObject o = new JsonObject();
        params.asMap().forEach((key, value) -> o.add(key, writeParam(value)));
        return o;
    }

    private static JsonElement writeParam(ParamValue value) {
        return switch (value) {
            case ParamValue.Scalar s -> new JsonPrimitive(s.value());
            case ParamValue.Flag f -> new JsonPrimitive(f.value());
            case ParamValue.Text t -> new JsonPrimitive(t.value());
            case ParamValue.Vec3 v -> {
                JsonArray a = new JsonArray();
                a.add(v.x());
                a.add(v.y());
                a.add(v.z());
                yield a;
            }
            case ParamValue.Rgba c -> {
                JsonObject o = new JsonObject();
                o.addProperty("r", c.r());
                o.addProperty("g", c.g());
                o.addProperty("b", c.b());
                o.addProperty("a", c.a());
                yield o;
            }
        };
    }

    private static JsonObject writeCondition(Condition condition) {
        JsonObject o = new JsonObject();
        switch (condition) {
            case Condition.Always ignored -> o.addProperty("type", "always");
            case Condition.Never ignored -> o.addProperty("type", "never");
            case Condition.Submerged ignored -> o.addProperty("type", "submerged");
            case Condition.TimeOfDay t -> {
                o.addProperty("type", "time_of_day");
                o.addProperty("from", t.from());
                o.addProperty("to", t.to());
            }
            case Condition.YRange y -> {
                o.addProperty("type", "y_range");
                o.addProperty("min", y.min());
                o.addProperty("max", y.max());
            }
            case Condition.InWeather w -> {
                o.addProperty("type", "weather");
                o.addProperty("weather", name(w.weather()));
            }
            case Condition.HasBiomeTag b -> {
                o.addProperty("type", "biome_tag");
                o.addProperty("tag", b.tag());
            }
            case Condition.All a -> {
                o.addProperty("type", "all");
                o.add("of", writeConditions(a.children()));
            }
            case Condition.Any a -> {
                o.addProperty("type", "any");
                o.add("of", writeConditions(a.children()));
            }
            case Condition.Not n -> {
                o.addProperty("type", "not");
                o.add("of", writeCondition(n.child()));
            }
        }
        return o;
    }

    private static JsonArray writeConditions(List<Condition> children) {
        JsonArray out = new JsonArray();
        children.forEach(c -> out.add(writeCondition(c)));
        return out;
    }

    // ── shared helpers ───────────────────────────────────────────────────────

    private static JsonObject object(JsonElement element, String source, JsonPath at) {
        if (element == null || !element.isJsonObject()) {
            throw new PackException(source, at.toString(),
                    "expected an object, found " + describe(element));
        }
        return element.getAsJsonObject();
    }

    private static JsonElement required(JsonObject o, String field, String source, JsonPath at) {
        JsonElement value = o.get(field);
        if (value == null || value.isJsonNull()) {
            throw new PackException(source, at.toString(), "missing '" + field + "'");
        }
        return value;
    }

    private static String string(
            JsonObject o, String field, String source, JsonPath at, String fallback) {
        JsonElement value = o.get(field);
        if (value == null || value.isJsonNull()) {
            if (fallback != null) {
                return fallback;
            }
            throw new PackException(source, at.toString(), "missing '" + field + "'");
        }
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) {
            throw new PackException(source, at.field(field).toString(),
                    "expected a string, found " + describe(value));
        }
        return value.getAsString();
    }

    private static double number(JsonObject o, String field, String source, JsonPath at) {
        JsonElement value = required(o, field, source, at);
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) {
            throw new PackException(source, at.field(field).toString(),
                    "expected a number, found " + describe(value));
        }
        return value.getAsDouble();
    }

    private static double element(JsonArray a, int i, String source, JsonPath at) {
        JsonElement value = a.get(i);
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) {
            throw new PackException(source, at.index(i).toString(),
                    "expected a number, found " + describe(value));
        }
        return value.getAsDouble();
    }

    private static int integer(
            JsonObject o, String field, String source, JsonPath at, int fallback) {
        if (!o.has(field) || o.get(field).isJsonNull()) {
            return fallback;
        }
        return (int) number(o, field, source, at);
    }

    private static <E extends Enum<E>> E enumValue(
            Class<E> type, JsonObject o, String field, String source, JsonPath at, E fallback) {
        if (!o.has(field) || o.get(field).isJsonNull()) {
            if (fallback != null) {
                return fallback;
            }
            throw new PackException(source, at.toString(), "missing '" + field + "'");
        }
        String raw = string(o, field, source, at, null);
        for (E candidate : type.getEnumConstants()) {
            if (candidate.name().equalsIgnoreCase(raw)) {
                return candidate;
            }
        }
        StringBuilder known = new StringBuilder();
        for (E candidate : type.getEnumConstants()) {
            known.append(known.isEmpty() ? "" : ", ").append(name(candidate));
        }
        throw new PackException(source, at.field(field).toString(),
                "'" + raw + "' is not one of: " + known);
    }

    private static String name(Enum<?> value) {
        return value.name().toLowerCase(Locale.ROOT);
    }

    private static String describe(JsonElement element) {
        if (element == null || element.isJsonNull()) {
            return "null";
        }
        if (element.isJsonArray()) {
            return "an array";
        }
        if (element.isJsonObject()) {
            return "an object";
        }
        JsonPrimitive p = element.getAsJsonPrimitive();
        if (p.isBoolean()) {
            return "a boolean";
        }
        if (p.isNumber()) {
            return "a number";
        }
        return "a string";
    }
}
