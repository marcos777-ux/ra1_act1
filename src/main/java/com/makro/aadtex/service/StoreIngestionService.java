package com.makro.aadtex.service;

import com.makro.aadtex.config.StorePaths;
import com.makro.aadtex.model.BatchReport;
import com.makro.aadtex.model.SalesTotals;
import java.io.BufferedInputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/** Orquestación: descubrir, validar, procesar y publicar lotes sin bloquear los demás. */
@Service
public class StoreIngestionService {
    private static final Logger log = LoggerFactory.getLogger(StoreIngestionService.class);
    private static final long MAX_CSV_SIZE = 5L * 1024 * 1024;
    private static final long MAX_ZIP_SIZE = 25L * 1024 * 1024;
    private static final int MAX_ZIP_ENTRIES = 12;
    private final ReentrantLock processingLock = new ReentrantLock();
    private final StorePaths paths;
    private final SalesCsvService csv;
    private final CryptoService crypto;
    private final SignatureService signatures;
    private final CloudBucketService bucket;
    private final boolean requireSignature;

    public StoreIngestionService(StorePaths paths, SalesCsvService csv, CryptoService crypto,
            SignatureService signatures, CloudBucketService bucket,
            @Value("${aadtex.require-signature:true}") boolean requireSignature) {
        this.paths = paths;
        this.csv = csv;
        this.crypto = crypto;
        this.signatures = signatures;
        this.bucket = bucket;
        this.requireSignature = requireSignature;
    }

    /** Invocado cada 5 s por el Scheduler, y directamente desde tests. */
    public void processStoreBatches() throws IOException {
        if (!processingLock.tryLock()) {
            log.debug("Todavía se procesa el ciclo anterior; se omite este tick");
            return;
        }
        try {
            paths.initialize();
            List<Path> files;
            try (Stream<Path> stream = Files.list(paths.input())) {
                files = stream.filter(Files::isRegularFile)
                        .sorted(Comparator.comparing(p -> p.getFileName().toString()))
                        .toList();
            }
            // Primera pasada: aislar entradas vacías antes de agrupar y procesar.
            for (Path file : files) {
                if (!Files.exists(file) || !supported(file)) continue;
                try {
                    if (Files.size(file) == 0) {
                        quarantine(file, "Fichero vacío (0 bytes)");
                    }
                } catch (IOException ex) {
                    quarantine(file, "No se puede consultar su tamaño: " + ex.getMessage());
                }
            }
            // Los ZIP son unidades de procesamiento; los demás se agrupan con su firma.
            for (Path file : files) {
                if (Files.isRegularFile(file) && extension(file).equals(".zip")) {
                    try { processZip(file); }
                    catch (Exception ex) { quarantine(file, "ZIP inválido: " + ex.getMessage()); }
                }
            }
            for (Path file : files) {
                if (!Files.isRegularFile(file)) continue;
                String ext = extension(file);
                if (ext.equals(".csv") || ext.equals(".enc")) {
                    Path signature = pairedSignature(file);
                    if (requireSignature && !Files.isRegularFile(signature)) {
                        log.info("Esperando firma para el lote {}", file.getFileName());
                        continue;
                    }
                    try { processSingle(file, Files.isRegularFile(signature) ? signature : null); }
                    catch (Exception ex) {
                        quarantine(file, "Lote inválido: " + ex.getMessage());
                        if (Files.isRegularFile(signature)) {
                            quarantine(signature, "Firma del lote fallido " + file.getFileName());
                        }
                    }
                }
            }
        } finally {
            processingLock.unlock();
        }
    }

    private void processSingle(Path file, Path signature) throws IOException {
        if (Files.size(file) > MAX_CSV_SIZE && extension(file).equals(".csv")) {
            throw new IOException("CSV demasiado grande");
        }
        SalesTotals sales;
        if (extension(file).equals(".enc")) {
            try (InputStream decrypted = crypto.decryptToMemory(file)) {
                sales = csv.parse(decrypted);
            }
        } else {
            try (InputStream raw = Files.newInputStream(file)) {
                sales = csv.parse(new BoundedInputStream(raw, MAX_CSV_SIZE));
            }
        }
        Path stagedSignature = null;
        try {
            if (signature != null) {
                try (InputStream binary = Files.newInputStream(signature)) {
                    stagedSignature = signatures.stage(binary);
                }
            }
            List<Path> originals = new ArrayList<>();
            originals.add(file);
            if (signature != null) originals.add(signature);
            publishBatch(originals, sales, stagedSignature,
                    signature == null ? null : signature.getFileName().toString(),
                    file.getFileName().toString(),
                    extension(file).equals(".enc") ? "AES-128" : "CSV");
        } finally {
            if (stagedSignature != null) Files.deleteIfExists(stagedSignature);
        }
    }

