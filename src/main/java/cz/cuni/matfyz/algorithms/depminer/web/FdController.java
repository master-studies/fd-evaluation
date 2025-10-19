package cz.cuni.matfyz.algorithms.depminer.web;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.springframework.beans.factory.annotation.Autowired;
import cz.cuni.matfyz.algorithms.depminer.DepMiner;
import cz.cuni.matfyz.algorithms.depminer.MainApp;
import cz.cuni.matfyz.algorithms.depminer.model._CSVTestCase;

@RestController
public class FdController {

    @Autowired
    public FdController() {
    }

    @GetMapping("/extract/{filename}")
    public List<String> extractFds(@PathVariable("filename") String filename) {
        List<String> FdsResult = null;
        try {
            boolean hasHeader = false;
            String fullName = filename + ".csv";
            _CSVTestCase input = new _CSVTestCase(fullName, hasHeader);

            DepMiner main = new DepMiner(/*numberOfThreads,*/input, fullName);
            FdsResult = main.execute();
        } catch (Exception ex) {
            Logger.getLogger(MainApp.class.getName()).log(Level.SEVERE, null, ex);
        }

        return FdsResult;
    }
}