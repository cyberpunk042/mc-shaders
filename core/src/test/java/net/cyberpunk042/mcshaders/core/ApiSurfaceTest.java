package net.cyberpunk042.mcshaders.core;

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
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * What a consumer of {@code mcshaders-core} can compile against, and what this module
 * promised never to touch.
 *
 * <p>{@code build.gradle.kts} states the rule this guards: JOML is "the one allowed
 * dependency class — never Minecraft, never a graphics API". The guide offers this
 * artifact for use "off Minecraft entirely", so that rule is the product, not a
 * preference.
 *
 * <p>Two different things are checked, because the compiler only covers one of them.
 *
 * <p><strong>The public surface.</strong> An {@code api} dependency reaches consumers at
 * compile scope; an {@code implementation} one does not. A type in a public signature
 * whose dependency is {@code implementation} is a method a consumer can see and cannot
 * call. {@code common} shipped exactly that — three codec methods returning gson while
 * gson was {@code implementation} — and this is the same check on the larger artifact.
 * JOML is declared {@code api} and is the only external package in 283 public types, so
 * core is correct today; nothing kept it that way.
 *
 * <p><strong>The forbidden imports.</strong> Today {@code javac} already rejects
 * {@code import net.minecraft.…} here, because Minecraft is not on this module's
 * classpath — so a test asserting it would merely restate the compiler. What the
 * compiler does <em>not</em> gate is somebody adding the dependency: the day that
 * happens, the rule stops being enforced by anything and internal use becomes invisible
 * to the surface scan above. This says no before that ships.
 */
class ApiSurfaceTest {

    private static final Path SOURCES = Path.of("src/main/java");

    /** JOML is the one allowed dependency class; see this module's build script. */
    private static boolean reachableByConsumers(String packageName) {
        return packageName.startsWith("java.")
                || packageName.startsWith("javax.")
                || packageName.startsWith("net.cyberpunk042.")
                || packageName.startsWith("org.joml");
    }

    /** What this module says it will never depend on, whatever is on the classpath. */
    private static final List<String> FORBIDDEN = List.of(
            "net.minecraft.", "com.mojang.", "org.lwjgl.");

    private static Path moduleRoot() {
        for (Path dir = Path.of("").toAbsolutePath(); dir != null; dir = dir.getParent()) {
            for (Path candidate : List.of(dir, dir.resolve("core"))) {
                if (Files.isDirectory(candidate.resolve(SOURCES))
                        && Files.isRegularFile(candidate.resolve("build.gradle.kts"))) {
                    return candidate;
                }
            }
        }
        throw new AssertionError("could not find the core module root from "
                + Path.of("").toAbsolutePath());
    }

    private static List<Path> sourceFiles(Path root) {
        try (Stream<Path> walk = Files.walk(root.resolve(SOURCES))) {
            return walk.filter(p -> p.toString().endsWith(".java")).sorted().toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static boolean isVisible(int modifiers) {
        return Modifier.isPublic(modifiers) || Modifier.isProtected(modifiers);
    }

    @Test
    @DisplayName("nothing in the public API is a type consumers cannot reach")
    void noTypeLeaksPastTheApiDeclaration() {
        Path root = moduleRoot();
        Path sources = root.resolve(SOURCES);

        int scanned = 0;
        Set<String> leaks = new TreeSet<>();
        for (Path file : sourceFiles(root)) {
            String name = sources.relativize(file).toString()
                    .replace(".java", "").replace(java.io.File.separatorChar, '.');
            Class<?> type;
            try {
                type = Class.forName(name, false, ApiSurfaceTest.class.getClassLoader());
            } catch (Throwable notAType) {
                continue;
            }
            if (!Modifier.isPublic(type.getModifiers())) {
                continue;
            }
            scanned++;

            List<Class<?>> referenced = new ArrayList<>();
            try {
                for (Method method : type.getDeclaredMethods()) {
                    if (isVisible(method.getModifiers())) {
                        referenced.add(method.getReturnType());
                        referenced.addAll(List.of(method.getParameterTypes()));
                        // A consumer catching a declared exception needs its class
                        // at compile scope like any other. No public member in this
                        // module declares one, so this is a guard rather than a
                        // checked path; the mechanism itself is exercised by check's
                        // copy of this test, where two do.
                        referenced.addAll(List.of(method.getExceptionTypes()));
                    }
                }
                for (Constructor<?> constructor : type.getDeclaredConstructors()) {
                    if (isVisible(constructor.getModifiers())) {
                        referenced.addAll(List.of(constructor.getParameterTypes()));
                        referenced.addAll(List.of(constructor.getExceptionTypes()));
                    }
                }
                for (Field field : type.getDeclaredFields()) {
                    if (isVisible(field.getModifiers())) {
                        referenced.add(field.getType());
                    }
                }
            } catch (Throwable unresolvable) {
                continue;
            }

            for (Class<?> reference : referenced) {
                Class<?> component = reference;
                while (component.isArray()) {
                    component = component.getComponentType();
                }
                if (!component.isPrimitive()
                        && !reachableByConsumers(component.getPackageName())) {
                    leaks.add(component.getName() + " (via " + type.getSimpleName() + ")");
                }
            }
        }

        // A scan that found nothing would satisfy the assertion below.
        assertTrue(scanned > 0, () -> "no public types found under " + sources);
        assertTrue(leaks.isEmpty(),
                () -> "these appear in mcshaders-core's public API but are not reachable by "
                        + "a consumer compiling against it — either expose the dependency "
                        + "with `api(...)` or keep the type out of the signature:\n  "
                        + String.join("\n  ", leaks));
    }

    @Test
    @DisplayName("JOML is exposed as api, since the public API is full of its types")
    void jomlIsDeclaredApi() {
        String build = read(moduleRoot().resolve("build.gradle.kts"));

        assertTrue(build.contains("api(\"org.joml:joml:"),
                "Vector3f appears throughout this module's public signatures, so JOML has "
                        + "to reach consumers at compile scope. Under `implementation` it "
                        + "lands in the published POM at runtime scope and none of those "
                        + "methods can be called.");
        assertFalse(build.contains("implementation(\"org.joml:joml:"),
                "declaring JOML both ways would leave the scope to Gradle rather than to "
                        + "this file");
    }

    @Test
    @DisplayName("nothing imports Minecraft or a graphics API, whatever the classpath allows")
    void theOneAllowedDependencyClassHolds() {
        Path root = moduleRoot();
        List<Path> files = sourceFiles(root);

        // Guards the guard: an empty file list agrees with everything below.
        assertFalse(files.isEmpty(), () -> "no sources found under " + root.resolve(SOURCES));

        List<String> violations = new ArrayList<>();
        for (Path file : files) {
            for (String line : read(file).split("\n", -1)) {
                String trimmed = line.strip();
                if (!trimmed.startsWith("import ")) {
                    continue;
                }
                for (String forbidden : FORBIDDEN) {
                    if (trimmed.contains(forbidden)) {
                        violations.add(root.relativize(file) + ": " + trimmed);
                    }
                }
            }
        }

        assertTrue(violations.isEmpty(),
                () -> "this module's build script calls JOML \"the one allowed dependency "
                        + "class — never Minecraft, never a graphics API\", and the guide "
                        + "offers this artifact for use off Minecraft entirely:\n  "
                        + String.join("\n  ", violations));
    }

    private static String read(Path file) {
        try {
            return Files.readString(file);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
