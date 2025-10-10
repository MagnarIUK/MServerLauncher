package com.magnariuk.util


fun prompt(message: String, timeoutMessage: String? = null, timeoutMs: Long=5000, default: String= "y"): String {
    print(message)
    var input: String? = null
    val thread = Thread{
        input = readlnOrNull()
    }

    thread.start()
    if(timeoutMs>0) thread.join(timeoutMs)
    println()

    val trimmed = input?.trim()

    return when {
        trimmed.isNullOrBlank() -> {
            if(input == null && timeoutMessage != null) {
                println(timeoutMessage)
            }
            default.lowercase()
        }
        else -> trimmed.lowercase()
    }
}