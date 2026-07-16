package evaluationpatterns.jobqueue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.util.ArrayList;
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

@RestController
@RequestMapping("/jobs")
public class JobQueueController {

    private final JobQueueService service;
    private final SuspiciousAnswerService answerService;
    private final ObjectMapper om = new ObjectMapper();

    public JobQueueController(JobQueueService service, SuspiciousAnswerService answerService) {
        this.service = service;
        this.answerService = answerService;
    }

    @PostMapping
    public ResponseEntity<JobResponse> createJob(@RequestParam("filename") String filename) {
        JobQueue job = service.enqueue(filename);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(toResponse(job));
    }

    @GetMapping("/{id}")
    public ResponseEntity<JobResponse> getJob(@PathVariable("id") UUID id) {
        return service.find(id)
                .map(this::toResponse)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /**
     * Submit a user answer for a SUSPICIOUS FD question.
     * genuine=true → the FD is genuine; genuine=false → treat as fake.
     * Returns 200 if accepted, 404 if no pending question exists for this job.
     */
    @PostMapping("/{id}/answer")
    public ResponseEntity<Void> answerQuestion(
            @PathVariable("id") UUID id,
            @RequestParam("genuine") boolean genuine) {
        boolean accepted = answerService.submitAnswer(id, genuine);
        return accepted ? ResponseEntity.ok().build() : ResponseEntity.notFound().build();
    }

    // ── Response types ────────────────────────────────────────────────────────

    record SuspiciousQuestion(List<String> lhs, String rhs, String text) {}

    record CompletedRhs(String rhs, List<String> antichain) {}

    record ProcessingState(
            String phase,
            List<CompletedRhs> completedRhs,
            String currentRhs,
            SuspiciousQuestion question) {}

    record JobResponse(
            UUID jobId,
            String status,
            String serviceType,
            String errorMessage,
            LocalDateTime createdAt,
            LocalDateTime startedAt,
            LocalDateTime completedAt,
            String resultCsv,
            ProcessingState processingState) {}

    // ── Mapping ───────────────────────────────────────────────────────────────

    private JobResponse toResponse(JobQueue job) {
        String resultCsv = null;
        ProcessingState processingState = null;

        String data = job.getResultData();
        if ("FINISHED".equals(job.getJobStatus())) {
            resultCsv = data;
        } else if (data != null && data.startsWith("{")) {
            processingState = parseProcessingState(data);
        }

        return new JobResponse(
                job.getJobId(),
                job.getJobStatus(),
                job.getServiceType(),
                job.getErrorMessage(),
                job.getCreatedAt(),
                job.getStartedAt(),
                job.getCompletedAt(),
                resultCsv,
                processingState);
    }

    private ProcessingState parseProcessingState(String json) {
        try {
            JsonNode root = om.readTree(json);
            String phase = root.path("phase").asText("processing");

            List<CompletedRhs> completedRhs = new ArrayList<>();
            JsonNode arr = root.path("completedRhs");
            if (arr.isArray()) {
                for (JsonNode entry : arr) {
                    String rhs = entry.path("rhs").asText();
                    List<String> antichain = new ArrayList<>();
                    entry.path("antichain").forEach(n -> antichain.add(n.asText()));
                    completedRhs.add(new CompletedRhs(rhs, antichain));
                }
            }

            String currentRhs = root.path("currentRhs").isNull()
                    ? null : root.path("currentRhs").asText(null);

            SuspiciousQuestion question = null;
            JsonNode q = root.path("question");
            if (!q.isNull() && !q.isMissingNode()) {
                List<String> lhs = new ArrayList<>();
                q.path("lhs").forEach(n -> lhs.add(n.asText()));
                question = new SuspiciousQuestion(
                        lhs,
                        q.path("rhs").asText(),
                        q.path("text").asText());
            }

            return new ProcessingState(phase, completedRhs, currentRhs, question);
        } catch (Exception e) {
            return null;
        }
    }
}
