package it.geosolutions.xsd2gml;

import static it.geosolutions.xsd2gml.TestsUtils.getNpraNamespace;
import static it.geosolutions.xsd2gml.TestsUtils.readDatex23Schema;
import static it.geosolutions.xsd2gml.Utils.documentToString;
import java.util.Arrays;
import org.junit.Test;
import org.w3c.dom.Document;

public final class Xsd2GmlTest {

    private final Document datex23Schema = readDatex23Schema();

    @Test
    public void testGroupOfLocationsLinearTypeToGmlConversion() {
        Xsd2Gml xsd2Gml =
                new Xsd2Gml(datex23Schema, getNpraNamespace(), Arrays.asList("GroupOfLocationsLinear"));
        Document result = xsd2Gml.getGmlSchema();
        String content = documentToString(result);
    }

    @Test
    public void testSituationToGmlConversion() {
        Xsd2Gml xsd2Gml =
                new Xsd2Gml(datex23Schema, getNpraNamespace(), Arrays.asList("Situation"));
        Document result = xsd2Gml.getGmlSchema();
        String content = documentToString(result);
    }

    @Test
    public void testTravelTimeDataToGmlConversion() {
        Xsd2Gml xsd2Gml =
                new Xsd2Gml(datex23Schema, getNpraNamespace(), Arrays.asList("TravelTimeData"));
        Document result = xsd2Gml.getGmlSchema();
        String content = documentToString(result);
    }

    @Test
    public void testSituationCctvToGmlConversion() {
        Xsd2Gml xsd2Gml =
                new Xsd2Gml(datex23Schema, getNpraNamespace(), Arrays.asList("Situation", "CctvCameraMetadataRecord"));
        Document result = xsd2Gml.getGmlSchema();
        String content = documentToString(result);
    }
}
