package org.geosolutions.datexgml;

import org.w3c.dom.Document;

/** Document argument representation class. */
class DocumentArgument {

    private String filename;
    private Document document;
    private String targetNamespace;

    DocumentArgument(String filename, Document document, String targetNamespace) {
        super();
        this.filename = filename;
        this.document = document;
        this.targetNamespace = targetNamespace;
    }

    public Document getDocument() {
        return document;
    }

    public void setDocument(Document document) {
        this.document = document;
    }

    public String getTargetNamespace() {
        return targetNamespace;
    }

    public void setTargetNamespace(String targetNamespace) {
        this.targetNamespace = targetNamespace;
    }

    public String getFilename() {
        return filename;
    }

    public void setFilename(String filename) {
        this.filename = filename;
    }
}
