# How to run
```
java -jar {jarfile} {schemafile} "{Type1},{Type2}" "{targetNamespace}"
```
example:
```
java -jar datexgml.jar datex.xsd "Situation,CCTV" "http://datex2.eu/schema/3/3_5"
```
This tool will generate a datex.xsd.converted file on same directory.

# Generate a file from IDE
Use and modify the GmlConverterTest.testOutputFile() test method to generate a schema from the original(fixed) datex schema located in resources.

