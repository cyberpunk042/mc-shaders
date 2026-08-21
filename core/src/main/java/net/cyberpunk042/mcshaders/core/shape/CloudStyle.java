/*
 * Ported from the-virus-block-mc (net.cyberpunk042.visual), where it is
 * geometry model code with no Minecraft or graphics-API dependency.
 * Relicensed to MIT here by the author, per the engine/content split
 * recorded in docs/PORTING.md.
 */
package net.cyberpunk042.mcshaders.core.shape;

/**
 * Cloud rendering algorithm styles.
 * 
 * <p>Different algorithms produce different visual characteristics:</p>
 * 
 * <h3>GAUSSIAN (Current Default)</h3>
 * <ul>
 *   <li>Spherical Gaussian bumps on a sphere</li>
 *   <li>"Grape cluster" look with distinct, even bumps</li>
 *   <li>Good for: stylized clouds, alien atmospheres</li>
 * </ul>
 * 
 * <h3>FRACTAL</h3>
 * <ul>
 *   <li>Multi-octave fractal Brownian motion (fBm) noise</li>
 *   <li>Organic, turbulent surface with detail at all scales</li>
 *   <li>Good for: fog, mist, wispy clouds</li>
 * </ul>
 * 
 * <h3>BILLOWING</h3>
 * <ul>
 *   <li>Layered blob placement + fBm detail (like metaballs)</li>
 *   <li>Large primary puffs with smaller puffs layered on top</li>
 *   <li>Good for: cumulus clouds, realistic billowing</li>
 * </ul>
 * 
 * <h3>WORLEY</h3>
 * <ul>
 *   <li>Cellular/Voronoi noise pattern</li>
 *   <li>Puffy, cell-like structure with distinct boundaries</li>
 *   <li>Good for: cauliflower clouds, alien formations</li>
 * </ul>
 */
public enum CloudStyle {
    
    /** Spherical Gaussian bumps - "grape cluster" appearance. */
    GAUSSIAN("Gaussian", "Distinct, even bumps (current)"),
    
    /** Fractal Brownian motion noise - organic turbulence. */
    FRACTAL("Fractal", "Organic turbulent surface"),
    
    /** Layered puffs with fractal detail - realistic cumulus. */
    BILLOWING("Billowing", "Layered puffs like cumulus"),
    
    /** Cellular/Worley noise - puffy cell structure. */
    WORLEY("Worley", "Cellular, puffy structure");
    
    private final String displayName;
    private final String description;
    
    CloudStyle(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }
    
    public String displayName() { return displayName; }
    public String description() { return description; }
    
    @Override
    public String toString() { return displayName; }
}
