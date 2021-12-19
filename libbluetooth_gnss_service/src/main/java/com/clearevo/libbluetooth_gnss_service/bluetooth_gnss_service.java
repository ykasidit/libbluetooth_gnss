package com.clearevo.libbluetooth_gnss_service;


import android.app.AppOpsManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanRecord;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.location.Criteria;
import android.net.Uri;
import android.os.Binder;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.ParcelUuid;


import android.util.Log;
import android.widget.Toast;
import android.location.LocationManager;
import android.location.LocationProvider;
import android.os.Build;
import android.os.SystemClock;
import android.location.Location;

import androidx.core.app.NotificationCompat;
import androidx.documentfile.provider.DocumentFile;

import com.clearevo.libecodroidbluetooth.ntrip_conn_callbacks;
import com.clearevo.libecodroidbluetooth.ntrip_conn_mgr;
import com.clearevo.libecodroidbluetooth.rfcomm_conn_callbacks;
import com.clearevo.libecodroidbluetooth.rfcomm_conn_mgr;
import com.clearevo.libecodroidgnss_parse.gnss_sentence_parser;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;

import static com.clearevo.libecodroidgnss_parse.gnss_sentence_parser.fromHexString;
import static com.clearevo.libecodroidgnss_parse.gnss_sentence_parser.toHexString;


public class bluetooth_gnss_service extends Service implements rfcomm_conn_callbacks, gnss_sentence_parser.gnss_parser_callbacks, ntrip_conn_callbacks {

    static final String TAG = "btgnss_service";
    static final long BLE_GAP_SCAN_LOOP_DURAITON_MILLIS = 3000;
    String ECODROIDGPS_BROADCAST_MODE = "ECODROIDGPS_BROADCAST";
    public static final String BLE_GAP_SCAN_MODE = "ble_gap_scan_mode";
    public static final ParcelUuid eddystone_service_uuid = ParcelUuid.fromString("0000feaa-0000-1000-8000-00805f9b34fb");  //https://proandroiddev.com/scanning-google-eddystone-in-android-application-cf181e0a8648
    public static final long BLE_GAP_SCAN_MODE_SETMOCK_INTERVAL = 1000;
    public long m_last_BLE_GAP_SCAN_MODE_SETMOCK_ts = 0;
    public String m_last_BLE_GAP_DEV_NAME = "";

    rfcomm_conn_mgr g_rfcomm_mgr = null;
    ntrip_conn_mgr m_ntrip_conn_mgr = null;
    private gnss_sentence_parser m_gnss_parser = new gnss_sentence_parser();

    final String EDG_DEVICE_PREFIX = "EcoDroidGPS";
    public static final String BROADCAST_ACTION_NMEA = "com.clearevo.bluetooth_gnss.NMEA";
    Thread m_connecting_thread = null;
    Thread m_ntrip_connecting_thread = null;
    Handler m_handler = new Handler();
    String m_bdaddr = "";
    boolean m_auto_reconnect = false;
    boolean m_secure_rfcomm = true;
    Class m_target_activity_class;
    int m_icon_id;
    int m_ntrip_cb_count;
    int m_ntrip_cb_count_added_to_send_buffer;
    Intent m_start_intent;

    boolean m_ubx_mode = true;
    boolean m_ubx_send_enable_extra_used_packets = true;
    boolean m_ubx_send_disable_extra_used_packets = false;
    boolean m_send_gga_to_ntrip = true;
    boolean m_all_ntrip_params_specified = false;
    long m_last_ntrip_gga_send_ts = 0;
    public static final long SEND_GGA_TO_NTRIP_EVERY_MILLIS = 29*1000;
    public static final String[] REQUIRED_INTENT_EXTRA_PARAM_KEYS = {"ntrip_host", "ntrip_port", "ntrip_mountpoint", "ntrip_user", "ntrip_pass"};
    boolean m_log_bt_rx = false;
    boolean m_disable_ntrip = false;
    boolean m_ble_gap_scan_mode = false;
    OutputStream m_log_bt_rx_fos = null;
    OutputStream m_log_bt_rx_csv_fos = null;
    long log_bt_rx_bytes_written = 0;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // If we get killed, after returning from here, restart
        Log.d(TAG, "onStartCommand");

