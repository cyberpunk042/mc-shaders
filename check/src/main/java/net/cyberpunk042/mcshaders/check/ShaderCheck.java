package net.cyberpunk042.mcshaders.check;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.cyberpunk042.mcshaders.core.chain.ChainProblem;
import net.cyberpunk042.mcshaders.core.chain.ChainValidator;
import net.cyberpunk042.mcshaders.core.chain.Pass;
import net.cyberpunk042.mcshaders.core.chain.PostChain;
import net.cyberpunk042.mcshaders.core.glsl.IncludeResolver;
import net.cyberpunk042.mcshaders.core.glsl.ResolvedShader;
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
    private final GlslCompiler compiler;
    private final boolean quiet;

    ShaderCheck(ResourceTree tree, boolean quiet) {
        this.tree = tree;
        this.validator = new ChainValidator(tree, HOST_TARGETS);
        this.compiler = new GlslCompiler();
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
        Set<String> reached = new LinkedHashSet<>();
        List<String> compileFailures = new ArrayList<>();
        int sound = 0;
        int withErrors = 0;
        // Tallied as they are printed, not as they are found, so the totals always
        // reconcile with the lines above them. In quiet mode the INFO lines are not
        // printed and so are not counted, which is the honest answer to "how many
        // findings does this report have".
        Map<LayoutMismatch.Severity, Integer> printed =
                new EnumMap<>(LayoutMismatch.Severity.class);

        for (Path file : chains) {
            String name = file.getFileName().toString().replaceFirst("\\.json$", "");
            List<ChainProblem> problems;
            try (Reader reader = Files.newBufferedReader(file)) {
                PostChain chain = PostChainCodec.read(reader);
                problems = keepReportable(chain, validator.validate(chain));
                reached.addAll(filesReachedBy(chain));
                compileFailures.addAll(compile(chain));
            } catch (RuntimeException e) {
                System.out.println("### " + name);
                System.out.println("    UNREADABLE: " + e.getMessage());
                printed.merge(LayoutMismatch.Severity.ERROR, 1, Integer::sum);
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
            for (ChainProblem problem : distinct(problems)) {
                System.out.println(render(problem));
                printed.merge(problem.severity(), 1, Integer::sum);
            }
        }

        reportOrphans(reached);
        reportCompilation(compileFailures);
        System.out.printf("%n%d chain(s): %d sound, %d with errors%n", chains.size(), sound, withErrors);
        reportTotals(printed);
        return withErrors == 0 && compileFailures.isEmpty() ? 0 : 1;
    }

    /**
     * Compiles each of a chain's shaders, if a validator is available.
     *
     * <p>This is the one check that needs a compiler rather than a reading of the
     * text — and, as it turns out, not a GPU.
     */
    private List<String> compile(PostChain chain) {
        if (!compiler.isAvailable()) {
            return List.of();
        }
        IncludeResolver resolver = new IncludeResolver(tree);
        List<String> failures = new ArrayList<>();
        for (Pass pass : chain.passes()) {
            for (String shader : List.of(pass.vertexShader(), pass.fragmentShader())) {
                if (tree.read(shader).isEmpty()) {
                    continue;
                }
                ResolvedShader resolved = resolver.resolve(shader);
                if (resolved.hasErrors()) {
                    // Its includes are already reported; compiling half a shader would
                    // report the same problem again wearing a compiler's words.
                    continue;
                }
                String stage = shader.endsWith(".vsh") ? "vert" : "frag";
                for (String error : compiler.compile(resolved.source(), stage)) {
                    failures.add(shader + ": " + error);
                }
            }
        }
        return failures;
    }

    private void reportCompilation(List<String> failures) {
        if (!compiler.isAvailable()) {
            System.out.println();
            System.out.println("GLSL was not compiled: glslangValidator is not on the path.");
            System.out.println("Install it (Debian/Ubuntu: glslang-tools) for a stronger check.");
            return;
        }
        if (failures.isEmpty()) {
            // Say so. Silence here would be indistinguishable from the check not
            // having run, which is the failure mode of every optional check.
            System.out.printf("%nGLSL compiled clean (%s).%n",
                    compiler.version().orElse("glslangValidator"));
            return;
        }
        System.out.printf("%n%d GLSL compile error(s):%n", failures.size());
        for (String failure : new LinkedHashSet<>(failures)) {
            System.out.println("    " + failure);
        }
    }

    /**
     * Every shader file a chain pulls in, directly or through an include.
     *
     * <p>The include resolver already records this: flattening a shader has to know
     * which file each line came from in order to emit {@code #line}, so the source
     * map it builds is exactly the reachable set.
     */
    private Set<String> filesReachedBy(PostChain chain) {
        Set<String> out = new LinkedHashSet<>();
        IncludeResolver resolver = new IncludeResolver(tree);
        for (Pass pass : chain.passes()) {
            for (String shader : List.of(pass.vertexShader(), pass.fragmentShader())) {
                if (tree.read(shader).isEmpty()) {
                    continue;
                }
                out.addAll(resolver.resolve(shader).sourceMap().paths());
            }
        }
        return out;
    }

    /**
     * Reports shader files no chain reaches.
     *
     * <p>Never an error. A pack may load shaders by means this does not model, and a
     * file kept deliberately — an archived variant, a work in progress — is not a
     * defect. It is worth saying, though: a chain naming a file that is not there
     * and a file sitting somewhere nothing names are usually the same event seen
     * from opposite ends.
     */
    private void reportOrphans(Set<String> reached) {
        // A chain names "ns:post/x"; the resolver records whatever path it opened.
        // Compare on the stem so the two forms meet.
        Set<String> reachedStems = new LinkedHashSet<>();
        for (String path : reached) {
            reachedStems.add(stem(path));
        }
        List<String> orphans = new ArrayList<>();
        for (String file : tree.shaderFiles()) {
            if (!reachedStems.contains(stem(file))) {
                orphans.add(file);
            }
        }
        if (orphans.isEmpty()) {
            return;
        }
        System.out.printf("%n%d shader file(s) no chain reaches. Some of these are expected:%n"
                + "Minecraft's core shaders are named directly rather than through a chain, and a%n"
                + "variant kept on purpose is not a defect. A file nothing names and a chain naming%n"
                + "a file that is not there are usually one event seen from opposite ends:%n",
                orphans.size());
        for (String orphan : orphans) {
            System.out.println("    " + orphan);
        }
    }

    /** A path reduced to what identifies it across the two spellings: no namespace, no extension. */
    private static String stem(String pathOrId) {
        String path = pathOrId.substring(pathOrId.indexOf(':') + 1);
        int dot = path.lastIndexOf('.');
        return dot > path.lastIndexOf('/') ? path.substring(0, dot) : path;
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

    private List<ChainProblem> distinct(List<ChainProblem> problems) {
        Map<String, ChainProblem> seen = new LinkedHashMap<>();
        for (ChainProblem p : problems) {
            if (quiet && p.severity() == LayoutMismatch.Severity.INFO) {
                continue;
            }
            seen.putIfAbsent(render(p), p);
        }
        return List.copyOf(seen.values());
    }

    private static String render(ChainProblem problem) {
        return "    " + problem.severity() + " " + problem.kind() + ": " + problem.detail();
    }

    /**
     * One line totalling the findings printed above.
     *
     * <p>Without it, checking a report against a previous run means reading several
     * hundred lines and counting. Re-running this tool against a tree it has been run
     * on before is the normal way to use it — that is what makes a report worth
     * keeping — and a run that has drifted should be one line's difference to notice,
     * not a paragraph's.
     */
    private static void reportTotals(Map<LayoutMismatch.Severity, Integer> printed) {
        int errors = printed.getOrDefault(LayoutMismatch.Severity.ERROR, 0);
        int warnings = printed.getOrDefault(LayoutMismatch.Severity.WARNING, 0);
        int infos = printed.getOrDefault(LayoutMismatch.Severity.INFO, 0);
        System.out.printf("%d finding(s) above: %d error, %d warning, %d info%n",
                errors + warnings + infos, errors, warnings, infos);
    }
}
