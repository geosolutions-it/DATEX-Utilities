package org.geosolutions.datexgml;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.apache.commons.io.FileUtils;

/**
 * Main executable class for GML schema converter.
 */
public class ConverterMain {

    static final int MINIMUN_PARAMETERS = 3;

    /**
     * Execute converter script with provided arguments.
     * 
     * @param args command line arguments array.
     * @throws IOException if there is a filesystem error.
     */
    public static void main(String[] args) throws IOException {
        final int firstFileArgPosition = MINIMUN_PARAMETERS - 1;
        // validate we must have at least 3 arguments
        if (args.length < MINIMUN_PARAMETERS)
            throw new IllegalArgumentException("This script needs at least 3 arguments.");
        if (args[firstFileArgPosition] == null || args[firstFileArgPosition].isEmpty()) {
            throw new IllegalArgumentException(
                    "This script need at least a file path as third argument.");
        }
        final int numberOfFiles = (args.length - MINIMUN_PARAMETERS) + 1;
        // get files from arg[2] onward
        final List<File> fileList = new ArrayList<>();
        for (int i = 0; i < numberOfFiles; i++) {
            final int currentPos = firstFileArgPosition + i;
            String filePath = args[currentPos];
            fileList.add(new File(filePath));
        }
        // get target namespace
        String targetNamespace = args[1];
        // instance converter
        //        if (fileList.size() == 1) {
        //            executeOneFileConverter(args, firstFileArgPosition, fileList,
        // targetNamespace);
        //        } else {
        //            MultiFileGmlConverter.executeOnFilesystem(
        //                    fileList, Arrays.asList(args[0].split(",")), targetNamespace);
        //        }
        MultiFileGmlConverter.executeOnFilesystem(
                fileList, Arrays.asList(args[0].split(",")), targetNamespace);
    }

    protected static void executeOneFileConverter(
            String[] args,
            final int firstFileArgPosition,
            final List<File> fileList,
            String targetNamespace)
            throws IOException {
        GmlConverter converter =
                new GmlConverter(fileList, Arrays.asList(args[0].split(",")), targetNamespace);
        String result = converter.convert().getResultDocAsString();
        File out = new File(args[firstFileArgPosition] + ".converted");
        FileUtils.writeStringToFile(out, result, StandardCharsets.UTF_8);
    }

}
