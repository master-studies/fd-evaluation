package coverage.jobqueue;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import coverage._FunctionalDependencyGroup;
import coverage._CoverageCalculator;

@Component
public class JobWorker {

    private static final Logger LOGGER = Logger.getLogger(JobWorker.class.getName());

    private final JobQueueService service;
    private final ObjectMapper objectMapper;

    public JobWorker(JobQueueService service, ObjectMapper objectMapper) {
        this.service = service;
        this.objectMapper = objectMapper;
    }

    @Scheduled(fixedDelayString = "${jobs.worker.delay-ms:2000}")
    public void pollAndWork() {
        try {
            service.pollNextNew().ifPresent(this::processJob);
        } catch (Exception ex) {
            LOGGER.log(Level.SEVERE, "Job worker iteration failed", ex);
        }
    }

    private void processJob(JobQueue job) {
        JobQueue running = service.markRunning(job);
        try {
            List<Double> result = doWork(running);
            
            // Serialize to JSON and store in DB
            String resultJson = objectMapper.writeValueAsString(result);
            running.setResultData(resultJson);
            
            service.markFinished(running);
        } catch (Exception ex) {
            service.markFailed(running, ex.getMessage());
            LOGGER.log(Level.SEVERE, "Job failed: " + running.getJobId(), ex);
        }
    }

    private List<Double> doWork(JobQueue job) throws Exception {
        // Extract filename from payload
        String filename = job.getPayload();

        // Query from db based on the filename, get the ResultData from ServiceType = FD-DISCOVERY with status = FINISHED
        JobQueue fdResult = service.findLatestFinishedFDDiscovery(filename)
                .orElseThrow(() -> new Exception("No FD-DISCOVERY result found for filename: " + filename));

        // calculate succinctness based on the fdResult's ResultData
        List<Double> scores = calculateSuccinctness(fdResult.getResultData(), filename);

        return scores;
    }

    private List<Double> calculateSuccinctness(String resultDataJson, String filename) throws Exception {
        List<_FunctionalDependencyGroup> fdGroup = new ArrayList<>();
        
        JsonNode jsonArray = objectMapper.readTree(resultDataJson);
        
        for (JsonNode item : jsonArray) {
            String indicesStr = item.get("indices").asText();
            
            // Parse LHS
            IntList values = parseIndices(indicesStr);
            
            // Parse RHS
            int attributeID = extractAttributeID(indicesStr);

            // Create functional dependency group with indices and attributeID
            _FunctionalDependencyGroup fd = new _FunctionalDependencyGroup(attributeID, values);
            fdGroup.add(fd);
        }
        
        _CoverageCalculator calc = new _CoverageCalculator();
        String fullName = filename + ".csv";
        // Map<_FunctionalDependencyGroup, Double> results = calc.computeMetrics(fdGroup, fullName);
        List<Double> results = calc.computeMetrics(fdGroup, fullName);

        return results;
    }

    private IntList parseIndices(String indicesStr) {
        IntList indices = new IntArrayList();
        
        String[] parts = indicesStr.split("-->");
        if (parts.length > 0) {
            String indexPart = parts[0].trim();
            indexPart = indexPart.replaceAll("[{}]", "").trim();
            String[] indexStrings = indexPart.split(",");
            
            for (String idx : indexStrings) {
                try {
                    indices.add(Integer.parseInt(idx.trim()));
                } catch (NumberFormatException e) {
                    LOGGER.log(Level.WARNING, "Failed to parse index: " + idx, e);
                }
            }
        }
        
        return indices;
    }

    private int extractAttributeID(String indicesStr) {
        String[] parts = indicesStr.split("-->");
        if (parts.length > 1) {
            try {
                return Integer.parseInt(parts[1].trim());
            } catch (NumberFormatException e) {
                LOGGER.log(Level.WARNING, "Failed to parse attribute ID from: " + indicesStr, e);
                return 0;
            }
        }
        return 0;
    }
}
