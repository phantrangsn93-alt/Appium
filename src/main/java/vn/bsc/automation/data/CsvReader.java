package vn.bsc.automation.data;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Minimal RFC-4180 CSV reader for test data edited in Excel (UTF-8, optional BOM, ',' or ';' separator,
 * quoted fields). Lines starting with '#' and empty lines are ignored.
 */
public final class CsvReader {

    private CsvReader() {
    }

    public static List<Map<String, String>> read(String resourcePath) {
        try (InputStream in = CsvReader.class.getClassLoader().getResourceAsStream(resourcePath)) {
            if (in == null) {
                throw new IllegalStateException("Test data not found: " + resourcePath);
            }
            BufferedReader br = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            List<String> lines = new ArrayList<>();
            for (String l; (l = br.readLine()) != null; ) {
                if (lines.isEmpty() && l.startsWith("﻿")) {
                    l = l.substring(1); // Excel BOM
                }
                if (!l.isBlank() && !l.stripLeading().startsWith("#")) {
                    lines.add(l);
                }
            }
            if (lines.isEmpty()) {
                return List.of();
            }
            char sep = lines.get(0).contains(";") && !lines.get(0).contains(",") ? ';' : ',';
            List<String> header = split(lines.get(0), sep);
            List<Map<String, String>> rows = new ArrayList<>();
            for (int i = 1; i < lines.size(); i++) {
                List<String> cells = split(lines.get(i), sep);
                Map<String, String> row = new LinkedHashMap<>();
                for (int c = 0; c < header.size(); c++) {
                    row.put(header.get(c).trim(), c < cells.size() ? cells.get(c).trim() : "");
                }
                rows.add(row);
            }
            return rows;
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read " + resourcePath, e);
        }
    }

    static List<String> split(String line, char sep) {
        List<String> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < line.length(); i++) {
            char ch = line.charAt(i);
            if (quoted) {
                if (ch == '"' && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    cur.append('"');
                    i++;
                } else if (ch == '"') {
                    quoted = false;
                } else {
                    cur.append(ch);
                }
            } else if (ch == '"') {
                quoted = true;
            } else if (ch == sep) {
                out.add(cur.toString());
                cur.setLength(0);
            } else {
                cur.append(ch);
            }
        }
        out.add(cur.toString());
        return out;
    }
}
