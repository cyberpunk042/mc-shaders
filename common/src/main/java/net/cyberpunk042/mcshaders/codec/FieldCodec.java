package net.cyberpunk042.mcshaders.codec;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonIOException;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSyntaxException;
import java.io.Reader;
import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.cyberpunk042.mcshaders.core.field.FieldLayer;
import net.cyberpunk042.mcshaders.core.field.Primitive;
import net.cyberpunk042.mcshaders.core.field.SimplePrimitive;
import net.cyberpunk042.mcshaders.core.serial.JsonField;
import net.cyberpunk042.mcshaders.core.shape.Shape;
import net.cyberpunk042.mcshaders.core.shape.ShapeRegistry;
import org.joml.Vector3f;

/**
 * Reads and writes field layers as JSON, from the model's own description of itself.
 *
 * <p>The model carries {@link JsonField} on several hundred components — a key name, any
 * aliases, and the conditions under which a value may be omitted. Those directives were
 * a specification with no implementation; this is the implementation. Nothing here
 * decides what the format looks like: the annotations already did, and this reflects
 * them.
 *
 * <h2>Why it mirrors the model rather than prettifying it</h2>
 *
 * <p>A hand-tuned format would have to be kept in agreement with the model by hand, and
 * the drift would be silent. Deriving both directions from the same annotations means a
 * component added to a record is carried by both without anyone remembering to, and
 * {@code write} then {@code read} returns an equal layer by construction rather than by
 * discipline.
 *
 * <h2>How a value is omitted, and how it comes back</h2>
 *
 * <p>The skip directives are what make a written file short, and they are also what lets
 * a reader put the value back: a component omitted because it equalled
 * {@code Transform.IDENTITY} is restored to {@code Transform.IDENTITY}, because the
 * annotation says that is why it was absent. That symmetry is the whole reason the
 * directives can be trusted in both directions.
 *
 * <h2>Shapes</h2>
 *
 * <p>{@code Shape} is an interface, so a written shape needs to say which one it is.
 * {@link SimplePrimitive} already carries that: its {@code type} component holds the
 * shape's own {@link Shape#getType()}, so the discriminator is a field of the model
 * rather than something invented for the file.
 *
 * <p>Resolving it back to a class cannot go through {@link ShapeRegistry#create}: those
 * factories read a subset of each shape's components and default the rest — the sphere
 * factory ignores {@code algorithm} outright — so a round trip through them would drop
 * fields silently. The registry is used only to discover which classes exist; every
 * value is read reflectively.
 */
public final class FieldCodec {

    private FieldCodec() {
    }

    // ── reading ──────────────────────────────────────────────────────────────

    /** Reads one field layer. */
    public static FieldLayer read(Reader json, String source) {
        JsonElement root;
        try {
            root = JsonParser.parseReader(json);
        } catch (JsonSyntaxException | JsonIOException e) {
            throw new PackException(source, "", "not valid JSON: " + e.getMessage(), e);
        }
        return (FieldLayer) readValue(FieldLayer.class, null, root, source, JsonPath.root());
    }

    /** Reads one field layer from a string. */
    public static FieldLayer read(String json, String source) {
        return read(new java.io.StringReader(json), source);
    }

    // ── writing ──────────────────────────────────────────────────────────────

    /** Writes a field layer, omitting every value its annotations say may be omitted. */
    public static JsonObject write(FieldLayer layer) {
        if (layer == null) {
            throw new IllegalArgumentException("Cannot write a null layer");
        }
        return (JsonObject) writeValue(layer);
    }

    // ── the record walk ──────────────────────────────────────────────────────

