package org.geosolutions.datexgml;

import java.io.File;
import java.io.StringWriter;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/** Schema file converter class. */
class SchemaFileMetadata {

    public static final String XS_NS = "http://www.w3.org/2001/XMLSchema";

    private final Document document;
    private final String filename;
    private final String formerNamespace;
    private final String targetNamespace;
    private final Map<String, String> prefixedNamespacesMap = new HashMap<>();

    private Map<String, TypeMetadata> activatedTypesMap = new HashMap<>();
    protected BiConsumer<String, String> activateTypeConsumer;
    
    private Document resultDoc;

    public SchemaFileMetadata(
            Document document,
            String targetNamespace,
            String filename,
            BiConsumer<String, String> activateTypeConsumer) {
        super();
        this.document = document;
        this.targetNamespace = targetNamespace;
        this.filename = filename;
        this.activateTypeConsumer = activateTypeConsumer;
        // get former namespace from 'targetNamespace' attribute
        this.formerNamespace = xpathToElement("/schema", document).getAttribute("targetNamespace");
        // get prefixed namespaces
        buildPrefixesMap();
    }

    /** Builds the result document and stores it on resultDoc field. */
    public void buildResultDocument() {
        createNewDocument();
        // add complex types
        List<ComplexType> complexResults = buildComplexResults();
        Node schema = resultDoc.getFirstChild();
        for (ComplexType ct : complexResults) {
            Element complexNode = (Element) resultDoc.importNode(ct.getComplexTypeNode(), true);
            schema.appendChild(complexNode);
            // convert internal element type
            GmlConverter.processElements(
                    complexNode,
                    x -> {
                        return activatedTypesMap.containsKey(x)
                                && activatedTypesMap.get(x).getType().equals(XsdTypeType.COMPLEX);
                    });
            // append propertyType and Element
            schema.appendChild(resultDoc.importNode(ct.getComplexPropertyTypeNode(), true));
            schema.appendChild(resultDoc.importNode(ct.getElementNode(), true));
        }
        // add simple types (just clone them)
        List<TypeMetadata> simpleList =
                activatedTypesMap.values().stream()
                        .filter(x -> x.getType().equals(XsdTypeType.SIMPLE))
                        .collect(Collectors.toList());
        for (TypeMetadata tmd : simpleList) {
            List<Node> simpleNodes =
                    xpathToList(
                            "//schema/simpleType[@name='" + tmd.getTypeName() + "']",
                            this.document);
            Element simpleElement = (Element) simpleNodes.get(0);
            schema.appendChild(resultDoc.importNode(simpleElement, true));
        }
        // add element tags (clone them)
        List<TypeMetadata> elementList =
                activatedTypesMap.values().stream()
                        .filter(x -> x.getType().equals(XsdTypeType.ELEMENT))
                        .collect(Collectors.toList());
        for (TypeMetadata tmd : elementList) {
            List<Node> elementNodes =
                    xpathToList(
                            "//schema/element[@name='" + tmd.getTypeName() + "']", this.document);
            Element element = (Element) elementNodes.get(0);
            schema.appendChild(resultDoc.importNode(element, true));
        }
        // post process namespacesURI to target parent
        postProcessNamespaceURIs();
    }

