package evaluationpatterns.jobqueue;

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
        job.setServiceType("EVALUATION_PATTERNS");
        return repository.save(job);
    }

    @Transactional(readOnly = true)
    public Optional<JobQueue> find(UUID jobId) {
        return repository.findById(jobId);
    }

    @Transactional
    public JobQueue markRunning(JobQueue job) {
        JobQueue managed = repository.findById(job.getJobId())
                .orElseThrow(() -> new RuntimeException("Job not found: " + job.getJobId()));
        managed.setJobStatus("RUNNING");
        managed.setStartedAt(LocalDateTime.now());
        return repository.save(managed);
    }

    @Transactional
    public JobQueue markFinished(JobQueue job) {
        JobQueue managed = repository.findById(job.getJobId())
                .orElseThrow(() -> new RuntimeException("Job not found: " + job.getJobId()));
        managed.setJobStatus("FINISHED");
        managed.setCompletedAt(LocalDateTime.now());
        if (job.getResultData() != null) {
            managed.setResultData(job.getResultData());
        }
        return repository.save(managed);
    }

    @Transactional
    public JobQueue markFailed(JobQueue job, String errorMessage) {
        JobQueue managed = repository.findById(job.getJobId())
                .orElseThrow(() -> new RuntimeException("Job not found: " + job.getJobId()));
        managed.setJobStatus("FAILED");
        managed.setCompletedAt(LocalDateTime.now());
        managed.setErrorMessage(errorMessage);
        return repository.save(managed);
    }

    @Transactional
    public Optional<JobQueue> pollNextNew() {
        return repository.findNextNewForUpdate();
    }

    @Transactional(readOnly = true)
    public Optional<JobQueue> findLatestFinishedFDDiscovery(String filename) {
        return repository.findLatestFinishedFDDiscovery(filename);
    }

    /** Persist an intermediate state JSON to ResultData while the job is RUNNING. */
    @Transactional
    public void updateResultData(UUID jobId, String resultData) {
        repository.findById(jobId).ifPresent(job -> {
            job.setResultData(resultData);
            repository.save(job);
        });
    }
}
