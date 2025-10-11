import java.util.*;
import org.json.*;
import org.java_websocket.server.WebSocketServer;
import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.net.InetSocketAddress;

public class ServidorTienda extends WebSocketServer {

    private static List<Producto> productos = new ArrayList<>();

    // Cliente activo
    private static WebSocket clienteActivo = null;
    // Cola de espera
    private static Queue<WebSocket> colaClientes = new LinkedList<>();

    public ServidorTienda(int port) {
        super(new InetSocketAddress(port));
        cargarProductos();
    }

    public static void main(String[] args) {
        ServidorTienda server = new ServidorTienda(9000);
        server.start();
        System.out.println("Servidor WebSocket de tienda iniciado en puerto 9000...");
    }

    @Override
    public void onOpen(WebSocket conn, ClientHandshake handshake) {
        synchronized (ServidorTienda.class) {
            if (clienteActivo == null) {
                clienteActivo = conn;
                System.out.println("Cliente activo conectado: " + conn.getRemoteSocketAddress());
                conn.send("AHORA_ACTIVO"); // enviar mensaje estándar al cliente activo
            } else {
                // Poner al cliente en espera
                colaClientes.add(conn);
                conn.send("ESTAS_EN_ESPERA"); // mensaje para el cliente en espera
                System.out.println("Cliente en espera: " + conn.getRemoteSocketAddress());
            }
        }
    }

    @Override
    public void onClose(WebSocket conn, int code, String reason, boolean remote) {
        synchronized (ServidorTienda.class) {
            if (conn == clienteActivo) {
                System.out.println("Cliente activo desconectado: " + conn.getRemoteSocketAddress());
                clienteActivo = null;

                // Tomar el siguiente cliente de la cola
                WebSocket siguiente = colaClientes.poll();
                if (siguiente != null) {
                    clienteActivo = siguiente;
                    System.out.println("Cliente de la cola ahora activo: " + siguiente.getRemoteSocketAddress());
                    siguiente.send("AHORA_ACTIVO"); // mensaje para indicar que ahora es activo
                }
            } else {
                // Si estaba en cola, simplemente removerlo
                colaClientes.remove(conn);
            }
        }
    }

    @Override
    public void onMessage(WebSocket conn, String message) {
        synchronized (ServidorTienda.class) {
            // Solo atender al cliente activo
            if (conn != clienteActivo) {
                conn.send("ESTAS_EN_ESPERA"); // mensaje uniforme de espera
                return;
            }
        }

        try {
            JSONObject solicitud = new JSONObject(message);
            JSONObject respuesta = procesarSolicitud(solicitud, conn);
            conn.send(respuesta.toString());
        } catch (JSONException e) {
            JSONObject error = new JSONObject();
            error.put("estado", "error");
            error.put("mensaje", "JSON inválido: " + e.getMessage());
            conn.send(error.toString());
        } catch (Exception e) {
            JSONObject error = new JSONObject();
            error.put("estado", "error");
            error.put("mensaje", e.getMessage());
            conn.send(error.toString());
        }
    }

    @Override
    public void onError(WebSocket conn, Exception ex) {
        ex.printStackTrace();
    }

    @Override
    public void onStart() {
        System.out.println("Servidor WebSocket listo.");
    }

    private static void cargarProductos() {
        try {
            StringBuilder contenido = new StringBuilder();
            BufferedReader br = new BufferedReader(new FileReader("articulos.json"));
            String linea;
            while ((linea = br.readLine()) != null) {
                contenido.append(linea);
            }
            br.close();

            JSONArray arrayProductos = new JSONArray(contenido.toString());

            for (int i = 0; i < arrayProductos.length(); i++) {
                JSONObject obj = arrayProductos.getJSONObject(i);
                Producto producto = new Producto(
                        obj.getInt("id"),
                        obj.getString("nombre"),
                        obj.getDouble("precio"),
                        obj.getInt("stock"),
                        obj.getString("imagen"),
                        obj.getString("categoria")
                );
                productos.add(producto);
            }

            System.out.println("Productos cargados correctamente desde JSON.");

        } catch (IOException | JSONException e) {
            System.out.println("Error al leer productos.json: " + e.getMessage());
        }
    }

