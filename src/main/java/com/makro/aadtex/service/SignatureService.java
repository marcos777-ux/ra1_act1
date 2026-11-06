package com.makro.aadtex.service;

import com.makro.aadtex.config.StorePaths;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.stereotype.Service;

/** Copia JPG en streaming a un temporal; comprueba SOI y EOI sin cargar la imagen en RAM. */
@Service
public class SignatureService {
    public static final int BUFFER_SIZE = 4096;
    private static final long MAX_IMAGE_BYTES = 20L * 1024 * 1024;
    private final StorePaths paths;

    public SignatureService(StorePaths paths) { this.paths = paths; }

    public Path stage(InputStream source) throws IOException {
        Path tmp = Files.createTempFile(paths.signatures(), ".firma-parcial-", ".jpg");
        long count = 0;
        int first = -1, second = -1, third = -1, prev = -1, last = -1;
        try (InputStream in = new BufferedInputStream(new FilterInputStream(source) {
                 @Override public void close() { /* El ZIP continúa abierto */ }
             }, BUFFER_SIZE);
             OutputStream out = new BufferedOutputStream(Files.newOutputStream(tmp), BUFFER_SIZE)) {
            byte[] buffer = new byte[BUFFER_SIZE];
            int read;
            while ((read = in.read(buffer)) != -1) {
                if (count + read > MAX_IMAGE_BYTES) throw new IOException("Firma JPG demasiado grande");
                for (int i = 0; i < read; i++) {
                    int b = buffer[i] & 0xFF;
                    if (count == 0) first = b;
                    if (count == 1) second = b;
                    if (count == 2) third = b;
                    prev = last;
                    last = b;
                    count++;
                }
                out.write(buffer, 0, read);
            }
            if (count < 5 || first != 0xFF || second != 0xD8 || third != 0xFF
                    || prev != 0xFF || last != 0xD9) {
                throw new IOException("JPG defectuoso (marcadores SOI/EOI inválidos)");
            }
            return tmp;
        } catch (IOException | RuntimeException e) {
            Files.deleteIfExists(tmp);
            throw e;
        }
    }
}
