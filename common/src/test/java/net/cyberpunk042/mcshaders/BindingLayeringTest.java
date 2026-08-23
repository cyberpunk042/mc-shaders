package net.cyberpunk042.mcshaders;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import net.cyberpunk042.mcshaders.codec.BindingLoader;
import net.cyberpunk042.mcshaders.core.binding.BindingRegistry;
import net.cyberpunk042.mcshaders.core.binding.DimensionBinding;
import net.cyberpunk042.mcshaders.core.binding.DimensionId;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;

/**
 * What a datapack reload does to bindings a mod registered in Java.
 *
 * <p>It used to delete them. {@code loadBindings} applied the pack set wholesale, so
 * the first {@code /reload} replaced every registered binding with whatever the packs
 * held — and because {@link McShadersAPI#registerBinding} throws once registration
 * closes, nothing could put them back. Not until the next restart, and then only
 * until the next reload. This mod's own {@code beyond_depths} was among the
 * casualties.
 *
 * <p>The failure is invisible from inside {@code /reload}: it reports success, the
 * pack's own bindings work, and only the ones nobody edited quietly stop existing.
 *
 * <p><strong>These assertions are about relationships, not contents.</strong> The
 * registered baseline is whatever else in this JVM has registered, so the tests
 * capture it and compare against it rather than naming ids. What they do need is for
 * it to be <em>non-empty</em> — an empty baseline makes "the baseline survives"
 * vacuously true, and the old code would have passed. Hence {@code @Order(1)} and
 * the {@code assertFalse} in {@link #baselineIsNotEmpty()}, which fails loudly if
 * this class ever stops running before registration closes.
 */
@Order(1)
class BindingLayeringTest {

    private static final DimensionId NOWHERE = DimensionId.of("mcshaders", "test_layering");

    /** Ids registered in Java before any pack is loaded. */
    private static Set<String> baseline;

    @BeforeAll
    static void captureBaseline() {
        // Registers the mod's own effects, bindings and backends. Idempotent, and it
        // must happen before anything closes registration — see junit-platform.properties.
        McShaders.init(message -> { });

        // Read the baseline through completeRegistration, NOT through loadBindings.
        // loadBindings is the method under test: capturing the baseline with it means
        // a broken implementation reports an empty baseline, and then every
        // "the baseline survived" assertion below is containsAll(emptySet()) — true
        // no matter what. That mistake was made here first and caught by mutating the
        // fix away: four of these tests passed against the old wholesale behaviour.
        McShaders.completeRegistration();
        baseline = idsOf(McShadersAPI.bindings());
    }

    private static Set<String> idsOf(BindingRegistry registry) {
        return registry.all().stream().map(DimensionBinding::id).collect(Collectors.toSet());
    }

    /** A pack file declaring one binding, in the format the loader reads. */
    private static Map<String, String> pack(String id) {
        Map<String, String> files = new LinkedHashMap<>();
        files.put(id + ".json", """
                {
                  "id": "%s",
                  "dimension": "mcshaders:test_layering",
                  "stack": {
                    "layers": [
                      { "id": "haze", "kind": "fog", "type": "mcshaders:fog", "params": {} }
                    ]
                  }
                }
                """.formatted(id));
        return files;
    }

    @Test
    @DisplayName("the baseline is not empty, or nothing below proves anything")
    void baselineIsNotEmpty() {
        assertFalse(baseline.isEmpty(),
                "registration closed before this class ran, so there are no Java-registered "
                        + "bindings to layer under and every other test here is vacuous");
    }

    @Test
    @DisplayName("a pack adds to the registered bindings instead of replacing them")
    void packIsLayeredOverRegistered() {
        BindingLoader.Result result = McShadersAPI.loadBindings(pack("test_layer_added"));

        Set<String> inForce = idsOf(result.registry());
        assertTrue(inForce.contains("test_layer_added"), "the pack's own binding should apply");
        assertTrue(inForce.containsAll(baseline),
                "a reload deleted the Java-registered bindings; nothing can re-register them");
    }

    @Test
    @DisplayName("loading no files returns to the registered baseline, not to empty")
    void noFilesMeansBaseline() {
        McShadersAPI.loadBindings(pack("test_layer_transient"));

        Set<String> afterRemoval = idsOf(McShadersAPI.loadBindings(Map.of()).registry());

        assertEquals(baseline, afterRemoval,
                "removing the last pack should leave what the mods registered, not nothing");
    }

    @Test
    @DisplayName("the baseline survives having been wiped, because it is re-layered each time")
    void baselineIsNotConsumed() {
        // reloadBindings is the wholesale path and is allowed to empty things; the
        // point is that it does not destroy the source loadBindings layers from.
        McShadersAPI.reloadBindings(BindingRegistry.empty());
        assertTrue(McShadersAPI.bindings().isEmpty(), "precondition: the registry is empty");

        Set<String> restored = idsOf(McShadersAPI.loadBindings(Map.of()).registry());

        assertEquals(baseline, restored, "the registered set is a source, not a one-shot");
    }

    @Test
    @DisplayName("a pack binding reusing a registered id overrides it, and only it")
    void packOverridesById() {
        String taken = baseline.iterator().next();

        BindingLoader.Result result = McShadersAPI.loadBindings(pack(taken));

        DimensionBinding winner = result.registry().byId(taken).orElseThrow();
        assertEquals(NOWHERE, winner.dimension(),
                "the pack's version should win on a shared id — that is what reusing one means");
        assertTrue(idsOf(result.registry()).containsAll(baseline),
                "overriding one binding must not remove the others");
    }

    @Test
    @DisplayName("the returned registry is the one in force, not the pack-only set")
    void resultReportsWhatIsInForce() {
        BindingLoader.Result result = McShadersAPI.loadBindings(pack("test_layer_reported"));

        assertEquals(idsOf(McShadersAPI.bindings()), idsOf(result.registry()),
                "a caller inspecting the result must see what the renderer sees");
    }

    @Test
    @DisplayName("problems still come from the pack, and are not swallowed by layering")
    void problemsSurvive() {
        Map<String, String> broken = new LinkedHashMap<>();
        broken.put("broken.json", "{ this is not json");

        BindingLoader.Result result = McShadersAPI.loadBindings(broken);

        assertTrue(result.hasFailures(), "a malformed file must still be reported");
        assertTrue(idsOf(result.registry()).containsAll(baseline),
                "and must not take the registered bindings down with it");
    }
}
