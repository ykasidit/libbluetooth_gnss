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
import net.sf.marineapi.nmea.sentence.TalkerId;
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


    final String TAG = "btgnss_nmea_p";
    final String[] KNOWN_NMEA_PREFIX_LIST = {
            "$"+ TalkerId.GN, //combined
            "$"+ TalkerId.GP, //GPS
            "$"+ TalkerId.GL, //GLONASS
            "$"+ TalkerId.GA, //Galileo
            "$"+ TalkerId.BD, //BeiDou
    };
    nmea_parser_callbacks m_cb;
    SentenceFactory m_sf = SentenceFactory.getInstance();
    HashMap<String, Object> m_parsed_params_hashmap = new HashMap<String, Object>();


    boolean parse(String read_line) {
        String nmea = read_line;

        boolean found_and_filt_to_prefix = false;
        for (String NMEA_PREFIX : KNOWN_NMEA_PREFIX_LIST) {
            if (nmea != null && nmea.contains(NMEA_PREFIX)) {
                if (nmea.startsWith(NMEA_PREFIX)) {
                    //ok good
                } else {
                    //get substring starting with it
                    nmea = nmea.substring(nmea.indexOf(NMEA_PREFIX));
                    //System.out.println("nmea substring filt done: " + nmea);
                }
                nmea = nmea.trim(); //this api requires complete valid sentence - no newlines at end...
                found_and_filt_to_prefix = true;
                break;
            }
        }

        if (!found_and_filt_to_prefix) {
            return false;
        }

        //try parse this nmea and update our states
        boolean sentence_valid = false;
        try {

            Sentence sentence =  m_sf.createParser(nmea);
            sentence_valid = true;
            String sentence_id = sentence.getSentenceId();
            String param_key = "sentence_id_"+sentence_id;
            String talker_id = sentence.getTalkerId().name(); //sepcifies talker_id like GN for combined, GA for Galileo, GP for GPS

            put_param(talker_id, param_key, sentence_id); //talter-to-sentence param

            /////////////////////// parse and put main params in hashmap


            if (sentence instanceof GGASentence) {
                GGASentence gga = (GGASentence) sentence;
                Position pos = gga.getPosition();

                try {
                    put_param(talker_id,"lat", pos.getLatitude());
                    put_param(talker_id,"lon", pos.getLongitude());
                } catch (DataNotAvailableException dae) {}

                try {
                    put_param(talker_id,"alt", pos.getAltitude());
                } catch (DataNotAvailableException dae) {}

                try {
                    put_param(talker_id,"datum", pos.getDatum());
                } catch (DataNotAvailableException dae) {}

            } else if (sentence instanceof RMCSentence) {
                RMCSentence rmc = (RMCSentence) sentence;

                try {
                    put_param(talker_id,"time", rmc.getTime().toISO8601());
                } catch (DataNotAvailableException dae) {}

                try {
                    put_param(talker_id,"speed", rmc.getSpeed());
                } catch (DataNotAvailableException dae) {}

                try {
                    put_param(talker_id,"course", rmc.getCourse());
                } catch (DataNotAvailableException dae) {}

                try {
                    put_param(talker_id,"mode", rmc.getMode());
                } catch (DataNotAvailableException dae) {}

                try {
                    put_param(talker_id,"mode", rmc.getMode());
                } catch (DataNotAvailableException dae) {}
                try {
                    put_param(talker_id,"status", rmc.getStatus());
                } catch (DataNotAvailableException dae) {}
            }

        } catch (Exception e) {
            Log.d(TAG, "parse/update nmea params/callbacks exception: "+Log.getStackTraceString(e));
        }

        return sentence_valid;
    }


    // put into m_parsed_params_hashmap directly if is int/long/double/string else conv to string then put... also ass its <param>_ts timestamp
    public void put_param(String talker_id, String param_key, Object val)
    {
        if (val == null) {
            return; //not supported
        }

        String key = ""+talker_id+"_"+param_key;

        if (val instanceof Double || val instanceof Integer || val instanceof Long || val instanceof String) {
            m_parsed_params_hashmap.put(key, val);
        } else {
            m_parsed_params_hashmap.put(key, val.toString());
        }

        m_parsed_params_hashmap.put(key+"_ts", System.currentTimeMillis());
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


    public interface nmea_parser_callbacks {
        public void on_updated_nmea_params(HashMap<String, Object> params_map);
    }


    void set_callbacks(nmea_parser_callbacks cb){
        m_cb = cb;
    }

}
