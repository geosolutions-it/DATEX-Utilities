package org.geosolutions.datexgml;

import java.io.File;
import java.io.IOException;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;
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

import org.apache.ws.commons.util.NamespaceContextImpl;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

/**
 * Generates a valid converted GML schema with given root type names and target
 * namespace.
 */
public class GmlConverter {

    public static final String XMLNS = "http://www.w3.org/2001/XMLSchema";
    public static final String XS_NS = "http://www.w3.org/2001/XMLSchema";
    public static final String DATEX_NS = "http://datex2.eu/schema/2/2_0";
    public static final String DATEX_PREFIX = "D2LogicalModel";

    private List<File> files;
    private List<Document> docs;
    private Document resultDoc;
    private Map<String, ComplexType> complexMap = new HashMap<>();
    private List<Node> simpleTypes;
    private List<String> rootTypesNames;
    private String targetNamespace;

    private NamespacesController namespacesController;

    /**
     * Main constructor.
     *
     * @param file original schema file to convert.
     * @param rootTypesNames main types to start to visit.
     * @param targetNamespace target namespace to use on final generated schema.
     */
    public GmlConverter(List<File> files, List<String> rootTypesNames, String targetNamespace) {
        super();
        this.files = files;
        docs = load();
        this.rootTypesNames = rootTypesNames;
        this.targetNamespace = targetNamespace;
        this.namespacesController = new NamespacesController(docs);
    }

    public GmlConverter convert() {
        createNewDocument();
        buildMaps();
        convertComplexTypes();
        buildXmlResult();
        return this;
    }

    protected void buildXmlResult() {
        Node schema = resultDoc.getFirstChild();
        complexMap.entrySet().forEach(x -> {
            Element complexNode = (Element) resultDoc.importNode(x.getValue().getComplexTypeNode(), true);
            schema.appendChild(complexNode);
            // convert internal element type
            processElements(complexNode);
            // append propertyType and Element
            schema.appendChild(resultDoc.importNode(x.getValue().getComplexPropertyTypeNode(), true));
            schema.appendChild(resultDoc.importNode(x.getValue().getElementNode(), true));
        });
        simpleTypes.forEach(x -> {
            schema.appendChild(resultDoc.importNode(x, true));
        });
    }

    protected void processElements(Element complexNode) {
        processElements(complexNode, x -> this.getComplexMap().containsKey(x));
    }

    static void processElements(Element complexNode, Predicate<String> existsComplexType) {
        // modify elements type
        xpath(complexNode, "descendant::element")
                .forEach(
                        e -> {
                            Element element = (Element) e;
                            final String typeName = cleanQname(element.getAttribute("type"));
                            // if type is a GML geometry definition, replace with real gml type
                            if (geomTypesToReplace().contains(typeName)) {
                                element.setAttribute("type", "gml:GeometryPropertyType");
                                return;
                            }
                            // if type is indexed on dependents complex types map
                            if (existsComplexType.test(typeName)) {
                                // add propertyType to type name
                                /*element.setAttribute(
                                        "type",
                                        element.getAttribute("type")
                                                + ComplexType.PROPERTY_TYPE_SUFIX);*/
                            }
                        });
        // convert attributes into elements
        List<Node> attributes = xpath(complexNode, "descendant::attribute").collect(Collectors.toList());
        if (!attributes.isEmpty()) {
            Element complexMain = (Element) attributes.get(0).getParentNode();
            // get existing sequence element, or create it if don't exists
            Element sequence = (Element) xpath(complexMain, "child::sequence").findFirst().orElseGet(() -> {
                Element sequence1 = complexNode.getOwnerDocument().createElementNS(XS_NS, "xs:sequence");
                complexMain.appendChild(sequence1);
                return sequence1;
            });
            // for every atrribute, convert it to an element tag within sequence
            attributes.forEach(a -> {
                // create and append element
                Element attr = (Element) a;
                String name = attr.getAttribute("name");
                String type = attr.getAttribute("type");
                Element element = complexNode.getOwnerDocument().createElementNS(XS_NS, "xs:element");
                element.setAttribute("name", name);
                element.setAttribute("type", type);
                // minOccurs="0"
                element.setAttribute("minOccurs", "0");
                sequence.appendChild(element);
                // delete attribute
                complexMain.removeChild(attr);
            });
        }
    }