        if (intent != null) {
            try {
                m_ble_gap_scan_mode = intent.getBooleanExtra(BLE_GAP_SCAN_MODE, false);
                {

                    m_bdaddr = intent.getStringExtra("bdaddr");
                    m_secure_rfcomm = intent.getBooleanExtra("secure", true);
                    m_auto_reconnect = intent.getBooleanExtra("reconnect", false);

                    m_log_bt_rx = intent.getBooleanExtra("log_bt_rx", false);
                    m_disable_ntrip = intent.getBooleanExtra("disable_ntrip", false);
                    Log.d(TAG, "m_secure_rfcomm: " + m_secure_rfcomm);
                    Log.d(TAG, "m_log_bt_rx: " + m_log_bt_rx);
                    Log.d(TAG, "m_disable_ntrip: " + m_disable_ntrip);
                    String cn = intent.getStringExtra("activity_class_name");
                    m_start_intent = intent;
                    if (cn == null) {
                        throw new Exception("activity_class_name not specified");
                    }
                    m_target_activity_class = Class.forName(cn);
                    Log.d(TAG, "m_target_activity_class: "+m_target_activity_class.getCanonicalName());
                    if (!intent.hasExtra("activity_icon_id")) {
                        throw new Exception("activity_icon_id not specified");
                    }
                    m_icon_id = intent.getIntExtra("activity_icon_id", 0);

                    if (m_auto_reconnect) {
                        start_auto_reconnect_thread();
                    } else {
                        connect();
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

    public static final String log_uri_pref_key = "flutter.pref_log_uri";
    void connect()
    {
        final SharedPreferences prefs = getApplicationContext().getSharedPreferences("FlutterSharedPreferences", MODE_PRIVATE);
        String log_uri = prefs.getString(log_uri_pref_key, "");
        if (!log_uri.isEmpty()) {
            try {
                set_log_folder(Uri.parse(log_uri));
            } catch (Throwable tr) {
                Log.d(TAG, "set_log_uri exception: "+Log.getStackTraceString(tr));
            }
        }

        if (m_ble_gap_scan_mode) {
            Log.d(TAG, "onStartCommand pre call start_forground m_ble_gap_scan_mode "+m_ble_gap_scan_mode);
            start_foreground("Scanning GPS broadcasts...", "", "");
            Log.d(TAG, "onStartCommand post call start_forground m_ble_gap_scan_mode "+m_ble_gap_scan_mode);
            handle_ble_gap_scan_enable_changed();
        } else {
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
                m_all_ntrip_params_specified = true;
                for (String key : REQUIRED_INTENT_EXTRA_PARAM_KEYS) {
                    if (m_start_intent.getStringExtra(key) == null || m_start_intent.getStringExtra(key).length() == 0) {
                        Log.d(TAG, "key: " + key + "got null or empty string so m_all_ntrip_params_specified false");
                        m_all_ntrip_params_specified = false;
                        break;
                    }
                }
                Log.d(TAG, "m_all_ntrip_params_specified: " + m_all_ntrip_params_specified);
                //ntrip connection would start after we get next gga bashed on this m_all_ntrip_params_specified flag
            }
        }
    }

    Thread ble_gap_scan_thread = null;

    public boolean is_ble_gap_scan_thread_running()
    {
        return ble_gap_scan_thread != null && ble_gap_scan_thread.isAlive();
    }

    //credit to https://github.com/joelwass/Android-BLE-Scan-Example/blob/master/app/src/main/java/com/example/joelwasserman/androidbletutorial/MainActivity.java
    private ScanCallback leScanCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            if (result == null) {
                Log.d(TAG, "WARNING: onScanResult got null result");
                return;
            }
            byte[] scan_record_bytes = null;
            ScanRecord scanRecord = result.getScanRecord();
            scan_record_bytes = scanRecord.getBytes();
            if (scan_record_bytes == null) {
                scan_record_bytes = new byte[0];
            }
            Log.d(TAG, "onScanResult Device Name: " + result.getDevice().getName() + " rssi: " + result.getRssi() + " scanrecord bytes: " + toHexString(scan_record_bytes));
            //ex: 02 01 1A 04 09 45 44 47 03 03 AA FE 12 16 AA FE 30 00 E1 6A 6D FD 03 10 9B 91 3C 38 50 32 28 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00
            parse_scan_record_bytes_and_set_location(scan_record_bytes);
            m_last_BLE_GAP_DEV_NAME = result.getDevice().getName();
        }
    };



    public void parse_scan_record_bytes_and_set_location(byte[] gap_buffer)
    {
        long now = System.currentTimeMillis();

        //handle system time change
        if (m_last_BLE_GAP_SCAN_MODE_SETMOCK_ts != 0) {
            if (now < m_last_BLE_GAP_SCAN_MODE_SETMOCK_ts || now > (m_last_BLE_GAP_SCAN_MODE_SETMOCK_ts + 2 * BLE_GAP_SCAN_MODE_SETMOCK_INTERVAL)) {
                m_last_BLE_GAP_SCAN_MODE_SETMOCK_ts = 0;
            }
        }

        if (now - m_last_BLE_GAP_SCAN_MODE_SETMOCK_ts > BLE_GAP_SCAN_MODE_SETMOCK_INTERVAL) {
            m_last_BLE_GAP_SCAN_MODE_SETMOCK_ts = now; //ok
        } else {
            return; //dont parse/announce locaiton yet
        }
        try {
            ecodroidgps_gap_buffer_parser.ecodroidgps_broadcasted_location loc = ecodroidgps_gap_buffer_parser.parse(gap_buffer);
            Log.d(TAG, "ECODROIDGPS_BROADCAST_MODE got broadcast: lat: "+loc.lat+" lon: "+loc.lon+" timestamp: "+loc.timestamp);

            int n_sats = 0;
            double lat = loc.lat, lon = loc.lon, alt = 0.0, hdop = 0.0, speed = 0.0, bearing = 0.0/0.0;
            double accuracy = hdop * get_connected_device_CEP();
            setMock(lat, lon, alt, (float) accuracy, (float) bearing, (float) speed, false, n_sats);

            String talker = "GN";
            m_gnss_parser.put_param(talker, "location_from_talker", talker);
            m_gnss_parser.put_param("GN", "time", loc.timestamp_str);
            m_gnss_parser.put_param("", "lat", lat);
            m_gnss_parser.put_param("", "lon", lon);
            m_gnss_parser.put_param("", "mock_location_set_ts", System.currentTimeMillis());
            HashMap<String, Object> param_map = m_gnss_parser.getM_parsed_params_hashmap();
            Log.d(TAG, "ble gap lat: "+param_map.get("lat_double_07_str"));
            Log.d(TAG, "ble gap lon: "+param_map.get("lon_double_07_str"));
            try {
                if (m_activity_for_nmea_param_callbacks != null) {
                    m_activity_for_nmea_param_callbacks.on_updated_nmea_params(param_map);
                }
            } catch (Exception e) {
                Log.d(TAG, "bluetooth_gnss_service call callback in m_activity_for_nmea_param_callbacks exception: "+Log.getStackTraceString(e));
            }

        } catch (Throwable tr) {
            Log.d(TAG, "parse_scan_record_bytes_and_set_location exception: "+Log.getStackTraceString(tr));
        }
    }

    public void handle_ble_gap_scan_enable_changed() {
        Log.d(TAG, "handle_ble_gap_scan_enable_changed() m_ble_gap_scan_mode "+ m_ble_gap_scan_mode);
        if (m_ble_gap_scan_mode) {
            if (is_ble_gap_scan_thread_running()) {
                Log.d(TAG, "handle_ble_gap_scan_enable_changed() m_ble_gap_scan_mode "+ m_ble_gap_scan_mode+" already running so omit");
            } else {
                ble_gap_scan_thread = new Thread() {
                    public void run ()
                    {
                        Log.d(TAG,"ble_gap_scan_thread START");
                        BluetoothLeScanner btLeScanner = null;
                        try {

                            //credit to https://github.com/joelwass/Android-BLE-Scan-Example/blob/master/app/src/main/java/com/example/joelwasserman/androidbletutorial/MainActivity.java
                            BluetoothManager btManager = (BluetoothManager)getSystemService(Context.BLUETOOTH_SERVICE);
                            BluetoothAdapter btAdapter = btManager.getAdapter();
                            List<ScanFilter> filters = new ArrayList<>();
                            filters.add(
                                    new ScanFilter.Builder()
                                            .setServiceUuid(eddystone_service_uuid)
                                            .build());
                            ScanSettings settings = new ScanSettings.Builder()
                                    .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                                    .build();

                            btLeScanner = btAdapter.getBluetoothLeScanner();

                            while (ble_gap_scan_thread == this) {
                                Log.d(TAG, "btLeScanner.startScan(leScanCallback); START");
                                btLeScanner.startScan(filters, settings, leScanCallback);
                                Log.d(TAG, "btLeScanner.startScan(leScanCallback); DONE");
                                Thread.sleep(BLE_GAP_SCAN_LOOP_DURAITON_MILLIS);
                            }
                        } catch (Throwable tr) {
                            Log.d(TAG, "ble_gap_scan_thread hash "+this.hashCode()+ " failed with exception: "+Log.getStackTraceString(tr));
                        } finally {
                            try {
                                if (btLeScanner != null) {
                                    btLeScanner.stopScan(leScanCallback);
                                }
                            } catch (Throwable tr) {}
                        }
                        Log.d(TAG,"ble_gap_scan_thread END");
                    }
                };
                ble_gap_scan_thread.start();
            }
        } else {
            close();
        }
    }

    public void start_ntrip_conn_if_specified_but_not_connected() {

        if (!m_all_ntrip_params_specified) {
            return;
        }

        if (m_disable_ntrip) {
            return;
        }

        if (is_trying_ntrip_connect()) {
            Log.d(TAG, "start_ntrip_conn_if_specified - ntrip already is_trying_ntrip_connect - omit this call");
            return;
        }

        if (is_ntrip_connected()) {
            Log.d(TAG, "start_ntrip_conn_if_specified - ntrip already connected - omit this call");
            return;
        }

        try {
            if (System.currentTimeMillis() - last_ntrip_connect_retry > 10000) {
                if (m_all_ntrip_params_specified) {
                    Log.d(TAG, "start_ntrip_conn_if_specified call connect_ntrip() since m_all_ntrip_params_specified true");
                    int port = -1;
                    try {
                        port = Integer.parseInt(m_start_intent.getStringExtra("ntrip_port"));
                        connect_ntrip(m_start_intent.getStringExtra("ntrip_host"), port, m_start_intent.getStringExtra("ntrip_mountpoint"), m_start_intent.getStringExtra("ntrip_user"), m_start_intent.getStringExtra("ntrip_pass"));
                    } catch (Exception e) {
                        Log.d(TAG, "call connect_ntrip exception: " + Log.getStackTraceString(e));
                    }
                } else {
                    Log.d(TAG, "dont call connect_ntrip() since m_all_ntrip_params_specified false");
                }
                last_ntrip_connect_retry = System.currentTimeMillis();
            }
        } catch (Exception e) {
            Log.d(TAG,"start_ntrip_conn_if_specified exception: "+Log.getStackTraceString(e));
        }
    }

    long last_ntrip_connect_retry = 0;

    public boolean is_bt_connected()
    {
        if (m_ble_gap_scan_mode) {
            if (System.currentTimeMillis() - m_last_BLE_GAP_SCAN_MODE_SETMOCK_ts < BLE_GAP_SCAN_MODE_SETMOCK_INTERVAL*3) {
                return true;
            }
            return false;
        }
        if (g_rfcomm_mgr != null && g_rfcomm_mgr.is_bt_connected()) {
            return true;
        }
        return false;
    }

    public boolean is_trying_bt_connect() {
        if (is_ble_gap_scan_thread_running())
            return true;
        return m_connecting_thread != null && m_connecting_thread.isAlive();
    }

    public boolean is_trying_ntrip_connect() {
        return m_ntrip_connecting_thread != null && m_ntrip_connecting_thread.isAlive();
    }

    Thread m_auto_reconnect_thread = null;
    public static final long AUTO_RECONNECT_MILLIS = 15*1000;

    public void stop_auto_reconnect_thread() {

        Log.d(TAG, "stop_auto_reconnect_thread start");
        if (m_auto_reconnect_thread != null && m_auto_reconnect_thread.isAlive()) {
            //interrupt old thread so it will end...
            Log.d(TAG, "stop_auto_reconnect_thread1.0");
            try {
                m_auto_reconnect_thread.interrupt();
                Log.d(TAG, "stop_auto_reconnect_thread1.1");
            } catch (Exception e) {
                Log.d(TAG, "interrrupt old m_auto_reconnect_thread failed exception: "+Log.getStackTraceString(e));
            }
            Log.d(TAG, "stop_auto_reconnect_thread1.2");
        }
        Log.d(TAG, "stop_auto_reconnect_thread end");
    }

    void start_auto_reconnect_thread()
    {
        if (m_auto_reconnect) {

            stop_auto_reconnect_thread();

            m_auto_reconnect_thread = new Thread() {

                public void run(){

                    Log.d(TAG, "auto-reconnect thread: "+this.hashCode()+" START");

                    try {

                        while (m_auto_reconnect_thread == this && m_auto_reconnect) {

                            //connect() must be run from main service thread in case it needs to post
                            if (!is_bt_connected() && !is_trying_bt_connect()) {
                                Log.d(TAG, "auto-reconnect thread - has target dev and not connected - try reconnect...");
                                m_handler.post(new Runnable() {
                                    @Override
                                    public void run() {
                                        toast("Auto-Reconnect: Trying to connect...");
                                        connect();
                                    }
                                });
                            } else {
                                Log.d(TAG, "auto-reconnect thread - likely already connecting or already connected or no target dev");
                            }

                            try {
                                Log.d(TAG, "auto-reconnect thread: " + this.hashCode() + " - start sleep");
                                Thread.sleep(AUTO_RECONNECT_MILLIS);
                            } catch (InterruptedException e) {
                                Log.d(TAG, "auto-reconnect thread: " + this.hashCode() + " - sleep interrupted likely by close() - break out of loop and end now");
                                break;
                            }

                        }
                    } catch (Throwable tr) {
                        Log.d(TAG, "auto-reconnect thread exception: "+Log.getStackTraceString(tr));
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


            if (is_trying_bt_connect()) {
                toast("connection already starting - please wait...");
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
            }
            ret = 0;
        } catch (Exception e) {
            String emsg = Log.getStackTraceString(e);
            Log.d(TAG, "connect() exception: "+emsg);
            toast("Connect failed: "+emsg);
        }

        return ret;
    }


    public int connect_ntrip(String host, int port, String first_mount_point, String user, String pass)
    {
        Log.d(TAG, "connect_ntrip set m_ntrip_conn_mgr start");

        if (is_trying_ntrip_connect()) {
            Log.d(TAG, "connect_ntrip - omit as already trying ntrip_connect");
            return 0;
        }

        if (is_ntrip_connected()) {
            Log.d(TAG, "connect_ntrip - omit as already trying ntrip_connected");
            return 0;
        }

        if (m_ntrip_conn_mgr != null) {
            try {
                m_ntrip_conn_mgr.close();
            } catch (Throwable e) {}
            m_ntrip_conn_mgr = null;
        }

        try {
            m_ntrip_conn_mgr = new ntrip_conn_mgr(host, port, first_mount_point, user, pass, this);
            Log.d(TAG, "connect_ntrip set m_ntrip_conn_mgr done");
            //need new thread here else will fail network on mainthread below...
            m_ntrip_connecting_thread = new Thread() {
                public void run()
                {
                    try {
                        m_ntrip_conn_mgr.connect();
                    } catch (Exception e) {
                        Log.d(TAG, "m_ntrip_conn_mgr.conenct() exception: "+Log.getStackTraceString(e));
                        final Exception ex = e;
                        try {
                            m_handler.post(
                                    new Runnable() {
                                        @Override
                                        public void run() {
                                            toast_long("NTRIP Connect Failed: "+ex.toString());
                                        }
                                    }
                            );
                        } catch (Throwable tr) {}
                    }
                }
            };
            m_ntrip_connecting_thread.start();
            return 0;
        } catch (Exception e) {
            Log.d(TAG, "connect_ntrip exception: "+Log.getStackTraceString(e));
            m_ntrip_conn_mgr = null;
        }
        return -1;
    }

    public boolean is_ntrip_connected()
    {
        if (m_ntrip_conn_mgr != null && m_ntrip_conn_mgr.is_connected()) {
            return true;
        }
        return false;
    }


    @Override //ntrip data callbacks
    public void on_read(byte[] read_buff) {

        try {
            //Log.d(TAG, "ntrip on_read: "+read_buff.toString());
            m_ntrip_cb_count += 1;
            g_rfcomm_mgr.add_send_buffer(read_buff);
	    m_ntrip_cb_count_added_to_send_buffer += 1;
        } catch (Exception e) {
            Log.d(TAG, "ntrip callback on_readline exception: "+ Log.getStackTraceString(e));
        }
    }
    

    //return true if was connected
    public boolean close()
    {
        Log.d(TAG, "close()0");
        deactivate_mock_location();

        if (is_ble_gap_scan_thread_running()) {
            try {
                ble_gap_scan_thread.interrupt();
            } catch (Throwable tr) {}
            ble_gap_scan_thread = null;
        }

        boolean was_connected = false;

        if (g_rfcomm_mgr != null) {
            Log.d(TAG, "close()3");
            was_connected = g_rfcomm_mgr.is_bt_connected();
            g_rfcomm_mgr.close();
            Log.d(TAG, "close()4");
        }

        Log.d(TAG, "close() m_ntrip_conn_mgr: "+m_ntrip_conn_mgr);
        if (m_ntrip_conn_mgr != null) {
            try {
                Log.d(TAG, "close() m_ntrip_conn_mgr.close()");
                m_ntrip_conn_mgr.close();
            } catch (Exception e) {
            }
        }
        m_ntrip_cb_count = 0;
        m_ntrip_cb_count_added_to_send_buffer = 0;

        try {
            if (m_log_bt_rx_fos != null) {
                m_log_bt_rx_fos.close();
                m_log_bt_rx_fos = null;
            }
        } catch (Exception e) {}
        try {
            if (m_log_bt_rx_csv_fos != null) {
                m_log_bt_rx_csv_fos.close();
                m_log_bt_rx_csv_fos = null;
            }
        } catch (Exception e) {}
        log_file_uri = null;
        log_folder_uri = null;

        return was_connected;
    }

    void toast(String msg)
    {
        //dont toast if running in background
        if (m_is_bound) {
            try {
                Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
            } catch (Exception e) {
                Log.d(TAG, "toast() exception: "+Log.getStackTraceString(e));
            }
            Log.d(TAG, "toast msg: "+msg);
        } else {
            Log.d(TAG, "m_is_bound false so omit: toast msg: "+msg);
        }


    }

    void toast_long(String msg)
    {
        //dont toast if running in background
        if (m_is_bound) {
            try {
                Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
            } catch (Exception e) {
                Log.d(TAG, "toast() exception: "+Log.getStackTraceString(e));
            }
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
        deactivate_mock_location();
        close();
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

    Uri log_folder_uri = null;
    Uri log_file_uri = null;
    public void set_log_folder(Uri uri){
        log_folder_uri = uri;
    }

    SimpleDateFormat log_name_sdf = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss");
    SimpleDateFormat csv_sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    public void log_bt_rx(byte[] read_buf)
    {
        if (read_buf == null || read_buf.length == 0)
            return;
        try {
            if (log_folder_uri != null) {
                if (m_log_bt_rx && read_buf != null) {
                    if (m_log_bt_rx_fos == null) {
                        //ref: https://stackoverflow.com/questions/61118918/create-new-file-in-the-directory-returned-by-intent-action-open-document-tree
                        DocumentFile dd = DocumentFile.fromTreeUri(getApplicationContext(), log_folder_uri);
                        DocumentFile df = dd.createFile("text/plain", (log_name_sdf.format(new Date()) + "_rx_log.txt"));
                        DocumentFile df_csv = dd.createFile("text/csv", (log_name_sdf.format(new Date()) + "_location_log.csv"));
                        log_file_uri = df.getUri();
                        Log.d(TAG, "log_bt_rx: log_fp: " + df.getUri().toString());
                        log_bt_rx_bytes_written = 0;
                        m_log_bt_rx_fos = getApplicationContext().getContentResolver().openOutputStream(df.getUri());
                        m_log_bt_rx_csv_fos = getApplicationContext().getContentResolver().openOutputStream(df_csv.getUri());
                        m_log_bt_rx_csv_fos.write("time,lat,lon,alt\n".getBytes());
                        m_log_bt_rx_csv_fos.flush();
                        toast("Logging to: "+log_folder_uri.getPath());
                        Log.d(TAG, "log_bt_rx: m_log_bt_rx_fos ready");
                    }
                    if (m_log_bt_rx_fos != null) {
                        m_log_bt_rx_fos.write(read_buf);
                        log_bt_rx_bytes_written += read_buf.length;
                        //Log.d(TAG, "log_bt_rx: written n bytes: "+read_buf.length);
                    }
                }
            }
        } catch (Throwable tr) {
            Log.d(TAG, "log_bt_rx exception: "+Log.getStackTraceString(tr));
        }
    }

    public void on_readline(byte[] readline)
    {
        try {
            //Log.d(TAG, "rfcomm on_readline: "+new String(readline, "ascii"));
            log_bt_rx(readline);
            String parsed_nmea = m_gnss_parser.parse(readline);
            if (parsed_nmea != null && parsed_nmea.length() > 6 && parsed_nmea.substring(3).startsWith("GGA")) {

                if (m_all_ntrip_params_specified) {
                    start_ntrip_conn_if_specified_but_not_connected();
                }
                if (m_send_gga_to_ntrip && is_ntrip_connected()) {
                    Log.d(TAG, "consider send gga to ntrip if not sent since millis: " + SEND_GGA_TO_NTRIP_EVERY_MILLIS);
                    long now = System.currentTimeMillis();
                    if (now >= m_last_ntrip_gga_send_ts) {
                        if (now - m_last_ntrip_gga_send_ts > SEND_GGA_TO_NTRIP_EVERY_MILLIS) {
                            m_last_ntrip_gga_send_ts = now;
                            String send_str = parsed_nmea.trim() + "\r\n";
                            Log.d(TAG, "yes send to ntrip now: "+send_str);
                            m_ntrip_conn_mgr.send_buff_to_server(send_str.getBytes("ascii"));
                        }
                    } else {
                        m_last_ntrip_gga_send_ts = 0;
                    }
                }
            }
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

    public void on_target_tcp_connected() {
        Log.d(TAG, "on_target_tcp_connected()");
        m_last_ntrip_gga_send_ts = 0;
    }

    public void on_target_tcp_disconnected(){
        Log.d(TAG, "on_target_tcp_disconnected()");
    }

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

        Intent notificationIntent = new Intent(this.getApplicationContext(), m_target_activity_class);
        PendingIntent pendingIntent =
                PendingIntent.getActivity(this.getApplicationContext(), 0, notificationIntent, 0);


        NotificationManager mNotificationManager =
                (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(notification_channel_id,
                    notification_name,
                    NotificationManager.IMPORTANCE_DEFAULT);
            channel.setDescription("Bluetooth GNSS Status");
            mNotificationManager.createNotificationChannel(channel);
        }

        Notification notification = null;

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, notification_channel_id)
                .setSmallIcon(m_icon_id)
                .setContentTitle(title)
                .setContentText(text)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true);

        notification = builder.build();

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
                //Log.d(TAG,"is_mock_location_enabled Build.VERSION.SDK_INT >= Build.VERSION_CODES.M");
                AppOpsManager opsManager = (AppOpsManager) context.getSystemService(Context.APP_OPS_SERVICE);
                mock_enabled = (opsManager.checkOp(AppOpsManager.OPSTR_MOCK_LOCATION, app_uid, app_id_string)== AppOpsManager.MODE_ALLOWED);
            } else {
                // in marshmallow this will always return true
                //Log.d(TAG,"is_mock_location_enabled older models");
                mock_enabled = !android.provider.Settings.Secure.getString(context.getContentResolver(), "mock_location").equals("0");
            }
        } catch(Exception e) {
            Log.d(TAG, "check mock_enabled exception: "+Log.getStackTraceString(e));
        }
        //Log.d(TAG,"is_mock_location_enabled ret "+mock_enabled);
        return mock_enabled;
    }

    File bt_gnss_test_debug_mock_location_1_1_mode_flag = new File("/sdcard/bt_gnss_test_debug_mock_location_1_1_mode_flag");

    private void setMock(double latitude, double longitude, double altitude, float accuracy, float bearing, float speed, boolean alt_is_elipsoidal, int n_sats) {

        try {
            if (bt_gnss_test_debug_mock_location_1_1_mode_flag.isFile()) {
                Log.d(TAG, "NOTE: bt_gnss_test_debug_mock_location_1_1_mode_flag exists - overriding lat, lon to 1, 1");
                latitude = 1;
                longitude = 1;
            }
        } catch (Throwable tr) {
            Log.d(TAG, "WARNING: check bt_gnss_test_debug_mock_location_1_1_mode_flag exception: "+Log.getStackTraceString(tr));
        }

        Log.d(TAG, "setMock accuracy_meters: "+accuracy);

        activate_mock_location(); //this will check a static flag and not re-activate if already active
        LocationManager locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);

        Location newLocation = new Location(LocationManager.GPS_PROVIDER);
        newLocation.setTime(System.currentTimeMillis());
        newLocation.setLatitude(latitude);
        newLocation.setLongitude(longitude);
        newLocation.setAccuracy(accuracy);
        newLocation.setAltitude(altitude);
        if (!Double.isNaN(bearing))
            newLocation.setBearing(bearing);
        else {
            //Log.d(TAG, "bearing is nan so not setting in newlocation");
        }
        newLocation.setSpeed(speed);
        if (n_sats > 0) {
            Bundle bundle = new Bundle();
            bundle.putInt("satellites", n_sats);
            newLocation.setExtras(bundle);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            newLocation.setElapsedRealtimeNanos(SystemClock.elapsedRealtimeNanos());

        }
        locationManager.setTestProviderStatus(LocationManager.GPS_PROVIDER,
                LocationProvider.AVAILABLE,
                null,System.currentTimeMillis());
        Log.d(TAG, "setMock lat: "+newLocation.getLatitude());
        Log.d(TAG, "setMock lon: "+newLocation.getLongitude());
        if (newLocation.getExtras() != null) {
            Log.d(TAG, "setMock satellites: " + newLocation.getExtras().getInt("satellites"));
        }
        locationManager.setTestProviderLocation(LocationManager.GPS_PROVIDER, newLocation);
    }

    private void deactivate_mock_location() {
        Log.d(TAG, "deactivate_mock_location0");
        if (is_mock_location_active()) {
            Log.d(TAG, "deactivate_mock_location1");
            try {
                LocationManager locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
                locationManager.setTestProviderEnabled(LocationManager.GPS_PROVIDER, false);
                locationManager.removeTestProvider(LocationManager.GPS_PROVIDER);
                g_mock_location_active = false;
                m_handler.post(
                        new Runnable() {
                            @Override
                            public void run() {
                                toast("Deactivated Mock location provider...");
                                updateNotification("Bluetooth GNSS - Not active...", "Deactivated", "");
                            }
                        }
                );
                Log.d(TAG, "deactivate_mock_location success");
            } catch (Exception e) {
                Log.d(TAG, "deactivate_mock_location exception: " + Log.getStackTraceString(e));
            }
        }
        Log.d(TAG, "deactivate_mock_location return");
    }

    private void activate_mock_location() {
        if (!is_mock_location_active()) {
            try {
                LocationManager locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
                locationManager.addTestProvider(LocationManager.GPS_PROVIDER,
                        /*boolean requiresNetwork*/ false,
                        /*boolean requiresSatellite*/ true,
                        /*boolean requiresCell*/ false,
                        /*boolean hasMonetaryCost*/ false,
                        /*boolean supportsAltitude*/ true,
                        /*boolean supportsSpeed*/ true,
                        /*boolean supportsBearing */ false,
                        Criteria.POWER_LOW,
                        Criteria.ACCURACY_MEDIUM);
                locationManager.setTestProviderEnabled(LocationManager.GPS_PROVIDER, true);
                m_handler.post(
                        new Runnable() {
                            @Override
                            public void run() {
                                toast("Activated Mock location provider...");
                                updateNotification("Bluetooth GNSS - Active...", "Connected to: "+get_connected_device_alias(), "");
                            }
                        }
                );
                g_mock_location_active = true;
            } catch (Exception e) {
                String st = Log.getStackTraceString(e);
                if (st.contains("already exists")) {
                    Log.d(TAG, "activate_mock_location exception but already exits so set success flag");
                    g_mock_location_active = true;
                    m_handler.post(
                            new Runnable() {
                                @Override
                                public void run() {
                                    toast("Activated Mock location provider...");
                                    updateNotification("Bluetooth GNSS - Active...", "Connected to: "+get_connected_device_alias(), "");
                                }
                            }
                    );
                }
                Log.d(TAG, "activate_mock_location exception: " + st);

            }
        }
    }

    static boolean g_mock_location_active = false;

    private boolean is_mock_location_active() {
        return g_mock_location_active;
    }

    public String get_connected_device_alias()
    {
        if (m_ble_gap_scan_mode) {
            return m_last_BLE_GAP_DEV_NAME;
        }
        return ""+m_bdaddr;
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

    double DEFAULT_CEP = 4.0;
    double DEFAULT_UBLOX_M8030_CEP = 2.0;
    double DEFAULT_UBLOX_ZED_F9P_CEP = 1.5;

    public double get_connected_device_CEP()
    {
        //TODO - later set per detected device or adjustable by user in settings
        return DEFAULT_CEP;
    }

    @Override
    public void on_updated_nmea_params(HashMap<String, Object> params_map) {

        Log.d(TAG, "service: on_updated_nmea_params() start");
        //try set_mock
        double lat = 0.0, lon = 0.0, alt = 0.0, hdop = 0.0, speed = 0.0, bearing = 0.0/0.0;
        int n_sats = 0;
        for (String talker : GGA_MESSAGE_TALKER_TRY_LIST) {

            try {
                if (params_map.containsKey(talker+"_lat_ts")) {
                    long new_ts = (long) params_map.get(talker+"_lat_ts");
                    if (new_ts != last_set_mock_location_ts) {
                        lat = (double) params_map.get(talker+"_lat");
                        lon = (double) params_map.get(talker+"_lon");
                        String ellips_height_key = talker+"_ellipsoidal_height";
                        boolean alt_is_ellipsoidal = false;
                        if (params_map.containsKey(ellips_height_key)) {
                            alt_is_ellipsoidal = true;
                            alt = (double) params_map.get(ellips_height_key);
                            Log.d(TAG, "ellips_height_key valid");
                        } else {
                            alt = (double) params_map.get(talker+"_alt");
                            Log.d(TAG, "ellips_height_key not valid");
                        }
                        String[] sats_keys = new String[]{"GP_n_sats_used", "GL_n_sats_used", "GA_n_sats_used", "GB_n_sats_used"};
                        for (String sk : sats_keys) {
                            if (params_map.containsKey(sk)) {
                                Object val = params_map.get(sk);
                                if (val != null && val instanceof Integer) {
                                    n_sats += (Integer) val;
                                }
                            }
                        }
                        hdop = (double) params_map.get(talker+"_hdop");
                        speed = (double) params_map.get(talker+"_speed"); //Speed in knots (nautical miles per hour).
                        speed = speed * 0.514444; //convert to m/s
                        try {
                            Object course = params_map.get(talker+"_true_course");
                            Log.d(TAG, "course: "+course);
                            if (course != null) {
                                bearing = (double) course;
                            }
                        } catch (Exception e) {
                            Log.d(TAG, "get course failed exception: "+Log.getStackTraceString(e));
                        }
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
                        setMock(lat, lon, alt, (float) accuracy, (float) bearing, (float) speed, alt_is_ellipsoidal, n_sats);
                        m_gnss_parser.put_param("", "hdop", hdop);
                        m_gnss_parser.put_param("", "location_from_talker", talker);
                        m_gnss_parser.put_param("", "lat", lat);
                        m_gnss_parser.put_param("", "lon", lon);
                        m_gnss_parser.put_param("", "alt", alt);
                        m_gnss_parser.put_param("", "alt_type", alt_is_ellipsoidal?"ellipsoidal":"orthometric");
                        m_gnss_parser.put_param("", "n_sats", n_sats);
                        m_gnss_parser.put_param("", "accuracy", accuracy);
                        m_gnss_parser.put_param("", "mock_location_set_ts", System.currentTimeMillis());
                        if (log_file_uri != null) {
                            m_gnss_parser.put_param("", "logfile_uri", log_file_uri.toString());
                            Log.d(TAG, "log_file_uri.toString() "+log_file_uri.toString());
                            String ls = log_file_uri.getLastPathSegment();
                            if (ls.contains("/")) {
                                String[] parts = ls.split("/");
                                if (parts.length > 1) {
                                    m_gnss_parser.put_param("", "logfile_folder", parts[0]);
                                    m_gnss_parser.put_param("", "logfile_name", parts[1]);
                                }
                            }
                            m_gnss_parser.put_param("", "logfile_n_bytes", log_bt_rx_bytes_written);
                        }
                        if (m_log_bt_rx_csv_fos != null) {
                            try {
                                String line = csv_sdf.format(new_ts)+","+lat+","+lon+","+alt+"\n";
                                m_log_bt_rx_csv_fos.write(line.getBytes());
                                m_log_bt_rx_csv_fos.flush();
                            } catch (Exception e) {
                                Log.d(TAG, "WARNING: write csv exception: "+Log.getStackTraceString(e));
                            }
                        }
                        if (m_log_bt_rx_fos != null) {
                            m_log_bt_rx_fos.flush();
                        }
                        break;
                    } else {
                        //omit as same ts as last
                    }
                }
            } catch (Exception e) {
                Log.d(TAG, "bluetooth_gnss_service on_updated_nmea_params talker: "+talker+" exception: "+Log.getStackTraceString(e));
            }
        }

        Log.d(TAG, "service: on_updated_nmea_params() act");

        //report params to activity
        try {
            if (m_activity_for_nmea_param_callbacks != null) {
                m_activity_for_nmea_param_callbacks.on_updated_nmea_params(params_map);
            }
        } catch (Exception e) {
            Log.d(TAG, "bluetooth_gnss_service call callback in m_activity_for_nmea_param_callbacks exception: "+Log.getStackTraceString(e));
        }

        Log.d(TAG, "service: on_updated_nmea_params() done");

    }

    public int get_ntrip_cb_count()
    {
        return m_ntrip_cb_count;
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
        stop_auto_reconnect_thread();
        toast("Stopped Bluetooth GNSS Service...");
    }
}
