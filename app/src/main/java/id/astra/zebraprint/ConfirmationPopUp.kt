package id.astra.zebraprint

import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.fragment.app.DialogFragment

/**
 * A simple [DialogFragment] subclass.
 * Use the [ConfirmationPopUp.newInstance] factory method to
 * create an instance of this fragment.
 */
class ConfirmationPopUp : DialogFragment() {
    private var retVal: Boolean = false
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }

    interface ConfirmationListener {
        fun onConfirmDialog(retVal: Boolean)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        // Inflate the layout for this fragment
        val view: View = inflater.inflate(R.layout.fragment_confirmation_pop_up, container, false)
        val activity: ConfirmationListener = activity as ConfirmationListener

        val btnYes = view.findViewById<Button>(R.id.btn_yes)
        val btnNo = view.findViewById<Button>(R.id.btn_no)

        view.findViewById<TextView>(R.id.popup_title).text = arguments?.getString("title")
        view.findViewById<TextView>(R.id.popup_text).text = arguments?.getString("text")

        btnYes.setOnClickListener{
            Log.wtf("conyol:", retVal.toString())
            activity.onConfirmDialog(true)
            dismiss()
        }

        btnNo.setOnClickListener{
            Log.wtf("conyol:", retVal.toString())
            activity.onConfirmDialog(false)
            dismiss()
        }
        return view
    }

    override fun onStart() {
        super.onStart()

        dialog!!.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        dialog!!.window?.setGravity(Gravity.BOTTOM)
    }

    companion object {
        @JvmStatic
        fun newInstance(popup_title: String, popup_text: String) =
            ConfirmationPopUp().apply {
                arguments = Bundle().apply {
                    putString("title", popup_title)
                    putString("text", popup_text)
                }
            }
    }
}