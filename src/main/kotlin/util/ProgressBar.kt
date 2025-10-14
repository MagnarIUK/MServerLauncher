package com.magnariuk.util

class ProgressBar(
    private val label: String = "",
    private val barLength: Int = 30,
    private val useMessages: Boolean = false,
    private val maxMessageLines: Int = 4
) {
    private var current = 0
    private var total = 0
    private var started = false
    private var messages = mutableListOf<String>()
    val cSyms = listOf("c", "C")

    fun start(total: Int) {
        this.total = total
        current = 0
        started = true
        if(useMessages) {
            messages.clear()
            repeat(maxMessageLines+1) { println()}
        }
        update()
    }

    fun step(step: Int = 1, message: String? = null) {
        if (!started) return
        current += step
        if(useMessages) {
            if(message != null) {
                messages.add(message)
                if (messages.size > maxMessageLines) {
                    messages.removeFirst()
                }
            }
        }

        update()
    }

    fun update() {
        if (!started) return
        if(useMessages) {
            print("\u001B[${messages.size}A")
            messages.takeLast(maxMessageLines).forEach {
                print("\r${it}")
                print("\u001B[1B")
            }
        }

        val progress = ((current.toDouble() / total) * 100).toInt().coerceIn(0..100)
        val filledLength = (progress * barLength) / 100
        val bar = "=".repeat(maxOf(0, filledLength - 1)) + cSyms.random() + "-".repeat(barLength - filledLength)

        val barString = "${if (label.isNotEmpty()) "$label: " else ""}[$bar] $progress% ($current/$total)"
        print("\r$barString")

        if (current >= total) {
            println()
        }

    }


}