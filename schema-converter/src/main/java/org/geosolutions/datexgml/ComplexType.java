package org.geosolutions.datexgml;

import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

public class ComplexType {
    
    public static final String TYPE_SUFIX = "Type";
    public static final String PROPERTY_TYPE_SUFIX = "PropertyType";
    public static final String EXTENSION_TYPE_SUFIX = "Extension";
    
    private GmlConverter converter;
    private Node node;
    private Node complexTypeNode;
    private Node complexPropertyTypeNode;
    private Node elementNode;

    public ComplexType(Node node, GmlConverter converter) {
        super();
        this.converter = converter;
        this.node = node;
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
        if(!extendsAnother) {
            buildExtendedContent();
        }
        buildPropertyTypeNode(originalName);
        buildElementNode(originalName);
    }
    
    protected void buildElementNode(String name) {
        // element
        Element element = node.getOwnerDocument()
                .createElementNS(GmlConverter.XS_NS, "xs:element");
        element.setAttribute("name", name);
        element.setAttribute("type", GmlConverter.DATEX_PREFIX + ":" + name + TYPE_SUFIX);
        element.setAttribute("substitutionGroup", "gml:AbstractFeature");
        elementNode = element;
    }
    
    protected void buildPropertyTypeNode(String name) {
        // complexType
        Element ptNode = node.getOwnerDocument()
                .createElementNS(GmlConverter.XS_NS, "xs:complexType");
        ptNode.setAttribute("name", name + PROPERTY_TYPE_SUFIX);
        complexPropertyTypeNode = ptNode;
        // sequence
        Element sequence = node.getOwnerDocument()
                .createElementNS(GmlConverter.XS_NS, "xs:sequence");
        sequence.setAttribute("minOccurs", "0");
        ptNode.appendChild(sequence);
        // element
        Element element = node.getOwnerDocument()
                .createElementNS(GmlConverter.XS_NS, "xs:element");
        element.setAttribute("ref", GmlConverter.DATEX_PREFIX + ":" + name);
        sequence.appendChild(element);
        // complexType/attributeGroup 
        Element attributeGroup = node.getOwnerDocument()
                .createElementNS(GmlConverter.XS_NS, "xs:attributeGroup");
        attributeGroup.setAttribute("ref", "gml:AssociationAttributeGroup");
        ptNode.appendChild(attributeGroup);
    }

    protected void buildExtendedContent() {
        List<Node> childs = GmlConverter.xpath(node, "child::*")
                .collect(Collectors.toList());
        // add annontations to result type
        childs.stream().filter(c -> c.getNodeName().equals("annotation"))
            .findFirst().ifPresent(c -> {
                complexTypeNode.appendChild(c.cloneNode(true));
            });
        // delete abstract if have
        if(((Element)complexTypeNode).hasAttribute("abstract")) {
            ((Element)complexTypeNode).removeAttribute("abstract");
        }
        // create /complexContent/extension nodes
        Element complexContentEl = node.getOwnerDocument()
                .createElementNS(GmlConverter.XS_NS,"xs:complexContent");
        complexTypeNode.appendChild(complexContentEl);
        Element extensionEl = node.getOwnerDocument()
                .createElementNS(GmlConverter.XS_NS,"xs:extension");
        extensionEl.setAttribute("base", "gml:AbstractFeatureType");
        complexContentEl.appendChild(extensionEl);
        // append original childs
        childs.stream().filter(c -> !c.getNodeName().equals("annotation"))
            .forEach(c -> {
                Node cloned = c.cloneNode(true);
                extensionEl.appendChild(cloned);
            });
    }
    
    boolean extendsAnotherComplex(Node node) {
        if(GmlConverter.xpath(node, "./complexContent/extension").findFirst().isPresent())
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
    
    
    
}
