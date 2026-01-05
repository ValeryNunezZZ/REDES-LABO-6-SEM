import java.net.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;

//CLASE SERVIDORWEB1
public class ServidorWeb1 {
    //PUERTO BASE
    public static int PUERTO = 8000;
    public static int POOL_SIZE;
    private ServerSocket ss;

    //CREACION DE POOL DE HILOS PARA LA REUTILIZACIÓN DE LOS MISMOS
    private ExecutorService executor;

    //EXECUTOR ES UNA ALBERCA DE HILOS ESPERANDO A QUE LES ASIGNEN UNA TAREA (BOQUE DE TRABAJO MANEJADOR)

    //CONSTRUCTO DE LA CLASE
    public ServidorWeb1(int puerto, int tamPool) throws Exception {
        PUERTO = puerto;
        POOL_SIZE = tamPool;

        //CONFIGURA EL SERVER SOCKEET EN EL PUERTO DEFINIDO
        this.ss = new ServerSocket(PUERTO);
        //SE CREA UN POOL DE HILOS
        this.executor = Executors.newFixedThreadPool(POOL_SIZE);
        
        System.out.println("Servidor iniciado en puerto " + PUERTO + " con Pool de " + POOL_SIZE);

        //ESCUCHA INFINITA DEL SERVIDOR
        while (true) {

            //SE ACEPTA LA CONECCIÓN CON EL CLIENTE
            Socket cliente = ss.accept();

            //CHECA SI EL POOL ESTÁ LLENO (A LA MITAD)
            int hilosActivos = ((ThreadPoolExecutor) executor).getActiveCount();
            
            // Si el pool supera la mitad, redireccionamos (excepto si ya somos el servidor espejo)
            //SE REDIRECCIONA AL PUERTO 8001 EN CASO DE QUE POOL = HILOS ACTIVOS
            if (hilosActivos >= POOL_SIZE/2 && PUERTO == 8000) {
                System.out.println("Pool al 100%. Redireccionando a servidor espejo (Puerto 8001)...");
                enviarRedireccion(cliente, "http://localhost:8001");
                //SE CIERRA LA CONECCIÓN DEL CLIENTE CON DICHO PUERTO
                cliente.close();
            } else {
                //SE LE ASIGNA EL BLOQUE DE TRABAJO CON LA CONECCIÓN DEL CLIENTE A UN HILO LIBRE, O DE NO HABER HILOS LIBRES SE PONE EN UNA COLA DE ESPERA

                //EXECUTOR SOLO ACEPTA OBJETOS DE TIPO RUNNABLE O CALLABLE
                executor.execute(new Manejador(cliente));
            }
        }
    }

    private void enviarRedireccion(Socket sc, String nuevaUrl) throws IOException {

        //SE DEFINE UN PRINT WRITER PARA FACILITAR LA ESCRITURA DE CADENA A BYTES, LOS CUALES SE ENVIAN POR EL CANAL DE COMUNICACIÓN ABIERTO
        PrintWriter pw = new PrintWriter(sc.getOutputStream());
        //ES 302 PARA QUE SEA UN REDIRECCIÓN TEMPORAL
        pw.print("HTTP/1.1 302 Movido temporalmente\r\n");
        pw.print("Location: " + nuevaUrl + "\r\n");
        pw.print("\r\n");

        //ENVIA EL MENSAJE SIN ESPERAR A QUE SE LLENE LA TUBERÍA.
        pw.flush();
    }

    //BLOQUE DE TRABAJO
    //RUNNABLE PERMITE LA INDEPENDENCIA ENTRE LOS CLIENTES ATENDIDOS
    //CUANDO ES DE TIPO RUNNABLE SE SABE POR DEFAULT QUE HAY UN METODO DE TIPO RUN()
    class Manejador implements Runnable {
        protected Socket socket;
        protected DataOutputStream dos;
        protected DataInputStream dis;

