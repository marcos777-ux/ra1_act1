package com.makro.aadtex.model;

import java.math.BigDecimal;
import java.util.List;

/** Contrato JSON publicado para que lo lea la App 2. */
public record BatchReport(
        String batchId,
        String sourceFile,
        String sourceType,
        String transactionDate,
        String processedAtUtc,
        int transactionCount,
        BigDecimal totalSales,
        List<String> storeIds,
        List<String> storeNames,
        List<String> signatures,
        String status) {
}
