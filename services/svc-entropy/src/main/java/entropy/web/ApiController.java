package entropy.web;

import entropy.EntropyService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@RestController
@RequestMapping("/entropy")
public class ApiController {
    private static final Logger logger = LoggerFactory.getLogger(ApiController.class);

    @PostMapping(path = "/calc/{filename}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> run(
            @RequestBody List<FunctionalDependencyGroupDto> body,
            @PathVariable("filename") String filename,
            @RequestParam(required = false) boolean identifyOnes,
            @RequestParam(required = false) boolean considerSubtables,
            @RequestParam(required = false) boolean randomizedApproach,
            @RequestParam(required = false) String runs,
            @RequestParam(required = false) boolean closure,
            @RequestParam(required = false) String saveResult
    ) {

        if (body != null) {
            for (int i = 0; i < body.size(); i++) {
                FunctionalDependencyGroupDto dto = body.get(i);
                logger.info("FD Body[{}]: values={}, attributeID={}", i, dto.values, dto.attributeID);
            }
        }

        List<String> params = new ArrayList<>();
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

		// test parameters
		// params.add("-i");
		// params.add("-s");
		// params.add("-r");
		// params.add("100000");

        String[] options = params.toArray(new String[0]);
        logger.debug("Parsed options: {}", Arrays.toString(options));

        try {
			String fullName = filename + ".csv";
            String output = EntropyService.run(fullName, body, options);
            logger.info("Service execution completed successfully for {}", filename);
            return ResponseEntity.ok(output);
        } catch (Exception e) {
            logger.error("Service execution failed for {}: {}", filename, e.getMessage(), e);
            return ResponseEntity.internalServerError().body("Error: " + e.getMessage());
        }
    }
}
