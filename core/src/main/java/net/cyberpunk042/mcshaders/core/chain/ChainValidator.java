package net.cyberpunk042.mcshaders.core.chain;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.cyberpunk042.mcshaders.core.api.Stable;
import net.cyberpunk042.mcshaders.core.glsl.IncludeResolver;
import net.cyberpunk042.mcshaders.core.glsl.ResolvedShader;
import net.cyberpunk042.mcshaders.core.glsl.SourceProvider;
import net.cyberpunk042.mcshaders.core.layout.GlslBlocks;
import net.cyberpunk042.mcshaders.core.layout.LayoutComparison;
import net.cyberpunk042.mcshaders.core.layout.LayoutMismatch;
import net.cyberpunk042.mcshaders.core.layout.UniformBlock;

/**
 * Checks a {@link PostChain} against the shaders it names, without a GPU.
 *
 * <p>Everything here is answerable from text: whether the shaders exist, whether
 * their includes resolve, whether targets are written before they are read, whether
 * the samplers a pass binds are the ones its shader declares, and whether each
 * uniform block means the same thing to the host and to the shader. What is left
 * for a GPU is whether the GLSL compiles — which is worth knowing, but is not
 * usually what is wrong.
 *
 * <p>Chain-wide state, in particular target liveness, is why this is not just a
 * per-pass loop: reading a target before anything wrote it is invisible from inside
 * either pass.
 */
@Stable(since = "0.4.0")
public final class ChainValidator {

    private final SourceProvider provider;
    private final Set<String> externalTargets;

    /**
     * @param provider        resolves shader ids to source
     * @param externalTargets targets supplied by the host rather than declared by the
     *                        chain — {@code minecraft:main} and the like. Reads of
     *                        these are not flagged; without the set they would all
     *                        look like unknown targets.
     */
    public ChainValidator(SourceProvider provider, Set<String> externalTargets) {
        this.provider = provider;
        this.externalTargets = Set.copyOf(externalTargets);
    }

    /** Every problem found, most structural first. */
    public List<ChainProblem> validate(PostChain chain) {
        List<ChainProblem> out = new ArrayList<>();
        Set<String> written = new HashSet<>(externalTargets);
        Set<String> read = new LinkedHashSet<>();

        for (Pass pass : chain.passes()) {
            String where = pass.describe();
            checkTargets(chain, pass, where, written, read, out);
            checkShaders(pass, where, out);
            written.add(pass.output());
        }

        for (String declared : chain.targets().keySet()) {
            if (!read.contains(declared)) {
                out.add(new ChainProblem(ChainProblem.Kind.UNUSED_TARGET, LayoutMismatch.Severity.INFO,
                        null, "target '" + declared + "' is declared but nothing reads it"));
            }
        }
        return List.copyOf(out);
    }

    /** Whether the chain has any problem that would stop it drawing correctly. */
    public boolean isSound(PostChain chain) {
        return validate(chain).stream().noneMatch(ChainProblem::isError);
    }

    private void checkTargets(PostChain chain, Pass pass, String where, Set<String> written,
                              Set<String> read, List<ChainProblem> out) {
        for (Input input : pass.inputs()) {
            read.add(input.target());
            if (!chain.declares(input.target()) && !externalTargets.contains(input.target())) {
                out.add(new ChainProblem(ChainProblem.Kind.UNKNOWN_TARGET, LayoutMismatch.Severity.ERROR,
                        where, "input '" + input.samplerName() + "' reads target '" + input.target()
                                + "', which is neither declared by the chain nor supplied by the host"));
            } else if (!written.contains(input.target())) {
                out.add(new ChainProblem(ChainProblem.Kind.READ_BEFORE_WRITE, LayoutMismatch.Severity.WARNING,
                        where, "input '" + input.samplerName() + "' reads target '" + input.target()
                                + "' before any pass has written it; it holds whatever was there"));
            }
        }
        if (!chain.declares(pass.output()) && !externalTargets.contains(pass.output())) {
            out.add(new ChainProblem(ChainProblem.Kind.UNKNOWN_OUTPUT, LayoutMismatch.Severity.ERROR,
                    where, "writes target '" + pass.output()
                            + "', which is neither declared by the chain nor supplied by the host"));
        }
    }

    private void checkShaders(Pass pass, String where, List<ChainProblem> out) {
        for (String shader : List.of(pass.vertexShader(), pass.fragmentShader())) {
            if (provider.read(shader).isEmpty()) {
                out.add(new ChainProblem(ChainProblem.Kind.MISSING_SHADER, LayoutMismatch.Severity.ERROR,
                        where, "shader '" + shader + "' cannot be found"));
            }
        }
        if (provider.read(pass.fragmentShader()).isEmpty()) {
            return;
        }

        ResolvedShader resolved = new IncludeResolver(provider).resolve(pass.fragmentShader());
        for (ResolvedShader.Diagnostic d : resolved.errors()) {
            out.add(new ChainProblem(ChainProblem.Kind.UNRESOLVED_INCLUDE, LayoutMismatch.Severity.ERROR,
                    where, d.toString()));
        }
        if (resolved.hasErrors()) {
            // The flattened source is incomplete, so anything read out of it would be
            // a guess. Report the include failure and stop rather than pile on.
            return;
        }

        checkSamplers(pass, where, resolved.source(), out);
        checkLayouts(pass, where, resolved.source(), out);
    }

    private void checkSamplers(Pass pass, String where, String source, List<ChainProblem> out) {
        Set<String> declared = Set.copyOf(GlslBlocks.samplers(source));
        for (Input input : pass.inputs()) {
            if (!declared.contains(input.declaredSampler())) {
                out.add(new ChainProblem(ChainProblem.Kind.SAMPLER_MISMATCH, LayoutMismatch.Severity.ERROR,
                        where, "input '" + input.samplerName() + "' is bound but the shader declares no '"
                                + input.declaredSampler() + "'"));
            }
        }
        Set<String> bound = new HashSet<>();
        for (Input input : pass.inputs()) {
            bound.add(input.declaredSampler());
        }
        for (String sampler : declared) {
            if (!bound.contains(sampler)) {
                out.add(new ChainProblem(ChainProblem.Kind.SAMPLER_MISMATCH, LayoutMismatch.Severity.WARNING,
                        where, "the shader declares '" + sampler + "' but no input is bound to it"));
            }
        }
    }

    private void checkLayouts(Pass pass, String where, String source, List<ChainProblem> out) {
        Map<String, UniformBlock> inShader = GlslBlocks.blocks(source);
        for (UniformBlock host : pass.uniforms()) {
            UniformBlock shader = inShader.get(host.name());
            if (shader == null) {
                // Not a defect on its own: a chain may declare a block for several
                // passes, only some of which read it.
                out.add(new ChainProblem(ChainProblem.Kind.LAYOUT_MISMATCH, LayoutMismatch.Severity.INFO,
                        where, "block '" + host.name() + "' is provided but this shader does not declare it"));
                continue;
            }
            for (LayoutMismatch m : LayoutComparison.compare(shader, host)) {
                out.add(new ChainProblem(ChainProblem.Kind.LAYOUT_MISMATCH, m.severity(), where,
                        "block '" + host.name() + "': " + m));
            }
        }
    }
}
