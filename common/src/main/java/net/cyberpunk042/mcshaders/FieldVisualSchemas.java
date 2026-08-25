package net.cyberpunk042.mcshaders;

import net.cyberpunk042.mcshaders.core.schema.EffectSchema;
import net.cyberpunk042.mcshaders.core.schema.ParamSpec;

/**
 * What is tunable on {@code the-virus-block-mc}'s two field-visual effects.
 *
 * <p>Derived from the {@code FieldVisualConfig} uniform block its shaders read and from
 * the thirteen presets that feed it. {@code docs/VIRUS-BLOCK-FIELD-VISUAL-STATE.md} is
 * the measurement this was built on, and its numbers are reproducible from the script
 * on that page.
 *
 * <h2>Nothing here is registered</h2>
 *
 * <p>{@code BuiltinEffects} claims {@code mcshaders:fog} "and nothing else", and these
 * are not this mod's effects to claim: {@code ENERGY_ORB} and {@code GEODESIC} belong to
 * another mod. So the effect type is an <strong>argument</strong>, and whoever owns the
 * type registers it in their own namespace:
 *
 * <pre>{@code
 * McShadersAPI.registerSchema(myType, FieldVisualSchemas.energyOrb(myType));
 * }</pre>
 *
 * <h2>The presets are the authority, not the comments</h2>
 *
 * <p>The block documents a range for four of these parameters and <strong>two of the
 * four are contradicted by the shipped presets</strong>: {@code coreSize} is documented
 * {@code 0-1} and reaches 10, {@code intensity} is documented {@code 0-2} and reaches
 * 4.32. {@code geoAnimMode} is the same story in another form — its comment enumerates
 * four modes and a preset uses a fifth value, which is why it is a slider here rather
 * than a choice.
 *
 * <p>So every bound widens to admit every value there is evidence for: the stated
 * range, the values the comment <em>enumerates</em>, everything the presets use, and the
 * fallback itself. The enumerated ones matter more than they look — {@code geoDomeClip}
 * documents {@code 0=sphere, 0.5=hemisphere, 1=flat} while no preset ever sets it above
 * {@code 0.5}, so deriving its bound from content alone put "flat" out of reach of an
 * editor entirely. It is the only parameter that rule changes, and it would have been a
 * feature quietly missing rather than a value visibly wrong. Getting that
 * rule half-right is the one bug this file has had: an earlier draft applied the
 * widening only where a range was stated, and {@code v2CoronaBrightness} — whose comment
 * gives a default of {@code 0.15} while three presets set it to {@code 1.0} — came out
 * bounded at {@code [0, 0.3]}. {@code FieldVisualSchemasTest} caught it against the real
 * presets; nothing about the file looked wrong.
 *
 * <h2>Where each bound comes from</h2>
 *
 * <ul>
 *   <li><strong>4 from the block's comments.</strong></li>
 *   <li><strong>31 from the observed spread</strong> — the presets disagree, so the
 *       range they cover is real evidence of an intended span.</li>
 *   <li><strong>40 placeholders</strong> — every preset sets one value, so there is no
 *       evidence of a limit at all. These span {@code [0, 2v]} around that value.
 *       <em>A guess about presentation, not a fact about the shader.</em></li>
 * </ul>
 *
 * <p>The placeholders exist because {@link net.cyberpunk042.mcshaders.core.schema.ControlKind}
 * has no free-numeric-entry option: a scalar of genuinely unknown range cannot be
 * described honestly, since {@code SLIDER} with {@code Bounds.NONE} renders as a control
 * from 0 to 0. Adding a {@code NUMBER} kind would fix it, and is an API change rather
 * than a correction, so it is not taken here.
 *
 * <h2>Labels are mechanical, deliberately</h2>
 *
 * <p>The block annotates 149 of its members with a description, and those would make
 * better labels than {@code "Core Size"}. They are not used. That mod is CC BY-ND-NC —
 * <em>NoDerivatives</em> — and copying its prose into an MIT artifact is the packaging
 * error {@code PORTING.md} warns about. Numbers extracted from those comments are facts
 * and are used; the sentences are expression and are not. The same author owns both
 * repositories, so richer labels are theirs to permit.
 *
 * <h2>Two keys the presets write are absent</h2>
 *
 * <p>{@code animationSpeed} and {@code previewRadius} appear in every preset and have no
 * reader anywhere in that mod's 1,040 Java files. A schema describing a parameter the
 * effect does not have is a control that edits nothing, so they stay out until something
 * is known to read them.
 */
public final class FieldVisualSchemas {

    private FieldVisualSchemas() {
    }

    private static String required(String effectType) {
        if (effectType == null || effectType.isBlank()) {
            throw new IllegalArgumentException(
                    "an effect type is required: these schemas describe another mod's "
                            + "effects, so the caller names them in its own namespace");
        }
        return effectType;
    }

