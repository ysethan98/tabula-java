package technology.tabula;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.util.List;
import java.util.Arrays;
import java.io.StringWriter;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.Before;
import technology.tabula.CommandLineApp;

public class TestCommandLineApp {

    private CommandLineApp app;
    private StringWriter stringWriter;
    private CommandLine cmd;


    @Before
    public void setUp() throws ParseException {
        stringWriter = new StringWriter();
        Options options = CommandLineOptions.buildOptions();
        CommandLineParser parser = new DefaultParser();

        String[] args = new String[]{"-f", "CSV", "path/to/singlefile.pdf"};
        cmd = parser.parse(options, args);

        app = new CommandLineApp(stringWriter, cmd);
    }

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    private String csvFromCommandLineArgs(String[] args) throws ParseException {
        CommandLineParser parser = new DefaultParser();
        CommandLine cmd = parser.parse(CommandLineOptions.buildOptions(), args);

        StringBuilder stringBuilder = new StringBuilder();
        new CommandLineApp(stringBuilder, cmd).extractTables(cmd);

        return stringBuilder.toString();
    }

    @Test
    public void testExtractSpreadsheetWithArea() throws ParseException, IOException {

        String expectedCsv = UtilsForTesting.loadCsv("src/test/resources/technology/tabula/csv/spreadsheet_no_bounding_frame.csv");

        assertEquals(expectedCsv, this.csvFromCommandLineArgs(new String[]{
                "src/test/resources/technology/tabula/spreadsheet_no_bounding_frame.pdf",
                "-p", "1", "-a",
                "150.56,58.9,654.7,536.12", "-f",
                "CSV"
        }));
    }

    @Test
    public void testExtractBatchSpreadsheetWithArea() throws ParseException, IOException {
        FileSystem fs = FileSystems.getDefault();
        String expectedCsv = UtilsForTesting.loadCsv("src/test/resources/technology/tabula/csv/spreadsheet_no_bounding_frame.csv");
        Path tmpFolder = Files.createTempDirectory("tabula-java-batch-test");
        tmpFolder.toFile().deleteOnExit();

        Path copiedPDF = tmpFolder.resolve(fs.getPath("spreadsheet.pdf"));
        Path sourcePDF = fs.getPath("src/test/resources/technology/tabula/spreadsheet_no_bounding_frame.pdf");
        Files.copy(sourcePDF, copiedPDF);
        copiedPDF.toFile().deleteOnExit();

        this.csvFromCommandLineArgs(new String[]{
                "-b", tmpFolder.toString(),
                "-p", "1", "-a",
                "150.56,58.9,654.7,536.12", "-f",
                "CSV"
        });

        Path csvPath = tmpFolder.resolve(fs.getPath("spreadsheet.csv"));
        assertTrue(csvPath.toFile().exists());
        assertArrayEquals(expectedCsv.getBytes(), Files.readAllBytes(csvPath));
    }

    @Test
    public void testExtractSpreadsheetWithAreaAndNewFile() throws ParseException, IOException {

        String expectedCsv = UtilsForTesting.loadCsv("src/test/resources/technology/tabula/csv/spreadsheet_no_bounding_frame.csv");

        File newFile = folder.newFile();
        this.csvFromCommandLineArgs(new String[]{
                "src/test/resources/technology/tabula/spreadsheet_no_bounding_frame.pdf",
                "-p", "1", "-a",
                "150.56,58.9,654.7,536.12", "-f",
                "CSV", "-o", newFile.getAbsolutePath()
        });

        assertArrayEquals(expectedCsv.getBytes(), Files.readAllBytes(Paths.get(newFile.getAbsolutePath())));
    }


    @Test
    public void testExtractJSONWithArea() throws ParseException, IOException {

        String expectedJson = UtilsForTesting.loadJson("src/test/resources/technology/tabula/json/spanning_cells_basic.json");

        assertEquals(expectedJson, this.csvFromCommandLineArgs(new String[]{
                "src/test/resources/technology/tabula/spanning_cells.pdf",
                "-p", "1", "-a",
                "150.56,58.9,654.7,536.12", "-f",
                "JSON"
        }));
    }

    @Test
    public void testExtractCSVWithArea() throws ParseException, IOException {

        String expectedCsv = UtilsForTesting.loadCsv("src/test/resources/technology/tabula/csv/spanning_cells.csv");

        assertEquals(expectedCsv, this.csvFromCommandLineArgs(new String[]{
                "src/test/resources/technology/tabula/spanning_cells.pdf",
                "-p", "1", "-a",
                "150.56,58.9,654.7,536.12", "-f",
                "CSV"
        }));
    }

