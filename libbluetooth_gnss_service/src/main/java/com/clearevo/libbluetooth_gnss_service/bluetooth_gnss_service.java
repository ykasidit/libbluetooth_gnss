package com.clearevo.libbluetooth_gnss_service;


import android.app.AppOpsManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.content.Intent;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.util.Log;
import android.widget.Toast;
import android.location.LocationManager;
import android.location.LocationProvider;
import android.os.Build;
import android.os.SystemClock;
import android.location.Location;

import com.clearevo.libecodroidbluetooth.rfcomm_conn_callbacks;
import com.clearevo.libecodroidbluetooth.rfcomm_conn_mgr;
import com.clearevo.libecodroidgnss_parse.gnss_sentence_parser;

import java.util.Date;
import java.util.HashMap;
import java.util.List;

import static com.clearevo.libecodroidgnss_parse.gnss_sentence_parser.fromHexString;


public class bluetooth_gnss_service extends Service implements rfcomm_conn_callbacks, gnss_sentence_parser.gnss_parser_callbacks {

    static final String TAG = "btgnss_service";

    rfcomm_conn_mgr g_rfcomm_mgr = null;
    private gnss_sentence_parser m_gnss_parser = new gnss_sentence_parser();

    final String EDG_DEVICE_PREFIX = "EcoDroidGPS";
    public static final String BROADCAST_ACTION_NMEA = "com.clearevo.bluetooth_gnss.NMEA";
    Thread m_connecting_thread = null;
    Handler m_handler = new Handler();
    String m_bdaddr = "";
    boolean m_auto_reconnect = false;
    boolean m_secure_rfcomm = true;
    Class m_target_activity_class;
    int m_icon_id;

    boolean m_ubx_mode = true;
    boolean m_ubx_send_enable_extra_used_packets = true;
    boolean m_ubx_send_disable_extra_used_packets = false;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // If we get killed, after returning from here, restart
        Log.d(TAG, "onStartCommand");

        if (intent != null) {
            try {
                m_bdaddr = intent.getStringExtra("bdaddr");
                m_secure_rfcomm = intent.getBooleanExtra("secure", true);
                m_auto_reconnect = intent.getBooleanExtra("reconnect", false);
                String cn = intent.getStringExtra("activity_class_name");
                if (cn == null) {
                    throw new Exception("activity_class_name not specified");
                }
                m_target_activity_class = Class.forName(cn);
                if (!intent.hasExtra("activity_icon_id")){
                    throw new Exception("activity_icon_id not specified");
                }
                m_icon_id = intent.getIntExtra("activity_icon_id", 0);

                if (m_bdaddr == null) {
                    String msg = "bluetooth_gnss_service: startservice: Target Bluetooth device not specifed - cannot start...";
                    Log.d(TAG, msg);
                    toast(msg);
                } else {
                    Log.d(TAG, "onStartCommand got bdaddr");
                    int start_ret = connect(m_bdaddr, m_secure_rfcomm, getApplicationContext());
                    if (start_ret == 0) {
                        start_foreground("Connecting...", "target device: " + m_bdaddr, "");
                    }
                }
            } catch (Exception e) {
                String msg = "bluetooth_gnss_service: startservice: parse intent failed - cannot start... - exception: "+Log.getStackTraceString(e);
                Log.d(TAG, msg);
            }
        } else {
            String msg = "bluetooth_gnss_service: startservice: null intent - cannot start...";
            Log.d(TAG, msg);
            toast(msg);
        }

