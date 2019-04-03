package it.geosolutions.xsd2gml;

import static it.geosolutions.xsd2gml.Utils.readDocument;
import static it.geosolutions.xsd2gml.Utils.searchElement;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import javax.xml.namespace.QName;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

final class TestsUtils {

    private TestsUtils() {}

    static Document readDatex23Schema() {
        InputStream input = TestsUtils.class.getResourceAsStream("/datex_2.3.xsd");
        if (input == null) {
            // strange, let's at least be clear about what happend.
            throw new RuntimeException("Resource '/datex_2.3.xsd' nor found.");
        }
        // read the schema
        try {
            return readDocument(input);
        } finally {
            // make sure we close the input stream
            try {
                input.close();
            } catch (Exception exception) {
                // nothing we can do about it
            }
        }
    }

    static List<Element> getComplexTypes(Document schema, String... complexTypeNames) {
        List<Element> complexTypes = new ArrayList<>();
        for (String complexTypeName : complexTypeNames) {
            String xpath = String.format("/schema/complexType[@name='%s']", complexTypeName);
            Element complexType = searchElement(schema, xpath);
            if (complexType == null) {
                throw new RuntimeException(
                        String.format("Xpath '%s' didn't yield any result.", xpath));
            }
            complexTypes.add(complexType);
        }
        return complexTypes;
    }

    static QName getNpraNamespace() {
        return new QName("http://www.vegvesen.no/datex/1.0", "npra", "npra");
    }
}
