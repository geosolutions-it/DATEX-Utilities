package it.geosolutions.xsd2gml;

import static it.geosolutions.xsd2gml.Utils.extractUnqualifiedTypeName;
import static it.geosolutions.xsd2gml.Utils.getName;
import static it.geosolutions.xsd2gml.Utils.getPropertyTypeName;
import static it.geosolutions.xsd2gml.Utils.getTypeName;
import static it.geosolutions.xsd2gml.Utils.isSimpleContent;
import static it.geosolutions.xsd2gml.Utils.qualify;
import static it.geosolutions.xsd2gml.Xsd2Gml.XML_NAMESPACE;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.xml.namespace.QName;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

final class ComplexTypeConverter {

    private static final String SIMPLE_TYPE_SUFIX = "SimpleType";

    private static final Logger LOGGER = Logger.getLogger(ComplexTypeConverter.class.getName());

    private final Document inputSchema;
    private final Element typeDefinition;
    private final Set<Element> relatedTypes;

    // non qualified name of this complex type
    private final String name;

    ComplexTypeConverter(Document inputSchema, Element typeDefinition, Set<Element> relatedTypes) {
        this.inputSchema = inputSchema;
        this.typeDefinition = typeDefinition;
        this.relatedTypes = relatedTypes;
        name = extractUnqualifiedTypeName(typeDefinition, "name");
    }

    /**
     * Converts this complex type to a GML feature, a GML valid complex type will be declared as
     * well a property type that cna be used for features chaining. A cocnrete instance (element)
     * will also be declared.
     */
    void toGmlFeature(Document outputSchema, Element outputSchemaRootNode, QName targetNamespace) {
        addGmlFeatureConcreteElement(outputSchema, outputSchemaRootNode, targetNamespace);
	addGmlFeatureType(outputSchema, outputSchemaRootNode, targetNamespace);
        addGmlFeaturePropertyElement(outputSchema, outputSchemaRootNode, targetNamespace);
    }

    /**
     * Coverts this complex feature type to a GML feature. Properties from all its super types and
     * extensions will be merged in the produced GML feature definition.
     */
    private void addGmlFeatureType(
            Document outputSchema, Element outputSchemaRootNode, QName targetNamespace) {
        // create the XML complex type definition elements
        Element complexType = outputSchema.createElementNS(XML_NAMESPACE, "xs:complexType");
        complexType.setAttribute("name", getTypeName(name, null));
        Element complexContent = outputSchema.createElementNS(XML_NAMESPACE, "xs:complexContent");
        Element extension = outputSchema.createElementNS(XML_NAMESPACE, "xs:extension");
        extension.setAttribute("base", "gml:AbstractFeatureType");
        Element annotation = outputSchema.createElementNS(XML_NAMESPACE, "xs:annotation");
        Element sequence = outputSchema.createElementNS(XML_NAMESPACE, "xs:sequence");
        // link all elements together and append them to the output schema
        outputSchemaRootNode.appendChild(complexType);
        complexType.appendChild(complexContent);
        complexContent.appendChild(extension);
        extension.appendChild(annotation);
        extension.appendChild(sequence);
	// if simple type
	if (isSimpleContent(typeDefinition)) {
	    if (LOGGER.isLoggable(Level.INFO))
		LOGGER.info("Converting SimpleContent: " + typeDefinition.getAttribute("name"));
	    handleSimpleContent(outputSchema, outputSchemaRootNode, targetNamespace, sequence);
	}
        // merge ths complex type info with all the related types, i.e. super and extensions
        ComplexTypesMerger merger = new ComplexTypesMerger();
        merger.merge(inputSchema, targetNamespace, typeDefinition);
        relatedTypes.forEach(
                relatedType -> merger.merge(inputSchema, targetNamespace, relatedType));
        // add the merged info to the complex type
        merger.addDocumentation(outputSchema, annotation);
        merger.addAttributes(outputSchema, extension);
        merger.addProperties(outputSchema, sequence);
    }

    private void handleSimpleContent(Document outputSchema, Element outputSchemaRootNode, QName targetNamespace,
	    Element sequence) {
	// clone the complexType with simpleContent adding a sufix on the name
	final Element simpleContentElement = (Element) typeDefinition.cloneNode(true);
	final String simpleTypeName = simpleContentElement.getAttribute("name");
	simpleContentElement.setAttribute("name", simpleTypeName);
	outputSchemaRootNode.appendChild(outputSchema.adoptNode(simpleContentElement));
	// build the generated element for this sufixed complex type on sequence
	Element element = outputSchema.createElementNS(XML_NAMESPACE, "xs:element");
	element.setAttribute("name", "value");
	element.setAttribute("type", qualify(simpleTypeName, targetNamespace));
	sequence.appendChild(element);
    }

    /**
     * Declares a concrete instance (element) for the GML version of this complex type into the
     * provided output schema and target namespace:
     *
     * <pre>{@code
     * <xs:element name="Situation" type="npra:SituationType" substitutionGroup="gml:AbstractFeature"/>
     * }</pre>
     *
     * The produced element will be append to the output schema root node.
     */
    private void addGmlFeatureConcreteElement(
            Document outputSchema, Element outputSchemaRootNode, QName targetNamespace) {
        // create the XML element for the concrete instance
        Element element = outputSchema.createElementNS(XML_NAMESPACE, "xs:element");
        element.setAttribute("name", getName(name, null));
	element.setAttribute("type", getTypeName(name, targetNamespace));
        // this is a GML feature
        element.setAttribute("substitutionGroup", "gml:AbstractFeature");
        outputSchemaRootNode.appendChild(element);
    }

    /**
     * Declares a property type (element) for this complex type into the provided output schema and
     * target namespace:
     *
     * <pre>{@code
     * <xs:complexType name="SituationPropertyType">
     *   <xs:sequence minOccurs="0">
     *     <xs:element ref="npra:Situation"/>
     *   </xs:sequence>
     *   <xs:attributeGroup ref="gml:AssociationAttributeGroup"/>
     * </xs:complexType>
     * }</pre>
     *
     * The produced element will be append to the output schema root node. The produced property
     * type can then be used by others GML features to use the GML features corresponding tot his
     * complex type.
     */
    private void addGmlFeaturePropertyElement(
            Document outputSchema, Element outputSchemaRootNode, QName targetNamespace) {
        // create the XML complex type element for the property type
        Element complexType = outputSchema.createElementNS(XML_NAMESPACE, "xs:complexType");
        complexType.setAttribute("name", getPropertyTypeName(name, null));
        // create the XML sequence containing a
        Element sequence = outputSchema.createElementNS(XML_NAMESPACE, "xs:sequence");
        sequence.setAttribute("minOccurs", "0");
        // create the reference to the concrete element of the GML version fo this complex type
        Element reference = outputSchema.createElementNS(XML_NAMESPACE, "xs:element");
        reference.setAttribute("ref", getName(name, targetNamespace));
        // this is a GML association type
        Element association = outputSchema.createElementNS(XML_NAMESPACE, "xs:attributeGroup");
        association.setAttribute("ref", "gml:AssociationAttributeGroup");
        // link all the elements together and append them to the root node
        complexType.appendChild(sequence);
        sequence.appendChild(reference);
        complexType.appendChild(association);
        outputSchemaRootNode.appendChild(complexType);
    }
}
