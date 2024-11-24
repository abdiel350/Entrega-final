package com.example.clienteactivity;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.StrictMode;
import android.provider.MediaStore;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.net.UnknownHostException;

public class MainActivity extends AppCompatActivity {

    static final int PICK_IMAGE_REQUEST = 1;
    static final int REQUEST_READ_EXTERNAL_STORAGE_PERMISSION = 1;
    static final int SocketServerPORT = 8080;

    LinearLayout loginPanel, chatPanel;
    EditText editTextUserName, editTextAddress;
    Button buttonConnect, buttonDisconnect, buttonSend;
    TextView chatMsg, textPort;
    EditText editTextSay;
    ImageView imagePreview;

    private byte[] imageBytes;
    private ChatClientThread chatClientThread;
    private String msgLog = "";

    private int userColor;

    // Colores predefinidos para los clientes
    private static final int GRAY_LIGHT = Color.parseColor("#D3D3D3");
    private static final int GRAY_MEDIUM = Color.parseColor("#808080");
    private static final int GRAY_DARK = Color.parseColor("#A9A9A9");

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Obteniendo las referencias o datos de la interfaz de usuario
        loginPanel = findViewById(R.id.loginpanel);
        chatPanel = findViewById(R.id.chatpanel);
        editTextUserName = findViewById(R.id.username);
        editTextAddress = findViewById(R.id.address);
        textPort = findViewById(R.id.port);
        textPort.setText("Puerto Asignado: " + SocketServerPORT);
        buttonConnect = findViewById(R.id.connect);
        buttonConnect.setOnClickListener(buttonConnectOnClickListener);
        buttonDisconnect = findViewById(R.id.disconnect);
        buttonDisconnect.setOnClickListener(buttonDisconnectOnClickListener);
        chatMsg = findViewById(R.id.chatmsg);

        imagePreview = findViewById(R.id.imagePreview);
        editTextSay = findViewById(R.id.say);
        buttonSend = findViewById(R.id.send);
        buttonSend.setOnClickListener(buttonSendOnClickListener);
        Button buttonSelectImage = findViewById(R.id.selectImage);

        buttonSelectImage.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (ActivityCompat.checkSelfPermission(MainActivity.this, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {
                    selectImage();
                } else {
                    ActivityCompat.requestPermissions(MainActivity.this, new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, REQUEST_READ_EXTERNAL_STORAGE_PERMISSION);
                }
            }
        });

        // evitar la excepción NetworkOnMainThreadException en aplicaciones Android.
        // Esta excepción ocurre cuando intentas realizar operaciones de red (como acceder a Internet)
        // en el hilo principal (UI thread) de la aplicación, lo cual está prohibido en Android para evitar
        // que la interfaz de usuario se bloquee mientras espera una respuesta de red.
        StrictMode.ThreadPolicy policy = new StrictMode.ThreadPolicy.Builder().permitAll().build();
        StrictMode.setThreadPolicy(policy);
    }

    // Método para seleccionar una imagen de la galería de nuestro dispositivo movil android
    private void selectImage() {
        Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        startActivityForResult(intent, PICK_IMAGE_REQUEST);
    }

    //Botón de desconexión
    View.OnClickListener buttonDisconnectOnClickListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            if (chatClientThread != null) {
                chatClientThread.disconnect();
                loginPanel.setVisibility(View.VISIBLE);  // Volver a mostrar el panel de login
                chatPanel.setVisibility(View.GONE);  // Ocultar el panel de chat
                Toast.makeText(MainActivity.this, "Desconectado del servidor", Toast.LENGTH_SHORT).show();
            }
        }
    };

    //Botón de enviar
    View.OnClickListener buttonSendOnClickListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            // Validar que haya texto o imagen para enviar
            if (editTextSay.getText().toString().equals("") && imageBytes == null) {
                Toast.makeText(v.getContext(), "Por favor, escribe un mensaje o añade una imagen.", Toast.LENGTH_SHORT).show();
                return;
            }

            // Si hay una imagen cargada
            if (imageBytes != null) {
                chatClientThread.sendImage(imageBytes); // Enviar la imagen al servidor
                Bitmap bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.length);

                // Mostrar la previsualización de la imagen en el chat
                runOnUiThread(() -> {
                    LinearLayout chatContainer = findViewById(R.id.chatImagesContainer); // Contenedor dinámico del ScrollView
                    ImageView chatImage = new ImageView(MainActivity.this);
                    chatImage.setImageBitmap(bitmap);
                    chatImage.setAdjustViewBounds(true);
                    chatImage.setScaleType(ImageView.ScaleType.CENTER_CROP);
                    chatContainer.addView(chatImage); // Agregar la imagen al contenedor del chat
                });

                // Limpiar imagen y previsualización
                imageBytes = null;
                imagePreview.setImageResource(0);
            }

            // Enviar mensaje de texto
            String msg = editTextSay.getText().toString();
            if (!msg.isEmpty()) {
                chatClientThread.sendMsg(msg); // Enviar el mensaje al servidor
                runOnUiThread(() -> {
                    LinearLayout chatContainer = findViewById(R.id.chatImagesContainer);
                    TextView chatText = new TextView(MainActivity.this);
                    chatText.setText(msg);
                    chatText.setPadding(10, 10, 10, 10);
                    chatText.setTextColor(getResources().getColor(android.R.color.black));

                    setTextBackgroundColor(chatText);
                    LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.WRAP_CONTENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                    );
                    params.setMargins(10, 10, 10, 10); // Márgenes entre mensajes
                    chatText.setLayoutParams(params);


                    chatContainer.addView(chatText); // Agregar el mensaje al contenedor
                    editTextSay.setText(""); // Limpiar el campo de texto
                });
            }
        }
    };

