package net.cyberpunk042.mcshaders.check;

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
 * What a consumer of {@code mcshaders-check} can actually compile against.
 *
 * <p>The third module to get this test, and the one where it found the most. An
 * {@code api} dependency lands in the published POM at compile scope and an
 * {@code implementation} one at runtime scope, so a type in a public signature whose
 * dependency is {@code implementation} is a method the consumer can see and cannot
 * call.
 *
 * <p>Gson was {@code implementation} here on the same reasoning that had it that way
 * in {@code common} — parsing is this module's job, not the consumer's. But
 * {@code PostChainCodec.read(JsonObject)} is public, and that is worse than the three
 * leaking methods in {@code common} were: <strong>javac resolves the whole overload
 * set</strong>, so a consumer calling the gson-free-looking
 * {@code PostChainCodec.read(Reader)} fails with {@code cannot access JsonObject} too.
 * Both halves of this module's only entry point were uncompilable against its own POM.
 * Adding gson to that classpath compiles both. The declaration is now {@code api}.
 *
 * <p>The scan reads exception types as well as returns, parameters and fields — a
 * consumer catching a declared exception needs its class at compile scope like any
 * other. It finds nothing here today; it is included so that it cannot start to.
 *
 * <p>This module is also a command-line tool, and that use is unaffected either way:
 * the distribution carries its own dependencies. It is the {@code maven-publish} half
 * — the one that declares {@code api("net.cyberpunk042:mcshaders-core")} and so offers
 * itself as a library — that this is about.
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
                || packageName.startsWith("org.joml")
                || packageName.startsWith("com.google.gson");
    }

    /**
     * This module's directory, from wherever the test was launched.
     *
     * <p>Gradle runs tests with the project directory as the working directory, so the
     * first candidate hits. Run from the repository root and {@code check/} is where
     * to look.
     */
    private static Path moduleRoot() {
        for (Path dir = Path.of("").toAbsolutePath(); dir != null; dir = dir.getParent()) {
            for (Path candidate : List.of(dir, dir.resolve("check"))) {
                if (Files.isDirectory(candidate.resolve(SOURCES))
                        && Files.isRegularFile(candidate.resolve("build.gradle.kts"))
                        && Files.isDirectory(candidate.resolve(SOURCES)
                                .resolve("net/cyberpunk042/mcshaders/check"))) {
                    return candidate;
                }
            }
        }
        throw new AssertionError("could not find the check module root from "
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
                // A consumer catching a declared exception needs its class at compile
                // scope like any other. Two public members here declare one, which is
                // what theScanReadsThrowsClauses covers.
                refs.addAll(List.of(method.getExceptionTypes()));
            }
        }
        for (Constructor<?> constructor : type.getDeclaredConstructors()) {
            if (isVisible(constructor.getModifiers())) {
                refs.addAll(List.of(constructor.getParameterTypes()));
                refs.addAll(List.of(constructor.getExceptionTypes()));
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
                () -> "these appear in mcshaders-check's public API but are not reachable by "
                        + "a consumer compiling against it — either expose the dependency "
                        + "with `api(...)` or keep the type out of the signature:\n  "
                        + String.join("\n  ", leaks));
    }

    @Test
    @DisplayName("gson is exposed as api, since the entry point takes its types")
    void gsonIsDeclaredApi() {
        String build = read(moduleRoot().resolve("build.gradle.kts"));

        assertTrue(build.contains("api(\"com.google.code.gson:gson:"),
                "PostChainCodec.read takes a JsonObject, so gson has to reach consumers at "
                        + "compile scope. Under `implementation` it lands in the POM at "
                        + "runtime scope and neither read overload compiles.");
        assertFalse(build.contains("implementation(\"com.google.code.gson:gson:"),
                "declaring gson both ways would leave which scope wins to Gradle rather "
                        + "than to this file");
    }

    @Test
    @DisplayName("the leak this test was written for is the one it would still catch")
    void theEntryPointIsStillTheReason() {
        Set<String> gsonFacing = new LinkedHashSet<>();
        for (Class<?> type : publicTypes(moduleRoot())) {
            for (Method method : type.getDeclaredMethods()) {
                if (!isVisible(method.getModifiers())) {
                    continue;
                }
                List<Class<?>> named = new ArrayList<>();
                named.add(method.getReturnType());
                named.addAll(List.of(method.getParameterTypes()));
                named.addAll(List.of(method.getExceptionTypes()));
                if (named.stream().anyMatch(c -> c.getPackageName().startsWith("com.google.gson"))) {
                    gsonFacing.add(type.getSimpleName() + "." + method.getName());
                }
            }
        }

        // If this ever empties, gson stopped being part of the API and the `api`
        // declaration above is no longer carrying its weight.
        assertEquals(Set.of("PostChainCodec.read"), gsonFacing,
                "the set of public methods naming a gson type changed; the build script's "
                        + "reasoning names exactly this one");
    }

    @Test
    @DisplayName("the scan reads throws clauses, not only returns and parameters")
    void theScanReadsThrowsClauses() {
        // The exception half of the scan cannot be shown by a leak: each module here
        // has one external dependency and all of them are `api`, so there is no
        // unreachable type on the compile classpath to declare in a throws clause.
        // This shows the mechanism instead — delete the getExceptionTypes lines and
        // this fails, which is what makes the same two lines in core's and common's
        // copies mean something.
        assertTrue(exposedBy(PostChainCodec.class).contains(IOException.class),
                "PostChainCodec.read declares `throws IOException`, so the scan should "
                        + "have collected it");
    }

    private static String read(Path file) {
        try {
            return Files.readString(file);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
