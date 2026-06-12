package com.kamildex.smsrelay

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.kamildex.smsrelay.databinding.ItemLogBinding

class LogAdapter(private var items: List<SmsEntry>) :
    RecyclerView.Adapter<LogAdapter.VH>() {

    inner class VH(val b: ItemLogBinding) : RecyclerView.ViewHolder(b.root)

    override fun onCreateViewHolder(p: ViewGroup, t: Int) =
        VH(ItemLogBinding.inflate(LayoutInflater.from(p.context), p, false))

    override fun onBindViewHolder(h: VH, i: Int) {
        items[i].also { e ->
            h.b.tvSender.text = e.sender
            h.b.tvMessage.text = e.message
            h.b.tvTime.text = "${e.time}  ${e.date}"
            h.b.statusDot.setBackgroundResource(
                if (e.forwarded) R.drawable.dot_green else R.drawable.dot_red
            )
        }
    }

    override fun getItemCount() = items.size

    fun update(new: List<SmsEntry>) {
        items = new
        notifyDataSetChanged()
    }
}
