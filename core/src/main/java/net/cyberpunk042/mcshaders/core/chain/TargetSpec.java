package net.cyberpunk042.mcshaders.core.chain;

import net.cyberpunk042.mcshaders.core.api.Stable;

/**
 * An off-screen render target a chain writes into and later reads back.
 *
 * @param width  in pixels, or 0 to follow the screen
 * @param height in pixels, or 0 to follow the screen
 */
@Stable(since = "0.4.0")
public record TargetSpec(int width, int height) {

    /** A target that tracks the screen's size, which is the common case. */
    public static final TargetSpec SCREEN_SIZED = new TargetSpec(0, 0);

    public TargetSpec {
        if (width < 0 || height < 0) {
            throw new IllegalArgumentException("target size cannot be negative: " + width + "x" + height);
        }
        if ((width == 0) != (height == 0)) {
            throw new IllegalArgumentException(
                    "a target is either fully screen-sized or fully fixed, not " + width + "x" + height);
        }
    }

    public boolean isScreenSized() {
        return width == 0;
    }
}
