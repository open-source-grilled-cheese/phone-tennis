package com.example.wifitennis2;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.fragment.app.ListFragment;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.icu.util.Output;
import android.media.MediaPlayer;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WpsInfo;
import android.net.wifi.p2p.WifiP2pConfig;
import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pDeviceList;
import android.net.wifi.p2p.WifiP2pInfo;
import android.net.wifi.p2p.WifiP2pManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Vibrator;
import android.text.PrecomputedText;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import org.w3c.dom.Text;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.StringWriter;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;

import android.os.VibrationEffect;
import android.os.Vibrator;

public class MainActivity extends AppCompatActivity {

    public static final String TAG = "Tennis";
    private boolean isWifiP2pEnabled = true;
    private boolean isGroupOwner = false;
    private final IntentFilter intentFilter = new IntentFilter();
    WifiP2pManager.Channel channel;
    WifiP2pManager manager;
    WifiReceiver receiver;

    private static final int POINT_TIME = 2000;
    private static final int WINDOW = 300;
    private static final int SHAKE_THRESHOLD = 1000;
    float lastUpdate = 0;

    private SensorManager sensorManager;
    Sensor accelerometer;

    public int portNumber = 5000;
    public ServerSocket serverSocket = null;
    public Socket clientSocket = null;

    public void setIsWifiP2pEnabled(boolean enabled) {
        this.isWifiP2pEnabled = enabled;
    }

    public boolean checkForHit() {
        MediaPlayer hitSound;

        boolean hit = false;
        float TestMultiplier = 0.5f;

        Vibrator vibe = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        hitSound = MediaPlayer.create(this, R.raw.coin_collect);

        long[] vibratePattern = new long[]{(long)(500.0f*TestMultiplier), (long)(500.0f*TestMultiplier), (long)(500.0f*TestMultiplier),(long)(500.0f*TestMultiplier),(long)(500.0f*TestMultiplier),(long)(300.0f*TestMultiplier), (long)(300.0f*TestMultiplier), (long)(300.0f*TestMultiplier), (long)(300.0f*TestMultiplier), (long)(300.0f*TestMultiplier), (long)(300.0f*TestMultiplier)};
        //for(long  Number : vibratePattern) {
        //Number = (long)((float) Number * SpeedMultiplier);
        //}
        int[] amplitudes = new int[]{15, 31, 45,45,45,45, 63, 74, 110, 127, 191};
        vibe.vibrate(VibrationEffect.createWaveform(vibratePattern, amplitudes, -1));

        hitSound.start();

        float startTime = System.currentTimeMillis();
        try {
            while (true) {
                Thread.sleep(POINT_TIME - WINDOW); //sleep until window starts
                break;
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        //now check every increment of time
        try {
            while (true) {
                float x = Swing.x;
                float y = Swing.y;
                float z = Swing.z;
                float last_x = Swing.last_x;
                float last_y = Swing.last_y;
                float last_z = Swing.last_z;

                float curTime = System.currentTimeMillis();

                if ((curTime - lastUpdate) > POINT_TIME - WINDOW && (curTime - lastUpdate) < POINT_TIME + WINDOW){ // sampling rate
                    float diffTime = (curTime - lastUpdate);
                    lastUpdate = curTime;

                    float speed = Math.abs(x+y+z- last_x - last_y - last_z)/ diffTime * 10000;
                    //if they are swinging
                    if (speed > SHAKE_THRESHOLD){
                        //swinging.setText("SWINGING");
                        hit = true;
                        hitSound.start();
                        return true;
                    }

                }
                //if they don't hit the ball in that time period
                else if((curTime - lastUpdate) > POINT_TIME + WINDOW && !hit){
                    hit = false;
                    return false;
                }

                Thread.sleep(10); //sleep until window starts

            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        return true;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Indicates a change in the Wi-Fi P2P status.
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION);

        // Indicates a change in the list of available peers.
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION);

        // Indicates the state of Wi-Fi P2P connectivity has changed.
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION);

        // Indicates this device's details have changed.
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION);

        manager = (WifiP2pManager) getSystemService(Context.WIFI_P2P_SERVICE);
        channel = manager.initialize(this, getMainLooper(), null);
        receiver = new WifiReceiver(manager, channel, this);

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            Log.d(TAG, "Permission not granted.");
            // TODO: Consider calling
            //    ActivityCompat#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.
            return;
        }
        manager.discoverPeers(channel, new WifiP2pManager.ActionListener() {
            @Override
            public void onSuccess() {
                Log.d(TAG, "successfully discovered");
            }

            @Override
            public void onFailure(int reasonCode) {
                Log.d(TAG, "failed to discover: " + reasonCode);
            }
        });
    }

    /* register the broadcast receiver with the intent values to be matched */
    @Override
    protected void onResume() {
        super.onResume();
        registerReceiver(receiver, intentFilter);
    }

    /* unregister the broadcast receiver */
    @Override
    protected void onPause() {
        super.onPause();
        unregisterReceiver(receiver);
    }

    public void doConnect(View view) {
        receiver.connect();
    }
}



