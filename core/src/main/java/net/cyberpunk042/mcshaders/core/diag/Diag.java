package net.cyberpunk042.mcshaders.core.diag;

import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.util.ArrayList;
import java.util.List;
import net.cyberpunk042.mcshaders.core.api.Stable;

/**
 * The tessellators' diagnostic output, routed to whatever logging the host already has.
 *
 * <p>The tessellation code came from a mod that logs through its own channel system —
 * config file, per-channel levels, a watchdog, an option to echo a line into player chat.
 * None of that belongs in a library, but the lines themselves do: when a mesh comes out
 * wrong the first question is what the tessellator was given, and
 * {@code shape=cylinder radius=1.0 segments=3 wave=true} answers it immediately.
 *
 * <p>So the call sites were kept and the backend was replaced with {@link System.Logger},
 * which is in the JDK and delegates to whatever the host has installed — SLF4J under
 * Minecraft, the JUL console in a test, nothing at all in a consumer that never
 * configures logging. There is no dependency to add and nothing to initialise.
 *
 * <h2>Cost when nobody is listening</h2>
 *
 * <p>Tessellation runs per shape per frame, so a disabled log line has to cost nothing.
 * The level is checked in {@link Channel#topic} before any {@code kv} value is boxed or
 * any string is built; a disabled topic returns a shared sink whose methods return
 * immediately. The one thing this cannot avoid is evaluating the arguments at the call
 * site — {@code kv("vertices", builder.vertexCount())} still calls {@code vertexCount()}.
 * Those are all cheap accessors in the ported code.
 *
 * <h2>What was dropped</h2>
 *
 * <p>{@code alwaysChat()}, which pushed a line into the Minecraft chat overlay. A library
 * has no chat, and the one call site that used it — an unknown shape type reaching
 * {@code Tessellator} — is a warning either way.
 */
@Stable(since = "0.5.0")
public final class Diag {

    /** Geometry generation: what a tessellator was asked for and what it produced. */
    public static final Channel RENDER = new Channel("render");

    /** Field-level composition: which tessellator was chosen, how rays were laid out. */
    public static final Channel FIELD = new Channel("field");

    private Diag() {
    }

    /** One named stream of diagnostics, backed by one {@link System.Logger}. */
    public static final class Channel {

        private final Logger logger;

        private Channel(String name) {
            this.logger = System.getLogger("net.cyberpunk042.mcshaders.core." + name);
        }

        /**
         * Opens an entry under {@code topic}, or returns a sink if nothing is listening.
         *
         * <p>The level tested here is {@link Level#DEBUG}, the most verbose level any
         * caller in the ported code uses beyond {@code trace}. A {@code trace} call on a
         * debug-enabled logger is filtered later, by the logger itself.
         */
        public Entry topic(String topic) {
            return logger.isLoggable(Level.DEBUG) || logger.isLoggable(Level.WARNING)
                    ? new Recording(logger, topic)
                    : Sink.INSTANCE;
        }
    }

    /**
     * A diagnostic line under construction.
     *
     * <p>Key-value pairs accumulate and are appended to the message when a terminal
     * method is called. Implementations are free to discard everything.
     */
    public interface Entry {

        /** Adds a named value to the line. */
        Entry kv(String key, Object value);

        /** Adds a free-text explanation of why this line is being emitted. */
        Entry reason(String reason);

        /** Emits at {@link Level#DEBUG}. {@code {}} in {@code message} takes an argument. */
        void debug(String message, Object... args);

        /** Emits at {@link Level#TRACE}. {@code {}} in {@code message} takes an argument. */
        void trace(String message, Object... args);

        /** Emits at {@link Level#WARNING}. {@code {}} in {@code message} takes an argument. */
        void warn(String message, Object... args);
    }

    /** The entry used when the channel is enabled. */
    private static final class Recording implements Entry {

        private final Logger logger;
        private final String topic;
        private final List<String> pairs = new ArrayList<>();
        private String reason;

        Recording(Logger logger, String topic) {
            this.logger = logger;
            this.topic = topic;
        }

        @Override
        public Entry kv(String key, Object value) {
            pairs.add(key + "=" + value);
            return this;
        }

        @Override
        public Entry reason(String reason) {
            this.reason = reason;
            return this;
        }

        @Override
        public void debug(String message, Object... args) {
            emit(Level.DEBUG, message, args);
        }

        @Override
        public void trace(String message, Object... args) {
            emit(Level.TRACE, message, args);
        }

        @Override
        public void warn(String message, Object... args) {
            emit(Level.WARNING, message, args);
        }

        private void emit(Level level, String message, Object... args) {
            if (!logger.isLoggable(level)) {
                return;
            }
            StringBuilder line = new StringBuilder(topic).append(": ").append(format(message, args));
            if (reason != null) {
                line.append(" (").append(reason).append(')');
            }
            for (String pair : pairs) {
                line.append(' ').append(pair);
            }
            logger.log(level, line.toString());
        }
    }

    /** The entry used when nothing is listening. Every method is a no-op. */
    private static final class Sink implements Entry {

        static final Sink INSTANCE = new Sink();

        @Override
        public Entry kv(String key, Object value) {
            return this;
        }

        @Override
        public Entry reason(String reason) {
            return this;
        }

        @Override
        public void debug(String message, Object... args) {
        }

        @Override
        public void trace(String message, Object... args) {
        }

        @Override
        public void warn(String message, Object... args) {
        }
    }

    /**
     * Substitutes {@code args} into the {@code {}} placeholders of {@code message}.
     *
     * <p>This is the SLF4J placeholder style the ported call sites were written against.
     * {@link System.Logger} formats with {@link java.text.MessageFormat} instead, whose
     * {@code {0}} numbering would leave every one of those messages showing a literal
     * {@code {}} and dropping its arguments, so the substitution happens here and the
     * logger receives a finished string.
     *
     * <p>Surplus arguments are appended rather than discarded, and surplus placeholders
     * are left standing rather than consuming the wrong value — a mismatch between a
     * message and its arguments is a mistake in the call site, and it is more useful to
     * see it than to have the line quietly come out plausible.
     */
    static String format(String message, Object... args) {
        if (args == null || args.length == 0) {
            return message;
        }
        StringBuilder out = new StringBuilder(message.length() + 16 * args.length);
        int arg = 0;
        int at = 0;
        while (at < message.length()) {
            int placeholder = message.indexOf("{}", at);
            if (placeholder < 0 || arg >= args.length) {
                break;
            }
            out.append(message, at, placeholder).append(args[arg++]);
            at = placeholder + 2;
        }
        out.append(message, at, message.length());
        for (; arg < args.length; arg++) {
            out.append(" [+").append(args[arg]).append(']');
        }
        return out.toString();
    }
}
