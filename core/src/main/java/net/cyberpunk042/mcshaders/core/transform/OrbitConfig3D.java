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
package net.cyberpunk042.mcshaders.core.transform;

import net.cyberpunk042.mcshaders.core.serial.JsonField;
import org.joml.Vector3f;

/**
 * 3D orbital motion configuration with independent per-axis control.
 * 
 * <p>Enables atom-like orbital motion by configuring independent motion
 * on each axis (X, Y, Z). Each axis can have a different motion mode,
 * amplitude, frequency, and phase - allowing complex 3D orbital patterns.</p>
 * 
 * <h2>JSON Format</h2>
 * <pre>
 * "orbit3d": {
 *   "enabled": true,
 *   "x": { "mode": "CIRCULAR", "amplitude": 2.0, "frequency": 0.5, "phase": 0.0 },
 *   "y": { "mode": "WAVE", "amplitude": 0.3, "frequency": 1.0, "phase": 0.25 },
 *   "z": { "mode": "CIRCULAR", "amplitude": 2.0, "frequency": 0.5, "phase": 0.25 },
 *   "coupling": "INDEPENDENT"
 * }
 * </pre>
 * 
 * <h2>Atom Orbital Examples</h2>
 * <ul>
 *   <li><b>Horizontal orbit</b>: X=CIRCULAR, Y=NONE, Z=CIRCULAR (with 0.25 phase offset)</li>
 *   <li><b>Vertical orbit</b>: X=CIRCULAR, Y=CIRCULAR, Z=NONE</li>
 *   <li><b>3D helix</b>: X=CIRCULAR, Y=WAVE, Z=CIRCULAR</li>
 *   <li><b>Figure-8</b>: X=FIGURE_8, Y=WAVE, Z=WAVE</li>
 *   <li><b>Electron cloud</b>: All axes WAVE with different frequencies</li>
 * </ul>
 * 
 * <h2>Coupling Modes</h2>
 * <ul>
 *   <li><b>INDEPENDENT</b>: Each axis calculates motion independently</li>
 *   <li><b>LINKED_XZ</b>: X and Z form a coupled circular pair (typical horizontal orbit)</li>
 *   <li><b>LINKED_XY</b>: X and Y form a coupled pair (front-facing orbit)</li>
 *   <li><b>LINKED_YZ</b>: Y and Z form a coupled pair (side-facing orbit)</li>
 * </ul>
 * 
 * @see AxisMotionConfig
 * @see MotionMode
 * @see OrbitConfig (legacy single-axis config)
 */
