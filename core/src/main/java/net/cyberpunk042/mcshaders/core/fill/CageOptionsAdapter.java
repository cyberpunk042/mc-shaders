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

/**
 * Adapter for shape-agnostic access to CageOptions.
 * 
 * <p>Provides a unified interface for GUI editing of cage options across
 * different shape types, with semantic mapping of "primary" and "secondary"
 * counts to shape-specific fields.</p>
 * 
 * <h2>Semantic Mapping</h2>
 * <table>
 *   <tr><th>Shape</th><th>Primary</th><th>Secondary</th><th>Extras</th></tr>
 *   <tr><td>Sphere</td><td>latitudeCount</td><td>longitudeCount</td><td>showEquator, showPoles</td></tr>
 *   <tr><td>Cylinder</td><td>horizontalRings</td><td>verticalLines</td><td>showCaps</td></tr>
 *   <tr><td>Prism</td><td>horizontalRings</td><td>verticalLines</td><td>showCaps</td></tr>
 *   <tr><td>Polyhedron</td><td>N/A</td><td>N/A</td><td>allEdges, faceOutlines</td></tr>
 * </table>
 * 
 * @see CageOptions
 */
public interface CageOptionsAdapter {
    
    /** Indicates this shape doesn't support count-based cage options. */
    int NOT_APPLICABLE = -1;
    
    // =========================================================================
    // Common Properties (from CageOptions interface)
    // =========================================================================
    
    /** Line width for cage rendering. */
    float lineWidth();
    
    /** Whether to show structural edges. */
    boolean showEdges();
    
    /** Set line width. Returns new adapter with updated value. */
    CageOptionsAdapter withLineWidth(float width);
    
    /** Set show edges. Returns new adapter with updated value. */
    CageOptionsAdapter withShowEdges(boolean show);
    
    // =========================================================================
    // Primary/Secondary Count Access
    // =========================================================================
    
    /** Primary count (latitude for sphere, horizontal rings for cylinder/prism). */
    int primaryCount();
    
    /** Secondary count (longitude for sphere, vertical lines for cylinder/prism). */
    int secondaryCount();
    
    /** Set primary count. Returns new adapter with updated value. */
    CageOptionsAdapter withPrimaryCount(int count);
    
    /** Set secondary count. Returns new adapter with updated value. */
    CageOptionsAdapter withSecondaryCount(int count);
    
    // =========================================================================
    // Dynamic Labels for GUI
    // =========================================================================
    
    /** Human-readable label for primary count. */
    String primaryLabel();
    
    /** Human-readable label for secondary count. */
    String secondaryLabel();
    
    /** Whether this shape supports primary/secondary counts. */
    default boolean supportsCountOptions() {
        return primaryCount() != NOT_APPLICABLE;
    }
    
    // =========================================================================
    // Shape-Specific Boolean Extras
    // =========================================================================
    
    // Sphere-specific
    default boolean showEquator() { return false; }
    default boolean showPoles() { return false; }
    default CageOptionsAdapter withShowEquator(boolean v) { return this; }
    default CageOptionsAdapter withShowPoles(boolean v) { return this; }
    default boolean hasSphereExtras() { return false; }
    
    // Cylinder/Prism-specific
    default boolean showCaps() { return false; }
    default CageOptionsAdapter withShowCaps(boolean v) { return this; }
    default boolean hasCapsOption() { return false; }
    
    // Polyhedron-specific
    default boolean allEdges() { return true; }
    default boolean faceOutlines() { return false; }
    default CageOptionsAdapter withAllEdges(boolean v) { return this; }
    default CageOptionsAdapter withFaceOutlines(boolean v) { return this; }
    default boolean hasPolyhedronExtras() { return false; }
    
    // =========================================================================
    // Build Result
    // =========================================================================
    
    /** Build the underlying typed CageOptions. */
    CageOptions build();
    
    /** Get the shape type this adapter is for. */
    String shapeType();
    
