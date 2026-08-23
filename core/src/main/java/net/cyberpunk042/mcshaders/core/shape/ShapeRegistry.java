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
package net.cyberpunk042.mcshaders.core.shape;

import java.util.*;
import java.util.function.Supplier;

/**
 * Registry for shape factories, allowing shape lookup by name.
 * 
 * <h2>Usage</h2>
 * <pre>
 * // Get a shape by name
 * Shape sphere = ShapeRegistry.create("sphere", 1.0f);
 * 
 * // Get with parameters
 * Shape ring = ShapeRegistry.create("ring", Map.of(
 *     "radius", 1.0f,
 *     "thickness", 0.1f,
 *     "segments", 48
 * ));
 * </pre>
 * 
 * @see Shape
 */
public final class ShapeRegistry {
    
    // ─────────────────────────────────────────────────────────────────────────
    // Shape Factories
    // ─────────────────────────────────────────────────────────────────────────
    
    @FunctionalInterface
    public interface ShapeFactory {
        Shape create(Map<String, Object> params);
    }
    
    private static final Map<String, ShapeFactory> FACTORIES = new LinkedHashMap<>();
    
    static {
        registerDefaults();
    }
    
    private ShapeRegistry() {}
    
    // ─────────────────────────────────────────────────────────────────────────
    // Registration
    // ─────────────────────────────────────────────────────────────────────────
    
    /**
     * Registers a shape factory.
     * 
     * @param name Shape name (lowercase)
     * @param factory Factory function
     */
    public static void register(String name, ShapeFactory factory) {
        FACTORIES.put(name.toLowerCase(), factory);

    }
    
    /**
     * Registers a simple shape (no parameters needed).
     */
    public static void registerSimple(String name, Supplier<Shape> supplier) {
        register(name, params -> supplier.get());
    }
    
    // ─────────────────────────────────────────────────────────────────────────
    // Creation
    // ─────────────────────────────────────────────────────────────────────────
    
    /**
     * Creates a shape by name with parameters.
     * 
     * <p>Null means <strong>no shape by that name</strong>, and nothing else. A factory
     * that fails is allowed to throw, which is a change: this used to catch every
     * exception and return null, so a bad parameter and an unknown name were the same
     * answer, with no way to tell which had happened and nothing logged.
     *
     * <p>The empty blocks that catch left behind are where the log calls stood before
     * the port — they could not come across, because {@code core} has no logger and is
     * not going to acquire one. Swallowing silently is the worse half of that trade, so
     * the exception travels instead.
     *
     * @param name Shape name
     * @param params Shape parameters
     * @return the shape, or null if no shape is registered under that name
     * @throws IllegalArgumentException if the name is null
     */
    public static Shape create(String name, Map<String, Object> params) {
        if (name == null) {
            throw new IllegalArgumentException("Cannot create a shape from a null name");
        }
        ShapeFactory factory = FACTORIES.get(name.toLowerCase());
        if (factory == null) {
            return null;
        }
        return factory.create(params);
    }
    
    /**
     * Creates a shape with a single radius parameter.
     */
    public static Shape create(String name, float radius) {
        return create(name, Map.of("radius", radius));
    }
    
    /**
     * Creates a shape with default parameters.
     */
    public static Shape create(String name) {
        return create(name, Map.of());
    }
    
    // ─────────────────────────────────────────────────────────────────────────
    // Queries
    // ─────────────────────────────────────────────────────────────────────────
    
    /**
     * Checks if a shape is registered.
     */
    public static boolean exists(String name) {
        return FACTORIES.containsKey(name.toLowerCase());
    }
    
    /**
     * Returns all registered shape names.
     */
    public static Set<String> names() {
        return Collections.unmodifiableSet(FACTORIES.keySet());
    }
    
    /**
     * Returns the number of registered shapes.
     */
    public static int count() {
        return FACTORIES.size();
    }
    
    // ─────────────────────────────────────────────────────────────────────────
    // Default Shapes
    // ─────────────────────────────────────────────────────────────────────────
    
