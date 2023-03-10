package id.astra.zebraprint

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.ContentValues
import android.content.Context
import android.graphics.*
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import android.util.Log
import android.widget.Toast
import com.afollestad.materialdialogs.MaterialDialog
import com.zebra.sdk.comm.Connection
import com.zebra.sdk.comm.ConnectionException
import com.zebra.sdk.printer.SGD
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.*


var loadingDialog: MaterialDialog? = null

var loadingProgressDialog : MaterialDialog? = null

fun Context.showLoading(messsage:String = "Loading..."){
    CoroutineScope(Dispatchers.Main).launch{
        loadingDialog = MaterialDialog.Builder(this@showLoading)
            .progress(true,0)
            .content(messsage)
            .cancelable(false)
            .build()
        loadingDialog?.show()
    }
}

fun Context.showLoadingProgress(message:String){
    CoroutineScope(Dispatchers.Main).launch{
        loadingProgressDialog = MaterialDialog.Builder(this@showLoadingProgress)
            .content(message)
            .cancelable(false)
            .build()

        loadingProgressDialog?.show()
    }
}
fun hideLoadingProgress(){
    CoroutineScope(Dispatchers.Main).launch{
        delay(500)
        loadingProgressDialog?.dismiss()
    }
}
fun setLoadingProgress(progress:Int){
    CoroutineScope(Dispatchers.Main).launch{
        loadingProgressDialog?.setProgress(progress)
    }
}

fun hideLoading(){
    CoroutineScope(Dispatchers.Main).launch {
        loadingDialog?.let {
            if (it.isShowing){
                it.dismiss()
            }
        }
    }
}

fun Context.showToast(message:String){
    CoroutineScope(Dispatchers.Main).launch {
        Toast.makeText(this@showToast, message, Toast.LENGTH_SHORT).show()
    }
}

fun Context.saveMediaToGallery(bitmap: Bitmap?) {
    //Generating a file name
    val filename = "${System.currentTimeMillis()}.png"

    //Output stream
    var fos: OutputStream? = null

    //For devices running android >= Q
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        //getting the contentResolver
        this.contentResolver?.also { resolver ->

            //Content resolver will process the contentvalues
            val contentValues = ContentValues().apply {

                //putting file information in content values
                put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
                put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES)
            }

            //Inserting the contentValues to contentResolver and getting the Uri
            val imageUri: Uri? =
                resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)

            //Opening an outputstream with the Uri that we got
            fos = imageUri?.let { resolver.openOutputStream(it) }
        }
    } else {
        //These for devices running on android < Q
        //So I don't think an explanation is needed here
        val imagesDir =
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
        val image = File(imagesDir, filename)
        fos = FileOutputStream(image)
    }

    fos?.use {
        //Finally writing the bitmap to the output stream that we opened
        bitmap?.compress(Bitmap.CompressFormat.PNG, 100, it)
        showToast("Saved to Photos")
    }
}