public record OrbitConfig3D(
    @JsonField(skipIfDefault = true) boolean enabled,
    @JsonField(skipIfNull = true) AxisMotionConfig x,
    @JsonField(skipIfNull = true) AxisMotionConfig y,
    @JsonField(skipIfNull = true) AxisMotionConfig z,
    @JsonField(skipIfDefault = true) CouplingMode coupling
) {
    /**
     * How axes interact for coupled motion.
     */
    public enum CouplingMode {
        /** Each axis is fully independent. */
        INDEPENDENT("Independent"),
        /** X and Z are coupled (horizontal orbit plane). */
        LINKED_XZ("Linked XZ"),
        /** X and Y are coupled (frontal orbit plane). */
        LINKED_XY("Linked XY"),
        /** Y and Z are coupled (sagittal orbit plane). */
        LINKED_YZ("Linked YZ");
        
        private final String displayName;
        CouplingMode(String displayName) { this.displayName = displayName; }
        public String displayName() { return displayName; }
        
        public static CouplingMode fromId(String id) {
            if (id == null || id.isEmpty()) return INDEPENDENT;
            try {
                return valueOf(id.toUpperCase().replace("-", "_"));
            } catch (IllegalArgumentException e) {
                return INDEPENDENT;
            }
        }
    }
    
    /** Disabled 3D orbit. */
    public static final OrbitConfig3D NONE = new OrbitConfig3D(
        false, null, null, null, CouplingMode.INDEPENDENT);
    
    /** Default horizontal circular orbit (XZ plane). */
    public static final OrbitConfig3D HORIZONTAL_ORBIT = new OrbitConfig3D(
        true,
        AxisMotionConfig.circular(1.0f, 0.5f, 0f),
        null,
        AxisMotionConfig.circular(1.0f, 0.5f, 0.25f),
        CouplingMode.LINKED_XZ);
    
    /** Default vertical orbit (XY plane). */
    public static final OrbitConfig3D VERTICAL_ORBIT = new OrbitConfig3D(
        true,
        AxisMotionConfig.circular(1.0f, 0.5f, 0f),
        AxisMotionConfig.circular(1.0f, 0.5f, 0.25f),
        null,
        CouplingMode.LINKED_XY);
    
    /** Electron cloud (multi-frequency waves). */
    public static final OrbitConfig3D ELECTRON_CLOUD = new OrbitConfig3D(
        true,
        AxisMotionConfig.wave(0.5f, 1.0f),
        AxisMotionConfig.wave(0.3f, 1.5f),
        AxisMotionConfig.wave(0.5f, 0.8f),
        CouplingMode.INDEPENDENT);
    
    // =========================================================================
    // Factory Methods
    // =========================================================================
    
    /**
     * Creates a horizontal orbit (XZ plane).
     * @param radius Orbit radius
     * @param frequency Speed
     */
    public static OrbitConfig3D horizontalOrbit(float radius, float frequency) {
        return new OrbitConfig3D(
            true,
            AxisMotionConfig.circular(radius, frequency, 0f),
            null,
            AxisMotionConfig.circular(radius, frequency, 0.25f),
            CouplingMode.LINKED_XZ);
    }
    
    /**
     * Creates a tilted orbit with vertical wobble.
     * @param radius Orbit radius
     * @param frequency Orbit speed
     * @param wobbleAmplitude Vertical wobble amount
     */
    public static OrbitConfig3D tiltedOrbit(float radius, float frequency, float wobbleAmplitude) {
        return new OrbitConfig3D(
            true,
            AxisMotionConfig.circular(radius, frequency, 0f),
            AxisMotionConfig.wave(wobbleAmplitude, frequency * 2),
            AxisMotionConfig.circular(radius, frequency, 0.25f),
            CouplingMode.INDEPENDENT);
    }
    
    /**
     * Creates from legacy OrbitConfig.
     * @param legacy The old single-axis config
     * @return Equivalent 3D config
     */
    public static OrbitConfig3D fromLegacy(OrbitConfig legacy) {
        if (legacy == null || !legacy.isActive()) {
            return NONE;
        }
        
        float radius = legacy.radius();
        float speed = legacy.speed();
        float phase = legacy.phase();
        
        // Convert legacy axis to 3D config
        return switch (legacy.axis()) {
            case Y -> new OrbitConfig3D(
                true,
                AxisMotionConfig.circular(radius, speed, 0f),
                null,
                AxisMotionConfig.circular(radius, speed, phase + 0.25f),
                CouplingMode.LINKED_XZ);
            case X -> new OrbitConfig3D(
                true,
                null,
                AxisMotionConfig.circular(radius, speed, 0f),
                AxisMotionConfig.circular(radius, speed, phase + 0.25f),
                CouplingMode.LINKED_YZ);
            case Z -> new OrbitConfig3D(
                true,
                AxisMotionConfig.circular(radius, speed, 0f),
                AxisMotionConfig.circular(radius, speed, phase + 0.25f),
                null,
                CouplingMode.LINKED_XY);
            default -> HORIZONTAL_ORBIT;
        };
    }
    
    // =========================================================================
    // Query Methods
    // =========================================================================
    
    /** Whether any orbit motion is active. */
    public boolean isActive() {
        if (!enabled) return false;
        return (x != null && x.isActive()) ||
               (y != null && y.isActive()) ||
               (z != null && z.isActive());
    }
    
    /** Factory for disabled orbit. */
    public static OrbitConfig3D disabled() {
        return NONE;
    }
    
    // Immutable with* methods for single-property updates
    public OrbitConfig3D withEnabled(boolean e) { return toBuilder().enabled(e).build(); }
    public OrbitConfig3D withX(AxisMotionConfig c) { return toBuilder().x(c).build(); }
    public OrbitConfig3D withY(AxisMotionConfig c) { return toBuilder().y(c).build(); }
    public OrbitConfig3D withZ(AxisMotionConfig c) { return toBuilder().z(c).build(); }
    public OrbitConfig3D withCoupling(CouplingMode m) { return toBuilder().coupling(m).build(); }
    
    /** Whether X axis has motion. */
    public boolean hasXMotion() { return x != null && x.isActive(); }
    
    /** Whether Y axis has motion. */
    public boolean hasYMotion() { return y != null && y.isActive(); }
    
    /** Whether Z axis has motion. */
    public boolean hasZMotion() { return z != null && z.isActive(); }
    
    /** Count of active axes. */
    public int activeAxisCount() {
        int count = 0;
        if (hasXMotion()) count++;
        if (hasYMotion()) count++;
        if (hasZMotion()) count++;
        return count;
    }
    
    // =========================================================================
    // Motion Calculation
    // =========================================================================
    
    /**
     * Calculates the 3D offset at a given time.
     * 
     * <p>Creates proper circular orbits automatically:
     * <ul>
     *   <li>2D Orbit modes (CIRCULAR, ELLIPTIC, FIGURE_8): X→XZ plane, Y→YX plane, Z→ZY plane</li>
     *   <li>Single-axis modes (WAVE, OSCILLATION, BOUNCE): Only affect that axis</li>
     * </ul>
     *
     * <p>When 2 axes are orbit modes: combined diagonal orbit (additive).
     *
     * <p>When 3 axes are orbit modes: PRECESSING ORBIT - the base circle is rotated by
     * the third axis.
     * 
     * @param time Time in ticks
     * @return Offset vector from center
     */
    public Vector3f getOffset(float time) {
        if (!isActive()) {
            return new Vector3f(0, 0, 0);
        }
        
        // Check which axes are in orbit mode
        boolean xIsOrbit = x != null && x.isActive() && (x.is2DOrbit() || x.is3DOrbit());
        boolean yIsOrbit = y != null && y.isActive() && (y.is2DOrbit() || y.is3DOrbit());
        boolean zIsOrbit = z != null && z.isActive() && (z.is2DOrbit() || z.is3DOrbit());
        int orbitCount = (xIsOrbit ? 1 : 0) + (yIsOrbit ? 1 : 0) + (zIsOrbit ? 1 : 0);
        
        // NESTED ROTATION: When 2+ axes are orbit modes
        // Each axis adds a rotation, like an electron with multiple degrees of freedom
        if (orbitCount >= 2) {
            Vector3f result = getNestedRotationOffset(time, xIsOrbit, yIsOrbit, zIsOrbit);
            
            // Also add contributions from NON-orbit axes (e.g., LINEAR mode)
            if (x != null && x.isActive() && !xIsOrbit) {
                result.x += x.getDisplacement(time);
            }
            if (y != null && y.isActive() && !yIsOrbit) {
                result.y += y.getDisplacement(time);
            }
            if (z != null && z.isActive() && !zIsOrbit) {
                result.z += z.getDisplacement(time);
            }
            
            return result;
        }
        
        // Standard behavior for 0 or 1 orbit axes
        float offsetX = 0f;
        float offsetY = 0f;
        float offsetZ = 0f;
        
        // X axis motion - orbits in XZ plane (horizontal circle)
        if (x != null && x.isActive()) {
            if (xIsOrbit) {
                float radiusMult = x.getRadiusMultiplier(time);
                offsetX += x.getDisplacementCos(time);
                offsetZ += x.getDisplacement(time) * radiusMult;
                if (x.is3DOrbit()) {
                    offsetY += x.getDisplacementPerp(time);
                }
            } else {
                offsetX += x.getDisplacement(time);
            }
        }
        
        // Y axis motion - orbits in YX plane (vertical circle facing Z)
        if (y != null && y.isActive()) {
            if (yIsOrbit) {
                float radiusMult = y.getRadiusMultiplier(time);
                offsetY += y.getDisplacementCos(time);
                offsetX += y.getDisplacement(time) * radiusMult;
                if (y.is3DOrbit()) {
                    offsetZ += y.getDisplacementPerp(time);
                }
            } else {
                offsetY += y.getDisplacement(time);
            }
        }
        
        // Z axis motion - orbits in ZY plane (vertical circle facing X)
        if (z != null && z.isActive()) {
            if (zIsOrbit) {
                float radiusMult = z.getRadiusMultiplier(time);
                offsetZ += z.getDisplacementCos(time);
                offsetY += z.getDisplacement(time) * radiusMult;
                if (z.is3DOrbit()) {
                    offsetX += z.getDisplacementPerp(time);
                }
            } else {
                offsetZ += z.getDisplacement(time);
            }
        }

        return new Vector3f(offsetX, offsetY, offsetZ);
    }
    
    /**
     * Computes nested rotation orbit when 2+ axes are in orbit mode.
     * 
     * <p>Like an electron with multiple degrees of freedom:
     * <ul>
     *   <li>1st orbit axis: creates base circle</li>
     *   <li>2nd orbit axis: rotates that circle around its axis</li>
     *   <li>3rd orbit axis: rotates the result around another axis</li>
     * </ul>
     * Combined = 360° freedom (can reach any point on a sphere over time)
     * </p>
     */
    private Vector3f getNestedRotationOffset(float time, boolean xIsOrbit, boolean yIsOrbit, boolean zIsOrbit) {
        // TILTED PRECESSING ORBIT - Standard orbital mechanics formula
        // 
        // Parameters:
        //   θ (theta) = orbital angle: position along the circle (X controls)
        //   i = inclination: tilt of orbital plane from horizontal (Y controls)
        //   Ω (Omega) = precession: rotation of the tilted plane around Z axis (Z controls)
        //   R = radius: constant distance from center
        //
        // Formula:
        //   x = R * (cos(θ) * cos(Ω) - sin(θ) * cos(i) * sin(Ω))
        //   y = R * (cos(θ) * sin(Ω) + sin(θ) * cos(i) * cos(Ω))
        //   z = R * sin(θ) * sin(i)
        
        // Determine radius from first active orbit axis
        float radius = 0f;
        if (xIsOrbit) radius = x.amplitude();
        else if (yIsOrbit) radius = y.amplitude();
        else if (zIsOrbit) radius = z.amplitude();
        
        if (radius < 0.001f) {
            return new Vector3f(0, 0, 0);
        }
        
        // θ (theta) = orbital angle - controlled by X
        // This is where we are on the orbit circle (0 to 2π)
        float theta = 0f;
        if (xIsOrbit) {
            float t = (time / 20f) * x.frequency() + x.phase();
            theta = (float) (t * Math.PI * 2);
        }
        
        // i = inclination - controlled by Y
        // This is the tilt of the orbital plane
        // Y amplitude = max tilt in radians (scaled: amp=1 → 45°, amp=2 → 90°)
        // Y frequency = oscillation rate of the tilt
        float inclination = 0f;
        if (yIsOrbit) {
            float t = (time / 20f) * y.frequency() + y.phase();
            float maxTilt = (float) (y.amplitude() * Math.PI / 4);  // amp=2 → 90°
            inclination = (float) Math.sin(t * Math.PI * 2) * maxTilt;
        }
        
        // Ω (Omega) = precession - controlled by Z
        // This rotates the tilted orbital plane around the vertical axis
        float omega = 0f;
        if (zIsOrbit) {
            float t = (time / 20f) * z.frequency() + z.phase();
            omega = (float) (t * Math.PI * 2);
        }
        
        // Precompute trig values
        float cosTheta = (float) Math.cos(theta);
        float sinTheta = (float) Math.sin(theta);
        float cosI = (float) Math.cos(inclination);
        float sinI = (float) Math.sin(inclination);
        float cosOmega = (float) Math.cos(omega);
        float sinOmega = (float) Math.sin(omega);
        
        // Apply the tilted precessing orbit formula
        float posX = radius * (cosTheta * cosOmega - sinTheta * cosI * sinOmega);
        float posY = radius * sinTheta * sinI;  // Z in formula = Y in Minecraft (vertical)
        float posZ = radius * (cosTheta * sinOmega + sinTheta * cosI * cosOmega);
        
        // Add 3D perpendicular effects (HELIX, etc.)
        if (xIsOrbit && x.is3DOrbit()) {
            posY += x.getDisplacementPerp(time);
        }
        if (yIsOrbit && y.is3DOrbit()) {
            posX += y.getDisplacementPerp(time);
        }
        if (zIsOrbit && z.is3DOrbit()) {
            posY += z.getDisplacementPerp(time);
        }

        return new Vector3f(posX, posY, posZ);
    }
    
    /**
     * Computes a TILTED orbit when exactly 2 axes are in orbit mode.
     * 
     * <p>First orbit axis creates the base circle, second tilts it.</p>
     */
    @Deprecated // Now handled by getNestedRotationOffset
    private Vector3f getTiltedOffset(float time, boolean xIsOrbit, boolean yIsOrbit, boolean zIsOrbit) {
        AxisMotionConfig primary;
        AxisMotionConfig tiltAxis;
        int tiltDimension; // 0=X, 1=Y, 2=Z
        
        // Determine which axis is primary (circle) and which is tilt
        if (xIsOrbit && yIsOrbit) {
            primary = x;
            tiltAxis = y;
            tiltDimension = 1; // Tilt around Y axis
        } else if (xIsOrbit && zIsOrbit) {
            primary = x;
            tiltAxis = z;
            tiltDimension = 2; // Tilt around Z axis
        } else { // yIsOrbit && zIsOrbit
            primary = y;
            tiltAxis = z;
            tiltDimension = 0; // Tilt around X axis
        }
        
        // Step 1: Get base circle from primary axis
        float baseX = primary.getDisplacementCos(time);
        float baseY = primary.is3DOrbit() ? primary.getDisplacementPerp(time) : 0f;
        float baseZ = primary.getDisplacement(time) * primary.getRadiusMultiplier(time);
        
        // Step 2: Get tilt angle from secondary axis
        float tiltT = (time / 20f) * tiltAxis.frequency() + tiltAxis.phase();
        float tiltAngle = (float) (tiltT * Math.PI * 2) * tiltAxis.amplitude();
        float cosT = (float) Math.cos(tiltAngle);
        float sinT = (float) Math.sin(tiltAngle);
        
        // Step 3: Apply tilt rotation
        float finalX, finalY, finalZ;
        if (tiltDimension == 0) {
            // Tilt around X axis (Y-Z rotation)
            finalX = baseX;
            finalY = baseY * cosT - baseZ * sinT;
            finalZ = baseY * sinT + baseZ * cosT;
        } else if (tiltDimension == 1) {
            // Tilt around Y axis (X-Z rotation)
            finalX = baseX * cosT + baseZ * sinT;
            finalY = baseY;
            finalZ = -baseX * sinT + baseZ * cosT;
        } else {
            // Tilt around Z axis (X-Y rotation)
            finalX = baseX * cosT - baseY * sinT;
            finalY = baseX * sinT + baseY * cosT;
            finalZ = baseZ;
        }
        
        // Add tilt axis's perpendicular contribution if it's a 3D mode
        if (tiltAxis.is3DOrbit()) {
            float perpContrib = tiltAxis.getDisplacementPerp(time);
            if (tiltDimension == 0) finalX += perpContrib;
            else if (tiltDimension == 1) finalY += perpContrib;
            else finalZ += perpContrib;
        }

        return new Vector3f(finalX, finalY, finalZ);
    }
    
    /**
     * Computes a PRECESSING orbit when all 3 axes are in orbit mode.
     * 
     * <p>Full combo support - each axis contributes its special behavior:
     * <ul>
     *   <li>X config: base circle/shape (uses full getDisplacement/Cos/Perp)</li>
     *   <li>Y config: precession around Y + Y's own perpendicular motion</li>
     *   <li>Z config: nutation around X + Z's own perpendicular motion</li>
     * </ul>
     * 
     * <p>Examples:
     * <ul>
     *   <li>X=WOBBLE, Y=CIRCULAR, Z=CIRCULAR: rocking orbit that precesses</li>
     *   <li>X=HELIX, Y=WOBBLE, Z=CIRCULAR: helix + Y's rock + Z's precession</li>
     *   <li>X=PENDULUM, Y=CIRCULAR, Z=CIRCULAR: swinging arc that precesses</li>
     * </ul>
     */
    private Vector3f getPrecessingOffset(float time) {
        // ========== STEP 1: Get base shape from X config ==========
        // X creates the primary shape in XZ plane (with Y perp if 3D mode)
        float baseX = x.getDisplacementCos(time);
        float baseY = x.is3DOrbit() ? x.getDisplacementPerp(time) : 0f;
        float baseZ = x.getDisplacement(time) * x.getRadiusMultiplier(time);
        
        // ========== STEP 2: Apply Y's precession + Y's perpendicular ==========
        // Y config rotates around Y axis (precession)
        float tY = (time / 20f) * y.frequency() + y.phase();
        float rotY = (float) (tY * Math.PI * 2);
        
        // Scale rotation by amplitude (0 = no rotation, 1 = full rotation)
        float cosY = (float) Math.cos(rotY * y.amplitude());
        float sinY = (float) Math.sin(rotY * y.amplitude());
        
        // Rotate (X, Z) around Y axis
        float afterYx = baseX * cosY + baseZ * sinY;
        float afterYy = baseY;
        float afterYz = -baseX * sinY + baseZ * cosY;
        
        // ADD Y's perpendicular contribution (from WOBBLE/HELIX/etc on Y axis)
        if (y.is3DOrbit()) {
            // Y's perp affects Z (since Y orbits in YX plane, perp is Z)
            afterYz += y.getDisplacementPerp(time);
        }
        
        // ========== STEP 3: Apply Z's nutation + Z's perpendicular ==========
        // Z config rotates around X axis (nutation)
        float tZ = (time / 20f) * z.frequency() + z.phase();
        float rotX = (float) (tZ * Math.PI * 2);
        
        // Scale rotation by amplitude
        float cosX = (float) Math.cos(rotX * z.amplitude());
        float sinX = (float) Math.sin(rotX * z.amplitude());
        
        // Rotate (Y, Z) around X axis
        float finalX = afterYx;
        float finalY = afterYy * cosX - afterYz * sinX;
        float finalZ = afterYy * sinX + afterYz * cosX;
        
        // ADD Z's perpendicular contribution (from WOBBLE/HELIX/etc on Z axis)
        if (z.is3DOrbit()) {
            // Z's perp affects X (since Z orbits in ZY plane, perp is X)
            finalX += z.getDisplacementPerp(time);
        }

        return new Vector3f(finalX, finalY, finalZ);
    }
    
    // =========================================================================
    // JSON Parsing
    // =========================================================================
    
    
    
    
    
    // =========================================================================
    // Builder
    // =========================================================================
    
    public static Builder builder() { return new Builder(); }
    
    /** Creates a builder pre-populated with this config's values. */
    public Builder toBuilder() {
        return new Builder()
            .enabled(enabled)
            .x(x)
            .y(y)
            .z(z)
            .coupling(coupling);
    }
    
    public static class Builder {
        private boolean enabled = true;
        private AxisMotionConfig x = null;
        private AxisMotionConfig y = null;
        private AxisMotionConfig z = null;
        private CouplingMode coupling = CouplingMode.INDEPENDENT;
        
        public Builder enabled(boolean e) { this.enabled = e; return this; }
        public Builder x(AxisMotionConfig c) { this.x = c; return this; }
        public Builder y(AxisMotionConfig c) { this.y = c; return this; }
        public Builder z(AxisMotionConfig c) { this.z = c; return this; }
        public Builder coupling(CouplingMode m) { this.coupling = m; return this; }
        
        /** Convenience: set X to circular motion. */
        public Builder xCircular(float amplitude, float frequency) {
            this.x = AxisMotionConfig.circular(amplitude, frequency);
            return this;
        }
        
        /** Convenience: set Y to wave motion. */
        public Builder yWave(float amplitude, float frequency) {
            this.y = AxisMotionConfig.wave(amplitude, frequency);
            return this;
        }
        
        /** Convenience: set Z to circular with phase. */
        public Builder zCircular(float amplitude, float frequency, float phase) {
            this.z = AxisMotionConfig.circular(amplitude, frequency, phase);
            return this;
        }
        
        public OrbitConfig3D build() {
            return new OrbitConfig3D(enabled, x, y, z, coupling);
        }
    }
}