    private static JsonElement writeValue(Object value) {
        if (value == null) {
            return JsonNull.INSTANCE;
        }
        if (value instanceof String s) {
            return new JsonPrimitive(s);
        }
        if (value instanceof Boolean b) {
            return new JsonPrimitive(b);
        }
        if (value instanceof Number n) {
            return new JsonPrimitive(n);
        }
        if (value instanceof Character c) {
            return new JsonPrimitive(c.toString());
        }
        if (value instanceof Enum<?> e) {
            return new JsonPrimitive(e.name());
        }
        if (value instanceof Vector3f v) {
            JsonObject o = new JsonObject();
            o.addProperty("x", v.x);
            o.addProperty("y", v.y);
            o.addProperty("z", v.z);
            return o;
        }
        if (value instanceof Collection<?> c) {
            JsonArray a = new JsonArray();
            for (Object element : c) {
                a.add(writeValue(element));
            }
            return a;
        }
        if (value.getClass().isArray()) {
            JsonArray a = new JsonArray();
            for (int i = 0; i < Array.getLength(value); i++) {
                a.add(writeValue(Array.get(value, i)));
            }
            return a;
        }
        if (value.getClass().isRecord()) {
            return writeRecord(value);
        }
        throw new IllegalArgumentException(
                "No way to write a " + value.getClass().getName()
                        + "; the codec covers records, enums, primitives, strings, "
                        + "Vector3f, collections and arrays");
    }

    private static JsonObject writeRecord(Object record) {
        JsonObject out = new JsonObject();
        RecordComponent[] components = record.getClass().getRecordComponents();
        Map<String, Object> byName = new LinkedHashMap<>();
        for (RecordComponent rc : components) {
            byName.put(rc.getName(), read(record, rc));
        }
        for (RecordComponent rc : components) {
            JsonField jf = annotation(record.getClass(), rc);
            Object value = byName.get(rc.getName());
            if (jf != null && omit(jf, value, byName, rc)) {
                continue;
            }
            out.add(key(jf, rc), writeValue(value));
        }
        return out;
    }

    /** Every reason a component may be left out, exactly as {@link JsonField} states them. */
    private static boolean omit(JsonField jf, Object value, Map<String, Object> siblings,
            RecordComponent rc) {
        if (jf.exclude()) {
            return true;
        }
        if (jf.skipIfNull() && value == null) {
            return true;
        }
        if (jf.skipIfEmpty() && isEmpty(value)) {
            return true;
        }
        // skipUnless is deliberately NOT honoured. It says when a value may be left out
        // and never what it was, and the model's "inactive" values are populated records
        // rather than nulls — Animation.NONE.spin() is a full SpinConfig. Omitting on a
        // predicate would therefore lose the value with no way to put it back, which is
        // the one thing a format derived from the model in both directions must not do.
        // It remains useful for a human-facing writer; this is not one.
        if (!jf.skipIfEqualsConstant().isEmpty()) {
            Object constant = constant(rc.getType(), jf.skipIfEqualsConstant());
            if (constant != null && constant.equals(value)) {
                return true;
            }
        }
        if (!jf.skipIfEqualsField().isEmpty()
                && java.util.Objects.equals(value, siblings.get(jf.skipIfEqualsField()))) {
            return true;
        }
        return jf.skipIfDefault()
                && java.util.Objects.equals(value, defaultValue(rc.getType(), jf.defaultValue()));
    }

    private static Object readValue(Class<?> type, RecordComponent owner, JsonElement json,
            String source, JsonPath at) {
        if (json == null || json.isJsonNull()) {
            return null;
        }
        if (type == String.class) {
            return primitive(json, source, at).getAsString();
        }
        if (type == boolean.class || type == Boolean.class) {
            return primitive(json, source, at).getAsBoolean();
        }
        if (type == int.class || type == Integer.class) {
            return primitive(json, source, at).getAsInt();
        }
        if (type == long.class || type == Long.class) {
            return primitive(json, source, at).getAsLong();
        }
        if (type == float.class || type == Float.class) {
            return primitive(json, source, at).getAsFloat();
        }
        if (type == double.class || type == Double.class) {
            return primitive(json, source, at).getAsDouble();
        }
        if (type.isEnum()) {
            String name = primitive(json, source, at).getAsString();
            for (Object constant : type.getEnumConstants()) {
                if (((Enum<?>) constant).name().equalsIgnoreCase(name)) {
                    return constant;
                }
            }
            throw new PackException(source, at.toString(),
                    "'" + name + "' is not one of " + names(type));
        }
        if (type == Vector3f.class) {
            JsonObject o = object(json, source, at);
            return new Vector3f(number(o, "x", source, at), number(o, "y", source, at),
                    number(o, "z", source, at));
        }
        if (type == List.class) {
            Class<?> element = implementationOf(listElement(owner, source, at));
            JsonArray array = array(json, source, at);
            List<Object> out = new ArrayList<>(array.size());
            for (int i = 0; i < array.size(); i++) {
                out.add(readValue(element, null, array.get(i), source, at.index(i)));
            }
            return List.copyOf(out);
        }
        if (type.isRecord()) {
            return readRecord(type, object(json, source, at), source, at);
        }
        throw new PackException(source, at.toString(),
                "no way to read a " + type.getSimpleName());
    }

