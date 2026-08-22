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
package net.cyberpunk042.mcshaders.core.mesh;

import net.cyberpunk042.mcshaders.core.diag.Diag;
import net.cyberpunk042.mcshaders.core.animation.WaveConfig;
import net.cyberpunk042.mcshaders.core.pattern.VertexPattern;
import net.cyberpunk042.mcshaders.core.pattern.TrianglePattern;
import net.cyberpunk042.mcshaders.core.shape.MoleculeShape;
import net.cyberpunk042.mcshaders.core.shape.AtomDistribution;
import net.cyberpunk042.mcshaders.core.visibility.VisibilityMask;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Quaternionf;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Tessellates MoleculeShape into a combined mesh of spheres and connectors.
 * 
 * <h2>Algorithm</h2>
 * <ol>
 *   <li>Generate atom positions based on distribution pattern</li>
 *   <li>For each atom, generate a sphere mesh and transform to position</li>
 *   <li>For each pair of nearby atoms, generate a connector tube</li>
 *   <li>Combine all meshes into a single result</li>
 * </ol>
 * 
 * <h2>Connector Tubes</h2>
 * <p>Connectors are generated as tapered cylinders (hourglass shape when pinched)
 * oriented along the axis between two atom centers.</p>
 * 
 * @see MoleculeShape
 * @see AtomDistribution
 */
public final class MoleculeTessellator {
    
    private MoleculeTessellator() {}
    
    // =========================================================================
    // Main Tessellation Entry Point
    // =========================================================================
    
    /**
     * Tessellates a molecule shape into a combined mesh.
     */
    public static Mesh tessellate(MoleculeShape shape, VertexPattern pattern,
                                   VisibilityMask visibility,
                                   WaveConfig wave, float time) {
        if (shape == null) {
            throw new IllegalArgumentException("MoleculeShape cannot be null");
        }
        
        shape = shape.validated();  // Ensure valid parameters
        
        MeshBuilder builder = MeshBuilder.triangles();
        
        // Generate atom positions
        List<AtomInfo> atoms = generateAtomPositions(shape);
        
        Diag.RENDER.topic("tessellate")
            .kv("shape", "molecule")
            .kv("atoms", atoms.size())
            .debug("Generating molecule mesh");
        
        // Generate sphere mesh for each atom
        for (AtomInfo atom : atoms) {
            generateAtomSphere(builder, atom, shape, pattern);
        }
        
        // Generate connector tubes between nearby atoms
        List<int[]> connections = findConnections(atoms, shape);
        for (int[] conn : connections) {
            AtomInfo a1 = atoms.get(conn[0]);
            AtomInfo a2 = atoms.get(conn[1]);
            generateConnector(builder, a1, a2, shape, pattern);
        }
        
        Diag.RENDER.topic("tessellate")
            .kv("vertices", builder.vertexCount())
            .kv("connections", connections.size())
            .trace("Molecule tessellation complete");
        
        return builder.build();
    }
    
    public static Mesh tessellate(MoleculeShape shape) {
        return tessellate(shape, null, null, null, 0);
    }
    
    // =========================================================================
    // Atom Position Generation
    // =========================================================================
    
    private static List<AtomInfo> generateAtomPositions(MoleculeShape shape) {
        List<AtomInfo> atoms = new ArrayList<>();
        
        int count = shape.atomCount();
        float distance = shape.atomDistance() * shape.scale();
        float baseRadius = shape.atomRadius() * shape.scale();
        float sizeVar = shape.sizeVariation();
        int seed = shape.seed();
        
        // Get direction vectors based on distribution
        Vector3f[] directions = getDistributionDirections(shape.distribution(), count, seed);
        
        // Random for size variation
        Random rng = new Random(seed + 1000);
        
        for (int i = 0; i < directions.length && i < count; i++) {
            // Size variation: 1.0 ± sizeVar/2
            float sizeMult = 1.0f - sizeVar * 0.5f + sizeVar * rng.nextFloat();
            float radius = baseRadius * sizeMult;
            
            // Position is direction * distance
            Vector3f pos = new Vector3f(directions[i]).mul(distance);
            
            atoms.add(new AtomInfo(pos, radius, i));
        }
        
        return atoms;
    }
    
