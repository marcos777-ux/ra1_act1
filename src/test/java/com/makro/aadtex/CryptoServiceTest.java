package com.makro.aadtex;

import com.makro.aadtex.bootstrap.MultiLanguageDataGenerator;
import com.makro.aadtex.config.StorePaths;
import com.makro.aadtex.service.CryptoService;
import com.makro.aadtex.service.SalesCsvService;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class CryptoServiceTest {
    @TempDir Path temp;

    @Test
    void descifraFinanzasEnMemoria() throws Exception {
        StorePaths paths = new StorePaths(temp.toString());
        new MultiLanguageDataGenerator(paths, true).run();
        try (var clear = new CryptoService(paths)
                .decryptToMemory(paths.input().resolve("daily_sales_financial.enc"))) {
            var result = new SalesCsvService().parse(clear);
            assertEquals("95000.00", result.totalSales().toPlainString());
            assertEquals("Central Financial Office", result.storeNames().get(0));
        }
    }
}
