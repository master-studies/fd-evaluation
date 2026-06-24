package evaluationpatterns.web;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.File;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@RestController
public class ApiController {

    @GetMapping("/dataset/list")
    public ResponseEntity<List<String>> listDatasets() {
        File dir = new File("datasets");
        if (!dir.exists() || !dir.isDirectory()) {
            return ResponseEntity.ok(Collections.emptyList());
        }
        String[] files = dir.list((d, name) -> name.endsWith(".csv"));
        if (files == null) {
            return ResponseEntity.ok(Collections.emptyList());
        }
        List<String> names = Arrays.stream(files)
                .map(f -> f.replaceAll("\\.csv$", ""))
                .sorted()
                .collect(Collectors.toList());
        return ResponseEntity.ok(names);
    }
}
