package cz.cuni.matfyz.algorithms.depminer.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import cz.cuni.matfyz.algorithms.depminer.ArmstrongRelationBuilder;
import cz.cuni.matfyz.algorithms.depminer.jobqueue.JobQueue;
import cz.cuni.matfyz.algorithms.depminer.jobqueue.JobQueueService;
import cz.cuni.matfyz.algorithms.depminer.model.ArmstrongStorageDto;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

@RestController
@RequestMapping("/negative-examples")
public class NegativeExamplesController {

    private static final Logger LOGGER = Logger.getLogger(NegativeExamplesController.class.getName());

    private final JobQueueService service;
    private final ObjectMapper objectMapper;

    public NegativeExamplesController(JobQueueService service, ObjectMapper objectMapper) {
        this.service = service;
        this.objectMapper = objectMapper;
    }

    // ── Request / Response DTOs ────────────────────────────────────────────────

    public record TargetFd(List<String> lhs, String rhs) {}

    public record NegativeExampleRequest(String filename, List<TargetFd> targets) {}

    public record NegativeExampleRow(List<String> values, String type) {}

    public record NegativeExampleEntry(List<String> lhs, String rhs, List<NegativeExampleRow> rows) {}

    public record NegativeExamplesResponse(List<String> columnNames, List<NegativeExampleEntry> negativeExamples) {}

    // ── Endpoint ───────────────────────────────────────────────────────────────

    @PostMapping
    public ResponseEntity<?> buildNegativeExamples(@RequestBody NegativeExampleRequest req) {
        LOGGER.log(Level.INFO, "[NegEx] Request: filename='" + req.filename()
                + "' targets=" + (req.targets() == null ? "null" : req.targets().size()));
        try {
            JobQueue fdJob = service.findLatestFinishedByFilename(req.filename())
                    .orElseThrow(() -> new IllegalArgumentException(
                            "No finished FD-Discovery job found for payload='" + req.filename()
                            + "'. Make sure FD discovery was re-run after the latest code update."));

            LOGGER.log(Level.INFO, "[NegEx] Job found: id=" + fdJob.getJobId()
                    + " armstrongData=" + (fdJob.getArmstrongData() == null
                            ? "NULL (re-run FD discovery to populate)" : "present (" + fdJob.getArmstrongData().length() + " chars)"));

            if (fdJob.getArmstrongData() == null) {
                return ResponseEntity.badRequest()
                        .body("Armstrong relation not stored for this job — re-run FD discovery to populate the ArmstrongData column.");
            }

            ArmstrongStorageDto stored = objectMapper.readValue(
                    fdJob.getArmstrongData(), ArmstrongStorageDto.class);

            List<String> columnNames = stored.getColumnNames();
            List<int[]> abstractAR = stored.getAbstractAR();

            List<NegativeExampleEntry> entries = new ArrayList<>();
            ArmstrongRelationBuilder arBuilder = new ArmstrongRelationBuilder();

            LOGGER.log(Level.INFO, "[NegEx] filename=" + req.filename()
                    + " columns=" + columnNames
                    + " storedAR_rows=" + abstractAR.size());

            for (TargetFd target : req.targets()) {
                int rhsIdx = columnNames.indexOf(target.rhs());
                if (rhsIdx < 0) {
                    LOGGER.log(Level.WARNING, "[NegEx] RHS column not found: " + target.rhs()
                            + " available=" + columnNames);
                    continue;
                }

                // Per FD_NEG_EX.pdf Algorithm 3:
                // 1. Shift all existing non-zero RHS abstract values up by 1.
                //    This makes room so that the new row's RHS=1 is unambiguously the first
                //    distinct non-base value when mapIndicesToValues scans top-to-bottom.
                // 2. Insert the negative example row: 0 on every LHS column (same base values),
                //    1 on the RHS column (maps to the first distinct real-world value ≠ base).
                // 3. Map the complete extended abstract AR to real-world values in one pass.
                List<int[]> extendedAR = new ArrayList<>();
                for (int[] row : abstractAR) {
                    int[] copy = row.clone();
                    if (copy[rhsIdx] != 0) copy[rhsIdx]++;
                    extendedAR.add(copy);
                }
                int[] negAbstractRow = new int[columnNames.size()];
                negAbstractRow[rhsIdx] = 1;
                extendedAR.add(1, negAbstractRow);

                List<List<String>> realWorldRows = new ArrayList<>(
                        arBuilder.realworldAR(extendedAR, null, req.filename() + ".csv"));

                LOGGER.log(Level.INFO, "[NegEx] target=" + target.lhs() + "->" + target.rhs()
                        + " rhsIdx=" + rhsIdx + " totalRows=" + realWorldRows.size());
                for (int i = 0; i < realWorldRows.size(); i++) {
                    String rowType = i == 0 ? "base" : i == 1 ? "NEGATIVE" : "existing";
                    LOGGER.log(Level.INFO, "  [" + rowType + "] " + realWorldRows.get(i));
                }

                // Step 4: annotate row types for the UI
                List<NegativeExampleRow> annotatedRows = new ArrayList<>();
                for (int i = 0; i < realWorldRows.size(); i++) {
                    String type = switch (i) {
                        case 0 -> "base";
                        case 1 -> "negative";
                        default -> "existing";
                    };
                    annotatedRows.add(new NegativeExampleRow(realWorldRows.get(i), type));
                }

                entries.add(new NegativeExampleEntry(target.lhs(), target.rhs(), annotatedRows));
            }

            return ResponseEntity.ok(new NegativeExamplesResponse(columnNames, entries));

        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(ex.getMessage());
        } catch (Exception ex) {
            LOGGER.log(Level.SEVERE, "Failed to build negative examples", ex);
            return ResponseEntity.internalServerError().body("Internal error: " + ex.getMessage());
        }
    }
}
