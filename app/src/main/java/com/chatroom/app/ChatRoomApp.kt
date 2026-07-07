package com.chatroom.app

import android.app.Application
import com.chatroom.app.util.AppLogger

class ChatRoomApp : Application() {
    override fun onCreate() {
        super.onCreate()
        instance = this
        AppLogger.init(this)
    }

    companion object {
        lateinit var instance: ChatRoomApp
            private set
    }
}
