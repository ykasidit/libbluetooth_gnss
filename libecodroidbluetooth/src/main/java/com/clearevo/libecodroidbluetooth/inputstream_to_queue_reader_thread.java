package com.clearevo.libecodroidbluetooth;

import android.util.Log;

import java.io.Closeable;
import java.io.InputStream;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Created by kasidit on 4/26/18.
 */

public class inputstream_to_queue_reader_thread extends Thread implements Closeable {

    int n_read;
    InputStream m_is;
    ConcurrentLinkedQueue<byte[]> m_queue;

    final String TAG = "edg_istqrt";
    final int READ_BUF_SIZE = 2048;

    public inputstream_to_queue_reader_thread(InputStream is, ConcurrentLinkedQueue<byte[]> queue)
    {
        assert is != null;

        m_is = is;
        m_queue = queue;
    }

    public void close()
    {
        Log.d(TAG,"close()");
        try {
            m_is.close();
        } catch (Exception e) {
        }
        m_is = null;
        this.interrupt();
        m_queue = null;
    }

    public void run()
    {
        try {
            while (true) {
                //Log.d(TAG,"pre m_is.read() m_is: "+m_is);
                byte[] read_tmp_buff = new byte[READ_BUF_SIZE];
                n_read = m_is.read(read_tmp_buff);
                if (n_read > 0) {
                    byte[] buf = new byte[n_read];
                    System.arraycopy(read_tmp_buff, 0, buf, 0, n_read);
                    if (m_queue != null) {
                        m_queue.add(buf);
                    }
                }
                if (n_read <= 0) {
                    throw new Exception("invalid n_read reading from bluetooth input stream: "+n_read);
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
