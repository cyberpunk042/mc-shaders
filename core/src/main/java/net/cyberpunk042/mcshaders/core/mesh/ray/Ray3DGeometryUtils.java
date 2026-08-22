/*
 * Ported from the-virus-block-mc (net.cyberpunk042.client.visual.mesh), where it
 * is tessellation code with no Minecraft or graphics-API dependency: the whole
 * subtree imported nothing from net.minecraft or com.mojang.
 * Relicensed to MIT here by the author, per the engine/content split recorded in
 * docs/PORTING.md.
 *
 * The mod's logging system was replaced with core's Diag, which routes to
 * java.lang.System.Logger. The call sites are unchanged apart from the name.
 */
package net.cyberpunk042.mcshaders.core.mesh.ray;

import net.cyberpunk042.mcshaders.core.mesh.MeshBuilder;
import net.cyberpunk042.mcshaders.core.mesh.VectorMath;
import net.cyberpunk042.mcshaders.core.shape.ShapeMath;
import net.cyberpunk042.mcshaders.core.shape.SphereDeformation;

/**
 * Ray-specific 3D geometry utilities.
 * 
 * <p><b>Delegates to {@link VectorMath} for shared polar surface generation.</b></p>
 * 
 * <p>This class provides ray-specific convenience methods that use the core
 * polar surface algorithm from VectorMath with ray-appropriate defaults.</p>
 * 
 * @see VectorMath#generatePolarSurface
 * @see RaySphericalTessellator
 * @see ShapeMath
 */
public final class Ray3DGeometryUtils {
    
    private Ray3DGeometryUtils() {} // Utility class
    
    // ═══════════════════════════════════════════════════════════════════════════
    // Shape Functions (delegates to ShapeMath)
    // ═══════════════════════════════════════════════════════════════════════════
    
    /** Sphere: constant radius. Delegates to {@link ShapeMath#sphere}. */
    public static float shapeSphere(float theta) {
        return ShapeMath.sphere(theta);
    }
    
    /** Droplet: sin(θ/2)^power. Delegates to {@link ShapeMath#droplet}. */
    public static float shapeDroplet(float theta, float power) {
        return ShapeMath.droplet(theta, power);
    }
    
    /** Egg/oval: 1 + asymmetry × cos(θ). Delegates to {@link ShapeMath#egg}. */
    public static float shapeEgg(float theta, float asymmetry) {
        return ShapeMath.egg(theta, asymmetry);
    }
    
    /** Bullet: hemisphere tip + cylinder. Delegates to {@link ShapeMath#bullet}. */
    public static float shapeBullet(float theta) {
        return ShapeMath.bullet(theta);
    }
    
    /** Cone: θ/π (linear). Delegates to {@link ShapeMath#cone}. */
    public static float shapeCone(float theta) {
        return ShapeMath.cone(theta);
    }
    
    // ═══════════════════════════════════════════════════════════════════════════
    // Ray-Specific Convenience Methods (delegate to VectorMath)
    // ═══════════════════════════════════════════════════════════════════════════
    
    /**
     * Generates a droplet/teardrop shape for rays.
     * 
     * <p>Uses SphereDeformation.DROPLET.computeFullVertex - same approach as sphere!</p>
     * 
     * @param builder MeshBuilder to add geometry to
     * @param center Center position
     * @param direction Direction the tip points (normalized)
     * @param radius Radius at the fattest point
     * @param intensity Blend amount: 0=sphere, 1=full droplet
     * @param length Axial stretch: &lt;1 oblate, 1 normal, &gt;1 prolate
     * @param rings Number of latitude rings
     * @param segments Number of longitude segments
     * @param pattern Pattern for cell rendering (null = filled)
     * @param visibility Visibility mask (null = full visibility)
     */
    public static void generateDroplet(
            MeshBuilder builder,
            float[] center,
            float[] direction,
            float radius,
            float intensity,
            float length,
            int rings,
            int segments,
            net.cyberpunk042.mcshaders.core.pattern.VertexPattern pattern,
            net.cyberpunk042.mcshaders.core.visibility.VisibilityMask visibility) {
        // Call the full version with default flow values
        generateDroplet(builder, center, direction, radius, intensity, length,
                       rings, segments, pattern, visibility, 0.0f, 1.0f, 1.0f);
    }
    
