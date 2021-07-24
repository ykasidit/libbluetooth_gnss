package com.clearevo.libecodroidgnss_parse;

import android.util.Log;

import net.sf.marineapi.nmea.parser.DataNotAvailableException;
import net.sf.marineapi.nmea.parser.SentenceFactory;
import net.sf.marineapi.nmea.sentence.GGASentence;
import net.sf.marineapi.nmea.sentence.GSASentence;
import net.sf.marineapi.nmea.sentence.GSVSentence;
import net.sf.marineapi.nmea.sentence.RMCSentence;
import net.sf.marineapi.nmea.sentence.Sentence;
import net.sf.marineapi.nmea.sentence.TalkerId;
import net.sf.marineapi.nmea.sentence.VTGSentence;
import net.sf.marineapi.nmea.util.Position;
import net.sf.marineapi.nmea.util.SatelliteInfo;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

import static com.clearevo.libecodroidgnss_parse.ubx_parser.ubx_parse_get_n_bytes_consumed;


public class gnss_sentence_parser {


    static final String TAG = "btgnss_nmea_p";
    final String[] KNOWN_NMEA_PREFIX_LIST = {
            "$"+ TalkerId.GN, //combined
            "$"+ TalkerId.GP, //GPS
            "$"+ TalkerId.GL, //GLONASS
            "$"+ TalkerId.GA, //Galileo
            "$"+ TalkerId.GB, //BeiDou
            "$PUBX",
    };
    gnss_parser_callbacks m_cb;
    SentenceFactory m_sf = SentenceFactory.getInstance();

    public HashMap<String, Object> getM_parsed_params_hashmap() {
        return m_parsed_params_hashmap;
    }

    HashMap<String, Object> m_parsed_params_hashmap = new HashMap<String, Object>();

    public gnss_parser_callbacks get_callback() {
        return m_cb;
    }

    //returns valid parsed nmea or null if parse failed
    public String parse(byte[] read_line_raw_bytes) throws Exception {

        if (read_line_raw_bytes == null)
            return null;

        //parse ubx messages if came in this same line first

        //Log.d(TAG, "pre ubx parse: "+toHexString(read_line_raw_bytes));
        int pos_after_ubx_parse = ubx_parse_get_n_bytes_consumed(read_line_raw_bytes);
        int len_remain = read_line_raw_bytes.length - pos_after_ubx_parse;
        //Log.d(TAG, "post ubx parse: pos_after_ubx_parse: "+pos_after_ubx_parse+" len_remain: "+len_remain);
        if (len_remain <= 0)
            return null;

        //String nmea = new String(read_line_raw_bytes, pos_after_ubx_parse, len_remain, "ascii"); //parse from remaining bytes
        String nmea = new String(read_line_raw_bytes, "ascii"); //parse nmea from all bytes just to be sure

        /* this should not happen - the case was because a wrong crlf raw buff reader code
        final String NMEA_SPLIT_STR_REGEX = "\\$";
        final String NMEA_SPLIT_STR = "$";
        final int MIN_NMEA_STR_LEN = 5;

        //if multiple sentences in one line
        if (nmea.indexOf(NMEA_SPLIT_STR) != nmea.lastIndexOf(NMEA_SPLIT_STR)) {
            String ret = null;
            String[] parts = nmea.split(NMEA_SPLIT_STR_REGEX);
            System.out.println("parts: " + parts.length);
            for (String part : parts) {
                if (part.length() < MIN_NMEA_STR_LEN)
                    continue;
                String try_sentence = NMEA_SPLIT_STR + part;
                try {
                    String try_ret = parse_nmea_string(try_sentence);
                    if (try_ret != null)
                        ret = try_ret;
                } catch (Exception e) {
                    Log.d(TAG, "multi sentence in one line exception: "+Log.getStackTraceString(e));
                }
            }
            return ret;
        }
        */
        //normal case
        return parse_nmea_string(nmea);
    }


