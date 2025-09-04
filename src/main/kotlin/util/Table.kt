package com.magnariuk.util

import kotlin.collections.get
import kotlin.collections.iterator

class Table(
    private val title: String,
    private val columns: List<Pair<String, Int>> = emptyList(),
    private val width: Int = 80
) {
    private val totalWidth: Int = calculateTotalWidth()

    private fun calculateTotalWidth(): Int {
        if (columns.isEmpty()) return 0
        val totalColumnsWidth = columns.sumOf { it.second }
        val numSpaces = columns.size - 1
        return totalColumnsWidth + numSpaces
    }

    fun printHeader() {
        println("\n" + title.center(totalWidth) + "\n")

        val headerParts = mutableListOf<String>()
        val separatorParts = mutableListOf<String>()

        for ((name, colWidth) in columns) {
            headerParts.add(name.padEnd(colWidth))
            separatorParts.add("-".repeat(colWidth).padEnd(colWidth))
        }

        println(headerParts.joinToString(" "))
        println(separatorParts.joinToString(" "))
    }

    fun printRow(rowValues: List<Any?>) {
        if (rowValues.size != columns.size) {
            println("Error: Row values count (${rowValues.size}) does not match column count (${columns.size}).")
            return
        }

        val contentParts = rowValues.mapIndexed { i, value ->
            value.toString().padEnd(columns[i].second)
        }
        println(contentParts.joinToString(" "))
    }

    fun printRowWrapped(rowValues: List<Any?>) {
        if (rowValues.size != columns.size) {
            println("Error: Row values count (${rowValues.size}) does not match column count (${columns.size}).")
            return
        }

        val wrappedCells = rowValues.mapIndexed { i, value ->
            wrapText(value.toString(), columns[i].second)
        }

        val maxLines = wrappedCells.maxOf { it.size }

        for (lineIdx in 0 until maxLines) {
            val lineParts = wrappedCells.mapIndexed { colIdx, cellLines ->
                if (lineIdx < cellLines.size) {
                    cellLines[lineIdx].padEnd(columns[colIdx].second)
                } else {
                    " ".repeat(columns[colIdx].second)
                }
            }
            println(lineParts.joinToString(" "))
        }
    }

    fun printClosing() {
        val separatorParts = columns.map { "-".repeat(it.second).padEnd(it.second) }
        println(separatorParts.joinToString(" "))
    }

    fun printVertical(data: Map<String, Any?>, wrapKeys: Int = 20) {
        println("\n" + title.center(width))
        println("-".repeat(width))

        for ((key, rawValue) in data) {
            val value = when (rawValue) {
                is Map<*, *> -> rawValue["username"]?.toString() ?: "N/A"
                is List<*> -> rawValue.joinToString(", ")
                else -> rawValue?.toString() ?: "N/A"
            }

            val wrapped = wrapText(value, width - wrapKeys - 2)
            println(key.padEnd(wrapKeys) + " " + wrapped.first())
            for (line in wrapped.drop(1)) {
                println(" ".repeat(wrapKeys) + " " + line)
            }
        }

        println("-".repeat(width))
    }

    private fun wrapText(text: String, lineWidth: Int): List<String> {
        if (text.isEmpty()) return listOf("")
        val words = text.split(" ")
        val lines = mutableListOf<String>()
        var currentLine = StringBuilder()

        for (word in words) {
            if (currentLine.isEmpty()) {
                currentLine.append(word)
            } else if (currentLine.length + 1 + word.length <= lineWidth) {
                currentLine.append(" ").append(word)
            } else {
                lines.add(currentLine.toString())
                currentLine = StringBuilder(word)
            }
        }
        if (currentLine.isNotEmpty()) lines.add(currentLine.toString())
        return lines
    }

    private fun String.center(width: Int): String {
        if (this.length >= width) return this
        val padding = width - this.length
        val left = padding / 2
        val right = padding - left
        return " ".repeat(left) + this + " ".repeat(right)
    }
}