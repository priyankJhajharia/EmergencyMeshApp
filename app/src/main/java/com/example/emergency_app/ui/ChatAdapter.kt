package com.example.emergency_app.ui

import android.text.util.Linkify
import android.text.method.LinkMovementMethod
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.emergency_app.R
import com.example.emergency_app.data.MeshMessage
import com.example.emergency_app.data.MessageType
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ChatAdapter : RecyclerView.Adapter<ChatAdapter.MessageViewHolder>() {

    private val messages = mutableListOf<MeshMessage>()
    private val dateFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

    class MessageViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvSenderName: TextView = view.findViewById(R.id.tvSenderName)
        val tvContent: TextView = view.findViewById(R.id.tvMessageContent)
        val tvTimestamp: TextView = view.findViewById(R.id.tvTimestamp)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MessageViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_message, parent, false)
        return MessageViewHolder(view)
    }

    override fun onBindViewHolder(holder: MessageViewHolder, position: Int) {
        val message = messages[position]

        if (message.type == MessageType.SOS) {
            holder.tvContent.setBackgroundResource(android.R.color.holo_red_light)
            holder.tvContent.setTextColor(
                holder.itemView.context.getColor(android.R.color.white)
            )
        } else {
            holder.tvContent.setBackgroundResource(R.drawable.rounded_input)
            holder.tvContent.setTextColor(
                holder.itemView.context.getColor(android.R.color.black)
            )
        }

        holder.tvSenderName.text = if (message.isMine) "You" else message.senderName
        holder.tvContent.text = message.content
        holder.tvTimestamp.text = dateFormat.format(Date(message.timestamp))

        // make links clickable (Google Maps, URLs etc.)
        holder.tvContent.autoLinkMask = Linkify.WEB_URLS
        holder.tvContent.movementMethod = LinkMovementMethod.getInstance()
        Linkify.addLinks(holder.tvContent, Linkify.WEB_URLS)

        // align my messages to right, others to left
        val container = holder.itemView as LinearLayout
        if (message.isMine) {
            container.gravity = Gravity.END
        } else {
            container.gravity = Gravity.START
        }
    }

    override fun getItemCount() = messages.size

    fun updateMessages(newMessages: List<MeshMessage>) {
        messages.clear()
        messages.addAll(newMessages)
        notifyDataSetChanged()
    }
}