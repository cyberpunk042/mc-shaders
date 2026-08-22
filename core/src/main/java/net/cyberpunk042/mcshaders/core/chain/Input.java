package net.cyberpunk042.mcshaders.core.chain;

import net.cyberpunk042.mcshaders.core.api.Stable;

/**
 * One texture a pass reads.
 *
 * @param samplerName    the name the pass knows it by; the shader declares this with
 *                       {@code Sampler} appended — see {@link #declaredSampler()}
 * @param target         the target it is read from
 * @param useDepthBuffer whether to bind the target's depth attachment rather than its
 *                       colour
 */
@Stable(since = "0.4.0")
public record Input(String samplerName, String target, boolean useDepthBuffer) {

    public Input {
        if (samplerName == null || samplerName.isBlank()) {
            throw new IllegalArgumentException("sampler name is required");
        }
        if (target == null || target.isBlank()) {
            throw new IllegalArgumentException("input " + samplerName + " has no target");
        }
    }

    public Input(String samplerName, String target) {
        this(samplerName, target, false);
    }

    /**
     * The identifier the shader declares for this input.
     *
     * <p>An input named {@code In} is declared as {@code uniform sampler2D InSampler}.
     * The suffix is convention rather than something either side states, which is
     * exactly why it is worth writing down in one place instead of being open-coded
     * wherever the two are compared.
     */
    public String declaredSampler() {
        return samplerName + "Sampler";
    }
}