class WifiReceiver extends BroadcastReceiver {

    public static final String TAG = "WifiReceiver";
    private List<WifiP2pDevice> peers = new ArrayList<>();

    private WifiP2pManager manager;
    private WifiP2pManager.Channel channel;
    private MainActivity activity;
    // private ConnectivityManager.NetworkCallback networkCallback =

    private WifiP2pManager.ConnectionInfoListener connectionInfoListener = new WifiP2pManager.ConnectionInfoListener() {
        @Override
        public void onConnectionInfoAvailable(WifiP2pInfo info) {
            // InetAddress from WifiP2pInfo struct.
            Log.d(TAG, "connection info available: called");
            Log.d(TAG, info.toString());

            TextView t = (TextView)activity.findViewById(R.id.t1);

            // After the group negotiation, we can determine the group owner.
            if (info.groupFormed && info.isGroupOwner) {
                // Do whatever tasks are specific to the group owner.
                // One common case is creating a server thread and accepting
                // incoming connections.
                Log.d(TAG, "we are group owner");
                t.setText("Player 1");
                new PlayerOneTask(activity, activity.findViewById(R.id.t2)).execute();
            } else if (info.groupFormed) {
                // The other device acts as the client. In this case,
                // you'll want to create a client thread that connects to the group
                // owner.
                Log.d(TAG, "we are client");
                t.setText("Player 2");
                new PlayerTwoTask(activity, activity.findViewById(R.id.t2), info.groupOwnerAddress).execute();

            } else {
                Log.d(TAG, "group not formed");
                t.setText("Failed to connect");
            }
        }

    };
    private WifiP2pManager.PeerListListener peerListListener = new WifiP2pManager.PeerListListener() {
        @Override
        public void onPeersAvailable(WifiP2pDeviceList peerList) {
            Collection<WifiP2pDevice> refreshedPeers = peerList.getDeviceList();
            if (!refreshedPeers.equals(peers)) {
                peers.clear();
                peers.addAll(refreshedPeers);

                for (WifiP2pDevice dev : peers) {
                    Log.d(TAG, "found device: " + dev.deviceName);
                }

                // If an AdapterView is backed by this data, notify it
                // of the change. For instance, if you have a ListView of
                // available peers, trigger an update.
                // ((WiFiPeerListAdapter) getListAdapter()).notifyDataSetChanged();

                // Perform any other updates needed based on the new list of
                // peers connected to the Wi-Fi P2P network.
            }

            if (peers.size() == 0) {
                Log.d(TAG, "No devices found");
                return;
            }
        }
    };

    public WifiReceiver(WifiP2pManager manager, WifiP2pManager.Channel channel,
                        MainActivity activity) {
        super();
        this.manager = manager;
        this.channel = channel;
        this.activity = activity;
    }

