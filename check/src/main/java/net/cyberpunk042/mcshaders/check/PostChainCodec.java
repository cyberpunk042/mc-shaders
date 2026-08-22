package net.cyberpunk042.mcshaders.check;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.cyberpunk042.mcshaders.core.chain.Input;
import net.cyberpunk042.mcshaders.core.chain.Pass;
import net.cyberpunk042.mcshaders.core.chain.PostChain;
import net.cyberpunk042.mcshaders.core.chain.TargetSpec;
import net.cyberpunk042.mcshaders.core.layout.GlslType;
import net.cyberpunk042.mcshaders.core.layout.Std140;
import net.cyberpunk042.mcshaders.core.layout.UniformBlock;

/**
 * Reads Minecraft's post-effect JSON into a {@link PostChain}.
 *
 * <p>This lives outside {@code core} on purpose. The engine models and checks
 * chains; it does not parse, so that content can arrive from anywhere and the
 * published library carries no JSON dependency.
 *
 * <h2>The format</h2>
 *
 * <pre>{@code
 * {
 *   "targets": { "swap": {} },
 *   "passes": [{
 *     "vertex_shader":   "minecraft:post/blit",
 *     "fragment_shader": "example:post/tint",
 *     "inputs":  [{ "sampler_name": "In", "target": "minecraft:main" }],
 *     "output":  "swap",
 *     "uniforms": { "Config": [{ "name": "Radius", "type": "float", "value": 1.0 }] }
 *   }]
 * }
 * }</pre>
 *
 * <p>Two things about it are worth stating, because both are load-bearing and
 * neither is obvious from a sample:
 *
 * <ul>
 *   <li><b>A uniform entry's order is its layout.</b> The list is not a bag of
 *       named values — it is the std140 declaration of the block, and the position
 *       of each entry decides which bytes the shader reads it from. Reordering the
 *       list changes the meaning of every entry after the move.</li>
 *   <li><b>There is no matrix type.</b> A {@code mat4} has to be spelled as four
 *       {@code vec4} entries. That is why
 *       {@link net.cyberpunk042.mcshaders.core.layout.Std140#expand} exists.</li>
 * </ul>
 *
 * <p>{@code value} is deliberately ignored. Defaults do not affect layout, and
 * layout is the question this module was built to answer.
 */
public final class PostChainCodec {

    private PostChainCodec() {
    }

    /**
     * @throws JsonSyntaxException if the document is not a well-formed chain
     */
    public static PostChain read(Reader json) throws IOException {
        JsonElement root = JsonParser.parseReader(json);
        if (!root.isJsonObject()) {
            throw new JsonSyntaxException("a post-effect chain must be a JSON object");
        }
        return read(root.getAsJsonObject());
    }

    public static PostChain read(JsonObject root) {
        Map<String, TargetSpec> targets = new LinkedHashMap<>();
        if (root.has("targets")) {
            for (Map.Entry<String, JsonElement> e : root.getAsJsonObject("targets").entrySet()) {
                targets.put(e.getKey(), target(e.getValue()));
            }
        }

        List<Pass> passes = new ArrayList<>();
        JsonArray declared = root.getAsJsonArray("passes");
        if (declared != null) {
            for (JsonElement e : declared) {
                passes.add(pass(e.getAsJsonObject()));
            }
        }
        return new PostChain(targets, passes);
    }

    private static TargetSpec target(JsonElement element) {
        JsonObject spec = element.getAsJsonObject();
        // An empty spec means "follow the screen", which is the overwhelmingly
        // common case; a fixed size has to name both dimensions.
        if (!spec.has("width") && !spec.has("height")) {
            return TargetSpec.SCREEN_SIZED;
        }
        return new TargetSpec(spec.get("width").getAsInt(), spec.get("height").getAsInt());
    }

    private static Pass pass(JsonObject json) {
        List<Input> inputs = new ArrayList<>();
        JsonArray declared = json.getAsJsonArray("inputs");
        if (declared != null) {
            for (JsonElement e : declared) {
                JsonObject in = e.getAsJsonObject();
                inputs.add(new Input(
                        in.get("sampler_name").getAsString(),
                        in.get("target").getAsString(),
                        in.has("use_depth_buffer") && in.get("use_depth_buffer").getAsBoolean()));
            }
        }

        List<UniformBlock> uniforms = new ArrayList<>();
        if (json.has("uniforms")) {
            for (Map.Entry<String, JsonElement> e : json.getAsJsonObject("uniforms").entrySet()) {
                uniforms.add(block(e.getKey(), e.getValue().getAsJsonArray()));
            }
        }

        return new Pass(
                json.get("vertex_shader").getAsString(),
                json.get("fragment_shader").getAsString(),
                inputs,
                json.get("output").getAsString(),
                uniforms);
    }

    private static UniformBlock block(String name, JsonArray entries) {
        List<Std140.Member> members = new ArrayList<>();
        for (JsonElement e : entries) {
            JsonObject entry = e.getAsJsonObject();
            String declaredType = entry.get("type").getAsString();
            GlslType type = GlslType.parse(declaredType).orElseThrow(() ->
                    new JsonSyntaxException("uniform '" + entry.get("name").getAsString()
                            + "' in block '" + name + "' has unknown type '" + declaredType + "'"));
            int count = entry.has("count") ? entry.get("count").getAsInt() : 1;
            members.add(new Std140.Member(entry.get("name").getAsString(), type, count));
        }
        return new UniformBlock(name, members);
    }
}
