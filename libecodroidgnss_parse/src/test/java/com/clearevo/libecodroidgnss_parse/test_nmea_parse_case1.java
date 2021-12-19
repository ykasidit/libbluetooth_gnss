package com.clearevo.libecodroidgnss_parse;

import net.sf.marineapi.nmea.parser.SentenceFactory;
import net.sf.marineapi.nmea.sentence.GGASentence;
import net.sf.marineapi.nmea.sentence.TalkerId;
import net.sf.marineapi.nmea.util.Position;

import org.junit.Test;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.List;

import static org.junit.Assert.assertTrue;

/**
 * Example local unit test, which will execute on the development machine (host).
 *
 * @see <a href="http://d.android.com/tools/testing">Testing documentation</a>
 */
public class test_nmea_parse_case1 {

    @Test
    public void test() throws Exception {
        String[] nmeas = {
                "$GNGSA,A,3,26,31,10,32,14,16,25,20,18,22,41,,1.34,0.74,1.12*16\n",
                "$GNGSA,A,3,73,80,70,,,,,,,,,,1.34,0.74,1.12*10",
                "$GNRMC,020125.00,A,1845.82207,N,09859.94984,E,0.027,,101219,,,F,V*1A"
        };


        gnss_sentence_parser parser = new gnss_sentence_parser();
        String fp = "/home/kasidit/Downloads/2021-12-17_13-40-09_rx_log.txt";
        File f = new File(fp);
        if (f.exists()) {
            FileInputStream fin = new FileInputStream(f);
            BufferedReader br = new BufferedReader(new InputStreamReader(fin));
            String nmea;
            while (true) {
                nmea = br.readLine();
                if (nmea == null)
                    break;
                parser.parse(nmea.getBytes("ascii"));
            }
            HashMap<String, Object> params = parser.get_params();
            for (String key : params.keySet()) {
                System.out.println("param key: "+key+" val: "+params.get(key));
            }
            double speed = (double) params.get("GN_speed");
            System.out.println("speed: "+speed);
            assertTrue(speed == 0.0);
        }


        for (String nmea : nmeas) {
            parser.parse(nmea.getBytes("ascii"));
        }

        HashMap<String, Object> params = parser.get_params();
        for (String key : params.keySet()) {
            System.out.println("param key: "+key+" val: "+params.get(key));
        }


    }
}
