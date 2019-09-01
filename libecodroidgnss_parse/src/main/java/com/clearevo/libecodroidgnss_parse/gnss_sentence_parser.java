package com.clearevo.libecodroidgnss_parse;

import android.os.SystemClock;
import android.util.Log;

import net.sf.marineapi.nmea.parser.DataNotAvailableException;
import net.sf.marineapi.nmea.parser.SentenceFactory;
import net.sf.marineapi.nmea.sentence.GGASentence;
import net.sf.marineapi.nmea.sentence.GSASentence;
import net.sf.marineapi.nmea.sentence.RMCSentence;
import net.sf.marineapi.nmea.sentence.Sentence;
import net.sf.marineapi.nmea.sentence.TalkerId;
import net.sf.marineapi.nmea.util.Position;

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
    gnss_parser_callbacks m_cb;
    SentenceFactory m_sf = SentenceFactory.getInstance();
    HashMap<String, Object> m_parsed_params_hashmap = new HashMap<String, Object>();

    public gnss_parser_callbacks get_callback() {
        return m_cb;
    }

    //returns valid parsed nmea or null if parse failed
    public String parse(String read_line) {
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
            return null;
        }

        //try parse this nmea and update our states
        String ret = null;
        try {

            Sentence sentence =  m_sf.createParser(nmea);
            ret = nmea; // if control reaches here means that this nmea string is valid
            String sentence_id = sentence.getSentenceId();

            //sentence type counter
            String param_key = sentence_id+"_count";
            String talker_id = sentence.getTalkerId().name(); //sepcifies talker_id like GN for combined, GA for Galileo, GP for GPS
            inc_param(talker_id, param_key); //talter-to-sentence param

            /////////////////////// parse and put main params in hashmap

            //System.out.println("got parsed read_line: "+ret);

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
                    put_param(talker_id,"hdop", gga.getHorizontalDOP());
                } catch (DataNotAvailableException dae) {}

                try {
                    put_param(talker_id,"dgps_age", gga.getDgpsAge());
                    put_param(talker_id,"dgps_station_id", gga.getDgpsStationId());
                } catch (DataNotAvailableException dae) {}

                try {
                    put_param(talker_id,"fix_quality", gga.getFixQuality().toString());
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

                //update on RMC
                if (m_cb != null) {
                    m_cb.on_updated_nmea_params(m_parsed_params_hashmap);
                }
            } else if (sentence instanceof GSASentence) {
                GSASentence gsa = (GSASentence) sentence;
                try {
                    String[] sids = gsa.getSatelliteIds();
                    put_param(talker_id,"n_sats_used", sids.length);
                    put_param(talker_id,"sat_ids", String.join(",", sids));
                } catch (DataNotAvailableException dae) {}
            }

        } catch (Exception e) {
            Log.d(TAG, "parse/update nmea params/callbacks exception: "+Log.getStackTraceString(e));
        }

        return ret;
    }


    // put into m_parsed_params_hashmap directly if is int/long/double/string else conv to string then put... also ass its <param>_ts timestamp
    public void put_param(String talker_id, String param_name, Object val)
    {
        if (val == null) {
            //Log.d(TAG, "put_param null so omit");
            return; //not supported
        }

        String key = ""+talker_id+"_"+param_name;

        if (val instanceof Double || val instanceof Integer || val instanceof Long || val instanceof String) {
            m_parsed_params_hashmap.put(key, val);
        } else {
            m_parsed_params_hashmap.put(key, val.toString());
        }

        m_parsed_params_hashmap.put(key+"_ts", System.currentTimeMillis());
    }

    //for counters
    public void inc_param(String talker_id, String param_name)
    {
        String key = ""+talker_id+"_"+param_name;
        //Log.d(TAG, "inc_param: "+key);
        int cur_counter = 0;
        if (m_parsed_params_hashmap.containsKey(key)) {
            //Log.d(TAG, "inc_param: "+param_name+" exists");
            try {
                cur_counter = (int) m_parsed_params_hashmap.get(key);
            } catch (Exception e) {
                //in case same param key was somehow not an int...
                Log.d(TAG, "WARNING: inc_param prev value for key was likely not an integer - using 0 counter start instead - exception: "+Log.getStackTraceString(e));
            }
        } else {
            //Log.d(TAG, "inc_param: "+param_name+" not exists");
        }

        cur_counter++;
        put_param(talker_id, param_name, cur_counter);
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


    public interface gnss_parser_callbacks {
        public void on_updated_nmea_params(HashMap<String, Object> params_map);
    }


    public void set_callback(gnss_parser_callbacks cb){
        Log.d(TAG, "set_callback() "+cb);
        m_cb = cb;
    }

}
