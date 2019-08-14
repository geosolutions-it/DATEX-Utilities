package it.geosolutions.xsd2gml;

import static it.geosolutions.xsd2gml.TestsUtils.getNpraNamespace;
import static it.geosolutions.xsd2gml.TestsUtils.readDatex23Schema;
import static it.geosolutions.xsd2gml.Utils.documentToStringNpraPrefixed;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.junit.Test;
import org.w3c.dom.Document;
import org.xml.sax.SAXException;

public final class Xsd2GmlTest {

    private final Document datex23Schema = readDatex23Schema();

    @Test
    public void testGroupOfLocationsLinearTypeToGmlConversion() {
        Xsd2Gml xsd2Gml =
                new Xsd2Gml(datex23Schema, getNpraNamespace(), Arrays.asList("GroupOfLocationsLinear"));
        Document result = xsd2Gml.getGmlSchema();
        String content = documentToStringNpraPrefixed(result, "npra");
    }

    @Test
    public void testSituationToGmlConversion() {
        Xsd2Gml xsd2Gml =
                new Xsd2Gml(datex23Schema, getNpraNamespace(), Arrays.asList("Situation"));
        Document result = xsd2Gml.getGmlSchema();
        String content = documentToStringNpraPrefixed(result, "npra");
    }

    @Test
    public void testTravelTimeDataToGmlConversion() {
        Xsd2Gml xsd2Gml =
                new Xsd2Gml(datex23Schema, getNpraNamespace(), Arrays.asList("TravelTimeData"));
        Document result = xsd2Gml.getGmlSchema();
        String content = documentToStringNpraPrefixed(result, "npra");
    }

    @Test
    public void testSituationCctvToGmlConversion() throws Exception {
        Xsd2Gml xsd2Gml =
                new Xsd2Gml(datex23Schema, getNpraNamespace(), Arrays.asList("Situation", "CctvCameraMetadataRecord"));
        Document result = xsd2Gml.getGmlSchema();
        String content =
                Main.addGmlExtras(documentToStringNpraPrefixed(result, "prefix"), "prefix");
        //        File testResultFile =
        //                new
        // File("C:\\Users\\fmino\\Documents\\NPRA\\convert_test\\datex2\\testResult.xsd");
        //        FileUtils.write(testResultFile, content, StandardCharsets.UTF_8);
    }

    private Document loadDocument(File file)
            throws ParserConfigurationException, SAXException, IOException {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        DocumentBuilder builder = factory.newDocumentBuilder();
        return builder.parse(file);
    }
}