    public static EffectSchema energyOrb(String effectType) {
        return EffectSchema.builder("Energy Orb", required(effectType), 1)
                .group("Animation base",
                        ParamSpec.slider("intensity", "Intensity", 0, 5, 1, "Animation base"))
                .group("Animation multi-speed channels",
                        ParamSpec.slider("speedHigh", "Speed High", 0, 2, 2, "Animation multi-speed channels"),
                        ParamSpec.slider("speedLow", "Speed Low", 0, 2, 2, "Animation multi-speed channels"),
                        ParamSpec.slider("speedRay", "Speed Ray", 0, 10, 5, "Animation multi-speed channels"),
                        ParamSpec.slider("speedRing", "Speed Ring", 0, 4, 2, "Animation multi-speed channels"))
                .group("Animation timing modifiers",
                        ParamSpec.slider("radialSpeed1", "Radial Speed 1", 0, 0.7, 0.35, "Animation timing modifiers"),
                        ParamSpec.slider("radialSpeed2", "Radial Speed 2", 0, 0.3, 0.15, "Animation timing modifiers"),
                        ParamSpec.slider("axialSpeed", "Axial Speed", 0, 0.03, 0.015, "Animation timing modifiers"))
                .group("Core/Edge",
                        ParamSpec.slider("coreSize", "Core Size", 0, 10, 0.24, "Core/Edge"),
                        ParamSpec.slider("edgeSharpness", "Edge Sharpness", 0, 8, 1, "Core/Edge"),
                        ParamSpec.slider("coreFalloff", "Core Falloff", 0, 100, 4, "Core/Edge"))
                .group("Falloff",
                        ParamSpec.slider("fadePower", "Fade Power", 0, 100, 0.5, "Falloff"),
                        ParamSpec.slider("fadeScale", "Fade Scale", 0, 50, 2, "Falloff"),
                        ParamSpec.slider("insideFalloffPower", "Inside Falloff Power", 0, 30, 24, "Falloff"),
                        ParamSpec.slider("coronaEdge", "Corona Edge", 0, 3, 1.1, "Falloff"))
                .group("Noise config",
                        ParamSpec.slider("noiseResLow", "Noise Res Low", 0, 20, 15, "Noise config"),
                        ParamSpec.slider("noiseResHigh", "Noise Res High", 0, 50, 45, "Noise config"),
                        ParamSpec.slider("noiseAmplitude", "Noise Amplitude", 0, 2, 2, "Noise config"),
                        ParamSpec.slider("noiseSeed", "Noise Seed", 0, 1, 0, "Noise config"))
                .group("Noise detail",
                        ParamSpec.slider("noiseBaseScale", "Noise Base Scale", 0, 10, 10, "Noise detail"),
                        ParamSpec.slider("noiseScaleMultiplier", "Noise Scale Multiplier", 0, 30, 4, "Noise detail"),
                        ParamSpec.slider("noiseOctaves", "Noise Octaves", 0, 10, 7, "Noise detail"))
                .group("Glow line",
                        ParamSpec.slider("glowLineCount", "Glow Line Count", 0, 20, 8, "Glow line"),
                        ParamSpec.slider("glowLineIntensity", "Glow Line Intensity", 0, 1, 1, "Glow line"),
                        ParamSpec.slider("rayPower", "Ray Power", 0, 4, 2, "Glow line"),
                        ParamSpec.slider("raySharpness", "Ray Sharpness", 0, 1, 1, "Glow line"))
                .group("Corona",
                        ParamSpec.slider("coronaWidth", "Corona Width", 0, 2, 0.5, "Corona"),
                        ParamSpec.slider("coronaPower", "Corona Power", 0, 20, 2, "Corona"),
                        ParamSpec.slider("coronaMultiplier", "Corona Multiplier", 0, 400, 50, "Corona"),
                        ParamSpec.slider("ringPower", "Ring Power", 0, 2, 1, "Corona"))
                .group("Transform",
                        ParamSpec.slider("transScale", "Trans Scale", 0, 2, 1, "Transform"))
                .group("Lighting",
                        ParamSpec.slider("lightDiffuse", "Light Diffuse", 0, 2, 0.3, "Lighting"),
                        ParamSpec.slider("lightAmbient", "Light Ambient", 0, 0.8, 0.75, "Lighting"))
                .group("Screen effects",
                        ParamSpec.slider("blackout", "Blackout", 0, 1, 1, "Screen effects"))
                .group("Distortion",
                        ParamSpec.slider("distortionStrength", "Distortion Strength", 0, 1, 0, "Distortion"),
                        ParamSpec.slider("distortionRadius", "Distortion Radius", 0, 1000, 1000, "Distortion"),
                        ParamSpec.slider("distortionFrequency", "Distortion Frequency", 0, 0.2, 0.1, "Distortion"))
                .group("V2 Corona Detail",
                        ParamSpec.slider("v2CoronaStart", "V 2 Corona Start", 0, 0.3, 0.15, "V2 Corona Detail"),
                        ParamSpec.slider("v2CoronaBrightness", "V 2 Corona Brightness", 0, 1, 0.15, "V2 Corona Detail"),
                        ParamSpec.slider("v2CoreMaskRadius", "V 2 Core Mask Radius", 0, 0.7, 0.35, "V2 Corona Detail"))
                .group("V2 Core Detail",
                        ParamSpec.slider("v2CoreSpread", "V 2 Core Spread", 0, 2, 1, "V2 Core Detail"),
                        ParamSpec.slider("v2CoreGlow", "V 2 Core Glow", 0, 2, 1, "V2 Core Detail"),
                        ParamSpec.slider("v2CoreMaskSoft", "V 2 Core Mask Soft", 0, 0.1, 0.05, "V2 Core Detail"),
                        ParamSpec.slider("v2EdgeRadius", "V 2 Edge Radius", 0, 0.6, 0.3, "V2 Core Detail"))
                .group("V2 Edge Detail",
                        ParamSpec.slider("v2EdgeSpread", "V 2 Edge Spread", 0, 2, 1, "V2 Edge Detail"),
                        ParamSpec.slider("v2EdgeGlow", "V 2 Edge Glow", 0, 2, 1, "V2 Edge Detail"),
                        ParamSpec.slider("v2SharpScale", "V 2 Sharp Scale", 0, 8, 4, "V2 Edge Detail"),
                        ParamSpec.slider("v2LinesUVScale", "V 2 Lines U V Scale", 0, 6, 3, "V2 Edge Detail"))
                .group("V2 Lines Detail",
                        ParamSpec.slider("v2LinesDensityMult", "V 2 Lines Density Mult", 0, 4, 1.6, "V2 Lines Detail"),
                        ParamSpec.slider("v2LinesContrast1", "V 2 Lines Contrast 1", 0, 5, 2.5, "V2 Lines Detail"),
                        ParamSpec.slider("v2LinesContrast2", "V 2 Lines Contrast 2", 0, 6, 3, "V2 Lines Detail"),
                        ParamSpec.slider("v2LinesMaskRadius", "V 2 Lines Mask Radius", 0, 0.6, 0.3, "V2 Lines Detail"))
                .group("V2 Alpha Detail",
                        ParamSpec.slider("v2LinesMaskSoft", "V 2 Lines Mask Soft", 0, 0.04, 0.02, "V2 Alpha Detail"),
                        ParamSpec.slider("v2RayRotSpeed", "V 2 Ray Rot Speed", 0, 0.6, 0.3, "V2 Alpha Detail"),
                        ParamSpec.slider("v2RayStartRadius", "V 2 Ray Start Radius", 0, 0.7, 0.32, "V2 Alpha Detail"),
                        ParamSpec.slider("v2AlphaScale", "V 2 Alpha Scale", 0, 1, 0.5, "V2 Alpha Detail"))
                .group("Flames",
                        ParamSpec.slider("flamesEdge", "Flames Edge", 0, 3, 1.1, "Flames"),
                        ParamSpec.slider("flamesPower", "Flames Power", 0, 2, 2, "Flames"),
                        ParamSpec.slider("flamesMult", "Flames Mult", 0, 100, 50, "Flames"),
                        ParamSpec.slider("flamesTimeScale", "Flames Time Scale", 0, 3, 1.2, "Flames"),
                        ParamSpec.slider("flamesInsideFalloff", "Flames Inside Falloff", 0, 50, 24, "Flames"),
                        ParamSpec.slider("surfaceNoiseScale", "Surface Noise Scale", 0, 10, 5, "Flames"))
                .build();
    }

