package com.chengjiguanjia.spotmark.widget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast
import com.chengjiguanjia.spotmark.R

class WidgetPinResultReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Toast.makeText(context, R.string.widget_pin_added, Toast.LENGTH_SHORT).show()
    }
}
