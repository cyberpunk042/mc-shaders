package net.cyberpunk042.mcshaders.core.chain;

import java.util.List;
import net.cyberpunk042.mcshaders.core.api.Stable;
import net.cyberpunk042.mcshaders.core.layout.UniformBlock;

/**
 * One draw in a chain: run a shader pair over some inputs, into one target.
 *
 * @param vertexShader   resource id of the vertex shader
 * @param fragmentShader resource id of the fragment shader
 * @param inputs         the textures it reads
 * @param output         the target it writes
 * @param uniforms       the uniform blocks it is given, as the host declares them —
 *                       to be checked against what the shader declares
 */
@Stable(since = "0.4.0")
public record Pass(String vertexShader, String fragmentShader, List<Input> inputs,
                   String output, List<UniformBlock> uniforms) {

    public Pass {
        if (fragmentShader == null || fragmentShader.isBlank()) {
            throw new IllegalArgumentException("a pass needs a fragment shader");
        }
        if (vertexShader == null || vertexShader.isBlank()) {
            throw new IllegalArgumentException("a pass needs a vertex shader");
        }
        if (output == null || output.isBlank()) {
            throw new IllegalArgumentException("a pass needs an output target");
        }
        inputs = List.copyOf(inputs);
        uniforms = List.copyOf(uniforms);
    }

    /** A short name for this pass, for diagnostics. */
    public String describe() {
        return fragmentShader + " -> " + output;
    }
}
