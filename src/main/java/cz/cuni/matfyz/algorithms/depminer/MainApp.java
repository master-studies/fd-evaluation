/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package cz.cuni.matfyz.algorithms.depminer;

import cz.cuni.matfyz.algorithms.depminer.model._CSVTestCase;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author pavel.koupil
 */
public class MainApp {

    public static String FILENAME;

    public static void main(String... args) {

//		FILENAME = "abalone.csv";
//		FILENAME = "adult.csv";		// DO NOT RUN - TOO LONG FILE
//		FILENAME = "balance-scale.csv";
        FILENAME = "breast.csv";
//		FILENAME = "breast_proj.csv";
//		FILENAME = "bridges.csv";
//		FILENAME = "armstrong.csv";
//		FILENAME = "echocardiogram.csv";
//		FILENAME = "flight_1k.csv";	// DO NOT RUN - TOO MANY FDs
//		FILENAME = "hepatitis.csv";	// DNR
//		FILENAME = "horse.csv";
//		FILENAME = "chess.csv";	// DO NOT RUN - TOO LONG FILE
//		FILENAME = "iris.csv";
//		FILENAME = "letter.csv";	// DO NOT RUN - TOO LONG FILE
//		FILENAME = "ncvoter_1001r_19c.csv";
//		FILENAME = "nursery.csv";	// DO NOT RUN - TOO LONG FILE
//		FILENAME = "plista_1k.csv";	// DO NOT RUN - TOO MANY FDs
//		FILENAME = "title10.csv";	// TODO: Tohle je dobrý running example, protože nad více daty platí méně funkčních závislostí - jasně řekneme, co je coincidental a budeme upravovat
//		FILENAME = "title5k.csv";	// TODO: Tohle je dobrý running example, protože nad více daty platí méně funkčních závislostí - jasně řekneme, co je coincidental a budeme upravovat
//		FILENAME = "title10k.csv";	// TODO: Tohle je dobrý running example, protože nad více daty platí méně funkčních závislostí - jasně řekneme, co je coincidental a budeme upravovat
//		FILENAME = "titanic.csv";
//		FILENAME = "armstrong.csv";
        boolean hasHeader = false;

        try {
//			int numberOfThreads = 1;
            _CSVTestCase input = new _CSVTestCase(FILENAME, hasHeader);

            long time = System.currentTimeMillis();
//			System.out.println("START: " + time);

            DepMiner main = new DepMiner(/*numberOfThreads,*/input);
            main.execute();
            time = System.currentTimeMillis() - time;
            System.out.println("Time: " + time);

//			if (FILENAME.equals("breast_proj.csv")) {
//				main.demo();
//			}
//			
//			if (FILENAME.equals("titanic.csv")) {
//				main.demo2();
//			}
        } catch (Exception ex) {
            Logger.getLogger(MainApp.class.getName()).log(Level.SEVERE, null, ex);
        }

    }

}