fun pdfToBitmap(pdfFile: File): Bitmap? {
    val listBitmap = mutableListOf<Bitmap>()
    var combineBitmap: Bitmap? = null
    try {
        val renderer =
            PdfRenderer(ParcelFileDescriptor.open(pdfFile, ParcelFileDescriptor.MODE_READ_ONLY))
        val pageCount = renderer.pageCount
        var totalHeight = 0
        var totalWidth = 0
        for (i in 0 until pageCount) {
            val page = renderer.openPage(i)

            val aspectRatio: Float = page.width / 575f //ZQ320 max width 72mm 203dpi = 575 pixel [https://pixelsconverter.com/millimeters-to-pixels]
            val multiplier = 1 / aspectRatio
            val width = (page.width * multiplier).toInt()
            val height = (page.height * multiplier).toInt() + 20

            totalHeight += height
            totalWidth = if (totalWidth < width) width else totalWidth
            val bitmap = Bitmap.createBitmap(
                width,
                height,
                Bitmap.Config.ARGB_8888
            )
            val canvas = Canvas(bitmap)
            val paint = Paint()
            /* Set grayscale color */
            val colorMatrix = ColorMatrix().also {
                it.setSaturation(0f)
            }
            val colorMatrixFilter = ColorMatrixColorFilter(colorMatrix)
            paint.colorFilter = colorMatrixFilter

            /* Add background white color */
            canvas.drawColor(Color.WHITE)
            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT)
            canvas.drawBitmap(bitmap, 0f, 0f, paint)

            /* Convert color to black and white */
            val bmOut = Bitmap.createBitmap(
                width,
                height,
                Bitmap.Config.ARGB_8888
            )
            val factor = 255f
            val redBri = 0.2126f
            val greenBri = 0.2126f
            val blueBri = 0.0722f

            val length = width * height
            val inpixels = IntArray(length)
            val oupixels = IntArray(length)

            bitmap.getPixels(inpixels, 0, width, 0, 0, width, height)

            var point = 0
            for (pix in inpixels) {
                val R = pix shr 16 and 0xFF
                val G = pix shr 8 and 0xFF
                val B = pix and 0xFF
                val lum = redBri * R / factor + greenBri * G / factor + blueBri * B / factor
                if (lum > 0.4) {
                    oupixels[point] = (0xFFFFFFFF).toInt()
                } else {
                    oupixels[point] = (0xFF000000).toInt()
                }
                point++
            }
            bmOut.setPixels(oupixels, 0, width, 0, 0, width, height)

            listBitmap.add(bmOut)
            // close the page
            page.close()
        }
        combineBitmap = Bitmap.createBitmap(totalWidth, totalHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(combineBitmap)
        var combineHeight = 0f
        listBitmap.forEachIndexed { index, bitmap ->
            canvas.drawBitmap(bitmap, 0f, combineHeight, null)
            combineHeight += bitmap.height.toFloat()
        }

        // close the renderer
        renderer.close()
    } catch (ex: java.lang.Exception) {
        ex.printStackTrace()
    }
    return combineBitmap
}

fun Context.isBluetoothEnable(): Boolean {
    val bluetoothManager: BluetoothManager = getSystemService(BluetoothManager::class.java)
    val bluetoothAdapter: BluetoothAdapter? = bluetoothManager.adapter
    if (bluetoothAdapter == null) {
        showToast("This device not support bluetooth")
        return false
    } else {
        return bluetoothAdapter.isEnabled
    }
}

fun Context.getFileFromUri(contentUri: Uri?): File? {
    // Get Input Stream && Init File
    var pdfFile: File? = null
    try {
        contentUri?.let {
            val inputStream: InputStream? = this.contentResolver.openInputStream(it)
            if (inputStream != null) {
                try {
                    pdfFile = File.createTempFile(
                        "TempFilePdf",
                        ".png",
                        this.cacheDir
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
    } catch (e: Exception) {
        showToast(e.message.toString())
    }
    return pdfFile
}

fun Context.isPrinterSupportsPDF(connection: Connection): Boolean {
    return try {
        //Enable emulation pdf
        SGD.SET("apl.enable", "pdf", connection)
        // Use SGD command to check if apl.enable returns "pdf"
        val printerInfo: String = SGD.GET("apl.enable", connection)
        Log.wtf("PDF ENABLE", printerInfo)
        printerInfo == "pdf"
    } catch (e: ConnectionException) {
        e.printStackTrace()
        showToast(e.message.toString())
        false
    }
}

fun saveBitmapToFile(bitmap: Bitmap?): File? {
    return try {
        val root =
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM).absolutePath + "/Printer"
        Log.wtf("ROOT", root)
        val myDir = File(root)
        myDir.mkdirs()
        val fname = "Image-bitmap.jpg"
        val file = File(myDir, fname)
        if (file.exists()) file.delete()
        file.createNewFile()
//            val file = File.createTempFile("ImageBitmapFile", ".jpg", myDir)
        // Convert bitmap to byte array
        val baos = ByteArrayOutputStream()

        //write the bytes in file
        FileOutputStream(file).use { output ->
            bitmap?.compress(
                Bitmap.CompressFormat.JPEG,
                100,
                baos
            ) // It can be also saved it as JPEG
            val bitmapdata = baos.toByteArray()
            output.write(bitmapdata)
            output.flush()
        }

        file
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }

}