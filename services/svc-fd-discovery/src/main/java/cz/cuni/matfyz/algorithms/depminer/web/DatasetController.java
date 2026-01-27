package cz.cuni.matfyz.algorithms.depminer.web;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

@RestController
public class DatasetController {

    private static final String DATASETS_FOLDER = "datasets";
    private static final Logger LOGGER = Logger.getLogger(DatasetController.class.getName());


    @GetMapping("/dataset/list")
    public List<String> getDatasetList() {
        List<String> csvFiles = new ArrayList<>();
        File datasetsDir = new File(DATASETS_FOLDER);

        // If datasets folder doesn't exist, return empty list
        if (!datasetsDir.exists() || !datasetsDir.isDirectory()) {
            return csvFiles;
        }

        File[] files = datasetsDir.listFiles((dir, name) -> name.toLowerCase().endsWith(".csv"));

        if (files != null) {
            for (File file : files) {
                csvFiles.add(file.getName());
            }
        }

        return csvFiles;
    }

    /**
     * Upload a CSV file from a multipart request
     * 
     * @param file The CSV file to upload
     * @return ResponseEntity with success or error message
     */
    @PostMapping("/dataset/upload")
    public ResponseEntity<String> uploadCsv(@RequestParam("file") MultipartFile file) {
        try {
            // Validate file
            if (file.isEmpty()) {
                return ResponseEntity.badRequest().body("File is empty");
            }

            if (!file.getOriginalFilename().endsWith(".csv")) {
                return ResponseEntity.badRequest().body("Only CSV files are allowed");
            }

            // Create datasets folder if it doesn't exist
            File datasetsDir = new File(DATASETS_FOLDER);
            if (!datasetsDir.exists()) {
                datasetsDir.mkdir();
            }

            // Check if file already exists
            String filePath = DATASETS_FOLDER + File.separator + file.getOriginalFilename();
            File existingFile = new File(filePath);
            if (existingFile.exists()) {
                if (!existingFile.delete()) {
                    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                            .body("Failed to replace existing file: " + file.getOriginalFilename());
                }
            }

            // Save file
            try (FileOutputStream fos = new FileOutputStream(filePath)) {
                fos.write(file.getBytes());
            }

            LOGGER.log(Level.INFO, "File uploaded successfully: " + filePath);
            return ResponseEntity.ok("File uploaded successfully: " + file.getOriginalFilename());

        } catch (IOException ex) {
            LOGGER.log(Level.SEVERE, "Error uploading file", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error uploading file: " + ex.getMessage());
        }
    }
}
