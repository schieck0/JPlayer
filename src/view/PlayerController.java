package view;

import java.io.File;
import java.net.URL;
import java.util.Optional;
import java.util.ResourceBundle;
import javafx.application.Platform;
import javafx.beans.InvalidationListener;
import javafx.beans.Observable;
import javafx.beans.binding.Bindings;
import javafx.beans.property.DoubleProperty;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.GridPane;
import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;
import javafx.scene.media.MediaPlayer.Status;
import javafx.scene.media.MediaView;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.util.Duration;

/**
 * FXML Controller class
 *
 * @author Rodrigo
 */
public class PlayerController implements Initializable {

    private final ImageView imgPlay = new ImageView("/view/img/Play-icon.png");
    private final ImageView imgPause = new ImageView("/view/img/Pause-icon.png");

    public static PlayerController instance;

    private MediaPlayer player;
    private Duration duration;
    private File mediaFile;

    @FXML
    private AnchorPane anchor;
    @FXML
    private MediaView mediaView;
    @FXML
    private Button bPlay;
    @FXML
    private Slider timeSlider;
    @FXML
    private Slider volSlider;
    @FXML
    private Label lTime;
    @FXML
    private GridPane controlBar;

    /**
     * Initializes the controller class.
     */
    @Override
    public void initialize(URL url, ResourceBundle rb) {
        instance = this;

        Platform.runLater(() -> {
            anchor.getScene().getAccelerators().put(new KeyCodeCombination(KeyCode.A, KeyCombination.CONTROL_DOWN), () -> {
                open();
            });

            anchor.getScene().getAccelerators().put(new KeyCodeCombination(KeyCode.ESCAPE), () -> {
                controlBar.setVisible(true);
            });

            anchor.getScene().getAccelerators().put(new KeyCodeCombination(KeyCode.X), () -> {
                Platform.exit();
            });

            anchor.getScene().getAccelerators().put(new KeyCodeCombination(KeyCode.SPACE), () -> {
                playPause();
            });

            anchor.getScene().getAccelerators().put(new KeyCodeCombination(KeyCode.RIGHT), () -> {
                if (player != null) {
                    player.seek(player.getCurrentTime().add(new Duration(5000)));
                }
            });

            anchor.getScene().getAccelerators().put(new KeyCodeCombination(KeyCode.LEFT), () -> {
                if (player != null) {
                    player.seek(player.getCurrentTime().subtract(new Duration(5000)));
                }
            });

            anchor.getScene().getAccelerators().put(new KeyCodeCombination(KeyCode.UP), () -> {
                volSlider.setValue(volSlider.getValue() + 10);
            });

            anchor.getScene().getAccelerators().put(new KeyCodeCombination(KeyCode.DOWN), () -> {
                volSlider.setValue(volSlider.getValue() - 10);
            });

            anchor.getScene().getAccelerators().put(new KeyCodeCombination(KeyCode.DELETE), () -> {
                if (player != null) {
                    player.stop();
                    player.dispose();
                    mediaView.setMediaPlayer(null);
                    player = null;

                    Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
                    alert.setTitle("Excluír");
                    alert.setHeaderText("Exclusão de Arquivo");
                    alert.setContentText("Excluír o arquivo do disco?");

                    Optional<ButtonType> result = alert.showAndWait();
                    if (result.get() == ButtonType.OK) {
                        if (mediaFile.delete()) {
                            Platform.exit();
                        }
                    }
                }
            });
        });
    }

    public void init() {
//        mediaView.setVisible(false);

        bPlay.setGraphic(imgPlay);

        if (!Start.params.isEmpty()) {
            mediaFile = new File(Start.params.get(0));
            play();
        }

        timeSlider.valueProperty().addListener(new ChangeListener<Number>() {
            public void changed(ObservableValue<? extends Number> ov, Number oldVal, Number newVal) {
                if (timeSlider.isValueChanging()) {
                    if (player != null) {
                        player.seek(duration.multiply(timeSlider.getValue() / 100.0));
                    }
                }
            }
        });

        volSlider.valueProperty().addListener(new ChangeListener<Number>() {
            public void changed(ObservableValue<? extends Number> ov, Number oldVal, Number newVal) {
                setVolume(volSlider.getValue() / 100);
            }
        });
    }

