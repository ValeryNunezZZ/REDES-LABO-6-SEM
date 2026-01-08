import java.io.FileOutputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class ServidorChat {

    private static final int PORT = 9876;
    private static final int BUFFER_SIZE = 1024; //TAM MAXIMO DE CADA PAQUETE
    private static final String SERVER_FILES_DIR = "server_files";

    //SALAS
    private Map<String, Set<ClientInfo>> rooms = new ConcurrentHashMap<>();

    //MAPS DE ARCHIVOS PARA LA ESCRITURA, TAMANIOS ESPERADOS, TAMANIOS RECIBIDOS, NOMBRES ORIGINALES DE LOS ARCHIVOS
    private Map<String, FileOutputStream> fileTransferStreams = new ConcurrentHashMap<>();
    private Map<String, Long> fileTransferExpectedSizes = new ConcurrentHashMap<>();
    private Map<String, Long> fileTransferReceivedSizes = new ConcurrentHashMap<>();
    private Map<String, String> fileTransferNames = new ConcurrentHashMap<>();


    public ServidorChat() {
        rooms.put("general", new HashSet<>());
        rooms.put("juegos", new HashSet<>());

        try {
            Files.createDirectories(Paths.get(SERVER_FILES_DIR));
        } catch (IOException e) {
            System.err.println("Error al crear directorio de archivos del servidor: " + e.getMessage());
        }
    }

    public void start() {
        try (DatagramSocket socket = new DatagramSocket(PORT)) {
            System.out.println("Servidor de chat UDP iniciado en el puerto " + PORT);
            byte[] buffer = new byte[BUFFER_SIZE];

            while (true) {
                DatagramPacket receivePacket = new DatagramPacket(buffer, buffer.length);
                socket.receive(receivePacket);

                String fullMessage = new String(receivePacket.getData(), 0, receivePacket.getLength());
                InetAddress clientAddress = receivePacket.getAddress();
                int clientPort = receivePacket.getPort();

                handleMessage(fullMessage, receivePacket.getData(), receivePacket.getLength(), clientAddress, clientPort, socket);
            }
        } catch (IOException e) {
            System.err.println("Error en el servidor: " + e.getMessage());
        }
    }

    private void handleMessage(String fullMessage, byte[] data, int length, InetAddress address, int port, DatagramSocket socket) throws IOException {
        String commandPart = fullMessage; 

        if (fullMessage.contains(":")) {
             commandPart = fullMessage.substring(0, fullMessage.indexOf(":"));
        } else {

        }

        if (commandPart.length() == 36 && commandPart.matches("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$")) {
            handleFileChunk(commandPart, data, length, address, port, socket);
            return;
        }


        String[] parts = fullMessage.split(":", 3);
        String command = parts[0];

        try {
            switch (command) {
                case "JOIN":
                    handleJoin(parts[1], parts[2], address, port, socket);
                    break;
                case "MSG":
                    parts = fullMessage.split(":", 4);
                    handlePublicMessage(parts[1], parts[2], parts[3], address, port);
                    break;
                case "PRIV":
                    parts = fullMessage.split(":", 5);
                    handlePrivateMessage(parts[1], parts[2], parts[3], parts[4], address, port);
                    break;
                case "LIST":
                    
                    handleListUsers(parts[1], address, port, socket);
                    break;
                case "LEAVE":
                    handleLeave(parts[1], parts[2], address, port);
                    break;
                case "FILE_INIT":
                    parts = fullMessage.split(":", 6);
                    handleFileInit(parts[1], parts[2], parts[3], parts[4], Long.parseLong(parts[5]), address, port, socket);
                    break;
                case "FILE_CHUNK":
                    break;
                case "FILE_END":
                    parts = fullMessage.split(":", 2);
                    handleFileEnd(parts[1], address, port, socket);
                    break;
                default:
                    sendPacket("Comando desconocido", address, port, socket);
            }
        } catch (Exception e) {
            System.err.println("Error procesando comando de texto: " + e.getMessage());
            sendPacket("Error procesando comando: " + e.getMessage(), address, port, socket);
        }
    }

    private void handleJoin(String room, String username, InetAddress address, int port, DatagramSocket socket) throws IOException {
        if (!rooms.containsKey(room)) {
            sendPacket("Error: La sala '" + room + "' no existe.", address, port, socket);
            return;
        }

        ClientInfo newClient = new ClientInfo(username, address, port);
        Set<ClientInfo> clients = rooms.get(room);

        clients.add(newClient);
        System.out.println(username + " se unió a la sala " + room);

        broadcast(room, "[SALA] " + username + " ha entrado.");
    }

    private void handleLeave(String room, String username, InetAddress address, int port) throws IOException {
        Set<ClientInfo> clients = rooms.get(room);
        if (clients == null) return;

        ClientInfo clientToRemove = new ClientInfo(username, address, port);

        if (clients.remove(clientToRemove)) {
            System.out.println(username + " salió de la sala " + room);
            broadcast(room, "[SALA] " + username + " ha salido.");
        }
    }

    private void handlePublicMessage(String room, String username, String message, InetAddress senderAddress, int senderPort) throws IOException {
        String formattedMessage = "[" + room + "] " + username + ": " + message;
        broadcast(room, formattedMessage);
    }

    private void handlePrivateMessage(String room, String senderUser, String targetUser, String message, InetAddress senderAddress, int senderPort) throws IOException {
        Set<ClientInfo> clients = rooms.get(room);
        if (clients == null) {
            sendPacket("[SALA] Error: Sala '" + room + "' no existe.", senderAddress, senderPort, new DatagramSocket());
            return;
        }

        ClientInfo targetClient = null;
        for (ClientInfo client : clients) {
            if (client.getUsername().equals(targetUser)) {
                targetClient = client;
                break;
            }
        }

        String formattedMessage = "[PRIVADO de " + senderUser + "]: " + message;
        
        if (targetClient != null) {
            sendPacket(formattedMessage, targetClient.getAddress(), targetClient.getPort(), new DatagramSocket());
            sendPacket("[Enviado a " + targetUser + "]: " + message, senderAddress, senderPort, new DatagramSocket());
        } else {
            sendPacket("[SALA] Error: Usuario '" + targetUser + "' no encontrado en la sala.", senderAddress, senderPort, new DatagramSocket());
        }
    }

    private void handleListUsers(String room, InetAddress address, int port, DatagramSocket socket) throws IOException {
        Set<ClientInfo> clients = rooms.get(room);
        if (clients == null) {
            sendPacket("Error: Sala '" + room + "' no existe.", address, port, socket);
            return;
        }

        StringBuilder userList = new StringBuilder("Usuarios en '" + room + "': ");
        if (clients.isEmpty()) {
            userList.append(" (vacía)");
        } else {
            for (ClientInfo client : clients) {
                userList.append(client.getUsername()).append(" ");
            }
        }
        sendPacket(userList.toString(), address, port, socket);
    }

    private void handleFileInit(String transferId, String room, String senderUser, String filename, long fileSize, InetAddress address, int port, DatagramSocket socket) throws IOException {
        Path filePath = Paths.get(SERVER_FILES_DIR, transferId + "_" + filename);
        FileOutputStream fos = new FileOutputStream(filePath.toFile());
        
        fileTransferStreams.put(transferId, fos);
        fileTransferExpectedSizes.put(transferId, fileSize);
        fileTransferReceivedSizes.put(transferId, 0L);
        fileTransferNames.put(transferId, filename); 

        System.out.println("Iniciando transferencia de archivo '" + filename + "' (" + fileSize + " bytes) desde " + senderUser + " en sala " + room + ", ID: " + transferId);
        
        sendPacket("FILE_ACK_INIT:" + transferId, address, port, socket);

        broadcast(room, "[SALA] " + senderUser + " ha empezado a enviar el archivo '" + filename + "'.");
    }

    private void handleFileChunk(String transferId, byte[] rawData, int length, InetAddress address, int port, DatagramSocket socket) throws IOException {
        FileOutputStream fos = fileTransferStreams.get(transferId);
        if (fos == null) {
            System.err.println("Error: Recibido chunk para transferencia desconocida: " + transferId);
            return;
        }

        int headerLength = transferId.length() + ":CHUNK_DATA:".length();
        
        if (length < headerLength) { 
            System.err.println("Chunk demasiado pequeño para contener el encabezado: " + transferId);
            return;
        }

        byte[] chunkData = new byte[length - headerLength];
        System.arraycopy(rawData, headerLength, chunkData, 0, chunkData.length);

        fos.write(chunkData);
        long receivedSize = fileTransferReceivedSizes.merge(transferId, (long) chunkData.length, Long::sum);
    }

    private void handleFileEnd(String transferId, InetAddress address, int port, DatagramSocket socket) throws IOException {
        FileOutputStream fos = fileTransferStreams.remove(transferId);
        String filename = fileTransferNames.remove(transferId);
        Long expectedSize = fileTransferExpectedSizes.remove(transferId);
        Long receivedSize = fileTransferReceivedSizes.remove(transferId);

        if (fos != null) {
            fos.close();
            System.out.println("Transferencia '" + filename + "' (ID: " + transferId + ") completada. Recibidos " + receivedSize + " de " + expectedSize + " bytes.");
            
            if (expectedSize != null && !expectedSize.equals(receivedSize)) {
                System.err.println("ADVERTENCIA: Archivo '" + filename + "' recibido con tamaño inconsistente. Esperado: " + expectedSize + ", Recibido: " + receivedSize);
            }

            sendPacket("FILE_ACK_END:" + transferId + ":" + filename, address, port, socket);

        } else {
            System.err.println("Error: Fin de transferencia para ID desconocida: " + transferId);
        }
    }


    private void broadcast(String room, String message) throws IOException {
        Set<ClientInfo> clients = rooms.get(room);
        if (clients == null) return;

        try (DatagramSocket sendSocket = new DatagramSocket()) {
            for (ClientInfo client : clients) {
                sendPacket(message, client.getAddress(), client.getPort(), sendSocket);
            }
        }
    }

    private void sendPacket(String message, InetAddress address, int port, DatagramSocket socket) throws IOException {
        byte[] buffer = message.getBytes();
        DatagramPacket packet = new DatagramPacket(buffer, buffer.length, address, port);
        socket.send(packet);
    }


    public static void main(String[] args) {
        ServidorChat server = new ServidorChat();
        server.start();
    }
}