package com.example.dog_tracker_java;

import androidx.fragment.app.FragmentActivity;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.widget.TextView;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class MapsActivity extends FragmentActivity implements OnMapReadyCallback {

    private GoogleMap mMap;
    BluetoothDevice mDevice;
    BluetoothAdapter bluetoothAdapter;
    int updateCnt = 1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_maps);
        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);
    }

    /**
     * Manipulates the map once available.
     * This callback is triggered when the map is ready to be used.
     * This is where we can add markers or lines, add listeners or move the camera. In this case,
     * we just add a marker near Sydney, Australia.
     * If Google Play services is not installed on the device, the user will be prompted to install
     * it inside the SupportMapFragment. This method will only be triggered once the user has
     * installed Google Play services and returned to the app.
     */
    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;

        // Add a marker and move the camera39.1500263, -86.5633982
        LatLng initialPos = new LatLng(39.1500263, -86.5633982);
        mMap.addMarker(new MarkerOptions()
                           .position(initialPos)
                           .title("Doggo")
                           .icon(BitmapDescriptorFactory.fromResource(R.drawable.mika_pink_ic)));
        mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(initialPos, 15.0f));

        bluetoothOn();

    }

    public void bluetoothOn() {

        final int REQUEST_ENABLE_BT = 1;

        Log.d("TESTING", "TESTING THE LOGGER HELLO HELLO");
        // Initializes Bluetooth adapter.
        final BluetoothManager bluetoothManager =
                (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);

        bluetoothAdapter = bluetoothManager.getAdapter();

        // Ensures Bluetooth is available on the device and it is enabled. If not,
        // displays a dialog requesting user permission to enable Bluetooth.
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled()) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
        }

        Set<BluetoothDevice> pairedDevices = bluetoothAdapter.getBondedDevices();
        if (pairedDevices.size() > 0) {
            for (BluetoothDevice device : pairedDevices) {
                if (device.getName().equals("Dog_Tracker_Rx")) {
                    mDevice = device;
                    Log.d("INSIDE PAIRED", "HELLO " + mDevice.getName());
                }
            }
        }

        if (mDevice != null) {
            Log.d("CONNECT THREAD", "HELLO HELLO");
            ConnectThread mConnectThread = new ConnectThread(mDevice);
            mConnectThread.start();
        }

    } // bluetoothOn

    private class ConnectThread extends Thread {
        private final BluetoothSocket mmSocket;
        private final BluetoothDevice mmDevice;
        private final UUID MY_UUID = UUID.fromString("00001101-0000-1000-8000-00805f9b34fb");

        // Constructor that will accept the device when instance is created
        public ConnectThread(BluetoothDevice device) {
            BluetoothSocket tmp = null;
            mmDevice = device;
            try {
                tmp = device.createRfcommSocketToServiceRecord(MY_UUID);
            } catch (IOException e) { }
            mmSocket = tmp;
        }
        public void run() {
            bluetoothAdapter.cancelDiscovery();
            try {
                Log.d("SOCKETTRY", "HELLO");
                mmSocket.connect();
            } catch (IOException connectException) {
                try {
                    Log.d("SOCKETCLOSE", "HELLO Connection to " + mDevice.getName() + " at "
                          + mDevice.getAddress() + " failed:" + connectException.getMessage());
                    mmSocket.close();
                } catch (IOException closeException) { }
                return;
            }
            Log.d("AFTER RUNNER", "HELLO");
            ConnectedThread mConnectedThread = new ConnectedThread(mmSocket);
            mConnectedThread.start();
        }
        public void cancel() {
            try {
                mmSocket.close();
            } catch (IOException e) { }
        }
    } // ConnectThread Device

    private class ConnectedThread extends Thread {
        private final BluetoothSocket mmSocket;
        private final InputStream mmInStream;
        private final OutputStream mmOutStream;
        public ConnectedThread(BluetoothSocket socket) {
            mmSocket = socket;
            InputStream tmpIn = null;
            OutputStream tmpOut = null;
            try {
                tmpIn = socket.getInputStream();
                tmpOut = socket.getOutputStream();
            } catch (IOException e) { Log.d("EXCEPTION", "HELLO " + e.getMessage());}
            mmInStream = tmpIn;
            mmOutStream = tmpOut;
        }
        public void run() {
            byte[] buffer = new byte[1024];
            int begin = 0;
            int bytes = 0;
            while (true) {
                try {
                    bytes += mmInStream.read(buffer, bytes, buffer.length - bytes);
                    for (int i = begin; i < bytes; i++) {
                        if (buffer[i] == "#".getBytes()[0]) {
                            mHandler.obtainMessage(1, begin, i, buffer).sendToTarget();
                            begin = i + 1;
                            if (i == bytes - 1) {
                                bytes = 0;
                                begin = 0;
                            }
                        }
                    }
                } catch (IOException e) {
                    Log.d("CAUGHT", "HELLO " + e.getMessage());
                    break;
                }
            }
        }
        public void write(byte[] bytes) {
            try {
                mmOutStream.write(bytes);
            } catch (IOException e) { }
        }
        public void cancel() {
            try {
                mmSocket.close();
            } catch (IOException e) { }
        }
    } //ConnectedThread Socket

    @SuppressLint("HandlerLeak")
    Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            byte[] writeBuf = (byte[]) msg.obj;
            int begin = (int)msg.arg1;
            int end = (int)msg.arg2;

            switch(msg.what) {
                case 1:
                    String writeMessage = new String(writeBuf);
                    List<String> data = Arrays.asList(writeMessage.split("\\s*,\\s*"));
                    Log.d("HELLOLAT", data.get(0));
                    Log.d("HELLOLON", data.get(1));
                    Log.d("HELLOSS", data.get(2));

                    // Only update the marker once every 10 times
                    if (updateCnt > 10) {
                        updateLatLon(data.get(0), data.get(1));
                        updateCnt = 1;
                    } else {
                        updateCnt++;
                    }
                    updateSignalStrength(data.get(2));
                    break;
            }
        }
    }; //Handler

    public void updateLatLon(String lat, String lon) {
        Log.d("LAT", "HELLO " + lat);
        Log.d("LON", "HELLO " + lon);
        try {
            Double dlat = Double.parseDouble(lat);
            Double dlon = Double.parseDouble(lon);

            mMap.clear(); //Keeps markers from persisting on map (Mem leak potential?)

            LatLng updatedPos = new LatLng(dlat, dlon);
            mMap.addMarker(new MarkerOptions()
                    .position(updatedPos)
                    .title("Doggo")
                    .icon(BitmapDescriptorFactory.fromResource(R.drawable.mika_pink_ic)));
            mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(updatedPos, 18.0f));
        } catch (Exception e) {Log.d("EXCEPTION", "HELLO " + e.getMessage()); }
    }

    public void updateSignalStrength(String ss) {
        TextView signalView = findViewById(R.id.signalStrength);
        signalView.setText("Signal Strength: " + ss + "dB");
    }
} //MapsActivity




