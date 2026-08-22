package net.cyberpunk042.mcshaders.check;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import net.cyberpunk042.mcshaders.core.glsl.SourceProvider;

/**
 * A {@link SourceProvider} over an unpacked {@code assets/} directory.
 *
 * <p>Resolves {@code namespace:path} the way the game's resource manager does:
 * {@code assets/<namespace>/shaders/<path>}. Where a chain names a shader without
 * an extension — which is the norm, since a pass names one id for what is really a
 * pair of files — the known shader extensions are tried in turn.
 *
 * <p>Ids in a namespace this tree does not contain resolve to empty. That is the
 * right answer for a checker pointed at one mod: {@code minecraft:post/blit} is
 * real, it simply lives in the game jar. {@link #isExternal} exists so a caller
 * can tell that apart from a genuinely missing file rather than reporting the
 * game's own shaders as broken.
 */
public final class ResourceTree implements SourceProvider {

    private static final List<String> SHADER_EXTENSIONS = List.of("", ".fsh", ".vsh", ".glsl");

    private final Path assets;

    public ResourceTree(Path assets) {
        this.assets = assets;
    }

    @Override
    public Optional<String> read(String id) {
        String namespace = "minecraft";
        String path = id;
        int colon = id.indexOf(':');
        if (colon >= 0) {
            namespace = id.substring(0, colon);
            path = id.substring(colon + 1);
        }
        for (String extension : SHADER_EXTENSIONS) {
            Path candidate = assets.resolve(namespace).resolve("shaders").resolve(path + extension);
            if (Files.isRegularFile(candidate)) {
                try {
                    return Optional.of(Files.readString(candidate));
                } catch (IOException e) {
                    throw new UncheckedIOException("cannot read " + candidate, e);
                }
            }
        }
        return Optional.empty();
    }

    /** The namespaces this tree actually contains. */
    public List<String> namespaces() {
        try (Stream<Path> dirs = Files.list(assets)) {
            return dirs.filter(Files::isDirectory).map(p -> p.getFileName().toString()).sorted().toList();
        } catch (IOException e) {
            throw new UncheckedIOException("cannot list " + assets, e);
        }
    }

    /**
     * Whether {@code id} belongs to a namespace this tree does not contain — in
     * which case its absence says nothing about whether it exists.
     */
    public boolean isExternal(String id) {
        int colon = id.indexOf(':');
        String namespace = colon >= 0 ? id.substring(0, colon) : "minecraft";
        return !namespaces().contains(namespace);
    }

    /** Every {@code post_effect/*.json} in the tree, sorted. */
    public List<Path> chains() {
        List<Path> out = new ArrayList<>();
        for (String namespace : namespaces()) {
            Path dir = assets.resolve(namespace).resolve("post_effect");
            if (!Files.isDirectory(dir)) {
                continue;
            }
            try (Stream<Path> files = Files.list(dir)) {
                files.filter(p -> p.getFileName().toString().endsWith(".json")).forEach(out::add);
            } catch (IOException e) {
                throw new UncheckedIOException("cannot list " + dir, e);
            }
        }
        out.sort(Path::compareTo);
        return out;
    }
}
