package com.jabcodex.uiporter;
import com.jabcodex.uiporter.adapters.FxmlAdapter;
import com.jabcodex.uiporter.adapters.JavaFxAdapter;
import com.jabcodex.uiporter.model.AppMetadata;
import java.nio.file.Files; import java.nio.file.Path; import java.util.Map;
public class ConvertCheck {
    public static void main(String[] args) throws Exception {
        String src = Files.readString(Path.of(args.length > 1 ? args[1] : "_example/ex.java"));
        JavaFxAdapter java = new JavaFxAdapter();
        FxmlAdapter fxml = new FxmlAdapter();
        AppMetadata app = java.parse(src);
        String out = fxml.generate(app, Map.of());
        Path outPath = Path.of(args.length > 2 ? args[2] : "_example/ex-out.fxml");
        Files.writeString(outPath, out);
        System.out.println("wrote " + outPath);
    }
}
