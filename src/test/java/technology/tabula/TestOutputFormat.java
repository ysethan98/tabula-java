package technology.tabula;

import org.junit.Test;
import static org.junit.Assert.*;

public class TestOutputFormat {
    
    @Test
    public void testFormatNames() {
        String[] expectedNames = {"CSV", "TSV", "JSON"};
        String[] actualNames = OutputFormat.formatNames();

        assertArrayEquals(expectedNames, actualNames);
    }
}
