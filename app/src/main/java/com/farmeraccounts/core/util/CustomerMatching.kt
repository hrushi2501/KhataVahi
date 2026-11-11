package com.farmeraccounts.core.util

import com.farmeraccounts.domain.model.Customer
import java.text.Normalizer
import java.util.Locale

/** Offline spelling suggestions. Names and identifiers are never changed. */
object CustomerMatching {
    fun normalize(value: String): String = Normalizer.normalize(value, Normalizer.Form.NFKC)
        .lowercase(Locale.ROOT).map { c ->
            when (c) {
                in '૦'..'૯' -> '0' + (c - '૦')
                in '०'..'९' -> '0' + (c - '०')
                else -> c
            }
        }.joinToString("").replace(Regex("[^\\p{L}\\p{M}\\p{N}]+"), " ").trim()

    private fun phone(value: String?): String {
        val digits = normalize(value.orEmpty()).filter { it in '0'..'9' }
        return if (digits.length == 12 && digits.startsWith("91")) digits.takeLast(10) else digits
    }

    fun search(customers: List<Customer>, query: String): List<Customer> {
        val q = normalize(query).take(80)
        if (q.isBlank()) return customers
        val numeric = q.filter { !it.isWhitespace() }.all { it.isDigit() }
        return customers.asSequence().filterNot { it.isDeleted }.mapNotNull { c ->
            val fields = listOfNotNull(c.name, c.nickname, c.fatherName, c.village).map(::normalize)
            val score = if (numeric) {
                if (phone(c.phone).contains(phone(query))) 0 else null
            } else when {
                fields.any { it == q || it.replace(" ", "") == q.replace(" ", "") } -> 0
                fields.any { it.startsWith(q) } -> 1
                fields.any { it.contains(q) } -> 2
                else -> {
                    val words = fields.flatMap { it.split(' ') }.filter { it.isNotBlank() }
                    val scores = q.split(' ').filter { it.isNotBlank() }.map { token ->
                        words.minOfOrNull { word -> tokenScore(token, word) } ?: NO_MATCH
                    }
                    if (scores.isNotEmpty() && scores.all { it < NO_MATCH }) 3 + scores.sum() else null
                }
            }
            score?.let { Triple(c, it, normalize(c.name)) }
        }.sortedWith(compareBy<Triple<Customer, Int, String>> { it.second }.thenBy { it.third }.thenBy { it.first.id })
            .map { it.first }.toList()
    }

    fun duplicateCandidates(customers: List<Customer>, name: String, phone: String?, excludeId: String?): List<Customer> {
        val normalizedName = normalize(name)
        val normalizedPhone = phone(phone)
        return customers.filter { c ->
            !c.isDeleted && c.id != excludeId &&
                ((normalizedPhone.length >= 7 && normalizedPhone == phone(c.phone)) ||
                    normalizedName.isNotBlank() && similarName(normalizedName, normalize(c.name)))
        }.sortedBy { if (normalizedPhone.isNotBlank() && normalizedPhone == phone(it.phone)) 0 else 1 }
    }

    private fun similarName(a: String, b: String): Boolean {
        if (a == b || a.replace(" ", "") == b.replace(" ", "")) return true
        val aw = a.split(' '); val bw = b.split(' ')
        return aw.size == bw.size && aw.zip(bw).all { (x,y) -> tokenScore(x,y, allowPrefix = false) < NO_MATCH }
    }

    private fun tokenScore(a: String, b: String, allowPrefix: Boolean = true): Int {
        if (a == b || allowPrefix && b.startsWith(a)) return 0
        if (a.length < 3 || a.length > 64 || b.length > 64) return NO_MATCH
        val limit = if (a.length >= 6) 2 else 1
        if (kotlin.math.abs(a.length - b.length) > limit) return NO_MATCH
        val d = Array(a.length + 1) { IntArray(b.length + 1) }
        for (i in 0..a.length) d[i][0] = i
        for (j in 0..b.length) d[0][j] = j
        for (i in 1..a.length) for (j in 1..b.length) {
            d[i][j] = minOf(d[i-1][j]+1, d[i][j-1]+1, d[i-1][j-1]+if(a[i-1]==b[j-1]) 0 else 1)
            if (i>1 && j>1 && a[i-1]==b[j-2] && a[i-2]==b[j-1]) d[i][j] = minOf(d[i][j],d[i-2][j-2]+1)
        }
        return d[a.length][b.length].takeIf { it <= limit } ?: NO_MATCH
    }
    private const val NO_MATCH = 1000
}
