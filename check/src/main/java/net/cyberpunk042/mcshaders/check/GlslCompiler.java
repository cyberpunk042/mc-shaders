package net.cyberpunk042.mcshaders.check;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Compiles flattened GLSL with Khronos' reference validator, when it is available.
 *
 * <p>Everything else this module does is answerable from text. This is the one
 * check that needs a compiler, and it turns out not to need a GPU: {@code
 * glslangValidator} parses and type-checks a shader without a context or a driver.
 *
 * <p>It is optional on purpose. The checker is otherwise pure Java with no native
 * dependency, and demanding an external binary to run any of it would make it
 * harder to adopt than the problems it finds are worth. Present, it adds a real
 * check; absent, the rest still runs.
 *
 * <h2>What a pass does and does not mean</h2>
 *
 * <p>glslang is the reference front end, not the driver that will actually run the
 * shader. A pass means the source is valid GLSL — the overwhelming majority of what
 * goes wrong — not that a particular driver will accept it, and certainly not that
 * it draws what was intended. Those remain a GPU's business.
 */
public final class GlslCompiler {

    private static final String EXECUTABLE = "glslangValidator";

    /** Minecraft's own include directive, which glslang does not know. */
    private static final String MOJANG_IMPORT = "#moj_import";

    private final boolean available;

    public GlslCompiler() {
        this.available = probe();
    }

    /** Whether the validator is on the path. */
    public boolean isAvailable() {
        return available;
    }

    /**
     * Compiles {@code source}, already flattened.
     *
     * @param stage {@code frag} or {@code vert}
     * @return the compiler's errors; empty if it compiled, and empty if the
     *         validator is unavailable or the source is not something it can read
     */
    public List<String> compile(String source, String stage) {
        if (!available || !canRead(source)) {
            return List.of();
        }
        Path file = null;
        try {
            file = Files.createTempFile("mcshaders-", "." + stage);
            Files.writeString(file, liftVersion(source));
            Process process = new ProcessBuilder(EXECUTABLE, "-S", stage, file.toString())
                    .redirectErrorStream(true)
                    .start();
            String output = new String(process.getInputStream().readAllBytes());
            int status = process.waitFor();
            if (status == 0) {
                return List.of();
            }
            List<String> errors = new ArrayList<>();
            for (String line : output.split("\n")) {
                if (line.startsWith("ERROR:") && !line.contains("compilation errors")) {
                    errors.add(line.trim());
                }
            }
            return List.copyOf(errors);
        } catch (IOException e) {
            throw new UncheckedIOException("could not run " + EXECUTABLE, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return List.of();
        } finally {
            deleteQuietly(file);
        }
    }

    /**
     * Whether this is source glslang can read at all.
     *
     * <p>Minecraft's core shaders use {@code #moj_import}, which the game's own
     * loader expands and glslang rejects as an unknown directive. Reporting that as
     * a compile error would be blaming the shader for the checker's gap.
     */
    static boolean canRead(String source) {
        return !source.contains(MOJANG_IMPORT);
    }

    /**
     * Moves {@code #version} to the first line.
     *
     * <p>Flattening an include graph can leave the directive behind whatever was
     * pulled in ahead of it, and GLSL requires it first. The game's loader does the
     * same thing, so this is matching its behaviour rather than being lenient.
     */
    static String liftVersion(String source) {
        List<String> version = new ArrayList<>();
        List<String> rest = new ArrayList<>();
        for (String line : source.split("\n", -1)) {
            if (version.isEmpty() && line.strip().startsWith("#version")) {
                version.add(line);
            } else {
                rest.add(line);
            }
        }
        if (version.isEmpty()) {
            return source;
        }
        return version.get(0) + "\n" + String.join("\n", rest);
    }

    private static boolean probe() {
        try {
            Process process = new ProcessBuilder(EXECUTABLE, "--version")
                    .redirectErrorStream(true)
                    .start();
            process.getInputStream().readAllBytes();
            return process.waitFor() == 0;
        } catch (IOException e) {
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private static void deleteQuietly(Path file) {
        if (file == null) {
            return;
        }
        try {
            Files.deleteIfExists(file);
        } catch (IOException ignored) {
            // A leftover temp file is not worth failing a check over.
        }
    }

    /** The validator's version string, for reporting what did the checking. */
    public Optional<String> version() {
        if (!available) {
            return Optional.empty();
        }
        try {
            Process process = new ProcessBuilder(EXECUTABLE, "--version")
                    .redirectErrorStream(true)
                    .start();
            String output = new String(process.getInputStream().readAllBytes());
            process.waitFor();
            return output.lines().findFirst().map(String::trim);
        } catch (IOException e) {
            return Optional.empty();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Optional.empty();
        }
    }
}
