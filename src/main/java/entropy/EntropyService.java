package entropy;

import com.opencsv.CSVParserBuilder;
import com.opencsv.CSVReader;
import com.opencsv.CSVReaderBuilder;
import com.opencsv.exceptions.CsvException;

import entropy.web.FunctionalDependencyGroupDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

public class EntropyService {
    private static final Logger logger = LoggerFactory.getLogger(EntropyService.class);

    public static String run(String filename, List<FunctionalDependencyGroupDto> fdDtos, String[] optionTokens) throws IOException {
        Options opts;
        try {
            opts = parseOptions(optionTokens);
            logger.debug("Options parsed: encoded={}, delimiter={}, header={}, showProcess={}, identifyOnes={}, considerSubtables={}, randomisation={}, closure={}", 
                    opts.encoded, opts.delimiter, opts.header, opts.showProcess, opts.identifyOnes, opts.considerSubtables, opts.randomisation, opts.closure);
        } catch (IllegalArgumentException e) {
            logger.error("Error parsing options: {}", e.getMessage());
            return e.getMessage();
        }

        int[][] table;
        String[] headers = null;
        try {
            if (opts.encoded) {
                table = getTable(filename);
            } else {
                // Read CSV and extract headers if requested
                if (opts.header) {
                    headers = readCsvHeaders(filename, opts.delimiter);
                }
                table = readCsv(filename, opts.delimiter, opts.header);
            }
        } catch (FileNotFoundException | IllegalArgumentException e) {
            return e.getMessage();
        }

        List<FunctionalDependency> fds;
        try {
            fds = mapDtos(fdDtos);
        } catch (IllegalArgumentException e) {
            return e.getMessage();
        }

        Computation computation = new Computation(table, opts.identifyOnes, opts.considerSubtables, opts.randomisation);
        if (opts.showProcess) {
            computation.enableProcessedCount();
        }

        try {
            int fdCount = 0;
            for (FunctionalDependency fd : fds) {
                computation.addFuncDepWithCheck(fd);
                fdCount++;
            }
            logger.info("Successfully added {} FDs", fdCount);
        } catch (IllegalArgumentException e) {
            return e.getMessage();
        }

        if (opts.closure) {
            long startClosure = System.nanoTime();
            computation.addTransitiveClosure();
            long endClosure = System.nanoTime();
            long closureTime = (endClosure - startClosure) / 1_000_000;
            logger.info("Transitive closure computed in {} ms", closureTime);
        }

        logger.info("Starting entropy computation...");
        long start = System.currentTimeMillis();
        double[][] infContMat = computation.getInformationContentMatrix();
        long end = System.currentTimeMillis();
        double runtime = (end - start) / 1000.0;
        logger.info("Entropy computation completed in {} seconds", runtime);

        if (opts.outputName != null) {
            try {
                return writeResultToOutputFile(opts.outputName, infContMat, headers);
            } catch (FileAlreadyExistsException e) {
                return e.getMessage();
            }
        }

        String output = getOutputString(opts.encoded ? null : filename, computation.getFdsString(), infContMat, runtime);
        System.out.println(output);
        return output;
    }

    private static Options parseOptions(String[] args) {
        Options opts = new Options();
        if (args == null) {
            return opts;
        }

        int i = 0;
        while (i < args.length) {
            String token = args[i++];
            switch (token) {
                case "-e" -> opts.encoded = true;
                case "-d" -> {
                    if (i >= args.length) {
                        throw new IllegalArgumentException("parameter for option -d missing");
                    }
                    if (args[i].length() != 1) {
                        throw new IllegalArgumentException("delimiter must be a single character");
                    }
                    opts.delimiter = args[i++].charAt(0);
                }
                case "--header" -> opts.header = true;
                case "--name" -> {
                    if (i >= args.length) {
                        throw new IllegalArgumentException("parameter for option --name missing");
                    }
                    opts.outputName = args[i++];
                }
                case "--show-process" -> opts.showProcess = true;
                case "-i" -> opts.identifyOnes = true;
                case "-s" -> opts.considerSubtables = true;
                case "-r" -> {
                    if (i >= args.length) {
                        throw new IllegalArgumentException("parameter for option -r missing");
                    }
                    try {
                        opts.randomisation = Integer.parseInt(args[i]);
                    } catch (NumberFormatException e) {
                        throw new IllegalArgumentException("number of iterations must be an integer");
                    }
                    i++;
                }
                case "--closure" -> opts.closure = true;
                default -> throw new IllegalArgumentException(String.format("unexpected parameter \"%s\"", token));
            }
        }

        return opts;
    }

    private static int[][] getTable(String tableStr) {
        String[] lines = tableStr.split(";");
        List<String[]> cells = new ArrayList<>();
        int cols = 0;

        for (String lineStr : lines) {
            String[] line = lineStr.split(",");
            cells.add(line);
            int c = line.length;

            if (c == 0 || line[0].isEmpty()) {
                continue;
            }

            if (cols == 0) {
                cols = c;
            } else if (cols != c) {
                throw new IllegalArgumentException("lines must have same number of cells");
            }
        }

        return stringArrListToIntMatrix(cells);
    }

    private static int[][] readCsv(String fileName, char delimiter, boolean header) throws IOException {
        String filePath = "datasets/" + fileName;
        logger.info("Resolved CSV file path: {}", filePath);
        
        try (FileReader fileReader = new FileReader(filePath);
             CSVReader csvReader = new CSVReaderBuilder(fileReader).withSkipLines(header ? 1 : 0)
                     .withCSVParser(new CSVParserBuilder().withSeparator(delimiter).build()).build()) {
            List<String[]> cells = csvReader.readAll();
            return stringArrListToIntMatrix(cells);
        } catch (FileNotFoundException e) {
            throw new FileNotFoundException("source file not found: " + filePath);
        } catch (IOException e) {
            throw new IOException("error reading file: " + filePath);
        } catch (CsvException e) {
            throw new RuntimeException("error reading file: " + filePath);
        }
    }

