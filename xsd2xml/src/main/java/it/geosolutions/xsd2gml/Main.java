package it.geosolutions.xsd2gml;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import javax.xml.namespace.QName;
import org.apache.commons.io.FileUtils;
import org.w3c.dom.Document;

public class Main {

    public static void main(String[] args) throws IOException {
        if (args.length == 0 || args[0] == null || args[0].isEmpty()) {
            throw new IllegalArgumentException(
                    "This script need at least a file path as first argument");
        }
        String filePath = args[0];
        Document document = Utils.readDocument(filePath);
        File file = new File(filePath);
        List<String> rootTypes = Arrays.asList(args[1].split(","));
        QName targetNamespace = new QName(args[3], args[2], args[2]);
        Xsd2Gml converter = new Xsd2Gml(document, targetNamespace, rootTypes);
        String result = Utils.documentToString(converter.getGmlSchema());
        File out = new File(filePath + ".converted");
        FileUtils.writeStringToFile(out, result, StandardCharsets.UTF_8);
    }
}