    private static Object readRecord(Class<?> type, JsonObject json, String source, JsonPath at) {
        RecordComponent[] components = type.getRecordComponents();
        Object[] values = new Object[components.length];
        Map<String, Object> byName = new LinkedHashMap<>();

        for (int pass = 0; pass < 2; pass++) {          // second pass resolves skipIfEqualsField
            for (int i = 0; i < components.length; i++) {
                RecordComponent rc = components[i];
                JsonField jf = annotation(type, rc);
                JsonElement value = lookUp(json, jf, rc);
                Class<?> target = concreteType(type, rc, json, source, at);
                values[i] = value != null
                        ? readValue(target, rc, value, source, at.field(key(jf, rc)))
                        : absent(type, rc, jf, byName, source, at);
                byName.put(rc.getName(), values[i]);
            }
        }
        try {
            Class<?>[] parameters = new Class<?>[components.length];
            for (int i = 0; i < components.length; i++) {
                parameters[i] = components[i].getType();
            }
            Constructor<?> canonical = type.getDeclaredConstructor(parameters);
            canonical.setAccessible(true);
            return canonical.newInstance(values);
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause() == null ? e : e.getCause();
            throw new PackException(source, at.toString(), cause.getMessage(), cause);
        } catch (ReflectiveOperationException e) {
            throw new PackException(source, at.toString(),
                    "could not build a " + type.getSimpleName() + ": " + e, e);
        }
    }

    /** What a component holds when the file left it out, per the reason it was left out. */
    private static Object absent(Class<?> owner, RecordComponent rc, JsonField jf,
            Map<String, Object> siblings, String source, JsonPath at) {
        if (jf == null) {
            throw new PackException(source, at.toString(), "missing '" + rc.getName() + "'");
        }
        if (!jf.skipIfEqualsConstant().isEmpty()) {
            Object constant = constant(rc.getType(), jf.skipIfEqualsConstant());
            if (constant != null) {
                return constant;
            }
        }
        if (!jf.skipIfEqualsField().isEmpty() && siblings.containsKey(jf.skipIfEqualsField())) {
            return siblings.get(jf.skipIfEqualsField());
        }
        if (jf.skipIfDefault()) {
            return defaultValue(rc.getType(), jf.defaultValue());
        }
        if (jf.skipIfEmpty()) {
            return rc.getType() == List.class ? List.of() : rc.getType() == String.class ? "" : null;
        }
        if (jf.skipIfNull() || jf.exclude()) {
            return zero(rc.getType());
        }
        throw new PackException(source, at.toString(),
                "missing '" + key(jf, rc) + "', and nothing says it may be omitted");
    }

    // ── the one polymorphic point ────────────────────────────────────────────

    /**
     * A {@code Shape} component is read as the class its sibling {@code type} names; a
     * {@code Primitive} as the one implementation there is.
     */
    private static Class<?> concreteType(Class<?> owner, RecordComponent rc, JsonObject json,
            String source, JsonPath at) {
        if (rc.getType() == Shape.class) {
            JsonElement type = json.get("type");
            if (type == null || !type.isJsonPrimitive()) {
                throw new PackException(source, at.toString(),
                        "a shape needs a sibling 'type' saying which one it is");
            }
            String name = type.getAsString().toLowerCase(Locale.ROOT);
            Class<? extends Shape> found = SHAPE_CLASSES.get(name);
            if (found == null) {
                throw new PackException(source, at.field("type").toString(),
                        "'" + name + "' is not a shape type. Known: " + SHAPE_CLASSES.keySet());
            }
            return found;
        }
        return implementationOf(rc.getType());
    }

