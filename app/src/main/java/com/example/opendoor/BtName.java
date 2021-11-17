package com.example.opendoor;

import android.bluetooth.BluetoothDevice;

public class BtName {
    BluetoothDevice device;
    @Override
    public String toString() {
        return device.getName();
    }

    public BtName(BluetoothDevice device) {
        this.device = device;
    }
}
