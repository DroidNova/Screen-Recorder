package com.droidnova.screenrecorder.extension

import android.content.Context
import android.widget.Toast

fun Context?.showToast(message: String, duration: Int = Toast.LENGTH_SHORT) {
    this?.let {
        Toast.makeText(it, message, duration).show()
    }
}
