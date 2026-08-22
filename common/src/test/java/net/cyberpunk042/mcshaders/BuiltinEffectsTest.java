package net.cyberpunk042.mcshaders;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.Gson;
import java.util.List;
import net.cyberpunk042.mcshaders.codec.BindingCodec;
import net.cyberpunk042.mcshaders.core.binding.Condition;
import net.cyberpunk042.mcshaders.core.binding.DimensionBinding;
import net.cyberpunk042.mcshaders.core.binding.DimensionId;
import net.cyberpunk042.mcshaders.core.effect.EffectDefinition;
import net.cyberpunk042.mcshaders.core.effect.EffectKind;
import net.cyberpunk042.mcshaders.core.effect.EffectLayer;
import net.cyberpunk042.mcshaders.core.effect.EffectStack;
import net.cyberpunk042.mcshaders.core.schema.EffectSchema;
import net.cyberpunk042.mcshaders.core.schema.SchemaAudit;
import net.cyberpunk042.mcshaders.core.schema.SchemaProblem;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Whether the effect the mod ships and the description of it agree.
 *
 * <p>They are written in two places a few lines apart, which is exactly the distance
 * at which they drift. A schema naming a parameter the effect does not have is a
 * control that edits nothing; an effect with a parameter no schema mentions is a
 * value nobody can reach from the editor. Neither shows up as a compile error, and
 * neither shows up at runtime as anything more helpful than a control that seems not
 * to work — so it is checked here instead.
 */
class BuiltinEffectsTest {

    @Test
    @DisplayName("the schema and the effect it describes agree on every parameter")
    void schemaMatchesTheEffect() {
        EffectSchema schema = BuiltinEffects.fogSchema();
        List<SchemaProblem> problems = SchemaAudit.audit(schema, BuiltinEffects.fogDefaults());

        assertTrue(problems.isEmpty(),
                () -> "schema and effect disagree: " + problems);
        assertTrue(SchemaAudit.agree(schema, BuiltinEffects.fogDefaults()));
    }

    @Test
    @DisplayName("the schema's own defaults are what the effect starts from")
    void schemaDefaultsMatchEffectDefaults() {
        // Two independent statements of the same values — the schema's per-spec
        // fallbacks and the definition's default params. If someone edits one, this
        // is what says the other needs editing too.
        assertEquals(BuiltinEffects.fogDefaults(), BuiltinEffects.fogSchema().defaults());
    }

    @Test
    @DisplayName("the definition is fog-kinded and owned by this mod")
    void definitionIsWellFormed() {
        EffectDefinition definition = BuiltinEffects.fogDefinition();

        assertEquals(BuiltinEffects.FOG, definition.type());
        assertEquals(EffectKind.FOG, definition.kind());
        assertEquals(McShaders.MOD_ID, definition.owner());
        assertTrue(definition.type().startsWith(McShaders.MOD_ID + ":"),
                "the type must be namespaced, or it races another mod's fog");
    }

    @Test
    @DisplayName("a binding using the built-in survives the codec")
    void builtinBindingRoundTrips() {
        // The join between the two pieces of work: an effect the mod ships, written
        // out in the format a pack author would write, and read back unchanged.
        DimensionBinding binding = new DimensionBinding(
                "overworld_haze",
                DimensionId.minecraft("overworld"),
                Condition.always(),
                EffectStack.of(EffectLayer.ofType(
                        "haze", BuiltinEffects.fogDefinition(), BuiltinEffects.fogDefaults())),
                0);

        String json = new Gson().toJson(BindingCodec.write(binding));
        assertEquals(binding, BindingCodec.read(json, "builtin.json"));
    }

    @Test
    @DisplayName("the fog distances cannot be set beyond the furthest anyone can see")
    void distancesAreBounded() {
        EffectSchema schema = BuiltinEffects.fogSchema();

        assertEquals(BuiltinEffects.MAX_DISTANCE,
                schema.parameter(BuiltinEffects.START).orElseThrow().bounds().max(), 1e-9);
        assertEquals(BuiltinEffects.MAX_DISTANCE,
                schema.parameter(BuiltinEffects.END).orElseThrow().bounds().max(), 1e-9);
    }
}