    private static String[] readCsvHeaders(String fileName, char delimiter) throws IOException {
        String filePath = "datasets/" + fileName;
        try (FileReader fileReader = new FileReader(filePath);
             CSVReader csvReader = new CSVReaderBuilder(fileReader)
                     .withCSVParser(new CSVParserBuilder().withSeparator(delimiter).build()).build()) {
            String[] headers = csvReader.readNext();
            return headers;
        } catch (FileNotFoundException e) {
            throw new FileNotFoundException("source file not found: " + filePath);
        } catch (IOException e) {
            throw new IOException("error reading file: " + filePath);
        } catch (CsvException e) {
            throw new RuntimeException("error reading file: " + filePath);
        }
    }

    private static List<FunctionalDependency> mapDtos(List<FunctionalDependencyGroupDto> dtos) {
        List<FunctionalDependency> list = new ArrayList<>();
        if (dtos == null) {
            return list;
        }

        for (FunctionalDependencyGroupDto dto : dtos) {
            if (dto == null || dto.values == null) {
                throw new IllegalArgumentException("Functional dependency values missing");
            }
            if (dto.attributeID < 0) {
                throw new IllegalArgumentException("attributeID must be positive");
            }

            Set<Integer> left = dto.values.stream().collect(Collectors.toSet());
            int right = dto.attributeID;
            list.add(new FunctionalDependency(left, Set.of(right)));
        }

        return list;
    }

    private static class Options {
        boolean encoded = false;
        char delimiter = ',';
        boolean header = false;
        String outputName = null;
        boolean showProcess = false;
        boolean identifyOnes = false;
        boolean considerSubtables = false;
        int randomisation = 0;
        boolean closure = false;
    }

    private static String writeResultToOutputFile(String outputPath, double[][] infContMat, String[] headers) throws IOException {
        if (outputPath != null) {
            String filename = determineFilename(outputPath);
            writeMatrixToCsv(filename, infContMat, headers);
            return filename;
        }
        return null;
    }

    private static String getOutputString(String tablePath, String fdsString, double[][] infContMat, double runtime) {
        StringBuilder builder = new StringBuilder();

        if (tablePath != null) {
            builder.append("Source: ").append(tablePath).append("\n");
        }

        builder.append("FDs: ").append(fdsString).append("\n")
                .append(matrixToString(infContMat, "\t")).append("\n")
                .append("Runtime: ").append(runtime).append(" seconds");
        return builder.toString();
    }

    private static int[][] stringArrListToIntMatrix(List<String[]> cells) {
        int[][] table = new int[cells.size()][cells.get(0).length];
        int j = 0;

        for (String[] row : cells) {
            if (row.length > 0 && !row[0].isEmpty()) {
                for (int k = 0; k < row.length; k++) {
                    try {
                        table[j][k] = Integer.parseInt(row[k]);

                        if (table[j][k] <= 0) {
                            return encodeCells(cells);
                        }
                    } catch (NumberFormatException e) {
                        return encodeCells(cells);
                    }
                }
                j++;
            }
        }

        return table;
    }

    private static String determineFilename(String filename) {
        String[] nameExt = filename.split("\\.", 2);
        int i = 1;

        while (new File(filename).exists()) {
            filename = nameExt.length > 1 ? String.format("%s(%d).%s", nameExt[0], ++i, nameExt[1]) : String.format("%s(%d)", nameExt[0], ++i);
        }

        return filename;
    }

    private static void writeMatrixToCsv(String filepath, double[][] matrix, String[] headers) throws IOException {
        File parentFile = new File(filepath).getParentFile();
        File parentFileIt = parentFile;

        while (parentFileIt != null) {
            if (parentFileIt.isFile()) {
                throw new FileAlreadyExistsException(String.format("cannot create directory '%s': file exists", parentFileIt));
            }

            parentFileIt = parentFileIt.getParentFile();
        }

        if (parentFile != null) {
            String parentPath = parentFile.getAbsolutePath();

            if (!new File(parentPath).exists()) {
                Files.createDirectories(Paths.get(parentPath));
            }
        }

        try (BufferedWriter writer = new BufferedWriter(new FileWriter(filepath))) {
            // Write headers if provided
            if (headers != null && headers.length > 0) {
                writer.write(String.join(",", headers));
                writer.write("\n");
            }
            // Write matrix data
            writer.write(matrixToString(matrix, ","));
            writer.write("\n");
        }
    }

    private static String matrixToString(double[][] matrix, String delimiter) {
        Function<double[], String> rowToString = row -> String.join(delimiter, Arrays.stream(row)
                .mapToObj(cell -> cell == 1 ? "1" : String.valueOf(cell)).toArray(String[]::new));
        String[] matrixConverted = Arrays.stream(matrix).map(rowToString).toArray(String[]::new);
        return String.join("\n", matrixConverted);
    }

    private static int[][] encodeCells(List<String[]> cells) {
        Map<String, Integer> stringToInt = new HashMap<>();
        int[][] table = new int[cells.size()][cells.get(0).length];
        int nextNumber = 1;

        for (int i = 0; i < cells.size(); i++) {
            for (int j = 0; j < cells.get(0).length; j++) {
                if (stringToInt.containsKey(cells.get(i)[j])) {
                    table[i][j] = stringToInt.get(cells.get(i)[j]);
                } else {
                    table[i][j] = nextNumber;
                    stringToInt.put(cells.get(i)[j], nextNumber++);
                }
            }
        }

        return table;
    }

}
