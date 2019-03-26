package org.geosolutions.datexgml;

import static org.junit.Assert.assertEquals;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

import javax.xml.namespace.NamespaceContext;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;

import org.junit.Test;
import org.w3c.dom.Document;

/** Tests for {@link MultiFileGmlConverter} class. */
public class GmlConverterTest {

    Document getResultList() throws Exception {
        List<File> fileList = new ArrayList<>();
        fileList.add(new File(this.getClass().getResource("datex.xsd").toURI()));

        return MultiFileGmlConverter.executeAndGet(
                fileList,
                Arrays.asList("D2LogicalModel:Situation".split(",")),
                "http://datex2.eu/schema/2/datex2");
    }

    /** Checks output document and its content. */
    @Test
    public void testOutputDocument() throws Exception {
        final Document doc = getResultList();
        final XPath xpath = getXpath();
        final Long numComplexTypes =
                Long.parseLong(xpath.evaluate("count(/schema/complexType)", doc));
        assertEquals(42L, numComplexTypes.longValue());
        String baseXpath =
                "./complexType[@name='OpenlrLastLocationReferencePointType']"
                        + "/complexContent/extension/@base";
        String base =
                (String)
                        xpath.compile(baseXpath)
                                .evaluate(doc.getDocumentElement(), XPathConstants.STRING);
        assertEquals("sit:OpenlrBaseLocationReferencePoint", base);
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
}
