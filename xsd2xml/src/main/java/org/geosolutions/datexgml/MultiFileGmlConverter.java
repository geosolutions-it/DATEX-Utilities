package org.geosolutions.datexgml;

import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.logging.Logger;
import java.util.regex.Pattern;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Result;
import javax.xml.transform.Source;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.TransformerFactoryConfigurationError;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.apache.commons.lang3.StringUtils;
import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

/** Converter for multiple schema files at same time. */
public class MultiFileGmlConverter {

    private static final String XPATH_PREFIX = ".//";
    static final String FINAL_PREFIX = "sit";
    private static final Logger LOGGER = Logger.getLogger(MultiFileGmlConverter.class.getName());

    private List<String> desiredTypes;
    private List<SchemaFileMetadata> schemasMetadata;
    private boolean convertAllTypes = true;

    public MultiFileGmlConverter(List<String> desiredTypes, List<DocumentArgument> documents) {
        if (desiredTypes == null || desiredTypes.isEmpty())
            throw new IllegalArgumentException("desiredTypes can not be empty or null.");
        if (documents == null || documents.isEmpty())
            throw new IllegalArgumentException("documents can not be empty or null.");
        this.desiredTypes = new ArrayList<>(desiredTypes);
        this.schemasMetadata = new ArrayList<SchemaFileMetadata>();
        for (DocumentArgument edoc : documents) {
            schemasMetadata.add(
                    new SchemaFileMetadata(
                            edoc.getDocument(),
                            edoc.getTargetNamespace(),
                            edoc.getFilename(),
                            this::activateType));
        }
        // type arguments activation
        for (String t : desiredTypes) {
            activatePrefixedType(t);
        }
        if (convertAllTypes) {
            activateAllTypes();
        }
    }

    private void activateAllTypes() {
        for (SchemaFileMetadata fm : schemasMetadata) {
            fm.activateAllTypes();
        }
    }

    /** Activates a namespace:typename for to be included into final converted schema document. */
    public void activateType(String namespaceURI, String typeName) {
        schemasMetadata.forEach(sm -> sm.activateType(namespaceURI, typeName));
    }

    /** Activates a type using its prefixed form. */
    public void activatePrefixedType(String prefixedType) {
        if (StringUtils.isBlank(prefixedType))
            throw new IllegalArgumentException("Blank prefixedType unsupported.");
        if (!prefixedType.contains(":"))
            throw new IllegalArgumentException("prefixedType without prefix unsupported.");
        String[] parts = prefixedType.split(Pattern.quote(":"));
        String prefix = parts[0];
        String localname = parts[1];
        if (StringUtils.isAllBlank(prefix))
            throw new IllegalArgumentException("prefix is required.");
        if (StringUtils.isAllBlank(localname))
            throw new IllegalArgumentException("local name is required.");
        Optional<SchemaFileMetadata> smd =
                schemasMetadata.stream()
                        .filter(sm -> sm.getTargetPrefix().equals(prefix))
                        .findFirst();
        if (smd.isPresent()) {
            smd.get().activateType(smd.get().getFormerNamespace(), localname);
        }
    }

    /** Builds the resulting document and return it. */
    public List<DocumentArgument> buildAndGetResults() {
        List<DocumentArgument> documents = new ArrayList<>();
        for (SchemaFileMetadata md : schemasMetadata) {
            md.buildResultDocument();
            DocumentArgument da =
                    new DocumentArgument(md.getFilename(), md.getResultDocument(), null);
            documents.add(da);
        }
        return documents;
    }

    /** Executes multiple schemas converter and save resulting schema documents into a folder. */
    public static void executeOnFilesystem(
            List<File> files, List<String> activatedTypes, String targetNamespace) {
        // load documents
        List<DocumentArgument> arguments = new ArrayList<>();
        for (File efile : files) {
            Document document = loadDocument(efile);
            DocumentArgument argument =
                    new DocumentArgument(efile.getPath(), document, targetNamespace);
            arguments.add(argument);
        }
        // create converter
        MultiFileGmlConverter converter = new MultiFileGmlConverter(activatedTypes, arguments);
        // build the result documents
        Document resultDocument = converter.compileOnSingleSchemaFile(targetNamespace);
        // save to filesystem
        // create output folder on base of the first (main) file
        final File directory = files.get(0).getParentFile();
        if (directory == null) throw new IllegalStateException("Unable to get parent directory.");
        final File outputFolder = new File(directory, "converted");
        boolean mkdirOk = outputFolder.mkdir();
        if (!mkdirOk) throw new IllegalStateException("Unable to create new output directory.");
        // save result documents to file in output folder
        saveDocument(outputFolder, "compiledSchema.xsd", resultDocument);
        LOGGER.info(
                MessageFormat.format(
                        "Converted files saved into {0} folder.", outputFolder.getAbsolutePath()));
    }

    /** Executes multiple schema files converter and returns resulting documents. */
    public static Document executeAndGet(
            List<File> files, List<String> activatedTypes, String targetNamespace) {
        // load documents
        List<DocumentArgument> arguments = new ArrayList<>();
        for (File efile : files) {
            Document document = loadDocument(efile);
            DocumentArgument argument =
                    new DocumentArgument(efile.getPath(), document, targetNamespace);
            arguments.add(argument);
        }
        // create converter
        MultiFileGmlConverter converter = new MultiFileGmlConverter(activatedTypes, arguments);
        // build the result documents
        // final List<DocumentArgument> buildedList = converter.buildAndGetResults();
        return converter.compileOnSingleSchemaFile(targetNamespace);
    }

