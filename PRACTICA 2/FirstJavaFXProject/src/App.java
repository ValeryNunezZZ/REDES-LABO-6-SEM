import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.layout.HBox; // Nuevo para los controles
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;
import java.io.File;
import java.nio.file.Paths;
import javafx.util.Duration; // Nuevo para reiniciar la canción

public class App extends Application {

    // La constante CANCION_NOMBRE debe coincidir con el nombre de archivo en cmusica.java
    private static final String CANCION_NOMBRE = "cancionRecibidaaaa.mp3";
    private MediaPlayer mediaPlayer; 

    // Métodos de Control del Reproductor
    
    public void pausarCancion() {
        if (mediaPlayer != null && mediaPlayer.getStatus() == MediaPlayer.Status.PLAYING) {
            mediaPlayer.pause();
            System.out.println("Canción pausada.");
        }
    }

    public void reanudarCancion() {
        if (mediaPlayer != null && mediaPlayer.getStatus() == MediaPlayer.Status.PAUSED) {
            mediaPlayer.play();
            System.out.println("Canción reanudada.");
        }
    }

    public void reiniciarCancion() {
        if (mediaPlayer != null) {
            mediaPlayer.seek(Duration.ZERO); // Mueve la reproducción al inicio (0 segundos)
            mediaPlayer.play(); // Inicia la reproducción desde el inicio
            System.out.println("Canción reiniciada.");
        }
    }
    
    // Método para manejar la reproducción (del ejemplo anterior)
    public void reproducirCancion(String filePath) {
        try {
            if (mediaPlayer != null) {
                mediaPlayer.stop();
                mediaPlayer.dispose();
            }

            File file = new File(filePath);
            if (!file.exists()) {
                System.out.println("Error: Archivo no encontrado en la ruta: " + filePath);
                return;
            }

            Media media = new Media(file.toURI().toString());
            mediaPlayer = new MediaPlayer(media);
            mediaPlayer.play();
            System.out.println("Reproduciendo: " + filePath);

        } catch (Exception e) {
            System.err.println("Error al intentar reproducir la canción:");
            e.printStackTrace();
        }
    }

    @Override
    public void start(Stage primaryStage) {
        
        Button iniciarBtn = new Button("Recibir y Reproducir");
        iniciarBtn.setOnAction(e -> {
            iniciarBtn.setDisable(true);
            iniciarBtn.setText("Recibiendo...");
            
            new Thread(() -> {
                cmusica.main(null); 
            }).start();
            
            iniciarEsperaYReproduccion(iniciarBtn);
        });
        
        // --- NUEVOS BOTONES DE CONTROL ---
        Button pauseBtn = new Button("⏸️ Pausa");
        pauseBtn.setOnAction(e -> pausarCancion());
        
        Button playBtn = new Button("▶️ Reanudar");
        playBtn.setOnAction(e -> reanudarCancion());
        
        Button restartBtn = new Button("⏮️ Reiniciar");
        restartBtn.setOnAction(e -> reiniciarCancion());
        
        // Contenedor para los botones de control (Horizontal)
        HBox controlBox = new HBox(10, pauseBtn, playBtn, restartBtn);
        controlBox.setStyle("-fx-alignment: center;");


        // Contenedor Principal (Vertical)
        VBox root = new VBox(20, iniciarBtn, controlBox); // Añadimos el HBox de controles
        root.setStyle("-fx-padding: 30; -fx-alignment: center;");

        Scene scene = new Scene(root, 350, 250); // Aumentamos un poco el tamaño
        // scene.getStylesheets().add(getClass().getResource("style.css").toExternalForm()); // Se comenta si no existe el archivo

        primaryStage.setTitle("Cliente UDP - Reproductor");
        primaryStage.setScene(scene);
        primaryStage.show();
    }
    
    // Método iniciarEsperaYReproduccion (se mantiene sin cambios)
    private void iniciarEsperaYReproduccion(Button button) {
        new Thread(() -> {
            File cancionRecibida = new File(CANCION_NOMBRE); 
            boolean recibidoLocal = false;
            int intentos = 0;
            
            while (!recibidoLocal && intentos < 60) {
                if (cancionRecibida.exists() && cancionRecibida.length() > 0) {
                    recibidoLocal = true;
                } else {
                    try {
                        Thread.sleep(500);
                    } catch (InterruptedException ex) {
                        Thread.currentThread().interrupt();
                    }
                }
                intentos++;
            }
            
            final boolean exitoFinal = recibidoLocal;

            Platform.runLater(() -> {
                if (exitoFinal) {
                    reproducirCancion(cancionRecibida.getAbsolutePath());
                    button.setText("Reproduciendo...");
                } else {
                    button.setText("Error: Archivo no recibido.");
                }
                button.setDisable(false);
            });
        }).start();
    }

    public static void main(String[] args) {
        launch(args);
    }
}