    /**
     * Returns unit direction vectors for the given distribution.
     */
    private static Vector3f[] getDistributionDirections(AtomDistribution dist, int count, int seed) {
        return switch (dist) {
            case FIBONACCI -> fibonacciSphereDirections(count, seed);
            case RANDOM -> randomSphereDirections(count, seed);
            case TETRAHEDRAL -> tetrahedralDirections();
            case OCTAHEDRAL -> octahedralDirections();
            case ICOSAHEDRAL -> icosahedralDirections();
            case LINEAR -> linearDirections();
        };
    }
    
    private static Vector3f[] fibonacciSphereDirections(int n, int seed) {
        Vector3f[] dirs = new Vector3f[n];
        float goldenAngle = (float) (Math.PI * (3 - Math.sqrt(5)));  // ~2.4 radians
        
        // Seed can offset the starting angle
        float offset = (seed % 100) * 0.01f * (float) Math.PI;
        
        for (int i = 0; i < n; i++) {
            float t = (i + 0.5f) / n;
            float theta = (float) Math.acos(1 - 2 * t);
            float phi = goldenAngle * i + offset;
            
            float x = (float) (Math.sin(theta) * Math.cos(phi));
            float y = (float) Math.cos(theta);
            float z = (float) (Math.sin(theta) * Math.sin(phi));
            
            dirs[i] = new Vector3f(x, y, z);
        }
        return dirs;
    }
    
    private static Vector3f[] randomSphereDirections(int n, int seed) {
        Vector3f[] dirs = new Vector3f[n];
        Random rng = new Random(seed);
        
        for (int i = 0; i < n; i++) {
            // Random point on sphere using Marsaglia method
            float x, y, z, s;
            do {
                x = rng.nextFloat() * 2 - 1;
                y = rng.nextFloat() * 2 - 1;
                s = x * x + y * y;
            } while (s >= 1);
            
            float factor = 2 * (float) Math.sqrt(1 - s);
            dirs[i] = new Vector3f(x * factor, y * factor, 1 - 2 * s).normalize();
        }
        return dirs;
    }
    
    private static Vector3f[] tetrahedralDirections() {
        float s = 1.0f / (float) Math.sqrt(3);
        return new Vector3f[] {
            new Vector3f(s, s, s),
            new Vector3f(s, -s, -s),
            new Vector3f(-s, s, -s),
            new Vector3f(-s, -s, s)
        };
    }
    
    private static Vector3f[] octahedralDirections() {
        return new Vector3f[] {
            new Vector3f(1, 0, 0),
            new Vector3f(-1, 0, 0),
            new Vector3f(0, 1, 0),
            new Vector3f(0, -1, 0),
            new Vector3f(0, 0, 1),
            new Vector3f(0, 0, -1)
        };
    }
    
    private static Vector3f[] icosahedralDirections() {
        // Golden ratio
        float phi = (1 + (float) Math.sqrt(5)) / 2;
        float a = 1.0f / (float) Math.sqrt(1 + phi * phi);
        float b = phi * a;
        
        return new Vector3f[] {
            new Vector3f(0, a, b), new Vector3f(0, a, -b),
            new Vector3f(0, -a, b), new Vector3f(0, -a, -b),
            new Vector3f(a, b, 0), new Vector3f(a, -b, 0),
            new Vector3f(-a, b, 0), new Vector3f(-a, -b, 0),
            new Vector3f(b, 0, a), new Vector3f(b, 0, -a),
            new Vector3f(-b, 0, a), new Vector3f(-b, 0, -a)
        };
    }
    
    private static Vector3f[] linearDirections() {
        return new Vector3f[] {
            new Vector3f(0, 1, 0),
            new Vector3f(0, -1, 0)
        };
    }
    
    // =========================================================================
    // Connection Detection
    // =========================================================================
    
