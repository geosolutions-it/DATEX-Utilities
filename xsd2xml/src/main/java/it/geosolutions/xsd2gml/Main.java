package it.geosolutions.xsd2gml;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.xml.namespace.QName;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.w3c.dom.Document;

public class Main {

    private static final String ADD_EXTRAS_PROP = "it.geosolutions.xsd2gml.add-extras";
    private static final Logger LOGGER = Logger.getLogger(Main.class.getName());

    public static void main(String[] args) throws IOException {
        if (args.length == 0 || args[0] == null || args[0].isEmpty()) {
            throw new IllegalArgumentException(
                    "This script need at least a file path as first argument");
        }
        String filePath = args[0];
        File mergeTempFile = null;
        try {
            // if is a directory, merge schema files
            if (checkIfIsDirectory(filePath)) {
                // save a merged document from all .xsd files on directory
                List<Document> docs = loadXsdDocumentFromDirectory(filePath);
                Document mergedDoc =
                        DocumentsMerger.merge(
                                docs, "D2LogicalModel", "http://targetnamespace.org/1.0");
                String result = Utils.documentToString(mergedDoc);
                mergeTempFile = new File(filePath, "merged");
                FileUtils.writeStringToFile(mergeTempFile, result, StandardCharsets.UTF_8);
                // swap filePath to merged file
                filePath = mergeTempFile.getAbsolutePath();
            }
            // build default path
            String defaultPath;
            if (mergeTempFile != null) {
                defaultPath =
                        mergeTempFile.getAbsolutePath()
                                + File.separator
                                + "convertedSchema"
                                + File.separator
                                + "gmlSchema.xsd.converted";
            } else {
                defaultPath = filePath + ".converted";
            }
            // ask to user output file
            String outputFile;
            if (args.length < 5) {
                outputFile = defaultPath;
            } else {
                outputFile = args[4];
            }
            convertSingleFile(args, filePath, outputFile);
        } finally {
            // clean merge file if was used
            if (mergeTempFile != null) {
                mergeTempFile.delete();
            }
        }
    }

    static List<Document> loadXsdDocumentFromDirectory(String dirPath) {
        File dir = new File(dirPath);
        File[] files = dir.listFiles((d, name) -> name.endsWith(".xsd"));
        List<Document> documents = new ArrayList<>();
        for (File ef : files) {
            try {
                Document document = Utils.readDocument(new FileInputStream(ef));
                documents.add(document);
            } catch (FileNotFoundException e) {
                LOGGER.log(Level.WARNING, e.getMessage(), e);
            }
        }
        return documents;
    }

    static boolean checkIfIsDirectory(String filePath) {
        File file = new File(filePath);
        return file.isDirectory();
    }

    private static void convertSingleFile(String[] args, String filePath, String outFilePath)
            throws IOException {
        Document document = Utils.readDocument(filePath);
        List<String> rootTypes = Arrays.asList(args[1].split(","));
        QName targetNamespace = new QName(args[3], args[2], args[2]);
        Xsd2Gml converter = new Xsd2Gml(document, targetNamespace, rootTypes);
        String result = Utils.documentToStringNpraPrefixed(converter.getGmlSchema());
        // add gml extras if it's required
        result = addGmlExtras(result);
        File out = new File(outFilePath);
        FileUtils.writeStringToFile(out, result, StandardCharsets.UTF_8);
    }

    static String addGmlExtras(String documentText) {
        try {
            Document inputDocument = GmlDiff.documentFromText(documentText);
            InputStream resourceAsStream =
                    Main.class.getClassLoader().getResourceAsStream("datex_2.3_gml.xsd");
            Document targetDocument = GmlDiff.documentFromInputStream(resourceAsStream);
            InputStream diffFileStream =
                    Main.class.getClassLoader().getResourceAsStream("report.txt");
            String diffText = IOUtils.toString(diffFileStream, StandardCharsets.UTF_8);
            DiffApplier diffApplier = new DiffApplier(inputDocument, targetDocument, diffText);
            Document resultDocument = diffApplier.applyDifferences();
            return Utils.documentToString(resultDocument);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
