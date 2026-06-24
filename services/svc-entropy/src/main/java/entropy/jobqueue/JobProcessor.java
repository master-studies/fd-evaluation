package entropy.jobqueue;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import entropy.EntropyService;
import entropy.web.FunctionalDependencyGroupDto;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;

@Service
public class JobProcessor {

    private static final Logger LOGGER = Logger.getLogger(JobProcessor.class.getName());

    private final JobQueueService service;
    private final ObjectMapper objectMapper;

    public JobProcessor(JobQueueService service, ObjectMapper objectMapper) {
        this.service = service;
        this.objectMapper = objectMapper;
    }

    @Async("taskExecutor")
    public void processJobAsync(JobQueue job) {
        LOGGER.info("Starting async processing for job: " + job.getJobId());
        processJob(job);
    }

    private void processJob(JobQueue job) {
        try {
            LOGGER.info("Processing job (already marked as RUNNING): " + job.getJobId());
            
            String outputFilename = doWork(job);
            LOGGER.info("Work completed for job: " + job.getJobId() + ", output: " + outputFilename);
            
            // Store the output filename in resultData
            job.setResultData(outputFilename);
            
            LOGGER.info("Marking job as FINISHED: " + job.getJobId());
            service.markFinished(job);
            LOGGER.info("Job marked as FINISHED: " + job.getJobId());
        } catch (Exception ex) {
            LOGGER.log(Level.SEVERE, "Job failed: " + job.getJobId() + ", marking as FAILED", ex);
            service.markFailed(job, ex.getMessage());
        }
    }

    private String doWork(JobQueue job) throws Exception {
        // Deserialize payload to get parameters array
        List<String> params = objectMapper.readValue(
            job.getPayload(),
            new TypeReference<List<String>>() {}
        );
        
        // First element is filename
        String filename = params.get(0);

        // Query from db based on the filename, get the ResultData from ServiceType = FD-DISCOVERY with status = FINISHED
        JobQueue fdResult = service.findLatestFinishedFDDiscovery(filename)
                .orElseThrow(() -> new Exception("No FD-DISCOVERY result found for filename: " + filename));

        // calculate succinctness based on the fdResult's ResultData
        long startNs = System.nanoTime();
        String output = calculateEntropy(filename, fdResult.getResultData(), params.subList(1, params.size()).toArray(new String[0]));
        long elapsedMs = (System.nanoTime() - startNs) / 1_000_000;
        LOGGER.log(Level.INFO, "[Entropy] jobId=" + job.getJobId() + " filename=" + filename + " computeMs=" + elapsedMs);

        return output;
    }

    private String calculateEntropy(String filename, String resultDataJson, String[] options) throws Exception {
        List<FunctionalDependencyGroupDto> fdGroup = new ArrayList<>();
        
        JsonNode jsonArray = objectMapper.readTree(resultDataJson);
        
        for (JsonNode item : jsonArray) {
            String indicesStr = item.get("indices").asText();
            
            // Parse LHS
            IntList values = parseIndices(indicesStr);
            
            // Parse RHS
            int attributeID = extractAttributeID(indicesStr);

            // Create functional dependency group with indices and attributeID
            FunctionalDependencyGroupDto fd = new FunctionalDependencyGroupDto(values, attributeID);
            fdGroup.add(fd);
        }
        
        String fullName = filename + ".csv";
        String output = EntropyService.run(fullName, fdGroup, options);

        return output;
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
