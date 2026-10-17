package com.HotelManagement.AirBnb.shared.csv;

import org.springframework.core.io.Resource;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * A CSV file read into rows addressed by header name (case-insensitive), so column order
 * doesn't matter. Supports quoted fields with commas and "" escapes; blank lines are ignored.
 */
public final class CsvTable {

    /** One data row; {@code line} is the 1-based line number in the file, for error messages. */
    public record Row(String file, int line, Map<String, String> values) {

        /** Trimmed value, or "" when the column is missing or blank. */
        public String get(String column) {
            String v = values.get(column.toLowerCase(Locale.ROOT));
            return v == null ? "" : v.strip();
        }

        public String where() {
            return file + " line " + line;
        }
    }

    private CsvTable() {
    }

    public static List<Row> read(Resource resource, String... requiredColumns) throws IOException {
        String file = resource.getFilename();
        List<Row> rows = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
            String headerLine = reader.readLine();
            if (headerLine == null) {
                return rows;
            }
            List<String> header = parseLine(headerLine.replace("﻿", "")).stream()
                    .map(h -> h.strip().toLowerCase(Locale.ROOT)).toList();
            for (String required : requiredColumns) {
                if (!header.contains(required)) {
                    throw new IOException(file + " is missing the '" + required + "' column (found " + header + ")");
                }
            }
            String line;
            int lineNo = 1;
            while ((line = reader.readLine()) != null) {
                lineNo++;
                if (line.isBlank()) {
                    continue;
                }
                List<String> fields = parseLine(line);
                Map<String, String> values = new HashMap<>();
                for (int i = 0; i < header.size() && i < fields.size(); i++) {
                    values.put(header.get(i), fields.get(i));
                }
                rows.add(new Row(file, lineNo, values));
            }
        }
        return rows;
    }

    static List<String> parseLine(String line) {
        List<String> fields = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (inQuotes) {
                if (c == '"' && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    current.append('"');
                    i++;
                } else if (c == '"') {
                    inQuotes = false;
                } else {
                    current.append(c);
                }
            } else if (c == '"') {
                inQuotes = true;
            } else if (c == ',') {
                fields.add(current.toString());
                current.setLength(0);
            } else {
                current.append(c);
            }
        }
        fields.add(current.toString());
        return fields;
    }
}
