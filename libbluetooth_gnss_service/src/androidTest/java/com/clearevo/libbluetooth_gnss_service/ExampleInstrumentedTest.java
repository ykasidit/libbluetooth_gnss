package com.clearevo.libbluetooth_gnss_service;

import android.content.Context;
import android.content.Intent;
import android.support.test.InstrumentationRegistry;
import android.support.test.runner.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.*;

/**
 * Instrumented test, which will execute on an Android device.
 *
 * @see <a href="http://d.android.com/tools/testing">Testing documentation</a>
 */
@RunWith(AndroidJUnit4.class)
public class ExampleInstrumentedTest {
    @Test
    public void useAppContext() {
        // Context of the app under test.
        Context appContext = InstrumentationRegistry.getTargetContext();
        assertEquals("com.clearevo.libbluetooth_gnss_service.test", appContext.getPackageName());

        Intent intent = new Intent(appContext, bluetooth_gnss_service.class);
        intent.putExtra("bdaddr", "B8:27:EB:0C:6F:E6");
        appContext.startService(intent);

    }
}
