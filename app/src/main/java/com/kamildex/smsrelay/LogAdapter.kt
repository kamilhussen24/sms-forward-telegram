package com.kamildex.smsrelay

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.kamildex.smsrelay.databinding.ItemLogBinding

class LogAdapter(private var items: List<SmsEntry>) :
    RecyclerView.Adapter<LogAdapter.ViewHolder>() {

    inner class ViewHolder(val binding: ItemLogBinding) :
        RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemLogBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        holder.binding.apply {
            tvSender.text = item.sender
            tvMessage.text = item.message
            tvTime.text = "${item.time} · ${item.date}"
            statusDot.setBackgroundResource(
                if (item.forwarded) R.drawable.dot_green else R.drawable.dot_red
            )
        }
    }

    override fun getItemCount() = items.size

    fun update(newItems: List<SmsEntry>) {
        items = newItems
        notifyDataSetChanged()
    }
}
