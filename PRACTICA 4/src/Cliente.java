import java.io.*;
import java.net.*;

/**
 *
 * @author Axel
 */
public class Cliente {
    public static void main(String[] args){
          try {
            // Crear objeto URL
            String urlString ="http://148.204.58.221/axel/aplicaciones/sockets/java/";
            URL url = new URL(urlString);

            // Abrir conexión HTTP
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");

            // Obtener la respuesta del servidor
            int responseCode = connection.getResponseCode();
                //HttpURLConnection.
            if (responseCode == HttpURLConnection.HTTP_OK) {
                // Leer la respuesta del servidor
                BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                String line;
                StringBuilder response = new StringBuilder();

                while ((line = reader.readLine()) != null) {
                    response.append(line);
                    response.append("\n");
                }

                reader.close();

                // Mostrar la respuesta del servidor
                System.out.println("Respuesta del servidor:");
                System.out.println(response.toString());
            } else {
                System.out.println("La solicitud no fue exitosa. Código de respuesta: " + responseCode);
            }

            // Cerrar la conexión
            connection.disconnect();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }//main
}
