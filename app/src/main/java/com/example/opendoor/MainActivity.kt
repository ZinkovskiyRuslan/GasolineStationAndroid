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
            GetfuelVolumeRetry("1"),
            StartFuelFill("2"),
        }

        var m_isClickClose: Boolean = false
        var fuelVolumeStart: String = ""

        var i:Int = 0
        var isNeedGetfuelVolumeRetry: Boolean = false;
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        m_isClickClose = false
        fuelVolumeStart = "0"
        i = 0;
        isNeedGetfuelVolumeRetry = false;
        setContentView(R.layout.activity_main)
        findViewById<TextView>(R.id.fuelVolume).text = "";
        deviceUniqueID = getDeviceUniqueID(this);
        m_address = intent.getStringExtra(SelectDeviceActivity.EXTRA_ADDRESS).toString()

        findViewById<Button>(R.id.startFuelFill).setOnClickListener {
            sendCommand(
                Command.StartFuelFill.value,
                "?"
            )
        }
        findViewById<Button>(R.id.control_led_disconnect).setOnClickListener { exit() }

        var t = this
        connectToBlueToothDevice(t)
        sendCommand(Command.GetfuelVolume.value, "?")
        Thread.sleep(200)

        val handler = Handler()
        handler.post(object : Runnable {
            override fun run() {
                println("$i: $bluetoothReadedMessage")
                if (i == 15 && isNeedGetfuelVolumeRetry) {
                    connectToBlueToothDevice(t);
                    sendCommand(Command.GetfuelVolumeRetry.value, "?");
                }
                if (!m_isClickClose) {
                    var res = readStringThread()
                    //var res = readString()
                    actionFromMessage(res)
                    handler.postDelayed(this, 1000)
                }
                i += 1;
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
                findViewById<TextView>(R.id.fuelVolume).text = "????????????";
            }
        }
    }

    private fun actionFromMessage(message: String) {
        val messageArray = message.split("|").toTypedArray()
        if (messageArray.size != 3)//invalid request
            return;
        if (messageArray[0] != deviceUniqueID)//invalid deviceUniqueID
            return;
        if (messageArray[1] == Command.GetfuelVolume.value || messageArray[1] == Command.GetfuelVolumeRetry.value) {
            if(messageArray[2]=="0")
            {
                findViewById<TextView>(R.id.fuelVolume).text = "";
                isNeedGetfuelVolumeRetry = true;
                disconnect();
            }
            else {
                findViewById<TextView>(R.id.fuelVolume).text =
                    StringBuilder().append(messageArray[2]).append(" ??.").toString()
                fuelVolumeStart = messageArray[2]
            }
        }
        if (messageArray[1] == Command.GetfuelVolumeRetry.value) {
            if(messageArray[2]=="0")
            {
                findViewById<TextView>(R.id.fuelVolume).text = "??????????"
            }
            else {
                findViewById<TextView>(R.id.fuelVolume).text =
                    StringBuilder().append(messageArray[2]).append(" ??.").toString()
                fuelVolumeStart = messageArray[2]
            }
        }
        if (messageArray[1] == Command.StartFuelFill.value) {
            findViewById<TextView>(R.id.fuelVolume).text =
                StringBuilder().append(fuelVolumeStart).append("/").append(messageArray[2])
                    .append(" ??.").toString()
            if(fuelVolumeStart == messageArray[2])
                disconnect()
        }
    }

    /* ???????????????? ?? ???????????????? ???????????? ?????? ???????????? ????????????*/
    private fun readString(): String {
        val buffer = ByteArray(256)
        var bytes = m_bluetoothSocket!!.inputStream.read(buffer)
        return String(buffer, 0, bytes)
    }

    private fun readStringThread(): String {
        val readThread = Thread {
            try {
                /* Example 2
                    while (inputStream.available() == 0);
                    val available = inputStream.available()
                    val bytes = ByteArray(available)
                    inputStream.read(bytes, 0, available)
                    val text = String(bytes)
                 */
                bluetoothReadedMessage = "";
                if( m_bluetoothSocket != null && m_bluetoothSocket!!.inputStream.available()  > 0) {
                    val buffer = ByteArray(256)
                    var bytes = m_bluetoothSocket!!.inputStream.read(buffer)
                    var btReadedMessage = String(buffer, 0, bytes)

                    while (btReadedMessage!!.indexOf("\r\n") == -1) {
                        var bytes = m_bluetoothSocket!!.inputStream.read(buffer)
                        btReadedMessage = StringBuilder().append(btReadedMessage)
                            .append(String(buffer, 0, bytes)).toString()
                    }
                    var msg = btReadedMessage!!.split("\r\n")
                    if (!msg.isNullOrEmpty()) {
                        if (msg.lastIndex > 0) {
                            var v = msg[msg.lastIndex - 1]
                            btReadedMessage = msg[msg.lastIndex - 1];
                        }
                    }
                    if (btReadedMessage!!.indexOf("\r\n") != -1) {
                        btReadedMessage!!.replace("\r\n", "")
                    }
                    if (btReadedMessage.isNotEmpty())
                        bluetoothReadedMessage = btReadedMessage
                    println(
                        StringBuilder().append("btReadedMessage = ").append(btReadedMessage)
                            .append("; bluetoothReadedMessage = ").append(bluetoothReadedMessage)
                            .append(";")
                    )
                }
            } catch (e: IOException) {
                // TODO Auto-generated catch block
                e.printStackTrace()
            }
        }

        synchronized(readThread) {
            readThread.start()
            readThread.join(900)
            /*
            try {
                if (readThread.isAlive) {
                    // probably really not good practice!
                }
            } catch (e: InterruptedException) {
                // TODO Auto-generated catch block
                e.printStackTrace()
            }*/
            return bluetoothReadedMessage
        }
    }

    @SuppressLint("HardwareIds")
    private fun getDeviceUniqueID(activity: Activity): String? {
        return Secure.getString(activity.contentResolver, Secure.ANDROID_ID)
    }

    private fun exit() {
        m_isClickClose = true;
        disconnect()
        finish()
    }
    private fun disconnect() {
       // m_isClickClose = true
        Thread.sleep(1000)
        if (m_bluetoothSocket != null) {
            try {
                m_bluetoothSocket!!.close()
                m_bluetoothSocket = null
                m_isConnected = false
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }
    }

    private fun connectToBlueToothDevice(context: Context){
        m_isClickClose = false;
        var tryCnt = 0;
        ConnectToDevice(context).execute()
        tryCnt = 0;
        while (m_bluetoothSocket == null && tryCnt++ <= 20) {
            Thread.sleep(100)
        }

        tryCnt = 0;
        while (!m_bluetoothSocket!!.isConnected && tryCnt++ <= 100) {
            Thread.sleep(100)
        }

        if (m_bluetoothSocket == null || !m_bluetoothSocket!!.isConnected) {
            val handler = Handler()
            handler.post {
                Toast.makeText(this, "?????? ???????????????????? ?? ??????", Toast.LENGTH_LONG).show()
                exit()
            }
        }
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