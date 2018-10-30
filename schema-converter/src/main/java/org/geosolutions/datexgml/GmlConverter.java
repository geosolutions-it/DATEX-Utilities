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
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
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

public class GmlConverter {
    
    public static final String DATEX_NS = "http://datex2.eu/schema/2/2_0";
    public static final String DATEX_PREFIX = "D2LogicalModel";
    public static final List<String> ROOT_NAMES = Arrays.asList("Situation");
    
    private File file;
    private Document doc;
    private Document resultDoc;
    private Map<String, ComplexType> complexMap = new HashMap<>();
    private List<Node> simpleTypes;
    
    public GmlConverter(File file) {
        super();
        this.file = file;
        try {
            doc = load();
        } catch (ParserConfigurationException | SAXException | IOException e) {
            throw new RuntimeException(e);
        }
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
            xpath(complexNode, "descendant::element").forEach(e -> {
                Element element = (Element) e;
                final String typeName = element.getAttribute("type")
                        .replace("D2LogicalModel:", "");
                // if type is a GML geometry definition, replace with real gml type
                if(geomTypesToReplace().contains(typeName)) {
                    element.setAttribute("type", "gml:GeometryPropertyType");
                    return;
                }
                // if type is indexed on dependents complex types map
                if(getComplexMap().containsKey(typeName)) {
                    // add propertyType to type name
                    element.setAttribute("type", element.getAttribute("type") 
                            + ComplexType.PROPERTY_TYPE_SUFIX);
                }
            });
            // append propertyType and Element
            schema.appendChild(resultDoc.importNode(x.getValue().getComplexPropertyTypeNode(), true));
            schema.appendChild(resultDoc.importNode(x.getValue().getElementNode(), true));
        });
        simpleTypes.forEach(x -> {
            schema.appendChild(resultDoc.importNode(x, true));
        });
    }
    
    protected void createNewDocument() {
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder;
        try {
            builder = dbf.newDocumentBuilder();
            resultDoc = builder.newDocument();
            Node schemaNode = doc.getFirstChild().cloneNode(false);
            resultDoc.adoptNode(schemaNode);
            resultDoc.appendChild(schemaNode);
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
        ROOT_NAMES.forEach(x -> treeToComplexMap(x));
        buildSimpleTypesList();
    }
    
    protected void buildSimpleTypesList() {
        simpleTypes = findSimpleTypes();
    }

    protected List<Node> findSimpleTypes() {
        return xpathRootDocument("/schema/simpleType").collect(Collectors.toList());
    }
    
    /**
     * Creates a map with [name -> wrapper(node)] pattern
     */
    protected void treeToComplexMap(String rootName) {
        // search for complex types
        Optional<Node> nodeOpt = getComplexTypeNode(rootName);
        if(!nodeOpt.isPresent()) return;
        Node rootNode = nodeOpt.get();
        // add the root node
        addToMap(rootName, rootNode);
        // check all elements
        childElements(rootNode).forEach(n -> {
            Node type = n.getAttributes().getNamedItem("type");
            // Node ref = n.getAttributes().getNamedItem("ref");
            if(type!=null) {
                String typeName = cleanQname(type.getNodeValue());
                if(!typeName.contains(":")) {
                    treeToComplexMap(typeName);
                }
            }
        });
    }
    
    private String cleanQname(String qname) {
        return qname.replace("D2LogicalModel:", "");
    }
    
    protected Stream<Node> childElements(Node node){
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
    
    protected static Stream<Node> xpath(Node node, String xpath){
        XPath xPath = getXpath();
        try {
            NodeList nodeList = (NodeList) xPath.compile(xpath).evaluate(node, XPathConstants.NODESET);
            Stream<Node> nodeStream = toStream(nodeList);
            return nodeStream;
        } catch (XPathExpressionException e) {
            throw new RuntimeException(e);
        }
    }
    
    protected Stream<Node> xpathRootDocument(String xpath){
        XPath xPath = getXpath();
        try {
            NodeList nodeList = (NodeList) xPath.compile(xpath).evaluate(doc, XPathConstants.NODESET);
            Stream<Node> nodeStream = toStream(nodeList);
            return nodeStream;
        } catch (XPathExpressionException e) {
            throw new RuntimeException(e);
        }
    }
    
    protected void addToMap(String name, Node node) {
        if (!complexMap.containsKey(name)) {
            complexMap.put(name, new ComplexType(node, this));
        }
    }
    
    protected Node getSituationRootNode() {
       return getComplexTypeNode("Situation").get();
    }
    
    protected Optional<Node> getComplexTypeNode(String name) {
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
    
    protected static Stream<Node> toStream(NodeList nodeList){
        Stream<Node> nodeStream = IntStream.range(0, nodeList.getLength())
                .mapToObj(nodeList::item);
        return nodeStream;
    }
    
    protected static XPath getXpath() {
        NamespaceContextImpl nsContext = new NamespaceContextImpl();
        nsContext.startPrefixMapping("", "http://www.w3.org/2001/XMLSchema");
        XPath xPath = XPathFactory.newInstance().newXPath();
        xPath.setNamespaceContext(nsContext);
        return xPath;
    }
    
    protected Document load() throws ParserConfigurationException, SAXException, IOException {
        DocumentBuilderFactory docFactory = DocumentBuilderFactory.newInstance();
        DocumentBuilder docBuilder = docFactory.newDocumentBuilder();
        Document doc = docBuilder.parse(file);
        return doc;
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
    
    public static List<String> geomTypesToReplace(){
        return Arrays.asList(
                "GMLLinearRing",
                "GMLLineString",
                "GMLMultiPolygon",
                "GMLPolygon");
    }
    
    public String getResultDocAsString() {
        try {
            TransformerFactory tf = TransformerFactory.newInstance();
            Transformer transformer;
            transformer = tf.newTransformer();
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
