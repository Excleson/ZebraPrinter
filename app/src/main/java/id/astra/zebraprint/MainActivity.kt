@file:OptIn(DelicateCoroutinesApi::class)

package id.astra.zebraprint

import Const.DEVICE_NAME
import Const.MAC_ADDRESS
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.*
import android.net.Uri
import android.os.*
import android.util.Log
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import com.zebra.sdk.comm.BluetoothConnection
import com.zebra.sdk.comm.Connection
import com.zebra.sdk.comm.ConnectionException
import com.zebra.sdk.device.ProgressMonitor
import com.zebra.sdk.graphics.internal.ZebraImageAndroid
import com.zebra.sdk.printer.SGD
import com.zebra.sdk.printer.ZebraPrinterFactory
import id.astra.zebraprint.databinding.ActivityMainBinding
import kotlinx.coroutines.*
import org.json.*
import java.io.File
import kotlin.math.roundToInt
import java.nio.charset.Charset
import java.nio.file.Files
import java.nio.file.Paths

class MainActivity : AppCompatActivity(),ConfirmationPopUp.ConfirmationListener {

    lateinit var binding: ActivityMainBinding
    lateinit var preferences: SharedPreferences
    val TAG = this::class.java.simpleName
    var pdfUri: Uri? = null
    var sourcePage: String? = null
    var permissionGranted = false

