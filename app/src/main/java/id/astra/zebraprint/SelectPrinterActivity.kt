package id.astra.zebraprint

import android.annotation.SuppressLint
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.isVisible
import androidx.recyclerview.widget.LinearLayoutManager
import com.afollestad.materialdialogs.MaterialDialog
import com.zebra.sdk.comm.ConnectionException
import com.zebra.sdk.printer.discovery.BluetoothDiscoverer
import com.zebra.sdk.printer.discovery.DeviceFilter
import com.zebra.sdk.printer.discovery.DiscoveredPrinter
import com.zebra.sdk.printer.discovery.DiscoveredPrinterBluetooth
import com.zebra.sdk.printer.discovery.DiscoveryHandler
import id.astra.zebraprint.databinding.ActivitySelectPrinterBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SelectPrinterActivity : AppCompatActivity(), DiscoveryHandler {
    lateinit var binding: ActivitySelectPrinterBinding
    var list: MutableList<DiscoveredPrinter> = mutableListOf()
    lateinit var printerAdapter: AdapterPrinterDevice
    lateinit var preferences: SharedPreferences
    var permissionGranted = false
    var requestPermission =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            permissionGranted = !it.containsValue(false)
            if (permissionGranted){
                discoveredDevice()
            }else{
                showToast("Permission not granted")
            }
        }
    var requestEnableBluetooth =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            if (isBluetoothEnable()){
                discoveredDevice()
            }else{
                showToast("Bluetooth not active")
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySelectPrinterBinding.inflate(layoutInflater)
        setContentView(binding.root)
        initUi()
        preferences = getSharedPreferences("mypref", MODE_PRIVATE)

    }

    private fun initUi() {
        discoveredDevice()
        setSupportActionBar(binding.toolbar)
        supportActionBar?.apply {
            title = getString(R.string.text_select_printer)
            setDisplayHomeAsUpEnabled(true)
            setDisplayShowHomeEnabled(true)
        }
        binding.toolbar.setNavigationOnClickListener {
            setResult(Activity.RESULT_CANCELED)
            finish()
        }
        binding.btnRetry.setOnClickListener{
            discoveredDevice()
        }
        onBackPressedDispatcher.addCallback(object : OnBackPressedCallback(true){
            override fun handleOnBackPressed() {
                setResult(Activity.RESULT_CANCELED)
                finish()
            }

        })

    }

    fun discoveredDevice() {
        try {
            list.clear()
            if (permissionGranted) {
                if (isBluetoothEnable()) {
                    showLoadingBar()
                    binding.notfoundPrinter.isVisible = false
                    BluetoothDiscoverer.findPrinters(this, this, DeviceFilter { true })
                } else {
                    requestEnableBluetooth.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
                }

            } else {
                requestPermission()
            }
        } catch (e: ConnectionException) {
            Log.d("catch discover", "com")
            hideLoadingBar()
            showToast(e.message.toString())
        }
    }

    private fun requestPermission() {
        val permissions = mutableListOf(
            android.Manifest.permission.ACCESS_FINE_LOCATION,
            android.Manifest.permission.BLUETOOTH
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions.add(android.Manifest.permission.BLUETOOTH_SCAN)
            permissions.add(android.Manifest.permission.BLUETOOTH_CONNECT)
        }
        requestPermission.launch(permissions.toTypedArray())
    }

    override fun foundPrinter(printer: DiscoveredPrinter?) {
        if (printer != null) {
            list.add(printer)
            Log.e("NOTIFY PRINTER FOUND", "$printer")
        }
    }

    override fun discoveryFinished() {
        Log.e("NOTIFY FINISH", "Finish")
        hideLoadingBar()
        if (list.isNotEmpty()){
            printerAdapter = AdapterPrinterDevice(list.map { it as DiscoveredPrinterBluetooth })
            binding.rclvPrinter.apply {
                adapter = printerAdapter
                layoutManager = LinearLayoutManager(this@SelectPrinterActivity, LinearLayoutManager.VERTICAL, false)
                setHasFixedSize(true)
            }
            printerAdapter.onPrinterSelected {
                preferences.edit().putString(Const.MAC_ADDRESS, it.address).apply()
                preferences.edit().putString(Const.DEVICE_NAME, it.friendlyName).apply()
                setResult(Activity.RESULT_OK)
                finish()
            }
        }else{
            binding.notfoundPrinter.isVisible = true
            showToast("Cloud not find printer")
        }
    }

    override fun discoveryError(message: String?) {
        Log.e(" notify error", message.toString())
        showToast(message.toString())
    }

    fun showLoadingBar(){
        binding.loadingBar.show()
    }

    fun hideLoadingBar(){
        binding.loadingBar.hide()
    }
}