    public static EffectSchema geodesic(String effectType) {
        return EffectSchema.builder("Geodesic", required(effectType), 1)
                .group("Animation base",
                        ParamSpec.slider("intensity", "Intensity", 0, 5, 1, "Animation base"))
                .group("Geometry",
                        ParamSpec.slider("geoSubdivisions", "Geo Subdivisions", 0, 6, 3, "Geometry"),
                        ParamSpec.slider("geoRoundTop", "Geo Round Top", 0, 0.1, 0.05, "Geometry"),
                        ParamSpec.slider("geoRoundCorner", "Geo Round Corner", 0, 0.2, 0.1, "Geometry"),
                        ParamSpec.slider("geoThickness", "Geo Thickness", 0, 4, 2, "Geometry"),
                        ParamSpec.slider("geoGap", "Geo Gap", 0, 0.01, 0.005, "Geometry"),
                        ParamSpec.slider("geoHeight", "Geo Height", 0, 4, 2, "Geometry"),
                        ParamSpec.slider("geoWaveResolution", "Geo Wave Resolution", 0, 60, 30, "Geometry"),
                        ParamSpec.slider("geoWaveAmplitude", "Geo Wave Amplitude", 0, 0.2, 0.1, "Geometry"))
                .group("Lighting",
                        ParamSpec.slider("lightFresnel", "Light Fresnel", 0, 10, 5, "Lighting"))
                .group("Geodesic Animation",
                        ParamSpec.slider("geoAnimMode", "Geo Anim Mode", 0, 1, 1, "Geodesic Animation"),
                        ParamSpec.slider("geoRotationSpeed", "Geo Rotation Speed", 0, 0.4, 0.2, "Geodesic Animation"),
                        ParamSpec.slider("geoDomeClip", "Geo Dome Clip", 0, 1, 0.5, "Geodesic Animation"))
                .build();
    }
}