    public Document compileOnSingleSchemaFile(String targetNamespace) {
        List<DocumentArgument> documents = buildAndGetResults();
        Document compiled = getCompiledInitialDocument(targetNamespace);
        Node firstChild = compiled.getFirstChild();
        // add all elements from resulting documents
        for (DocumentArgument darg : documents) {
            NodeList childNodes = darg.getDocument().getFirstChild().getChildNodes();
            for (int i = 0; i < childNodes.getLength(); i++) {
                Node item = childNodes.item(i);
                String nameToAdd = "";
                if (item instanceof Element) {
                    nameToAdd = ((Element) item).getAttribute("Name");
                    // check if its repeated on compiled document
                    // by name=
                    List<Node> foundNodes =
                            SchemaFileMetadata.xpathToList(
                                    "/schema/*[@name='" + nameToAdd + "']", compiled);
                    if (foundNodes.size() == 0) {
                        firstChild.appendChild(compiled.importNode(item, true));
                    }
                }
            }
        }
        // fix all prefix to standard one
        // for attribute names: ref, base, type, xpath=".//sit:SituationRecord"
        // except prefixes: gml: xs:
        List<Attr> linkinElementAttributes = getLinkinElementAttributes(compiled);
        for (Attr attr : linkinElementAttributes) {
            replacePrefixIfSwapScenario(attr);
        }
        return compiled;
    }
    
    private boolean replacePrefixIfSwapScenario(Attr attribute) {
        final String name = attribute.getName();
        String value = attribute.getValue();
        if (StringUtils.isAnyBlank(name, value)) return false;
        String initialText = value.startsWith(XPATH_PREFIX) ? XPATH_PREFIX : "";
        value = value.replace(XPATH_PREFIX, "");
        if (value.contains(":")) {
            // ommit gml: xs: prefixes
            String[] parts = value.split(Pattern.quote(":"));
            String prefix = parts[0];
            String localname = parts[1];
            if (!"gml".equals(prefix) && !"xs".equals(prefix)) {
                attribute.setValue(initialText + this.FINAL_PREFIX + ":" + localname);
                return true;
            }
        }
        return false;
    }

    private List<Attr> getLinkinElementAttributes(Document compiled){
        List<Node> foundNodes = SchemaFileMetadata.xpathToList("/schema//*/@ref", compiled);
        foundNodes.addAll(SchemaFileMetadata.xpathToList("/schema//*/@base", compiled));
        foundNodes.addAll(SchemaFileMetadata.xpathToList("/schema//*/@type", compiled));
        foundNodes.addAll(SchemaFileMetadata.xpathToList("/schema//*/@xpath", compiled));
        return (List) foundNodes;
    }

    private static Document getCompiledInitialDocument(String targetNamespaceURI) {
        String documentText =
                "<?xml version=\"1.0\" encoding=\"utf-8\" standalone=\"no\"?>"
                        + "<xs:schema xmlns:"
                        + FINAL_PREFIX
                        + "=\""
                        + targetNamespaceURI
                        + "\" "
                        + "xmlns:gml=\"http://www.opengis.net/gml/3.2\" xmlns:xs=\"http://www.w3.org/2001/XMLSchema\" "
                        + "attributeFormDefault=\"unqualified\" elementFormDefault=\"qualified\" "
                        + "targetNamespace=\""
                        + targetNamespaceURI
                        + "\" version=\"3.0\"> </xs:schema>";
	return loadDocument(documentText);
    }

    private static void saveDocument(File folder, String filename, Document document) {
        try {
            File newFile = new File(folder, filename);
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
            Transformer xformer = TransformerFactory.newInstance().newTransformer();
            xformer.setOutputProperty(OutputKeys.METHOD, "xml");
            xformer.setOutputProperty(OutputKeys.INDENT, "yes");
            xformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "4");
            xformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
            Source source = new DOMSource(document);
            Result result = new StreamResult(newFile);
            xformer.transform(source, result);
        } catch (ParserConfigurationException
                | TransformerFactoryConfigurationError
                | TransformerException e) {
            throw new RuntimeException(e);
        }
    }

    private static Document loadDocument(File file) {
        try {
            DocumentBuilderFactory docFactory = DocumentBuilderFactory.newInstance();
            DocumentBuilder docBuilder;
            docBuilder = docFactory.newDocumentBuilder();
            Document doc = docBuilder.parse(file);
            return doc;
        } catch (ParserConfigurationException | SAXException | IOException e) {
            throw new RuntimeException(e);
        }
    }
    
    private static Document loadDocument(String xmlText) {
        try {
            DocumentBuilderFactory docFactory = DocumentBuilderFactory.newInstance();
            DocumentBuilder docBuilder;
            docBuilder = docFactory.newDocumentBuilder();
            Document doc = docBuilder.parse(new InputSource(new StringReader(xmlText)));
            return doc;
        } catch (ParserConfigurationException | SAXException | IOException e) {
            throw new RuntimeException(e);
        }
    }
    
}
