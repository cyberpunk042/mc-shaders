package net.cyberpunk042.mcshaders.core;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import net.cyberpunk042.mcshaders.core.serial.JsonField;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@link JsonField} is a specification for a codec that does not exist yet.
 *
 * <p>It is applied several hundred times across the model and read by nothing: there is
 * no {@code JsonSerializer}, and no reflective consumer anywhere in any module. Every
 * directive on it is therefore a promise about behaviour that has never once been
 * executed, which is the most comfortable place in a codebase for a wrong one to sit.
 *
 * <p>These tests hold the annotations to the contract the annotation itself documents,
 * so the spec is known-good before anything is built on it — and so that adding an
 * annotation that cannot be honoured fails here rather than in whatever finally reads
 * them.
 */
class JsonFieldContractTest {

    /** Every annotated field in {@code core}, paired with the type that declares it. */
    private static List<Field> annotatedFields() {
        Path sources = repoRoot().resolve("core/src/main/java");
        List<Field> found = new ArrayList<>();
        try (Stream<Path> tree = Files.walk(sources)) {
            for (Path file : tree.filter(p -> p.toString().endsWith(".java")).toList()) {
                String name = sources.relativize(file).toString()
                        .replace(".java", "").replace('/', '.');
                if (name.endsWith("package-info")) {
                    continue;
                }
                Class<?> type;
                try {
                    type = Class.forName(name);
                } catch (Throwable notLoadable) {
                    continue;
                }
                for (Field f : type.getDeclaredFields()) {
                    if (f.isAnnotationPresent(JsonField.class)) {
                        found.add(f);
                    }
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return found;
    }

    @Test
    @DisplayName("the corpus of annotations is substantial, or these tests check nothing")
    void thereIsSomethingToCheck() {
        assertTrue(annotatedFields().size() > 250,
                "expected a large annotated corpus, found " + annotatedFields().size()
                        + " — has the annotation been renamed or removed?");
    }

    /**
     * {@code skipIfEqualsConstant} names either a constant on the field's own type, or a
     * {@code Type.CONSTANT} in that type's package. Anything else can never resolve, so
     * the skip silently never fires and the default is written out anyway.
     */
    @Test
    @DisplayName("every skipIfEqualsConstant names a constant that exists")
    void constantsResolve() {
        List<String> unresolved = new ArrayList<>();
        for (Field f : annotatedFields()) {
            String constant = f.getAnnotation(JsonField.class).skipIfEqualsConstant();
            if (constant.isEmpty()) {
                continue;
            }
            if (!resolvesConstant(f.getType(), constant)) {
                unresolved.add(f.getDeclaringClass().getSimpleName() + "." + f.getName()
                        + ": '" + constant + "' is not a constant on "
                        + f.getType().getSimpleName() + " or in its package");
            }
        }
        assertTrue(unresolved.isEmpty(), String.join("; ", unresolved));
    }

    /** {@code skipUnless} names a no-argument boolean method on the field's type. */
    @Test
    @DisplayName("every skipUnless names a no-argument boolean method on the field's type")
    void predicatesResolve() {
        List<String> unresolved = new ArrayList<>();
        for (Field f : annotatedFields()) {
            String predicate = f.getAnnotation(JsonField.class).skipUnless();
            if (predicate.isEmpty()) {
                continue;
            }
            boolean ok;
            try {
                Method m = f.getType().getMethod(predicate);
                ok = m.getReturnType() == boolean.class || m.getReturnType() == Boolean.class;
            } catch (NoSuchMethodException e) {
                ok = false;
            }
            if (!ok) {
                unresolved.add(f.getDeclaringClass().getSimpleName() + "." + f.getName()
                        + ": " + f.getType().getSimpleName() + " has no boolean "
                        + predicate + "()");
            }
        }
        assertTrue(unresolved.isEmpty(), String.join("; ", unresolved));
    }

    /**
     * Every {@code skipUnless} type can say what "left out" was, without guessing.
     *
     * <p>{@code skipUnless} is the one directive that records when a value may be
     * omitted and not what it was, which is why the codec does not honour it on the
     * way out. Reading a file a person wrote is the other direction, and needs an
     * answer for an absent key. This pins the answer the model already has: each such
     * type carries a {@code NONE}, and the predicate is false on it.
     *
     * <p>Nothing consumes that yet - see {@code docs/VIRUS-BLOCK-FIELD-STATE.md} for
     * the open question about which value an absent key should take. It is pinned now
     * because the choice depends on it, and a type added later without a {@code NONE}
     * would remove an option without anyone noticing.
     */
    @Test
    @DisplayName("every skipUnless type has a NONE its predicate calls inactive")
    void skipUnlessTypesHaveAnInactiveConstant() {
        List<String> problems = new ArrayList<>();
        int checked = 0;
        for (Field f : annotatedFields()) {
            String predicate = f.getAnnotation(JsonField.class).skipUnless();
            if (predicate.isEmpty()) {
                continue;
            }
            Class<?> type = f.getType();
            String where = f.getDeclaringClass().getSimpleName() + "." + f.getName()
                    + " (" + type.getSimpleName() + ")";
            try {
                Field none = type.getDeclaredField("NONE");
                none.setAccessible(true);
                Object inactive = none.get(null);
                if (inactive == null) {
                    problems.add(where + ": NONE is null");
                    continue;
                }
                if ((Boolean) type.getMethod(predicate).invoke(inactive)) {
                    problems.add(where + ": NONE." + predicate + "() is true, so it is "
                            + "not the value an omitted key means");
                    continue;
                }
                checked++;
            } catch (NoSuchFieldException e) {
                problems.add(where + ": no NONE constant, so an absent key has no value");
            } catch (ReflectiveOperationException e) {
                problems.add(where + ": " + e);
            }
        }
        assertTrue(problems.isEmpty(), String.join("; ", problems));
        assertTrue(checked > 0, "no skipUnless directives found, so this checked nothing");
    }

    /** {@code skipIfEqualsField} names another field on the same declaring type. */
    @Test
    @DisplayName("every skipIfEqualsField names a sibling field")
    void siblingFieldsResolve() {
        List<String> unresolved = new ArrayList<>();
        for (Field f : annotatedFields()) {
            String sibling = f.getAnnotation(JsonField.class).skipIfEqualsField();
            if (sibling.isEmpty()) {
                continue;
            }
            boolean ok = false;
            for (Field g : f.getDeclaringClass().getDeclaredFields()) {
                ok |= g.getName().equals(sibling);
            }
            if (!ok) {
                unresolved.add(f.getDeclaringClass().getSimpleName() + "." + f.getName()
                        + ": no sibling field '" + sibling + "'");
            }
        }
        assertTrue(unresolved.isEmpty(), String.join("; ", unresolved));
    }

    /**
     * No two fields of one type may claim the same JSON key, by name or by alias — a
     * reader given the key would have no way to decide, and a writer would emit one of
     * them twice.
     */
    @Test
    @DisplayName("no two fields of a type claim the same JSON key")
    void keysAreUnambiguous() {
        Map<Class<?>, Map<String, String>> claimed = new HashMap<>();
        List<String> clashes = new ArrayList<>();
        for (Field f : annotatedFields()) {
            JsonField jf = f.getAnnotation(JsonField.class);
            Map<String, String> keys =
                    claimed.computeIfAbsent(f.getDeclaringClass(), k -> new HashMap<>());
            List<String> mine = new ArrayList<>();
            mine.add(jf.name().isEmpty() ? f.getName() : jf.name());
            mine.addAll(List.of(jf.aliases()));
            for (String key : mine) {
                String previous = keys.put(key, f.getName());
                if (previous != null && !previous.equals(f.getName())) {
                    clashes.add(f.getDeclaringClass().getSimpleName() + ": '" + key
                            + "' claimed by " + previous + " and " + f.getName());
                }
            }
        }
        assertTrue(clashes.isEmpty(), String.join("; ", clashes));
    }

    /** A field excluded from serialisation cannot also be given a key to serialise under. */
    @Test
    @DisplayName("exclude is not combined with directives that emit a key")
    void excludeIsNotContradicted() {
        List<String> contradictions = new ArrayList<>();
        for (Field f : annotatedFields()) {
            JsonField jf = f.getAnnotation(JsonField.class);
            if (jf.exclude() && (!jf.name().isEmpty() || jf.aliases().length > 0)) {
                contradictions.add(f.getDeclaringClass().getSimpleName() + "." + f.getName());
            }
        }
        assertTrue(contradictions.isEmpty(),
                "excluded yet also naming a key: " + contradictions);
    }

    // ── resolution, exactly as the annotation documents it ────────────────────

    private static boolean resolvesConstant(Class<?> fieldType, String constant) {
        int dot = constant.lastIndexOf('.');
        if (dot < 0) {
            return isStaticField(fieldType, constant);
        }
        // Qualified: Type.CONSTANT, resolved in the field type's own package.
        String simpleName = constant.substring(0, dot);
        String member = constant.substring(dot + 1);
        Package pkg = fieldType.getPackage();
        if (pkg == null) {
            return false;                       // primitives have no package, and no constants
        }
        try {
            return isStaticField(Class.forName(pkg.getName() + "." + simpleName), member);
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    private static boolean isStaticField(Class<?> type, String name) {
        try {
            return Modifier.isStatic(type.getField(name).getModifiers());
        } catch (NoSuchFieldException e) {
            return false;
        }
    }

    private static Path repoRoot() {
        for (Path dir = Path.of("").toAbsolutePath(); dir != null; dir = dir.getParent()) {
            if (Files.isRegularFile(dir.resolve("LICENSE"))
                    && Files.isDirectory(dir.resolve("core/src/main/java"))) {
                return dir;
            }
        }
        throw new AssertionError("could not find the repository root");
    }
}
