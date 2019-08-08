package com.clearevo.libecodroidbluetooth;

import android.util.Log;

import java.io.Closeable;
import java.io.OutputStream;
import java.util.concurrent.ConcurrentLinkedQueue;
/**
 * Created by kasidit on 4/26/18.
 */

public class queue_to_outputstream_writer_thread extends Thread implements Closeable {

    ConcurrentLinkedQueue<byte[]> m_queue;
    OutputStream m_os;

    final String TAG = "edg_qtowt";

    public queue_to_outputstream_writer_thread(ConcurrentLinkedQueue<byte[]> queue, OutputStream os)
    {
        m_queue = queue;
        m_os = os;
    }

    public void close()
    {
        Log.d(TAG,"close()");
        try {
            m_os.close();
        } catch (Exception e) {
        }
        m_os = null;
        this.interrupt();
        m_queue = null;
    }

    public void run()
    {
        try {
            while (true) {
                //System.out.println("m_queue poll pre poll");
                byte[] out_buf = m_queue.poll();
                //System.out.println("m_queue poll buf:" + out_buf);
                if (out_buf != null && out_buf.length > 0) {
                    m_os.write(out_buf);
                }
            }
        } catch (Exception e) {
            if (m_queue != null) { //dont log exception if close() already
                Log.d(TAG, "thread ending with exception: " + Log.getStackTraceString(e));
            }
        } finally {
            close();
        }
        Log.d(TAG, "thread ended");
    }
}
