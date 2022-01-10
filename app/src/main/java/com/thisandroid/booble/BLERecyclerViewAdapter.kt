package com.thisandroid.booble

import android.bluetooth.BluetoothClass
import android.bluetooth.BluetoothDevice
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.thisandroid.booble.databinding.RecyclerItemDeviceBinding

class BLERecyclerViewAdapter : RecyclerView.Adapter<BLERecyclerViewAdapter.ViewHolder>() {
    var listDevice = mutableListOf<BluetoothDevice>()

    class ViewHolder(val binding: RecyclerItemDeviceBinding): RecyclerView.ViewHolder(binding.root) {
        fun setDeviceInfo(device : BluetoothDevice){
            binding.textDevice.text = device.getName()
            binding.textAddress.text = device.address
        }
    }
    fun addDevice(device: BluetoothDevice){
        if(!listDevice.contains(device)) listDevice.add(device)
    }
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = RecyclerItemDeviceBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.setDeviceInfo(listDevice.get(position))
    }

    override fun getItemCount(): Int {
        return listDevice.size
    }
}