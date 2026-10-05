package com.checkmind.app

import android.app.Application
import com.checkmind.app.data.BookRepository

class CheckMindApp : Application() {
    lateinit var bookRepository: BookRepository
        private set

    override fun onCreate() {
        super.onCreate()
        bookRepository = BookRepository(this).also { it.start() }
    }
}
