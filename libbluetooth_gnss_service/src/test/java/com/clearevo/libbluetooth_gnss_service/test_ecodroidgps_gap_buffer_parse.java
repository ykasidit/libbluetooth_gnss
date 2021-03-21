package com.clearevo.libbluetooth_gnss_service;

import org.junit.Test;

import static com.clearevo.libecodroidgnss_parse.gnss_sentence_parser.fromHexString;

/**
 * Example local unit test, which will execute on the development machine (host).
 *
 * @see <a href="http://d.android.com/tools/testing">Testing documentation</a>
 */
public class test_ecodroidgps_gap_buffer_parse {

    @Test
    public void test() throws Exception {

        byte[] ba = fromHexString("02 01 1A 03 03 AA FE 12 16 AA FE 30 00 e1 07 26 63 49 f9 d9 9c b6 e5 bf 56 60"); // see format/assert buffer from https://github.com/ykasidit/ecodroidgps/blob/master/test_gen_edg_broadcast_buffer.py
        ecodroidgps_gap_buffer_parser.ecodroidgps_broadcasted_location loc = ecodroidgps_gap_buffer_parser.parse(ba);
        System.out.println("lat: "+loc.lat);
        System.out.println("lon: "+loc.lon);
        System.out.println("ts: "+loc.timestamp);
        assert loc.lat == 123.1234567;
        assert loc.lon == -123.1234567;
        assert loc.timestamp == 1616297957;
    }
}