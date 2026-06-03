module com.jabcodex.uiporter {
    requires javafx.controls;
    requires javafx.fxml;
    requires org.fxmisc.richtext;
    requires org.fxmisc.flowless;
    requires com.github.javaparser.core;
    requires java.xml;
    requires java.net.http;
    requires java.desktop;
    requires java.compiler;

    opens com.jabcodex.uiporter to javafx.fxml;
    exports com.jabcodex.uiporter;

    // Sub-packages are module-internal; open them to javafx.base in case
    // JavaFX needs reflective access (e.g. property bindings in future).
    opens com.jabcodex.uiporter.model    to javafx.base;
    opens com.jabcodex.uiporter.core     to javafx.base;
    opens com.jabcodex.uiporter.adapters to javafx.base;
}
