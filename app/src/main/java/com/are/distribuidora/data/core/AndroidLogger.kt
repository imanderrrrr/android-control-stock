package com.are.distribuidora.data.core

import com.are.distribuidora.domain.core.Logger
import javax.inject.Inject

class AndroidLogger @Inject constructor() : Logger {
    override fun d(tag: String, message: String) {
        android.util.Log.d(tag, message)
    }

    override fun e(tag: String, message: String, throwable: Throwable?) {
        android.util.Log.e(tag, message, throwable)
    }
}
