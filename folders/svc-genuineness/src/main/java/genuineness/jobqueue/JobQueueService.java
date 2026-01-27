package genuineness.jobqueue;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class JobQueueService {

    private final JobQueueRepository repository;

    public JobQueueService(JobQueueRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public JobQueue enqueue(String payload) {
        JobQueue job = new JobQueue();
        job.setJobStatus("NEW");
        job.setPayload(payload);
        job.setServiceType("GENUINENESS");
        return repository.save(job);
    }

    @Transactional(readOnly = true)
    public Optional<JobQueue> find(UUID jobId) {
        return repository.findById(jobId);
    }

    @Transactional
    public JobQueue markRunning(JobQueue job) {
        // Re-fetch to ensure we have the managed entity
        JobQueue managedJob = repository.findById(job.getJobId())
                .orElseThrow(() -> new RuntimeException("Job not found: " + job.getJobId()));
        managedJob.setJobStatus("RUNNING");
        managedJob.setStartedAt(LocalDateTime.now());
        return repository.save(managedJob);
    }

    @Transactional
    public JobQueue markFinished(JobQueue job) {
        // Re-fetch to ensure we have the managed entity
        JobQueue managedJob = repository.findById(job.getJobId())
                .orElseThrow(() -> new RuntimeException("Job not found: " + job.getJobId()));
        managedJob.setJobStatus("FINISHED");
        managedJob.setCompletedAt(LocalDateTime.now());
        // Preserve result data from the passed job
        if (job.getResultData() != null) {
            managedJob.setResultData(job.getResultData());
        }
        return repository.save(managedJob);
    }

    @Transactional
    public JobQueue markFailed(JobQueue job, String errorMessage) {
        // Re-fetch to ensure we have the managed entity
        JobQueue managedJob = repository.findById(job.getJobId())
                .orElseThrow(() -> new RuntimeException("Job not found: " + job.getJobId()));
        managedJob.setJobStatus("FAILED");
        managedJob.setCompletedAt(LocalDateTime.now());
        managedJob.setErrorMessage(errorMessage);
        return repository.save(managedJob);
    }

    @Transactional
    public Optional<JobQueue> pollNextNew() {
        return repository.findNextNewForUpdate();
    }

    @Transactional(readOnly = true)
    public Optional<JobQueue> findLatestFinishedFDDiscovery(String filename) {
        return repository.findLatestFinishedFDDiscovery(filename);
    }
}
