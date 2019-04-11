package it.geosolutions.xsd2gml;

import static it.geosolutions.xsd2gml.GmlDiff.findElementsXpath;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.commons.lang3.StringUtils;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

public class DiffApplier {

    private static final Logger LOGGER = Logger.getLogger(DiffApplier.class.getName());

    private Document originalDoc;

    private Document templateDoc;
    private Set<String> xpathRules;

    public DiffApplier(Document originalDoc, Document templateDoc, String diffText) {
        super();
        this.originalDoc = originalDoc;
        this.templateDoc = templateDoc;
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
                    // import the template Element
                    Node importedNode = originalDoc.importNode(templateElement, true);
                    originParentElement.appendChild(importedNode);
                }
            } catch (Exception ex) {
                LOGGER.log(Level.WARNING, ex.getMessage(), ex);
	    }
	}
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
