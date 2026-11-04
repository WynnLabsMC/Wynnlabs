package io.github.anilken.wynnlabs.core;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * One of the mod's small JSON files, read when a feature loads and written straight away when it
 * changes. A file that cannot be read or written is reported by name and otherwise skipped, so a
 * broken file never stops the feature. The path is asked for on every use: it follows the
 * character in play.
 */
public final class JsonFile {

    private final Supplier<Path> path;

    public JsonFile(Supplier<Path> path) {
        this.path = path;
    }

    /**
     * Hands the file's text (UTF-8) to {@code parse}. Returns false when the file is missing or
     * reading or parsing it failed part way (what was taken in before the failure stays).
     */
    public boolean read(Consumer<String> parse) {
        Path p = path.get();
        try {
            if (!Files.exists(p)) {
                return false;
            }
            parse.accept(Files.readString(p, StandardCharsets.UTF_8));
            return true;
        } catch (Exception e) {
            System.out.println("[WynnLabs] could not read " + p.getFileName() + ": " + e);
            return false;
        }
    }

    /** Writes the text {@code text} builds (UTF-8) now, creating the folder when needed. */
    public void writeNow(Supplier<String> text) {
        Path p = path.get();
        try {
            String s = text.get();
            Files.createDirectories(p.getParent());
            Files.writeString(p, s, StandardCharsets.UTF_8);
        } catch (Exception e) {
            System.out.println("[WynnLabs] could not save " + p.getFileName() + ": " + e);
        }
    }
}
