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
import cz.cuni.matfyz.algorithms.depminer.model.ArmstrongStorageDto;

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
            String filename = running.getPayload();
            String fullName = filename + ".csv";

            _CSVTestCase input = new _CSVTestCase(fullName, false);
            DepMiner miner = new DepMiner(input, fullName);

            long startNs = System.nanoTime();
            List<_FunctionalDependencyOutput> result = miner.execute();
            long elapsedMs = (System.nanoTime() - startNs) / 1_000_000;
            LOGGER.log(Level.INFO, "[FD-Discovery] jobId=" + running.getJobId()
                    + " filename=" + filename + " computeMs=" + elapsedMs);

            // ResultData: unchanged JSON array format — all other services depend on this
            running.setResultData(objectMapper.writeValueAsString(result));

            // ArmstrongData: new column, only read by the negative-examples endpoint
            ArmstrongStorageDto arDto = new ArmstrongStorageDto(
                    miner.getLastColumnNames(), miner.getLastAbstractAR());
            running.setArmstrongData(objectMapper.writeValueAsString(arDto));

            service.markFinished(running);
        } catch (Exception ex) {
            service.markFailed(running, ex.getMessage());
            LOGGER.log(Level.SEVERE, "Job failed: " + running.getJobId(), ex);
        }
    }
}
