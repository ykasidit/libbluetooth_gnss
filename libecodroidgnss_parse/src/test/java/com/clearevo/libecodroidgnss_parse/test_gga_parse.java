package com.clearevo.libecodroidgnss_parse;

import net.sf.marineapi.nmea.parser.SentenceFactory;
import net.sf.marineapi.nmea.sentence.GGASentence;
import net.sf.marineapi.nmea.sentence.MWVSentence;
import net.sf.marineapi.nmea.sentence.TalkerId;
import net.sf.marineapi.nmea.util.Position;

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

        SentenceFactory sf = SentenceFactory.getInstance();
        GGASentence gga = (GGASentence) sf.createParser(TalkerId.GN, "GGA");
        Position position = new Position(0.1, -0.2, 0.3);
        gga.setPosition(position);

        String example_nmea_gga = gga.toSentence();
        System.out.println("gga sentence: "+example_nmea_gga);

        String[] nmeas = {
                example_nmea_gga,
                "$GAGSV,2,1,07,02,28,068,28,07,04,307,21,13,16,327,29,15,68,339,,0*73\n",
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

        assertTrue(1 == (int) params.get("GN_GGA_count"));
        assertTrue(1 == (int) params.get("GN_RMC_count"));
        assertTrue(1 == (int) params.get("GA_GSV_count"));

        assertTrue(params.get("GN_lat").toString().startsWith("0.1"));
        assertTrue(params.get("GN_lon").toString().startsWith("-0.2"));
    }
}
