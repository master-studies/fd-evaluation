package genuineness;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.io.File;

public class _CSVDataset {

    private static String filePath = "datasets/";
    private static String defaultFileName = "dbtesmaData.c100000.r10";
    private static boolean defaultHasHeader = false;
    private BufferedReader br;
    private boolean hasHeader;
    private String fileName;
    private String nextLine;
    private String delimiter;

    public _CSVDataset() throws IOException {

        this(_CSVDataset.defaultFileName, _CSVDataset.defaultHasHeader);
    }

    public _CSVDataset(String fileName, boolean hasHeader) throws IOException {

        this.fileName = fileName;
        this.hasHeader = hasHeader;

        this.br = new BufferedReader(new FileReader(new File(_CSVDataset.filePath + fileName)));
        this.nextLine = this.br.readLine();

        if (this.nextLine.split(",").length > this.nextLine.split(";").length) {
            this.delimiter = ",";
        } else {
            this.delimiter = ";";
        }
    }

    public List<String[]> readData() throws IOException {
        List<String[]> data = new ArrayList<>();
        BufferedReader reader = new BufferedReader(new FileReader(new File(filePath + fileName)));
        String line = reader.readLine();
        if (hasHeader) {
            line = reader.readLine(); // Skip header
        }
        while (line != null) {
            String[] row = line.split(delimiter);
            for (int i = 0; i < row.length; i++) {
                row[i] = row[i].replaceAll("\"", "");
                if (row[i].isEmpty()) {
                    row[i] = null;
                }
            }
            data.add(row);
            line = reader.readLine();
        }
        reader.close();
        return data;
    }   

}
