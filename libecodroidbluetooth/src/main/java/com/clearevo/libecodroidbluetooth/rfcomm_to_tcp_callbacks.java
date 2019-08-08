package com.clearevo.libecodroidbluetooth;

    public interface rfcomm_to_tcp_callbacks {
        public void on_bt_connected();
        public void on_bt_disconnected();
        public void on_target_tcp_connected();
        public void on_target_tcp_disconnected();
    }
