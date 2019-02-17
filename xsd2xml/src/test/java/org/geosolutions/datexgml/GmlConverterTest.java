package org.geosolutions.datexgml;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.Iterator;
import java.util.Map;

import javax.xml.namespace.NamespaceContext;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;

import org.junit.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Node;

/**
 * Tests for {@link GmlConverter} class.
 */
public class GmlConverterTest {

    GmlConverter getConverter() {
        try {
            return new GmlConverter(new File(this.getClass().getResource("datex.xsd").toURI()),
                    Arrays.asList("Situation"), GmlConverter.DATEX_NS);
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    public void testSituationNode() {
        GmlConverter conv = getConverter();
        Node situationNode = conv.getSituationRootNode();
        assertTrue(situationNode != null);
    }

    /**
     * Checks output document and its content.
     */
    @Test
    public void testOutputDocument() throws Exception {
        final GmlConverter conv = getConverter();
        conv.convert();
        final Document doc = conv.getResultDoc();
        final XPath xpath = getXpath();
        final Long numComplexTypes = Long.parseLong(xpath.evaluate("count(/schema/complexType)", doc));
        assertEquals(42L, numComplexTypes.longValue());
        String baseXpath = "./complexType[@name='OpenlrLastLocationReferencePointType']"
                + "/complexContent/extension/@base";
        String base = (String) xpath.compile(baseXpath).evaluate(doc.getDocumentElement(), XPathConstants.STRING);
        assertEquals("D2LogicalModel:OpenlrBaseLocationReferencePoint", base);
    }

    private XPath getXpath() {
        XPath xpath = XPathFactory.newInstance().newXPath();
        xpath.setNamespaceContext(getNSContext());
        return xpath;
    }

    private NamespaceContext getNSContext() {
        return new NamespaceContext() {

            @Override
            public Iterator getPrefixes(String namespaceURI) {
                return null;
            }

            @Override
            public String getPrefix(String namespaceURI) {
                return null;
            }

            @Override
            public String getNamespaceURI(String prefix) {
                if ("xs".equals(prefix)) {
                    return "http://www.w3.org/2001/XMLSchema";
                }
                return null;
            }
        };
    }

    @Test
    public void testSituationToMap() throws Exception {
        GmlConverter conv = getConverter();
        conv.treeToComplexMap("Situation");
        Map<String, ComplexType> map = conv.getComplexMap();
        assertEquals(42, map.size());
    }

}
