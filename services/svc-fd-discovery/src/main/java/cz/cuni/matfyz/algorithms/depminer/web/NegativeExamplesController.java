package cz.cuni.matfyz.algorithms.depminer.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import cz.cuni.matfyz.algorithms.depminer.ArmstrongRelationBuilder;
import cz.cuni.matfyz.algorithms.depminer.jobqueue.JobQueue;
import cz.cuni.matfyz.algorithms.depminer.jobqueue.JobQueueService;
import cz.cuni.matfyz.algorithms.depminer.model.ArmstrongStorageDto;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
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

                Set<Integer> lhsIdx = new HashSet<>();
                boolean lhsOk = true;
                for (String col : target.lhs()) {
                    int idx = columnNames.indexOf(col);
                    if (idx < 0) {
                        LOGGER.log(Level.WARNING, "[NegEx] LHS column not found: " + col
                                + " available=" + columnNames);
                        lhsOk = false;
                        break;
                    }
                    lhsIdx.add(idx);
                }
                if (!lhsOk) continue;

                // Negative-example construction:
                // The new row copies the LHS values of the base (first) row of the
                // Armstrong relation and takes a fresh, previously unseen value in
                // every other column. The pair (base row, negative row) then agrees
                // on exactly the LHS, so one example challenges LHS -> Y for every
                // column Y outside the LHS and, by downward closure, all subsets
                // of the LHS as well.
                //
                // Abstractly: shift all non-zero values of every non-LHS column up
                // by 1, then insert the new row (0 on LHS columns, 1 elsewhere)
                // directly after the base row. This keeps first occurrences
                // increasing down each column, which the real-world mapping
                // (mapIndicesToValues) relies on.
                List<int[]> extendedAR = new ArrayList<>();
                for (int[] row : abstractAR) {
                    int[] copy = row.clone();
                    for (int c = 0; c < copy.length; c++) {
                        if (!lhsIdx.contains(c) && copy[c] != 0) copy[c]++;
                    }
                    extendedAR.add(copy);
                }
                int[] baseRow = abstractAR.get(0);
                int[] negAbstractRow = new int[columnNames.size()];
                for (int c = 0; c < negAbstractRow.length; c++) {
                    negAbstractRow[c] = lhsIdx.contains(c) ? baseRow[c] : 1;
                }
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
