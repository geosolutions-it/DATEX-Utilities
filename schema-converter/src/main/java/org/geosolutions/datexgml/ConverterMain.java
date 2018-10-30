package org.geosolutions.datexgml;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.apache.commons.io.FileUtils;

public class ConverterMain {
    
    public static void main(String[] args) throws IOException {
        if (args.length == 0 || args[0]==null || args[0].isEmpty()) {
            throw new IllegalArgumentException("This script need at least a file path as first argument");
        }
        String filePath = args[0];
        File file = new File(filePath);
        GmlConverter converter = new GmlConverter(file);
        String result = converter.convert().getResultDocAsString();
        File out = new File(filePath + ".converted");
        FileUtils.writeStringToFile(out, result, StandardCharsets.UTF_8);
    }
    
}
