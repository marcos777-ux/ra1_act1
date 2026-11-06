package com.makro.aadtex;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.makro.aadtex.bootstrap.MultiLanguageDataGenerator;
import com.makro.aadtex.config.StorePaths;
import com.makro.aadtex.service.CloudBucketService;
import com.makro.aadtex.service.CryptoService;
import com.makro.aadtex.service.SalesCsvService;
import com.makro.aadtex.service.SignatureService;
import com.makro.aadtex.service.StoreIngestionService;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class StoreIngestionIntegrationTest {
    @TempDir Path temp;
    private final ObjectMapper mapper = new ObjectMapper();

    private StorePaths paths() { return new StorePaths(temp.toString()); }

    private StoreIngestionService service(StorePaths paths) {
        return new StoreIngestionService(paths, new SalesCsvService(), new CryptoService(paths),
                new SignatureService(paths), new CloudBucketService(paths, mapper), true);
    }

    @Test
    void procesaLosCuatroLotesAislaVacioYEsIdempotente() throws Exception {
        StorePaths paths = paths();
        new MultiLanguageDataGenerator(paths, true).run();
        assertEquals(8, count(paths.input()));
        assertEquals(16, Files.readString(paths.key()).trim().length());
        assertEquals(0, Files.size(paths.input().resolve("daily_sales_broken_store.csv")));

        StoreIngestionService service = service(paths);
        service.processStoreBatches();

        Path reports = paths.bucket().resolve("2026/09");
        assertEquals(4, count(reports));
        assertEquals(4, count(paths.signatures()));
        assertEquals(7, countOnlyRegularFiles(paths.processed()));
        assertEquals(1, count(paths.error()));
        assertEquals(0, count(paths.input()));
        assertTrue(Files.exists(paths.error().resolve("daily_sales_broken_store.csv")));
        assertTrue(Files.exists(paths.signatures().resolve("signature_usa.jpg")));

        List<JsonNode> results = new ArrayList<>();
        try (var list = Files.list(reports)) {
            for (Path report : list.toList()) results.add(mapper.readTree(report.toFile()));
        }
        BigDecimal total = BigDecimal.ZERO;
        int transactions = 0;
        boolean hasChinese = false, hasEncrypted = false, hasZip = false;
        for (JsonNode json : results) {
            assertEquals("PROCESSED", json.get("status").asText());
            assertEquals("2026-09-13", json.get("transactionDate").asText());
            total = total.add(json.get("totalSales").decimalValue());
            transactions += json.get("transactionCount").asInt();
            if (json.get("sourceType").asText().equals("AES-128")) hasEncrypted = true;
            if (json.get("sourceType").asText().equals("ZIP")) hasZip = true;
            if (json.get("storeNames").toString().contains("上海")) hasChinese = true;
        }
        assertEquals(0, new BigDecimal("224852.35").compareTo(total));
        assertEquals(9, transactions);
        assertTrue(hasChinese, "Caracteres CJK preservados en el JSON");
        assertTrue(hasEncrypted, "AES desencriptado correctamente");
        assertTrue(hasZip, "ZIP procesado sin CSV intermedio");

        service.processStoreBatches();
        new MultiLanguageDataGenerator(paths, true).run();
        assertEquals(4, count(reports), "No se duplican resúmenes");
        assertEquals(0, count(paths.input()), "El generador no reintroduce los lotes");
    }

    @Test
    void rechazaZipSlipYAislaSoloEseZip() throws Exception {
        StorePaths paths = paths();
        paths.initialize();
        Path attack = paths.input().resolve("malicious.zip");
        try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(attack))) {
            out.putNextEntry(new ZipEntry("../escape.csv"));
            out.write("BAD".getBytes(StandardCharsets.UTF_8));
            out.closeEntry();
        }
        service(paths).processStoreBatches();
        assertEquals(1, count(paths.error()));
        assertFalse(Files.exists(temp.resolve("escape.csv")));
        assertEquals(0, count(paths.bucket()));
    }

    @Test
    void cifraInvalidaNoInterrumpeOtrosLotes() throws Exception {
        StorePaths paths = paths();
        new MultiLanguageDataGenerator(paths, true).run();
        // Fallo de longitud no válida (AES exige bloques de 16 bytes).
        Files.write(paths.input().resolve("daily_sales_financial.enc"), new byte[] {1, 2, 3});
        service(paths).processStoreBatches();
        assertTrue(count(paths.error()) >= 2); // CSV roto y ENC inválido.
        assertEquals(3, count(paths.bucket().resolve("2026/09")));
        assertTrue(Files.exists(paths.error().resolve("daily_sales_financial.enc")));
    }

    private static long count(Path folder) throws IOException {
        if (!Files.isDirectory(folder)) return 0;
        try (var files = Files.list(folder)) { return files.filter(Files::isRegularFile).count(); }
    }

    private static long countOnlyRegularFiles(Path folder) throws IOException {
        return count(folder);
    }
}
