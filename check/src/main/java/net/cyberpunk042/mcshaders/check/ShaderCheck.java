package net.cyberpunk042.mcshaders.check;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import net.cyberpunk042.mcshaders.core.chain.ChainProblem;
import net.cyberpunk042.mcshaders.core.chain.ChainValidator;
import net.cyberpunk042.mcshaders.core.chain.Pass;
import net.cyberpunk042.mcshaders.core.chain.PostChain;
import net.cyberpunk042.mcshaders.core.layout.LayoutMismatch;

/**
 * Checks every post-effect chain in an unpacked {@code assets/} tree.
 *
 * <pre>{@code
 * mcshaders-check <assets-dir> [--quiet]
 * }</pre>
 *
 * <p>Exits 1 if anything is an error, so it can gate a build. Warnings and notes
 * are printed and do not fail.
 *
 * <p>Nothing here needs a GPU or the game. What a GPU would add is whether the
 * GLSL compiles; what this covers is the rest, which in practice is where chains
 * actually break — a shader that moved, a target read before it was written, a
 * uniform block that two declarations disagree about.
 */
public final class ShaderCheck {

    /**
     * Targets the host supplies rather than the chain declaring them. Reads of
     * these are legitimate; without the set every chain would look like it reads an
     * undeclared target on its first pass.
     */
    private static final Set<String> HOST_TARGETS = Set.of("minecraft:main");

    private final ResourceTree tree;
    private final ChainValidator validator;
    private final boolean quiet;

    ShaderCheck(ResourceTree tree, boolean quiet) {
        this.tree = tree;
        this.validator = new ChainValidator(tree, HOST_TARGETS);
        this.quiet = quiet;
    }

    public static void main(String[] args) throws IOException {
        if (args.length < 1) {
            System.err.println("usage: mcshaders-check <assets-dir> [--quiet]");
            System.exit(2);
        }
        Path assets = Path.of(args[0]);
        if (!Files.isDirectory(assets)) {
            System.err.println("not a directory: " + assets);
            System.exit(2);
        }
        boolean quiet = List.of(args).contains("--quiet");
        System.exit(new ShaderCheck(new ResourceTree(assets), quiet).run());
    }

    /** @return the process exit code */
    int run() throws IOException {
        List<Path> chains = tree.chains();
        int sound = 0;
        int withErrors = 0;

        for (Path file : chains) {
            String name = file.getFileName().toString().replaceFirst("\\.json$", "");
            List<ChainProblem> problems;
            try (Reader reader = Files.newBufferedReader(file)) {
                PostChain chain = PostChainCodec.read(reader);
                problems = keepReportable(chain, validator.validate(chain));
            } catch (RuntimeException e) {
                System.out.println("### " + name);
                System.out.println("    UNREADABLE: " + e.getMessage());
                withErrors++;
                continue;
            }

            boolean failed = problems.stream().anyMatch(ChainProblem::isError);
            if (failed) {
                withErrors++;
            } else {
                sound++;
            }
            if (problems.isEmpty() || (quiet && !failed)) {
                continue;
            }
            System.out.println("### " + name);
            // Several passes in a chain routinely share one include, and so report the
            // same finding. Print each once: a repeated line is not more evidence.
            for (String line : distinct(problems)) {
                System.out.println(line);
            }
        }

        System.out.printf("%n%d chain(s): %d sound, %d with errors%n", chains.size(), sound, withErrors);
        return withErrors == 0 ? 0 : 1;
    }

    /**
     * Drops findings that are artefacts of checking one mod's tree in isolation.
     *
     * <p>A chain naming {@code minecraft:post/blit} is not broken because the game's
     * own shaders are not in this directory. Reporting them would bury the real
     * findings under one per pass, which is how a checker gets ignored.
     */
    private List<ChainProblem> keepReportable(PostChain chain, List<ChainProblem> problems) {
        Set<String> external = new LinkedHashSet<>();
        for (Pass pass : chain.passes()) {
            for (String shader : List.of(pass.vertexShader(), pass.fragmentShader())) {
                if (tree.isExternal(shader)) {
                    external.add(shader);
                }
            }
        }
        List<ChainProblem> out = new ArrayList<>();
        for (ChainProblem p : problems) {
            boolean aboutExternalShader = p.kind() == ChainProblem.Kind.MISSING_SHADER
                    && external.stream().anyMatch(id -> p.detail().contains("'" + id + "'"));
            if (!aboutExternalShader) {
                out.add(p);
            }
        }
        return out;
    }

    private List<String> distinct(List<ChainProblem> problems) {
        Set<String> seen = new LinkedHashSet<>();
        for (ChainProblem p : problems) {
            if (quiet && p.severity() == LayoutMismatch.Severity.INFO) {
                continue;
            }
            seen.add("    " + p.severity() + " " + p.kind() + ": " + p.detail());
        }
        return List.copyOf(seen);
    }
}
