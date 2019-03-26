package org.geosolutions.datexgml;

import java.util.List;
import java.util.stream.Collectors;

import org.w3c.dom.Element;
import org.w3c.dom.Node;

/**
 * Representation and convert processor for a complex type on schemas.
 * 
 * @see GmlConverter
 */
public class ComplexType {

    public static final String TYPE_SUFIX = "Type";
    public static final String PROPERTY_TYPE_SUFIX = "PropertyType";
    public static final String EXTENSION_TYPE_SUFIX = "Extension";

    private String encodePrefix = GmlConverter.DATEX_PREFIX;

    private Node node;
    private Node complexTypeNode;
    private Node complexPropertyTypeNode;
    private Node elementNode;

    /**
     * Main constructor.
     *
     * @param node schema node to process.
     */
    public ComplexType(Node node) {
        super();
        this.node = node;
    }

    public ComplexType(Node node, String encodePrefix) {
        super();
        this.node = node;
        this.encodePrefix = encodePrefix;
    }

    public void buildResultNodes() {
        // type node
        // deep clone only if extends another type
        final boolean extendsAnother = extendsAnotherComplex(node);
        complexTypeNode = node.cloneNode(extendsAnother);
        final String originalName = node.getAttributes().getNamedItem("name").getNodeValue();
        final String typeName = originalName + TYPE_SUFIX;
        complexTypeNode.getAttributes().getNamedItem("name").setNodeValue(typeName);
        // if dont extends another type, build internal GML extension
        if (!extendsAnother) {
            buildExtendedContent();
        }
        buildPropertyTypeNode(originalName);
        buildElementNode(originalName);
    }

    /**
     * Builds the resulting element node.
     * 
     * @param name Value for "name" attribute.
     */
    protected void buildElementNode(String name) {
        // element
        Element element = node.getOwnerDocument().createElementNS(GmlConverter.XS_NS, "xs:element");
        element.setAttribute("name", name);
        element.setAttribute("type", encodePrefix + ":" + name + TYPE_SUFIX);
        element.setAttribute("substitutionGroup", "gml:AbstractFeature");
        elementNode = element;
    }

    protected void buildPropertyTypeNode(String name) {
        // complexType
        Element ptNode = node.getOwnerDocument().createElementNS(GmlConverter.XS_NS, "xs:complexType");
        ptNode.setAttribute("name", name + PROPERTY_TYPE_SUFIX);
        complexPropertyTypeNode = ptNode;
        // sequence
        Element sequence = node.getOwnerDocument().createElementNS(GmlConverter.XS_NS, "xs:sequence");
        sequence.setAttribute("minOccurs", "0");
        ptNode.appendChild(sequence);
        // element
        Element element = node.getOwnerDocument().createElementNS(GmlConverter.XS_NS, "xs:element");
        element.setAttribute("ref", encodePrefix + ":" + name);
        sequence.appendChild(element);
        // complexType/attributeGroup
        Element attributeGroup = node.getOwnerDocument().createElementNS(GmlConverter.XS_NS, "xs:attributeGroup");
        attributeGroup.setAttribute("ref", "gml:AssociationAttributeGroup");
        ptNode.appendChild(attributeGroup);
    }

    /**
     * Builds internal GML extension.
     */
    protected void buildExtendedContent() {
        List<Node> childs = GmlConverter.xpath(node, "child::*").collect(Collectors.toList());
        // add annotations to result type
        childs.stream()
                .filter(c -> c.getNodeName().equals("annotation"))
                .findFirst()
                .ifPresent(
                        c -> {
                            complexTypeNode.appendChild(c.cloneNode(true));
                        });
        // delete abstract if have
        if (((Element) complexTypeNode).hasAttribute("abstract")) {
            ((Element) complexTypeNode).removeAttribute("abstract");
        }
        // create /complexContent/extension nodes
        Element complexContentEl = node.getOwnerDocument().createElementNS(GmlConverter.XS_NS, "xs:complexContent");
        complexTypeNode.appendChild(complexContentEl);
        Element extensionEl = node.getOwnerDocument().createElementNS(GmlConverter.XS_NS, "xs:extension");
        extensionEl.setAttribute("base", "gml:AbstractFeatureType");
        complexContentEl.appendChild(extensionEl);
        // append original childs
        childs.stream().filter(c -> !c.getNodeName().equals("annotation")).forEach(c -> {
            Node cloned = c.cloneNode(true);
            extensionEl.appendChild(cloned);
        });
    }

    /**
     * Detects if node is an extension.
     * 
     * @param node Node to check
     * @return true if it is an extension, otherwise false.
     */
    boolean extendsAnotherComplex(Node node) {
        if (GmlConverter.xpath(node, "./complexContent/extension").findFirst().isPresent())
            return true;
        return false;
    }

    public Node getNode() {
        return node;
    }

    public void setNode(Node node) {
        this.node = node;
    }

    Node getComplexTypeNode() {
        return complexTypeNode;
    }

    void setComplexTypeNode(Node complexTypeNode) {
        this.complexTypeNode = complexTypeNode;
    }

    Node getComplexPropertyTypeNode() {
        return complexPropertyTypeNode;
    }

    void setComplexPropertyTypeNode(Node complexPropertyTypeNode) {
        this.complexPropertyTypeNode = complexPropertyTypeNode;
    }

    Node getElementNode() {
        return elementNode;
    }

    void setElementNode(Node elementNode) {
        this.elementNode = elementNode;
    }

    public String getEncodePrefix() {
        return encodePrefix;
    }

    public void setEncodePrefix(String encodePrefix) {
        this.encodePrefix = encodePrefix;
    }
}
