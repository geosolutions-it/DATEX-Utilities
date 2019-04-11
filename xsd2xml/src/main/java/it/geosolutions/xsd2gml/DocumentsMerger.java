package it.geosolutions.xsd2gml;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;

import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

public class DocumentsMerger {

    private static final Logger LOGGER = Logger.getLogger(DocumentsMerger.class.getName());
    private static final Set<String> forbiddenPrefixes = new HashSet<String>(Arrays.asList("xs", "gml"));
    
    private List<Document> documents;
    private String targetPrefix;
    private String targetNamespaceURI;
    private Document resultDocument;
    private Set<String> typesSet = new HashSet<>();

    public static Document merge(
            List<Document> documents, String targetPrefix, String targetNamespaceURI) {
        DocumentsMerger merger = new DocumentsMerger(documents, targetPrefix, targetNamespaceURI);
        return merger.mergeDocument();
    }

    private DocumentsMerger(
            List<Document> documents, String targetPrefix, String targetNamespaceURI) {
        this.documents = documents;
        this.targetPrefix = targetPrefix;
        this.targetNamespaceURI = targetNamespaceURI;
    }

    private Document mergeDocument() {
        resultDocument = getCompiledInitialDocument();
        for (Document document : documents) {
            process(document);
        }
        translatePrefixes();
        return resultDocument;
    }

    private void translatePrefixes() {
	// walk for all document attributes
	List<Attr> attributes = Utils.searchAttributes(resultDocument.getFirstChild(), "//@*");
	for (Attr eattr : attributes) {
	    final String attrName = eattr.getName();
	    final String value = eattr.getValue();
            if (("base".equals(attrName)
                            || "name".equals(attrName)
                            || "type".equals(attrName)
                            || "ref".equals(attrName))
                    && isValidPrefixedValue(value)) {
                String[] parts = value.split(Pattern.quote(":"));
                String replaceValue = targetPrefix + ":" + parts[1];
                eattr.setValue(replaceValue);
            }
	}
    }
    
    private boolean isValidPrefixedValue(String value) {
	if (value == null || value.isEmpty() || !value.contains(":"))
	    return false;
	String[] parts = value.split(Pattern.quote(":"));
	if (parts.length > 2) return false;
	for (String cfp : forbiddenPrefixes) {
	    if (cfp.equals(parts[0])) return false;
	}
	return true;
    }

    private void process(Document document) {
        // get all /schema child elements except xs:import
        List<Element> childs = Utils.searchElements(document.getFirstChild(), "child::*");
        for (Element child : childs) {
            if (!child.getTagName().equals("import")) {
                Element importedChild = (Element) resultDocument.importNode(child, true);
                resultDocument.getFirstChild().appendChild(importedChild);
                checkTypeRegistry(importedChild);
            }
        }
    }

    private void checkTypeRegistry(Element importedChild) {
        String tn = importedChild.getTagName();
        if ("element".equals(tn) || "complexType".equals(tn) || "simpleType".equals(tn)) {
            String typeName = importedChild.getAttribute("name");
            if (typesSet.contains(typeName)) {
                LOGGER.log(Level.INFO, "Type '{0}' already found.", typeName);
            } else {
                typesSet.add(typeName);
            }
        }
    }

    private Document getCompiledInitialDocument() {
        String documentText =
                "<?xml version=\"1.0\" encoding=\"utf-8\" standalone=\"no\"?>"
                        + "<xs:schema xmlns:"
                        + targetPrefix
                        + "=\""
                        + targetNamespaceURI
                        + "\" "
                        + "xmlns:gml=\"http://www.opengis.net/gml/3.2\" xmlns:xs=\"http://www.w3.org/2001/XMLSchema\" "
                        + "attributeFormDefault=\"unqualified\" elementFormDefault=\"qualified\" "
                        + "targetNamespace=\""
                        + targetNamespaceURI
                        + "\" version=\"3.0\"> </xs:schema>";
        return Utils.readDocument(
                new ByteArrayInputStream(documentText.getBytes(StandardCharsets.UTF_8)));
    }
}
