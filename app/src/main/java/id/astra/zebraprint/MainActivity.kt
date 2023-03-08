package id.astra.zebraprint

import Const.DEVICE_NAME
import Const.MAC_ADDRESS
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.ArrayAdapter
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import com.afollestad.materialdialogs.MaterialDialog
import com.zebra.sdk.comm.BluetoothConnection
import com.zebra.sdk.comm.Connection
import com.zebra.sdk.comm.ConnectionException
import com.zebra.sdk.device.ProgressMonitor
import com.zebra.sdk.printer.SGD
import com.zebra.sdk.printer.ZebraPrinterFactory
import id.astra.zebraprint.databinding.ActivityMainBinding
import id.astra.zebraprint.databinding.LoadingDialogBinding
import kotlinx.coroutines.*
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import kotlin.coroutines.CoroutineContext
import kotlin.math.roundToInt

class MainActivity : AppCompatActivity() {

    lateinit var binding: ActivityMainBinding
    lateinit var preferences: SharedPreferences
    val TAG = this::class.java.simpleName
    var pdfUri: Uri? = null
    var permissionGranted = false
    var requestPermission =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            permissionGranted = !it.containsValue(false)
        }

    var selectPrinterResult =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            if (it.resultCode == Activity.RESULT_OK) {
                updateSelectedPrinter()
                binding.btnPrint.performClick()
            }
        }

    var requestEnableBluetooth =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            if (it.resultCode == Activity.RESULT_OK) {
                binding.btnPrint.performClick()
            }
        }

    var getPdf = registerForActivityResult(ActivityResultContracts.GetContent()) {
        pdfUri = it
        binding.pdfViewer.fromUri(it)
            .enableAntialiasing(true)
            .enableDoubletap(false)
            .pageFling(false)
            .pageSnap(false)
            .disableLongpress()
            .fitEachPage(true)
            .load()

    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        preferences = getSharedPreferences("mypref", MODE_PRIVATE)
        setContentView(binding.root)
        requestPermission()
        checkIntentData()
        initUi()

    }

    private fun checkIntentData() {
        Log.wtf("DATA", intent.data.toString())
        Log.wtf("ACTION", intent.action.toString())
        Log.wtf("TYPE", intent.type.toString())
        if (intent.action == Intent.ACTION_SEND) {
            binding.pdfViewer.fromUri(intent.data)
                .enableAntialiasing(true)
                .enableDoubletap(false)
                .pageFling(false)
                .pageSnap(false)
                .disableLongpress()
                .fitEachPage(true)
                .load()
            pdfUri = intent.data
        }
    }

    private fun initUi() {
        updateSelectedPrinter()
        binding.btnPrint.setOnClickListener {
            val printerAddress = preferences.getString(MAC_ADDRESS, null)
            if (permissionGranted) {
                if (isBluetoothEnable()) {
                    if (!printerAddress.isNullOrEmpty()) {
                        if (pdfUri != null) {
                            val filePdf = getFileFromUri(this, pdfUri)
                            filePdf?.let {
                                showLoading()
                                binding.btnPrint.isEnable(false)
                                Handler(Looper.getMainLooper()).postDelayed({
                                    printPdf(it)
                                }, 1000)
                            }
                        } else {
                            showToast("PDF file not available!")
                        }
                    } else {
                        selectPrinterResult.launch(Intent(this, SelectPrinterActivity::class.java))
                    }
                } else {
                    requestEnableBluetooth.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
                }

            } else {
                requestPermission()
            }
        }

        binding.openPdf.setOnClickListener {
            openPdf()
        }

        binding.btnChangePrinter.setOnClickListener {
            selectPrinterResult.launch(Intent(this, SelectPrinterActivity::class.java))
        }
    }

    private fun updateSelectedPrinter() {
        val printerName = preferences.getString(DEVICE_NAME, null)
        val printerAddress = preferences.getString(MAC_ADDRESS, null)
        if (!printerName.isNullOrEmpty() && !printerAddress.isNullOrEmpty()) {
            binding.apply {
                tvPrinterName.text = printerName
                tvPrinterAddress.text = printerAddress
                btnChangePrinter.text = getString(R.string.text_change_printer)
            }
        } else {
            binding.apply {
                tvPrinterName.text = getString(R.string.text_not_availabe)
                tvPrinterAddress.text = "-"
                btnChangePrinter.text = getString(R.string.text_select_printer)
            }
        }
    }

    private fun isBluetoothEnable(): Boolean {
        val bluetoothManager: BluetoothManager = getSystemService(BluetoothManager::class.java)
        val bluetoothAdapter: BluetoothAdapter? = bluetoothManager.adapter
        if (bluetoothAdapter == null) {
            showToast("This device not support bluetooth")
            return false
        } else {
            return bluetoothAdapter.isEnabled
        }
    }

    fun requestPermission() {
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

    fun openPdf() {
        getPdf.launch("application/pdf")
    }

    fun getFileFromUri(context: Context, contentUri: Uri?): File? {
        // Get Input Stream && Init File
        var pdfFile: File? = null
        try{
            contentUri?.let {
                val inputStream: InputStream? = context.contentResolver.openInputStream(it)
                if (inputStream != null) {
                    try {
                        pdfFile = File.createTempFile(
                            "TempFilePdf",
                            ".pdf",
                            context.getCacheDir()
                        )
                        FileOutputStream(pdfFile).use { output ->
                            val buffer = ByteArray(4 * 1024) // or other buffer size
                            var read: Int
                            while (inputStream.read(buffer).also { read = it } != -1) {
                                output.write(buffer, 0, read)
                            }
                            output.flush()
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                        inputStream.close()
                    } finally {
                        inputStream.close()
                    }
                }
            }
        }catch (e:Exception){
            showToast(e.message.toString())
        }
        return pdfFile
    }

    fun printPdf(pdfFile: File) {
        val address = preferences.getString(MAC_ADDRESS, null)
        address?.let { address ->
            connectToPrinterPdf(address, pdfFile)
        }
    }

    fun connectToPrinterPdf(mPrinterToConnectToMACAddress: String, pdfFile: File) {
        val handler = CoroutineExceptionHandler { ctx, e ->
                Log.e(TAG, "Connection Failed: " + e.message)
                hideLoading()
                binding.btnPrint.isEnable(true)
                showToast(e.message.toString())
            }
        val scope = CoroutineScope(SupervisorJob() + handler)

        scope.launch {
            val connection: Connection = BluetoothConnection(mPrinterToConnectToMACAddress)
            connection.open()
            // Verify Printer Supports PDF
            if (zebraPrinterSupportsPDF(connection)) {
                if (connection.isConnected) {
                    sendPdfToPrinter(connection, pdfFile)
                }
            } else {
                Log.e(TAG, "Printer does not support PDF Printing")
                hideLoading()
                binding.btnPrint.isEnable(true)
                showToast("Printer does not support PDF Printing")
                // Close Connection
                connection.close()
            }
        }

    }

    fun zebraPrinterSupportsPDF(connection: Connection): Boolean {
        return try{
            //Enable emulation pdf
            SGD.SET("apl.enable","pdf", connection)
            // Use SGD command to check if apl.enable returns "pdf"
            val printerInfo: String = SGD.GET("apl.enable", connection)
            Log.wtf("PDF ENABLE", printerInfo)
            printerInfo == "pdf"
        }catch (e:ConnectionException){
            Log.wtf(TAG, e.message.toString())
            showToast(e.message.toString())
            false
        }

    }


    fun sendPdfToPrinter(mPrinterConnection: Connection, pdfFile: File) {
        Looper.prepare()
        try {
            // Get Instance of Printer
            val printer = ZebraPrinterFactory.getInstance(mPrinterConnection);

            // Verify Printer Status is Ready
            val printerStatus = printer.currentStatus;
            Log.wtf("PRINTER STATUS", printerStatus.toString())
            if (printerStatus.isReadyToPrint) {
                // Send the data to printer as a byte array.
                printer.sendFileContents(pdfFile.absolutePath, ProgressMonitor { write, total ->
                    val rawProgress = (write * 100 / total).toDouble()
                    val progress = rawProgress.roundToInt()
                    if (progress == 100){
                        hideLoading()
                        showToast("Print finish")
                    }
                })

                // Make sure the data got to the printer before closing the connection
                Thread.sleep(500)
            } else {
                hideLoading()
                binding.btnPrint.isEnable(true)
                if (printerStatus.isPaused) {
                    Log.e(TAG, "Printer paused")
                } else if (printerStatus.isHeadOpen) {
                    Log.e(TAG, "Printer head open")
                } else if (printerStatus.isPaperOut) {
                    Log.e(TAG, "Printer is out of paper")
                } else {
                    Log.e(TAG, "Unknown error occurred")
                }
            }
        } catch (e: ConnectionException) {
            // Pass Error Up
            Log.e(TAG, e.message.toString())
            hideLoading()
            binding.btnPrint.isEnable(true)
            showToast(e.message.toString())
        } finally {
            try {
                // Close Connections
                Looper.myLooper()?.quit()
                hideLoading()
                binding.btnPrint.isEnable(true)
                mPrinterConnection.close()
            } catch (e: ConnectionException) {
                e.printStackTrace()
                showToast(e.message.toString())
            }
        }
    }

    fun View.isEnable(boolean: Boolean){
        MainScope().launch {
            this@isEnable.isEnabled = boolean
        }
    }


}