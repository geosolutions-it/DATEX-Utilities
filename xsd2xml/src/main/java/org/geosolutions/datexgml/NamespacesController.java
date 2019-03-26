package org.geosolutions.datexgml;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.commons.lang3.tuple.Pair;
import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;

/** Namespaces controller for handling multiple Documents and their declared NS. */
public class NamespacesController {

    private final List<Document> docs;
    private final List<Pair<String, String>> nsDefinitions;

    public NamespacesController(List<Document> docs) {
        super();
        if (docs == null)
            throw new IllegalArgumentException("Documents List should not be a null reference.");
        this.docs = docs;
        this.nsDefinitions = new ArrayList<>();
        buildNsDefinitions();
    }

    private void buildNsDefinitions() {
        for (Document edoc : docs) {
            // extract namespaces definitions from root element
            List<Pair<String, String>> namespacesDefinitions = extractNamespacesDefinitions(edoc);
            // compare and add only new namespace URIs
            List<Pair<String, String>> newNamespacesToAdd =
                    namespacesDefinitions.stream()
                            .filter(
                                    n -> {
                                        boolean existsAlready =
                                                nsDefinitions.stream()
                                                        .anyMatch(
                                                                n2 ->
                                                                        n2.getValue()
                                                                                .equals(
                                                                                        n
                                                                                                .getValue()));
                                        // return true only if not exists already
                                        return !existsAlready;
                                    })
                            .collect(Collectors.toCollection(ArrayList::new));
            nsDefinitions.addAll(newNamespacesToAdd);
        }
    }
    
    public List<Pair<String, String>> getNsDefinitions() {
        return nsDefinitions;
    }

    /**
     * Adds namespaces attributes on provided Element.
     *
     * @param element Element object where add namespaces attributes.
     */
    public void addNamespacesAttributes(Element element) {
        nsDefinitions.forEach(
                ns -> {
                    element.setAttribute(ns.getKey(), ns.getValue());
                });
    }

    /**
     * Extracts all declared namespaces from document.
     *
     * @param doc XML document to process.
     * @return A List of Pair with extracted namespaces declaration.
     */
    public static List<Pair<String, String>> extractNamespacesDefinitions(final Document doc) {
        final List<Pair<String, String>> nsList = new ArrayList<>();
        // null check
        if (doc == null) return nsList;
        Element schemaNode = (Element) doc.getFirstChild();
        // get the 'xmlns:' prefixed attributes
        NamedNodeMap attributes = schemaNode.getAttributes();
        for (int i = 0; i < attributes.getLength(); i++) {
            Attr item = (Attr) attributes.item(i);
            if (item.getName().startsWith("xmlns:")) {
                Pair<String, String> pairItem = Pair.of(item.getName(), item.getValue());
                nsList.add(pairItem);
            }
        }
        return nsList;
    }
    
}
