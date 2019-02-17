package org.geosolutions.datexgml;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import org.apache.commons.io.FileUtils;

/**
 * Main executable class for GML schema converter.
 */
public class ConverterMain {

    /**
     * Execute converter script with provided arguments.
     * 
     * @param args command line arguments array.
     * @throws IOException if there is a filesystem error.
     */
    public static void main(String[] args) throws IOException {
        if (args.length == 0 || args[0] == null || args[0].isEmpty()) {
            throw new IllegalArgumentException("This script need at least a file path as first argument");
        }
        String filePath = args[0];
        File file = new File(filePath);
        String targetNamespace;
        if (args.length < 3)
            targetNamespace = GmlConverter.DATEX_NS;
        else
            targetNamespace = args[2];
        GmlConverter converter = new GmlConverter(file, Arrays.asList(args[1].split(",")), targetNamespace);
        String result = converter.convert().getResultDocAsString();
        File out = new File(filePath + ".converted");
        FileUtils.writeStringToFile(out, result, StandardCharsets.UTF_8);
    }

}
