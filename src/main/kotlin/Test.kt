package com.magnariuk

import com.github.ajalt.clikt.core.main
import com.magnariuk.util.I18n
import com.magnariuk.util.configs.readConfig


object Test{
    fun main() {
        val ms = MS()
        I18n.setLocale(readConfig().lang)
        while (true){
            val input = readln()
            if (input == "q") { break }
            try {
                ms.main(input.split(" "))
            } catch(e: Exception){
                println(e.message)
            }

        }
    }
}

fun main() = Test.main()