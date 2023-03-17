@file:OptIn(DelicateCoroutinesApi::class)

package id.astra.zebraprint

import Const.DEVICE_NAME
import Const.MAC_ADDRESS
import android.app.Activity
import android.app.AlertDialog
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.*
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.*
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.net.toUri
import androidx.core.util.rangeTo
import com.zebra.sdk.comm.BluetoothConnection
import com.zebra.sdk.comm.Connection
import com.zebra.sdk.comm.ConnectionException
import com.zebra.sdk.device.ProgressMonitor
import com.zebra.sdk.graphics.internal.ZebraImageAndroid
import com.zebra.sdk.printer.PrinterLanguage
import com.zebra.sdk.printer.SGD
import com.zebra.sdk.printer.ZebraPrinterFactory
import id.astra.zebraprint.databinding.ActivityMainBinding
import kotlinx.coroutines.*
import org.json.*
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import kotlin.coroutines.CoroutineContext
import kotlin.math.log
import kotlin.math.roundToInt

class MainActivity : AppCompatActivity() {

    lateinit var binding: ActivityMainBinding
    lateinit var preferences: SharedPreferences
    val TAG = this::class.java.simpleName
    var pdfUri: Uri? = null
    var sourcePage: String? = null
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
            //val jsonParam = JSONTokener(intent.extras.toString()).nextValue() as JSONObject
            //val uri = jsonParam.getString("uri").toUri()
            //val source = jsonParam.getString("source")
            Log.wtf("extra",intent.extras.toString())
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

    fun printPdf(pdfFile: File) {
        Log.wtf("source",sourcePage)
        if(sourcePage!=null && sourcePage == "pkb"){
            hideLoading()
            val alertDialogBuilder = AlertDialog.Builder(this)
            alertDialogBuilder.setTitle("Release and Print PKB")
            alertDialogBuilder.setMessage("Apakah anda yakin data PKB yang diinputkan sudah benar?")
            alertDialogBuilder.setPositiveButton("ya",DialogInterface.OnClickListener{dialogInterface, i ->
                showLoading()
                val address = preferences.getString(MAC_ADDRESS, null)
                address?.let { address ->
                    connectToPrinterPdf(address, pdfFile)
                }

            })
            alertDialogBuilder.setNegativeButton("tidak",DialogInterface.OnClickListener{dialogInterface, i ->
                finish()
            })
            alertDialogBuilder.show()
        }else{
            val address = preferences.getString(MAC_ADDRESS, null)
            address?.let { address ->
                connectToPrinterPdf(address, pdfFile)
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
            if (isPrinterSupportsPDF(connection)) {
                if (connection.isConnected) {
                    printPdf(connection, pdfFile)
                }
            } else {
                if (connection.isConnected) {
                    printPdfImage(connection, pdfFile)
                }
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
            connection.write("! UTILITIES\r\nIN-MILLIMETERS\r\nSETFF 10 2\r\nPRINT\r\n".toByteArray())
            val printer = ZebraPrinterFactory.getInstance(connection)

            // Verify Printer Status is Ready
            val printerStatus = printer.currentStatus
            if (printerStatus.isReadyToPrint) {
                val bitmap = pdfToBitmap(pdfFile)
                printer.printImage(ZebraImageAndroid(bitmap), 0, 0, -1, -1, false)
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
            }
            // Make sure the data got to the printer before closing the connection
            // Thread.sleep(500)
            delay(500)
            binding.btnPrint.isEnable(true)
            hideLoading()
            connection.close()
            pdfFile.delete()
        }
        /*try {
            Looper.prepare()
            SGD.SET("apl.enable", "none", connection)
            connection.write("! UTILITIES\r\nIN-MILLIMETERS\r\nSETFF 10 2\r\nPRINT\r\n".toByteArray());
            // Get Instance of Printer
            val printer = ZebraPrinterFactory.getInstance(PrinterLanguage.CPCL, connection)

            // Verify Printer Status is Ready
            val printerStatus = printer.currentStatus
            if (printerStatus.isReadyToPrint) {
                val bitmap = pdfToBitmap(pdfFile)
                printer.printImage(ZebraImageAndroid(bitmap), 0, 0, -1, -1, false)

                // Make sure the data got to the printer before closing the connection
                Thread.sleep(500)
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
                connection.close()
                pdfFile.delete()
            } catch (e: Exception) {
                e.printStackTrace()
                showToast(e.message.toString())
            }
        }*/
    }

    fun printPdf(mPrinterConnection: Connection, pdfFile: File) {

        val errorHandler = CoroutineExceptionHandler { coroutineContext, e ->
            Log.e(TAG, e.message.toString())
            hideLoading()
            binding.btnPrint.isEnable(true)
            showToast(e.message.toString())
        }
        val scope = CoroutineScope(Dispatchers.IO + errorHandler)
        scope.launch {
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
            }
            // Make sure the data got to the printer before closing the connection
            // Thread.sleep(500)
            delay(500)
            binding.btnPrint.isEnable(true)
            hideLoading()
            mPrinterConnection.close()
            pdfFile.delete()
        }

        /*Looper.prepare()
        try {
            // Get Instance of Printer
            val printer = ZebraPrinterFactory.getInstance(mPrinterConnection);

            // Verify Printer Status is Ready
            val printerStatus = printer.currentStatus
            if (printerStatus.isReadyToPrint) {
                // Send the data to printer as a byte array.
                printer.sendFileContents(pdfFile.absolutePath) { write, total ->
                    val rawProgress = (write * 100 / total).toDouble()
                    val progress = rawProgress.roundToInt()
                    if (progress == 100) {
                        hideLoading()
                        showToast("Print finish")
                    }
                }
                // Make sure the data got to the printer before closing the connection
                Thread.sleep(500)
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
                pdfFile.delete()
            } catch (e: ConnectionException) {
                e.printStackTrace()
                showToast(e.message.toString())
            }
        }*/
    }

    fun View.isEnable(boolean: Boolean) {
        MainScope().launch {
            withContext(Dispatchers.Main){
                this@isEnable.isEnabled = boolean
            }
        }
    }


}