    private static void registerDefaults() {
        // Sphere (supports partial sphere with latStart/End, lonStart/End, algorithm selection)
        register("sphere", params -> {
            float radius = getFloat(params, "radius", 1.0f);
            int latSteps = getInt(params, "latSteps", 32);
            int lonSteps = getInt(params, "lonSteps", latSteps * 2);
            float latStart = getFloat(params, "latStart", 0.0f);
            float latEnd = getFloat(params, "latEnd", 1.0f);
            float lonStart = getFloat(params, "lonStart", 0.0f);
            float lonEnd = getFloat(params, "lonEnd", 1.0f);
            SphereAlgorithm algorithm = SphereAlgorithm.values()[0];
            int deformCount = getInt(params, "deformationCount", 6);
            float deformSmoothness = getFloat(params, "deformationSmoothness", 0.5f);
            float deformBumpSize = getFloat(params, "deformationBumpSize", 0.5f);
            // Planet parameters
            float planetFrequency = getFloat(params, "planetFrequency", 2f);
            int planetOctaves = getInt(params, "planetOctaves", 4);
            float planetLacunarity = getFloat(params, "planetLacunarity", 2f);
            float planetPersistence = getFloat(params, "planetPersistence", 0.5f);
            float planetRidged = getFloat(params, "planetRidged", 0f);
            int planetCraterCount = getInt(params, "planetCraterCount", 0);
            int planetSeed = getInt(params, "planetSeed", 42);
            return new SphereShape(radius, latSteps, lonSteps, latStart, latEnd, lonStart, lonEnd, 
                algorithm, SphereDeformation.NONE, 0f, 1f, deformCount, deformSmoothness, 
                deformBumpSize, CloudStyle.GAUSSIAN, 42, 1.0f,
                planetFrequency, planetOctaves, planetLacunarity, planetPersistence, 
                planetRidged, planetCraterCount, planetSeed,
                false, 3f, 1.5f, 1f, 1f, 1f,  // horizon defaults
                false, 2f, 1f, 0.5f, 1f, 1f, 1f, 0f, 1f); // corona defaults (+ offset, width)
        });
        
        // Ring
        register("ring", params -> {
            float y = getFloat(params, "y", 0);
            float radius = getFloat(params, "radius", 1.0f);
            float thickness = getFloat(params, "thickness", 0.1f);
            int segments = getInt(params, "segments", 48);
            return RingShape.builder()
                .innerRadius(radius - thickness/2)
                .outerRadius(radius + thickness/2)
                .segments(segments)
                .y(y)
                .height(thickness)
                .build();
        });
        
        // Prism
        register("prism", params -> {
            int sides = getInt(params, "sides", 6);
            float radius = getFloat(params, "radius", 1.0f);
            float height = getFloat(params, "height", 1.0f);
            return new PrismShape(sides, radius, radius, height, 0f, 1, true, true);
        });
        
        // Beam
        register("beam", params -> {
            float radius = getFloat(params, "radius", 0.05f);
            float height = getFloat(params, "height", 256.0f);
            int segments = getInt(params, "segments", 8);
            return new CylinderShape(radius, height, segments, radius, 1, true, true, 360f);
        });
        
        // Polyhedron
        register("cube", params -> {
            float size = getFloat(params, "size", 1.0f);
            return PolyhedronShape.cube(size);
        });
        
        register("octahedron", params -> {
            float size = getFloat(params, "size", 1.0f);
            return PolyhedronShape.octahedron(size);
        });
        
        register("icosahedron", params -> {
            float size = getFloat(params, "size", 1.0f);
            return PolyhedronShape.icosahedron(size);
        });
        
        register("tetrahedron", params -> {
            float size = getFloat(params, "size", 1.0f);
            return PolyhedronShape.tetrahedron(size);
        });
        
        register("dodecahedron", params -> {
            float size = getFloat(params, "size", 1.0f);
            return PolyhedronShape.dodecahedron(size);
        });
        
        // Cylinder (plain, not beam-specific)
        register("cylinder", params -> {
            float radius = getFloat(params, "radius", 1.0f);
            float height = getFloat(params, "height", 2.0f);
            int segments = getInt(params, "segments", 32);
            return new CylinderShape(radius, height, segments, radius, 1, true, true, 360f);
        });
        
        // Torus (donut shape)
        register("torus", params -> {
            float majorRadius = getFloat(params, "majorRadius", 1.0f);
            float minorRadius = getFloat(params, "minorRadius", 0.25f);
            int majorSegments = getInt(params, "majorSegments", 32);
            int minorSegments = getInt(params, "minorSegments", 16);
            return new TorusShape(majorRadius, minorRadius, majorSegments, minorSegments, 0, 360);
        });
        
        // Capsule (cylinder with hemispherical caps)
        register("capsule", params -> {
            float radius = getFloat(params, "radius", 0.5f);
            float height = getFloat(params, "height", 2.0f);
            int segments = getInt(params, "segments", 32);
            int rings = getInt(params, "rings", 8);
            return new CapsuleShape(radius, height, segments, rings);
        });
        
        // Cone (or frustum if topRadius > 0)
        register("cone", params -> {
            float bottomRadius = getFloat(params, "bottomRadius", 1.0f);
            float topRadius = getFloat(params, "topRadius", 0f);
            float height = getFloat(params, "height", 2.0f);
            int segments = getInt(params, "segments", 32);
            return new ConeShape(bottomRadius, topRadius, height, segments, false, true);
        });
        
        // Jet (dual cone/tube for relativistic jets)
        register("jet", params -> {
            float length = getFloat(params, "length", 2.0f);
            float baseRadius = getFloat(params, "baseRadius", 0.3f);
            float topTipRadius = getFloat(params, "topTipRadius", 0f);
            float bottomTipRadius = getFloat(params, "bottomTipRadius", topTipRadius);
            int segments = getInt(params, "segments", 16);
            int lengthSegments = getInt(params, "lengthSegments", 1);
            boolean dualJets = params.containsKey("dualJets") ? 
                Boolean.parseBoolean(params.get("dualJets").toString()) : true;
            float gap = getFloat(params, "gap", 0f);
            boolean hollow = params.containsKey("hollow") ?
                Boolean.parseBoolean(params.get("hollow").toString()) : false;
            float innerBaseRadius = getFloat(params, "innerBaseRadius", 0f);
            float innerTipRadius = getFloat(params, "innerTipRadius", 0f);
            boolean unifiedInner = params.containsKey("unifiedInner") ?
                Boolean.parseBoolean(params.get("unifiedInner").toString()) : true;
            float innerWallThickness = getFloat(params, "innerWallThickness", 0.02f);
            boolean capBase = params.containsKey("capBase") ?
                Boolean.parseBoolean(params.get("capBase").toString()) : true;
            boolean capTip = params.containsKey("capTip") ?
                Boolean.parseBoolean(params.get("capTip").toString()) : false;
            float baseAlpha = getFloat(params, "baseAlpha", 1.0f);
            float baseMinAlpha = getFloat(params, "baseMinAlpha", 0f);
            float tipAlpha = getFloat(params, "tipAlpha", 1.0f);
            float tipMinAlpha = getFloat(params, "tipMinAlpha", 0f);
            return new JetShape(length, baseRadius, topTipRadius, bottomTipRadius, 
                segments, lengthSegments, dualJets, gap, hollow, 
                innerBaseRadius, innerTipRadius, unifiedInner, innerWallThickness,
                capBase, capTip, baseAlpha, baseMinAlpha, tipAlpha, tipMinAlpha);
        });
        
        // Molecule (procedural metaball-style shape)
        register("molecule", params -> {
            int atomCount = getInt(params, "atomCount", 4);
            float atomRadius = getFloat(params, "atomRadius", 0.3f);
            float atomDistance = getFloat(params, "atomDistance", 0.8f);
            float neckRadius = getFloat(params, "neckRadius", 0.12f);
            float neckPinch = getFloat(params, "neckPinch", 0.5f);
            float connectionDistance = getFloat(params, "connectionDistance", 1.2f);
            int seed = getInt(params, "seed", 42);
            String distName = getString(params, "distribution", "FIBONACCI");
            AtomDistribution distribution;
            try {
                distribution = AtomDistribution.valueOf(distName.toUpperCase());
            } catch (IllegalArgumentException e) {
                distribution = AtomDistribution.FIBONACCI;
            }
            return MoleculeShape.builder()
                .atomCount(atomCount)
                .atomRadius(atomRadius)
                .atomDistance(atomDistance)
                .neckRadius(neckRadius)
                .neckPinch(neckPinch)
                .connectionDistance(connectionDistance)
                .seed(seed)
                .distribution(distribution)
                .build();
        });
        
        // Rays (collection of straight line segments)
        register("rays", params -> {
            float rayLength = getFloat(params, "rayLength", 2.0f);
            float rayWidth = getFloat(params, "rayWidth", 1.0f);
            int count = getInt(params, "count", 12);
            String arrangementStr = getString(params, "arrangement", "RADIAL");
            RayArrangement arrangement = RayArrangement.fromString(arrangementStr);
            String distributionStr = getString(params, "distribution", "UNIFORM");
            RayDistribution distribution;
            try {
                distribution = RayDistribution.valueOf(distributionStr.toUpperCase());
            } catch (IllegalArgumentException e) {
                distribution = RayDistribution.UNIFORM;
            }
            float innerRadius = getFloat(params, "innerRadius", 0.5f);
            float outerRadius = getFloat(params, "outerRadius", 3.0f);
            int layers = getInt(params, "layers", 1);
            float layerSpacing = getFloat(params, "layerSpacing", 0.5f);
            float randomness = getFloat(params, "randomness", 0f);
            float lengthVariation = getFloat(params, "lengthVariation", 0f);
            float fadeStart = getFloat(params, "fadeStart", 1.0f);
            float fadeEnd = getFloat(params, "fadeEnd", 1.0f);
            int segments = getInt(params, "segments", 1);
            float segmentGap = getFloat(params, "segmentGap", 0f);
            // NEW: Line shape and curvature fields
            RayLineShape lineShape = RayLineShape.STRAIGHT;
            float lineShapeAmplitude = getFloat(params, "lineShapeAmplitude", 0.1f);
            float lineShapeFrequency = getFloat(params, "lineShapeFrequency", 2.0f);
            int lineResolution = getInt(params, "lineResolution", 16);
            RayCurvature curvature = RayCurvature.NONE;
            float curvatureIntensity = getFloat(params, "curvatureIntensity", 0f);
            RayType rayType = RayType.LINE;
            float shapeIntensity = getFloat(params, "shapeIntensity", 1.0f);
            float shapeLength = getFloat(params, "shapeLength", 1.0f);
            RayOrientation rayOrientation = RayOrientation.ALONG_RAY;
            RayLayerMode layerMode = RayLayerMode.VERTICAL;
            FieldDeformationMode fieldDeformation = FieldDeformationMode.NONE;
            float fieldDeformationIntensity = getFloat(params, "fieldDeformationIntensity", 0.0f);
            // Energy interaction fields (new in v2)
            net.cyberpunk042.mcshaders.core.energy.RadiativeInteraction radiativeInteraction = 
                net.cyberpunk042.mcshaders.core.energy.RadiativeInteraction.NONE;
            float segmentLength = getFloat(params, "segmentLength", 1.0f);
            float waveArc = getFloat(params, "waveArc", 1.0f);
            net.cyberpunk042.mcshaders.core.animation.WaveDistribution waveDistribution = 
                net.cyberpunk042.mcshaders.core.animation.WaveDistribution.SEQUENTIAL;
            float waveCount = getFloat(params, "waveCount", 2.0f);
            // Animation behavior fields (moved from RayFlowConfig)
            boolean startFullLength = params.containsKey("startFullLength") ?
                Boolean.parseBoolean(params.get("startFullLength").toString()) : false;
            boolean followCurve = params.containsKey("followCurve") ?
                Boolean.parseBoolean(params.get("followCurve").toString()) : true;
            boolean unifiedEnd = params.containsKey("unifiedEnd") ?
                Boolean.parseBoolean(params.get("unifiedEnd").toString()) : false;
            return new RaysShape(rayLength, rayWidth, count, arrangement, distribution, innerRadius, outerRadius,
                layers, layerSpacing, layerMode, unifiedEnd, randomness, lengthVariation, fadeStart, fadeEnd, segments, segmentGap,
                lineShape, lineShapeAmplitude, lineShapeFrequency, lineResolution, curvature, curvatureIntensity, 
                rayType, shapeIntensity, shapeLength, rayOrientation, fieldDeformation, fieldDeformationIntensity,
                null, // shapeState
                radiativeInteraction, segmentLength, waveArc, waveDistribution, waveCount,
                startFullLength, followCurve);
        });

    }
    
    // ─────────────────────────────────────────────────────────────────────────
    // Parameter Helpers
    // ─────────────────────────────────────────────────────────────────────────
    
    private static float getFloat(Map<String, Object> params, String key, float defaultValue) {
        Object value = params.get(key);
        if (value instanceof Number) {
            return ((Number) value).floatValue();
        }
        return defaultValue;
    }
    
    private static int getInt(Map<String, Object> params, String key, int defaultValue) {
        Object value = params.get(key);
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        return defaultValue;
    }
    
    private static String getString(Map<String, Object> params, String key, String defaultValue) {
        Object value = params.get(key);
        if (value instanceof String) {
            return (String) value;
        }
        return defaultValue;
    }
}
