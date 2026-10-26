package com.db2.proyectofinaldb2.util;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CsvSupportTest {

    @TempDir
    Path tempDir;

    @Test
    void detectsCommaDelimiter() throws Exception {
        Path file = Files.writeString(tempDir.resolve("coma.csv"), "a,b,c\n1,2,3\n");
        assertEquals(',', CsvSupport.detectDelimiter(file));
    }

    @Test
    void detectsSemicolonDelimiter() throws Exception {
        Path file = Files.writeString(tempDir.resolve("punto-y-coma.csv"), "a;b;c\n1;2;3\n");
        assertEquals(';', CsvSupport.detectDelimiter(file));
    }

    @Test
    void detectsTabDelimiter() throws Exception {
        Path file = Files.writeString(tempDir.resolve("tab.tsv"), "a\tb\tc\n1\t2\t3\n");
        assertEquals('\t', CsvSupport.detectDelimiter(file));
    }

    @Test
    void ignoresDelimitersInsideQuotedValues() throws Exception {
        Path file = Files.writeString(tempDir.resolve("comillas.csv"), "\"a;b,c\",b,c\n1;2,3\n");
        assertEquals(',', CsvSupport.detectDelimiter(file));
    }
}
