package net.cyberpunk042.mcshaders.core.layout;

import java.util.List;
import net.cyberpunk042.mcshaders.core.api.Stable;

/**
 * A named uniform block: what it is called, and what is in it, in order.
 *
 * @param name    the block name as the shader and the host both spell it
 * @param members its members, in declaration order — the order <em>is</em> the layout
 */
@Stable(since = "0.4.0")
public record UniformBlock(String name, List<Std140.Member> members) {

    public UniformBlock {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("block name is required");
        }
        members = List.copyOf(members);
    }

    /** Size in bytes under std140. */
    public int sizeInBytes() {
        return Std140.sizeOf(members);
    }

    /** This block with every matrix rewritten as its columns. */
    public UniformBlock expanded() {
        return new UniformBlock(name, Std140.expand(members));
    }
}
