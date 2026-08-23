package net.cyberpunk042.mcshaders.vanilla;

import java.io.IOException;
import java.io.Reader;
import java.util.LinkedHashMap;
import java.util.Map;
import net.cyberpunk042.mcshaders.McShaders;
import net.cyberpunk042.mcshaders.McShadersAPI;
import net.cyberpunk042.mcshaders.codec.BindingLoader;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Reads binding files out of a {@link ResourceManager} and puts them in force.
 *
 * <p>Everything a datapack reload has to do that is not loader-specific. Each loader
 * registers a reload listener its own way — Fabric through {@code ResourceLoader},
 * NeoForge through {@code AddServerReloadListenersEvent} — but what happens once the
 * game hands over a {@code ResourceManager} is identical, and it lives here so there
 * is one copy of it.
 *
 * <h2>Why not the vanilla JSON listener</h2>
 *
 * <p>{@code SimpleJsonResourceReloadListener} is generic over a DFU {@code Codec} on
 * 26.2 and hands its subclass decoded objects. Our format is read by a gson codec
 * that lives outside {@code core} on purpose, so adopting the vanilla listener would
 * mean a second codec for the same format and a drift bug between them. It would also
 * fail a reload wholesale on one bad file, where {@link BindingLoader} skips that file
 * and names it. See {@code docs/DATAPACKS-26.2.md}.
 *
 * <h2>Scope, stated rather than implied</h2>
 *
 * <p>Bindings are datapack content — {@code data/<ns>/mcshaders/bindings/} — so a
 * reload carrying them is server-side, and the registry this fills is a static in
 * that JVM. <strong>That is singleplayer and LAN, not a dedicated server.</strong> On
 * a dedicated server the reload runs where nothing renders, and the connecting client
 * never sees those files at all. Syncing them over the network is not written and is
 * not pretended to be.
 */
public final class BindingScan {

    private static final Logger LOGGER = LoggerFactory.getLogger(McShaders.MOD_NAME);

    /** Matches {@code data/<namespace>/mcshaders/bindings/<name>.json}. */
    public static final String DIRECTORY = McShaders.MOD_ID + "/bindings";

    private static final FileToIdConverter LISTER = FileToIdConverter.json(DIRECTORY);

    private BindingScan() {
    }

    /**
     * Loads every binding file the manager can see, and reports what happened.
     *
     * @param manager the reload's resource manager
     * @return what is now in force, and everything that went wrong getting there
     */
    public static BindingLoader.Result reload(ResourceManager manager) {
        if (manager == null) {
            throw new IllegalArgumentException("Cannot scan a null resource manager");
        }

        Map<String, String> files = new LinkedHashMap<>();

        // One file open at a time, closed before the next — vanilla's own pattern in
        // SimpleJsonResourceReloadListener#scanDirectory. BindingLoader.loadReaders
        // would take the readers directly, but its signature wants every one of them
        // open at once, and a pack set is not bounded.
        for (Map.Entry<Identifier, Resource> entry : LISTER.listMatchingResources(manager).entrySet()) {
            Identifier location = entry.getKey();
            try (Reader reader = entry.getValue().openAsReader()) {
                // Keyed by the path a pack author sees, so a later error message names
                // the file they have to go and look at.
                files.put(location.toString(), readFully(reader));
            } catch (IOException e) {
                // One unreadable file must not cost the rest.
                LOGGER.error("Couldn't read binding file '{}'", location, e);
            }
        }

        BindingLoader.Result result = McShadersAPI.loadBindings(files);
        report(files.size(), result);
        return result;
    }

    /**
     * Says what happened, because a silent reload is the failure mode here.
     *
     * <p>A binding skipped for a typo and a binding that was never written look
     * identical in game: the dimension keeps its default look. The only difference a
     * pack author can act on is in this log.
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