//boton para cargar ip y nombre
    View.OnClickListener buttonConnectOnClickListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            String textUserName = editTextUserName.getText().toString();
            if (textUserName.isEmpty()) {
                Toast.makeText(MainActivity.this, "Introduzca su Nombre", Toast.LENGTH_LONG).show();
                return;
            }

            String textAddress = editTextAddress.getText().toString();
            if (textAddress.isEmpty()) {
                Toast.makeText(MainActivity.this, "Ingrese Dirección del servidor", Toast.LENGTH_LONG).show();
                return;
            }

            // Validar que la dirección sea una IP válida
            if (!isValidIPAddress(textAddress)) {
                Toast.makeText(MainActivity.this, "Ingrese una dirección IP válida", Toast.LENGTH_LONG).show();
                return;
            }

            // Verificar conexión con el servidor antes de proceder
            if (!isServerAvailable(textAddress, SocketServerPORT)) {
                Toast.makeText(MainActivity.this, "El servidor no está disponible", Toast.LENGTH_LONG).show();
                return;
            }

            msgLog = "";
            chatMsg.setText(msgLog);
            loginPanel.setVisibility(View.GONE);
            chatPanel.setVisibility(View.VISIBLE);

            // Asignar un color único para el cliente
            userColor = assignUserColor(textUserName);

            // Proceso de Iniciar el hilo del cliente al chat
            chatClientThread = new ChatClientThread(textUserName, textAddress, SocketServerPORT);
            chatClientThread.start();
        }
    };

    // Método para verificar si el servidor está disponible
    private boolean isServerAvailable(String address, int port) {
        try (Socket socket = new Socket(address, port)) {
            return true; // Conexión exitosa
        } catch (IOException e) {
            return false; // Error al conectar
        }
    }

    // Método para validar una dirección IP
    private boolean isValidIPAddress(String ip) {
        // Expresión regular para validar IPv4
        String ipPattern = "^((25[0-5]|2[0-4][0-9]|[0-1]?[0-9][0-9]?)\\.){3}(25[0-5]|2[0-4][0-9]|[0-1]?[0-9][0-9]?)$";
        return ip.matches(ipPattern);
    }

    // Función para asignar un color según el nombre del cliente
    public int assignUserColor(String userName) {
        // Aquí asignamos un color fijo basado en el nombre del cliente
        switch (userName.hashCode() % 3) {
            case 0:
                return GRAY_LIGHT;
            case 1:
                return GRAY_MEDIUM;
            case 2:
                return GRAY_DARK;
            default:
                return GRAY_MEDIUM; // Valor por defecto
        }
    }

    // Función para asignar el fondo dinámico al TextView
    public void setTextBackgroundColor(TextView textView) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(userColor);  // Usamos el color asignado al cliente
        drawable.setCornerRadius(20); // Esquinas redondeadas
        drawable.setStroke(2, Color.BLACK); // Borde opcional

        textView.setPadding(40, 20, 40, 20); // Ajusta según lo que necesites
        textView.setBackground(drawable); // Establecer el fondo después del padding
    }

    // Esta clase nos ayuda a realizar la carga asíncrona de imágenes
    private class SendImageTask extends AsyncTask<Uri, Void, Void> {
        @Override
        protected Void doInBackground(Uri... uris) {
            Uri uri = uris[0];
            try {
                Bitmap bitmap = MediaStore.Images.Media.getBitmap(getContentResolver(), uri);
                ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
                bitmap.compress(Bitmap.CompressFormat.JPEG, 100, byteArrayOutputStream);
                imageBytes = byteArrayOutputStream.toByteArray();

                runOnUiThread(() -> {
                    imagePreview.setImageBitmap(bitmap);
                    imagePreview.setVisibility(View.VISIBLE);
                });
            } catch (IOException e) {
                e.printStackTrace();
                Toast.makeText(MainActivity.this, "Error al cargar la imagen", Toast.LENGTH_SHORT).show();
            }
            return null;
        }
    }

    // Clase para manejar la lógica del cliente de chat
    private class ChatClientThread extends Thread {
        String name;
        String dstAddress;
        int dstPort;
        String msgToSend = "";
        boolean goOut = false;
        Socket socket;
        DataOutputStream dataOutputStream;
        DataInputStream dataInputStream;

        ChatClientThread(String name, String address, int port) {
            this.name = name;
            dstAddress = address;
            dstPort = port;
        }

        // Método para enviar una imagen al servidor
        private void sendImage(byte[] imageBytes) {
            try {
                dataOutputStream.writeUTF("IMAGE");
                dataOutputStream.writeInt(imageBytes.length);
                dataOutputStream.write(imageBytes);
                dataOutputStream.flush();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }


        // Método para enviar un mensaje de texto al servidor
        private void sendMsg(String msg) {
            try {
                dataOutputStream.writeUTF("TEXT");
                dataOutputStream.writeUTF(msg);
                dataOutputStream.flush();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        private void addMessageToChat(String message) {
            LinearLayout chatContainer = findViewById(R.id.chatImagesContainer);
            TextView chatText = new TextView(MainActivity.this);
            chatText.setText(message);
            chatText.setPadding(10, 10, 10, 10);
            chatText.setTextColor(Color.BLACK);

            // Estilo dinámico según el remitente
            if (message.startsWith(editTextUserName.getText().toString() + ":")) {
                // Mensaje enviado por el usuario actual
                setTextBackgroundColor(chatText);
            } else {
                // Mensaje de otro usuario (puedes asignar otro color o estilo aquí)
                GradientDrawable drawable = new GradientDrawable();
                drawable.setColor(Color.LTGRAY); // Color diferente para mensajes de otros usuarios
                drawable.setCornerRadius(20);
                drawable.setStroke(2, Color.BLACK);
                chatText.setBackground(drawable);
            }

            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
            );
            params.setMargins(10, 10, 10, 10); // Márgenes entre mensajes
            chatText.setLayoutParams(params);

            chatContainer.addView(chatText); // Agregar el mensaje al contenedor
        }



        // Método para desconectar el cliente y enviar un mensaje de desconexión al servidor
        private void disconnect() {
            try {
                if (socket != null && !socket.isClosed()) {
                    dataOutputStream.writeUTF("DISCONNECT");  // Enviar la señal de desconexión
                    dataOutputStream.flush();
                    goOut = true;
                    socket.close();  // Cerrar el socket
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        //metodo que establece una conexion con una dirrecion ip y un puerto especifico
        @Override
        public void run() {
            try {
                socket = new Socket(dstAddress, dstPort);

                // Objetos para enviar y recibir datos a través del socket
                dataOutputStream = new DataOutputStream(socket.getOutputStream());
                dataInputStream = new DataInputStream(socket.getInputStream());

                // Enviar el nombre del usuario al servidor
                dataOutputStream.writeUTF(name);
                dataOutputStream.flush();

                while (!goOut) {
                    // Verificar si hay datos disponibles
                    if (dataInputStream.available() > 0) {
                        String msgType = dataInputStream.readUTF(); // Leer el tipo de mensaje (texto o imagen)

                        if (msgType.equals("TEXT")) {
                            String msg = dataInputStream.readUTF();
                            // Actualizar la interfaz de usuario con el mensaje recibido
                            MainActivity.this.runOnUiThread(() -> addMessageToChat(msg));
                            msgLog += msg + "\n"; // Actualizamos el log de mensajes

                           // MainActivity.this.runOnUiThread(() -> chatMsg.setText(msgLog));
                        } else if (msgType.equals("IMAGE")) {
                            int imageSize = dataInputStream.readInt();
                            byte[] imageData = new byte[imageSize];
                            dataInputStream.readFully(imageData);

                            // Mostrar la imagen en el chat
                            Bitmap bitmap = BitmapFactory.decodeByteArray(imageData, 0, imageSize);

                            MainActivity.this.runOnUiThread(() -> {
                                LinearLayout chatContainer = findViewById(R.id.chatImagesContainer);
                                ImageView chatImage = new ImageView(MainActivity.this);

                                chatImage.setImageBitmap(bitmap);
                                chatImage.setAdjustViewBounds(true);
                                chatImage.setScaleType(ImageView.ScaleType.CENTER_CROP);

                                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                                        LinearLayout.LayoutParams.MATCH_PARENT,
                                        LinearLayout.LayoutParams.WRAP_CONTENT
                                );
                                params.setMargins(10, 10, 10, 10); // Ajusta los márgenes si es necesario
                                chatImage.setLayoutParams(params);

                                chatContainer.addView(chatImage); // Agregar la imagen al contenedor
                            });

                        }
                    }

                    // Enviar el mensaje de texto si existe
                    if (!msgToSend.equals("")) {
                        //dataOutputStream.writeUTF("TEXT"); // Indicamos que es un mensaje de texto
                        dataOutputStream.writeUTF(msgToSend);
                        dataOutputStream.flush();
                        msgToSend = "";
                    }
                }
            } catch (UnknownHostException e) {
                e.printStackTrace();
                final String eString = e.toString();
                MainActivity.this.runOnUiThread(() -> Toast.makeText(MainActivity.this, eString, Toast.LENGTH_LONG).show());
            } catch (IOException e) {
                e.printStackTrace();
                final String eString = e.toString();
                MainActivity.this.runOnUiThread(() -> Toast.makeText(MainActivity.this, eString, Toast.LENGTH_LONG).show());
            } finally {
                try {
                    if (socket != null) socket.close();
                    if (dataOutputStream != null) dataOutputStream.close();
                    if (dataInputStream != null) dataInputStream.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    // Método para manejar los resultados de la solicitud de permisos de la imagen
    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        switch (requestCode) {
            case REQUEST_READ_EXTERNAL_STORAGE_PERMISSION:
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Toast.makeText(this, "Permiso denegado", Toast.LENGTH_SHORT).show();
                } else {

                    selectImage();
                }
                break;
            default:
                break;
        }
    }

    // Método para manejar los resultados de la actividad de selección de imagen
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == PICK_IMAGE_REQUEST && resultCode == RESULT_OK && data != null && data.getData() != null) {
            Uri uri = data.getData();

            // Ejecutar la tarea asíncrona para procesar la imagen seleccionada
            new SendImageTask().execute(uri);
        }
    }
}