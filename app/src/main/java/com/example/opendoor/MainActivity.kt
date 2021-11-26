package com.example.opendoor

import android.annotation.SuppressLint
import android.app.Activity
import android.app.ProgressDialog
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.os.AsyncTask
import android.os.Bundle
import android.os.Handler
import android.provider.Settings.Secure
import android.util.Log
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import java.io.IOException
import java.util.*


@Suppress("UsePropertyAccessSyntax")
class MainActivity : AppCompatActivity() {
    companion object {
        var m_myUUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
        var m_bluetoothSocket: BluetoothSocket? = null
        lateinit var m_progress: ProgressDialog
        lateinit var m_bluetoothAdapter: BluetoothAdapter
        var m_isConnected: Boolean = false
        lateinit var m_address: String
        var bluetoothReadedMessage: String = ""

        var deviceUniqueID: String? = ""

        enum class Command(val value: String) {
            GetfuelVolume("0"),
            StartFuelFill("1"),
        }

        var m_isClickClose: Boolean = false
        var fuelVolumeStart: String = ""
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        m_isClickClose = false
        fuelVolumeStart = "0"
        var tryCnt = 0;
        setContentView(R.layout.activity_main)
        findViewById<TextView>(R.id.fuelVolume).text = "";
        deviceUniqueID = getDeviceUniqueID(this);
        m_address = intent.getStringExtra(SelectDeviceActivity.EXTRA_ADDRESS).toString()
        ConnectToDevice(this).execute()
        findViewById<Button>(R.id.startFuelFill).setOnClickListener {
            sendCommand(
                Command.StartFuelFill.value,
                "?"
            )
        }
        findViewById<Button>(R.id.control_led_disconnect).setOnClickListener { disconnect() }

        tryCnt = 0;
        while (m_bluetoothSocket == null && tryCnt++ <= 20) {
            Thread.sleep(100)
        }

        tryCnt = 0;
        while (!m_bluetoothSocket!!.isConnected && tryCnt++ <= 20) {
            Thread.sleep(100)
        }

        if (m_bluetoothSocket == null || !m_bluetoothSocket!!.isConnected) {
            val handler = Handler()
            handler.post {
                Toast.makeText(this, "Нет соединения с АЗС", Toast.LENGTH_LONG).show()
                disconnect()
            }
        }

        sendCommand(Command.GetfuelVolume.value, "?")
        Thread.sleep(200)
        val handler = Handler()
        handler.post(object : Runnable {
            override fun run() {
                if (!m_isClickClose) {
                    var res = readStringThread()
                    //var res = readString()
                    actionFromMessage(res)
                    handler.postDelayed(this, 1000)
                }
            }
        })

    }

    private fun sendCommand(command: String, value: String) {
        if (m_bluetoothSocket != null) {
            try {
                var message = StringBuilder()
                    .append(deviceUniqueID)
                    .append("|")
                    .append(command)
                    .append("|")
                    .append(value)
                    .toString()
                m_bluetoothSocket!!.outputStream.write(message.toByteArray())
                bluetoothReadedMessage
                //var res = readStringThread()
                //var res = readString()
                //actionFromMessage(res)
            } catch (e: IOException) {
                e.printStackTrace()
                findViewById<TextView>(R.id.fuelVolume).text = "00";
            }
        }
    }

    private fun actionFromMessage(message: String) {
        val messageArray = message.split("|").toTypedArray()
        if (messageArray.size != 3)//invalid request
            return;
        if (messageArray[0] != deviceUniqueID)//invalid deviceUniqueID
            return;
        if (messageArray[1] == Command.GetfuelVolume.value) {
            findViewById<TextView>(R.id.fuelVolume).text =
                StringBuilder().append(messageArray[2]).append(" л.").toString()
            fuelVolumeStart = messageArray[2]
        }
        if (messageArray[1] == Command.StartFuelFill.value) {
            findViewById<TextView>(R.id.fuelVolume).text =
                StringBuilder().append(fuelVolumeStart).append("/").append(messageArray[2])
                    .append(" л.").toString()
        }
    }

    /* Зависает в ожидании ответа при пустом буфере*/
    private fun readString(): String {
        val buffer = ByteArray(256)
        var bytes = m_bluetoothSocket!!.inputStream.read(buffer)
        return String(buffer, 0, bytes)
    }

    private fun readStringThread(): String {
        val readThread = Thread {
            try {
                bluetoothReadedMessage = "";
                val buffer = ByteArray(256)
                var bytes = m_bluetoothSocket!!.inputStream.read(buffer)
                bluetoothReadedMessage = String(buffer, 0, bytes)

                while (bluetoothReadedMessage!!.indexOf("\r\n") == -1) {
                    var bytes = m_bluetoothSocket!!.inputStream.read(buffer)
                    bluetoothReadedMessage = StringBuilder().append(bluetoothReadedMessage)
                        .append(String(buffer, 0, bytes)).toString()
                }
                var msg = bluetoothReadedMessage!!.split("\r\n")
                if (!msg.isNullOrEmpty()) {
                    if (msg.lastIndex > 0) {
                        var v = msg[msg.lastIndex - 1]
                        bluetoothReadedMessage = msg[msg.lastIndex - 1];
                    }
                }
                if (bluetoothReadedMessage!!.indexOf("\r\n") != -1) {
                    var v = bluetoothReadedMessage
                    v = v + "--"
                    bluetoothReadedMessage!!.replace("\r\n", "")
                }
                println(bluetoothReadedMessage)
            } catch (e: IOException) {
                // TODO Auto-generated catch block
                e.printStackTrace()
            }
        }

        synchronized(readThread) {
            readThread.start()
            readThread.join(900)
            try {
                if (readThread.isAlive) {
                    // probably really not good practice!
                }
            } catch (e: InterruptedException) {
                // TODO Auto-generated catch block
                e.printStackTrace()
            }
            return bluetoothReadedMessage
        }
    }

    @SuppressLint("HardwareIds")
    private fun getDeviceUniqueID(activity: Activity): String? {
        return Secure.getString(activity.contentResolver, Secure.ANDROID_ID)
    }

    private fun disconnect() {
        m_isClickClose = true
        Thread.sleep(500)
        if (m_bluetoothSocket != null) {
            try {
                m_bluetoothSocket!!.close()
                m_bluetoothSocket = null
                m_isConnected = false
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }
        finish()
    }

    private class ConnectToDevice(c: Context) : AsyncTask<Void, Void, String>() {
        private var connectSuccess: Boolean = true
        private val context: Context

        init {
            this.context = c
        }

        override fun onPreExecute() {
            super.onPreExecute()
            m_progress = ProgressDialog.show(context, "Connecting...", "please wait")
        }

        override fun doInBackground(vararg p0: Void?): String? {
            try {
                if (m_bluetoothSocket == null || !m_isConnected) {
                    m_bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
                    val device: BluetoothDevice = m_bluetoothAdapter.getRemoteDevice(m_address)
                    m_bluetoothSocket = device.createInsecureRfcommSocketToServiceRecord(m_myUUID)
                    BluetoothAdapter.getDefaultAdapter().cancelDiscovery()
                    m_bluetoothSocket!!.connect()
                }
            } catch (e: IOException) {
                connectSuccess = false
                e.printStackTrace()
            }
            return null
        }

        override fun onPostExecute(result: String?) {
            super.onPostExecute(result)
            if (!connectSuccess) {
                Log.i("data", "couldn't connect")
            } else {
                m_isConnected = true
            }
            m_progress.dismiss()
        }
    }
}