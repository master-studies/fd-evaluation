package cz.cuni.matfyz.algorithms.depminer.jobqueue;

import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;

import cz.cuni.matfyz.algorithms.depminer.DepMiner;
import cz.cuni.matfyz.algorithms.depminer.model._CSVTestCase;
import cz.cuni.matfyz.algorithms.depminer.model._FunctionalDependencyOutput;

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
            List<_FunctionalDependencyOutput> result = doWork(running);
            
            // Serialize to JSON and store in DB
            String resultJson = objectMapper.writeValueAsString(result);
            running.setResultData(resultJson);
            
            service.markFinished(running);
        } catch (Exception ex) {
            service.markFailed(running, ex.getMessage());
            LOGGER.log(Level.SEVERE, "Job failed: " + running.getJobId(), ex);
        }
    }

    private List<_FunctionalDependencyOutput> doWork(JobQueue job) throws Exception {
        // Extract filename from payload
        String filename = job.getPayload();
        boolean hasHeader = false;
        String fullName = filename + ".csv";
        
        _CSVTestCase input = new _CSVTestCase(fullName, hasHeader);
        DepMiner miner = new DepMiner(input, fullName);
        
        return miner.execute();
    }
}
