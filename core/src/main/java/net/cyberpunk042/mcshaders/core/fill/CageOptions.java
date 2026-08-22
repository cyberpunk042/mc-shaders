/*
 * Ported from the-virus-block-mc (net.cyberpunk042.visual), where it is
 * geometry model code with no Minecraft or graphics-API dependency.
 * Relicensed to MIT here by the author, per the engine/content split
 * recorded in docs/PORTING.md.
 *
 * JSON binding was removed on the way across: the model stays free of
 * serialisation so it can be loaded from external content. The @JsonField
 * metadata is retained for a codec layer above core.
 */
package net.cyberpunk042.mcshaders.core.fill;

import net.cyberpunk042.mcshaders.core.validation.Range;
import net.cyberpunk042.mcshaders.core.validation.ValueRange;

/**
 * Base interface for cage rendering options.
 * 
 * <p>Cage mode renders a structured grid rather than all tessellation edges.
 * Each shape type has its own implementation with shape-specific options.</p>
 * 
 * <h2>Curved Surface Shapes (Distinct Cage)</h2>
 * <ul>
 *   <li>{@link SphereCageOptions} - Latitude/longitude grid</li>
 *   <li>{@link CylinderCageOptions} - Vertical lines and horizontal rings</li>
 *   <li>{@link PrismCageOptions} - Vertical edges and horizontal rings</li>
 *   <li>{@link ConeCageOptions} - Radial lines to apex and base ring</li>
 *   <li>{@link RingCageOptions} - Radial lines with inner/outer rings</li>
 *   <li>{@link TorusCageOptions} - Major and minor rings</li>
 * </ul>
 * 
 * <h2>Polyhedral Shapes (Cage = Wireframe)</h2>
 * <ul>
 *   <li>{@link PolyhedronCageOptions} - Natural edges (same as wireframe)</li>
 * </ul>
 * 
 * @see FillConfig
 * @see FillMode#CAGE
 */
public sealed interface CageOptions 
    permits SphereCageOptions, PrismCageOptions, CylinderCageOptions, PolyhedronCageOptions,
            RingCageOptions, ConeCageOptions, TorusCageOptions {
    
    /** Line width for cage rendering. */
    @Range(ValueRange.POSITIVE_NONZERO) 
    float lineWidth();
    
    /** Whether to show all structural edges. */
    boolean showEdges();
    
    /** Default line width. */
    float DEFAULT_LINE_WIDTH = 1.0f;
}

