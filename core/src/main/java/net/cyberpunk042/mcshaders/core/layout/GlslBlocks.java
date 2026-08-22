package net.cyberpunk042.mcshaders.core.layout;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.cyberpunk042.mcshaders.core.api.Stable;

/**
 * Reads the uniform-block and sampler declarations back out of GLSL source.
 *
 * <p>This is deliberately not a GLSL parser. It reads declarations and nothing
 * else, because the only question being asked is "what layout does this shader
 * expect?" — a question the declarations answer completely.
 *
 * <p>Give it source that has already been through
 * {@link net.cyberpunk042.mcshaders.core.glsl.IncludeResolver}: blocks are
 * routinely declared in a shared include rather than in the shader that uses
 * them, which is exactly how the two copies drift apart in the first place.
 */
@Stable(since = "0.4.0")
public final class GlslBlocks {

    private static final Pattern BLOCK_HEAD = Pattern.compile(
            "layout\\s*\\(\\s*std140\\s*(?:,[^)]*)?\\)\\s*uniform\\s+(\\w+)\\s*\\{");

    private static final Pattern MEMBER = Pattern.compile(
            "\\b(\\w+)\\s+(\\w+)\\s*(?:\\[\\s*(\\d+)\\s*\\])?\\s*;");

    private static final Pattern SAMPLER = Pattern.compile(
            "\\buniform\\s+(?:lowp|mediump|highp\\s+)?(\\w*sampler\\w*)\\s+(\\w+)\\s*;");

    private GlslBlocks() {
    }

    /**
     * Every {@code layout(std140) uniform} block in the source, keyed by name and
     * in the order they appear.
     */
    public static Map<String, UniformBlock> blocks(String source) {
        String clean = stripComments(source);
        Map<String, UniformBlock> out = new LinkedHashMap<>();
        Matcher head = BLOCK_HEAD.matcher(clean);
        while (head.find()) {
            int open = clean.indexOf('{', head.end() - 1);
            int close = matchingBrace(clean, open);
            if (close < 0) {
                // Unbalanced source: report what was found rather than throwing, so a
                // half-written shader still yields useful diagnostics.
                break;
            }
            out.put(head.group(1), new UniformBlock(head.group(1), members(clean.substring(open + 1, close))));
            head.region(close, clean.length());
        }
        return Collections.unmodifiableMap(out);
    }

    /** Names of the samplers the source declares, in declaration order. */
    public static List<String> samplers(String source) {
        String clean = stripComments(source);
        List<String> out = new ArrayList<>();
        Matcher m = SAMPLER.matcher(clean);
        while (m.find()) {
            out.add(m.group(2));
        }
        return List.copyOf(out);
    }

    private static List<Std140.Member> members(String body) {
        List<Std140.Member> out = new ArrayList<>();
        Matcher m = MEMBER.matcher(body);
        while (m.find()) {
            GlslType.parse(m.group(1)).ifPresent(type -> {
                int length = m.group(3) == null ? 1 : Integer.parseInt(m.group(3));
                out.add(new Std140.Member(m.group(2), type, length));
            });
        }
        return out;
    }

    private static int matchingBrace(String s, int open) {
        int depth = 0;
        for (int i = open; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '{') {
                depth++;
            } else if (c == '}' && --depth == 0) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Blanks comments, preserving newlines so that any line number derived from the
     * result still points at the right line of the original.
     */
    static String stripComments(String source) {
        StringBuilder out = new StringBuilder(source.length());
        int i = 0;
        while (i < source.length()) {
            char c = source.charAt(i);
            if (c == '/' && i + 1 < source.length() && source.charAt(i + 1) == '/') {
                while (i < source.length() && source.charAt(i) != '\n') {
                    i++;
                }
            } else if (c == '/' && i + 1 < source.length() && source.charAt(i + 1) == '*') {
                i += 2;
                while (i < source.length() && !(source.charAt(i) == '*'
                        && i + 1 < source.length() && source.charAt(i + 1) == '/')) {
                    if (source.charAt(i) == '\n') {
                        out.append('\n');
                    }
                    i++;
                }
                i = Math.min(i + 2, source.length());
            } else {
                out.append(c);
                i++;
            }
        }
        return out.toString();
    }
}