        return START_REDELIVER_INTENT;
    }

    public boolean is_bt_connected()
    {
        if (g_rfcomm_mgr != null && g_rfcomm_mgr.is_bt_connected()) {
            return true;
        }
        return false;
    }


    public boolean is_conn_thread_alive() {
        return m_connecting_thread != null && m_connecting_thread.isAlive();
    }

    Thread m_auto_reconnect_thread = null;
    public static final long AUTO_RECONNECT_MILLIS = 30*1000;

    void start_auto_reconnect_thread()
    {
        if (m_auto_reconnect) {

            if (m_auto_reconnect_thread != null && m_auto_reconnect_thread.isAlive()) {
                //interrupt old thread so it will end...
                try {
                    m_auto_reconnect_thread.interrupt();
                } catch (Exception e) {
                    Log.d(TAG, "interrrupt old m_auto_reconnect_thread failed exception: "+Log.getStackTraceString(e));
                }
            }

            m_auto_reconnect_thread = new Thread() {

                public void run(){

                    Log.d(TAG, "auto-reconnect thread: "+this.hashCode()+" START");

                    while (m_auto_reconnect_thread == this && m_auto_reconnect) {

                        try {
                            Log.d(TAG, "auto-reconnect thread: "+this.hashCode()+" - start sleep");
                            Thread.sleep(AUTO_RECONNECT_MILLIS);
                        } catch (InterruptedException e) {
                            Log.d(TAG, "auto-reconnect thread: "+this.hashCode()+" - sleep interrupted likely by close() - break out of loop and end now");
                            break;
                        }

                        //connect() must be run from main service thread in case it needs to post
                        if (m_bdaddr != null && m_bdaddr.length() > 0 && !is_bt_connected() && !is_conn_thread_alive()) {
                            Log.d(TAG, "auto-reconnect thread - has target dev and not connected - try reconnect...");
                            m_handler.post(new Runnable() {
                                @Override
                                public void run() {
                                    toast("Auto-Reconnect: Trying to connect...");
                                    connect(m_bdaddr, m_secure_rfcomm, getApplicationContext());
                                }
                            });
                        } else {
                            Log.d(TAG, "auto-reconnect thread - likely already connecting or already connected or no target dev");
                        }


                    }

                    Log.d(TAG, "auto-reconnect thread: "+this.hashCode()+" END");
                }

            };
            m_auto_reconnect_thread.start();

        }
    }

    int connect(String bdaddr, boolean secure, Context context)
    {
        int ret = -1;

        try {


            if (is_conn_thread_alive()) {
                toast("connection already ongoing - please wait...");
                return 1;
            } else if (g_rfcomm_mgr != null && g_rfcomm_mgr.is_bt_connected()) {
                toast("already connected - press Back to disconnect and exit...");
                return 2;
            } else {

                m_gnss_parser = new gnss_sentence_parser(); //use new instance
                m_gnss_parser.set_callback(this);

                toast("connecting to: "+bdaddr);
                if (g_rfcomm_mgr != null) {
                    g_rfcomm_mgr.close();
                }
                BluetoothAdapter.getDefaultAdapter().cancelDiscovery();
                BluetoothDevice dev = BluetoothAdapter.getDefaultAdapter().getRemoteDevice(bdaddr);

                if (dev == null) {
                    toast("Please pair your Bluetooth GPS Receiver in phone Bluetooth Settings...");
                    throw new Exception("no paired bluetooth devices...");
                } else {
                    //ok
                }
                Log.d(TAG, "using dev: " + dev.getAddress());
                g_rfcomm_mgr = new rfcomm_conn_mgr(dev, secure, this, context);

                start_connecting_thread();
                if (m_auto_reconnect) {
                    start_auto_reconnect_thread();
                }
            }
            ret = 0;
        } catch (Exception e) {
            String emsg = Log.getStackTraceString(e);
            Log.d(TAG, "connect() exception: "+emsg);
            toast("Connect failed: "+emsg);
        }

        return ret;
    }

    //return true if was connected
    public boolean close()
    {
        try {
            m_auto_reconnect_thread.interrupt();
        } catch (Exception e) {
        }
        m_auto_reconnect_thread = null;

        if (g_rfcomm_mgr != null) {
            boolean was_connected = g_rfcomm_mgr.is_bt_connected();
            g_rfcomm_mgr.close();
            return was_connected;
        }

        return false;
    }

    void toast(String msg)
    {
        //dont toast if running in background
        if (m_is_bound) {
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
            Log.d(TAG, "toast msg: "+msg);
        } else {
            Log.d(TAG, "m_is_bound false so omit: toast msg: "+msg);
        }


    }

    public void on_rfcomm_connected()
    {
        Log.d(TAG, "on_rfcomm_connected()");
        m_handler.post(
                new Runnable() {
                    @Override
                    public void run() {
                        toast("Connected...");
                        updateNotification("Connected...", "Target device: "+m_bdaddr, "");
                    }
                }
        );

        //try send some initial ubx queries to device:
        if (m_ubx_mode && m_ubx_send_enable_extra_used_packets) {
            new Thread() {
                public void run() {
                    try {
                        g_rfcomm_mgr.add_send_buffer(fromHexString("B5 62 06 01 03 00 F1 00 01 FC 13"));  //enable pubx config data - for pubx accuracies
                        g_rfcomm_mgr.add_send_buffer(fromHexString("B5 62 0A 04 00 00 0E 34"));  //poll ubx-mon-ver for hardware/firmware info of the receiver
                        g_rfcomm_mgr.add_send_buffer(fromHexString("B5 62 0A 28 00 00 32 A0"));  //poll ubx-mon-gnss default system-settings
                    } catch (Exception e) {
                        Log.d(TAG, "m_ubx_send_enable_extra_used_packets exception: "+Log.getStackTraceString(e));
                    }
                }
            }.start();
        }
    }



    public void on_rfcomm_disconnected()
    {
        Log.d(TAG, "on_rfcomm_disconnected() m_auto_reconnect: "+m_auto_reconnect);
        m_handler.post(
                new Runnable() {
                    @Override
                    public void run() {
                        toast("Disconnected...");
                        updateNotification("Disconnected...", "Target device: "+m_bdaddr, "");
                    }
                }
        );

    }

    public void start_connecting_thread()
    {
        m_connecting_thread = new Thread() {
            public void run() {
                try {
                    g_rfcomm_mgr.connect();
                } catch (Exception e) {
                    m_handler.post(
                            new Runnable() {
                                @Override
                                public void run() {
                                    toast("Connect failed... Please make sure your device is ready...");
                                    updateNotification("Connect failed...", "Target device: "+m_bdaddr, "");
                                }
                            }
                    );
                    Log.d(TAG, "g_rfcomm_mgr connect exception: "+Log.getStackTraceString(e));
                }
            }
        };

        m_connecting_thread.start();
    }

    public void on_readline(byte[] readline)
    {
        try {
            Log.d(TAG, "on_readline()");
            String parsed_nmea = m_gnss_parser.parse(readline);

            /* too much cpu usage - anyone who wants this can uncomment and rebuild project
            if (parsed_nmea != null) {
                Intent intent = new Intent();
                intent.setAction(BROADCAST_ACTION_NMEA);
                intent.putExtra("NMEA", parsed_nmea);
                getApplicationContext().sendBroadcast(intent);
            }
            */
        } catch (Exception e) {
            Log.d(TAG, "bluetooth_gnss_service on_readline parse exception: "+Log.getStackTraceString(e));
        }
    }

    public void on_readline_stream_connected()
    {
        Log.d(TAG, "on_readline_stream_connected()");
        m_handler.post(
                new Runnable() {
                    @Override
                    public void run() {
                        toast("Data stream connected...");
                        updateNotification("Connected...", "Target device: "+m_bdaddr, "");
                    }
                }
        );
    }

    public void on_readline_stream_closed()
    {
        Log.d(TAG, "on_readline_stream_closed()");
        m_handler.post(
                new Runnable() {
                    @Override
                    public void run() {
                        toast("Data stream disconnected...");
                        updateNotification("Disconnected...", "Target device: "+m_bdaddr, "");
                    }
                }
        );
    }

    public void on_target_tcp_connected(){}
    public void on_target_tcp_disconnected(){}


    @Override
    public void onCreate() {
        Log.d(TAG, "onCreate()");
        super.onCreate();
    }

    void start_foreground(String title, String text, String ticker)
    {
        Log.d(TAG, "start_forgroud 0");

        startForeground(1, getMyActivityNotification(title, text, ticker));
        Log.d(TAG, "start_forgroud end");
    }

    String notification_channel_id = "BLUETOOTH_GNSS_CHANNEL_ID";
    String notification_name = "BLUETOOTH_GNSS";

    private Notification getMyActivityNotification(String title, String text, String ticker){

        Intent notificationIntent = new Intent(this, m_target_activity_class);
        PendingIntent pendingIntent =
                PendingIntent.getActivity(this, 0, notificationIntent, 0);


        NotificationManager mNotificationManager =
                (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(notification_channel_id,
                    notification_name,
                    NotificationManager.IMPORTANCE_DEFAULT);
            channel.setDescription("Bluetooth GNSS Status");
            mNotificationManager.createNotificationChannel(channel);
        }

        Notification notification =
                new Notification.Builder(this, notification_channel_id)
                        .setContentTitle(title)
                        .setContentText(text)
                        .setSmallIcon(m_icon_id)
                        .setContentIntent(pendingIntent)
                        .setTicker(ticker)
                        .build();

        return notification;
    }

    private void updateNotification(String title, String text, String ticker) {

        if (ticker == null || ticker.length() == 0) {
            ticker = new Date().toString();
        }

        Notification notification = getMyActivityNotification(title, text, ticker);

        NotificationManager mNotificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        mNotificationManager.notify(1, notification);
    }

    public static boolean is_location_enabled(Context context)
    {
        Log.d(TAG, "is_location_enabled() 0");
        LocationManager lm = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
        boolean gps_enabled = false;

        try {
            Log.d(TAG, "is_location_enabled() getting providers");
            List<String> providers = lm.getAllProviders();
            for (String p : providers) {
                Log.d(TAG,"location provider enabled: "+p);
            }

            Log.d(TAG, "is_location_enabled() 1");

            gps_enabled = lm.isProviderEnabled(LocationManager.GPS_PROVIDER);
        } catch(Exception e) {
            Log.d(TAG, "check gps_enabled exception: "+Log.getStackTraceString(e));
        }
        return gps_enabled;

    }

    public static boolean is_mock_location_enabled(Context context, int app_uid, String app_id_string)
    {
        LocationManager locationManager = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
        boolean mock_enabled = false;
        try {

            if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                Log.d(TAG,"is_mock_location_enabled Build.VERSION.SDK_INT >= Build.VERSION_CODES.M");
                AppOpsManager opsManager = (AppOpsManager) context.getSystemService(Context.APP_OPS_SERVICE);
                mock_enabled = (opsManager.checkOp(AppOpsManager.OPSTR_MOCK_LOCATION, app_uid, app_id_string)== AppOpsManager.MODE_ALLOWED);
            } else {
                // in marshmallow this will always return true
                Log.d(TAG,"is_mock_location_enabled older models");
                mock_enabled = !android.provider.Settings.Secure.getString(context.getContentResolver(), "mock_location").equals("0");
            }
        } catch(Exception e) {
            Log.d(TAG, "check mock_enabled exception: "+Log.getStackTraceString(e));
        }
        Log.d(TAG,"is_mock_location_enabled ret "+mock_enabled);
        return mock_enabled;
    }

    private void setMock(double latitude, double longitude, double altitude, float accuracy) {
        LocationManager locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        locationManager.addTestProvider (LocationManager.GPS_PROVIDER,
                "requiresNetwork" == "",
                "requiresSatellite" == "",
                "requiresCell" == "",
                "hasMonetaryCost" == "",
                "supportsAltitude" == "",
                "supportsSpeed" == "",
                "supportsBearing" == "",
                android.location.Criteria.POWER_LOW,
                android.location.Criteria.ACCURACY_FINE);

        Location newLocation = new Location(LocationManager.GPS_PROVIDER);

        newLocation.setLatitude(latitude);
        newLocation.setLongitude(longitude);
        newLocation.setAccuracy(accuracy);
        newLocation.setAltitude(altitude);
        newLocation.setAccuracy(accuracy);
        newLocation.setTime(System.currentTimeMillis());
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            newLocation.setElapsedRealtimeNanos(SystemClock.elapsedRealtimeNanos());
        }
        locationManager.setTestProviderEnabled(LocationManager.GPS_PROVIDER, true);

        locationManager.setTestProviderStatus(LocationManager.GPS_PROVIDER,
                LocationProvider.AVAILABLE,
                null,System.currentTimeMillis());

        locationManager.setTestProviderLocation(LocationManager.GPS_PROVIDER, newLocation);
    }


    // Binder given to clients
    private final IBinder m_binder = new LocalBinder();
    gnss_sentence_parser.gnss_parser_callbacks m_activity_for_nmea_param_callbacks;
    long last_set_mock_location_ts = 0;

    public void set_callback(gnss_sentence_parser.gnss_parser_callbacks cb)
    {
        m_activity_for_nmea_param_callbacks = cb;
    }


    public final String[] GGA_MESSAGE_TALKER_TRY_LIST = {
            "GN",
            "GA",
            "GB",
            "GP",
            "GL"
    };

    double DEFAULT_UBLOX_M8030_CEP = 2.0;
    double DEFAULT_UBLOX_ZED_F9P_CEP = 1.5;

    public double get_connected_device_CEP()
    {
        //TODO - later set per detected device or adjustable by user in settings
        return DEFAULT_UBLOX_M8030_CEP;
    }

    @Override
    public void on_updated_nmea_params(HashMap<String, Object> params_map) {

        //try set_mock


            double lat, lon, alt, hdop;

            for (String talker : GGA_MESSAGE_TALKER_TRY_LIST) {

                try {
                    if (params_map.containsKey(talker+"_lat_ts")) {
                        long new_ts = (long) params_map.get(talker+"_lat_ts");
                        if (new_ts != last_set_mock_location_ts) {
                            lat = (double) params_map.get(talker+"_lat");
                            lon = (double) params_map.get(talker+"_lon");
                            alt = (double) params_map.get(talker+"_alt");
                            hdop = (double) params_map.get(talker+"_hdop");
                            double accuracy = -1.0;
                            if (params_map.containsKey("UBX_POSITION_hAcc")) {
                                try {
                                    accuracy = Double.parseDouble((String) params_map.get("UBX_POSITION_hAcc"));
                                } catch (Exception e) {}
                            }

                            //if not ubx or ubx conv failed...
                            if (accuracy == -1.0) {

                                accuracy = hdop * get_connected_device_CEP();
                            }
                            setMock(lat, lon, alt, (float) accuracy);
                            m_gnss_parser.put_param("", "lat", lat);
                            m_gnss_parser.put_param("", "lon", lon);
                            m_gnss_parser.put_param("", "alt", alt);
                            m_gnss_parser.put_param("", "hdop", hdop);
                            m_gnss_parser.put_param("", "accuracy", accuracy);
                            m_gnss_parser.put_param("", "location_from_talker", talker);
                            m_gnss_parser.put_param("", "mock_location_set_ts", System.currentTimeMillis());

                            break;
                        } else {
                            //omit as same ts as last
                        }
                    }
                } catch (Exception e) {
                    Log.d(TAG, "bluetooth_gnss_service on_updated_nmea_params exception: "+Log.getStackTraceString(e));
                }
            }


        //report to activity
        try {
            if (m_activity_for_nmea_param_callbacks != null) {
                m_activity_for_nmea_param_callbacks.on_updated_nmea_params(params_map);
            }
        } catch (Exception e) {
            Log.d(TAG, "bluetooth_gnss_service call callback in m_activity_for_nmea_param_callbacks exception: "+Log.getStackTraceString(e));
        }

    }

    /**
     * Class used for the client Binder.  Because we know this service always
     * runs in the same process as its clients, we don't need to deal with IPC.
     */
    public class LocalBinder extends Binder {
        public bluetooth_gnss_service getService() {
            // Return this instance of LocalService so clients can call public methods
            return bluetooth_gnss_service.this;
        }
    }

    boolean m_is_bound = false;

    @Override
    public boolean onUnbind(Intent intent) {
        m_is_bound = false;
        return super.onUnbind(intent);
    }

    @Override
    public IBinder onBind(Intent intent) {
        m_is_bound = true;
        Log.d(TAG, "onBind()");
        return m_binder;
    }

    @Override
    public void onDestroy() {
        Log.d(TAG, "onDestroy()");
        boolean was_connected = close();

        Toast.makeText(this, "Stopped Bluetooth GNSS Service...", Toast.LENGTH_SHORT).show();
    }
}