    protected void createNewDocument() {
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder;
        try {
            builder = dbf.newDocumentBuilder();
            resultDoc = builder.newDocument();

            // Node schemaNode = doc.getFirstChild().cloneNode(false);
            Element schemaElement = resultDoc.createElement("xs:schema");
            // Add elementFormDefault="qualified" attributeFormDefault="unqualified" version="3.0"
            // xmlns:xs="http://www.w3.org/2001/XMLSchema"
            schemaElement.setAttribute("elementFormDefault", "qualified");
            schemaElement.setAttribute("attributeFormDefault", "unqualified");
            schemaElement.setAttribute("version", "3.0");
            schemaElement.setAttribute("xmlns:xs", "http://www.w3.org/2001/XMLSchema");

            // add imported namespaces
            this.namespacesController.addNamespacesAttributes(schemaElement);

            // resultDoc.adoptNode(schemaNode);
            resultDoc.appendChild(schemaElement);
            // xmlns:gml="http://www.opengis.net/gml/3.2"
            schemaElement.setAttribute("xmlns:gml", "http://www.opengis.net/gml/3.2");
            // targetNamespace
            schemaElement.setAttribute("targetNamespace", targetNamespace);
            // xmlns:D2LogicalModel
            schemaElement.setAttribute("xmlns:D2LogicalModel", targetNamespace);
            // add import gml schema
            Element importElement = resultDoc.createElementNS(XS_NS, "xs:import");
            importElement.setAttribute("namespace", "http://www.opengis.net/gml/3.2");
            importElement.setAttribute("schemaLocation", "http://schemas.opengis.net/gml/3.2.1/gml.xsd");
            schemaElement.appendChild(importElement);
        } catch (ParserConfigurationException e) {
            throw new RuntimeException(e);
        }
    }

    protected void convertComplexTypes() {
        complexMap.entrySet().forEach(e -> {
            e.getValue().buildResultNodes();
        });
    }

    protected void buildMaps() {
        rootTypesNames.forEach(x -> treeToComplexMap(x));
        buildSimpleTypesList();
    }

    protected void buildSimpleTypesList() {
        simpleTypes = findSimpleTypes();
    }

    protected List<Node> findSimpleTypes() {
        return xpathAllDocuments("/schema/simpleType");
    }

    /**
     * Creates a map with [name -> wrapper(node)] pattern
     */
    protected void treeToComplexMap(String rootName) {
        // search for complex types
        Optional<Node> nodeOpt = getComplexTypeNode(rootName);
        if (!nodeOpt.isPresent())
            return;
        Node rootNode = nodeOpt.get();
        // add the root node
        addToMap(rootName, rootNode);
        // if extends another type -> map that type:
        Optional<Node> extensionNode = xpath(rootNode, "./complexContent/extension").findFirst();
        extensionNode.ifPresent(e -> {
            Element ex = (Element) e;
            String exType = ex.getAttribute("base").replace("D2LogicalModel:", "");
            treeToComplexMap(exType);
        });
        // check all elements
        childElements(rootNode).forEach(n -> {
            Node type = n.getAttributes().getNamedItem("type");
            // Node ref = n.getAttributes().getNamedItem("ref");
            if (type != null) {
                String typeName = cleanQname(type.getNodeValue());
                if (!typeName.contains(":")) {
                    treeToComplexMap(typeName);
                }
            }
        });
    }

