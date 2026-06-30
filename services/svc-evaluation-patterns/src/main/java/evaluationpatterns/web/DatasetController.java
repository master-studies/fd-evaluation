package evaluationpatterns.web;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

@RestController
public class DatasetController {

    private static final String DATASETS_FOLDER = "datasets";
    private static final Logger LOGGER = Logger.getLogger(DatasetController.class.getName());

    @PostMapping("/dataset/upload")
    public ResponseEntity<String> uploadCsv(@RequestParam("file") MultipartFile file) {
        try {
            if (file.isEmpty()) {
                return ResponseEntity.badRequest().body("File is empty");
            }
            if (!file.getOriginalFilename().endsWith(".csv")) {
                return ResponseEntity.badRequest().body("Only CSV files are allowed");
            }

            File datasetsDir = new File(DATASETS_FOLDER);
            if (!datasetsDir.exists()) {
                datasetsDir.mkdir();
            }

            String filePath = DATASETS_FOLDER + File.separator + file.getOriginalFilename();
            File existing = new File(filePath);
            if (existing.exists() && !existing.delete()) {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body("Failed to replace existing file: " + file.getOriginalFilename());
            }

            try (FileOutputStream fos = new FileOutputStream(filePath)) {
                fos.write(file.getBytes());
            }

            LOGGER.log(Level.INFO, "File uploaded: " + filePath);
            return ResponseEntity.ok("File uploaded successfully: " + file.getOriginalFilename());

        } catch (IOException ex) {
            LOGGER.log(Level.SEVERE, "Error uploading file", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error uploading file: " + ex.getMessage());
        }
    }
}
