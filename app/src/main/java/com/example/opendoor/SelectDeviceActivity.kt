package com.example.opendoor
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.*
import androidx.appcompat.app.AppCompatActivity


class SelectDeviceActivity : AppCompatActivity() {
    private var m_bluetoothAdapter: BluetoothAdapter? = null
    private lateinit var m_pairedDevices: Set<BluetoothDevice>
    private val REQUEST_ENABLE_BLUETOOTH = 1
    private var HAS_EXTRA_ADDRESS = false

    companion object {
        val EXTRA_ADDRESS: String = "24:6F:28:D1:22:8E"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.select_device_layout)

        m_bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
        if (m_bluetoothAdapter == null) {
            toast("bluetooth не поддерживается")
            return
        }
        if (!m_bluetoothAdapter!!.isEnabled) {
            val enableBluetoothIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            startActivityForResult(enableBluetoothIntent, REQUEST_ENABLE_BLUETOOTH)
        }
        Thread.sleep(300);
        pairedDeviceList()
        findViewById<Button>(R.id.select_device_refresh).setOnClickListener { pairedDeviceList() }

        if (HAS_EXTRA_ADDRESS)
            startMainActivity()

    }

    private fun pairedDeviceList() {
        m_pairedDevices = m_bluetoothAdapter!!.bondedDevices
        //val list: ArrayList<BluetoothDevice> = ArrayList()
        var list: ArrayList<BtName> = ArrayList()
        if (!m_pairedDevices.isEmpty()) {
            for (device: BluetoothDevice in m_pairedDevices) {
                val n =  BtName(device)
                list.add(n)
                if (device.address == EXTRA_ADDRESS)
                    HAS_EXTRA_ADDRESS = true;
                Log.i("device", "" + device)
            }
        } else {
            toast("Нет сопряженных устройств")
        }

        val adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, list)
        findViewById<ListView>(R.id.select_device_list).adapter = adapter
        findViewById<ListView>(R.id.select_device_list).onItemClickListener =
            AdapterView.OnItemClickListener { _, _, position, _ ->
                val device: BtName = list[position]
                val address: String = device.toString()

                val intent = Intent(this, MainActivity::class.java)
                intent.putExtra(EXTRA_ADDRESS, device.device.address)
                startActivity(intent)
            }
    }

    private fun startMainActivity() {
        val intent = Intent(this, MainActivity::class.java)
        intent.putExtra(EXTRA_ADDRESS, EXTRA_ADDRESS)
        startActivity(intent)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_ENABLE_BLUETOOTH) {
            if (resultCode == Activity.RESULT_OK) {
                if (m_bluetoothAdapter!!.isEnabled) {
                    toast("Bluetooth включен")
                    pairedDeviceList()
                    findViewById<Button>(R.id.select_device_refresh).setOnClickListener { pairedDeviceList() }
                    if (HAS_EXTRA_ADDRESS)
                        startMainActivity()
                } else {
                    toast("Bluetooth отключен")
                }
            } else if (resultCode == Activity.RESULT_CANCELED) {
                toast("Bluetooth отклонён")
            }
        }
    }

    fun toast(message: CharSequence) =
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
}