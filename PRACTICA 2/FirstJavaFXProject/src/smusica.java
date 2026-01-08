import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInput;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.Arrays;
import java.util.Scanner;

import javax.print.DocFlavor.STRING;


class smusica {

    public static void main(String[] args){

    
        try {
            //System.out.println("Directorio actual: " + new File(".").getAbsolutePath());

            //ABRIMOS UN ARCHIVO
            File cancion = new File("C:/Users/valer/Desktop/JavaProyecto/FirstJavaFXProject/src/cancion.mp3");

            //ESTABLECEMOS EL ARCHIVO COMO ENTRADA
            FileInputStream fis = new FileInputStream(cancion);

            //CREAMOS ARREGLO PARA GUARDAR LOS TROZOS DE LA CANCION
            byte[] trozo = new byte[1024];

            Scanner s = new Scanner(System.in);

            //TAMANO DE VENTANA
            int ventana = 5;

            System.out.print("Tamanio de la ventana: ");
            ventana = s.nextInt();

            //CREAMOS ARREGLO PARA GURARDAR LOS TROZOZ DENTRO DE LA VENTANA
            byte[][] ventanaData = new byte[ventana][];

            //CREAMOS UN ARREGLO PARA LOS TAMANOS DE CADA TROZO
            //CREO QUE NO ES NECESARIO
            int[] tamanoTrozo = new int[ventana];

            //NUMERO DE SECUENCIA PARA CADA TROZO
            int numSec = 0;

            //CONTADOR DE LOS BYTES LEIDOS
            int bytesLeidos = 0;

            //POS DEL INICIO DE LA VENTANA
            int base = 0;



            //ESTABLECER UN PUERTO DE ESCUCHA
            int port = 1234;

            //CREAMOS SOCKET Y LO VINCULAMOS AL PUERTO
            DatagramSocket socket = new DatagramSocket(port);

            System.out.println("Servidor escuchando en el puerto " + port);

            byte[] mensajeCliente = new byte[1024];

            //PARA EL MENSAJE QUE RECIBAMOS DEL CLIENTE
            DatagramPacket dpr = new DatagramPacket(mensajeCliente, mensajeCliente.length);

            socket.receive(dpr);

            String cadena = new String(mensajeCliente, 0, mensajeCliente.length);

            System.out.println(cadena);
            

            //BANDERA PARA QUE SE REPITA EL PROCESO HASTA QUE SE HAYA MANDADO TODA LA CANCION CORRECTAMENTE
            boolean enviada = false;

            while(!enviada){

                socket.setSoTimeout(5000);

                //MIENTRAS EXISTAN BYTES PARA LEER Y QUE HAYA ESPACIO EN LA VENTANA
                while(numSec < ventana+base && (bytesLeidos = fis.read(trozo)) != -1){
                    
                    //SE CREA ESPACIO DE MEMORIA QUE NO ES ARCHIVO PARA ESCRIBIR, Y ES DINÁMICO
                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    
                    //SE CREA UN ARREGLO QUE ADMITE DIFERENTES TIPOS DE DATOS Y SU SALIDA SE ESCRIBIRA EN BAOS
                    DataOutputStream dos = new DataOutputStream(baos);
                    
                    //SE ESCRIBE EL NUMERO DE SECUENCIA
                    dos.writeInt(numSec);
                    
                    //SE ESCRIBE EL TROZO DE LA CANCION
                    dos.write(trozo, 0, bytesLeidos);
                    
                    //CREAMOS UN DP PARA ENVIAR EL PAQUETITO
                    DatagramPacket dpe = new DatagramPacket(baos.toByteArray(), baos.toByteArray().length, dpr.getAddress(), dpr.getPort());
                    
                    socket.send(dpe);
                    
                    //GUARDAMOS EL PAQUETITO RESIEN ENVIADO EN LOS ARREGLOS DE DATA
                    ventanaData[numSec%ventana] = Arrays.copyOf(trozo, bytesLeidos);
                    tamanoTrozo[numSec%ventana] = bytesLeidos;

                    //INCREMENTAMOS EL NUMERO DE SECUENCIA PARA EL SIGUIENTE PAQUETITO
                    numSec++;

                }

                //YA QUE SE ENVIARON TODOS LOS DATAGRMAS DE LA VENTANA, ESPERAREMOS EL ACUSO CON TRY CATCH
                try {
                    //EL ACUSE ES UN ENTERO
                    byte[] ack = new byte[4];

                    DatagramPacket acuse = new DatagramPacket(ack, 4);

                    socket.receive(acuse);

                    //SE CREA UN ARREGLO QUE ADMITE DIFERENTES TIPOS DE DATOS DE ENTRADA
                    //RECIBE UN TIPO ESPACIO DE MEMORIA DINAMICO Y GUARDA EL ACUESE 
                    DataInputStream disAcuse = new DataInputStream(new ByteArrayInputStream(ack));

                    int ackRecibido = disAcuse.readInt();

                    System.out.println("ACUSE RECIBIDO: " + ackRecibido);

                    if(ackRecibido == base){
                        base = ackRecibido + 1;
                    }

                    //VERIFICAR SI YA SE TERMINO DE ENVIAR TODA LA CANCION
                    if(base == numSec && fis.available() == 0){enviada = true;}

                } catch (SocketTimeoutException e) {
                    //EN CASO DE QUE NO SE RECIBA NINGUN ACUSE, ES DECIR QUE SE EXCEDIO DEL TIEMPO LIMITE

                    //SE HACER UN FOR SOBRE EL ARREGLO VENTANA QUE HICIMOS
                    for(int i=base ; i<numSec ; i++){

                        ByteArrayOutputStream baos = new ByteArrayOutputStream();
                        DataOutputStream dos = new DataOutputStream(baos);

                        dos.writeInt(i);
                        dos.write(ventanaData[i%ventana], 0, ventanaData[i%ventana].length);

                        DatagramPacket dpReenviar = new DatagramPacket(baos.toByteArray(), baos.toByteArray().length, dpr.getAddress(), dpr.getPort());
                        socket.send(dpReenviar);
                    }
                }
            }

            //ENVIO DEL ULTIMO PAQUETITO

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            DataOutputStream dos = new DataOutputStream(baos);

            dos.writeInt(-1);

            DatagramPacket dp = new DatagramPacket(baos.toByteArray(), baos.toByteArray().length, dpr.getAddress(), dpr.getPort());

            socket.send(dp);

            System.out.println("MUSICA ENVIADA CORRECTAMENTE");

            fis.close();
            socket.close();

                
        } catch (Exception e) {
                e.printStackTrace();
        }
    }
}