    public String parse_nmea_string(String nmea) throws Exception{

        boolean found_and_filt_to_prefix = false;
        for (String NMEA_PREFIX : KNOWN_NMEA_PREFIX_LIST) {
            if (nmea != null && nmea.contains(NMEA_PREFIX)) {
                if (nmea.startsWith(NMEA_PREFIX)) {
                    //ok good
                } else {
                    int nmea_start_pos = nmea.indexOf(NMEA_PREFIX);
                    //get substring starting with it
                    String prefix_non_nmea = nmea.substring(0, nmea_start_pos);
                    //do something with the prefix_non_nmea if required here
                    nmea = nmea.substring(nmea_start_pos);
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
        boolean is_nmea = false;
        try {

            if (nmea.startsWith("$PUBX")) {
                //proprietary messages handle here...
                //Log.d(TAG, "got PUBX: "+nmea);

                if (nmea.startsWith("$PUBX,00")) {
                    //ublox 31.3.2 POSITION (PUBX,00) - https://www.u-blox.com/sites/default/files/products/documents/u-blox8-M8_ReceiverDescrProtSpec_%28UBX-13003221%29_Public.pdf

                    //dont parase to numbers here in case it is empty and we want to continue to other params (would otherwise stop if exception is thrown)...
                    put_param("UBX", "POSITION_time", get_nmea_csv_offset_part(nmea, 2));
                    put_param("UBX", "POSITION_lat", get_nmea_csv_offset_part(nmea, 3));
                    put_param("UBX", "POSITION_NS", get_nmea_csv_offset_part(nmea, 4));
                    put_param("UBX", "POSITION_long", get_nmea_csv_offset_part(nmea, 5));
                    put_param("UBX", "POSITION_EW", get_nmea_csv_offset_part(nmea, 6));
                    put_param("UBX", "POSITION_altRef", get_nmea_csv_offset_part(nmea, 7));
                    put_param("UBX", "POSITION_navStat", get_nmea_csv_offset_part(nmea, 8));
                    put_param("UBX", "POSITION_hAcc", get_nmea_csv_offset_part(nmea, 9));
                    put_param("UBX", "POSITION_vAcc", get_nmea_csv_offset_part(nmea, 10));
                    put_param("UBX", "POSITION_SOG", get_nmea_csv_offset_part(nmea, 11));
                    put_param("UBX", "POSITION_COG", get_nmea_csv_offset_part(nmea, 12));
                    put_param("UBX", "POSITION_vVel", get_nmea_csv_offset_part(nmea, 13));
                    put_param("UBX", "POSITION_diffAge", get_nmea_csv_offset_part(nmea, 14));
                    put_param("UBX", "POSITION_HDOP", get_nmea_csv_offset_part(nmea, 15));
                    put_param("UBX", "POSITION_VDOP", get_nmea_csv_offset_part(nmea, 16));
                    put_param("UBX", "POSITION_TDOP", get_nmea_csv_offset_part(nmea, 17));
                    put_param("UBX", "POSITION_numSvs", get_nmea_csv_offset_part(nmea, 18));

                }


            } else {

                Sentence sentence = m_sf.createParser(nmea);
                ret = nmea; // if control reaches here means that this nmea string is valid
                String sentence_id = sentence.getSentenceId();

                //sentence type counter
                String param_key = sentence_id + "_count";
                String talker_id = sentence.getTalkerId().name(); //sepcifies talker_id like GN for combined, GA for Galileo, GP for GPS
                inc_param(talker_id, param_key); //talter-to-sentence param

                /////////////////////// parse and put main params in hashmap

                //System.out.println("got parsed read_line: "+ret);

                if (sentence instanceof GGASentence) {
                    GGASentence gga = (GGASentence) sentence;
                    Position pos = gga.getPosition();

                    try {
                        put_param(talker_id, "lat", pos.getLatitude());
                        put_param(talker_id, "lon", pos.getLongitude());
                    } catch (Exception pe) {
                        Log.d(TAG, "parse/put gga nmea: [" + nmea + "] got exception: " + Log.getStackTraceString(pe));
                    }

                    try {
                        put_param(talker_id, "alt", pos.getAltitude());
                    } catch (Exception pe) {
                        Log.d(TAG, "parse/put gga nmea: [" + nmea + "] got exception: " + Log.getStackTraceString(pe));
                    }

                    try {
                        put_param(talker_id, "hdop", gga.getHorizontalDOP());
                    } catch (Exception pe) {
                        Log.d(TAG, "parse/put gga nmea: [" + nmea + "] got exception: " + Log.getStackTraceString(pe));
                    }

                    try {
                        put_param(talker_id, "dgps_age", gga.getDgpsAge());
                        put_param(talker_id, "dgps_station_id", gga.getDgpsStationId());
                    } catch (DataNotAvailableException dae) {

                    } catch (Exception pe) {
                        Log.d(TAG, "parse/put gga nmea: [" + nmea + "] got exception: " + Log.getStackTraceString(pe));
                    }

                    try {
                        put_param(talker_id, "fix_quality", gga.getFixQuality().toString());
                    } catch (Exception pe) {
                        Log.d(TAG, "parse/put gga nmea: [" + nmea + "] got exception: " + Log.getStackTraceString(pe));
                    }

                    try {
                        put_param(talker_id, "datum", pos.getDatum());
                    } catch (Exception pe) {
                        Log.d(TAG, "parse/put gga nmea: [" + nmea + "] got exception: " + Log.getStackTraceString(pe));
                    }

                } else if (sentence instanceof RMCSentence) {
                    RMCSentence rmc = (RMCSentence) sentence;

                    try {
                        put_param(talker_id, "time", rmc.getTime().toISO8601());
                    } catch (Exception pe) {
                        Log.d(TAG, "parse/put rmc nmea: [" + nmea + "] got exception: " + Log.getStackTraceString(pe));
                    }

                    try {
                        put_param(talker_id, "speed", rmc.getSpeed());
                    } catch (Exception pe) {
                        Log.d(TAG, "parse/put rmc nmea: [" + nmea + "] got exception: " + Log.getStackTraceString(pe));
                    }

                    try {
                        put_param(talker_id, "course", rmc.getCourse());
                    } catch (DataNotAvailableException dae) {
                    } catch (Exception pe) {
                        Log.d(TAG, "parse/put rmc nmea: [" + nmea + "] got exception: " + Log.getStackTraceString(pe));
                    }

                    try {
                        put_param(talker_id, "mode", rmc.getMode());
                    } catch (Exception pe) {
                        if (pe.toString().contains("No enum constant net.sf.marineapi.nmea.util.FaaMode.F")) {
                            put_param(talker_id, "mode", "FloatRTK");
                        } else if (pe.toString().contains("No enum constant net.sf.marineapi.nmea.util.FaaMode.R")) {
                            put_param(talker_id, "mode", "RTK");
                        } else if (pe.toString().contains("No enum constant net.sf.marineapi.nmea.util.FaaMode.P")) {
                            put_param(talker_id, "mode", "Precise");
                        } else {
                            Log.d(TAG, "parse/put rmc nmea: [" + nmea + "] got exception: " + Log.getStackTraceString(pe));
                        }
                    }

                    try {
                        put_param(talker_id, "status", rmc.getStatus());
                    } catch (Exception pe) {
                        Log.d(TAG, "parse/put rmc nmea: [" + nmea + "] got exception: " + Log.getStackTraceString(pe));
                    }

                    //update on RMC
                    if (m_cb != null) {
                        Log.d(TAG, "calling m_cb callback with parsed params");
                        m_cb.on_updated_nmea_params(m_parsed_params_hashmap);
                    }
                } else if (sentence instanceof GSASentence) {
                    GSASentence gsa = (GSASentence) sentence;
                    try {
                        //Log.d(TAG, "gsa sentence:" +gsa.toString());
                        String[] sids = gsa.getSatelliteIds();
                        String gsa_talker_id = get_gsa_talker_id_from_gsa_nmea(nmea, sids);
                        //Log.d(TAG, "gsa_talker_id: "+gsa_talker_id);
                        if (gsa_talker_id != null && talker_id.equals(TalkerId.GN.toString())) {
                            put_param(gsa_talker_id, "n_sats_used", sids.length);
                            put_param(gsa_talker_id, "sat_used_ids", str_list_to_csv(Arrays.asList(sids)));
                            put_param(gsa_talker_id, "gsa_hdop", gsa.getHorizontalDOP());
                            put_param(gsa_talker_id, "gsa_pdop", gsa.getPositionDOP());
                            put_param(gsa_talker_id, "gsa_vdop", gsa.getVerticalDOP());
                        } else {
                            put_param(talker_id, "n_sats_used", sids.length);
                            put_param(talker_id, "sats_used_ids", str_list_to_csv(Arrays.asList(sids)));
                            put_param(talker_id, "gsa_hdop", gsa.getHorizontalDOP());
                            put_param(talker_id, "gsa_pdop", gsa.getPositionDOP());
                            put_param(talker_id, "gsa_vdop", gsa.getVerticalDOP());
                        }


                    } catch (Exception pe) {
                        Log.d(TAG, "parse/put gsa nmea: [" + nmea + "] got exception: " + Log.getStackTraceString(pe));
                    }
                } else if (sentence instanceof GSVSentence) {
                    GSVSentence gsv = (GSVSentence) sentence;
                    try {
                        String tmp_param_key_prefix = "tmp_" + talker_id + "_gsv_";
                        final String[] tmp_list_keys_to_flush = {
                                tmp_param_key_prefix + "sats_in_view_id_list",
                                tmp_param_key_prefix + "sats_in_view_snr_list",
                                tmp_param_key_prefix + "sats_in_view_elevation_list",
                                tmp_param_key_prefix + "sats_in_view_azimuth_list"
                        };
                        final int tmp_list_keys_to_flush_offset_id = 0;
                        final int tmp_list_keys_to_flush_offset_noise = 1;
                        final int tmp_list_keys_to_flush_offset_elevation = 2;
                        final int tmp_list_keys_to_flush_offset_azimuth = 3;

                        if (gsv.isFirst()) {

                            m_parsed_params_hashmap.put(tmp_list_keys_to_flush[tmp_list_keys_to_flush_offset_id], new ArrayList<String>());
                            m_parsed_params_hashmap.put(tmp_list_keys_to_flush[tmp_list_keys_to_flush_offset_noise], new ArrayList<Integer>());
                            m_parsed_params_hashmap.put(tmp_list_keys_to_flush[tmp_list_keys_to_flush_offset_elevation], new ArrayList<Integer>());
                            m_parsed_params_hashmap.put(tmp_list_keys_to_flush[tmp_list_keys_to_flush_offset_azimuth], new ArrayList<Integer>());
                        }


                        //Log.d(TAG, "gsv talker " + talker_id + " page " + gsv.getSentenceIndex() + " n sats in view " + gsv.getSatelliteCount() + " n sat info: " + gsv.getSatelliteInfo().size());

                        for (SatelliteInfo si : gsv.getSatelliteInfo()) {

                            ((List<String>) m_parsed_params_hashmap.get(tmp_list_keys_to_flush[tmp_list_keys_to_flush_offset_id])).add(si.getId());
                            ((List<Integer>) m_parsed_params_hashmap.get(tmp_list_keys_to_flush[tmp_list_keys_to_flush_offset_noise])).add(si.getNoise());
                            ((List<Integer>) m_parsed_params_hashmap.get(tmp_list_keys_to_flush[tmp_list_keys_to_flush_offset_elevation])).add(si.getElevation());
                            ((List<Integer>) m_parsed_params_hashmap.get(tmp_list_keys_to_flush[tmp_list_keys_to_flush_offset_azimuth])).add(si.getAzimuth());
                        }

                        if (gsv.isLast()) {
                            put_param(talker_id, "n_sats_in_view", gsv.getSatelliteCount());
                            for (String tmp_key : tmp_list_keys_to_flush) {
                                put_param(talker_id, tmp_key.replace(tmp_param_key_prefix, ""), m_parsed_params_hashmap.get(tmp_key));
                            }
                        }

                    } catch (Exception pe) {
                        Log.d(TAG, "parse/put gsv nmea: [" + nmea + "] got exception: " + Log.getStackTraceString(pe));
                    }
                } else if (sentence instanceof VTGSentence) {
                    VTGSentence vtg = (VTGSentence) sentence;
                    try {
                        put_param(talker_id, "true_course", vtg.getTrueCourse());
                    } catch (DataNotAvailableException dae) {
                    } catch (Exception pe) {
                        Log.d(TAG, "parse/put gsa nmea: [" + nmea + "] got exception: " + Log.getStackTraceString(pe));
                    }
                    try {
                        put_param(talker_id, "magnetic_course", vtg.getMagneticCourse());
                    } catch (DataNotAvailableException dae) {
                    } catch (Exception pe) {
                        Log.d(TAG, "parse/put gsa nmea: [" + nmea + "] got exception: " + Log.getStackTraceString(pe));
                    }
                }
            } //else of non-pubx
        } catch(Exception e){
            Log.d(TAG, "parse/update nmea params/callbacks nmea: [" + nmea + "] got exception: " + Log.getStackTraceString(e));
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
        if (talker_id.length() == 0)
            key = param_name;

        if (val instanceof Double) {
            m_parsed_params_hashmap.put(key+"_double_02_str", String.format("%.2f", val));
            m_parsed_params_hashmap.put(key+"_double_07_str", String.format("%.7f", val));
        }
        if (val instanceof Double || val instanceof Integer || val instanceof Long || val instanceof List) {
            m_parsed_params_hashmap.put(key, val);
        } else {
            m_parsed_params_hashmap.put(key, val.toString());
        }

        m_parsed_params_hashmap.put(key+"_str", val.toString());
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

    public static String get_gsa_talker_id_from_gsa_nmea(String nmea, String[] sids)
    {
        if (nmea.contains(",")) {
            String[] parts = nmea.split(",");
            //Log.d(TAG, "parts.length(): " + parts.length);
            final int GSA_SYSTEM_ID_NMEA_CSV_INDEX = 18;
            String part = get_nmea_csv_offset_part(nmea, GSA_SYSTEM_ID_NMEA_CSV_INDEX);
            if (part != null) {
                int gnss_system_id = Integer.parseInt(part);
                String gnss_system_id_talker_id = get_talker_id_for_gnss_system_id_int(gnss_system_id);
                return gnss_system_id_talker_id;
            } else {
                //part is null so likely no GSA_SYSTEM_ID_NMEA_CSV_INDEX so infer for sat ids
                //https://docs.novatel.com/OEM7/Content/Logs/GPGSA.htm

                boolean is_gps = false;
                boolean is_glonass = false;
                for (String sid: sids) {
                    try {
                        int isid = Integer.parseInt(sid);
                        //if ids are 1-32 so it is GPS
                        //if ids are 65 to 96 then it is GLONASS
                        if (isid >= 1 && isid <= 32) {
                            is_gps = true;
                        } else if (isid >= 65 && isid <= 96) {
                            is_glonass = true;
                        }
                    } catch (Exception e) {

                    }
                }
                if (is_gps) {
                    return TalkerId.GP.toString();
                }
                if (is_glonass) {
                    return TalkerId.GL.toString();
                }


            }
        }
        return null;
    }

    public static String get_nmea_csv_offset_part(String nmea, int offset)
    {
        String[] parts = nmea.split(",");
        if (parts.length > offset) {
            String part = parts[offset];
            if (part.contains("*")) {
                part = part.split("\\*")[0];
            }
            return part;
        }
        return null;
    }

    public static String get_talker_id_for_gnss_system_id_int(int gnss_system_id)
    {
        switch (gnss_system_id) {
            case 1:
                return TalkerId.GP.toString();
            case 2:
                return TalkerId.GL.toString();
            case 3:
                return TalkerId.GA.toString();
            case 4:
                return TalkerId.GB.toString();
        }
        return null;
    }

    public static String str_list_to_csv(List<String> names)
    {
        StringBuilder namesStr = new StringBuilder();
        for(String name : names)
        {
            namesStr = namesStr.length() > 0 ? namesStr.append(",").append(name) : namesStr.append(name);
        }
        return namesStr.toString();
    }


    public interface gnss_parser_callbacks {
        public void on_updated_nmea_params(HashMap<String, Object> params_map);
    }


    public void set_callback(gnss_parser_callbacks cb){
        Log.d(TAG, "set_callback() "+cb);
        m_cb = cb;
    }

    public static final byte[] fromHexString(final String s) {
        String[] v = s.split(" ");
        byte[] arr = new byte[v.length];
        int i = 0;
        for(String val: v) {
            arr[i++] =  Integer.decode("0x" + val).byteValue();

        }
        return arr;
    }

    public static String toHexString(byte[] a) {
        return toHexString(a, 0, a.length);
    }

    public static String toHexString(byte[] a, int offset, int len) {
        StringBuilder sb = new StringBuilder(a.length * 2);
        byte b;
        for(int i = 0; i < len; i++) {
            b = a[offset + i];
            sb.append(String.format("%02X ", b));
        }
        return sb.toString().trim();
    }



}
