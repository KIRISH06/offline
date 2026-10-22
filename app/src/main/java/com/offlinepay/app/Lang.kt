package com.offlinepay.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

object Lang {
    var hindi by mutableStateOf(false)
}

fun tr(en: String, hi: String): String = if (Lang.hindi) hi else en
