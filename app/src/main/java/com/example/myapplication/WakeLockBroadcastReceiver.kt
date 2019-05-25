package com.example.myapplication

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

open class WakeLockBroadcastReceiver(private val listener: WakeLockListener) : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action == Intent.ACTION_SCREEN_ON) {
            this.listener.onScreenOn()
        } else if (action == Intent.ACTION_SCREEN_OFF) {
            this.listener.onScreenOff()
        }
    }
}