    private void postProcessNamespaceURIs() {
        try {
            // get former namespace URI parent
            URI formerNSURI = new URI(formerNamespace);
            URI formerParent = formerNSURI.resolve(".");
            String formerParentURL = formerParent.toString();
            // get target namespace
            URI targetURI = (new URI(targetNamespace)).resolve(".");
            String targetParentURL = targetURI.toString();
            // check all <xs:schema xmlns:* attribute that starts with formerParent
            Element schemaElement = xpathToElement("/schema", this.resultDoc);
            NamedNodeMap attributesNodeMap = schemaElement.getAttributes();
            for (int i = 0; i < attributesNodeMap.getLength(); i++) {
                Attr item = (Attr) attributesNodeMap.item(i);
                if (item.getName().startsWith("xmlns:")
                        && item.getValue().startsWith(formerParentURL)) {
                    // replace parents
                    item.setValue(item.getValue().replace(formerParentURL, targetParentURL));
                }
            }
            // All <xs:schema / <xs:import / @namespace
            List<Node> importList = xpathToList("/schema/import", this.resultDoc);
            for (Node item : importList) {
                Element importElement = (Element) item;
                String namespace = importElement.getAttribute("namespace");
                if (namespace != null && namespace.startsWith(formerParentURL)) {
                    importElement.setAttribute(
                            "namespace", namespace.replace(formerParentURL, targetParentURL));
                }
            }
            // <xs:schema targetNamespace
            String newTargetNS = this.formerNamespace.replace(formerParentURL, targetParentURL);
            schemaElement.setAttribute("targetNamespace", newTargetNS);

        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }

    private void createNewDocument() {
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder;
        try {
            builder = dbf.newDocumentBuilder();
            resultDoc = builder.newDocument();
            Node schemaNode = document.getFirstChild().cloneNode(false);
            resultDoc.adoptNode(schemaNode);
            resultDoc.appendChild(schemaNode);
            // xmlns:gml="http://www.opengis.net/gml/3.2"
            ((Element)schemaNode).setAttribute("xmlns:gml", "http://www.opengis.net/gml/3.2");
            // targetNamespace
            // ((Element)schemaNode).setAttribute("targetNamespace", targetNamespace);
            // xmlns:D2LogicalModel
            // ((Element)schemaNode).setAttribute("xmlns:D2LogicalModel", targetNamespace);
            // add import gml schema
            Element importElement = resultDoc.createElementNS(XS_NS, "xs:import");
            importElement.setAttribute("namespace", "http://www.opengis.net/gml/3.2");
            importElement.setAttribute("schemaLocation", "http://schemas.opengis.net/gml/3.2.1/gml.xsd");
            schemaNode.appendChild(importElement);
        } catch (ParserConfigurationException e) {
            throw new RuntimeException(e);
        }
    }

    /** Returns the result converted schema. */
    public Document getResultDocument() {
        return this.resultDoc;
    }

    public String getResultDocAsString() {
        try {
            TransformerFactory tf = TransformerFactory.newInstance();
            Transformer transformer = tf.newTransformer();
            transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
            StringWriter writer = new StringWriter();
            transformer.transform(new DOMSource(resultDoc), new StreamResult(writer));
            String output = writer.getBuffer().toString();
            return output;
        } catch (TransformerException e) {
            throw new RuntimeException(e);
        }
    }

    private List<ComplexType> buildComplexResults() {
	List<ComplexType> results = new ArrayList<>();
        List<TypeMetadata> complexTypesMD =
                activatedTypesMap.entrySet().stream()
                        .filter(e -> e.getValue().getType().equals(XsdTypeType.COMPLEX))
                        .map(Entry::getValue)
                        .collect(Collectors.toList());
        for (TypeMetadata tmd : complexTypesMD) {
            List<Node> complexNodes =
                    xpathToList("//schema/complexType[@name='" + tmd.getTypeName() + "']", this.document);
            ComplexType ctype = new ComplexType(complexNodes.get(0), getTargetPrefix());
            ctype.buildResultNodes();
            results.add(ctype);
        }
        return results;
    }

    /** Activates (namespace, typename) on this schema document. */
    public void activateType(String namespaceURI, String typeName) {
        if (StringUtils.isAnyBlank(namespaceURI, typeName))
            throw new IllegalArgumentException("Blank or null paramaters unsupported.");
        // check if namespaceURI is targetNamespace
        if (!formerNamespace.equals(namespaceURI)) return;
        // check if already activated
        if (activatedTypesMap.containsKey(typeName)) return;
        List<Pair<String, String>> collectedRelatedTypes = new ArrayList<>();
        // check if exists as ComplexType
        List<Node> complexNodes =
                xpathToList("//schema/complexType[@name='" + typeName + "']", this.document);
        if (!complexNodes.isEmpty()) {
            if (complexNodes.size() > 1)
                throw new IllegalStateException("Illegal ComplexType name repeating.");
            // save activate reference on map
            TypeMetadata tm = new TypeMetadata(typeName, XsdTypeType.COMPLEX);
            activatedTypesMap.put(typeName, tm);
            // check for extended/used type dependencies
            Element complexTypeElement = (Element) complexNodes.get(0);
            collectedRelatedTypes.addAll(typeDependencies(complexTypeElement));
        }
        // check if exists as SimpleType
        List<Node> simpleNodes =
                xpathToList("//schema/simpleType[@name='" + typeName + "']", this.document);
        if (!simpleNodes.isEmpty()) {
            if (simpleNodes.size() > 1)
                throw new IllegalStateException("Illegal SimpleType name repeating.");
            // save activate reference on map
            TypeMetadata tm = new TypeMetadata(typeName, XsdTypeType.SIMPLE);
            activatedTypesMap.put(typeName, tm);
            // check for extended/used type dependencies
            Element TypeElement = (Element) simpleNodes.get(0);
            collectedRelatedTypes.addAll(typeDependencies(TypeElement));
        }
        // check if exists as element
        List<Node> elementNodes =
                xpathToList("//schema/element[@name='" + typeName + "']", this.document);
        if (!elementNodes.isEmpty()) {
            if (elementNodes.size() > 1)
                throw new IllegalStateException("Illegal Element name repeating.");
            TypeMetadata tm = new TypeMetadata(typeName, XsdTypeType.ELEMENT);
            activatedTypesMap.put(typeName, tm);
            // check for extended/used type dependencies
            Element element = (Element) elementNodes.get(0);
            collectedRelatedTypes.addAll(typeDependencies(element));
        }
        // process type dependencies
        for (Pair<String, String> ep : collectedRelatedTypes) {
            activateTypeConsumer.accept(ep.getKey(), ep.getValue());
        }
    }

    /** Returns a list of (namespace, typename) of typeElement dependencies. */
    List<Pair<String, String>> typeDependencies(Element typeElement) {
        List<Pair<String, String>> result = new ArrayList<>();
        // get base type from extension element if exists
        List<Node> extensions = xpathToList("./complexContent/extension", typeElement);
        if (extensions != null && !extensions.isEmpty()) {
            Element extensionElement = (Element) extensions.get(0);
            // get the typename with (maybe) NS prefix
            String typeNameWithPrefix = extensionElement.getAttribute("base");
            // build the pair tuple and add to result list
            Pair<String, String> namePair = namePair(typeNameWithPrefix);
            if (namePair != null) result.add(namePair(typeNameWithPrefix));
        }
        // check child <element tags for other related types
        List<Node> childElements = childElements(typeElement).collect(Collectors.toList());
        for (Node childItem : childElements) {
            Node namedItem = childItem.getAttributes().getNamedItem("type");
            if (namedItem != null) {
                Pair<String, String> namePair = namePair(namedItem.getNodeValue());
                if (namePair != null) result.add(namePair);
            }
        }
        // check for child <restriction elements on simpleType elements
        List<Node> childRestrictions =
                childRestrictions(typeElement).collect(Collectors.toList());
        for (Node childItem : childRestrictions) {
            if (childItem instanceof Element) {
                Element el = (Element) childItem;
                String typeFound = el.getAttribute("base");
                Pair<String, String> namePair = namePair(typeFound);
                if (namePair != null) result.add(namePair);
            }
        }
        // check for unique/selector/@xpath on <element
        List<Node> childSelectors = childSelector(typeElement).collect(Collectors.toList());
        for (Node childItem : childSelectors) {
            Element el = (Element) childItem;
            String typeFound = el.getAttribute("xpath");
            if (StringUtils.isNotBlank(typeFound)) {
                typeFound = typeFound.replace(".//", "");
                Pair<String, String> namePair = namePair(typeFound);
                if (namePair != null) result.add(namePair);
            }
        }
        // check if current node is "Element" tag and chech 'type' attribute for activate it
        if (typeElement.getTagName().equals("xs:element")
                || typeElement.getTagName().equals("element")) {
            String type = typeElement.getAttribute("type");
            if (StringUtils.isNotBlank(type)) {
                Pair<String, String> namePair = namePair(type);
                if (namePair != null) result.add(namePair);
            }
        }
        return result;
    }

    private Pair<String, String> namePair(String typeNameWithPrefix) {
        if (StringUtils.isBlank(typeNameWithPrefix) || typeNameWithPrefix.startsWith("xs:"))
            return null;
        String prefix, localname;
        if (typeNameWithPrefix.contains(":")) {
            String[] parts = typeNameWithPrefix.split(Pattern.quote(":"));
            prefix = parts[0];
            localname = parts[1];
        } else {
            prefix = null;
            localname = typeNameWithPrefix;
        }
        // get the namespaceURI
        String namespaceURI;
        if (prefix == null) {
            // if prefix is null it is local namespaceURI
            namespaceURI = formerNamespace;
        } else {
            namespaceURI = getNamespaceURI(prefix);
            if (namespaceURI == null) {
                return null;
            }
        }
        return Pair.of(namespaceURI, localname);
    }

    private Stream<Node> childElements(Node node) {
        XPath xPath = getXpath();
        String query = "descendant::element";
        try {
            NodeList nodeList = (NodeList) xPath.evaluate(query, node, XPathConstants.NODESET);
            Stream<Node> nodeStream = toStream(nodeList);
            return nodeStream;
        } catch (XPathExpressionException e) {
            throw new RuntimeException(e);
        }
    }

    private Stream<Node> childRestrictions(Node node) {
        XPath xPath = getXpath();
        String query = "descendant::restriction";
        try {
            NodeList nodeList = (NodeList) xPath.evaluate(query, node, XPathConstants.NODESET);
            Stream<Node> nodeStream = toStream(nodeList);
            return nodeStream;
        } catch (XPathExpressionException e) {
            throw new RuntimeException(e);
        }
    }

    private Stream<Node> childSelector(Node node) {
        XPath xPath = getXpath();
        String query = "descendant::selector";
        try {
            NodeList nodeList = (NodeList) xPath.evaluate(query, node, XPathConstants.NODESET);
            Stream<Node> nodeStream = toStream(nodeList);
            return nodeStream;
        } catch (XPathExpressionException e) {
            throw new RuntimeException(e);
        }
    }

    public String getNamespaceURI(String prefix) {
        return this.prefixedNamespacesMap.get(prefix);
    }

    public String getPrefix(String namespaceURI) {
        Optional<Entry<String, String>> matchingEntry =
                this.prefixedNamespacesMap.entrySet().stream()
                        .filter(es -> Objects.equals(es.getValue(), namespaceURI))
                        .findFirst();
        if (matchingEntry.isPresent()) {
            return matchingEntry.get().getKey();
        } else {
            return null;
        }
    }

    public String getTargetPrefix() {
        return getPrefix(this.formerNamespace);
    }

    private void buildPrefixesMap() {
        NamedNodeMap attributesNodeMap = xpathToElement("/schema", document).getAttributes();
        for (int i = 0; i < attributesNodeMap.getLength(); i++) {
            Attr item = (Attr) attributesNodeMap.item(i);
            if ("xmlns".equals(prefixFromString(item.getName()))) {
                this.prefixedNamespacesMap.put(
                        localNameFromString(item.getName()), item.getValue());
            }
        }
    }

    private String prefixFromString(String name) {
        String[] parts = name.split(Pattern.quote(":"));
        if (parts.length > 0) {
            return parts[0];
        } else {
            return null;
        }
    }

    private String localNameFromString(String name) {
        String[] parts = name.split(Pattern.quote(":"));
        if (parts.length > 1) {
            return parts[1];
        } else {
            return null;
        }
    }

    protected static XPath getXpath() {
        XPath xPath = XPathFactory.newInstance().newXPath();
        return xPath;
    }

    static List<Node> xpathToList(String xpath, Node node) {
        XPath xPath = getXpath();
        try {
            NodeList nodeList =
                    (NodeList) xPath.compile(xpath).evaluate(node, XPathConstants.NODESET);
            List<Node> nodes = toStream(nodeList).collect(Collectors.toCollection(ArrayList::new));
            return nodes;
        } catch (XPathExpressionException e) {
            throw new RuntimeException(e);
        }
    }

    static Element xpathToElement(String xpath, Document doc) {
        List<Node> nodesList = xpathToList(xpath, doc);
        if (nodesList.isEmpty() || !(nodesList.get(0) instanceof Element)) {
            return null;
        } else {
            return (Element) nodesList.get(0);
        }
    }

    protected static Stream<Node> toStream(NodeList nodeList) {
        Stream<Node> nodeStream = IntStream.range(0, nodeList.getLength()).mapToObj(nodeList::item);
        return nodeStream;
    }

    public String getFormerNamespace() {
        return formerNamespace;
    }

    public String getTargetNamespace() {
        return targetNamespace;
    }

    public String getFilename() {
        String separator = File.separator;
        String[] parts = filename.split(Pattern.quote(separator));
        // return only the filename, without paths
        return parts[parts.length - 1];
    }

    public Map<String, TypeMetadata> getActivatedTypesMap() {
        return activatedTypesMap;
    }

    /** Activates for convertion all complex types found on schema file. */
    public void activateAllTypes() {
        List<Node> complexNodes = xpathToList("//schema/complexType[@name]", this.document);
        for (Node node : complexNodes) {
            Element el = (Element) node;
            String typeName = el.getAttribute("name");
            activateType(this.formerNamespace, typeName);
        }
    }
}
