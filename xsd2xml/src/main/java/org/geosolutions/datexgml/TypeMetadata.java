package org.geosolutions.datexgml;

/** Metadata for typeName including its own subtype. */
class TypeMetadata {

    private String typeName;
    private XsdTypeType type;

    public TypeMetadata(String typeName, XsdTypeType type) {
        super();
        this.typeName = typeName;
        this.type = type;
    }

    public String getTypeName() {
        return typeName;
    }

    public void setTypeName(String typeName) {
        this.typeName = typeName;
    }

    public XsdTypeType getType() {
        return type;
    }

    public void setType(XsdTypeType type) {
        this.type = type;
    }
}
