package com.clearevo.libecodroidbluetooth;
import android.util.Log;

import java.io.Closeable;
import java.io.InputStream;
import java.io.PushbackInputStream;
import java.util.concurrent.ConcurrentLinkedQueue;


public class inputstream_to_queue_reader_thread extends Thread implements Closeable {

    int n_read;
    InputStream m_is;
    ConcurrentLinkedQueue<byte[]> m_queue;

    final String TAG = "btgnss_istqrt";
    public static final int READ_BUF_SIZE = 20480;
    public static final int PUSHBACK_BUF_SIZE = READ_BUF_SIZE*10;
    public static final byte[] CRLF = {0x0D, 0x0A};
    readline_callbacks m_readline_cb;
    PushbackInputStream m_pb_is;
    String wk = "kasidit_yak_pai_wangkeaw_leaw_na";

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
            if (m_pb_is != null) {
                m_pb_is.close();
            }
        } catch (Exception e) {
        }
        m_pb_is = null;

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
                m_pb_is = new PushbackInputStream(m_is, PUSHBACK_BUF_SIZE);
            }

            while (true) {

                if (readline_mode) {

                    /*
                    DONT use 'readers' that do readline() as they return strings and this 'encodes' our raw packets which are changed when we do .getbytes('ascii') later
                    so use pusbackinputstreams and read until we get 0d 0a instead...
                    */
                    byte[] read_line = bytes_readline(m_pb_is);
                    m_readline_cb.on_readline(read_line); //if pushback buffer is full then this thread will end and exception logged, conn closed so conn watcher would trigger disconnected stage so user would know somethings wrong anyway...

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
                        throw new Exception("invalid n_read reading from input stream: " + n_read);
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


    //NOTE: below func can fail with a full pushback buffer full - handle its exception accordingly
    public static byte[] bytes_readline(PushbackInputStream pb_is) throws Exception
    {
        return bytes_read_until_sequence(pb_is, CRLF);
    }

    public static byte[] bytes_read_until_sequence(PushbackInputStream pb_is, byte[] suffix_sequence) throws Exception
    {
        if (suffix_sequence.length == 0) {
            throw new Exception("invalid suffix_sequence.length == 0 supplied");
        }

        byte[] read_buffer = new byte[READ_BUF_SIZE];
        int nread = pb_is.read(read_buffer);

        int crlf_end_pos = -1;
        int suffix_seq_len = suffix_sequence.length;
        for (int i = 0; i < nread; i++) {

            if (i >= suffix_seq_len-1) {
                for (int j = 0; j < suffix_seq_len; j++) {
                    if (read_buffer[i-j] == suffix_sequence[suffix_seq_len-1-j]) {
                        if (j == suffix_seq_len-1) {
                            crlf_end_pos = i;
                            break;
                        } else {
                            continue;
                        }
                    } else {
                        break;
                    }
                }
                if (crlf_end_pos != -1)
                    break;
            }
        }

        //System.out.println("crlf_end_pos: "+crlf_end_pos);
        //if cant find crlf the unread this buffer
        if (crlf_end_pos == -1) {
            if (read_buffer != null) {
                pb_is.unread(read_buffer);
            }
            return null;
        }

        //if read pos is last in buffer already so no need to push back - just deliver whole buffer
        if (crlf_end_pos == read_buffer.length-1) {
            return read_buffer;
        } else {
            //read pos is not last so deliver up to and including crlf and push back the rest
            int readline_buffer_len = crlf_end_pos+1;  //yes, length is offset+1
            byte[] readline_buffer = new byte[readline_buffer_len];
            System.arraycopy(read_buffer, 0, readline_buffer, 0, readline_buffer_len); //yes, length is offset+1
            int remainder_len = read_buffer.length - readline_buffer_len;
            //recheck just to be sure and future proof...
            if (remainder_len > 0) {
                byte[] pushback_buffer = new byte[remainder_len];
                System.arraycopy(read_buffer, crlf_end_pos+1, pushback_buffer, 0, remainder_len);
                pb_is.unread(pushback_buffer);
            }
            return readline_buffer;
        }
    }
}
