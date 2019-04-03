package it.geosolutions.xsd2gml;

import static it.geosolutions.xsd2gml.Utils.toElement;
import java.util.List;
import javax.xml.namespace.QName;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

public final class Xsd2Gml {

    static final String GML_NAMESPACE_32 = "http://www.opengis.net/gml/3.2";
    static final String XML_NAMESPACE = "http://www.w3.org/2001/XMLSchema";

    private final Document outputSchema;
    private final Element outputRootNode;

    public Xsd2Gml(Document inputSchema, QName targetNamespace, List<String> startingTypesNames) {
        // create and initiate the GML output schema document
        outputSchema = createOutputSchema();
        outputRootNode = initOutputSchema(inputSchema, outputSchema, targetNamespace);
        // walk the schema to get the relevant types and relations
        SchemaWalker walker = new SchemaWalker(inputSchema, startingTypesNames);
        walker.getRootComplexTypes()
                .forEach(
                        (complexType, relatedTypes) -> {
                            ComplexTypeConverter converter =
                                    new ComplexTypeConverter(
                                            inputSchema, complexType, relatedTypes);
                            converter.toGmlFeature(outputSchema, outputRootNode, targetNamespace);
                        });
        walker.getRootSimpleTypes()
                .forEach(
                        simpleType -> {
                            Node node = simpleType.cloneNode(true);
                            outputSchema.adoptNode(node);
                            outputRootNode.appendChild(node);
                        });
    }

    static Document createOutputSchema() {
        try {
            // build the output document that will contain the generated GML schema
            DocumentBuilderFactory documentBuilderFactory = DocumentBuilderFactory.newInstance();
            DocumentBuilder documentBuilder = documentBuilderFactory.newDocumentBuilder();
            return documentBuilder.newDocument();
        } catch (Exception exception) {
            // something bad happen when creating the document
            throw new RuntimeException("Error creating output schema.", exception);
        }
    }

    static Element initOutputSchema(
            Document inputSchema, Document outputSchema, QName targetNamespace) {
        // copy the root node (schema) of the input schema to the output schema
        Element outputRootNode =
                toElement(outputSchema.importNode(inputSchema.getFirstChild(), false));
        // get ride of the old namespace
        outputRootNode.removeAttribute("xmlns:D2LogicalModel");
        outputRootNode.removeAttribute("targetNamespace");
        outputSchema.appendChild(outputRootNode);
        // set the needed namespaces and target namespaces
        outputRootNode.setAttribute("xmlns:gml", GML_NAMESPACE_32);
        outputRootNode.setAttribute(
                "xmlns:" + targetNamespace.getLocalPart(), targetNamespace.getNamespaceURI());
        outputRootNode.setAttribute("targetNamespace", targetNamespace.getNamespaceURI());
        // import GML 3.2 schema
        Element gmlImport = outputSchema.createElementNS(XML_NAMESPACE, "xs:import");
        gmlImport.setAttribute("namespace", GML_NAMESPACE_32);
        gmlImport.setAttribute("schemaLocation", "http://schemas.opengis.net/gml/3.2.1/gml.xsd");
        outputRootNode.appendChild(gmlImport);
        // return the root node of the output schema
        return outputRootNode;
    }

    Document getGmlSchema() {
        // add multilingual string type custom nodes
        /*
            <xs:complexType name="MultilingualStringType">
                <xs:complexContent>
                    <xs:extension base="gml:AbstractFeatureType">
                        <xs:sequence>
                            <xs:element maxOccurs="1" name="value" type="xs:string"/>
                            <xs:element maxOccurs="1" name="lang" type="xs:language"/>
                        </xs:sequence>
                    </xs:extension>
                </xs:complexContent>
            </xs:complexType>
            <xs:element name="MultilingualString" substitutionGroup="gml:AbstractFeature" type="npra:MultilingualStringType"/>
            <xs:complexType name="MultilingualStringPropertyType">
                <xs:sequence minOccurs="0">
                    <xs:element ref="npra:MultilingualString"/>
                </xs:sequence>
                <xs:attributeGroup ref="gml:AssociationAttributeGroup"/>
            </xs:complexType>
        */

        // create the type node
        Element complexType = outputSchema.createElementNS(XML_NAMESPACE, "xs:complexType");
        complexType.setAttribute("name", "MultilingualStringType");
        Element complexContent = outputSchema.createElementNS(XML_NAMESPACE, "xs:complexContent");
        Element extension = outputSchema.createElementNS(XML_NAMESPACE, "xs:extension");
        extension.setAttribute("base", "gml:AbstractFeatureType");
        Element sequence = outputSchema.createElementNS(XML_NAMESPACE, "xs:sequence");
        // link all elements together and append them to the output schema
        outputRootNode.appendChild(complexType);
        complexType.appendChild(complexContent);
        complexContent.appendChild(extension);
        extension.appendChild(sequence);
        // create elements
        Element element = outputSchema.createElementNS(XML_NAMESPACE, "xs:element");
        element.setAttribute("name", "value");
        element.setAttribute("maxOccurs", "1");
        element.setAttribute("type", "xs:string");
        sequence.appendChild(element);
        element = outputSchema.createElementNS(XML_NAMESPACE, "xs:element");
        element.setAttribute("name", "lang");
        element.setAttribute("maxOccurs", "1");
        element.setAttribute("type", "xs:language");
        sequence.appendChild(element);
        element = outputSchema.createElementNS(XML_NAMESPACE, "xs:element");
        element.setAttribute("name", "MultilingualString");
        element.setAttribute("substitutionGroup", "gml:AbstractFeature");
        element.setAttribute("type", "npra:MultilingualStringType");
        outputRootNode.appendChild(element);
        // create and append property type
        complexType = outputSchema.createElementNS(XML_NAMESPACE, "xs:complexType");
        complexType.setAttribute("name", "MultilingualStringPropertyType");
        outputRootNode.appendChild(complexType);
        sequence = outputSchema.createElementNS(XML_NAMESPACE, "xs:sequence");
        sequence.setAttribute("minOccurs", "0");
        complexType.appendChild(sequence);
        element = outputSchema.createElementNS(XML_NAMESPACE, "xs:element");
        element.setAttribute("ref", "npra:MultilingualString");
        sequence.appendChild(element);
        Element group = outputSchema.createElementNS(XML_NAMESPACE, "xs:attributeGroup");
        group.setAttribute("ref", "gml:AssociationAttributeGroup");
        complexType.appendChild(group);
        return outputSchema;
    }
}