    public void connect() {
        // pick the first device
        WifiP2pDevice device = peers.get(0);
        WifiP2pConfig config = new WifiP2pConfig();
        config.deviceAddress = device.deviceAddress;
        config.wps.setup = WpsInfo.PBC;

        if (ActivityCompat.checkSelfPermission(activity, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            Log.d(TAG, "access denied");
            // TODO: Consider calling
            //    ActivityCompat#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.
            return;
        }
        manager.connect(channel, config, new WifiP2pManager.ActionListener() {
            @Override
            public void onSuccess() {
                Log.v(TAG, "connect success");
                manager.requestConnectionInfo(channel, connectionInfoListener);
            }

            @Override
            public void onFailure(int reason) {
                Toast.makeText(activity, "Connect failed. Retry.",
                        Toast.LENGTH_SHORT).show();
            }
        });

    }

    @Override
    public void onReceive(Context context, Intent intent) {
        Log.d(TAG, "received intent: " + intent);
        String action = intent.getAction();
        if (WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION.equals(action)) {
            // Determine if Wifi P2P mode is enabled or not, alert
            // the Activity.
            int state = intent.getIntExtra(WifiP2pManager.EXTRA_WIFI_STATE, -1);
            Log.d(TAG, "extra wifi state: "  +  state);
            if (state == WifiP2pManager.WIFI_P2P_STATE_ENABLED) {
                activity.setIsWifiP2pEnabled(true);
            } else {
                activity.setIsWifiP2pEnabled(false);
            }
        } else if (WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION.equals(action)) {
            Log.d(TAG, "wifi p2p peers changed action");
            // request available peers from the wifi p2p manager. This is an
            // asynchronous call and the calling activity is notified with a
            // callback on PeerListListener.onPeersAvailable()
            if (manager != null) {
                if (ActivityCompat.checkSelfPermission(activity , Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                    Log.v(TAG, "fine location permission denied");
                    // TODO: Consider calling
                    //    ActivityCompat#requestPermissions
                    // here to request the missing permissions, and then overriding
                    //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
                    //                                          int[] grantResults)
                    // to handle the case where the user grants the permission. See the documentation
                    // for ActivityCompat#requestPermissions for more details.
                    return;
                }
                manager.requestPeers(channel, peerListListener);
            }
            // The peer list has changed! We should probably do something about
            // that.

        } else if (WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION.equals(action)) {
                Log.d(TAG, "wifi p2p connection changed action");
            // Connection state changed! We should probably do something about
            // that.

        } else if (WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION.equals(action)) {
            Log.d(TAG, "wifi p2p this device changed action");
            //DeviceListFragment fragment = (DeviceListFragment) activity.getFragmentManager()
            //        .findFragmentById(R.id.frag_list);
            //fragment.updateThisDevice((WifiP2pDevice) intent.getParcelableExtra(
            //        WifiP2pManager.EXTRA_WIFI_P2P_DEVICE));

        }
    }

    public static class PlayerOneTask extends AsyncTask<Void, Void, String> {
        private MainActivity activity;
        private Context context;
        private View statusText;

        public PlayerOneTask(MainActivity activity, View statusText ) {
            this.activity = activity;
            this.statusText = statusText;
        }

        @Override
        protected String doInBackground(Void... params) {
            try {
                // write data "served"
                // serversocket.accept()
                ServerSocket serverSocket = new ServerSocket(5000);
                Socket client = serverSocket.accept();
                while(true) {
                    OutputStream outputStream = client.getOutputStream();
                    if(activity.checkForHit()) {
                        outputStream.write("hit".getBytes(StandardCharsets.UTF_8));
                        InputStream inputstream = client.getInputStream();
                        BufferedReader r = new BufferedReader(new InputStreamReader(inputstream));
                        Log.d(TAG, "from client: " + r.readLine());
                    } else {
                        Log.d(TAG, "finished game");
                        serverSocket.close();
                        return null;
                    }
                }
            } catch (IOException e) {
                Log.e(TAG, e.getMessage());
                return null;
            }

        }

        @Override
        protected void onPostExecute(String result) {
            if (result != null) {
                Log.d(TAG, "accepted input: " + result);
            }
        }
    }
    public static class PlayerTwoTask extends AsyncTask<Void, Void, String> {
        private MainActivity activity;
        private View statusText;
        private InetAddress groupOwnerAddress;

        public PlayerTwoTask(MainActivity activity, View statusText, InetAddress groupOwnerAddress) {
            this.activity = activity;
            this.statusText = statusText;
            this.groupOwnerAddress = groupOwnerAddress;
        }

        @Override
        protected String doInBackground(Void... params) {
            // wait for 'serve' from player 1
            // check for hit
            // send "miss" or "hit"
            Socket clientSocket = new Socket();
            try {
                clientSocket.bind(null);
                clientSocket.connect(new InetSocketAddress(groupOwnerAddress, 5000));
            } catch (IOException e) {
                Log.e(TAG, e.toString());
                return null;
            }
            while(true) {
                try {
                    InputStream inputStream = clientSocket.getInputStream();
                    BufferedReader r = new BufferedReader(new InputStreamReader(inputStream));
                    Log.d(TAG, "message from server: " + r.readLine()); // wait for hit signal from server
                    boolean hit = activity.checkForHit();
                    OutputStream outputStream = clientSocket.getOutputStream();
                    if(hit) {
                        outputStream.write("hit".getBytes(StandardCharsets.UTF_8));
                    } else {
                        outputStream.write("miss".getBytes(StandardCharsets.UTF_8));
                        return null;
                    }
                    // outputStream.write("data to send to server".getBytes(StandardCharsets.UTF_8));
                    // outputStream.close();
                } catch (IOException e) {
                    //catch logic
                    Log.e(TAG, e.toString());
                }
                finally {
                    if (clientSocket != null) {
                        if (clientSocket.isConnected()) {
                            try {
                                clientSocket.close();
                            } catch (IOException e) {
                                Log.e(TAG, e.toString());
                            }
                        }
                    }
                }
            }
        }

        @Override
        protected void onPostExecute(String result) {
            Log.d(TAG, "finished executing client");
        }
    }
}

class Swing extends AppCompatActivity implements SensorEventListener {

    private static final String TAG = "Swing";

    private Button toss;

    private long lastUpdate = 0;
    public static float last_x, last_y, last_z;
    private static final int SHAKE_THRESHOLD = 1000;
    private static final int VIBRATION_TIME = 200;
    private static final int POLLING_TIME = 50;
    private static final int SERVE_TIME = 2000;
    private static final int POINT_TIME = 2000;
    private static final int SWING_WINDOW = 300;
    //private float swingElapsedTime = 0;
    //private float swingStartTime;
    private float pointStartTime;
    private boolean checkServing;
    private boolean prevSwing;
    private boolean hit;

    private SensorManager sensorManager;
    Sensor accelerometer;

    TextView xValue, yValue, zValue, swinging;

    Vibrator vibrator;

    MediaPlayer serve;

    //public boolean isHitting = false;

    public static float x, y, z;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
       /* setContentView(R.layout.activity_swing);

        //toss ball to serve
        toss = (Button) findViewById(R.id.toss_ball);
        toss.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                serve.start();
                //startPoint();
                //vibe.vibrate(1000);
                //vibe.vibrate(VibrationEffect.createOneShot(1000, 255));
            }
        }); */


        //xValue = (TextView) findViewById(R.id.xValue);
        //yValue = (TextView) findViewById(R.id.yValue);
        //zValue = (TextView) findViewById(R.id.zValue);
        //swinging = (TextView) findViewById(R.id.swinging);

        vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);


        Log.d(TAG, "onCreate: Initializing Sensor Services");
        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);

        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        sensorManager.registerListener(Swing.this, accelerometer, SensorManager.SENSOR_DELAY_NORMAL);
        Log.d(TAG, "onCreate: Registered accelerometer listener");
        //swinging.setText("Not swinging");
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int i) {

    }

    @Override
    public void onSensorChanged(SensorEvent sensorEvent) {
        Log.d(TAG, "onSensorChanged: X: " + sensorEvent.values[0] + " Y: " + sensorEvent.values[1] + " Z: " + sensorEvent.values[2]);
        //set previous values before grabbing the new ones
        last_x = x;
        last_y = y;
        last_z = z;

        x = sensorEvent.values[0];
        y = sensorEvent.values[1];
        z = sensorEvent.values[2];


    }
}