    /**
     * Generates a droplet/teardrop shape for rays with flow animation support.
     * 
     * <p>Uses SphereDeformation.DROPLET.computeFullVertex with flow-based vertex alpha.</p>
     * 
     * @param builder MeshBuilder to add geometry to
     * @param center Center position
     * @param direction Direction the tip points (normalized)
     * @param radius Radius at the fattest point
     * @param intensity Blend amount: 0=sphere, 1=full droplet
     * @param length Axial stretch: &lt;1 oblate, 1 normal, &gt;1 prolate
     * @param rings Number of latitude rings
     * @param segments Number of longitude segments
     * @param pattern Pattern for cell rendering (null = filled)
     * @param visibility Visibility mask (null = full visibility)
     * @param visibleTStart Start of visible range on axis (0-1, for CLIP mode)
     * @param visibleTEnd End of visible range on axis (0-1, for CLIP mode)
     * @param flowAlpha Base alpha from flow animation (flicker, etc.)
     */
    public static void generateDroplet(
            MeshBuilder builder,
            float[] center,
            float[] direction,
            float radius,
            float intensity,
            float length,
            int rings,
            int segments,
            net.cyberpunk042.mcshaders.core.pattern.VertexPattern pattern,
            net.cyberpunk042.mcshaders.core.visibility.VisibilityMask visibility,
            float visibleTStart,
            float visibleTEnd,
            float flowAlpha) {
        // Call with no travel animation
        generateDropletWithTravel(builder, center, direction, radius, intensity, length,
            rings, segments, pattern, visibility, visibleTStart, visibleTEnd, flowAlpha,
            net.cyberpunk042.mcshaders.core.energy.EnergyTravel.NONE, 0f, 0f, 0, 0.3f);
    }
    
    /**
     * Generates a droplet with flow animation and EnergyTravel support.
     * 
     * <p>EnergyTravel controls per-vertex alpha gradients that animate along the droplet.</p>
     * 
     * @param builder MeshBuilder to add geometry to
     * @param center Center position
     * @param direction Direction the tip points (normalized)
     * @param radius Radius at the fattest point
     * @param intensity Blend amount: 0=sphere, 1=full droplet
     * @param length Axial stretch
     * @param rings Number of latitude rings
     * @param segments Number of longitude segments
     * @param pattern Pattern for cell rendering
     * @param visibility Visibility mask
     * @param visibleTStart Start of visible range on axis
     * @param visibleTEnd End of visible range on axis
     * @param flowAlpha Base alpha from flow animation
     * @param travelMode EnergyTravel type
     * @param travelPhase Current phase of travel animation (0-1)
     * @param travelSpeed Speed multiplier
     * @param chaseCount Number of chase particles
     * @param chaseWidth Width of each particle (0-1)
     */
    public static void generateDropletWithTravel(
            MeshBuilder builder,
            float[] center,
            float[] direction,
            float radius,
            float intensity,
            float length,
            int rings,
            int segments,
            net.cyberpunk042.mcshaders.core.pattern.VertexPattern pattern,
            net.cyberpunk042.mcshaders.core.visibility.VisibilityMask visibility,
            float visibleTStart,
            float visibleTEnd,
            float flowAlpha,
            net.cyberpunk042.mcshaders.core.energy.EnergyTravel travelMode,
            float travelPhase,
            float travelSpeed,
            int chaseCount,
            float chaseWidth) {
        
        // Use SphereDeformation.DROPLET - it handles intensity and length correctly!
        SphereDeformation deformation = SphereDeformation.DROPLET;
        
        // Capture finals for lambda
        final float finalVisibleTStart = visibleTStart;
        final float finalVisibleTEnd = visibleTEnd;
        final float finalFlowAlpha = flowAlpha;
        final float finalChaseWidth = Math.max(0.05f, chaseWidth);
        final int finalChaseCount = Math.max(1, chaseCount);
        
        VectorMath.FullVertexWithAlphaFunction vertexFunc = (theta, phi, r) -> {
            // Base vertex from deformation
            float[] vertex = deformation.computeFullVertex(theta, phi, r, intensity, length);
            
            // Compute t-value based on theta (0 at bottom, 1 at top)
            // theta goes from 0 (top) to PI (bottom), so invert for t
            float t = 1.0f - (theta / (float)Math.PI);
            
            // Start with base alpha
            float alpha = finalFlowAlpha;
            
            // === Apply visibility clipping for CLIP mode ===
            if (finalVisibleTStart > 0.0f || finalVisibleTEnd < 1.0f) {
                if (t < finalVisibleTStart) {
                    float fadeRange = 0.1f;
                    if (t < finalVisibleTStart - fadeRange) {
                        alpha = 0.0f;
                    } else {
                        alpha *= (t - (finalVisibleTStart - fadeRange)) / fadeRange;
                    }
                } else if (t > finalVisibleTEnd) {
                    float fadeRange = 0.1f;
                    if (t > finalVisibleTEnd + fadeRange) {
                        alpha = 0.0f;
                    } else {
                        alpha *= 1.0f - (t - finalVisibleTEnd) / fadeRange;
                    }
                }
            }
            
            // === Apply EnergyTravel alpha modulation ===
            if (travelMode != null && travelMode.isActive() && alpha > 0.001f) {
                float travelAlpha = computeTravelAlpha(t, travelMode, travelPhase, finalChaseCount, finalChaseWidth);
                alpha *= travelAlpha;
            }
            
            // Return vertex with alpha
            return new float[] { vertex[0], vertex[1], vertex[2], vertex[3], vertex[4], vertex[5], alpha };
        };
        
        // Use the grid generation with per-vertex alpha support
        VectorMath.generateLatLonGridFullOrientedWithAlpha(
            builder, center, direction, radius, rings, segments, vertexFunc, pattern, visibility);
    }
    
