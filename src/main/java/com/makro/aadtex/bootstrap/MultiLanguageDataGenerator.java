package com.makro.aadtex.bootstrap;

import com.makro.aadtex.config.StorePaths;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/** Semilla reproducible e idempotente; no regenera lotes consumidos al reiniciar. */
@Component
public class MultiLanguageDataGenerator implements CommandLineRunner {
    private static final Logger log = LoggerFactory.getLogger(MultiLanguageDataGenerator.class);
    private static final List<String> DEMO_FILES = List.of(
            "daily_sales_spain.csv", "signature_spain.jpg",
            "daily_sales_china.csv", "signature_china.jpg",
            "daily_sales_financial.enc", "signature_financial.jpg",
            "batch_usa.zip", "daily_sales_broken_store.csv");
    private final StorePaths paths;
    private final boolean demoEnabled;

    public MultiLanguageDataGenerator(StorePaths paths,
            @Value("${aadtex.demo.enabled:true}") boolean demoEnabled) {
        this.paths = paths;
        this.demoEnabled = demoEnabled;
    }

    @Override
    public void run(String... args) throws IOException {
        paths.initialize();
        if (!demoEnabled) {
            log.info("Generador de demostración desactivado");
            return;
        }
        if (Files.exists(paths.demoMarker())) {
            log.info("Datos de demostración ya generados; no se duplican");
            return;
        }
        if (!Files.exists(paths.key())) {
            Files.writeString(paths.key(), "AadTexSecret2026\n", StandardCharsets.UTF_8);
        }
        for (String name : DEMO_FILES) {
            Path destination = paths.input().resolve(name);
            if (Files.exists(destination)) continue;
            try (InputStream resource = new ClassPathResource("demo-input/" + name).getInputStream()) {
                Files.copy(resource, destination);
            }
        }
        Files.writeString(paths.demoMarker(), "Datos de prueba inicializados\n", StandardCharsets.UTF_8);
        log.info("Ejemplos España, China, financiera AES, ZIP USA y lote defectuoso listos en {}",
                paths.input());
    }
}
