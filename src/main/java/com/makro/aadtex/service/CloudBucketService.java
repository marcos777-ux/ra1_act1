package com.makro.aadtex.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.makro.aadtex.config.StorePaths;
import com.makro.aadtex.model.BatchReport;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import org.springframework.stereotype.Service;

/** Publicación JSON local para App 2, con escritura temporal y renombrado atómico. */
@Service
public class CloudBucketService {
    private final StorePaths paths;
    private final ObjectMapper json;

    public CloudBucketService(StorePaths paths, ObjectMapper json) {
        this.paths = paths;
        this.json = json;
    }

    public Path publish(BatchReport report) throws IOException {
        LocalDate date = LocalDate.parse(report.transactionDate());
        Path directory = paths.bucket().resolve("%04d".formatted(date.getYear()))
                .resolve("%02d".formatted(date.getMonthValue()));
        Files.createDirectories(directory);
        Path target = directory.resolve("summary_batch_" + report.batchId() + ".json");
        Path tmp = Files.createTempFile(directory, ".resumen-parcial-", ".tmp");
        try {
            json.writerWithDefaultPrettyPrinter().writeValue(tmp.toFile(), report);
            try {
                Files.move(tmp, target, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tmp, target);
            }
            return target;
        } finally {
            Files.deleteIfExists(tmp);
        }
    }
}