    /** Consume cada ZipEntry en caliente; no extrae CSVs intermedios al disco. */
    private void processZip(Path file) throws IOException {
        if (Files.size(file) > MAX_ZIP_SIZE) throw new IOException("ZIP demasiado grande");
        SalesTotals sales = null;
        Path stagedSignature = null;
        String csvName = null, signatureName = null;
        int entries = 0;
        try (ZipInputStream zip = new ZipInputStream(
                new BufferedInputStream(Files.newInputStream(file), 4096), StandardCharsets.UTF_8)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (++entries > MAX_ZIP_ENTRIES) throw new IOException("ZIP con demasiadas entradas");
                if (entry.isDirectory()) { zip.closeEntry(); continue; }
                String name = entry.getName();
                // Solo nombres planos para evitar Zip Slip y rutas de escape.
                if (name.contains("/") || name.contains("\\") || name.contains("..")
                        || name.startsWith(".")) {
                    throw new IOException("Nombre de entrada ZIP inseguro: " + name);
                }
                if (name.startsWith("daily_sales_") && name.endsWith(".csv")) {
                    if (sales != null) throw new IOException("ZIP con varios CSV de ventas");
                    csvName = name;
                    sales = csv.parse(new BoundedInputStream(zip, MAX_CSV_SIZE));
                } else if (name.startsWith("signature_") && name.endsWith(".jpg")) {
                    if (stagedSignature != null) throw new IOException("ZIP con varias firmas");
                    signatureName = name;
                    stagedSignature = signatures.stage(new BoundedInputStream(zip, 20L * 1024 * 1024));
                } else {
                    throw new IOException("Entrada ZIP no autorizada: " + name);
                }
                zip.closeEntry();
            }
            if (sales == null || stagedSignature == null) {
                throw new IOException("ZIP incompleto: se requiere CSV + JPG");
            }
            String csvSuffix = csvName.substring("daily_sales_".length(), csvName.length() - 4);
            String jpgSuffix = signatureName.substring("signature_".length(), signatureName.length() - 4);
            if (!csvSuffix.equals(jpgSuffix)) {
                throw new IOException("La firma y el CSV del ZIP no pertenecen a la misma tienda");
            }
            publishBatch(List.of(file), sales, stagedSignature, signatureName,
                    file.getFileName().toString(), "ZIP");
        } finally {
            if (stagedSignature != null) Files.deleteIfExists(stagedSignature);
        }
    }

    /** Una operación lógica: archiva fuentes, publica firma y resumen; revierte ante error. */
    private void publishBatch(List<Path> originals, SalesTotals sales, Path stagedSignature,
                              String signatureName, String sourceName, String sourceType) throws IOException {
        List<Move> archived = new ArrayList<>();
        Path publishedSignature = null;
        try {
            for (Path original : originals) {
                Path destination = uniqueDestination(paths.processed(), original.getFileName().toString());
                move(original, destination);
                archived.add(new Move(original, destination));
            }
            List<String> outputSignatures = List.of();
            if (stagedSignature != null) {
                publishedSignature = uniqueDestination(paths.signatures(), signatureName);
                move(stagedSignature, publishedSignature);
                outputSignatures = List.of("signatures/" + publishedSignature.getFileName());
            }
            BatchReport report = new BatchReport(
                    UUID.randomUUID().toString(), sourceName, sourceType,
                    sales.transactionDate().toString(), Instant.now().toString(),
                    sales.transactionCount(), sales.totalSales(), sales.storeIds(),
                    sales.storeNames(), outputSignatures, "PROCESSED");
            Path summary = bucket.publish(report);
            log.info("Lote OK: {} ({} transacciones, total = {}) -> {}",
                    sourceName, sales.transactionCount(), sales.totalSales(), summary);
        } catch (IOException | RuntimeException e) {
            if (publishedSignature != null) Files.deleteIfExists(publishedSignature);
            for (int i = archived.size() - 1; i >= 0; i--) {
                Move restore = archived.get(i);
                try { move(restore.destination(), restore.source()); }
                catch (IOException rollbackError) {
                    log.error("No se pudo revertir el archivo {}", restore.source(), rollbackError);
                }
            }
            throw e;
        }
    }

    private Path pairedSignature(Path file) {
        String name = file.getFileName().toString();
        int dot = name.lastIndexOf('.');
        String stem = name.substring(0, dot);
        String suffix = stem.startsWith("daily_sales_")
                ? stem.substring("daily_sales_".length()) : stem;
        return paths.input().resolve("signature_" + suffix + ".jpg");
    }

    private void quarantine(Path source, String explanation) {
        log.error("[ALERT EMAIL SENT -> soporte@aadtex.com] CRITICAL: Empty/Corrupt batch file: {}. {}",
                source.getFileName(), explanation);
        try {
            if (Files.isRegularFile(source)) {
                move(source, uniqueDestination(paths.error(), source.getFileName().toString()));
            }
        } catch (IOException ex) {
            log.error("No se pudo aislar el fichero {}", source, ex);
        }
    }

    private static boolean supported(Path file) {
        return List.of(".csv", ".enc", ".jpg", ".zip").contains(extension(file));
    }

    private static String extension(Path file) {
        String name = file.getFileName().toString().toLowerCase(Locale.ROOT);
        int dot = name.lastIndexOf('.');
        return dot < 0 ? "" : name.substring(dot);
    }

    private static Path uniqueDestination(Path dir, String name) throws IOException {
        Files.createDirectories(dir);
        Path result = dir.resolve(name);
        if (!Files.exists(result)) return result;
        int dot = name.lastIndexOf('.');
        String base = dot < 0 ? name : name.substring(0, dot);
        String ext = dot < 0 ? "" : name.substring(dot);
        do {
            result = dir.resolve(base + "_" + UUID.randomUUID().toString().substring(0, 8) + ext);
        } while (Files.exists(result));
        return result;
    }

    private static void move(Path from, Path to) throws IOException {
        try {
            Files.move(from, to, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(from, to, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private record Move(Path source, Path destination) { }

    /** Impide entradas ZIP bomba incluso si su tamaño declarado es falso. */
    private static class BoundedInputStream extends FilterInputStream {
        private final long max;
        private long used;

        BoundedInputStream(InputStream in, long max) { super(in); this.max = max; }

        @Override public int read() throws IOException {
            int b = super.read();
            if (b != -1 && ++used > max) throw new IOException("Entrada excede límite de tamaño");
            return b;
        }

        @Override public int read(byte[] b, int off, int len) throws IOException {
            int count = in.read(b, off, len);
            if (count > 0 && (used += count) > max) {
                throw new IOException("Entrada excede límite de tamaño");
            }
            return count;
        }
    }
}
