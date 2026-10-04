package com.aivo.aivosdk

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform