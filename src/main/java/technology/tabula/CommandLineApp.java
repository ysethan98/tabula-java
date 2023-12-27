package technology.tabula;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.Arrays;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.ParseException;
import org.apache.commons.cli.DefaultParser;
import org.apache.pdfbox.pdmodel.PDDocument;

import technology.tabula.writers.CSVWriter;
import technology.tabula.writers.JSONWriter;
import technology.tabula.writers.TSVWriter;
import technology.tabula.writers.Writer;


public class CommandLineApp {

    private static String VERSION = "1.0.6-SNAPSHOT";
    private static String VERSION_STRING = String.format("tabula %s (c) 2012-2020 Manuel Aristarán", VERSION);
    private static String BANNER = "\nTabula helps you extract tables from PDFs\n\n";

    private static final int RELATIVE_AREA_CALCULATION_MODE = 0;
    private static final int ABSOLUTE_AREA_CALCULATION_MODE = 1;


    private Appendable defaultOutput;
    private List<Pair<Integer, Rectangle>> pageAreas;
    private List<Integer> pages;
    private OutputFormat outputFormat;
    private String password;
    private TableExtractor tableExtractor;

    public CommandLineApp(Appendable defaultOutput, CommandLine line) throws ParseException {
        this.defaultOutput = defaultOutput;
        this.pageAreas = whichAreas(line);
        this.pages = whichPages(line);
        this.tableExtractor = createExtractor(line);
        new DebugOutput(true);
        new CommandLineOptions();

        if (line.hasOption('s')) {
            this.password = line.getOptionValue('s');
        }
    }

    public void main(String[] args) {
        CommandLineParser parser = new DefaultParser();
        try {
            // parse the command line arguments
            CommandLine line = parser.parse(CommandLineOptions.buildOptions(), args);

            if (line.hasOption('h')) {
                printHelp();
                System.exit(0);
            }

            if (line.hasOption('v')) {
                System.out.println(VERSION_STRING);
                System.exit(0);
            }

            new CommandLineApp(System.out, line).extractTables(line);
        } catch (ParseException exp) {
            System.err.println("Error: " + exp.getMessage());
            System.exit(1);
        }
        System.exit(0);
    }

    public void extractTables(CommandLine line) throws ParseException {
        if (line.hasOption('b')) {
            handleBatchProcessing(line);
        } else {
            handleSingleFileProcessing(line);
        }
    }
    
    private void handleBatchProcessing(CommandLine line) throws ParseException {
        checkBatchModeArguments(line);
        File pdfDirectory = getDirectoryFromCommandLine(line);
        extractDirectoryTables(line, pdfDirectory);
    }

    private void checkBatchModeArguments(CommandLine line) throws ParseException{
        if (line.getArgs().length != 0) {
            throw new ParseException("Filename specified with batch\nTry --help for help");
        }
    }
    
    private void handleSingleFileProcessing(CommandLine line) throws ParseException {
        checkSingleFileModeArguments(line);
        File pdfFile = getFileFromCommandLine(line);
        extractFileTables(line, pdfFile);
    }

    private void checkSingleFileModeArguments(CommandLine line) throws ParseException{
        if (line.getArgs().length != 1) {
            throw new ParseException("Need exactly one filename\nTry --help for help");
        }
    }

    public File getDirectoryFromCommandLine(CommandLine line) throws ParseException{
        File pdfDirectory = new File(line.getOptionValue('b'));
        if (!pdfDirectory.isDirectory()) {
            throw new ParseException("Directory does not exist or is not a directory");
        }
        return pdfDirectory;
    }

    private File getFileFromCommandLine(CommandLine line) throws ParseException{
        File pdfFile = new File(line.getArgs()[0]);
        if (!pdfFile.exists()) {
            throw new ParseException("File does not exist");
        }
        return pdfFile;
    }

    public void extractDirectoryTables(CommandLine line, File pdfDirectory) {
        File[] pdfs = pdfDirectory.listFiles((dir, name) -> name.endsWith(".pdf"));
    
        for (File pdfFile : pdfs) {
            File outputFile = new File(getOutputFilename(pdfFile));
            try {
                extractFileInto(pdfFile, outputFile);
            } catch (Exception e) {
                handleException("Error processing file: " + pdfFile.getPath(), e);
            }
        }
    }

    public void extractFileTables(CommandLine line, File pdfFile) throws ParseException {
        if (!line.hasOption('o')) {
            extractFile(pdfFile, this.defaultOutput);
            return;
        }

        File outputFile = new File(line.getOptionValue('o'));
        extractFileInto(pdfFile, outputFile);
    }

    public void extractFileInto(File pdfFile, File outputFile) {
        try (BufferedWriter bufferedWriter = new BufferedWriter(new FileWriter(outputFile))) {
            outputFile.createNewFile();
            extractFile(pdfFile, bufferedWriter);
        } catch (IOException e) {
            handleException("Cannot create or write to file: " + outputFile, e);
        }
    }

    private void extractFile(File pdfFile, Appendable outFile) {
        try (PDDocument pdfDocument = loadPdfDocument(pdfFile)){
            processPdfDocument(pdfDocument, outFile);
        } catch (IOException e) {
            handleException("Error processing file: " + pdfFile.getPath(), e);
        } 
    }

    private static void handleException(String message, Exception e) {
        System.err.println(message);
        e.printStackTrace();
    }

    private void processPdfDocument(PDDocument pdfDocument, Appendable outFile) throws IOException {
        PageIterator pageIterator = getPageIterator(pdfDocument);
        List<Table> tables = processPages(pageIterator);
        writeTables(tables, outFile);
    }

    private PDDocument loadPdfDocument(File pdfFile) throws IOException {
        return this.password == null ? PDDocument.load(pdfFile) : PDDocument.load(pdfFile, this.password);
    }

    private List<Table> processPages(PageIterator pageIterator) {
        List<Table> tables = new ArrayList<>();
        while (pageIterator.hasNext()) {
            Page page = pageIterator.next();
            applyVerticalRulings(page);
            tables.addAll(extractTablesFromPage(page));
        }
        return tables;
    }

    private void applyVerticalRulings(Page page) {
        if (tableExtractor.verticalRulingPositions != null) {
            for (Float verticalRulingPosition : tableExtractor.verticalRulingPositions) {
                page.addRuling(new Ruling(0, verticalRulingPosition, 0.0f, (float) page.getHeight()));
            }
        }
    }

    private List<Table> extractTablesFromPage(Page page) {
        if (pageAreas != null) {
            return pageAreas.stream().map(areaPair -> adjustAreaBasedOnMode(areaPair, page)).flatMap(area -> tableExtractor.extractTables(page.getArea(area)).stream()).collect(Collectors.toList());
        } else {
            return tableExtractor.extractTables(page);
        }    
    }

    private Rectangle adjustAreaBasedOnMode(Pair<Integer, Rectangle> areaPair, Page page) {
        Rectangle area = areaPair.getRight();
        if (areaPair.getLeft() == RELATIVE_AREA_CALCULATION_MODE) {
            return new Rectangle(
                (float) (area.getTop() / 100 * page.getHeight()),
                (float) (area.getLeft() / 100 * page.getWidth()),
                (float) (area.getWidth() / 100 * page.getWidth()),
                (float) (area.getHeight() / 100 * page.getHeight())
            );
        }
        return area;
    }

    private PageIterator getPageIterator(PDDocument pdfDocument) throws IOException {
        ObjectExtractor extractor = new ObjectExtractor(pdfDocument);
        return (pages == null) ?
                extractor.extract() :
                extractor.extract(pages);
    }

    private List<Pair<Integer, Rectangle>> whichAreas(CommandLine line) throws ParseException {
        if (!line.hasOption('a')) {
            return null;
        }

        String[] optionValues = line.getOptionValues('a');

        List<Pair<Integer, Rectangle>> areaList = new ArrayList<Pair<Integer, Rectangle>>();
        for (String optionValue : optionValues) {
            int areaCalculationMode = ABSOLUTE_AREA_CALCULATION_MODE;
            int startIndex = 0;
            if (optionValue.startsWith("%")) {
                startIndex = 1;
                areaCalculationMode = RELATIVE_AREA_CALCULATION_MODE;
            }
            List<Float> f = parseFloatList(optionValue.substring(startIndex));
            if (f.size() != 4) {
                throw new ParseException("area parameters must be top,left,bottom,right optionally preceded by %");
            }
            areaList.add(new Pair<Integer, Rectangle>(areaCalculationMode, new Rectangle(f.get(0), f.get(1), f.get(3) - f.get(1), f.get(2) - f.get(0))));
        }
        return areaList;
    }

    private List<Integer> whichPages(CommandLine line) throws ParseException {
        String pagesOption = line.hasOption('p') ? line.getOptionValue('p') : "1";
        return Utils.parsePagesOption(pagesOption);
    }

    private static ExtractionMethod whichExtractionMethod(CommandLine line) {
        // -r/--spreadsheet [deprecated; use -l] or -l/--lattice
        if (line.hasOption('r') || line.hasOption('l')) {
            return ExtractionMethod.SPREADSHEET;
        }

        // -n/--no-spreadsheet [deprecated; use -t] or  -c/--columns or -g/--guess or -t/--stream
        if (line.hasOption('n') || line.hasOption('c') || line.hasOption('t')) {
            return ExtractionMethod.BASIC;
        }
        return ExtractionMethod.DECIDE;
    }

    private TableExtractor createExtractor(CommandLine line) throws ParseException {
        TableExtractor extractor = new TableExtractor();
        extractor.setGuess(line.hasOption('g'));
        extractor.setMethod(CommandLineApp.whichExtractionMethod(line));
        extractor.setUseLineReturns(line.hasOption('u'));

        if (line.hasOption('c')) {
            String optionString = line.getOptionValue('c');
            if (optionString.startsWith("%")) {
                extractor.setVerticalRulingPositionsRelative(true);
                optionString = optionString.substring(1);
            }
            extractor.setVerticalRulingPositions(parseFloatList(optionString));
        }

        return extractor;
    }

    // utilities, etc.

    public static List<Float> parseFloatList(String option) throws ParseException {
        try {
            return Arrays.stream(option.split(","))
                         .map(Float::parseFloat)
                         .collect(Collectors.toList());
        } catch (NumberFormatException e) {
            throw new ParseException("Invalid number format in: " + option);
        }
    }
    

    private void printHelp() {
        HelpFormatter formatter = new HelpFormatter();
        formatter.printHelp("tabula", BANNER, CommandLineOptions.buildOptions(), "", true);
    }

    private void writeTables(List<Table> tables, Appendable out) throws IOException {
        Writer writer = null;
        switch (outputFormat) {
            case CSV:
                writer = new CSVWriter();
                break;
            case JSON:
                writer = new JSONWriter();
                break;
            case TSV:
                writer = new TSVWriter();
                break;
        }
        writer.write(out, tables);
    }

    private String getOutputFilename(File pdfFile) {
        String extension = ".csv";
        switch (outputFormat) {
            case CSV:
                extension = ".csv";
                break;
            case JSON:
                extension = ".json";
                break;
            case TSV:
                extension = ".tsv";
                break;
        }
        return pdfFile.getPath().replaceFirst("(\\.pdf|)$", extension);
    }

}