    /**
     * Creates an adapter for the given shape type.
     * 
     * @param shapeType Shape type name (e.g., "sphere", "cylinder")
     * @param current Current cage options, or null for defaults
     * @return Appropriate adapter for the shape
     */
    static CageOptionsAdapter forShape(String shapeType, CageOptions current) {
        String type = shapeType != null ? shapeType.toLowerCase() : "sphere";
        return switch (type) {
            case "sphere" -> new SphereCageAdapter(
                current instanceof SphereCageOptions s ? s : SphereCageOptions.DEFAULT);
            case "cylinder" -> new CylinderCageAdapter(
                current instanceof CylinderCageOptions c ? c : CylinderCageOptions.DEFAULT);
            case "prism" -> new PrismCageAdapter(
                current instanceof PrismCageOptions p ? p : PrismCageOptions.DEFAULT);
            case "polyhedron", "poly" -> new PolyhedronCageAdapter(
                current instanceof PolyhedronCageOptions p ? p : PolyhedronCageOptions.DEFAULT);
            case "ring" -> new RingCageAdapter(
                current instanceof RingCageOptions r ? r : RingCageOptions.DEFAULT);
            case "cone" -> new ConeCageAdapter(
                current instanceof ConeCageOptions c ? c : ConeCageOptions.DEFAULT);
            case "torus" -> new TorusCageAdapter(
                current instanceof TorusCageOptions t ? t : TorusCageOptions.DEFAULT);
            case "capsule", "rays" -> new SphereCageAdapter(  // Capsule and Rays use sphere-like controls
                current instanceof SphereCageOptions s ? s : SphereCageOptions.DEFAULT);
            default -> new SphereCageAdapter(SphereCageOptions.DEFAULT);
        };
    }

    
    // =========================================================================
    // Implementations
    // =========================================================================
    
    record SphereCageAdapter(SphereCageOptions options) implements CageOptionsAdapter {
        @Override public String shapeType() { return "sphere"; }
        @Override public float lineWidth() { return options.lineWidth(); }
        @Override public boolean showEdges() { return options.showEdges(); }
        @Override public int primaryCount() { return options.latitudeCount(); }
        @Override public int secondaryCount() { return options.longitudeCount(); }
        @Override public String primaryLabel() { return "Latitude Lines"; }
        @Override public String secondaryLabel() { return "Longitude Lines"; }
        
        // Sphere extras
        @Override public boolean hasSphereExtras() { return true; }
        @Override public boolean showEquator() { return options.showEquator(); }
        @Override public boolean showPoles() { return options.showPoles(); }
        
        @Override public CageOptionsAdapter withLineWidth(float w) {
            return new SphereCageAdapter(options.toBuilder().lineWidth(w).build());
        }
        @Override public CageOptionsAdapter withShowEdges(boolean v) {
            return new SphereCageAdapter(options.toBuilder().showEdges(v).build());
        }
        @Override public CageOptionsAdapter withPrimaryCount(int c) {
            return new SphereCageAdapter(options.toBuilder().latitudeCount(c).build());
        }
        @Override public CageOptionsAdapter withSecondaryCount(int c) {
            return new SphereCageAdapter(options.toBuilder().longitudeCount(c).build());
        }
        @Override public CageOptionsAdapter withShowEquator(boolean v) {
            return new SphereCageAdapter(options.toBuilder().showEquator(v).build());
        }
        @Override public CageOptionsAdapter withShowPoles(boolean v) {
            return new SphereCageAdapter(options.toBuilder().showPoles(v).build());
        }
        @Override public CageOptions build() { return options; }
    }
    
    record CylinderCageAdapter(CylinderCageOptions options) implements CageOptionsAdapter {
        @Override public String shapeType() { return "cylinder"; }
        @Override public float lineWidth() { return options.lineWidth(); }
        @Override public boolean showEdges() { return options.showEdges(); }
        @Override public int primaryCount() { return options.horizontalRings(); }
        @Override public int secondaryCount() { return options.verticalLines(); }
        @Override public String primaryLabel() { return "Horizontal Rings"; }
        @Override public String secondaryLabel() { return "Vertical Lines"; }
        
