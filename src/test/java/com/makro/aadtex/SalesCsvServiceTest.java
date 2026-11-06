package com.makro.aadtex;

import com.makro.aadtex.model.SalesTotals;
import com.makro.aadtex.service.SalesCsvService;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SalesCsvServiceTest {
    private final SalesCsvService service = new SalesCsvService();
    private static final String HEADER = "StoreID,StoreName,SalesAmount,Category,TransactionDate\n";

    @Test
    void admiteComasDentroDeComillasYCaracteresCjk() throws Exception {
        String sample = HEADER + "ID,\"Zara 上海, Madrid\",100.20,WOMEN,2026-09-13\n";
        SalesTotals result = service.parse(bytes(sample));
        assertEquals(1, result.transactionCount());
        assertEquals(new BigDecimal("100.20"), result.totalSales());
        assertEquals("Zara 上海, Madrid", result.storeNames().get(0));
    }

    @Test
    void rechazaImportesInvalidosYCsvSinFilas() {
        assertThrows(IOException.class, () -> service.parse(bytes(HEADER)));
        assertThrows(IOException.class, () -> service.parse(bytes(HEADER + "ID,Tiendita,-2.00,MEN,2026-09-13\n")));
        assertThrows(IOException.class, () -> service.parse(bytes(HEADER + "ID,Tiendita,6.111,MEN,2026-09-13\n")));
        assertThrows(IOException.class, () -> service.parse(bytes("mal,cabecera\n")));
    }

    @Test
    void rechazaUtf8Malformado() {
        byte[] prefix = HEADER.getBytes(StandardCharsets.UTF_8);
        byte[] invalid = new byte[prefix.length + 1];
        System.arraycopy(prefix, 0, invalid, 0, prefix.length);
        invalid[invalid.length - 1] = (byte) 0xFF;
        assertThrows(IOException.class, () -> service.parse(new ByteArrayInputStream(invalid)));
    }

    private static ByteArrayInputStream bytes(String value) {
        return new ByteArrayInputStream(value.getBytes(StandardCharsets.UTF_8));
    }
}
