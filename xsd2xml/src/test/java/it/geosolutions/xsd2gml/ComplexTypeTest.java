package it.geosolutions.xsd2gml;

import static it.geosolutions.xsd2gml.TestsUtils.getComplexTypes;
import static it.geosolutions.xsd2gml.TestsUtils.getNpraNamespace;
import static it.geosolutions.xsd2gml.TestsUtils.readDatex23Schema;
import static it.geosolutions.xsd2gml.Utils.documentToStringNpraPrefixed;
import static it.geosolutions.xsd2gml.Utils.searchElement;
import static it.geosolutions.xsd2gml.Utils.searchElements;
import static it.geosolutions.xsd2gml.Xsd2Gml.createOutputSchema;
import static it.geosolutions.xsd2gml.Xsd2Gml.initOutputSchema;
import java.util.Collections;
import static java.util.Collections.singleton;
import java.util.List;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.junit.Assert.assertThat;
import org.junit.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

public final class ComplexTypeTest {

    private final Document datex23Schema = readDatex23Schema();

    @Test
    public void testSituationToGmlConversionWithoutExtensions() {
        // get the DATEX 23 Situation complex type
        List<Element> elements = getComplexTypes(datex23Schema, "Situation");
        assertThat(elements.size(), is(1));
        ComplexTypeConverter situation =
                new ComplexTypeConverter(datex23Schema, elements.get(0), Collections.emptySet());
        // convert DATEX 23 Situation complex type to a GML feature
        Document outputSchema = createOutputSchema();
        Element outputSchemaRootNode =
                initOutputSchema(datex23Schema, outputSchema, getNpraNamespace());
        situation.toGmlFeature(outputSchema, outputSchemaRootNode, getNpraNamespace());
        // check the content of the output schema
        searchElements(outputSchema, "/schema/element[@name='Situation']");
        searchElement(outputSchema, "/schema/complexType[@name='SituationType']");
        searchElement(outputSchema, "/schema/complexType[@name='SituationTypePropertyType']");
        String c = documentToStringNpraPrefixed(outputSchema);
    }

    @Test
    public void testSituationToGmlConversionWithExtensions() {
        // get Situation and SituationRecord complex type
        Element situation = searchElement(datex23Schema, "/schema/complexType[@name='Situation']");
        Element situationRecord =
                searchElement(datex23Schema, "/schema/complexType[@name='SituationRecord']");
        assertThat(situation, notNullValue());
        assertThat(situationRecord, notNullValue());
        // create the converter for Situation complex type and convert to GML
        ComplexTypeConverter converter =
                new ComplexTypeConverter(datex23Schema, situation, singleton(situationRecord));
        Document outputSchema = createOutputSchema();
        Element outputSchemaRootNode =
                initOutputSchema(datex23Schema, outputSchema, getNpraNamespace());
        converter.toGmlFeature(outputSchema, outputSchemaRootNode, getNpraNamespace());
        // check the content of the output schema
        searchElements(outputSchema, "/schema/element[@name='Situation']");
        searchElement(outputSchema, "/schema/complexType[@name='SituationType']");
        searchElement(outputSchema, "/schema/complexType[@name='SituationTypePropertyType']");
        String c = documentToStringNpraPrefixed(outputSchema);
    }
}
