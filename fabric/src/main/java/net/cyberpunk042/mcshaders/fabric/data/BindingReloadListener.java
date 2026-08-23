package net.cyberpunk042.mcshaders.fabric.data;

import java.io.IOException;
import java.io.Reader;
import java.util.LinkedHashMap;
import java.util.Map;
import net.cyberpunk042.mcshaders.McShaders;
import net.cyberpunk042.mcshaders.McShadersAPI;
import net.cyberpunk042.mcshaders.codec.BindingLoader;
import net.fabricmc.fabric.api.resource.v1.ResourceLoader;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Hands the game's binding files to {@link BindingLoader}.
 *
 * <p>The loader could already turn a stack of files into a registry, and
 * {@link McShadersAPI#loadBindings} could already put one in force. What neither of
 * them could do is <em>get</em> the files: that needs Minecraft's resource manager,
 * so until this existed the datapack format was one nothing ever read. A pack author
 * could write a perfectly valid binding and watch it do nothing.
 *
 * <h2>Why not the vanilla JSON listener</h2>
 *
 * <p>{@code SimpleJsonResourceReloadListener} is generic over a DFU {@code Codec} on
 * 26.2 and hands its subclass decoded objects. Our format is read by a gson codec
 * that lives outside {@code core} on purpose, so adopting the vanilla listener would
 * mean a second codec for the same format and a drift bug between them. It would
 * also fail a reload wholesale on one bad file, where {@code BindingLoader} skips
 * that file and names it. See {@code docs/DATAPACKS-26.2.md}.
 *
 * <h2>Scope, stated rather than implied</h2>
 *
 * <p>Bindings are datapack content — {@code data/<ns>/mcshaders/bindings/} — so
 * this listens on {@link PackType#SERVER_DATA}, and the registry it fills is a static
 * in this JVM. <strong>That is singleplayer and LAN, not a dedicated server.</strong>
 * On a dedicated server the reload runs server-side, where nothing renders, and the
 * connecting client never sees those files at all. Making a server's bindings reach
 * its clients means syncing them over the network, which is not written and is not
 * pretended to be.
 *
 * <p>Registration is on the main entrypoint rather than the client one for the same
 * reason: {@code SERVER_DATA} is a server-side reload, and it is the integrated
 * server that runs it in singleplayer.
 */
public final class BindingReloadListener implements ResourceManagerReloadListener {

    private static final Logger LOGGER = LoggerFactory.getLogger(McShaders.MOD_NAME);

    /** Matches {@code data/<namespace>/mcshaders/bindings/<name>.json}. */
    private static final String DIRECTORY = McShaders.MOD_ID + "/bindings";

    private static final FileToIdConverter LISTER = FileToIdConverter.json(DIRECTORY);

    private static final Identifier ID =
            Identifier.fromNamespaceAndPath(McShaders.MOD_ID, "bindings");

    private BindingReloadListener() {
    }

    /** Wires this into the datapack reload. Call once, from the main entrypoint. */
    public static void register() {
        ResourceLoader.get(PackType.SERVER_DATA)
                .registerReloadListener(ID, new BindingReloadListener());
    }

    @Override
    public void onResourceManagerReload(ResourceManager manager) {
        Map<String, String> files = new LinkedHashMap<>();

        // One file open at a time, closed before the next — vanilla's own pattern in
        // SimpleJsonResourceReloadListener#scanDirectory. BindingLoader.loadReaders
        // would take the readers directly, but its signature wants every one of them
        // open at once, and a pack set is not bounded.
        for (Map.Entry<Identifier, Resource> entry : LISTER.listMatchingResources(manager).entrySet()) {
            Identifier location = entry.getKey();
            try (Reader reader = entry.getValue().openAsReader()) {
                files.put(location.toString(), readFully(reader));
            } catch (IOException e) {
                // Keyed by the path a pack author sees, so this names the file they
                // have to go and look at. One unreadable file must not cost the rest.
                LOGGER.error("Couldn't read binding file '{}'", location, e);
            }
        }

        BindingLoader.Result result = McShadersAPI.loadBindings(files);
        report(files.size(), result);
    }

    /**
     * Says what happened, because a silent reload is the failure mode here.
     *
     * <p>A binding that was skipped for a typo and a binding that was never written
     * look identical in game: the dimension keeps its default look. The only
     * difference a pack author can act on is in this log.
     */
    private static void report(int fileCount, BindingLoader.Result result) {
        for (BindingLoader.Problem problem : result.problems()) {
            switch (problem.kind()) {
                case SKIPPED -> LOGGER.error("{}", problem);
                case OVERRIDDEN -> LOGGER.info("{}", problem);
            }
        }
        LOGGER.info("Loaded {} binding file(s); {} binding(s) in force",
                fileCount, result.registry().size());
    }

    private static String readFully(Reader reader) throws IOException {
        StringBuilder out = new StringBuilder();
        char[] buffer = new char[8192];
        int read;
        while ((read = reader.read(buffer)) != -1) {
            out.append(buffer, 0, read);
        }
        return out.toString();
    }
}
