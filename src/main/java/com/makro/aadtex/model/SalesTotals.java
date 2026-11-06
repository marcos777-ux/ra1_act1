package com.makro.aadtex.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Resultado de analizar un CSV. Usa BigDecimal para evitar errores de coma flotante. */
public record SalesTotals(
        BigDecimal totalSales,
        int transactionCount,
        List<String> storeIds,
        List<String> storeNames,
        LocalDate transactionDate) {
}