        // Caps option
        @Override public boolean hasCapsOption() { return true; }
        @Override public boolean showCaps() { return options.showCaps(); }
        
        @Override public CageOptionsAdapter withLineWidth(float w) {
            return new CylinderCageAdapter(options.toBuilder().lineWidth(w).build());
        }
        @Override public CageOptionsAdapter withShowEdges(boolean v) {
            return new CylinderCageAdapter(options.toBuilder().showEdges(v).build());
        }
        @Override public CageOptionsAdapter withPrimaryCount(int c) {
            return new CylinderCageAdapter(options.toBuilder().horizontalRings(c).build());
        }
        @Override public CageOptionsAdapter withSecondaryCount(int c) {
            return new CylinderCageAdapter(options.toBuilder().verticalLines(c).build());
        }
        @Override public CageOptionsAdapter withShowCaps(boolean v) {
            return new CylinderCageAdapter(options.toBuilder().showCaps(v).build());
        }
        @Override public CageOptions build() { return options; }
    }
    
    record PrismCageAdapter(PrismCageOptions options) implements CageOptionsAdapter {
        @Override public String shapeType() { return "prism"; }
        @Override public float lineWidth() { return options.lineWidth(); }
        @Override public boolean showEdges() { return options.showEdges(); }
        @Override public int primaryCount() { return options.horizontalRings(); }
        @Override public int secondaryCount() { return options.verticalLines(); }
        @Override public String primaryLabel() { return "Horizontal Rings"; }
        @Override public String secondaryLabel() { return "Vertical Lines"; }
        
        // Caps option
        @Override public boolean hasCapsOption() { return true; }
        @Override public boolean showCaps() { return options.showCaps(); }
        
        @Override public CageOptionsAdapter withLineWidth(float w) {
            return new PrismCageAdapter(options.toBuilder().lineWidth(w).build());
        }
        @Override public CageOptionsAdapter withShowEdges(boolean v) {
            return new PrismCageAdapter(options.toBuilder().showEdges(v).build());
        }
        @Override public CageOptionsAdapter withPrimaryCount(int c) {
            return new PrismCageAdapter(options.toBuilder().horizontalRings(c).build());
        }
        @Override public CageOptionsAdapter withSecondaryCount(int c) {
            return new PrismCageAdapter(options.toBuilder().verticalLines(c).build());
        }
        @Override public CageOptionsAdapter withShowCaps(boolean v) {
            return new PrismCageAdapter(options.toBuilder().showCaps(v).build());
        }
        @Override public CageOptions build() { return options; }
    }
    
    record PolyhedronCageAdapter(PolyhedronCageOptions options) implements CageOptionsAdapter {
        @Override public String shapeType() { return "polyhedron"; }
        @Override public float lineWidth() { return options.lineWidth(); }
        @Override public boolean showEdges() { return options.showEdges(); }
        @Override public int primaryCount() { return NOT_APPLICABLE; }
        @Override public int secondaryCount() { return NOT_APPLICABLE; }
        @Override public String primaryLabel() { return "N/A"; }
        @Override public String secondaryLabel() { return "N/A"; }
        
        // Polyhedron extras
        @Override public boolean hasPolyhedronExtras() { return true; }
        @Override public boolean allEdges() { return options.allEdges(); }
        @Override public boolean faceOutlines() { return options.faceOutlines(); }
        
        @Override public CageOptionsAdapter withLineWidth(float w) {
            return new PolyhedronCageAdapter(options.toBuilder().lineWidth(w).build());
        }
        @Override public CageOptionsAdapter withShowEdges(boolean v) {
            return new PolyhedronCageAdapter(options.toBuilder().showEdges(v).build());
        }
        @Override public CageOptionsAdapter withPrimaryCount(int c) { return this; }
        @Override public CageOptionsAdapter withSecondaryCount(int c) { return this; }
        @Override public CageOptionsAdapter withAllEdges(boolean v) {
            return new PolyhedronCageAdapter(options.toBuilder().allEdges(v).build());
        }
        @Override public CageOptionsAdapter withFaceOutlines(boolean v) {
            return new PolyhedronCageAdapter(options.toBuilder().faceOutlines(v).build());
        }
        @Override public CageOptions build() { return options; }
    }
    
