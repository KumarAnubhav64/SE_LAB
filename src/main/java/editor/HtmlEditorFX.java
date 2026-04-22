package editor;

import javafx.application.Application;
import javafx.stage.Stage;

public class HtmlEditorFX extends Application {

    public static void main(String[] args) {
        launch();
    }

    @Override
    public void start(Stage stage) {
        new UIController().init(stage);
    }
}
