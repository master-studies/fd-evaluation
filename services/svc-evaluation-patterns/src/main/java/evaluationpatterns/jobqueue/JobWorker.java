package evaluationpatterns.jobqueue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import evaluationpatterns.algorithm.CsvDataset;
import evaluationpatterns.algorithm.EvaluationPatternsProcessor;
import evaluationpatterns.algorithm.EvaluationPatternsProcessor.ProgressCallback;
import evaluationpatterns.algorithm.EvaluationPatternsProcessor.QuestionCallback;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

@Component
public class JobWorker {

    private static final Logger LOGGER = Logger.getLogger(JobWorker.class.getName());

    private final JobQueueService service;
    private final SuspiciousAnswerService answerService;
    private final ObjectMapper om = new ObjectMapper();

    public JobWorker(JobQueueService service, SuspiciousAnswerService answerService) {
        this.service = service;
        this.answerService = answerService;
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
        try {
            JobQueue running = service.markRunning(job);
            String resultCsv = doWork(running);
            running.setResultData(resultCsv);
            service.markFinished(running);
        } catch (Exception ex) {
            service.markFailed(job, ex.getMessage());
            LOGGER.log(Level.SEVERE, "Job failed: " + job.getJobId(), ex);
        }
    }

    private String doWork(JobQueue job) throws Exception {
        UUID jobId = job.getJobId();
        String filename = job.getPayload();

        JobQueue fdResult = service.findLatestFinishedFDDiscovery(filename)
                .orElseThrow(() -> new Exception("No FD-DISCOVERY result found for: " + filename));

        CsvDataset dataset = CsvDataset.load(filename + ".csv");
        EvaluationPatternsProcessor processor = new EvaluationPatternsProcessor();
        List<EvaluationPatternsProcessor.FdEntry> fds = processor.parseFds(fdResult.getResultData());

        LOGGER.info("[EvalPatterns] jobId=" + jobId + " filename=" + filename
                + " parsedFds=" + fds.size());

        // Ordered map: rhs -> antichain strings (null while in-progress)
        Map<String, List<String>> rhsResults = Collections.synchronizedMap(new LinkedHashMap<>());

        ProgressCallback progressCallback = new ProgressCallback() {
            @Override
            public void onRhsStarted(String rhs) {
                rhsResults.put(rhs, null); // mark as in-progress
                persistState(jobId, "processing", rhsResults, rhs, null, null, null);
            }

            @Override
            public void onRhsCompleted(String rhs, List<String> antichainLhs) {
                rhsResults.put(rhs, new ArrayList<>(antichainLhs));
                persistState(jobId, "processing", rhsResults, null, null, null, null);
            }
        };

        QuestionCallback questionCallback = (lhs, rhs) -> {
            String questionText = "Does {" + String.join(", ", lhs) + "} -> " + rhs
                    + " make semantic sense as a genuine functional dependency? "
                    + "Or is it a coincidental pattern in the data?";
            persistState(jobId, "awaiting_input", rhsResults, rhs, lhs, rhs, questionText);
            try {
                return answerService.waitForAnswer(jobId);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        };

        long startNs = System.nanoTime();
        List<EvaluationPatternsProcessor.RhsAntichain> results =
                processor.process(dataset, fds, progressCallback, questionCallback);
        String csv = processor.buildOutputCsv(results);
        long elapsedMs = (System.nanoTime() - startNs) / 1_000_000;

        LOGGER.log(Level.INFO, "[EvaluationPatterns] jobId=" + jobId
                + " filename=" + filename + " computeMs=" + elapsedMs
                + " resultRows=" + results.size());

        return csv;
    }

    private void persistState(UUID jobId, String phase, Map<String, List<String>> rhsResults,
            String currentRhs, List<String> questionLhs, String questionRhs, String questionText) {
        try {
            ObjectNode root = om.createObjectNode();
            root.put("phase", phase);

            ArrayNode completedArr = root.putArray("completedRhs");
            synchronized (rhsResults) {
                for (Map.Entry<String, List<String>> e : rhsResults.entrySet()) {
                    if (e.getValue() != null) { // only completed entries
                        ObjectNode row = completedArr.addObject();
                        row.put("rhs", e.getKey());
                        ArrayNode antichain = row.putArray("antichain");
                        e.getValue().forEach(antichain::add);
                    }
                }
            }

            if (currentRhs != null) root.put("currentRhs", currentRhs);
            else root.putNull("currentRhs");

            if (questionLhs != null && questionRhs != null && questionText != null) {
                ObjectNode q = root.putObject("question");
                ArrayNode lhsArr = q.putArray("lhs");
                questionLhs.forEach(lhsArr::add);
                q.put("rhs", questionRhs);
                q.put("text", questionText);
            } else {
                root.putNull("question");
            }

            service.updateResultData(jobId, om.writeValueAsString(root));
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to persist processing state for job " + jobId, e);
        }
    }
}
