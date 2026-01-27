package coverage.jobqueue;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;


@RestController
@RequestMapping("/jobs")
public class JobQueueController {

    private final JobQueueService service;
    private final ObjectMapper objectMapper;

    public JobQueueController(JobQueueService service, ObjectMapper objectMapper) {
        this.service = service;
        this.objectMapper = objectMapper;
    }

    @PostMapping
    public ResponseEntity<JobResponse> createJob(@RequestParam("filename") String filename) {
        JobQueue job = service.enqueue(filename);
        JobResponse body = JobResponse.from(job, null);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(body);
    }

    @GetMapping("/{id}")
    public ResponseEntity<JobResponse> getJob(@PathVariable("id") UUID id) {
        return service.find(id)
                .map(job -> {
                    List<Double> result = null;
                    if ("FINISHED".equals(job.getJobStatus()) && job.getResultData() != null) {
                        try {
                            result = objectMapper.readValue(
                                job.getResultData(), 
                                new TypeReference<List<Double>>() {}
                            );
                        } catch (Exception e) {
                            // Log and continue without result
                        }
                    }
                    return JobResponse.from(job, result);
                })
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    record JobResponse(UUID jobId, String status, String serviceType, String errorMessage,
                       java.time.LocalDateTime createdAt,
                       java.time.LocalDateTime startedAt,
                       java.time.LocalDateTime completedAt,
                       List<Double> result) {
        static JobResponse from(JobQueue job, List<Double> result) {
            return new JobResponse(
                    job.getJobId(),
                    job.getJobStatus(),
                    job.getServiceType(),
                    job.getErrorMessage(),
                    job.getCreatedAt(),
                    job.getStartedAt(),
                    job.getCompletedAt(),
                    result);
        }
    }
}