    /**
     * The class to build for a declared type that cannot be built directly.
     *
     * <p>{@code Primitive} has exactly one implementation, so it needs no discriminator in
     * the file — but it does need resolving in both the places a primitive appears: as a
     * component, and as an element of a layer's list. Doing it here rather than at each
     * call site is what the first version of this got wrong.
     */
    private static Class<?> implementationOf(Class<?> declared) {
        return declared == Primitive.class ? SimplePrimitive.class : declared;
    }

    /**
     * Shape type name to class, built by asking the registry for one of each and reading
     * back what it calls itself. Several registry names share a class — every platonic
     * solid is a {@code PolyhedronShape} calling itself {@code polyhedron} — so this maps
     * what {@link Shape#getType()} writes rather than what the registry is keyed by.
     */
    private static final Map<String, Class<? extends Shape>> SHAPE_CLASSES = shapeClasses();

    private static Map<String, Class<? extends Shape>> shapeClasses() {
        Map<String, Class<? extends Shape>> byType = new LinkedHashMap<>();
        for (String name : ShapeRegistry.names()) {
            Shape one = ShapeRegistry.create(name, Map.of());
            if (one != null) {
                byType.put(one.getType().toLowerCase(Locale.ROOT), one.getClass());
            }
        }
        return Map.copyOf(byType);
    }

    // ── directive plumbing ───────────────────────────────────────────────────

    private static JsonField annotation(Class<?> owner, RecordComponent rc) {
        JsonField onComponent = rc.getAnnotation(JsonField.class);
        if (onComponent != null) {
            return onComponent;
        }
        try {
            return owner.getDeclaredField(rc.getName()).getAnnotation(JsonField.class);
        } catch (NoSuchFieldException e) {
            return null;
        }
    }

    private static String key(JsonField jf, RecordComponent rc) {
        return jf == null || jf.name().isEmpty() ? rc.getName() : jf.name();
    }

    private static JsonElement lookUp(JsonObject json, JsonField jf, RecordComponent rc) {
        JsonElement direct = json.get(key(jf, rc));
        if (direct != null) {
            return direct;
        }
        if (jf != null) {
            for (String alias : jf.aliases()) {
                JsonElement byAlias = json.get(alias);
                if (byAlias != null) {
                    return byAlias;
                }
            }
        }
        return null;
    }

    /** Bare name on the field's type, or {@code Type.CONSTANT} in that type's package. */
    private static Object constant(Class<?> type, String reference) {
        int dot = reference.lastIndexOf('.');
        Class<?> holder = type;
        String member = reference;
        if (dot >= 0) {
            if (type.getPackage() == null) {
                return null;
            }
            try {
                holder = Class.forName(
                        type.getPackage().getName() + "." + reference.substring(0, dot));
            } catch (ClassNotFoundException e) {
                return null;
            }
            member = reference.substring(dot + 1);
        }
        try {
            java.lang.reflect.Field f = holder.getField(member);
            return Modifier.isStatic(f.getModifiers()) ? f.get(null) : null;
        } catch (ReflectiveOperationException e) {
            return null;
        }
    }

    private static boolean predicateHolds(Object value, String method) {
        if (value == null) {
            return false;
        }
        try {
            Method m = value.getClass().getMethod(method);
            return Boolean.TRUE.equals(m.invoke(value));
        } catch (ReflectiveOperationException e) {
            return false;
        }
    }