    @Test
    public void testGuessOption() throws ParseException, IOException {
        String expectedCsvNoGuessing = UtilsForTesting.loadCsv("src/test/resources/technology/tabula/csv/TestCommandLineApp_testGuessOption_no_guessing.csv");
        assertEquals(expectedCsvNoGuessing, this.csvFromCommandLineArgs(new String[]{
                "src/test/resources/technology/tabula/icdar2013-dataset/competition-dataset-eu/eu-001.pdf",
                "-p", "1",
                "-f", "CSV"
        }));

        String expectedCsvWithGuessing = UtilsForTesting.loadCsv("src/test/resources/technology/tabula/csv/TestCommandLineApp_testGuessOption_with_guessing.csv");
        assertEquals(expectedCsvWithGuessing, this.csvFromCommandLineArgs(new String[]{
                "src/test/resources/technology/tabula/icdar2013-dataset/competition-dataset-eu/eu-001.pdf",
                "-p", "1",
                "-f", "CSV",
                "-g"
        }));
    }

    @Test
    public void testEncryptedPasswordSupplied() throws ParseException {
        String s = this.csvFromCommandLineArgs(new String[]{
                "src/test/resources/technology/tabula/encrypted.pdf",
                "-s", "userpassword",
                "-p", "1",
                "-f", "CSV"
        });
        assertEquals("FLA Audit Profile,,,,,,,,,", s.split("\\r?\\n")[0]);
    }

    @Test(expected=org.apache.commons.cli.ParseException.class)
    public void testEncryptedWrongPassword() throws ParseException {
        String s = this.csvFromCommandLineArgs(new String[]{
                "src/test/resources/technology/tabula/encrypted.pdf",
                "-s", "wrongpassword",
                "-p", "1",
                "-f", "CSV"
        });
    }

    @Test
    public void testExtractWithMultiplePercentArea() throws ParseException, IOException {

        String expectedCsv = UtilsForTesting.loadCsv("src/test/resources/technology/tabula/csv/MultiColumn.csv");

        assertEquals(expectedCsv, this.csvFromCommandLineArgs(new String[]{
                "src/test/resources/technology/tabula/MultiColumn.pdf",
                "-p", "1", "-a",
                "%0,0,100,50", "-a",
                "%0,50,100,100", "-f",
                "CSV"
        }));
    }

    @Test
    public void testExtractWithMultipleAbsoluteArea() throws ParseException, IOException {

        String expectedCsv = UtilsForTesting.loadCsv("src/test/resources/technology/tabula/csv/MultiColumn.csv");

        assertEquals(expectedCsv, this.csvFromCommandLineArgs(new String[]{
                "src/test/resources/technology/tabula/MultiColumn.pdf",
                "-p", "1", "-a",
                "0,0,451,212", "-a",
                "0,212,451,425", "-f",
                "CSV"
        }));
    }

    @Test
    public void testExtractWithPercentAndAbsoluteArea() throws ParseException, IOException {

        String expectedCsv = UtilsForTesting.loadCsv("src/test/resources/technology/tabula/csv/MultiColumn.csv");

        assertEquals(expectedCsv, this.csvFromCommandLineArgs(new String[]{
                "src/test/resources/technology/tabula/MultiColumn.pdf",
                "-p", "1", "-a",
                "%0,0,100,50", "-a",
                "0,212,451,425", "-f",
                "CSV"
        }));
    }

    @Test
    public void testLatticeModeWithColumnOption() throws ParseException, IOException {

        String expectedCsv = UtilsForTesting.loadCsv("src/test/resources/technology/tabula/csv/AnimalSounds.csv");

        assertEquals(expectedCsv, this.csvFromCommandLineArgs(new String[]{
                "src/test/resources/technology/tabula/AnimalSounds.pdf",
                "-p", "1", "-c",
                "59,218,331,551",
                "-r", "-f", "CSV"
        }));
    }

    @Test
    public void testLatticeModeWithColumnAndMultipleAreasOption() throws ParseException, IOException {

        String expectedJson = UtilsForTesting.loadJson("src/test/resources/technology/tabula/json/AnimalSounds1.json");
        String resultJson = this.csvFromCommandLineArgs(new String[]{
                "src/test/resources/technology/tabula/AnimalSounds1.pdf",
                "-p", "1", "-c", "57,136,197,296,314,391,457,553",
                "-a", "%0,0,100,50", "-a", "%0,50,100,100",
                 "-r", "-f", "JSON"
        });
        assertEquals(expectedJson, resultJson);
    }

    @Test
    public void testParseFloatListValid() throws ParseException {
        String input = "1.0,2.5,3.3";
        List<Float> expected = Arrays.asList(1.0f, 2.5f, 3.3f);
        assertEquals(expected, app.parseFloatList(input));
    }
    
    @Test
    void testParseFloatListInvalid() {
        String input = "1.0,abc,3.8";
        assertThrows(ParseException.class, () -> app.parseFloatList(input));
    }

    @Test
    public void testSingleFileProcessing() throws ParseException {
        app.extractTables(cmd);
        assertTrue(stringWriter.toString().contains("Expected output content"));
    }
    
    @Test
    public void testBatchFileProcessing() throws ParseException {
        when(cmd.hasOption("b")).thenReturn(true);
        File mockedDirectory = mock(File.class);
        when(mockedDirectory.isDirectory()).thenReturn(true);
        doReturn(mockedDirectory).when(app).getDirectoryFromCommandLine(cmd);
        app.extractTables(cmd);
        verify(app, times(1)).extractDirectoryTables(any(CommandLine.class), any(File.class));
    }
}