    static String cleanQname(String qname) {
        if (qname == null) return null;
        if (qname.contains(":")) {
            String[] parts = qname.split(Pattern.quote(":"));
            return parts[1];
        } else {
            return qname;
        }
    }

    protected Stream<Node> childElements(Node node) {
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

    protected static Stream<Node> xpath(Node node, String xpath) {
        XPath xPath = getXpath();
        try {
            NodeList nodeList = (NodeList) xPath.compile(xpath).evaluate(node, XPathConstants.NODESET);
            Stream<Node> nodeStream = toStream(nodeList);
            return nodeStream;
        } catch (XPathExpressionException e) {
            throw new RuntimeException(e);
        }
    }

    protected List<Node> xpathAllDocuments(String xpath) {
        List<Node> nodesList = new ArrayList<Node>();
        for (Document edoc : this.docs) {
            List<Node> resultNodes = xpathDocument(xpath, edoc);
            nodesList.addAll(resultNodes);
        }
        return nodesList;
    }

    List<Node> xpathDocument(String xpath, Document doc) {
        XPath xPath = getXpath();
        try {
            NodeList nodeList =
                    (NodeList) xPath.compile(xpath).evaluate(doc, XPathConstants.NODESET);
            List<Node> nodes = toStream(nodeList).collect(Collectors.toCollection(ArrayList::new));
            return nodes;
        } catch (XPathExpressionException e) {
            throw new RuntimeException(e);
        }
    }

    protected void addToMap(String name, Node node) {
        if (!complexMap.containsKey(name)) {
            complexMap.put(name, new ComplexType(node));
        }
    }

    protected Node getSituationRootNode() {
        return getComplexTypeNode("Situation").get();
    }

    protected Optional<Node> getComplexTypeNode(String name) {
        for (Document edoc : this.docs) {
            Optional<Node> complexTypeNode = getComplexTypeNode(name, edoc);
            if (complexTypeNode.isPresent()) {
                return complexTypeNode;
            }
        }
        return Optional.empty();
    }

    protected Optional<Node> getComplexTypeNode(String name, Document doc) {
        XPath xPath = getXpath();
        String query = "//schema/complexType[@name='" + name + "']";
        try {
            NodeList nodeList = (NodeList) xPath.compile(query).evaluate(doc, XPathConstants.NODESET);
            Stream<Node> nodeStream = toStream(nodeList);
            return nodeStream.findFirst();
        } catch (XPathExpressionException e) {
            throw new RuntimeException(e);
        }
    }

    protected static Stream<Node> toStream(NodeList nodeList) {
        Stream<Node> nodeStream = IntStream.range(0, nodeList.getLength()).mapToObj(nodeList::item);
        return nodeStream;
    }

    protected static XPath getXpath() {
        NamespaceContextImpl nsContext = new NamespaceContextImpl();
        nsContext.startPrefixMapping("", "http://www.w3.org/2001/XMLSchema");
        XPath xPath = XPathFactory.newInstance().newXPath();
        xPath.setNamespaceContext(nsContext);
        return xPath;
    }

    protected List<Document> load() {
        List<Document> documents = new ArrayList<>();
        for (File efile : files) {
            try {
                DocumentBuilderFactory docFactory = DocumentBuilderFactory.newInstance();
                DocumentBuilder docBuilder;
                docBuilder = docFactory.newDocumentBuilder();
                Document doc = docBuilder.parse(efile);
                documents.add(doc);
            } catch (ParserConfigurationException | SAXException | IOException e) {
                throw new RuntimeException(e);
            }
        }
        return documents;
    }

    Map<String, ComplexType> getComplexMap() {
        return complexMap;
    }

    List<Node> getSimpleTypes() {
        return simpleTypes;
    }

    public Document getResultDoc() {
        return resultDoc;
    }

    public static List<String> geomTypesToReplace() {
        return Arrays.asList("GMLLinearRing", "GMLLineString", "GMLMultiPolygon", "GMLPolygon");
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

}
