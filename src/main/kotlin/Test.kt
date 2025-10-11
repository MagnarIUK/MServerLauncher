package com.magnariuk

import com.github.ajalt.clikt.core.main
import com.magnariuk.util.I18n
import com.magnariuk.util.configs.readConfig
import com.magnariuk.util.t


object Test{
    fun main() {
        val ms = MS()
        I18n.loadAllLocales()
        I18n.setLocale(readConfig().lang)
        while (true){
            val input = readln()
            if (input == "q") { break }
            if (input.split(" ")[0] == "g") {
                println("Getting: '${input.split(" ")[1]}'")
                println("String: \n\t'${t(input.split(" ")[1])}'")
                continue
            }
            try {
                ms.main(input.split(" "))
            } catch(e: Exception){
                println(e.message)
            }

        }
    }
}

fun main() = Test.main()