package entropy.jobqueue;

import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class JobWorker {

    private static final Logger LOGGER = Logger.getLogger(JobWorker.class.getName());

    private final JobQueueService service;
    private final JobProcessor processor;

    public JobWorker(JobQueueService service, JobProcessor processor) {
        this.service = service;
        this.processor = processor;
    }

    @Scheduled(fixedDelayString = "${jobs.worker.delay-ms:1000}")
    public void pollAndWork() {
        try {
            // Poll for all available jobs and submit them asynchronously
            int jobsSubmitted = 0;
            Optional<JobQueue> job;
            while ((job = service.pollNextNew()).isPresent() && jobsSubmitted < 10) {
                JobQueue runningJob = service.markRunning(job.get());
                processor.processJobAsync(runningJob);
                jobsSubmitted++;
            }
            if (jobsSubmitted > 0) {
                LOGGER.info("Submitted " + jobsSubmitted + " job(s) for async processing");
            }
        } catch (Exception ex) {
            LOGGER.log(Level.SEVERE, "Job worker iteration failed", ex);
        }
    }
}
