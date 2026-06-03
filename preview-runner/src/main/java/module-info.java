module com.jabcodex.preview {
    requires javafx.controls;
    requires javafx.fxml;
    opens com.jabcodex.preview to javafx.graphics;
    exports com.jabcodex.preview;
}
