import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.Socket;

import javax.print.DocFlavor.STRING;

class cmusica {
    public static void main(String args[]){

        try {

            //DIRECCION DEL SERVIDOR AL QUE LE QUEREMOS ENVIAR MENSAJITO
            InetAddress direccionServidor = InetAddress.getByName("localhost");

            //CREAMOS UN ARCHIVO PARA ESCRIBIR LA CANCION
            //File cancion = new File("cancionRecibida.mp3");

            //CAMBIAMOS EL FLUJO DE SALIDA HACIA E NUEVO ARCHIVO DE LA CANCION
            FileOutputStream fos = new FileOutputStream("cancionRecibidaaaa.mp3");

            //CREAMOS ESPACIO TEMPORAL DONDE VAMOS A RECIBIR EL TROZO DE LA CANCION
            byte[] trozo = new byte[2048];

            
            //CREAMOS UN SOCKET EN UN PUERTO QUE NOS ASIGNE EL SO
            DatagramSocket socket = new DatagramSocket();

            String mensaje = "Hola SERVIDOR, estoy listo para recibir cancion ...";

            //ENVIAMOS UN MENSAJE INICIAL
            DatagramPacket dpe = new DatagramPacket(mensaje.getBytes(), mensaje.getBytes().length, direccionServidor, 1234);

            socket.send(dpe);
            
            //ESTABLECEMOS UN NUM DE SEC ESPERADO
            int numSecExp = 0;

            while(true){

                DatagramPacket dpr = new DatagramPacket(trozo, trozo.length);

                socket.receive(dpr);

                //CREAMOS UNO PARA ALMACENAR DISTINTOS TIPOS DE DATOS
                DataInputStream dis = new DataInputStream(new ByteArrayInputStream(dpr.getData()));

                int numSec = dis.readInt();

                //SIGNIFICARIA QUE YA SE TERMINO DE ENVIAR LA CANCION
                if(numSec == -1){
                    System.out.println("CANCION RECIBIDA CORRECTAMENTE");
                    break;
                }

                //CREAMOS UN ARRAY PARA GUARDAR LA DATA RECIBIDA SIN EL NUM DE ACUSE
                byte[] data = new byte[dpr.getLength()-4];
                dis.readFully(data);

                //VERIFICAMOS SI ES EL ACUSE QUE ESPERABAMOS
                if(numSecExp == numSec){
                    fos.write(data);
                    numSecExp++;
                }else{
                    System.out.println("DATAGRAMA RECIBIDO INESPERADO");
                }

                //ENVIO DE ACUSE

                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                DataOutputStream dos = new DataOutputStream(baos);

                //SE LE RESTA MENOS UNO PORQUE LO INCREMENTAMOS EN EL IF
                dos.writeInt(numSecExp-1);

                DatagramPacket dp = new DatagramPacket(baos.toByteArray(), baos.toByteArray().length, direccionServidor, 1234);

                socket.send(dp);
            }

            socket.close();
            fos.close();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}