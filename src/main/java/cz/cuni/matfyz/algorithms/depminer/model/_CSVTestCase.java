/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package cz.cuni.matfyz.algorithms.depminer.model;

import com.google.common.collect.ImmutableList;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.LinkedList;
import java.util.List;

/**
 *
 * @author pavel.koupil
 */
public class _CSVTestCase {

    private static String filePath = "datasets" + File.separator;
    private static String defaultFileName = "dbtesmaData.c100000.r10";
    private static boolean defaultHasHeader = false;
    private static BufferedWriter bw;
    private BufferedReader br;
    private boolean hasHeader;
    private String fileName;
    private String nextLine;
    private int numberOfColumns;
    private ImmutableList<String> names;
    private String delimiter;

    public _CSVTestCase() throws IOException {

        this(_CSVTestCase.defaultFileName, _CSVTestCase.defaultHasHeader);
    }

    public _CSVTestCase(String fileName, boolean hasHeader) throws IOException {

        this.fileName = fileName;
        // Always treat first line as headers for this use case
        this.hasHeader = true;

        this.br = new BufferedReader(new FileReader(new File(_CSVTestCase.filePath + fileName)));
        this.nextLine = this.br.readLine();

        if (this.nextLine.split(",").length > this.nextLine.split(";").length) {
            this.delimiter = ",";
        } else {
            this.delimiter = ";";
        }

        this.calcNumbers();
        this.getNames();

    }

    public static List<String> getAllFileNames() {

        File[] fa = new File(_CSVTestCase.filePath).listFiles();

        List<String> result = new LinkedList<>();
        for (File f : fa) {

            if (f.getName().contains(".csv")) {
                result.add(f.getName());
            }

        }

        return result;

    }

    public static void writeToResultFile(String s) throws IOException {

        bw.write(s);
        bw.newLine();
        bw.flush();
    }

    public static void init() throws IOException {

        _CSVTestCase.bw = new BufferedWriter(new FileWriter("Result" + System.currentTimeMillis() + ".csv"));
        bw.write("file;time;mem");
        bw.newLine();
        bw.flush();
    }

    public void close() throws IOException {

        _CSVTestCase.bw.close();
    }

    private void getNames() throws IOException {

        ImmutableList.Builder<String> builder = new ImmutableList.Builder<>();

        if (this.hasHeader) {

            for (String s : this.nextLine.split(this.delimiter)) {
                builder.add(s);
            }
            this.nextLine = this.br.readLine();

        } else {

            for (int i = 0; i < this.numberOfColumns; i++) {

                builder.add(this.fileName + ":" + i);
            }
        }
        this.names = builder.build();

    }

    private void calcNumbers() {

        this.numberOfColumns = this.nextLine.split(this.delimiter).length;
    }

    public ImmutableList<String> columnNames() {

        return this.names;
    }

    public boolean hasNext() throws Exception {

        return (this.nextLine != null);
    }

    public ImmutableList<String> next() throws Exception {

        if (this.hasNext()) {
            ImmutableList<String> result = this.getList(this.nextLine);
            try {
                this.nextLine = this.br.readLine();
            } catch (IOException e) {
                this.nextLine = null;
            }
            return result;
        } else {
            throw new Exception("nix mehr da");
        }
    }

    private ImmutableList<String> getList(String nextLine) {

        String[] splitted = nextLine.split(this.delimiter);

        ImmutableList.Builder<String> builder = new ImmutableList.Builder<>();

        for (String aSplitted : splitted) {

            String t = aSplitted;
            if (t.equals("")) {
                t = null;
            } else {
                t = t.replaceAll("\"", "");
            }

            builder.add(t);
        }

        return builder.build();
    }

    public int numberOfColumns() {

        return this.numberOfColumns;
    }

    public String relationName() {

        return this.fileName;
    }

    public _CSVTestCase generateNewCopy() throws Exception {

        return this;
    }

    public void receiveResult(_FunctionalDependency fd) {

        // System.out.println(fd.getDeterminant() + "-->" + fd.getDependant());
    }

    public Boolean acceptedResult(_FunctionalDependency result) {
        throw new UnsupportedOperationException("Not supported yet."); // Generated from nbfs://nbhost/SystemFileSystem/Templates/Classes/Code/GeneratedMethodBody
    }

}
