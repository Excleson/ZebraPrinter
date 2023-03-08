package id.astra.zebraprint

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.zebra.sdk.printer.discovery.DiscoveredPrinter
import com.zebra.sdk.printer.discovery.DiscoveredPrinterBluetooth
import id.astra.zebraprint.databinding.ItemPrinterDeviceBinding

class AdapterPrinterDevice(var list:List<DiscoveredPrinterBluetooth>) : RecyclerView.Adapter<AdapterPrinterDevice.ViewHolder>() {
    lateinit var binding: ItemPrinterDeviceBinding
    private var selectedPrinter: ((DiscoveredPrinterBluetooth)->Unit)? = null

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AdapterPrinterDevice.ViewHolder {
        binding = ItemPrinterDeviceBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return AdapterPrinterDevice.ViewHolder(binding.root)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val itemPrinter = list[position]
        binding.tvName.text = itemPrinter.friendlyName
        binding.tvMacAddress.text = itemPrinter.address

        binding.root.setOnClickListener {
            selectedPrinter?.invoke(list[position])
        }
    }

    override fun getItemCount(): Int {
        return list.size
    }

    fun onPrinterSelected(listerner : (DiscoveredPrinterBluetooth)->Unit){
        this.selectedPrinter = listerner
    }

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView)
}