    private val resultIntent = Intent()

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
        /*val pdfFile = getFileFromUri(it)
        pdfFile?.let {file->
            val bitmap = pdfToBitmap(file)
            saveBitmapToFile(bitmap)
        }*/
        binding.pdfViewer.fromUri(it)
            .enableAntialiasing(true)
            .enableDoubletap(false)
            .pageFling(false)
            .pageSnap(false)
            .disableLongpress()
            .fitEachPage(true)
            .load()

    }

    override fun onConfirmDialog(retVal: Boolean) {
        if(retVal){
            showLoading()
            val address = preferences.getString(MAC_ADDRESS, null)
            address?.let { addr ->
                getFileFromUri(pdfUri)?.let {
                    connectToPrinterPdf(addr, it)
                }
            }
        }else{
            setResult(RESULT_CANCELED, resultIntent)
            finish()
        }
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
        if (intent.action == Intent.ACTION_SEND) {
            //val jsonParam = JSONTokener(intent.extras.toString()).nextValue() as JSONObject
            //val uri = jsonParam.getString("uri").toUri()
            //val source = jsonParam.getString("source")
            sourcePage =  intent.getStringExtra("src")
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
                            val filePdf = getFileFromUri(pdfUri)
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

    fun requestPermission() {
        val permissions = mutableListOf(
            android.Manifest.permission.ACCESS_FINE_LOCATION,
            android.Manifest.permission.BLUETOOTH,
            android.Manifest.permission.BLUETOOTH_ADMIN
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions.add(android.Manifest.permission.BLUETOOTH_SCAN)
            permissions.add(android.Manifest.permission.BLUETOOTH_CONNECT)
            permissions.add(android.Manifest.permission.BLUETOOTH_ADMIN)
        }
        requestPermission.launch(permissions.toTypedArray())
    }

    fun openPdf() {
        getPdf.launch("application/pdf")
    }

    fun printPdf(pdfFile: File) {
        if(sourcePage!=null && sourcePage == "pkb"){
            hideLoading()
            ConfirmationPopUp.newInstance(getString(R.string.pkb_con_title),getString(R.string.pkb_con_text))
                .show(supportFragmentManager,"Confirmation")
        }else{
            val address = preferences.getString(MAC_ADDRESS, null)
            address?.let { addr ->
                connectToPrinterPdf(addr, pdfFile)
            }
        }
    }

    fun connectToPrinterPdf(mPrinterToConnectToMACAddress: String, pdfFile: File) {
        val handler = CoroutineExceptionHandler { ctx, e ->
            Log.e("Connect", "Connection Failed: " + e.message)
            hideLoading()
            binding.btnPrint.isEnable(true)
            showToast(e.message.toString())
        }

        val scope = CoroutineScope(Dispatchers.IO + handler)

        scope.launch {
            val connection: Connection = BluetoothConnection(mPrinterToConnectToMACAddress)
            connection.open()
            // Verify Printer Supports PDF
            if (zebraPrinterSupportsPDF(connection)) {
                if (connection.isConnected) {
                    printPdfImage(connection, pdfFile)
                    //sendPdfToPrinter(connection, pdfFile)
                }
            } else {
                if (connection.isConnected) {
                    printPdfImage(connection, pdfFile)
                }
            }
        }
    }

    fun zebraPrinterSupportsPDF(connection: Connection): Boolean {
        return try{
            //Enable emulation pdf
            SGD.SET("apl.enable","pdf", connection)
            SGD.SET("ezpl.print_width","575",connection)
            // Use SGD command to check if apl.enable returns "pdf"
            val printerInfo: String = SGD.GET("apl.enable", connection)
            Log.wtf("PDF ENABLE", printerInfo)
            printerInfo == "pdf"
        }catch (e: ConnectionException){
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

    private fun printPdfImage(connection: Connection, pdfFile: File) {
        val errorHandler = CoroutineExceptionHandler { coroutineContext, e ->
            Log.e(TAG, e.message.toString())
            hideLoading()
            binding.btnPrint.isEnable(true)
            showToast(e.message.toString())
        }
        val scope = CoroutineScope(Dispatchers.IO + errorHandler)

        scope.launch {
            SGD.SET("apl.enable", "none", connection)
            connection.write("! U1 JOURNAL\r\n! U1 SETFF 50 2\r\n".toByteArray())

            val bitmap = pdfToBitmap(pdfFile)
            val h :Int = bitmap?.height!!

            SGD.SET("zpl.label_length",h.toString(),connection)
            val printer = ZebraPrinterFactory.getInstance(connection)

            // Verify Printer Status is Ready
            val printerStatus = printer.currentStatus

            if (printerStatus.isReadyToPrint) {
                printerStatus.labelLengthInDots = h*203
                printer.printImage(ZebraImageAndroid(bitmap), 0, 0, -1, -1, false)

                setResult(RESULT_OK, resultIntent)
            } else {
                hideLoading()
                binding.btnPrint.isEnable(true)
                if (printerStatus.isPaused) {
                    Log.e(TAG, "Printer paused")
                    showToast("Print paused")
                } else if (printerStatus.isHeadOpen) {
                    Log.e(TAG, "Printer head open")
                    showToast("Print head open")
                } else if (printerStatus.isPaperOut) {
                    Log.e(TAG, "Printer is out of paper")
                    showToast("Print is out of paper")
                } else {
                    Log.e(TAG, "Unknown error occurred")
                    showToast("Unknown error occurred")
                }
                setResult(RESULT_CANCELED,resultIntent)

            }
            // Make sure the data got to the printer before closing the connection
            // Thread.sleep(500)
            delay(500)
            binding.btnPrint.isEnable(true)

            connection.close()
            pdfFile.delete()
            hideLoading()
            finish()
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    fun printPdf(mPrinterConnection: Connection, pdfFile: File) {

        val errorHandler = CoroutineExceptionHandler { coroutineContext, e ->
            Log.e(TAG, e.message.toString())
            hideLoading()
            binding.btnPrint.isEnable(true)
            showToast(e.message.toString())
        }
        val scope = CoroutineScope(Dispatchers.IO + errorHandler)
        scope.launch {
            val path = pdfFile.absolutePath
            val printer = ZebraPrinterFactory.getInstance(mPrinterConnection)

            // Verify Printer Status is Ready
            val printerStatus = printer.currentStatus

            if (printerStatus.isReadyToPrint) {
                // Send the data to printer as a byte array.
                hideLoading()
                showLoadingProgress("Sedang mengirim data ke printer...")
                printer.sendFileContents(pdfFile.absolutePath) { write, total ->
                    val rawProgress = (write * 100 / total).toDouble()
                    val progress = rawProgress.roundToInt()
                    setLoadingProgress(progress)
                    if (progress == 100) {
                        hideLoadingProgress()
                        showToast("Print finish")
                        setResult(RESULT_OK, resultIntent)
                        finish()
                    }
                }

            } else {
                hideLoading()
                binding.btnPrint.isEnable(true)
                if (printerStatus.isPaused) {
                    Log.e(TAG, "Printer paused")
                    showToast("Print paused")
                } else if (printerStatus.isHeadOpen) {
                    Log.e(TAG, "Printer head open")
                    showToast("Print head open")
                } else if (printerStatus.isPaperOut) {
                    Log.e(TAG, "Printer is out of paper")
                    showToast("Print is out of paper")
                } else {
                    Log.e(TAG, "Unknown error occurred")
                    showToast("Unknown error occurred")
                }
                setResult(RESULT_CANCELED, resultIntent)
            }
            // Make sure the data got to the printer before closing the connection
            // Thread.sleep(500)
            delay(500)
            binding.btnPrint.isEnable(true)
            hideLoading()
            mPrinterConnection.close()
            pdfFile.delete()
            finish()
        }
    }

    fun View.isEnable(boolean: Boolean) {
        MainScope().launch {
            withContext(Dispatchers.Main){
                this@isEnable.isEnabled = boolean
            }
        }
    }

}