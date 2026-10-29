package com.db2.proyectofinaldb2.util;

import com.opencsv.CSVParser;
import com.opencsv.CSVParserBuilder;
import com.opencsv.CSVReader;
import com.opencsv.CSVReaderBuilder;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public final class CsvSupport {

    private CsvSupport() {
    }

    public static char detectDelimiter(Path path) throws IOException {
        byte[] buffer = new byte[8192];
        int read;
        try (InputStream input = Files.newInputStream(path)) {
            read = input.read(buffer);
        }
        if (read <= 0) {
            return ',';
        }
        String firstLine = new String(buffer, 0, read, StandardCharsets.UTF_8).split("\\R", 2)[0];
        int commas = 0;
        int semicolons = 0;
        int tabs = 0;
        boolean quoted = false;
        for (char character : firstLine.toCharArray()) {
            if (character == '"') {
                quoted = !quoted;
            } else if (!quoted) {
                if (character == ',') {
                    commas++;
                } else if (character == ';') {
                    semicolons++;
                } else if (character == '\t') {
                    tabs++;
                }
            }
        }
        if (semicolons > commas && semicolons >= tabs) {
            return ';';
        }
        if (tabs > commas && tabs > semicolons) {
            return '\t';
        }
        return ',';
    }

    public static CSVReader reader(Path path, char delimiter) throws IOException {
        CharsetDecoder decoder = StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT);
        Reader reader = new BufferedReader(new InputStreamReader(Files.newInputStream(path), decoder));
        CSVParser parser = new CSVParserBuilder()
                .withSeparator(delimiter)
                .withIgnoreQuotations(false)
                .build();
        return new CSVReaderBuilder(reader).withCSVParser(parser).withMultilineLimit(100).build();
    }
}
