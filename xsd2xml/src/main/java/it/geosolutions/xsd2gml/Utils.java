package it.geosolutions.xsd2gml;

import java.io.FileInputStream;
import java.io.InputStream;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;

import javax.xml.namespace.QName;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;

import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

final class Utils {

    private Utils() {}

    static Document readDocument(String filePath) {
        try (InputStream input = new FileInputStream(filePath)) {
            return readDocument(input);
        } catch (Exception exception) {
            throw new RuntimeException(
                    String.format(
                            "file or directory '%s' does not exists or have errors, "
                                    + "please check if you are using the correct filesystem path"
                                    + " or check possible XML errors: \n"
                                    + exception.getMessage(),
                            filePath));
        }
    }
    
    static Document readDocument(InputStream input) {
        try {
            // red the XML document form the input stream
            DocumentBuilderFactory documentBuilderFactory = DocumentBuilderFactory.newInstance();
            DocumentBuilder documentBuilder = documentBuilderFactory.newDocumentBuilder();
            return documentBuilder.parse(input);
        } catch (Exception exception) {
            // something bad happen when reading the XMl document
            throw new RuntimeException(
                    "Error reading XML document form the input stream.", exception);
        }
    }

    static Element toElement(Node node) {
        if (node instanceof Element) {
            // we have an element, let's just cast it
            return (Element) node;
        }
        // strange situation but there is nothing else we can do
        throw new RuntimeException(
                String.format(
                        "The provided node can't be converted to a node: %s", node.toString()));
    }

    static String documentToStringNpraPrefixed(Document document) {
        try {
            // indent the document with two spaces and we don't want XML declarations
            TransformerFactory transformerFactory = TransformerFactory.newInstance();
            transformerFactory.setAttribute("indent-number", 2);
            Transformer transformer = transformerFactory.newTransformer();
            transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
            transformer.setOutputProperty(OutputKeys.INDENT, "yes");
            // write to the string output and get the result back
            StringWriter writer = new StringWriter();
            transformer.transform(new DOMSource(document), new StreamResult(writer));
            return writer.getBuffer().toString().replace("D2LogicalModel", "npra");
        } catch (Exception exception) {
            throw new RuntimeException(
                    "Something bad happen when writing the document to the provided output stream.",
                    exception);
        }
    }

    static String documentToString(Document document) {
        try {
            // indent the document with two spaces and we don't want XML declarations
            TransformerFactory transformerFactory = TransformerFactory.newInstance();
            transformerFactory.setAttribute("indent-number", 2);
            Transformer transformer = transformerFactory.newTransformer();
            transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
            transformer.setOutputProperty(OutputKeys.INDENT, "yes");
            // write to the string output and get the result back
            StringWriter writer = new StringWriter();
            transformer.transform(new DOMSource(document), new StreamResult(writer));
            return writer.getBuffer().toString();
        } catch (Exception exception) {
            throw new RuntimeException(
                    "Something bad happen when writing the document to the provided output stream.",
                    exception);
        }
    }

    static Element searchElement(Node startingNode, String xpath) {
        try {
            XPath xpathBuilder = XPathFactory.newInstance().newXPath();
            Node node =
                    (Node) xpathBuilder.compile(xpath).evaluate(startingNode, XPathConstants.NODE);
            return node == null ? null : toElement(node);
        } catch (Exception exception) {
            throw new RuntimeException(
                    String.format("Error compiling \\ executing xpath '%s'.", xpath), exception);
        }
    }

    static List<Element> cloneList(List<Element> elements, boolean deep) {
        List<Element> cloned = new ArrayList<>();
        for (Element element : elements) {
            cloned.add(toElement(element.cloneNode(deep)));
        }
        return cloned;
    }
    
    static List<Element> searchElements(Node startingNode, String xpath) {
        try {
            XPath xpathBuilder = XPathFactory.newInstance().newXPath();
            NodeList nodes =
                    (NodeList)
                            xpathBuilder
                                    .compile(xpath)
                                    .evaluate(startingNode, XPathConstants.NODESET);
            if (nodes == null || nodes.getLength() == 0) {
                return new ArrayList<>();
            }
            List<Element> elements = new ArrayList<>();
            for (int i = 0; i < nodes.getLength(); i++) {
                elements.add(toElement(nodes.item(i)));
            }
            return elements;
        } catch (Exception exception) {
            throw new RuntimeException(
                    String.format("Error compiling \\ executing xpath '%s'.", xpath), exception);
        }
    }

    /**
     * Extracts the attribute from the provided XML element that contains a type name. Both
     * qualified and non qualified names are supported, int he last case only the local part is
     * returned.
     */
    static String extractUnqualifiedTypeName(Element element, String attributeName) {
        // get the value of attribute
        String rawName = element.getAttribute(attributeName);
        if (rawName == null) {
            // this means that we didn't get the expected element
            throw new RuntimeException(
                    String.format(
                            "No name attribute available in element: %s", element.toString()));
        }
        return unQualifyName(rawName);
    }

    /**
     * Retain only the local aprt of a qualified name.
     */
    static String unQualifyName(String name) {
        // we are only interested in the local part if a qualified name was provided
        String[] nameParts = name.split(":");
        if (nameParts.length == 2) {
            // return the local part of the name
            return nameParts[1];
        }
        if (nameParts.length == 1) {
            // not a qualified name, just return the whole thing
            return name;
        }
        // this is not a valid type name
        throw new RuntimeException(String.format("Type name '%s' is not a valid name.", name));
    }

    /**
     * Qualify the provided name using the provided namespace.. 
     */
    static String qualify(String name, QName targetNamespace) {
        return String.format("%s:%s", targetNamespace.getPrefix(), name);
    }
    
    /**
     * The name of the concrete element of the GML version the provided complex type name. If a
     * target namespace is provided the name will be qualified.
     */
    static String getName(String name, QName targetNamespace) {
        if (targetNamespace == null) {
            // non qualified
            return name;
        }
        return String.format("%s:%s", targetNamespace.getPrefix(), name);
    }

    /**
     * The type name of the provided complex type name, it will be the concatenation of it's name
     * with 'Type'. If a target namespace is provided the name will be qualified.
     */
    static String getTypeName(String name, QName targetNamespace) {
        if (targetNamespace == null) {
            // non qualified
            return name + "Type";
        }
        return String.format("%s:%sType", targetNamespace.getPrefix(), name);
    }

    /**
     * The property type name of the provided complex type name, it will be the concatenation of
     * it's name with 'PropertyType'. If a target namespace is provided the name will be qualified.
     */
    static String getPropertyTypeName(String name, QName targetNamespace) {
        if (targetNamespace == null) {
            // non qualified
            return name + "PropertyType";
        }
        return String.format("%s:%sPropertyType", targetNamespace.getPrefix(), name);
    }

    static List<Attr> searchAttributes(Node startingNode, String xpath) {
        try {
            XPath xpathBuilder = XPathFactory.newInstance().newXPath();
            NodeList nodes =
                    (NodeList)
                            xpathBuilder
                                    .compile(xpath)
                                    .evaluate(startingNode, XPathConstants.NODESET);
            if (nodes == null || nodes.getLength() == 0) {
                return new ArrayList<>();
            }
            List<Attr> attributes = new ArrayList<>();
            for (int i = 0; i < nodes.getLength(); i++) {
                attributes.add((Attr) nodes.item(i));
            }
            return attributes;
        } catch (Exception exception) {
            throw new RuntimeException(
                    String.format("Error compiling \\ executing xpath '%s'.", xpath), exception);
        }
    }
}
