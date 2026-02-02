package succinctness.web;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import succinctness._FunctionalDependencyGroup;
import succinctness._SuccinctnessCalculator;

@RestController
@RequestMapping(path = "/succinctness")
public class ApiController {

	@PostMapping(path = "/calc", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<?> succinctnessCalc(@RequestBody List<FunctionalDependencyGroupDto> body) {
		try {
			// Map DTOs to internal _FunctionalDependencyGroup instances
			List<_FunctionalDependencyGroup> fds = new ArrayList<>();
			for (FunctionalDependencyGroupDto dto : body) {
				IntList intList = new IntArrayList();
				if (dto.values != null) {
					for (Integer v : dto.values) {
						if (v != null) intList.add(v);
					}
				}
				fds.add(new _FunctionalDependencyGroup(dto.attributeID, intList));
			}

			_SuccinctnessCalculator calc = new _SuccinctnessCalculator();
			List<Double> results = calc.computeMetrics(fds);

			return ResponseEntity.ok(results);

		} catch (IOException ex) {
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ex.getMessage());
		} catch (Exception ex) {
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ex.toString());
		}
	}

}
