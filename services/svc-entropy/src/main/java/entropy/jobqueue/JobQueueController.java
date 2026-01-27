package entropy.jobqueue;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.nio.file.Files;
import java.nio.file.Paths;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;


@RestController
@RequestMapping("/jobs")
public class JobQueueController {

    private final JobQueueService service;

    public JobQueueController(JobQueueService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<JobResponse> createJob(
            @RequestParam("filename") String filename,
            @RequestParam(required = false) boolean identifyOnes,
            @RequestParam(required = false) boolean considerSubtables,
            @RequestParam(required = false) boolean randomizedApproach,
            @RequestParam(required = false) String runs,
            @RequestParam(required = false) boolean closure,
            @RequestParam(required = false) String saveResult) {

        List<String> params = new ArrayList<>();
        params.add(filename);
        // by default, add header option
        params.add("--header");
        if (identifyOnes) params.add("-i");
        if (considerSubtables) params.add("-s");
        if (randomizedApproach) {
            params.add("-r");
            params.add(runs);
        }
        if (closure) params.add("--closure");
        if (saveResult != null) {
            params.add("--name");
            params.add("outout/{filename}.csv".replace("{filename}", filename));
        }

        JobQueue job = service.enqueue(params);
        JobResponse body = JobResponse.from(job, null);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(body);
    }

    @GetMapping("/{id}")
    public ResponseEntity<JobResponse> getJob(@PathVariable("id") UUID id) {
        return service.find(id)
                .map(job -> {
                    String result = null;
                    if ("FINISHED".equals(job.getJobStatus()) && job.getResultData() != null) {
                        try {
                            result = job.getResultData();
                        } catch (Exception e) {
                            // Log and continue without result
                        }
                    }
                    return JobResponse.from(job, result);
                })
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/{id}/download")
    public ResponseEntity<byte[]> downloadJobResultCsv(@PathVariable("id") UUID id) {
        return service.find(id)
                .filter(job -> "FINISHED".equals(job.getJobStatus()) && job.getResultData() != null)
                .map(job -> {
                    try {
                        // The filename is stored in resultData
                        String filename = job.getResultData().trim();
                        
                        // Read the file from disk
                        byte[] fileBytes = Files.readAllBytes(Paths.get(filename));
                        
                        // Extract filename for the download header
                        String downloadFilename = Paths.get(filename).getFileName().toString();
                        
                        HttpHeaders headers = new HttpHeaders();
                        headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
                        headers.setContentDispositionFormData("attachment", downloadFilename);
                        headers.setContentLength(fileBytes.length);

                        return ResponseEntity.ok().headers(headers).body(fileBytes);
                    } catch (Exception e) {
                        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).<byte[]>build();
                    }
                })
                .orElseGet(() -> ResponseEntity.notFound().<byte[]>build());
    }

    record JobResponse(UUID jobId, String status, String serviceType, String errorMessage,
                       java.time.LocalDateTime createdAt,
                       java.time.LocalDateTime startedAt,
                       java.time.LocalDateTime completedAt,
                       String result) {
        static JobResponse from(JobQueue job, String result) {
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
