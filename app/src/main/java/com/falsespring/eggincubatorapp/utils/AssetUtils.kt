package com.falsespring.eggincubatorapp.utils

import android.content.Context
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader

fun readEsp32MacPrefixes(context: Context, fileName: String = "esp32_mac_oui.csv"): List<String> {
    val prefixes = mutableListOf<String>()
    try {
        context.assets.open(fileName).use { inputStream ->
            InputStreamReader(inputStream).use { reader ->
                BufferedReader(reader).useLines { lines ->
                    lines.forEach { line ->
                        val macCandidate = line.trim().uppercase()
                            .replace(".", "")
                            .replace(":", "")
                            .replace("-", "")

                        if (macCandidate.length >= 6 && macCandidate.all { "0123456789ABCDEF".contains(it) }) {
                            prefixes.add(macCandidate.substring(0, 6))
                        } else {
                            // NONE FOR NOW
                        }
                    }
                }
            }
        }
    } catch (e: IOException) {
        e.printStackTrace()
    }
    return prefixes.distinct()
}