    protected void play() {
        Media media = new Media(mediaFile.toURI().toString());
        player = new MediaPlayer(media);
        player.setAutoPlay(true);

        mediaView.setMediaPlayer(player);
        Stage stage = (Stage) anchor.getScene().getWindow();
//        volSlider.setValue((int) Math.round(player.getVolume() * 100));
        setVolume(volSlider.getValue() / 100);
        player.setOnReady(new Runnable() {
            @Override
            public void run() {
                double difW = stage.getWidth() - anchor.getScene().getWidth();
                double difH = stage.getHeight() - anchor.getScene().getHeight();
                int w = player.getMedia().getWidth();
                int h = player.getMedia().getHeight();
                stage.setWidth(w + difW);
                stage.setHeight(h + 50 + difH);

                //redimensionar
                final DoubleProperty width = mediaView.fitWidthProperty();
                final DoubleProperty height = mediaView.fitHeightProperty();
                width.bind(Bindings.selectDouble(mediaView.sceneProperty(), "width"));
                height.bind(Bindings.selectDouble(mediaView.sceneProperty(), "height"));
                mediaView.setPreserveRatio(true);

                duration = player.getMedia().getDuration();
                updateValues();
            }
        });

        player.currentTimeProperty().addListener(new InvalidationListener() {
            @Override
            public void invalidated(Observable observable) {
                updateValues();
            }
        });

        player.setOnPlaying(new Runnable() {
            public void run() {
                bPlay.setGraphic(imgPause);
            }
        });

        player.setOnPaused(new Runnable() {
            public void run() {
                bPlay.setGraphic(imgPlay);
            }
        });

        player.setOnEndOfMedia(new Runnable() {
            public void run() {
                bPlay.setGraphic(imgPlay);
            }
        });
    }

    protected void setVolume(double val) {
        if (player != null) {
            player.setVolume(val);
        }
    }

    protected void updateValues() {
        if (player != null && lTime != null && timeSlider != null && volSlider != null) {
            Platform.runLater(new Runnable() {
                public void run() {
                    Duration currentTime = player.getCurrentTime();
                    lTime.setText(formatTime(currentTime, duration));
                    timeSlider.setDisable(duration.isUnknown());
                    if (!timeSlider.isDisabled()
                            && duration.greaterThan(Duration.ZERO)
                            && !timeSlider.isValueChanging()) {
                        timeSlider.setValue(currentTime.toSeconds() / duration.toSeconds() * 100.0);
                    }
                }
            });
        }
    }

    private void open() {
        FileChooser fc = new FileChooser();
        File file = fc.showOpenDialog(null);
        if (file != null) {
            mediaFile = file;
            play();
        }
    }

    private void playPause() {
        if (player == null) {
            open();
        } else {
            Status status = player.getStatus();

            if (status == Status.UNKNOWN || status == Status.HALTED) {
                // don't do anything in these states
                return;
            }

            if (status == Status.PAUSED
                    || status == Status.READY
                    || status == Status.STOPPED) {
                // rewind the movie if we're sitting at the end
//             if (atEndOfMedia) {
//                mp.seek(mp.getStartTime());
//                atEndOfMedia = false;
//             }
                player.play();
            } else {
                player.pause();
            }
        }
    }

    private static String formatTime(Duration elapsed, Duration duration) {
        int intElapsed = (int) Math.floor(elapsed.toSeconds());
        int elapsedHours = intElapsed / (60 * 60);
        if (elapsedHours > 0) {
            intElapsed -= elapsedHours * 60 * 60;
        }
        int elapsedMinutes = intElapsed / 60;
        int elapsedSeconds = intElapsed - elapsedHours * 60 * 60
                - elapsedMinutes * 60;

        if (duration.greaterThan(Duration.ZERO)) {
            int intDuration = (int) Math.floor(duration.toSeconds());
            int durationHours = intDuration / (60 * 60);
            if (durationHours > 0) {
                intDuration -= durationHours * 60 * 60;
            }
            int durationMinutes = intDuration / 60;
            int durationSeconds = intDuration - durationHours * 60 * 60
                    - durationMinutes * 60;
            if (durationHours > 0) {
                return String.format("%d:%02d:%02d/%d:%02d:%02d",
                        elapsedHours, elapsedMinutes, elapsedSeconds,
                        durationHours, durationMinutes, durationSeconds);
            } else {
                return String.format("%02d:%02d/%02d:%02d",
                        elapsedMinutes, elapsedSeconds, durationMinutes,
                        durationSeconds);
            }
        } else if (elapsedHours > 0) {
            return String.format("%d:%02d:%02d", elapsedHours,
                    elapsedMinutes, elapsedSeconds);
        } else {
            return String.format("%02d:%02d", elapsedMinutes,
                    elapsedSeconds);
        }
    }

    @FXML
    private void mvPlayPauseAction(MouseEvent event) {
//        if (event.getClickCount() == 1) {
//            playPause();
//        } else 
        if (event.getClickCount() == 2 && player != null) {
            Stage stage = (Stage) anchor.getScene().getWindow();
            controlBar.setVisible(stage.isFullScreen());
            stage.setFullScreen(!stage.isFullScreen());
        }
    }

    @FXML
    private void bPlayPauseAction(ActionEvent event) {
        playPause();
    }

    @FXML
    private void volScroll(ScrollEvent event) {
        volSlider.setValue(volSlider.getValue() + (event.getDeltaY() / 10));
    }

    @FXML
    private void sliderSeek(KeyEvent event) {
        if (player != null) {
            if (event.getCode().equals(KeyCode.RIGHT)) {
                player.seek(player.getCurrentTime().add(new Duration(5000)));
                event.consume();
            } else if (event.getCode().equals(KeyCode.LEFT)) {
                player.seek(player.getCurrentTime().subtract(new Duration(5000)));
                event.consume();
            }
        }
    }
}
