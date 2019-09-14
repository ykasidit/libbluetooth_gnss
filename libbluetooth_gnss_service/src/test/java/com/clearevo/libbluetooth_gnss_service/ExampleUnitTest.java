package com.clearevo.libbluetooth_gnss_service;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Example local unit test, which will execute on the development machine (host).
 *
 * @see <a href="http://d.android.com/tools/testing">Testing documentation</a>
 */
public class ExampleUnitTest {

    public static String byteArrayToHex(byte[] a) {
        StringBuilder sb = new StringBuilder(a.length * 2);
        for(byte b: a)
            sb.append(String.format("%02x", b));
        return sb.toString();
    }

    @Test
    public void addition_isCorrect() {


        byte[] buffer = bluetooth_gnss_service.fromHexString("B5 6206 01 03 00 F1 00 01 FC 13");
        System.out.println("buffer: "+byteArrayToHex(buffer));

    }
}