    record RingCageAdapter(RingCageOptions options) implements CageOptionsAdapter {
        @Override public String shapeType() { return "ring"; }
        @Override public float lineWidth() { return options.lineWidth(); }
        @Override public boolean showEdges() { return options.showEdges(); }
        @Override public int primaryCount() { return options.radialLines(); }
        @Override public int secondaryCount() { return options.concentricRings(); }
        @Override public String primaryLabel() { return "Radial Lines"; }
        @Override public String secondaryLabel() { return "Concentric Rings"; }
        
        @Override public CageOptionsAdapter withLineWidth(float w) {
            return new RingCageAdapter(options.toBuilder().lineWidth(w).build());
        }
        @Override public CageOptionsAdapter withShowEdges(boolean v) {
            return new RingCageAdapter(options.toBuilder().showEdges(v).build());
        }
        @Override public CageOptionsAdapter withPrimaryCount(int c) {
            return new RingCageAdapter(options.toBuilder().radialLines(c).build());
        }
        @Override public CageOptionsAdapter withSecondaryCount(int c) {
            return new RingCageAdapter(options.toBuilder().concentricRings(c).build());
        }
        @Override public CageOptions build() { return options; }
    }
    
    record ConeCageAdapter(ConeCageOptions options) implements CageOptionsAdapter {
        @Override public String shapeType() { return "cone"; }
        @Override public float lineWidth() { return options.lineWidth(); }
        @Override public boolean showEdges() { return options.showEdges(); }
        @Override public int primaryCount() { return options.radialLines(); }
        @Override public int secondaryCount() { return options.horizontalRings(); }
        @Override public String primaryLabel() { return "Radial Lines"; }
        @Override public String secondaryLabel() { return "Horizontal Rings"; }
        
        // Has base option similar to caps
        @Override public boolean hasCapsOption() { return true; }
        @Override public boolean showCaps() { return options.showBase(); }
        @Override public CageOptionsAdapter withShowCaps(boolean v) {
            return new ConeCageAdapter(options.toBuilder().showBase(v).build());
        }
        
        @Override public CageOptionsAdapter withLineWidth(float w) {
            return new ConeCageAdapter(options.toBuilder().lineWidth(w).build());
        }
        @Override public CageOptionsAdapter withShowEdges(boolean v) {
            return new ConeCageAdapter(options.toBuilder().showEdges(v).build());
        }
        @Override public CageOptionsAdapter withPrimaryCount(int c) {
            return new ConeCageAdapter(options.toBuilder().radialLines(c).build());
        }
        @Override public CageOptionsAdapter withSecondaryCount(int c) {
            return new ConeCageAdapter(options.toBuilder().horizontalRings(c).build());
        }
        @Override public CageOptions build() { return options; }
    }
    
    record TorusCageAdapter(TorusCageOptions options) implements CageOptionsAdapter {
        @Override public String shapeType() { return "torus"; }
        @Override public float lineWidth() { return options.lineWidth(); }
        @Override public boolean showEdges() { return options.showEdges(); }
        @Override public int primaryCount() { return options.majorRings(); }
        @Override public int secondaryCount() { return options.minorRings(); }
        @Override public String primaryLabel() { return "Major Rings"; }
        @Override public String secondaryLabel() { return "Minor Rings"; }
        
        @Override public CageOptionsAdapter withLineWidth(float w) {
            return new TorusCageAdapter(options.toBuilder().lineWidth(w).build());
        }
        @Override public CageOptionsAdapter withShowEdges(boolean v) {
            return new TorusCageAdapter(options.toBuilder().showEdges(v).build());
        }
        @Override public CageOptionsAdapter withPrimaryCount(int c) {
            return new TorusCageAdapter(options.toBuilder().majorRings(c).build());
        }
        @Override public CageOptionsAdapter withSecondaryCount(int c) {
            return new TorusCageAdapter(options.toBuilder().minorRings(c).build());
        }
        @Override public CageOptions build() { return options; }
    }
}