    private static JSONObject procesarSolicitud(JSONObject solicitud, WebSocket conn) throws Exception {
        String operacion = solicitud.getString("operacion");
        JSONObject respuesta = new JSONObject();

        switch (operacion) {
            case "LISTAR_PRODUCTOS":
                respuesta.put("estado", "exito");
                respuesta.put("datos", convertirProductosAJSON(productos));
                break;
            case "FINALIZAR_COMPRA":
                JSONArray carrito = solicitud.getJSONArray("carrito");
                respuesta = procesarCompra(carrito, conn);
                break;
            case "BUSCAR_PRODUCTO":
                String nombre = solicitud.getString("nombre").toLowerCase();
                List<Producto> filtrados = new ArrayList<>();
                for (Producto p : productos) {
                    if (p.getNombre().toLowerCase().contains(nombre)) {
                        filtrados.add(p);
                    }
                }
                respuesta.put("estado", "exito");
                respuesta.put("datos", convertirProductosAJSON(filtrados));
            break;
            
            default:
                respuesta.put("estado", "error");
                respuesta.put("mensaje", "Operación no válida");
        }

        return respuesta;
    }

    private static JSONArray convertirProductosAJSON(List<Producto> productos) {
        JSONArray array = new JSONArray();
        for (Producto p : productos) {
            JSONObject obj = new JSONObject();
            obj.put("id", p.getId());
            obj.put("nombre", p.getNombre());
            obj.put("precio", p.getPrecio());
            obj.put("stock", p.getStock());
            obj.put("imagen", p.getImagen());
            obj.put("categoria", p.getCategoria());
            array.put(obj);
        }
        return array;
    }

    private static JSONObject procesarCompra(JSONArray carrito, WebSocket conn) {
        JSONObject respuesta = new JSONObject();
        JSONArray items = new JSONArray();
        double total = 0;

        for (int i = 0; i < carrito.length(); i++) {
            JSONObject item = carrito.getJSONObject(i);
            int id = item.getInt("id");
            int cantidad = item.getInt("cantidad");
            Producto p = productos.stream().filter(x -> x.getId() == id).findFirst().orElse(null);
            if (p == null) continue;

            p.setStock(p.getStock() - cantidad);
            double subtotal = p.getPrecio() * cantidad;
            total += subtotal;

            JSONObject itemProcesado = new JSONObject();
            itemProcesado.put("nombre", p.getNombre());
            itemProcesado.put("cantidad", cantidad);
            itemProcesado.put("subtotal", subtotal);
            items.put(itemProcesado);
        }

        JSONObject ticket = new JSONObject();
        ticket.put("numero", System.currentTimeMillis());
        ticket.put("fecha", new Date().toString());
        ticket.put("items", items);
        ticket.put("total", total);

        respuesta.put("estado", "exito");
        respuesta.put("ticket", ticket);

        if (conn != null) {
            System.out.println("Compra realizada correctamente por el cliente: " + conn.getRemoteSocketAddress());
        }

        return respuesta;
    }

    // ---------------------------------------------
    // Clase Producto
    // ---------------------------------------------
    static class Producto {
        private int id; private String nombre; private double precio; private int stock; private String imagen; private String categoria;
        public Producto(int id, String nombre, double precio, int stock, String imagen, String categoria) {
            this.id = id; this.nombre = nombre; this.precio = precio; this.stock = stock; this.imagen = imagen; this.categoria = categoria;
        }
        public int getId(){return id;}
        public String getNombre(){return nombre;}
        public double getPrecio(){return precio;}
        public int getStock(){return stock;}
        public void setStock(int stock){this.stock = stock;}
        public String getImagen(){return imagen;}
        public String getCategoria(){return categoria;}
    }
}
