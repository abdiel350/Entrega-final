package com.example.chat;

import java.io.File;
import java.io.FileOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import android.os.Environment;
import android.util.Log;
import android.widget.TextView;
import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {
    static final int SocketServerPORT = 8080; // se define el puerto en el que se establecerá el servidor socket.
    TextView infoIp, infoPort, chatMsg;
    String msgLog = "";
    List<ChatClient> userList;
    ServerSocket serverSocket;

    //Este método se ejecuta cuando se crea la actividad. En este método se inicializan las vistas y se establece la dirección IP del dispositivo en el TextView
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        infoIp = (TextView) findViewById(R.id.infoip);
        infoPort = (TextView) findViewById(R.id.infoport);
        chatMsg = (TextView) findViewById(R.id.chatmsg);

        infoIp.setText(getIpAddress());
        userList = new ArrayList<ChatClient>();

        ChatServerThread chatServerThread = new ChatServerThread();
        chatServerThread.start();//iniciar servidor a traves de socket
    }

    @Override
    protected void onDestroy() {// este metodo es para cerrar el servidor socket
        super.onDestroy();

        if (serverSocket != null) {
            try {
                serverSocket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private class ChatServerThread extends Thread {// Esta clase interna hereda de Thread y se utiliza para crear y administrar el servidor socket
        @Override
        public void run() {// Este metodo Se encarga de aceptar conexiones de clientes y crear hilos para manejar cada conexion entrante.
            Socket socket = null;

            try {
                serverSocket = new ServerSocket(SocketServerPORT);
                MainActivity.this.runOnUiThread(new Runnable() {

                    @Override
                    public void run() {
                        infoPort.setText("Puerto Asignado: "
                                + serverSocket.getLocalPort());
                    }
                });

                while (true) {
                    socket = serverSocket.accept();
                    ChatClient client = new ChatClient();
                    userList.add(client);
                    ConnectThread connectThread = new ConnectThread(client, socket);
                    connectThread.start();
                }

            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                if (socket != null) {
                    try {
                        socket.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }

        }
    }

    private class ConnectThread extends Thread {//Esta clase interna hereda de Thread y se utiliza para manejar una conexión de cliente específica.
        Socket socket;
        ChatClient connectClient;
        String msgToSend = "";

        ConnectThread(ChatClient client, Socket socket){
            connectClient = client;
            this.socket= socket;
            client.socket = socket;
            client.chatThread = this;
        }

        @Override
        public void run() {
            DataInputStream dataInputStream = null;
            DataOutputStream dataOutputStream = null;

            try {
                dataInputStream = new DataInputStream(socket.getInputStream());
                dataOutputStream = new DataOutputStream(socket.getOutputStream());

                // Leer el nombre del cliente
                String clientName = dataInputStream.readUTF();
                connectClient.name = clientName;

                msgLog += clientName + " conectado @" + connectClient.socket.getInetAddress() + "\n";
                MainActivity.this.runOnUiThread(() -> chatMsg.setText(msgLog));

                dataOutputStream.writeUTF("Bienvenido: " + clientName);
                dataOutputStream.flush();

                broadcastMsg("SERVER", clientName + " se ha unido al chat.");

                while (true) {
                    // Leer tipo de mensaje
                    String messageType = dataInputStream.readUTF();

                    if (messageType.equals("TEXT")) {
                        String message = dataInputStream.readUTF();
                        msgLog += clientName + ": " + message + "\n";
                        MainActivity.this.runOnUiThread(() -> chatMsg.setText(msgLog));
                        broadcastMsg(clientName, message);

                    } else if (messageType.equals("IMAGE")) {
                        try {
                            int imageSize = dataInputStream.readInt();
                            byte[] imageBytes = new byte[imageSize];
                            dataInputStream.readFully(imageBytes);

                            String savedImagePath = saveImageLocally(imageBytes, clientName);

                            Log.d("ChatServer", "Imagen guardada en: " + savedImagePath);

                            // Mostrar log para depuración
                            Log.d("ChatServer", "Imagen recibida de " + clientName + " con tamaño: " + imageSize);

                            broadcastImage(clientName, imageBytes);
                        } catch (IOException e) {
                            Log.e("ChatServer", "Error al recibir imagen de " + clientName, e);
                        }
                    }
                }
            } catch (IOException e) {
                msgLog += connectClient.name + " se desconectó.\n";
                MainActivity.this.runOnUiThread(() -> chatMsg.setText(msgLog));
            } finally {
                try {
                    if (dataInputStream != null) dataInputStream.close();
                    if (dataOutputStream != null) dataOutputStream.close();
                    if (socket != null) socket.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
                userList.remove(connectClient);
                broadcastMsg("SERVER", connectClient.name + " ha abandonado el chat.");
            }
        }

        private void sendMsg(String msg){
            msgToSend = msg;
        }

    }

//envía una imagen a todos los clientes conectados, exceptuando al remitente, y actualiza el historial de chat en la interfaz de usuario para reflejar que una imagen fue enviada.
    private void broadcastImage(String sender, byte[] imageBytes) {
        for (ChatClient client : userList) {
            try {
                if (!client.name.equals(sender)) {
                    DataOutputStream dataOutputStream = new DataOutputStream(client.socket.getOutputStream());
                    dataOutputStream.writeUTF("IMAGE");
                    dataOutputStream.writeInt(imageBytes.length);
                    dataOutputStream.write(imageBytes);
                    dataOutputStream.flush();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        msgLog += sender + " envió una imagen.\n";
        MainActivity.this.runOnUiThread(() -> chatMsg.setText(msgLog));
    }

    private void broadcastMsg(String sender, String message) {
        for (ChatClient client : userList) {
            try {
                if (!client.name.equals(sender)) {
                    DataOutputStream dataOutputStream = new DataOutputStream(client.socket.getOutputStream());
                    dataOutputStream.writeUTF("TEXT");
                    dataOutputStream.writeUTF(sender + ": " + message);
                    dataOutputStream.flush();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private String saveImageLocally(byte[] imageBytes, String senderName) {
        String imagePath = "";
        try {
            // Directorio para guardar imágenes
            File imageDir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "ChatImages");

            // Crear directorio si no existe
            if (!imageDir.exists()) {
                if (!imageDir.mkdirs()) {
                    Log.e("ChatServer", "No se pudo crear el directorio para imágenes.");
                    return "";
                }
            }
            // Nombre único para la imagen
            String imageFileName = "IMG_" + System.currentTimeMillis() + "_" + senderName + ".jpg";

            File imageFile = new File(imageDir, imageFileName);
            // Escribir los bytes en el archivo
            try (FileOutputStream fos = new FileOutputStream(imageFile)) {
                fos.write(imageBytes);
            }
            imagePath = imageFile.getAbsolutePath();
        } catch (IOException e) {
            Log.e("ChatServer", "Error al guardar imagen localmente.", e);
        }
        return imagePath;
    }



    //Este método se utiliza para obtener la dirección IP, tambien obtiene las interfaces de red del disppsitivo o los dispositivos y luego itera sobre ellas para obtener las dirreciones Ip de cada uno.
    private String getIpAddress() {
        String ip = "";
        try {
            // Obtener todas las interfaces de red disponibles
            Enumeration<NetworkInterface> enumNetworkInterfaces = NetworkInterface.getNetworkInterfaces();
            // Iterar sobre las interfaces de red
            while (enumNetworkInterfaces.hasMoreElements()) {
                NetworkInterface networkInterface = enumNetworkInterfaces.nextElement();
                // Asegurarse de que la interfaz esté activa y no sea la interfaz de loopback (127.0.0.1)
                if (!networkInterface.isLoopback() && networkInterface.isUp()) {
                    // Obtener las direcciones IP de la interfaz de red
                    Enumeration<InetAddress> enumInetAddress = networkInterface.getInetAddresses();
                    // Iterar sobre las direcciones IP
                    while (enumInetAddress.hasMoreElements()) {
                        InetAddress inetAddress = enumInetAddress.nextElement();
                        // Solo tomar direcciones IPv4 y no direcciones de loopback
                        if (inetAddress instanceof java.net.Inet4Address) {
                            ip = inetAddress.getHostAddress();
                            break;  // Solo tomar la primera dirección IPv4 que se encuentre
                        }
                    }
                }
                // Si se encontró una dirección IP, no seguimos buscando más interfaces
                if (!ip.isEmpty()) {
                    break;
                }
            }
        } catch (SocketException e) {
            e.printStackTrace();
            ip = "Error al obtener IP: " + e.toString();
        }
        // Si no se encuentra ninguna dirección IPv4, mostramos un mensaje de error
        if (ip.isEmpty()) {
            ip = "No se pudo obtener la dirección IP";
        }
        return ip;
    }

    class ChatClient {
        String name;
        Socket socket;
        ConnectThread chatThread;
    }
}