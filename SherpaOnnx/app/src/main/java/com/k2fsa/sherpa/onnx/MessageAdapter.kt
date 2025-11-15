package com.k2fsa.sherpa.onnx

import android.content.Context
import android.text.TextUtils
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class MessageAdapter : RecyclerView.Adapter<MessageAdapter.MessageItemHolder> {

    private var layoutInflater: LayoutInflater
    private val items: ArrayList<String> = ArrayList()

    constructor(context: Context) {
        layoutInflater = LayoutInflater.from(context)
    }

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): MessageItemHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_message, parent, false)
        return MessageItemHolder(view)
    }

    override fun onBindViewHolder(
        holder: MessageItemHolder,
        position: Int
    ) {
        val item = items[position]
        holder.messageTextView.text = item
    }

    override fun getItemCount(): Int {
        return items.size
    }

    fun replaceLatestItem(latestMessage: String) {
        if(TextUtils.isEmpty(latestMessage)){
            return
        }
        Log.d("---abc", "extend new line $latestMessage")
        if (items.isNotEmpty()) {
            val index = items.size - 1
            items[index] = latestMessage
            notifyItemChanged(index)
        } else {
            addNewItem(latestMessage)
        }
    }

    fun addNewItem(message: String) {
        if(TextUtils.isEmpty(message)){
            return
        }
        Log.d("---abc", "add new line $message")
        items.add(message)
        notifyDataSetChanged()
    }

    class MessageItemHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val messageTextView: TextView = itemView.findViewById(R.id.text_message)
    }
}