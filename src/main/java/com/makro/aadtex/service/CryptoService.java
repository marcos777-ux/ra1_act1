package com.makro.aadtex.service;

import com.makro.aadtex.config.StorePaths;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Service;

/** Cifrado del enunciado: AES-128/ECB/PKCS5Padding. ECB NO es seguro para producción. */
@Service
public class CryptoService {
    private static final long MAX_ENCRYPTED_BYTES = 16L * 1024 * 1024;
    private final StorePaths paths;

    public CryptoService(StorePaths paths) { this.paths = paths; }

    public ByteArrayInputStream decryptToMemory(Path file) throws IOException {
        if (Files.size(file) > MAX_ENCRYPTED_BYTES) {
            throw new IOException("Archivo AES demasiado grande para desencriptación en RAM");
        }
        byte[] encrypted = Files.readAllBytes(file);
        if (encrypted.length == 0 || encrypted.length % 16 != 0) {
            throw new IOException("Contenido AES inválido: longitud no múltiplo de 16");
        }
        byte[] secret = Files.readString(paths.key(), StandardCharsets.UTF_8).trim()
                .getBytes(StandardCharsets.UTF_8);
        if (secret.length != 16) throw new IOException("Clave AES-128 incorrecta: debe tener 16 bytes");
        try {
            Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(secret, "AES"));
            // El texto en claro no se guarda en disco; se pasa como flujo en RAM al lector CSV.
            return new ByteArrayInputStream(cipher.doFinal(encrypted));
        } catch (GeneralSecurityException e) {
            throw new IOException("No se pudo descifrar el lote AES", e);
        }
    }
}
