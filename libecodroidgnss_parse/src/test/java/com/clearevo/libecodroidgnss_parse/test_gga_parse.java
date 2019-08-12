package com.clearevo.libecodroidgnss_parse;

import org.junit.Test;

import java.util.HashMap;

import static org.junit.Assert.*;

/**
 * Example local unit test, which will execute on the development machine (host).
 *
 * @see <a href="http://d.android.com/tools/testing">Testing documentation</a>
 */
public class test_gga_parse {

    @Test
    public void test() {
        String[] nmeas = {
                "$GNGGA,095519.00,2733.35606,S,15302.15700,E,1,12,0.70,46.6,M,38.3,M,,*63",
                "�b\u00010\u0004\u0001�e�\u0011\u0015\u0004\u0000\u0000\n" +
                        "\u0002\n" +
                        "\u0007\"\u001FZ\u0001W���\u0003\u0006\n" +
                        "\u0007\"?,\u0000����\b\f\n" +
                        "\u0007 \n" +
                        "�\u0000����\u0004\n" +
                        "\n" +
                        "\u0007\u001B\u0014D\u0001\u0015���\u0000\u000F\n" +
                        "\u0007\"\u000E\u001F\u0001����\u0001\u0011\n" +
                        "\u0007&.�\u0000����\u0007\u0013\n" +
                        "\u0004\u0014=�\u0000L���\u000E\u0018\n" +
                        "\u0007\u001D\"�\u0000�\u0003\u0000\u0000\u0002\u001C\n" +
                        "\u0007\u001F\u001Ca\u0000U���\u0011\u001E\n" +
                        "\u0007\u001A\u000B \u0000�\u0002\u0000\u0000\u000B�\n" +
                        "\u0007\u001C\u001CD\u0000�\u0007\u0000\u0000\n" +
                        "�\f\u0004\u0014\u00043\u0001z\u0003\u0000\u0000\t�\n" +
                        "\u0007\u001D\u0010G\u0001!\u0001\u0000\u0000\f�\u0010\u0001\u0000�\u0000\u0000\u0000\u0000\u0000\u0000��\f\u0000\u0000DS\u0001\u0000\u0000\u0000\u0000\u0006�\n" +
                        "\u0007\u001F%�\u0000.\u0000\u0000\u0000\u000F�\n" +
                        "\u0007$D�\u0000A\u0000\u0000\u0000��\u0004\u0000\u0000\u0004�\u0000\u0000\u0000\u0000\u0000\u0012�\u0004\u0004\u0010\f[\u0001\u0000\u0000\u0000\u0000\u0005�\n" +
                        "\u0007\"X_\u0000%\u0001\u0000\u0000\u0010�\n" +
                        "\u0007\u001F8 \u0001e���ٰ�b\u0001\u0003\u0010\u0000�e�\u0011\u0003�\u0000\b��\u0000\u0000=�\u0012\u0000N�$GNRMC,095520.00,A,2733.35607,S,15302.15703,E,0.042,,240719,,,A,V*0A\n"
        };

        gnss_sentence_parser parser = new gnss_sentence_parser();
        for (String nmea : nmeas) {
            parser.parse(nmea);
        }

        HashMap<String, Object> params = parser.get_params();
        for (String key : params.keySet()) {
            System.out.println("param key: "+key+" val: "+params.get(key));
        }

        assertTrue(params.containsKey("sentence_id_GGA"));
        assertTrue(params.containsKey("sentence_id_RMC"));
        assertTrue(params.get("lat").toString().startsWith("-27.555934333333333"));
        assertTrue(params.get("lon").toString().startsWith("153.03595"));
    }
}
