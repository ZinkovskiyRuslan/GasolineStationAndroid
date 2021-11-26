package com.example.opendoor;

import android.bluetooth.BluetoothDevice;

public class BtName {
    BluetoothDevice device;
    @Override
    public String toString()
    {
        return new StringBuilder(String.format("%40s", new StringBuilder(device.getName()).reverse().toString())).reverse().toString();
    }


    public BtName(BluetoothDevice device) {
        this.device = device;
    }
}