    /**
     * Finds pairs of atoms that should be connected.
     * Returns list of [index1, index2] pairs.
     */
    private static List<int[]> findConnections(List<AtomInfo> atoms, MoleculeShape shape) {
        List<int[]> connections = new ArrayList<>();
        
        float maxDist = shape.connectionDistance() * shape.atomDistance() * shape.scale();
        
        for (int i = 0; i < atoms.size(); i++) {
            for (int j = i + 1; j < atoms.size(); j++) {
                AtomInfo a1 = atoms.get(i);
                AtomInfo a2 = atoms.get(j);
                
                float dist = a1.position.distance(a2.position);
                
                // Connect if distance is within threshold
                // Adjust for atom radii - we connect if surfaces are close
                float surfaceDist = dist - a1.radius - a2.radius;
                
                if (surfaceDist < maxDist * 0.5f) {
                    connections.add(new int[]{i, j});
                }
            }
        }
        
        return connections;
    }
    
    // =========================================================================
    // Atom Sphere Generation
    // =========================================================================
    
    /**
     * Generates a sphere at the atom position.
     */
    private static void generateAtomSphere(MeshBuilder builder, AtomInfo atom,
                                            MoleculeShape shape, VertexPattern pattern) {
        int latSteps = shape.atomLatSteps();
        int lonSteps = shape.atomLonSteps();
        float radius = atom.radius;
        Vector3f pos = atom.position;
        
        // Generate sphere vertices with indices for later quad emission
        int[][] indices = new int[latSteps + 1][lonSteps + 1];
        
        for (int lat = 0; lat <= latSteps; lat++) {
            float theta = (float) Math.PI * lat / latSteps;
            for (int lon = 0; lon <= lonSteps; lon++) {
                float phi = 2 * (float) Math.PI * lon / lonSteps;
                indices[lat][lon] = builder.addVertex(sphereVertex(theta, phi, radius, pos));
            }
        }
        
        // Generate quads using pattern support
        for (int lat = 0; lat < latSteps; lat++) {
            for (int lon = 0; lon < lonSteps; lon++) {
                // Four corners of the quad
                int i00 = indices[lat][lon];
                int i01 = indices[lat][lon + 1];
                int i10 = indices[lat + 1][lon];
                int i11 = indices[lat + 1][lon + 1];
                
                // Handle poles (triangles instead of quads)
                if (lat == 0) {
                    // Top pole - single triangle (pole is degenerate)
                    builder.triangle(i00, i10, i11);
                } else if (lat == latSteps - 1) {
                    // Bottom pole - single triangle
                    builder.triangle(i00, i10, i01);
                } else {
                    // Regular quad - use pattern-aware emission
                    // TL=i00, TR=i01, BR=i11, BL=i10
                    builder.quadAsTrianglesFromPattern(i00, i01, i11, i10, pattern);
                }
            }
        }
    }
    
    private static Vertex sphereVertex(float theta, float phi, float radius, Vector3f center) {
        float sinTheta = (float) Math.sin(theta);
        float cosTheta = (float) Math.cos(theta);
        float sinPhi = (float) Math.sin(phi);
        float cosPhi = (float) Math.cos(phi);
        
        // Normal (on unit sphere)
        float nx = sinTheta * cosPhi;
        float ny = cosTheta;
        float nz = sinTheta * sinPhi;
        
        // Position
        float x = center.x + radius * nx;
        float y = center.y + radius * ny;
        float z = center.z + radius * nz;
        
        // UV coordinates
        float u = phi / (2 * (float) Math.PI);
        float v = theta / (float) Math.PI;
        
        return new Vertex(x, y, z, nx, ny, nz, u, v, 1.0f);
    }
    
    // =========================================================================
    // Connector Tube Generation
    // =========================================================================
    
