package genuineness.web;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import genuineness._GenuinenessCalculator;
import genuineness._FunctionalDependencyGroup;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;

@RestController
@RequestMapping("/genuineness")
public class ApiController {

    @PostMapping(path = "/calc/{filename}", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> coverageCalc(@RequestBody List<FunctionalDependencyGroupDto> body, 
        @PathVariable("filename") String filename) {
        try {
			// Map DTOs to internal _FunctionalDependencyGroup instances
			List<_FunctionalDependencyGroup> fds = new ArrayList<>();
			for (FunctionalDependencyGroupDto dto : body) {
				IntList intList = new IntArrayList();
				System.out.println("Processing FD: attributeID=" + dto.attributeID + ", values=" + 
					(dto.values != null ? dto.values.toString() : "null"));
				if (dto.values != null) {
					for (Integer v : dto.values) {
						if (v != null) intList.add(v);
					}
				}
				fds.add(new _FunctionalDependencyGroup(dto.attributeID, intList));
			}

			_GenuinenessCalculator calc = new _GenuinenessCalculator();
            String fullName = filename + ".csv";
			 List<Double> results = calc.computeMetrics(fds, fullName);

			// Convert results to response DTOs
			// List<GenuinenessResultDto> resp = results.entrySet().stream()
			// 		.map(e -> {
			// 			_FunctionalDependencyGroup fd = e.getKey();
			// 			IntList vals = fd.getValues();
			// 			List<Integer> values = new ArrayList<>();
			// 			for (int i = 0; i < vals.size(); i++) values.add(vals.getInt(i));
			// 			return new GenuinenessResultDto(values, fd.getAttributeID(), e.getValue());
			// 		})
			// 		.collect(Collectors.toList());

			// System.out.println("Final results:");
			// for (GenuinenessResultDto result : resp) {
			// 	System.out.println("Result: attributeID=" + result.attributeID + ", values=" + 
			// 		result.values + ", genuineness=" + result.score);
			// }

			return ResponseEntity.ok(results);

		} catch (IOException ex) {
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ex.getMessage());
		} catch (Exception ex) {
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ex.toString());
		}
    }
}
