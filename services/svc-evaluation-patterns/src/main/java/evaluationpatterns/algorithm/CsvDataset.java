package evaluationpatterns.algorithm;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Loads a CSV file with a header row and provides column-name-based access.
 * Used by evaluation-patterns metrics which operate on column names rather than indices.
 */
public class CsvDataset {

    private static final String DATASETS_PATH = "datasets/";

    private final List<String> columns;
    private final List<String[]> rows;
    private final Map<String, Integer> colIndex;

    private CsvDataset(List<String> columns, List<String[]> rows) {
        this.columns = Collections.unmodifiableList(columns);
        this.rows = Collections.unmodifiableList(rows);
        this.colIndex = new HashMap<>();
        for (int i = 0; i < columns.size(); i++) {
            colIndex.put(columns.get(i), i);
        }
    }

    public static CsvDataset load(String filename) throws IOException {
        String path = DATASETS_PATH + filename;
        try (BufferedReader br = new BufferedReader(new FileReader(path))) {
            String firstLine = br.readLine();
            if (firstLine == null) {
                throw new IOException("Empty CSV file: " + path);
            }

            // Detect delimiter: comma vs semicolon
            char delim = firstLine.split(",").length >= firstLine.split(";").length ? ',' : ';';

            // Parse header
            String[] rawHeader = parseLine(firstLine, delim);
            List<String> columns = new ArrayList<>(rawHeader.length);
            for (String h : rawHeader) {
                columns.add(h.trim().replaceAll("^\"|\"$", "").trim());
            }

            // Parse data rows
            List<String[]> rows = new ArrayList<>();
            String line;
            while ((line = br.readLine()) != null) {
                if (line.isBlank()) continue;
                String[] raw = parseLine(line, delim);
                String[] row = new String[raw.length];
                for (int i = 0; i < raw.length; i++) {
                    String val = raw[i].replaceAll("^\"|\"$", "");
                    // Treat empty string and \N (MySQL null marker) as Java null
                    row[i] = (val.isEmpty() || val.equals("\\N")) ? null : val;
                }
                rows.add(row);
            }

            return new CsvDataset(columns, rows);
        }
    }

    /** Parse a single CSV line respecting double-quoted fields. */
    private static String[] parseLine(String line, char delim) {
        List<String> fields = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                // Handle escaped double-quote ""
                if (inQuotes && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    current.append('"');
                    i++;
                } else {
                    inQuotes = !inQuotes;
                }
            } else if (c == delim && !inQuotes) {
                fields.add(current.toString());
                current.setLength(0);
            } else {
                current.append(c);
            }
        }
        fields.add(current.toString());
        return fields.toArray(new String[0]);
    }

    public List<String> getColumns() {
        return columns;
    }

    public List<String[]> getRows() {
        return rows;
    }

    public int size() {
        return rows.size();
    }

    public boolean hasColumn(String name) {
        return colIndex.containsKey(name);
    }

    /** Get the value of a named column from a row, or null if missing/null. */
    public String get(String[] row, String colName) {
        Integer idx = colIndex.get(colName);
        if (idx == null || idx >= row.length) return null;
        return row[idx];
    }
}