    /**
     * Generates a connector tube between two atoms.
     * 
     * <p>The tube is an hourglass shape (tapered cylinder) that connects
     * the surfaces of the two atom spheres.</p>
     */
    private static void generateConnector(MeshBuilder builder, AtomInfo a1, AtomInfo a2,
                                           MoleculeShape shape, VertexPattern pattern) {
        Vector3f p1 = a1.position;
        Vector3f p2 = a2.position;
        float r1 = a1.radius;
        float r2 = a2.radius;
        
        // Vector from p1 to p2
        Vector3f axis = new Vector3f(p2).sub(p1);
        float length = axis.length();
        axis.normalize();
        
        // Start and end points INSIDE atoms (not at surface) for visual connection
        Vector3f start = new Vector3f(p1).add(new Vector3f(axis).mul(r1 * 0.5f));
        Vector3f end = new Vector3f(p2).sub(new Vector3f(axis).mul(r2 * 0.5f));
        
        // Tube length (between surfaces)
        float tubeLength = start.distance(end);
        if (tubeLength <= 0) return;  // Atoms overlapping
        
        // Neck parameters
        float neckRadius = shape.neckRadius() * shape.scale();
        float pinch = shape.neckPinch();
        int segments = shape.neckSegments();
        int rings = shape.neckRings();
        
        // Build rotation matrix to orient tube along axis
        // Default tube is along Y axis, we need to rotate to 'axis'
        Vector3f up = new Vector3f(0, 1, 0);
        Quaternionf rotation = new Quaternionf().rotationTo(up, axis);
        
        // Generate tube rings
        for (int ring = 0; ring < rings; ring++) {
            float t1 = (float) ring / rings;
            float t2 = (float) (ring + 1) / rings;
            
            // Positions along tube
            Vector3f ringPos1 = new Vector3f(start).lerp(end, t1);
            Vector3f ringPos2 = new Vector3f(start).lerp(end, t2);
            
            // Radius at each ring (hourglass: thin in middle)
            float rad1 = tubeRadius(t1, neckRadius, pinch);
            float rad2 = tubeRadius(t2, neckRadius, pinch);
            
            // Generate quads around this ring section
            for (int seg = 0; seg < segments; seg++) {
                float angle1 = 2 * (float) Math.PI * seg / segments;
                float angle2 = 2 * (float) Math.PI * (seg + 1) / segments;
                
                // Four vertices of this quad
                Vertex v00 = tubeVertex(ringPos1, rad1, angle1, axis, rotation);
                Vertex v01 = tubeVertex(ringPos1, rad1, angle2, axis, rotation);
                Vertex v10 = tubeVertex(ringPos2, rad2, angle1, axis, rotation);
                Vertex v11 = tubeVertex(ringPos2, rad2, angle2, axis, rotation);
                
                int i00 = builder.addVertex(v00);
                int i01 = builder.addVertex(v01);
                int i10 = builder.addVertex(v10);
                int i11 = builder.addVertex(v11);
                
                // Quad with pattern support: TL=i00, TR=i01, BR=i11, BL=i10
                builder.quadAsTrianglesFromPattern(i00, i01, i11, i10, pattern);
            }
        }
    }
    
    /**
     * Computes tube radius at position t (0=start, 1=end).
     * Creates hourglass shape based on pinch factor.
     */
    private static float tubeRadius(float t, float baseRadius, float pinch) {
        // Parabolic pinch: 1 at ends, (1-pinch) at middle
        float distFromMiddle = Math.abs(t - 0.5f) * 2;  // 0 at middle, 1 at ends
        float pinchFactor = 1 - pinch * (1 - distFromMiddle * distFromMiddle);
        return baseRadius * pinchFactor;
    }
    
    /**
     * Creates a vertex on the tube surface.
     */
    private static Vertex tubeVertex(Vector3f center, float radius, float angle,
                                      Vector3f axis, Quaternionf rotation) {
        // Local position in tube space (around Y axis)
        float lx = (float) Math.cos(angle) * radius;
        float lz = (float) Math.sin(angle) * radius;
        
        // Rotate to world orientation
        Vector3f localOffset = new Vector3f(lx, 0, lz);
        rotation.transform(localOffset);
        
        // World position
        float x = center.x + localOffset.x;
        float y = center.y + localOffset.y;
        float z = center.z + localOffset.z;
        
        // Normal points radially outward
        Vector3f normal = new Vector3f(localOffset).normalize();
        
        // UV coordinates
        float u = angle / (2 * (float) Math.PI);
        float v = 0.5f;  // Middle of texture
        
        return new Vertex(x, y, z, normal.x, normal.y, normal.z, u, v, 1.0f);
    }
    
    // =========================================================================
    // Helper Classes
    // =========================================================================
    
    private record AtomInfo(Vector3f position, float radius, int index) {}
}
