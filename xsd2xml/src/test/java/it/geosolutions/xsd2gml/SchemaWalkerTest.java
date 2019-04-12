package it.geosolutions.xsd2gml;

import static it.geosolutions.xsd2gml.TestsUtils.readDatex23Schema;
import static it.geosolutions.xsd2gml.Utils.extractUnqualifiedTypeName;
import static java.util.Arrays.asList;
import static org.hamcrest.CoreMatchers.hasItems;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

public final class SchemaWalkerTest {

    private final Document datex23Schema = readDatex23Schema();

    @Test
    public void testWalkingGroupOfLocationsLinearComplexType() {
        // we should only find the 'GroupOfLocations' super type
        List<String> types = asList("GroupOfLocationsLinear");
        SchemaWalker walker = new SchemaWalker(datex23Schema, types);
        // let's check the result
        assertThat(walker.getRootSimpleTypes().size(), is(35));
        assertThat(walker.getRootComplexTypes().size(), is(127));
        assertThat(
                contains(
                        walker.getRootComplexTypes(), "GroupOfLocationsLinear", "GroupOfLocations"),
                is(true));
    }

    @Test
    public void testWalkingLocationComplexType() {
        // we should find the 'Location' several extension and a super type
        List<String> types = asList("Location");
        SchemaWalker walker = new SchemaWalker(datex23Schema, types);
        // let's check the result
        assertThat(walker.getRootSimpleTypes().size(), is(35));
        assertThat(walker.getRootComplexTypes().size(), is(127));
    }

    @Test
    public void testWalkingSituationComplexType() {
        // we should find the 'Location' several extension and a super type
        List<String> types = asList("Situation");
        SchemaWalker walker = new SchemaWalker(datex23Schema, types);
        // let's check the result
        assertThat(walker.getRootSimpleTypes().size(), is(152));
        assertThat(walker.getRootComplexTypes().size(), is(274));
    }

    @Test
    public void testWalkingSituationRecordComplexType() {
        // we should find the 'Location' several extension and a super type
        List<String> types = asList("SituationRecord");
        SchemaWalker walker = new SchemaWalker(datex23Schema, types);
        // let's check the result
        assertThat(walker.getRootSimpleTypes().size(), is(149));
        assertThat(walker.getRootComplexTypes().size(), is(272));
    }

    @Test
    public void testWalkingDataValueComplexType() {
        // we should find the 'Location' several extension and a super type
        List<String> types = asList("DataValue");
        SchemaWalker walker = new SchemaWalker(datex23Schema, types);
        // let's check the result
        List<String> names = walker.getRootSimpleTypes().stream().map(e -> e.getAttribute("name")).collect(Collectors.toList());
        Collections.sort(names);
        assertThat(walker.getRootSimpleTypes().size(), is(24));
        assertThat(walker.getRootComplexTypes().size(), is(23));
    }
    
    private boolean contains(
            Map<Element, Set<Element>> rootComplexTypes,
            String rootTypeName,
            String... relatedTypesNames) {
        for (Map.Entry<Element, Set<Element>> entry : rootComplexTypes.entrySet()) {
            String candidateRootTypeName = extractUnqualifiedTypeName(entry.getKey(), "name");
            if (candidateRootTypeName.equals(rootTypeName)) {
                if (relatedTypesNames.length == 0) {
                    return true;
                }
                List<String> candidateRelatedTypesNames =
                        entry.getValue()
                                .stream()
                                .map(element -> extractUnqualifiedTypeName(element, "name"))
                                .collect(Collectors.toList());
                assertThat(candidateRelatedTypesNames.size(), is(relatedTypesNames.length));
                assertThat(candidateRelatedTypesNames, hasItems(relatedTypesNames));
                return true;
            }
        }
        return false;
    }
}
