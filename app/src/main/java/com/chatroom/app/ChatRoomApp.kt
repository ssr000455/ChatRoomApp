package com.chatroom.app

import android.app.Application

class ChatRoomApp : Application() {
    override fun onCreate() {
        super.onCreate()
        instance = this
    }

    companion object {
        lateinit var instance: ChatRoomApp
            private set
    }
}