    /**
     * Computes the alpha value for a vertex based on EnergyTravel.
     * 
     * <p>Delegates to {@link net.cyberpunk042.mcshaders.core.mesh.ray.flow.FlowTravelStage#computeTravelAlpha}
     * which has the canonical implementation.</p>
     * 
     * @param t Vertex position along axis (0 = base, 1 = tip)
     * @param mode EnergyTravel type
     * @param phase Current animation phase (0-1)
     * @param chaseCount Number of chase particles
     * @param chaseWidth Width of each particle
     * @return Alpha multiplier (0-1)
     */
    private static float computeTravelAlpha(float t, net.cyberpunk042.mcshaders.core.energy.EnergyTravel mode, 
            float phase, int chaseCount, float chaseWidth) {
        return net.cyberpunk042.mcshaders.core.mesh.ray.flow.FlowTravelStage.computeTravelAlpha(
            t, mode, phase, chaseCount, chaseWidth);
    }
    
    /**
     * Generates a droplet with proper gravitational spaghettification.
     * 
     * <p>Applies per-vertex deformation based on tidal forces, following the physics formula:
     * <ol>
     *   <li>Compute direction from field center (black hole) to each vertex</li>
     *   <li>Compute tidal influence factor: K / distance³</li>
     *   <li>Separate vertex offset into radial and lateral components</li>
     *   <li>Stretch radial, compress lateral: displacement = radial*tidal - lateral*tidal</li>
     * </ol>
     *
     * <p>This creates authentic "spaghettification" - stretching toward center, thinning perpendicular.
     * 
     * @param builder MeshBuilder to add geometry to
     * @param dropletCenter Center position of droplet (in world space)
     * @param direction Direction the tip points (normalized)
     * @param radius Radius at the fattest point
     * @param intensity Blend amount: 0=sphere, 1=full droplet
     * @param length Axial stretch: &lt;1 oblate, 1 normal, &gt;1 prolate
     * @param rings Number of latitude rings
     * @param segments Number of longitude segments
     * @param pattern Pattern for cell rendering (null = filled)
     * @param visibility Visibility mask (null = full visibility)
     * @param visibleTStart Start of visible range on axis (0-1, for CLIP mode)
     * @param visibleTEnd End of visible range on axis (0-1, for CLIP mode)
     * @param flowAlpha Base alpha from flow animation (flicker, etc.)
     * @param fieldCenter Center of gravity (the "black hole" position)
     * @param gravityIntensity Strength of gravitational deformation (0 = none, 1+ = strong)
     * @param fieldOuterRadius Outer radius of field (for scaling the effect)
     * @param gravityMode Type of deformation: GRAVITATIONAL, REPULSION, or TIDAL
     */
    public static void generateDropletWithGravity(
            MeshBuilder builder,
            float[] dropletCenter,
            float[] direction,
            float radius,
            float intensity,
            float length,
            int rings,
            int segments,
            net.cyberpunk042.mcshaders.core.pattern.VertexPattern pattern,
            net.cyberpunk042.mcshaders.core.visibility.VisibilityMask visibility,
            float visibleTStart,
            float visibleTEnd,
            float flowAlpha,
            float[] fieldCenter,
            float gravityIntensity,
            float fieldOuterRadius,
            net.cyberpunk042.mcshaders.core.shape.FieldDeformationMode gravityMode) {
        
        // Skip if no gravity effect
        if (gravityMode == null || !gravityMode.isActive() || gravityIntensity <= 0.001f) {
            generateDroplet(builder, dropletCenter, direction, radius, intensity, length,
                rings, segments, pattern, visibility, visibleTStart, visibleTEnd, flowAlpha);
            return;
        }
        
        SphereDeformation deformation = SphereDeformation.DROPLET;
        
        // Final copies for lambda
        final float[] finalFieldCenter = fieldCenter != null ? fieldCenter : new float[] {0, 0, 0};
        final float[] finalDropletCenter = dropletCenter;
        final float finalOuterRadius = Math.max(0.1f, fieldOuterRadius);
        final float finalGravityIntensity = gravityIntensity;
        final float finalVisibleTStart = visibleTStart;
        final float finalVisibleTEnd = visibleTEnd;
        final float finalFlowAlpha = flowAlpha;
        final float finalRadius = radius;
        final float[] finalDirection = direction;
        
        // Pre-compute droplet center's distance and direction from field center
        float dcX = finalDropletCenter[0] - finalFieldCenter[0];
        float dcY = finalDropletCenter[1] - finalFieldCenter[1];
        float dcZ = finalDropletCenter[2] - finalFieldCenter[2];
        float dcDist = (float) Math.sqrt(dcX * dcX + dcY * dcY + dcZ * dcZ);
        
        // Direction FROM field center TO droplet center (normalized)
        final float[] dirFieldToDroplet = new float[3];
        if (dcDist > 0.001f) {
            dirFieldToDroplet[0] = dcX / dcDist;
            dirFieldToDroplet[1] = dcY / dcDist;
            dirFieldToDroplet[2] = dcZ / dcDist;
        } else {
            // If at center, default to up
            dirFieldToDroplet[0] = 0;
            dirFieldToDroplet[1] = 1;
            dirFieldToDroplet[2] = 0;
        }
        
        // Minimum distance to prevent division by zero
        final float minDist = finalOuterRadius * 0.05f;
        
        VectorMath.FullVertexWithAlphaFunction vertexFunc = (theta, phi, r) -> {
            // 1. Get base vertex from droplet deformation (local space)
            //    vertex = [x, y, z, nx, ny, nz] in LOCAL coordinates
            float[] vertex = deformation.computeFullVertex(theta, phi, r, intensity, length);
            
            float localX = vertex[0];  // Along droplet axis
            float localY = vertex[1];  // Perpendicular
            float localZ = vertex[2];  // Perpendicular
            
            // 2. Transform local vertex to world position
            //    We need a proper orientation frame. The droplet's direction vector is
            //    the primary axis. We need two perpendicular vectors.
            float[] up = {0, 1, 0};
            if (Math.abs(finalDirection[1]) > 0.99f) {
                up = new float[] {1, 0, 0}; // Avoid singularity
            }
            
            // Compute perpendicular vectors (tangent and bitangent)
            float[] tangent = new float[3];
            // tangent = cross(direction, up)
            tangent[0] = finalDirection[1] * up[2] - finalDirection[2] * up[1];
            tangent[1] = finalDirection[2] * up[0] - finalDirection[0] * up[2];
            tangent[2] = finalDirection[0] * up[1] - finalDirection[1] * up[0];
            float tangentLen = (float) Math.sqrt(tangent[0]*tangent[0] + tangent[1]*tangent[1] + tangent[2]*tangent[2]);
            if (tangentLen > 0.001f) {
                tangent[0] /= tangentLen;
                tangent[1] /= tangentLen;
                tangent[2] /= tangentLen;
            }
            
            // bitangent = cross(direction, tangent)
            float[] bitangent = new float[3];
            bitangent[0] = finalDirection[1] * tangent[2] - finalDirection[2] * tangent[1];
            bitangent[1] = finalDirection[2] * tangent[0] - finalDirection[0] * tangent[2];
            bitangent[2] = finalDirection[0] * tangent[1] - finalDirection[1] * tangent[0];
            
            // Transform local to world: worldPos = center + localX*direction + localY*tangent + localZ*bitangent
            float worldX = finalDropletCenter[0] + localX * finalDirection[0] + localY * tangent[0] + localZ * bitangent[0];
            float worldY = finalDropletCenter[1] + localX * finalDirection[1] + localY * tangent[1] + localZ * bitangent[1];
            float worldZ = finalDropletCenter[2] + localX * finalDirection[2] + localY * tangent[2] + localZ * bitangent[2];
            
            // 3. Compute vector FROM field center TO this vertex's world position
            float vfX = worldX - finalFieldCenter[0];
            float vfY = worldY - finalFieldCenter[1];
            float vfZ = worldZ - finalFieldCenter[2];
            float vfDist = (float) Math.sqrt(vfX * vfX + vfY * vfY + vfZ * vfZ);
            float effectiveDist = Math.max(vfDist, minDist);
            
            // Direction from field center to vertex (normalized)
            float dirX = vfX / effectiveDist;
            float dirY = vfY / effectiveDist;
            float dirZ = vfZ / effectiveDist;
            
            // 4. Compute tidal influence factor: K / dist³
            //    Scale K so that the effect is reasonable at the field's outer radius
            float kTidal = finalGravityIntensity * finalOuterRadius * finalOuterRadius * finalOuterRadius;
            float tidalFactor = kTidal / (effectiveDist * effectiveDist * effectiveDist);
            tidalFactor = Math.min(tidalFactor, 2.0f);  // Cap to prevent extreme distortion
            
            // 5. Compute vertex position RELATIVE to droplet center
            float relX = worldX - finalDropletCenter[0];
            float relY = worldY - finalDropletCenter[1];
            float relZ = worldZ - finalDropletCenter[2];
            
            // 6. Separate into radial and lateral components
            //    Radial = projection onto dirFieldToDroplet (toward center from droplet)
            float radialMag = relX * dirFieldToDroplet[0] + relY * dirFieldToDroplet[1] + relZ * dirFieldToDroplet[2];
            float radialX = radialMag * dirFieldToDroplet[0];
            float radialY = radialMag * dirFieldToDroplet[1];
            float radialZ = radialMag * dirFieldToDroplet[2];
            
            //    Lateral = perpendicular component
            float lateralX = relX - radialX;
            float lateralY = relY - radialY;
            float lateralZ = relZ - radialZ;
            
            // 7. Apply spaghettification: stretch radial, compress lateral
            //    displacement = radial * tidalFactor - lateral * tidalFactor
            float dispX, dispY, dispZ;
            
            switch (gravityMode) {
                case GRAVITATIONAL -> {
                    // Stretch toward center (radial), compress lateral
                    dispX = radialX * tidalFactor - lateralX * tidalFactor * 0.5f;
                    dispY = radialY * tidalFactor - lateralY * tidalFactor * 0.5f;
                    dispZ = radialZ * tidalFactor - lateralZ * tidalFactor * 0.5f;
                }
                case REPULSION -> {
                    // Stretch away from center (invert radial effect)
                    dispX = -radialX * tidalFactor - lateralX * tidalFactor * 0.5f;
                    dispY = -radialY * tidalFactor - lateralY * tidalFactor * 0.5f;
                    dispZ = -radialZ * tidalFactor - lateralZ * tidalFactor * 0.5f;
                }
                case TIDAL -> {
                    // Pure tidal: both stretch and compress more equally
                    dispX = radialX * tidalFactor * 0.8f - lateralX * tidalFactor * 0.8f;
                    dispY = radialY * tidalFactor * 0.8f - lateralY * tidalFactor * 0.8f;
                    dispZ = radialZ * tidalFactor * 0.8f - lateralZ * tidalFactor * 0.8f;
                }
                default -> {
                    dispX = 0; dispY = 0; dispZ = 0;
                }
            }
            
            // 8. New world position = original + displacement
            float newWorldX = worldX + dispX;
            float newWorldY = worldY + dispY;
            float newWorldZ = worldZ + dispZ;
            
            // 9. Transform back to local space for output
            //    localPos = worldPos - center, then inverse transform
            //    For simplicity, just output the displacement in local coordinates
            float newRelX = newWorldX - finalDropletCenter[0];
            float newRelY = newWorldY - finalDropletCenter[1];
            float newRelZ = newWorldZ - finalDropletCenter[2];
            
            // Inverse transform: project onto local axes
            float newLocalX = newRelX * finalDirection[0] + newRelY * finalDirection[1] + newRelZ * finalDirection[2];
            float newLocalY = newRelX * tangent[0] + newRelY * tangent[1] + newRelZ * tangent[2];
            float newLocalZ = newRelX * bitangent[0] + newRelY * bitangent[1] + newRelZ * bitangent[2];
            
            // 10. Compute t-value for visibility clipping
            float t = 1.0f - (theta / (float)Math.PI);
            
            // Apply visibility clipping for CLIP mode
            float alpha = finalFlowAlpha;
            if (finalVisibleTStart > 0.0f || finalVisibleTEnd < 1.0f) {
                if (t < finalVisibleTStart) {
                    float fadeRange = 0.1f;
                    if (t < finalVisibleTStart - fadeRange) {
                        alpha = 0.0f;
                    } else {
                        alpha *= (t - (finalVisibleTStart - fadeRange)) / fadeRange;
                    }
                } else if (t > finalVisibleTEnd) {
                    float fadeRange = 0.1f;
                    if (t > finalVisibleTEnd + fadeRange) {
                        alpha = 0.0f;
                    } else {
                        alpha *= 1.0f - (t - finalVisibleTEnd) / fadeRange;
                    }
                }
            }
            
            // Return deformed vertex with original normal (could also transform normal)
            return new float[] { newLocalX, newLocalY, newLocalZ, vertex[3], vertex[4], vertex[5], alpha };
        };
        
        VectorMath.generateLatLonGridFullOrientedWithAlpha(
            builder, dropletCenter, direction, radius, rings, segments, vertexFunc, pattern, visibility);
    }
    
    /**
     * Generates an egg/oval shape for rays.
     * 
     * <p>Delegates to {@link VectorMath#generatePolarSurface} with egg radius function.</p>
     */
    public static void generateEgg(
            MeshBuilder builder,
            float[] center,
            float[] direction,
            float radius,
            float asymmetry,
            int rings,
            int segments) {
        
        VectorMath.generatePolarSurface(builder, center, direction, radius, rings, segments,
            theta -> ShapeMath.egg(theta, asymmetry));
    }
    
    /**
     * Generates a sphere for rays.
     * 
     * <p>Delegates to {@link VectorMath#generatePolarSurface} with constant radius.</p>
     */
    public static void generateSphere(
            MeshBuilder builder,
            float[] center,
            float[] direction,
            float radius,
            int rings,
            int segments) {
        
        VectorMath.generatePolarSurface(builder, center, direction, radius, rings, segments, null);
    }
}
