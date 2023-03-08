package id.astra.zebraprint

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import id.astra.zebraprint.databinding.LoadingDialogBinding
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch


var loadingDialog: AlertDialog? = null

fun Context.showLoading(isCancelable:Boolean = false){
    val binding = LoadingDialogBinding.inflate(LayoutInflater.from(this), null, false)
    loadingDialog = AlertDialog.Builder(this, R.style.DialogRounded)
        .setCancelable(isCancelable)
        .setView(binding.root)
        .create()

    loadingDialog?.let { alert->
        if (!alert.isShowing){
            Handler(Looper.getMainLooper()).post {
                alert.show()
                WindowManager.LayoutParams().also {
                    it.height = resources.getDimensionPixelSize(R.dimen.dimen_60dp) + resources.getDimensionPixelSize(R.dimen.dimen_16dp)
                    it.width = resources.getDimensionPixelSize(R.dimen.dimen_60dp) + resources.getDimensionPixelSize(R.dimen.dimen_16dp)
                    alert.window?.apply {
                        attributes = it
                        setGravity(Gravity.CENTER)
                        setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
                    }
                }
            }
        }
    }
}

fun hideLoading(){
    loadingDialog?.let {
        if (it.isShowing){
            it.dismiss()
        }
    }
}

fun Context.showToast(message:String){
    MainScope().launch {
        Toast.makeText(this@showToast, message, Toast.LENGTH_SHORT).show()
    }
}