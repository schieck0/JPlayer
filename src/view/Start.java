package view;

import java.util.List;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.scene.paint.Color;
import javafx.stage.Stage;

/**
 *
 * @author Rodrigo
 */
public class Start extends Application {
    
    public static List<String> params;

    @Override
    public void start(Stage stage) throws Exception {
        params = getParameters().getRaw();
        
        Parent root = FXMLLoader.load(getClass().getResource("player.fxml"));
        Scene scene = new Scene(root);
        scene.setFill(Color.BLACK);
        stage.setScene(scene);
        stage.setTitle("JPlayer");
        stage.getIcons().add(new Image("/view/img/Player-Video-icon.png"));
        stage.show();

        PlayerController.instance.init();
    }

    /**
     * @param args the command line arguments
     */
    public static void main(String[] args) {
        launch(args);
    }

}
