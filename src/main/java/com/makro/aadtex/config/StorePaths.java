package com.makro.aadtex.config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Centraliza todas las rutas (NIO.2) y permite tests con @TempDir. */
@Component
public class StorePaths {
    private final Path root;

    public StorePaths(@Value("${aadtex.root-path:}") String configuredRoot) {
        String defaultRoot = System.getProperty("os.name", "").toLowerCase(Locale.ROOT)
                .contains("win") ? "C:/workspace/store_data" : "/workspace/store_data";
        root = Path.of(configuredRoot == null || configuredRoot.isBlank()
                ? defaultRoot : configuredRoot).toAbsolutePath().normalize();
    }

    public Path root() { return root; }
    public Path input() { return root.resolve("input"); }
    public Path output() { return root.resolve("output"); }
    public Path processed() { return output().resolve("processed"); }
    public Path signatures() { return processed().resolve("signatures"); }
    public Path error() { return output().resolve("error"); }
    public Path bucket() { return output().resolve("bucket").resolve("aadtex-daily-reports"); }
    public Path key() { return root.resolve("keys").resolve("AadTexSecret.key"); }
    public Path demoMarker() { return root.resolve(".demo_initialized"); }

    public void initialize() throws IOException {
        Files.createDirectories(input());
        Files.createDirectories(processed());
        Files.createDirectories(signatures());
        Files.createDirectories(error());
        Files.createDirectories(bucket());
        Files.createDirectories(key().getParent());
    }
}