    private static Object defaultValue(Class<?> type, String literal) {
        if (literal.isEmpty()) {
            return zero(type);
        }
        if (type == float.class || type == Float.class) {
            return Float.parseFloat(literal);
        }
        if (type == double.class || type == Double.class) {
            return Double.parseDouble(literal);
        }
        if (type == int.class || type == Integer.class) {
            return Integer.parseInt(literal);
        }
        if (type == long.class || type == Long.class) {
            return Long.parseLong(literal);
        }
        if (type == boolean.class || type == Boolean.class) {
            return Boolean.parseBoolean(literal);
        }
        if (type.isEnum()) {
            for (Object constant : type.getEnumConstants()) {
                if (((Enum<?>) constant).name().equalsIgnoreCase(literal)) {
                    return constant;
                }
            }
        }
        return literal;
    }

    private static Object zero(Class<?> type) {
        if (!type.isPrimitive()) {
            return null;
        }
        if (type == boolean.class) {
            return false;
        }
        if (type == float.class) {
            return 0f;
        }
        if (type == double.class) {
            return 0d;
        }
        if (type == long.class) {
            return 0L;
        }
        if (type == char.class) {
            return '\0';
        }
        return 0;
    }

    private static boolean isEmpty(Object value) {
        if (value == null) {
            return true;
        }
        if (value instanceof Collection<?> c) {
            return c.isEmpty();
        }
        if (value instanceof Map<?, ?> m) {
            return m.isEmpty();
        }
        if (value instanceof CharSequence s) {
            return s.isEmpty();
        }
        return value.getClass().isArray() && Array.getLength(value) == 0;
    }

    /**
     * The component's own value, read from the field rather than the accessor.
     *
     * <p>An accessor may be overridden to present something other than what the
     * component holds — {@code Transform.offset()} returns a zero vector where the
     * component is null, documented as "never null". That is helpful to a caller and
     * wrong for a codec: writing the accessor's answer emits a value the canonical
     * constructor was never given, and reading it back produces a record that is not
     * equal to the one written. The field is what the constructor took, so the field is
     * what round-trips.
     */
    private static Object read(Object record, RecordComponent rc) {
        try {
            java.lang.reflect.Field field = record.getClass().getDeclaredField(rc.getName());
            field.setAccessible(true);
            return field.get(record);
        } catch (ReflectiveOperationException viaField) {
            try {
                Method accessor = rc.getAccessor();
                accessor.setAccessible(true);
                return accessor.invoke(record);
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException("could not read " + rc.getName(), e);
            }
        }
    }

    private static Class<?> listElement(RecordComponent owner, String source, JsonPath at) {
        if (owner != null
                && owner.getGenericType() instanceof java.lang.reflect.ParameterizedType p
                && p.getActualTypeArguments().length == 1
                && p.getActualTypeArguments()[0] instanceof Class<?> c) {
            return c;
        }
        throw new PackException(source, at.toString(), "a list here has no element type");
    }

    private static List<String> names(Class<?> enumType) {
        List<String> out = new ArrayList<>();
        for (Object constant : enumType.getEnumConstants()) {
            out.add(((Enum<?>) constant).name().toLowerCase(Locale.ROOT));
        }
        return out;
    }

    // ── shared shapes of failure ─────────────────────────────────────────────

    private static JsonObject object(JsonElement e, String source, JsonPath at) {
        if (!e.isJsonObject()) {
            throw new PackException(source, at.toString(), "expected an object");
        }
        return e.getAsJsonObject();
    }

    private static JsonArray array(JsonElement e, String source, JsonPath at) {
        if (!e.isJsonArray()) {
            throw new PackException(source, at.toString(), "expected an array");
        }
        return e.getAsJsonArray();
    }

    private static JsonPrimitive primitive(JsonElement e, String source, JsonPath at) {
        if (!e.isJsonPrimitive()) {
            throw new PackException(source, at.toString(), "expected a value");
        }
        return e.getAsJsonPrimitive();
    }

    private static float number(JsonObject o, String field, String source, JsonPath at) {
        JsonElement e = o.get(field);
        if (e == null || !e.isJsonPrimitive()) {
            throw new PackException(source, at.field(field).toString(), "missing '" + field + "'");
        }
        return e.getAsFloat();
    }
}
