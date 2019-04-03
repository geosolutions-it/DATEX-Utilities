package it.geosolutions.xsd2gml;

import static it.geosolutions.xsd2gml.Utils.extractUnqualifiedTypeName;
import static it.geosolutions.xsd2gml.Utils.searchElement;
import static it.geosolutions.xsd2gml.Utils.searchElements;
import static it.geosolutions.xsd2gml.Utils.unQualifyName;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

final class SchemaWalker {

    private final Document inputSchema;

    private final Map<String, Set<Element>> extensionsIndex = new HashMap<>();

    private final Set<Element> rootSimpleTypes = new HashSet<>();
    private final Map<Element, Set<Element>> rootComplexTypes = new HashMap<>();

    SchemaWalker(Document inputSchema, List<String> typesToWalk) {
        this.inputSchema = inputSchema;
        indexExtensionTypes();
        typesToWalk.forEach(
                startingTypeName -> walkTypeProperties(unQualifyName(startingTypeName)));
    }

    Set<Element> getRootSimpleTypes() {
        return rootSimpleTypes;
    }

    Map<Element, Set<Element>> getRootComplexTypes() {
        return rootComplexTypes;
    }

    /** Index all types that extend another type byt the type they extend. */
    private void indexExtensionTypes() {
        // get all complex type which have an extension
        List<Element> complexTypes =
                searchElements(inputSchema, "//*/extension/ancestor::complexType");
        for (Element complexType : complexTypes) {
            // get the extension node for this complex type
            List<Element> foundExtensions = searchElements(complexType, "*/extension");
            if (foundExtensions.size() != 1) {
                // strange situation, we don't support this
                throw new RuntimeException("Complex type has more than one child extension node.");
            }
            // get the super type name
            String superTypeName = extractUnqualifiedTypeName(foundExtensions.get(0), "base");
            if (superTypeName == null || superTypeName.isEmpty()) {
                // strange but no base attribute available or empty, let's move on
                continue;
            }
            // index this extension to its super type
            Set<Element> extensions =
                    extensionsIndex.computeIfAbsent(superTypeName, key -> new HashSet<>());
            extensions.add(complexType);
        }
    }

    /** Walk a type */
    private void walkTypeProperties(String unqualifiedTypeName) {
        // custom handling for multilingual strings
        if (unqualifiedTypeName.equals("MultilingualString")
                || unqualifiedTypeName.equals("MultilingualStringValue")
                || unqualifiedTypeName.equals("MultilingualStringValueType")) {
            // we ignore this types hierarchy, they are handled with a specific code path
            return;
        }
        // let's see if this is a complex type
        Element complexType =
                searchElement(
                        inputSchema,
                        String.format("/schema/complexType[@name='%s']", unqualifiedTypeName));
        if (complexType != null) {
            // yes this is a complex type, let's check if we already visit it
            if (rootComplexTypes.get(complexType) != null) {
                // yes we did, so let's move on
                return;
            }
            // so we found a new root complex type, let's store it
            rootComplexTypes.put(complexType, Collections.emptySet());
            // let's find the related types now, we do this way to avoid a cyclic recursive loop
            rootComplexTypes.put(complexType, findRelatedTypes(complexType));
            // let's walk over the complex type properties and attributes
            List<Element> properties = searchElements(complexType, "*//element");
            properties.addAll(searchElements(complexType, "attribute"));
            properties = properties.stream().filter(element -> {
                String typeName = element.getAttribute("type");
                return typeName != null && !typeName.isEmpty() && typeName.contains("D2LogicalModel");
            }).collect(Collectors.toList());
            for (Element property : properties) {
                // recursively walk the properties of this property type
                walkTypeProperties(extractUnqualifiedTypeName(property, "type"));
            }
            return;
        }
        // this is a simple type
        Element simpleType =
                searchElement(
                        inputSchema,
                        String.format("/schema/simpleType[@name='%s']", unqualifiedTypeName));
        if (simpleType != null) {
            rootSimpleTypes.add(simpleType);
        } else {
            throw new RuntimeException(
                    String.format("Type definition for '%s' not found.", unqualifiedTypeName));
        }
    }

    /**
     * Helper method that finds all the types at all levels that related with the provided complex
     * type. All the hierarchy levels of the complex type will be visited.
     */
    private Set<Element> findRelatedTypes(Element element) {
        Set<Element> relatedTypes = new HashSet<>();
        // find all the types from which this complex type extends from
        findRelatedTypesUpper(element, relatedTypes);
        // find all the types that extend from this complex type
        findRelatedTypesDown(element, relatedTypes);
        // walk on the found related types
        relatedTypes.forEach(
                relatedType -> walkTypeProperties(extractUnqualifiedTypeName(relatedType, "name")));
        return relatedTypes;
    }

    /** Find all the super types of the provided complex type definition. */
    private void findRelatedTypesUpper(Element complexType, Set<Element> relatedTypes) {
        // get the super type if available
        String superTypeName = extractSuperType(complexType);
        if (superTypeName == null || superTypeName.isEmpty()) {
            // no super type available
            return;
        }
        // search for the super type, it should be a complex type
        Element parent =
                searchElement(
                        inputSchema,
                        String.format("/schema/complexType[@name='%s']", superTypeName));
        if (parent == null) {
            // super type not found, there is nothing else we can do
            throw new RuntimeException(
                    String.format("Type definition for '%s' not found.", superTypeName));
        }
        // let's check if we already visited the super type
        if (!relatedTypes.contains(parent)) {
            // we didn't, so let's store it and get it's super type if available
            relatedTypes.add(parent);
            findRelatedTypesUpper(parent, relatedTypes);
        }
    }

    /**
     * Find all extensions, at all levels, of the provided complex type, this function will use the
     * extensions index. This method will recursively walk the type hierarchy.
     */
    private void findRelatedTypesDown(Element complexType, Set<Element> relatedTypes) {
        // get the complex type name and retrieve its extensions
        String complexTypeName = extractUnqualifiedTypeName(complexType, "name");
        Set<Element> extensions = extensionsIndex.get(complexTypeName);
        if (extensions == null || extensions.isEmpty()) {
            // this complex type has no extensions, we are done
            return;
        }
        // retrieve recursively all extensions
        extensions
                .stream()
                // skip types we already visited
                .filter(extension -> !relatedTypes.contains(extension))
                .forEach(
                        extension -> {
                            // add the found extension to the related types list
                            relatedTypes.add(extension);
                            // recursively walk down the hierarchy of this extension
                            findRelatedTypesDown(extension, relatedTypes);
                        });
    }

    /**
     * Extract the super type fo the provided complex type definition. The return ned super type may
     * be NULL or an empty String.
     */
    private static String extractSuperType(Element complexType) {
        List<Element> extensions = searchElements(complexType, "*/extension");
        if (extensions.size() > 1) {
            // this is usually means and invalid complex type definition
            throw new RuntimeException("Complex type contains multiple extensions.");
        }
        return extensions.isEmpty() ? null : extractUnqualifiedTypeName(extensions.get(0), "base");
    }
}
