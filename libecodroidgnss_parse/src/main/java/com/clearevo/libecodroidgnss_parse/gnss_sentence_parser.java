package com.clearevo.libecodroidgnss_parse;

import android.content.Context;
import android.content.Intent;
import android.util.Log;

import net.sf.marineapi.nmea.parser.DataNotAvailableException;
import net.sf.marineapi.nmea.parser.SentenceFactory;
import net.sf.marineapi.nmea.sentence.GGASentence;
import net.sf.marineapi.nmea.sentence.GSASentence;
import net.sf.marineapi.nmea.sentence.RMCSentence;
import net.sf.marineapi.nmea.sentence.Sentence;
import net.sf.marineapi.nmea.util.Position;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;


public class gnss_sentence_parser {


    public interface nmea_parser_callbacks {
        public void on_updated_nmea_params(HashMap<String, Object> params_map);
    }

    void set_callbacks(nmea_parser_callbacks cb){
        m_cb = cb;
    }

    nmea_parser_callbacks m_cb;
    final String TAG = "btgnss_nmea_p";
    final String NMEA_PREFIX = "$G";
    Context m_context;

    public static final String BROADCAST_ACTION_NMEA = "com.clearevo.bluetooth_gnss.NMEA";
    HashMap<String, Object> m_parsed_params_hashmap = new HashMap<String, Object>();
    SentenceFactory m_sf = SentenceFactory.getInstance();


    void parse(String read_line) {
        String nmea = read_line;
        if (nmea != null && nmea.contains(NMEA_PREFIX)) {

            if (nmea.startsWith(NMEA_PREFIX)) {
                //ok good
            } else {
                //get substring starting with it
                nmea = nmea.substring(nmea.indexOf(NMEA_PREFIX));
                System.out.println("nmea substring filt done: "+nmea);
            }
            nmea = nmea.trim(); //this api requires complete valid sentence - no newlines at end...

            /*
            Intent intent = new Intent();
            intent.setAction(BROADCAST_ACTION_NMEA);
            intent.putExtra("NMEA",nmea);
            m_context.sendBroadcast(intent);
            */

            //try parse this nmea and update our states
            try {

                Sentence sentence =  m_sf.createParser(nmea);
                String sentence_id = sentence.getSentenceId();
                String param_key = "sentence_id_"+sentence_id;
                put_param(param_key, sentence_id);
                /////////////////////// parse and put main params in hashmap

                //GGA
                if (sentence instanceof GGASentence) {
                    GGASentence gga = (GGASentence) sentence;
                    Position pos = gga.getPosition();

                    put_param("lat", pos.getLatitude());
                    put_param("lon", pos.getLongitude());
                    put_param("alt", pos.getAltitude());
                    put_param("datum", pos.getDatum());

                } else if (sentence instanceof RMCSentence) {
                    RMCSentence rmc = (RMCSentence) sentence;
                    try {
                        put_param("time", rmc.getTime().toISO8601());
                    } catch (DataNotAvailableException dae) {}

                    try {
                        put_param("speed", rmc.getSpeed());
                    } catch (DataNotAvailableException dae) {}

                    try {
                        put_param("course", rmc.getCourse());
                    } catch (DataNotAvailableException dae) {}

                    try {
                        put_param("mode", rmc.getMode());
                    } catch (DataNotAvailableException dae) {}

                    try {
                        put_param("mode", rmc.getMode());
                    } catch (DataNotAvailableException dae) {}
                    try {
                        put_param("status", rmc.getStatus());
                    } catch (DataNotAvailableException dae) {}
                }


            } catch (Exception e) {
                System.out.println(TAG+": parse/update nmea params/callbacks exception: "+e.toString());
            }
        }
    }

    // put into m_parsed_params_hashmap directly if is int/long/double/string else conv to string then put... also ass its <param>_ts timestamp
    public void put_param(String param_key, Object val)
    {
        if (val == null) {
            return; //not supported
        }

        if (val instanceof Double || val instanceof Integer || val instanceof Long || val instanceof String) {
            m_parsed_params_hashmap.put(param_key, val);
        } else {
            m_parsed_params_hashmap.put(param_key, val.toString());
        }

        m_parsed_params_hashmap.put(param_key+"_ts", System.currentTimeMillis());
    }

    public HashMap<String, Object> get_params()
    {
        return m_parsed_params_hashmap;
    }

    public boolean is_gga(String sentence) {
        if (sentence.length() > 5 && sentence.substring(3).startsWith("GGA"))
            return true;
        return false;
    }

}
