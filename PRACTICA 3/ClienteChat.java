import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.Scanner;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class ClienteChat {

    private static final String SERVER_ADDRESS = "localhost";
    private static final int SERVER_PORT = 9876;
    private static final int BUFFER_SIZE = 1024;
    private static final int MAX_UDP_PAYLOAD = 1024 - 50; 
    private static final String CLIENT_DOWNLOADS_DIR = "downloads";

    private DatagramSocket socket;
    private InetAddress serverAddress;
    private String username;
    private String currentRoom = "";

    private Map<String, String> outgoingFileTransfers = new ConcurrentHashMap<>();


    public ClienteChat(String username) throws IOException {
        this.username = username;
        this.socket = new DatagramSocket();
        this.serverAddress = InetAddress.getByName(SERVER_ADDRESS);

        Files.createDirectories(Paths.get(CLIENT_DOWNLOADS_DIR));
        System.out.println("Los archivos descargados (si se implementa) se guardarían en: " + Paths.get(CLIENT_DOWNLOADS_DIR).toAbsolutePath());
    }

    public void start() {
        Thread receiverThread = new Thread(new Receiver(socket));
        receiverThread.setDaemon(true);
        receiverThread.start();

        Scanner scanner = new Scanner(System.in);
        System.out.println("Cliente de chat iniciado. Escribe '/help' para ver comandos.");

        while (true) {
            System.out.print("> ");
            String line = scanner.nextLine();
            
            if (line.trim().isEmpty()) continue;

            try {
                handleUserInput(line);
            } catch (IOException e) {
                System.err.println("Error al enviar mensaje: " + e.getMessage());
            }
        }
    }

    private void handleUserInput(String input) throws IOException {
        String messageToSend = null;

        if (input.startsWith("/join ")) {
            String room = input.substring(6).trim();
            this.currentRoom = room;
            messageToSend = "JOIN:" + room + ":" + this.username;
            System.out.println("Intentando unirse a la sala: " + room);

        } else if (input.startsWith("/leave")) {
            if (currentRoom.isEmpty()) {
                System.out.println("No estás en ninguna sala.");
                return;
            }
            messageToSend = "LEAVE:" + this.currentRoom + ":" + this.username;
            System.out.println("Saliendo de la sala: " + currentRoom);
            this.currentRoom = "";

        } else if (input.startsWith("/list")) {
            if (currentRoom.isEmpty()) {
                System.out.println("Debes unirte a una sala primero (/join <sala>)");
                return;
            }
            messageToSend = "LIST:" + this.currentRoom;

        } else if (input.startsWith("/priv ")) {
            if (currentRoom.isEmpty()) {
                System.out.println("Debes unirte a una sala para enviar mensajes privados.");
                return;
            }
            try {
                String[] parts = input.split(" ", 3);
                String targetUser = parts[1];
                String msg = parts[2];
                messageToSend = "PRIV:" + this.currentRoom + ":" + this.username + ":" + targetUser + ":" + msg;
            } catch (Exception e) {
                System.out.println("Formato incorrecto. Usa: /priv <usuario> <mensaje>");
            }
        
        } else if (input.startsWith("/sendfile ")) {
            if (currentRoom.isEmpty()) {
                System.out.println("Debes unirte a una sala para enviar archivos.");
                return;
            }
            try {
                String filePath = input.substring(10).trim();
                new Thread(() -> {
                    try {
                        sendFile(filePath);
                    } catch (IOException e) {
                        System.err.println("\nError al enviar archivo (hilo): " + e.getMessage());
                        System.out.print("> ");
                    }
                }).start();
            } catch (Exception e) {
                System.out.println("Formato incorrecto. Usa: /sendfile <ruta_archivo>");
                System.err.println("Error al iniciar envío de archivo: " + e.getMessage());
            }

        } else if (input.startsWith("/help")) {
            System.out.println("Comandos disponibles:");
            System.out.println(" /join <sala> - Unirse a una sala (ej: general, juegos)");
            System.out.println(" /leave - Salir de la sala actual");
            System.out.println(" /list - Listar usuarios en la sala");
            System.out.println(" /priv <usuario> <mensaje> - Enviar mensaje privado");
            System.out.println(" /sendfile <ruta_archivo> - Enviar archivo (imagen o audio)");
            System.out.println(" (cualquier otro texto) - Enviar mensaje público a la sala");
        
        } else {
            if (currentRoom.isEmpty()) {
                System.out.println("Debes unirte a una sala primero (/join <sala>)");
                return;
            }
            messageToSend = "MSG:" + this.currentRoom + ":" + this.username + ":" + input;
        }

        if (messageToSend != null) {
            sendPacket(messageToSend.getBytes());
        }
    }

    private void sendPacket(byte[] data) throws IOException {
        DatagramPacket packet = new DatagramPacket(data, data.length, serverAddress, SERVER_PORT);
        socket.send(packet);
    }

    private void sendFile(String filePath) throws IOException {
        File file = new File(filePath);
        if (!file.exists() || !file.isFile()) {
            System.out.println("\nError: Archivo no encontrado o no válido: " + filePath);
            System.out.print("> ");
            return;
        }

        String transferId = UUID.randomUUID().toString();
        String filename = file.getName();
        long fileSize = file.length();

        String initCommand = "FILE_INIT:" + transferId + ":" + currentRoom + ":" + username + ":" + filename + ":" + fileSize;
        sendPacket(initCommand.getBytes());
        System.out.println("\n> Iniciando envío de '" + filename + "' (" + fileSize + " bytes)...");
        System.out.print("> ");

        outgoingFileTransfers.put(transferId, filename);

        try (FileInputStream fis = new FileInputStream(file)) {
            byte[] buffer = new byte[MAX_UDP_PAYLOAD];
            int bytesRead;
            long totalBytesSent = 0;

            while ((bytesRead = fis.read(buffer)) != -1) {
                String chunkHeader = transferId + ":CHUNK_DATA:";
                byte[] headerBytes = chunkHeader.getBytes();

                byte[] fullChunkData = new byte[headerBytes.length + bytesRead];
                System.arraycopy(headerBytes, 0, fullChunkData, 0, headerBytes.length);
                System.arraycopy(buffer, 0, fullChunkData, headerBytes.length, bytesRead);

                sendPacket(fullChunkData);
                totalBytesSent += bytesRead;

                try {
                    Thread.sleep(1); 
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    System.err.println("\nEnvío de archivo interrumpido.");
                    System.out.print("> ");
                    return;
                }
            }

            String endCommand = "FILE_END:" + transferId;
            sendPacket(endCommand.getBytes());
            System.out.println("\n> Finalizado el envío de chunks para '" + filename + "'. (Total: " + totalBytesSent + " bytes)");
            System.out.print("> ");

        } catch (IOException e) {
            System.err.println("\nError durante la lectura/envío del archivo: " + e.getMessage());
            System.out.print("> ");
        }
    }

    class Receiver implements Runnable {
        private DatagramSocket socket;

        public Receiver(DatagramSocket socket) {
            this.socket = socket;
        }

        @Override
        public void run() {
            byte[] buffer = new byte[BUFFER_SIZE];
            while (true) {
                try {
                    DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                    socket.receive(packet); 
                    
                    String receivedContent = new String(packet.getData(), 0, packet.getLength()).trim();
                    
                    if (receivedContent.startsWith("FILE_ACK_INIT:")) {
                        String transferId = receivedContent.split(":")[1];
                        String filename = outgoingFileTransfers.get(transferId);
                        System.out.print("\rServidor confirmó inicio de transferencia " + (filename != null ? filename : "") + " (ID: " + transferId + ")\n> ");
                    
                    } else if (receivedContent.startsWith("FILE_ACK_END:")) {
                        String[] parts = receivedContent.split(":", 3);
                        String transferId = parts[1];
                        String filename = parts[2];
                        outgoingFileTransfers.remove(transferId);
                        System.out.print("\rServidor confirmó recepción COMPLETA de '" + filename + "'.\n> ");

                        sendPacket(("MSG:" + currentRoom + ":" + username + ":[ARCHIVO] '" + filename + "' está disponible en el servidor.").getBytes());

                    } else if (receivedContent.startsWith("[SALA] ")) {

                        if (receivedContent.contains("ha empezado a enviar el archivo '") && !receivedContent.contains(username)) {

                            System.out.print("\r" + receivedContent + "\n> ");
                        } else if (receivedContent.contains("[ARCHIVO] '") && !receivedContent.contains(username)) {
                            System.out.print("\r" + receivedContent + "\n> ");
                        } else {
                            System.out.print("\r" + receivedContent + "\n> ");
                        }
                    }
                    else {
                        System.out.print("\r" + receivedContent + "\n> ");
                    }

                } catch (IOException e) {
                    if (!socket.isClosed()) {
                        System.err.println("Error al recibir paquete: " + e.getMessage());
                    }
                }
            }
        }
    }

    public static void main(String[] args) {
        if (args.length != 1) {
            System.out.println("Uso: java ClienteChat <username>");
            return;
        }

        try {
            String username = args[0];
            ClienteChat client = new ClienteChat(username);
            client.start();
        } catch (IOException e) {
            System.err.println("No se pudo iniciar el cliente: " + e.getMessage());
        }
    }
}