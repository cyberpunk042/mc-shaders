/**
 * Energy interaction system for visual effects.
 * 
 * <h2>Hierarchy</h2>
 * <pre>
 * Energy Interaction (UI control: "Energy Interaction")
 * └── Radiative Interaction
 *     ├── EMISSION      (energy radiates outward)
 *     ├── ABSORPTION    (energy flows inward)
 *     ├── REFLECTION    (energy bounces)
 *     ├── TRANSMISSION  (energy passes through)
 *     └── SCATTERING    (energy disperses)
 * └── Energy Travel
 *     ├── CHASE         (particles moving)
 *     ├── SCROLL        (gradient scrolling)
 *     ├── COMET         (head with tail)
 *     └── ...
 * └── Energy Flicker
 *     ├── SCINTILLATION (star twinkling)
 *     ├── STROBE        (on/off blinking)
 *     └── ...
 * </pre>
 * 
 * <h2>Migration from Old Names</h2>
 * <table>
 *   <tr><th>Old</th><th>New</th></tr>
 *   <tr><td>LengthMode.RADIATE</td><td>RadiativeInteraction.EMISSION</td></tr>
 *   <tr><td>LengthMode.ABSORB</td><td>RadiativeInteraction.ABSORPTION</td></tr>
 *   <tr><td>LengthMode.SEGMENT</td><td>RadiativeInteraction.TRANSMISSION</td></tr>
 *   <tr><td>LengthMode.PULSE</td><td>RadiativeInteraction.OSCILLATION</td></tr>
 *   <tr><td>LengthMode.GROW_SHRINK</td><td>RadiativeInteraction.RESONANCE</td></tr>
 *   <tr><td>TravelMode.*</td><td>EnergyTravel.*</td></tr>
 *   <tr><td>FlickerMode.*</td><td>EnergyFlicker.*</td></tr>
 * </table>
 * 
 * @see net.cyberpunk042.mcshaders.core.energy.EnergyInteractionType
 * @see net.cyberpunk042.mcshaders.core.energy.RadiativeInteraction
 * @see net.cyberpunk042.mcshaders.core.energy.EnergyTravel
 * @see net.cyberpunk042.mcshaders.core.energy.EnergyFlicker
 */
package net.cyberpunk042.mcshaders.core.energy;
