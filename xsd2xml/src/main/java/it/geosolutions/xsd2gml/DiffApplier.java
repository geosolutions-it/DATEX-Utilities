package it.geosolutions.xsd2gml;

import static it.geosolutions.xsd2gml.GmlDiff.findElementsXpath;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class DiffApplier {

    private static final Logger LOGGER = Logger.getLogger(DiffApplier.class.getName());
    private static final String TEMPLATE_PREFIX = "npra";
    private static final List<String> ATTRIBUTES_TO_REPLACE = Arrays.asList("type", "base");

    private Document originalDoc;

    private Document templateDoc;
    private Set<String> xpathRules;
    private final String targetPrefix;

    public DiffApplier(
            Document originalDoc, Document templateDoc, String diffText, String targetPrefix) {
        super();
        this.originalDoc = originalDoc;
        this.templateDoc = templateDoc;
        this.targetPrefix = targetPrefix;
        List<String> diffList = Arrays.asList(diffText.split("\\r?\\n"));
        xpathRules = new HashSet<String>(diffList);
    }

    public Document applyDifferences() {
        for (String xpath : xpathRules) {
            try {
                if (existsParentElement(xpath)) {
                    // if exists parent element, import Node to original doc
                    // get original parent Element
                    String parentXpathExpression = parentXpathExpression(xpath);
                    Element originParentElement =
                            findElementsXpath(parentXpathExpression, originalDoc).get(0);
                    // get template element
                    Element templateElement = findElementsXpath(xpath, templateDoc).get(0);
                    checkRepeatedElement(parentXpathExpression, templateElement);
                    // import the template Element
                    Element importedNode = (Element) originalDoc.importNode(templateElement, true);
                    originParentElement.appendChild(importedNode);
                    replacePrefixes(importedNode);
                }
            } catch (Exception ex) {
                LOGGER.log(Level.WARNING, ex.getMessage(), ex);
            }
        }
        cleanRepeatedNameElements();
        // modify SituationRecordType -> groupOfLocations type
        List<Element> groupLocElementList =
                findElementsXpath(
                        "/xs:schema/xs:complexType[@name='SituationRecordType']"
                                + "/xs:complexContent/xs:extension[@base='gml:AbstractFeatureType']"
                                + "/xs:sequence/xs:element[@name='groupOfLocations'"
                                + "]",
                        originalDoc);
        if (!groupLocElementList.isEmpty()) {
            Element element = groupLocElementList.get(0);
            element.setAttribute("type", "npra:GroupOfLocationsType");
        }
        return originalDoc;
    }

    private void replacePrefixes(Element element) {
        // try to replace attributes
        for (String att : ATTRIBUTES_TO_REPLACE) {
            if (element.hasAttribute(att) && element.getAttribute(att).contains(":")) {
                String[] parts = element.getAttribute(att).split(Pattern.quote(":"));
                if (parts.length == 2 && TEMPLATE_PREFIX.equals(parts[0]))
                    element.setAttribute(att, targetPrefix + ":" + parts[1]);
            }
        }
        // check child elements and use recursion
        NodeList childNodes = element.getChildNodes();
        for (int i = 0; i < childNodes.getLength(); i++) {
            Node node = childNodes.item(i);
            if (node instanceof Element) {
                replacePrefixes((Element) node);
            }
        }
    }

    private void cleanRepeatedNameElements() {
        try {
            final String parentXpathExpression = "/xs:schema/xs:complexType";
            List<Element> complexTypeElements =
                    findElementsXpath(parentXpathExpression, originalDoc);
            for (Element cte : complexTypeElements) {
                final String xpath =
                        parentXpathExpression + "[@name='" + cte.getAttribute("name") + "']";
                // check if element with same name exists
                final String nameAttrValue = "name";
                List<Element> repeatedElements =
                        findElementsXpath(
                                xpath + "//xs:element[@name='" + nameAttrValue + "']", originalDoc);
                // if repated elements found
                List<Element> elementsForRemove = new ArrayList<>();
                boolean hasPropertyType = false;
                if (repeatedElements.size() > 1) {
                    for (Element re : repeatedElements) {
                        String attribute = re.getAttribute("type");
                        if (attribute.endsWith("PropertyType")) {
                            hasPropertyType = true;
                        } else {
                            elementsForRemove.add(re);
                        }
                    }
                    if (hasPropertyType) {
                        for (Element re : elementsForRemove) {
                            Node parentNode = re.getParentNode();
                            parentNode.removeChild(re);
                        }
                    }
                }
            }

        } catch (Exception e) {
            LOGGER.fine("Unable to check and delete repeated elements.");
        }
    }

    private void checkRepeatedElement(String parentXpathExpression, Element templateElement) {
        try {
            // check if element with same name exists
            String nameAttrValue = templateElement.getAttribute("name");
            List<Element> repeatedElements =
                    findElementsXpath(
                            parentXpathExpression + "/xs:element[@name='" + nameAttrValue + "']",
                            originalDoc);
            for (Element re : repeatedElements) {
                Node parentNode = re.getParentNode();
                parentNode.removeChild(re);
            }
        } catch (Exception e) {
            LOGGER.fine("Unable to check and delete repeated elements.");
        }
    }

    private boolean existsParentElement(String xpathExpression) {
        String parentXpathExpression = parentXpathExpression(xpathExpression);
        List<Element> elementFound = findElementsXpath(parentXpathExpression, originalDoc);
        return !elementFound.isEmpty();
    }

    private String parentXpathExpression(String xpathExpression) {
        int lastPathSeparatorIndex = StringUtils.lastIndexOf(xpathExpression, "/");
        return xpathExpression.substring(0, lastPathSeparatorIndex);
    }
}
