package succinctness.jobqueue;

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
        job.setServiceType("SUCCINCTNESS");
        return repository.save(job);
    }

    @Transactional(readOnly = true)
    public Optional<JobQueue> find(UUID jobId) {
        return repository.findById(jobId);
    }

    @Transactional
    public JobQueue markRunning(JobQueue job) {
        job.setJobStatus("RUNNING");
        job.setStartedAt(LocalDateTime.now());
        return repository.save(job);
    }

    @Transactional
    public JobQueue markFinished(JobQueue job) {
        job.setJobStatus("FINISHED");
        job.setCompletedAt(LocalDateTime.now());
        return repository.save(job);
    }

    @Transactional
    public JobQueue markFailed(JobQueue job, String errorMessage) {
        job.setJobStatus("FAILED");
        job.setCompletedAt(LocalDateTime.now());
        job.setErrorMessage(errorMessage);
        return repository.save(job);
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
