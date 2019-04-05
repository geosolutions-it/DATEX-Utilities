package it.geosolutions.xsd2gml;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.ws.commons.util.NamespaceContextImpl;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

public class GmlDiff {

    static final String NAME_ATTR = "name";
    static final List<String> IDENTITY_ATTRIBUTES = Arrays.asList("name", "base", "type", "ref");

    private Document testDocument;
    private Document targetDocument;

    GmlDiff(Document testDocument, Document targetDocument) {
        this.testDocument = testDocument;
        this.targetDocument = targetDocument;
    }

    public String compare() {
        SubElementsWalker walker = new SubElementsWalker(testDocument, targetDocument);
        Set<String> missingElements = walker.buildMissingElementsList();
        StringBuilder sb = new StringBuilder();
        for (String cstr : missingElements) {
            sb.append(cstr);
            sb.append("\n");
        }
        return sb.toString();
    }

    /**
     * Discovers all missing elements we have on target document but don't on origin document.
     *
     * @param target target document
     * @param origin origin document
     * @return a list of missing elements
     */
    static List<Element> getMissingTypeElements(Document target, Document origin) {
        List<Element> missingElements = new ArrayList<>();
        List<Element> typeElements = getRootTypeElements(target);
        for (Element element : typeElements) {
            if (!existsTypeElement(element, origin)) {
                missingElements.add(element);
            }
        }
        return missingElements;
    }

    static List<Element> getMatchingTypeElements(Document target, Document origin) {
        List<Element> matchingElements = new ArrayList<>();
        List<Element> typeElements = getRootTypeElements(target);
        for (Element element : typeElements) {
            if (existsTypeElement(element, origin)) {
                matchingElements.add(element);
            }
        }
        return matchingElements;
    }

    static boolean existsTypeElement(Element element, Document anotherDoc) {
        List<Element> typeElements = getRootTypeElements(anotherDoc);
        for (Element el : typeElements) {
            if (isSameTypeElement(el, element)) return true;
        }
        return false;
    }

    static boolean isSameTypeElement(Element one, Element two) {
        if (one == null || two == null) return false;
        if (!existsAttribute(one, NAME_ATTR) || !existsAttribute(two, NAME_ATTR)) return false;
        if (Objects.equals(one.getTagName(), two.getTagName())
                && Objects.equals(one.getAttribute(NAME_ATTR), two.getAttribute(NAME_ATTR)))
            return true;
        return false;
    }

    static boolean existsAttribute(Element element, String attributeName) {
        String attribute = element.getAttribute(attributeName);
        return (attribute != null && !attribute.trim().isEmpty());
    }

    static List<Element> getRootTypeElements(Document doc) {
        // element, complexType, simpleType
        List<Element> elements = new ArrayList<>();
        NodeList nodeList = doc.getFirstChild().getChildNodes();
        for (int i = 0; i < nodeList.getLength(); i++) {
            Node item = nodeList.item(i);
            if (item instanceof Element) {
                elements.add((Element) item);
            }
        }
        return elements;
    }

    static XPath xpath() {
        XPathFactory factory = XPathFactory.newInstance();
        XPath xpath = factory.newXPath();
        NamespaceContextImpl ctx = new NamespaceContextImpl();
        ctx.startPrefixMapping("xs", "http://www.w3.org/2001/XMLSchema");
        xpath.setNamespaceContext(ctx);
        return xpath;
    }

    static DocumentBuilder getNamespaceAwareBuilder() {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        DocumentBuilder builder;
        try {
            builder = factory.newDocumentBuilder();
        } catch (ParserConfigurationException e) {
            throw new RuntimeException(e);
        }
        return builder;
    }

    static Document documentFromText(String documentText) {
        try {
            return getNamespaceAwareBuilder()
                    .parse(new ByteArrayInputStream(documentText.getBytes(StandardCharsets.UTF_8)));
        } catch (SAXException | IOException e) {
            throw new RuntimeException(e);
        }
    }

    static Document documentFromInputStream(InputStream is) {
        try {
            return getNamespaceAwareBuilder().parse(is);
        } catch (SAXException | IOException e) {
            throw new RuntimeException(e);
        }
    }

    static List<Element> findElementsXpath(String xpath, Node node) {
        List<Element> elements = new ArrayList<>();
        try {
            NodeList nodeList = (NodeList) xpath().evaluate(xpath, node, XPathConstants.NODESET);
            for (int i = 0; i < nodeList.getLength(); i++) {
                Node item = nodeList.item(i);
                if (item instanceof Element) elements.add((Element) item);
            }
            return elements;
        } catch (XPathExpressionException e) {
            throw new RuntimeException(e);
        }
    }

    static class SubElementsWalker {

        Document originDoc;
        Document targetDoc;

        Set<String> missingElementsXpathSet = new HashSet<>();

        SubElementsWalker(Document originDoc, Document targetDoc) {
            super();
            this.originDoc = originDoc;
            this.targetDoc = targetDoc;
        }

        Set<String> buildMissingElementsList() {
            List<Element> childElements = findElementsXpath("/xs:schema/*", targetDoc);
            for (Element el : childElements) {
                walkElement(el);
            }
            return missingElementsXpathSet;
        }

        private void walkElement(Element targetElement) {
            String xpathExpression = getXpathExpression(targetElement);
            // check if element exists on origin document
            List<Element> elementsFound = findElementsXpath(xpathExpression, originDoc);
            if (elementsFound.isEmpty()) {
                missingElementsXpathSet.add(xpathExpression);
            } else {
                // walk into each child element of target element
                NodeList childNodes = targetElement.getChildNodes();
                for (int i = 0; i < childNodes.getLength(); i++) {
                    Node item = childNodes.item(i);
                    if (item instanceof Element) {
                        walkElement((Element) item);
                    }
                }
            }
        }

        private String getXpathExpression(Element element) {
            StringBuilder sb = new StringBuilder();
            sb.append("/");
            sb.append(element.getPrefix());
            sb.append(":");
            sb.append(element.getLocalName());
            // check for identity attributes
            List<Pair<String, String>> identAttributes = new ArrayList<>();
            for (String attrName : IDENTITY_ATTRIBUTES) {
                String value = element.getAttribute(attrName);
                if (StringUtils.isNotBlank(value)) {
                    identAttributes.add(Pair.of(attrName, value));
                }
            }
            if (!identAttributes.isEmpty()) {
                sb.append("[");
                for (int i = 0; i < identAttributes.size(); i++) {
                    Pair<String, String> pair = identAttributes.get(i);
                    if (i > 0) sb.append(" and ");
                    sb.append("@");
                    sb.append(pair.getLeft());
                    sb.append("='");
                    sb.append(pair.getRight());
                    sb.append("'");
                }
                sb.append("]");
            }
            if (element.getParentNode() != null && element.getParentNode() instanceof Element) {
                Element parent = (Element) element.getParentNode();
                return getXpathExpression(parent) + sb.toString();
            } else {
                return sb.toString();
            }
        }

        private boolean equalsElements(Element one, Element two) {
            if (!Objects.equals(one.getTagName(), two.getTagName())
                    || !Objects.equals(one.getPrefix(), two.getPrefix())) return false;
            // check identity attributes
            for (String attrName : IDENTITY_ATTRIBUTES) {
                if (Objects.equals(one.getAttribute(attrName), two.getAttribute(attrName)))
                    return false;
            }
            // all checks passed
            return true;
        }
    }
}
