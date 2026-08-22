package net.cyberpunk042.mcshaders.core.portal;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import net.cyberpunk042.mcshaders.core.api.Experimental;

/**
 * Finds the opening inside a portal frame, without knowing what shape the frame is.
 *
 * <p>Lighting a portal the way a nether portal is lit does not need the frame's
 * dimensions. Vanilla does not hardcode them either: it starts where the player
 * struck the igniter and grows outward through empty blocks, and whatever it can
 * reach is the portal — provided the frame closed around it.
 *
 * <p>That property is what makes this useful here. The Ancient City's frame is a
 * fixed shape somewhere in vanilla's structure files, two secondary descriptions of
 * it disagree, and none of that matters: a flood fill bounded by the frame material
 * finds the opening whatever its size. The alternative — hardcoding a shape read
 * from a source that might be wrong — fails silently on the real structure.
 *
 * <p>The gate is not the shape either. Reinforced deepslate cannot be obtained in
 * survival and cannot be pushed by a piston, so the only frame a player can light is
 * one the game generated for them.
 *
 * <h2>No Minecraft in here</h2>
 *
 * <p>The world arrives as a {@link Blocks} predicate pair: what counts as empty, and
 * what counts as frame. So the algorithm is testable against a string diagram
 * instead of a running game, which is the only way any of it could be checked from
 * where it was written.
 *
 * <h2>What "closed" means</h2>
 *
 * <p>A fill that reaches the search limit has escaped into open air, and an opening
 * that is not enclosed is not a portal. Reporting that as {@link Result#unbounded()}
 * rather than as a very large portal is the difference between refusing to light a
 * doorway and filling a cave with portal blocks.
 */
@Experimental
public final class FrameScan {

    /**
     * How many blocks a fill may visit before it is called open.
     *
     * <p>Generous enough for any frame a structure generates, small enough that a
     * fill leaking into a cave stops promptly. Vanilla's own portal limits are
     * tighter, but this has to admit frames larger than a nether portal's.
     */
    public static final int DEFAULT_LIMIT = 4096;

    private FrameScan() {
    }

    /** What the world looks like to the scan, as two predicates over coordinates. */
    public interface Blocks {

        /** Whether this position is empty — fillable, part of the opening. */
        boolean isEmpty(int x, int y, int z);

        /** Whether this position is the frame material that bounds the opening. */
        boolean isFrame(int x, int y, int z);
    }

    /** A position in the opening. */
    public record Pos(int x, int y, int z) {
    }

    /**
     * What a scan found.
     *
     * @param interior the opening, in the order it was reached; empty when there is none
     * @param bounded  whether the frame closed around it
     */
    public record Result(List<Pos> interior, boolean bounded) {

        public Result {
            interior = interior == null ? List.of() : List.copyOf(interior);
        }

        /** A fill that escaped, or was never enclosed. Not a portal. */
        public static Result unbounded() {
            return new Result(List.of(), false);
        }

        /** Whether this is an opening that could be lit. */
        public boolean isPortal() {
            return bounded && !interior.isEmpty();
        }

        public int size() {
            return interior.size();
        }
    }

    /**
     * Scans outward from where the igniter struck.
     *
     * <p>Six-way, not diagonal: a portal opening is a solid volume, and a diagonal
     * step could slip between two frame blocks meeting at an edge and leak out
     * through a wall that looks closed.
     *
     * @param blocks what the world looks like
     * @param x      where the player lit it
     * @param y      where the player lit it
     * @param z      where the player lit it
     * @param limit  how many blocks may be visited before the fill is called open
     * @return the opening, or {@link Result#unbounded()}
     */
    public static Result from(Blocks blocks, int x, int y, int z, int limit) {
        if (blocks == null) {
            throw new IllegalArgumentException("Cannot scan a null world");
        }
        if (limit < 1) {
            throw new IllegalArgumentException("Scan limit must be at least 1, got " + limit);
        }
        if (!blocks.isEmpty(x, y, z)) {
            // Lighting the frame itself, or a solid block. Nothing to fill.
            return Result.unbounded();
        }

        Set<Pos> seen = new LinkedHashSet<>();
        Deque<Pos> queue = new ArrayDeque<>();
        Pos start = new Pos(x, y, z);
        seen.add(start);
        queue.add(start);

        while (!queue.isEmpty()) {
            Pos at = queue.removeFirst();
            for (Pos next : neighbours(at)) {
                if (seen.contains(next)) {
                    continue;
                }
                if (blocks.isFrame(next.x(), next.y(), next.z())) {
                    // The wall. Stop, and it counts as closed on this side.
                    continue;
                }
                if (!blocks.isEmpty(next.x(), next.y(), next.z())) {
                    // Neither empty nor frame: something else is in the way, and it
                    // is not part of the opening. Treated as a wall rather than as a
                    // leak, because a portal blocked by a stray block is a portal a
                    // player can clear, not a broken frame.
                    continue;
                }
                if (seen.size() >= limit) {
                    // Room to keep going means nothing closed around this.
                    return Result.unbounded();
                }
                seen.add(next);
                queue.add(next);
            }
        }

        return new Result(List.copyOf(seen), true);
    }

    /** Scans with {@link #DEFAULT_LIMIT}. */
    public static Result from(Blocks blocks, int x, int y, int z) {
        return from(blocks, x, y, z, DEFAULT_LIMIT);
    }

    private static List<Pos> neighbours(Pos at) {
        return List.of(
                new Pos(at.x() + 1, at.y(), at.z()),
                new Pos(at.x() - 1, at.y(), at.z()),
                new Pos(at.x(), at.y() + 1, at.z()),
                new Pos(at.x(), at.y() - 1, at.z()),
                new Pos(at.x(), at.y(), at.z() + 1),
                new Pos(at.x(), at.y(), at.z() - 1));
    }
}
