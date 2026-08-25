package net.cyberpunk042.mcshaders;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * What a consumer of {@code mcshaders-api} can actually compile against.
 *
 * <p>This module is published as the artifact other mods build on, and the
 * {@code api} / {@code implementation} split in its build script decides what reaches
 * them: an {@code api} dependency lands in the POM at compile scope, an
 * {@code implementation} one at runtime scope. So a type that appears in a public
 * signature while its dependency is {@code implementation} is unreachable — the
 * consumer sees the method and cannot call it.
 *
 * <p>That was the state until this test existed. Three methods —
 * {@code FieldCodec.write}, {@code BindingCodec.write}, {@code BindingCodec.writeAll} —
 * return {@code JsonObject} or {@code JsonArray}, while gson was declared
 * {@code implementation} on the stated reasoning that parsing is "not something
 * consumers compile against". Compiling the example on line 613 of
 * {@code USING_AS_A_LIBRARY.md} against only what the artifact exposes fails with
 * {@code cannot access JsonObject}; adding gson to that classpath compiles it. The
 * declaration is now {@code api}, and this keeps the two in step.
 *
 * <p>Nothing here needs the game or the network: it reads this module's own sources to
 * find its public types, then asks the classes themselves what they expose.
 */
class ApiSurfaceTest {

    private static final Path SOURCES = Path.of("src/main/java");

    /**
     * Packages a consumer is entitled to see without declaring anything of their own.
     *
     * <p>The JDK, this project, and every dependency declared {@code api}. Anything
     * else appearing in a public signature is a leak.
     */
    private static boolean reachableByConsumers(String packageName) {
        return packageName.startsWith("java.")
                || packageName.startsWith("javax.")
                || packageName.startsWith("net.cyberpunk042.")
                || packageName.equals("com.google.gson");
    }

    /**
     * This module's directory, from wherever the test was launched.
     *
     * <p>Gradle runs tests with the project directory as the working directory, so the
     * first candidate hits. Run from the repository root instead — which is how the
     * javac harness in this repo does it — and {@code common/} is where to look.
     */
    private static Path moduleRoot() {
        for (Path dir = Path.of("").toAbsolutePath(); dir != null; dir = dir.getParent()) {
            for (Path candidate : List.of(dir, dir.resolve("common"))) {
                if (Files.isDirectory(candidate.resolve(SOURCES))
                        && Files.isRegularFile(candidate.resolve("build.gradle.kts"))) {
                    return candidate;
                }
            }
        }
        throw new AssertionError("could not find the common module root from "
                + Path.of("").toAbsolutePath());
    }

    private static List<Class<?>> publicTypes(Path root) {
        Path sources = root.resolve(SOURCES);
        List<Class<?>> types = new ArrayList<>();
        try (Stream<Path> walk = Files.walk(sources)) {
            for (Path file : walk.filter(p -> p.toString().endsWith(".java")).sorted().toList()) {
                String name = sources.relativize(file).toString()
                        .replace(".java", "").replace(java.io.File.separatorChar, '.');
                try {
                    Class<?> type = Class.forName(name, false,
                            ApiSurfaceTest.class.getClassLoader());
                    if (Modifier.isPublic(type.getModifiers())) {
                        types.add(type);
                    }
                } catch (Throwable ignored) {
                    // package-info and anything not on the classpath: not public API.
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return types;
    }

    /** Every type named by a public or protected member of {@code type}. */
    private static List<Class<?>> exposedBy(Class<?> type) {
        List<Class<?>> refs = new ArrayList<>();
        for (Method method : type.getDeclaredMethods()) {
            if (isVisible(method.getModifiers())) {
                refs.add(method.getReturnType());
                refs.addAll(List.of(method.getParameterTypes()));
            }
        }
        for (Constructor<?> constructor : type.getDeclaredConstructors()) {
            if (isVisible(constructor.getModifiers())) {
                refs.addAll(List.of(constructor.getParameterTypes()));
            }
        }
        for (Field field : type.getDeclaredFields()) {
            if (isVisible(field.getModifiers())) {
                refs.add(field.getType());
            }
        }
        return refs;
    }

    private static boolean isVisible(int modifiers) {
        return Modifier.isPublic(modifiers) || Modifier.isProtected(modifiers);
    }

    @Test
    @DisplayName("nothing in the public API is a type consumers cannot reach")
    void noTypeLeaksPastTheApiDeclaration() {
        Path root = moduleRoot();
        List<Class<?>> types = publicTypes(root);

        // A scan that found nothing would agree with every assertion below.
        assertFalse(types.isEmpty(), "no public types found — the scan is not looking at "
                + root.resolve(SOURCES));

        Set<String> leaks = new TreeSet<>();
        for (Class<?> type : types) {
            for (Class<?> referenced : exposedBy(type)) {
                Class<?> component = referenced;
                while (component.isArray()) {
                    component = component.getComponentType();
                }
                if (component.isPrimitive()) {
                    continue;
                }
                if (!reachableByConsumers(component.getPackageName())) {
                    leaks.add(component.getName() + " (via " + type.getSimpleName() + ")");
                }
            }
        }

        assertTrue(leaks.isEmpty(),
                () -> "these appear in mcshaders-api's public API but are not reachable by "
                        + "a consumer compiling against it — either expose the dependency "
                        + "with `api(...)` or keep the type out of the signature:\n  "
                        + String.join("\n  ", leaks));
    }

    @Test
    @DisplayName("gson is exposed as api, since the public API hands its types back")
    void gsonIsDeclaredApi() {
        String build = read(moduleRoot().resolve("build.gradle.kts"));

        assertTrue(build.contains("api(\"com.google.code.gson:gson:"),
                "FieldCodec.write and BindingCodec.write return gson types, so gson has to "
                        + "reach consumers at compile scope. Under `implementation` it lands "
                        + "in the POM at runtime scope and the documented example does not "
                        + "compile.");
        assertFalse(build.contains("implementation(\"com.google.code.gson:gson:"),
                "declaring gson both ways would leave which scope wins to Gradle rather "
                        + "than to this file");
    }

    @Test
    @DisplayName("the leak this test was written for is the one it would still catch")
    void theCodecReturnTypesAreStillTheReason() {
        Set<String> gsonReturning = new LinkedHashSet<>();
        for (Class<?> type : publicTypes(moduleRoot())) {
            for (Method method : type.getDeclaredMethods()) {
                if (isVisible(method.getModifiers())
                        && method.getReturnType().getPackageName().equals("com.google.gson")) {
                    gsonReturning.add(type.getSimpleName() + "." + method.getName());
                }
            }
        }

        // If this ever empties, gson stopped being part of the API and the `api`
        // declaration above is no longer carrying its weight.
        assertEquals(Set.of("FieldCodec.write", "BindingCodec.write", "BindingCodec.writeAll"),
                gsonReturning,
                "the set of public methods returning a gson type changed; the build script's "
                        + "reasoning names exactly these three");
    }

    private static String read(Path file) {
        try {
            return Files.readString(file);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
