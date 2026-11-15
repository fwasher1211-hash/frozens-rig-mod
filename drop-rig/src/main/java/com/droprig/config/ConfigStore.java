package com.droprig.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.function.Consumer;

/**
 * Loads and saves {@link DropRigConfig} as JSON. A broken file is kept as {@code .broken} and
 * defaults are used, so a bad edit never crashes the game. Saves go through a temp file so a
 * crash mid-write can't corrupt the config.
 */
public final class ConfigStore {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final Path path;
    private final Consumer<String> warn;

    public ConfigStore(Path path, Consumer<String> warn) {
        this.path = path;
        this.warn = warn == null ? s -> { } : warn;
    }

    public Path path() {
        return path;
    }

    public DropRigConfig load() {
        if (!Files.exists(path)) {
            DropRigConfig fresh = new DropRigConfig();
            save(fresh);
            return fresh;
        }
        try (Reader r = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            DropRigConfig cfg = GSON.fromJson(r, DropRigConfig.class);
            if (cfg == null) cfg = new DropRigConfig();
            return cfg.sanitize();
        } catch (IOException | JsonParseException e) {
            warn.accept("Could not read " + path + " (" + e.getMessage() + "); using defaults");
            try {
                Files.move(path, path.resolveSibling(path.getFileName() + ".broken"), StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException ignored) {
                // best effort
            }
            DropRigConfig fresh = new DropRigConfig();
            save(fresh);
            return fresh;
        }
    }

    public boolean save(DropRigConfig cfg) {
        try {
            Path dir = path.toAbsolutePath().getParent();
            if (dir != null) Files.createDirectories(dir);
            Path tmp = path.resolveSibling(path.getFileName() + ".tmp");
            try (Writer w = Files.newBufferedWriter(tmp, StandardCharsets.UTF_8)) {
                GSON.toJson(cfg, w);
            }
            try {
                Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING);
            }
            return true;
        } catch (IOException e) {
            warn.accept("Could not save " + path + ": " + e.getMessage());
            return false;
        }
    }
}