        public Manejador(Socket _socket) {
            this.socket = _socket;
        }

        public void run() {
            try {
                dos = new DataOutputStream(socket.getOutputStream());
                dis = new DataInputStream(socket.getInputStream());
                byte[] b = new byte[50000];
                int t = dis.read(b);
                if (t <= 0) return;

                String peticion = new String(b, 0, t);

                //TOMA SOLO LA PRIMER PARTE DEL ENCABEZADO (DONDE VA EL MÉTODO)
                System.out.println("Petición recibida:\n" + peticion.split("\n")[0]);

                //TOQEUINIZA EL RENGLON POR ESPACIOS EN BLANCO
                StringTokenizer st = new StringTokenizer(peticion);
                String metodo = st.nextToken();
                String recurso = st.nextToken();

                if (recurso.equals("/")) recurso = "/index.html";
                recurso = recurso.substring(1); //QUITA EL "/""

                switch (metodo.toUpperCase()) {
                    case "GET":
                        procesarGet(recurso);
                        break;
                    case "POST":
                    case "PUT":
                        enviarRespuestaTexto("200 OK", "Recurso " + metodo + " procesado: " + recurso);
                        break;
                    case "DELETE":
                        enviarRespuestaTexto("200 OK", "Recurso " + recurso + " eliminado (simulado)");
                        break;
                    default:
                        enviarRespuestaTexto("501 Not Implemented", "Método no soportado");
                }
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                try { socket.close(); } catch (Exception e) {}
            }
        }

        private void procesarGet(String nombreArchivo) throws IOException {
            File f = new File(nombreArchivo);
            if (f.exists()) {
                String mime = getMimeType(nombreArchivo);
                enviarArchivo(f, mime);
            } else {
                enviarRespuestaTexto("404 Not Found", "Archivo no encontrado");
            }
        }

        private String getMimeType(String archivo) {
            if (archivo.endsWith(".htm") || archivo.endsWith(".html")) return "text/html";
            if (archivo.endsWith(".jpg") || archivo.endsWith(".jpeg")) return "image/jpeg";
            if (archivo.endsWith(".css")) return "text/css";
            if (archivo.endsWith(".pdf")) return "application/pdf";
            return "text/plain"; // 4 tipos: html, jpg, pdf, plain
        }

        private void enviarArchivo(File f, String mime) throws IOException {
            byte[] archivoBytes = new byte[(int) f.length()];
            FileInputStream fis = new FileInputStream(f);
            fis.read(archivoBytes);
            fis.close();

            dos.writeBytes("HTTP/1.0 200 OK\r\n");
            dos.writeBytes("Content-Type: " + mime + "\r\n");
            dos.writeBytes("Content-Length: " + f.length() + "\r\n");
            dos.writeBytes("\r\n");
            dos.write(archivoBytes);
            dos.flush();
        }

        private void enviarRespuestaTexto(String status, String mensaje) throws IOException {
            String respuesta = "<html><body><h1>" + status + "</h1><p>" + mensaje + "</p></body></html>";
            dos.writeBytes("HTTP/1.0 " + status + "\r\n");
            dos.writeBytes("Content-Type: text/html\r\n");
            dos.writeBytes("Content-Length: " + respuesta.length() + "\r\n");
            dos.writeBytes("\r\n");
            dos.writeBytes(respuesta);
            dos.flush();
        }
    }

    public static void main(String[] args) throws Exception {
        BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
        System.out.print("Define el tamaño del Pool de conexiones: ");
        int pool = Integer.parseInt(br.readLine());
        
        System.out.print("¿Es servidor principal (8000) o espejo (8001)? [p/e]: ");
        String tipo = br.readLine();
        int puerto = tipo.equalsIgnoreCase("p") ? 8000 : 8001;

        //CREACION DE OBJETO SERVIDORWEB1
        new ServidorWeb1(puerto, pool);
    }
}