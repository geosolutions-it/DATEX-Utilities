package it.geosolutions.xsd2gml;

import static it.geosolutions.xsd2gml.Utils.cloneList;
import static it.geosolutions.xsd2gml.Utils.extractUnqualifiedTypeName;
import static it.geosolutions.xsd2gml.Utils.getPropertyTypeName;
import static it.geosolutions.xsd2gml.Utils.getTypeName;
import static it.geosolutions.xsd2gml.Utils.qualify;
import static it.geosolutions.xsd2gml.Utils.searchElement;
import static it.geosolutions.xsd2gml.Utils.searchElements;
import static it.geosolutions.xsd2gml.Utils.toElement;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import javax.xml.namespace.QName;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

final class ComplexTypesMerger {

    private final List<Element> documentation = new ArrayList<>();
    private final List<Element> attributes = new ArrayList<>();
    private final List<Element> properties = new ArrayList<>();

    void merge(Document inputSchema, QName targetNamespace, Element complexType) {
        // get the documentation elements
        documentation.addAll(
                cloneList(searchElements(complexType, "annotation/documentation"), true));
        // get the attributes elements
        attributes.addAll(searchElements(complexType, "attribute").stream().map(attribute -> {
            Element clone = toElement(attribute.cloneNode(true));
            String typeName = clone.getAttribute("type");
            if (typeName != null && !typeName.isEmpty()) {
                String[] parts = typeName.split(":");
                if (parts.length == 2 && parts[0].equals("D2LogicalModel")) {
                    clone.setAttribute("type", targetNamespace.getPrefix() + ":" + parts[1]);
                }
            }
            return clone;
        }).collect(Collectors.toList()));
        // get the properties
        extractProperties(inputSchema, targetNamespace, complexType);
    }

    void addDocumentation(Document outputSchema, Element annotation) {
        addElements(outputSchema, annotation, documentation);
    }

    void addAttributes(Document outputSchema, Element complexType) {
        addElements(outputSchema, complexType, attributes);
    }

    void addProperties(Document outputSchema, Element sequence) {
        addElements(outputSchema, sequence, properties);
    }

    private static void addElements(
            Document outputSchema, Element parentElement, List<Element> elements) {
        elements.forEach(
                element -> {
                    outputSchema.adoptNode(element);
                    parentElement.appendChild(element);
                });
    }

    private void extractProperties(Document schema, QName targetNamespace, Element complexType) {
        List<Element> elements = searchElements(complexType, "*//element");
        for (Element element : elements) {
            if (isSimpleType(schema, element)) {
                // is a simple property so we are done
                String typeName = extractUnqualifiedTypeName(element, "type");
                Element simpleProperty = toElement(element.cloneNode(true));
                simpleProperty.setAttribute("type", qualify(typeName, targetNamespace));
                properties.add(simpleProperty);
                continue;
            }
            // it's a complex type, let's check if it's single or multiple
            Element multipleProperty = toElement(element.cloneNode(true));
            String typeName = extractUnqualifiedTypeName(element, "type");
            if (isSingle(element)) {
                // we can use the type name
                multipleProperty.setAttribute("type", getTypeName(typeName, targetNamespace));
            } else {
                // we need to use the property type name
                multipleProperty.setAttribute("type", getPropertyTypeName(typeName, targetNamespace));
            }
            properties.add(multipleProperty);
        }
    }

    private boolean isSingle(Element property) {
        // get the value of the max occurs attribute
        String value = property.getAttribute("maxOccurs");
        if (value == null || value.isEmpty()) {
            // no max occurs means it's a single property
            return true;
        }
        if (value.equals("unbounded")) {
            // it's a multiple property
            return false;
        }
        // let's parse the multiplicity
        int maxOccurs = Integer.parseInt(value);
        return maxOccurs == 1;
    }

    private boolean isSimpleType(Document schema, Element property) {
        String name = extractUnqualifiedTypeName(property, "type");
        // we look for either a simple or a complex type definition to be sure the type exists
        if (isComplexTypeWithoutSimpleContent(schema, name)) {
            // it's a complex type
            return false;
        }
        if (searchElement(schema, String.format("/schema/simpleType[@name='%s']", name)) != null
                || isComplexTypeWithSimpleContent(schema, name)) {
            // it's a simple type
            return true;
        }
        // strange, we didn't found the type
        throw new RuntimeException(String.format("Definition for type '%s' not found.", name));
    }

    private boolean isComplexTypeWithoutSimpleContent(Document schema, String name) {
        final Element complexElement =
                searchElement(schema, String.format("/schema/complexType[@name='%s']", name));
        if (complexElement == null) return false;
        return searchElement(complexElement, "simpleContent") == null;
    }

    private boolean isComplexTypeWithSimpleContent(Document schema, String name) {
        final Element complexElement =
                searchElement(schema, String.format("/schema/complexType[@name='%s']", name));
        if (complexElement == null) return false;
        return searchElement(complexElement, "simpleContent") != null;
    }
}
