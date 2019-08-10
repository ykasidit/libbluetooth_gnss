package com.clearevo.libecodroidbluetooth;
import android.util.Log;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.Closeable;
import java.io.DataInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.concurrent.ConcurrentLinkedQueue;


public class inputstream_to_queue_reader_thread extends Thread implements Closeable {

    int n_read;
    InputStream m_is;
    ConcurrentLinkedQueue<byte[]> m_queue;

    final String TAG = "edg_istqrt";
    final int READ_BUF_SIZE = 2048;
    readline_callbacks m_readline_cb;
    BufferedReader m_buffered_reader;

    //read to queue mode
    public inputstream_to_queue_reader_thread(InputStream is, ConcurrentLinkedQueue<byte[]> queue)
    {
        assert is != null;
        m_is = is;
        m_queue = queue;
    }

    //readline to callback mode
    public inputstream_to_queue_reader_thread(InputStream is, readline_callbacks cb)
    {
        assert is != null;
        m_is = is;

        m_readline_cb = cb;
    }

    public void close()
    {
        Log.d(TAG,"close()");
        try {
            m_is.close();
        } catch (Exception e) {
        }
        m_is = null;

        try {
            if (m_buffered_reader != null) {
                m_buffered_reader.close();
            }
        } catch (Exception e) {
        }
        m_buffered_reader = null;

        this.interrupt();
        m_queue = null;
    }


    @Override
    public void run()
    {
        try {

            boolean readline_mode = false;
            if (m_readline_cb != null) {
                readline_mode = true;
            }
            Log.d(TAG, "readline_mode: "+readline_mode);


            if (readline_mode) {
                m_queue = null;
                m_buffered_reader = new BufferedReader(new InputStreamReader(m_is));
            }

            while (true) {

                if (readline_mode) {
                    String read_line = m_buffered_reader.readLine();
                    m_readline_cb.on_readline(read_line);
                } else {
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
                        throw new Exception("invalid n_read reading from bluetooth input stream: " + n_read);
                    }
                }
            }
        } catch (Exception e) {
            if (m_queue != null) { //dont log exception if close() already
                Log.d(TAG, "inputstream_to_queue_reader_thread ending with exception: " + Log.getStackTraceString(e));
            }
        } finally {
            close();
        }
        Log.d(TAG, "thread ended");
    }
}
