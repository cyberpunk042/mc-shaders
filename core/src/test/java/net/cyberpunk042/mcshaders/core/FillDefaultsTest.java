package net.cyberpunk042.mcshaders.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import net.cyberpunk042.mcshaders.core.fill.CageOptions;
import net.cyberpunk042.mcshaders.core.fill.CylinderCageOptions;
import net.cyberpunk042.mcshaders.core.fill.FillConfig;
import net.cyberpunk042.mcshaders.core.fill.FillMode;
import net.cyberpunk042.mcshaders.core.fill.PrismCageOptions;
import net.cyberpunk042.mcshaders.core.fill.SphereCageOptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * The two things that stop {@code field_fills} loading, pinned as facts rather than
 * opinions.
 *
 * <p>Twelve of the fifteen fill presets in {@code the-virus-block-mc} fail to read, for
 * two causes six files each — see {@code docs/VIRUS-BLOCK-FIELD-STATE.md}. Both were
 * filed as format decisions for the operator. They still are, but each has since been
 * narrowed by something checkable, and this is what does the checking: if either
 * changes, the decision written down for it has moved and the page describing it is
 * stale.
 *
 * <p><strong>Neither half asserts what the answer should be.</strong> One measures how
 * far one candidate answer gets and where it stops; the other records that core already
 * gives two answers to a question filed as unanswered.
 */
class FillDefaultsTest {

    private static Set<String> componentsOf(Class<?> record) {
        Set<String> names = new TreeSet<>();
        for (RecordComponent component : record.getRecordComponents()) {
            names.add(component.getName());
        }
        return names;
    }

    @Nested
    @DisplayName("the cage six")
    class CageDiscriminator {

        /**
         * The cage objects the six failing files actually carry, as keys.
         *
         * <p>Read out of {@code config/the-virus-block/field_fills/}: {@code cage_dense}
         * and {@code cage_globe} carry the four-key form, {@code default}, {@code points},
         * {@code thick_wire} and {@code thin_wire} the two-key form. No file carries any
         * other shape of cage, and none carries a discriminator.
         */
        private static final List<Set<String>> AUTHORED = List.of(
                new TreeSet<>(List.of(
                        "latitudeCount", "longitudeCount", "showEquator", "showPoles")),
                new TreeSet<>(List.of("latitudeCount", "longitudeCount")));

        /** The implementations whose components could absorb every key in {@code authored}. */
        private static List<Class<?>> candidatesFor(Set<String> authored) {
            return Arrays.stream(CageOptions.class.getPermittedSubclasses())
                    .filter(type -> componentsOf(type).containsAll(authored))
                    .collect(Collectors.toList());
        }

        @Test
        @DisplayName("inference from field names resolves every cage in the corpus, uniquely")
        void inferenceResolvesTheCorpus() {
            for (Set<String> authored : AUTHORED) {
                List<Class<?>> candidates = candidatesFor(authored);
                assertEquals(List.of(SphereCageOptions.class), candidates,
                        "the cage " + authored + " is expected to name exactly one "
                                + "implementation, because no cage but the sphere's has "
                                + "latitudeCount/longitudeCount. If this becomes ambiguous, "
                                + "'infer it from which fields are present' stops resolving "
                                + "the content it was proposed for");
            }
        }

        @Test
        @DisplayName("but it is not a general rule: two implementations are field-identical")
        void inferenceHasAReachableFailure() {
            Set<String> prism = componentsOf(PrismCageOptions.class);
            Set<String> cylinder = componentsOf(CylinderCageOptions.class);

            assertFalse(prism.isEmpty(), "no record components found — the scan is broken");
            assertEquals(prism, cylinder,
                    "PrismCageOptions and CylinderCageOptions carry the same components, so a "
                            + "cage object of that shape names both and inference cannot choose. "
                            + "Today no content carries one; the rule would be adopted with this "
                            + "failure already in it. If they ever diverge, that objection is "
                            + "gone and VIRUS-BLOCK-FIELD-STATE.md is stale");

            assertEquals(2, candidatesFor(prism).size(),
                    "and the ambiguity is exactly two-wide, not a general collapse");
        }

        @Test
        @DisplayName("the sibling-shape candidate has nothing to read: a preset carries no shape")
        void aFillPresetNamesNoShape() {
            // The other candidate in VIRUS-BLOCK-FIELD-STATE.md is "take it from the sibling
            // shape's type". FillConfig is the whole of a preset file, and it has no shape
            // component to take it from — the fill is applied to a primitive later. The
            // candidate can only work where a fill is nested inside one, which is not how
            // these six files are written.
            assertFalse(componentsOf(FillConfig.class).contains("shape"),
                    "FillConfig gained a shape component; the sibling-shape candidate may now "
                            + "be readable from a standalone preset");
        }
    }

    @Nested
    @DisplayName("the depth six")
    class DepthWriteDefault {

        @Test
        @DisplayName("core gives two answers for an unspecified depthWrite on a solid fill")
        void theBuilderAndTheConstantDisagree() {
            FillConfig built = FillConfig.builder().mode(FillMode.SOLID).build();
            FillConfig constant = FillConfig.SOLID;

            // Characterisation, not endorsement. "What does an omitted depthWrite mean"
            // was filed as a decision about the JSON format; it is not, or not only —
            // core answers it twice already, and differently. Resolving the format
            // question means picking one of these, and one assertion here will flip.
            assertTrue(built.depthWrite(),
                    "the builder defaults depthWrite to true regardless of mode");
            assertFalse(constant.depthWrite(),
                    "the SOLID constant sets it false");
            assertNotEquals(built, constant,
                    "two ways of asking core for a solid fill therefore differ; if they ever "
                            + "agree, the contradiction was resolved and the open question in "
                            + "VIRUS-BLOCK-FIELD-STATE.md has an answer");
        }

        @Test
        @DisplayName("depthTest has no such disagreement, which is why only one of the two is open")
        void depthTestIsConsistent() {
            assertTrue(FillConfig.builder().mode(FillMode.SOLID).build().depthTest());
            assertTrue(FillConfig.SOLID.depthTest());
            assertTrue(FillConfig.WIREFRAME.depthTest());
            assertTrue(FillConfig.SPHERE_CAGE.depthTest());
        }
    }
}
