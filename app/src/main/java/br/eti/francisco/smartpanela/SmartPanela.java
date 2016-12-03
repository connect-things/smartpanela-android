package br.eti.francisco.smartpanela;

import android.app.ProgressDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Handler;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import org.w3c.dom.Text;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.UUID;

public class SmartPanela extends AppCompatActivity {

    private String address = null;

    private ProgressDialog progress;

    private BluetoothAdapter myBluetooth = null;
    private BluetoothSocket btSocket = null;
    private boolean isBtConnected = false;
    private boolean active = true;
    private static final UUID myUUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");

    private TextView txTempAtual;
    private TextView txtAquecendo;
    private TextView txtTempFinalGrav;
    private TextView txtCronAtual;
    private TextView txtCronometro;
    private EditText txtTempFinal;
    private EditText txtTempo;

    private double tempAtual;
    private boolean heating;
    private boolean activeTimer;
    private int timeLeft;
    private double tempFinal;

    private Handler handler = new Handler();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_smart_panela);

        Intent newint = getIntent();
        address = newint.getStringExtra(Constants.BT_ADDRESS);
        new ConnectBT().execute();

        txTempAtual = (TextView) findViewById(R.id.txtTempAtual);
        txtAquecendo = (TextView) findViewById(R.id.txtAquecendo);
        txtCronometro = (TextView) findViewById(R.id.txtCronometro);
        txtTempFinalGrav = (TextView) findViewById(R.id.txtTempFinalGrav);
        txtCronAtual = (TextView) findViewById(R.id.txtCronAtual);
        txtTempFinal = (EditText) findViewById(R.id.txtTempFinal);
        txtTempo = (EditText) findViewById(R.id.txtTempo);

        Button btnDisc = (Button)findViewById(R.id.btnDisc);
        btnDisc.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                disconnect(); //close connection
            }
        });

        Button btnEnviar = (Button)findViewById(R.id.btnEnviar);
        btnEnviar.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                enviar();
            }
        });

        Button btnLigarRecirculacao = (Button)findViewById(R.id.btnLigarRecirculacao);
        btnLigarRecirculacao.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ligarRecirculacao();
            }
        });

        Button btnDesligarRecirculacao = (Button)findViewById(R.id.btnDesligarRecirculacao);
        btnDesligarRecirculacao.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                desligarRecirculacao();
            }
        });
    }

    private void desligarRecirculacao() {
        try {
            String temperatura = txtTempFinal.getText().toString();
            String tempo = txtTempo.getText().toString();
            btSocket.getOutputStream().write("r,N,".getBytes());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void ligarRecirculacao() {
        try {
            String temperatura = txtTempFinal.getText().toString();
            String tempo = txtTempo.getText().toString();
            btSocket.getOutputStream().write("r,S,".getBytes());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void enviar(){
        try {
            String temperatura = txtTempFinal.getText().toString();
            String tempo = txtTempo.getText().toString();
            btSocket.getOutputStream().write(String.format("t,%s,%s,", temperatura, tempo).getBytes());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void disconnect(){
        if (btSocket!=null){ //If the btSocket is busy
            try{
                btSocket.close(); //close connection
                active = false;
                finish();
            }
            catch (IOException e){ msg("Error");}
        }
        finish(); //return to the first layout

    }

    private void msg(String s){
        Toast.makeText(getApplicationContext(), s, Toast.LENGTH_LONG).show();
    }


    private class ConnectBT extends AsyncTask<Void, Void, Void>{  // UI thread
        private boolean ConnectSuccess = true; //if it's here, it's almost connected

        @Override
        protected void onPreExecute(){
            progress = ProgressDialog.show(SmartPanela.this, "Connecting...", "Please wait!!!");  //show a progress dialog
        }

        @Override
        protected Void doInBackground(Void... devices){ //while the progress dialog is shown, the connection is done in background
            try{
                if (btSocket == null || !isBtConnected){
                    myBluetooth = BluetoothAdapter.getDefaultAdapter();//get the mobile bluetooth device
                    BluetoothDevice dispositivo = myBluetooth.getRemoteDevice(address);//connects to the device's address and checks if it's available
                    btSocket = dispositivo.createInsecureRfcommSocketToServiceRecord(myUUID);//create a RFCOMM (SPP) connection
                    BluetoothAdapter.getDefaultAdapter().cancelDiscovery();
                    btSocket.connect();//start connection

                    new Thread(){
                        @Override
                        public void run() {
                            try {
                                InputStream is = btSocket.getInputStream();
                                byte[] buffer = new byte[2048];
                                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                                int count = 0;
                                while(active){
                                    try {
                                        int v = is.read(buffer);
                                        String str = new String(buffer, 0, v, "UTF-8");
                                        if(str.contains(">>>")){
                                            //str.contains("<<<") &&
                                            str = str.substring(str.indexOf(">>>") + 3);
                                            if(str.contains("<<<")){
                                                str = str.substring(0, str.indexOf("<<<"));
                                                String [] dados = str.split(",");
                                                tempAtual = Double.parseDouble(dados[0]);
                                                heating = Boolean.parseBoolean(dados[1]);
                                                activeTimer = Boolean.parseBoolean(dados[2]);
                                                timeLeft = Integer.parseInt(dados[3]);
                                                tempFinal = Double.parseDouble(dados[4]);
                                            }
                                        }


                                            handler.post(new Runnable(){
                                                public void run() {
                                                    txTempAtual.setText(String.format("%.1f ºC", tempAtual));
                                                    txtAquecendo.setText(heating ? "SIM" : "NÃO");
                                                    txtCronometro.setText(activeTimer ? "SIM" : "NÃO");
                                                    txtTempFinalGrav.setText(String.format("%.1f ºC", tempFinal));
                                                    txtCronAtual.setText(String.format("%d min", timeLeft));
                                                }
                                            });
                                        Thread.sleep(1000);
                                    } catch (InterruptedException e) {
                                        Log.e("smartpanela", e.getMessage(), e);
                                    }
                                }
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                        }
                    }.start();
                }
            }
            catch (IOException e){
                ConnectSuccess = false;//if the try failed, you can check the exception here
            }
            return null;
        }
        @Override
        protected void onPostExecute(Void result){ //after the doInBackground, it checks if everything went fine
            super.onPostExecute(result);

            if (!ConnectSuccess){
                msg("Connection Failed. Is it a SPP Bluetooth? Try again.");
                finish();
            }
            else{
                msg("Connected.");
                isBtConnected = true;
            }
            progress.dismiss();
        }
    }
}
