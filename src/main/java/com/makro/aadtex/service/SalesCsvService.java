package com.makro.aadtex.service;

import com.makro.aadtex.model.SalesTotals;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;

/** Lector UTF-8 estricto. No cierra el InputStream recibido: puede ser una entrada ZIP. */
@Service
public class SalesCsvService {
    private static final List<String> HEADER = List.of(
            "StoreID", "StoreName", "SalesAmount", "Category", "TransactionDate");

    public SalesTotals parse(InputStream input) throws IOException {
        var decoder = StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT);
        BufferedReader reader = new BufferedReader(new InputStreamReader(input, decoder), 4096);
        String header = reader.readLine();
        if (header == null) throw new IOException("CSV vacío: falta cabecera");
        if (header.startsWith("\uFEFF")) header = header.substring(1); // UTF-8 BOM opcional
        if (!csvFields(header).equals(HEADER)) {
            throw new IOException("Cabecera CSV incorrecta: " + header);
        }
        BigDecimal total = BigDecimal.ZERO;
        int count = 0;
        Set<String> storeIds = new LinkedHashSet<>();
        Set<String> storeNames = new LinkedHashSet<>();
        LocalDate date = null;
        String line;
        int lineNo = 1;
        while ((line = reader.readLine()) != null) {
            lineNo++;
            if (line.isBlank()) continue;
            List<String> fields = csvFields(line);
            if (fields.size() != 5) throw new IOException("CSV línea " + lineNo + ": se esperaban 5 columnas");
            String id = fields.get(0).trim();
            String name = fields.get(1).trim();
            String category = fields.get(3).trim();
            if (id.isEmpty() || name.isEmpty() || category.isEmpty()) {
                throw new IOException("CSV línea " + lineNo + ": campos obligatorios vacíos");
            }
            try {
                BigDecimal amount = new BigDecimal(fields.get(2).trim());
                if (amount.scale() > 2 || amount.signum() < 0) {
                    throw new IOException("CSV línea " + lineNo + ": importe negativo o con más de 2 decimales");
                }
                LocalDate rowDate = LocalDate.parse(fields.get(4).trim());
                if (date != null && !date.equals(rowDate)) {
                    throw new IOException("CSV línea " + lineNo + ": contiene otra fecha; un lote debe ser diario");
                }
                date = rowDate;
                total = total.add(amount);
                storeIds.add(id);
                storeNames.add(name);
                count++;
            } catch (NumberFormatException | DateTimeParseException ex) {
                throw new IOException("CSV línea " + lineNo + ": importe o fecha inválidos", ex);
            }
        }
        if (count == 0) throw new IOException("CSV sin transacciones");
        return new SalesTotals(total.setScale(2), count,
                List.copyOf(storeIds), List.copyOf(storeNames), date);
    }

    /** CSV con comillas, comas en campos y comillas escapadas (sin saltos de línea entre comillas). */
    static List<String> csvFields(String line) throws IOException {
        List<String> fields = new ArrayList<>();
        StringBuilder value = new StringBuilder();
        boolean quoted = false;
        boolean closedQuote = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (quoted) {
                if (c == '"') {
                    if (i + 1 < line.length() && line.charAt(i + 1) == '"') {
                        value.append('"'); i++;
                    } else { quoted = false; closedQuote = true; }
                } else { value.append(c); }
            } else if (c == ',') {
                fields.add(value.toString()); value.setLength(0); closedQuote = false;
            } else if (c == '"' && value.isEmpty() && !closedQuote) {
                quoted = true;
            } else if (closedQuote || c == '"') {
                throw new IOException("CSV con comillas mal formadas");
            } else {
                value.append(c);
            }
        }
        if (quoted) throw new IOException("CSV con comillas sin cerrar");
        fields.add(value.toString());
        return fields;
    }
}
