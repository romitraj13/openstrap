package org.openstrap.app.data

/**
 * `String.format` for the format strings the data code uses, because common Kotlin has none.
 *
 * Port of the two call shapes the Android code relies on: `%.<n>f` and `%d`/`%s` (with the `,`
 * grouping and `0<n>` zero padding flags). Output is locale-free — ASCII digits and `.` for the
 * decimal separator — which is what the Android code asked for by passing
 * `java.util.Locale.ROOT`.
 *
 * Rounding follows `java.util.Formatter` for `%f`: half away from zero on the exact value.
 */
fun String.format(vararg args: Any?): String {
    val out = StringBuilder(length + 16)
    var i = 0
    var next = 0
    while (i < length) {
        val c = this[i]
        if (c != '%') {
            out.append(c)
            i++
            continue
        }
        if (i + 1 < length && this[i + 1] == '%') {
            out.append('%')
            i += 2
            continue
        }
        var j = i + 1
        var zeroPad = false
        var group = false
        var plus = false
        while (j < length && (this[j] == '0' || this[j] == ',' || this[j] == '-' || this[j] == '+')) {
            when (this[j]) {
                '0' -> zeroPad = true
                ',' -> group = true
                '+' -> plus = true
            }
            j++
        }
        var width = 0
        while (j < length && this[j].isDigit()) {
            width = width * 10 + (this[j] - '0')
            j++
        }
        var precision = -1
        if (j < length && this[j] == '.') {
            j++
            precision = 0
            while (j < length && this[j].isDigit()) {
                precision = precision * 10 + (this[j] - '0')
                j++
            }
        }
        if (j >= length) {
            out.append(substring(i))
            break
        }
        val conversion = this[j]
        val arg = args.getOrNull(next++)
        val text = when (conversion) {
            'd' -> (arg as? Number)?.toLong()?.toString()?.let { if (group) it.grouped() else it } ?: "null"
            'f' -> fixed((arg as? Number)?.toDouble() ?: Double.NaN, if (precision < 0) 6 else precision)
            's' -> arg?.toString() ?: "null"
            else -> arg?.toString() ?: "null"
        }
        // `+`: Java puts the sign on a non-negative number too, and a negative one keeps its own.
        val signed = if (plus && (conversion == 'f' || conversion == 'd') && !text.startsWith('-')) "+$text" else text
        if (signed.length < width) {
            repeat(width - signed.length) { out.append(if (zeroPad) '0' else ' ') }
        }
        out.append(signed)
        i = j + 1
    }
    return out.toString()
}

/** `%.<decimals>f` of [value], half away from zero, as Java prints it. */
private fun fixed(value: Double, decimals: Int): String {
    if (value.isNaN() || value.isInfinite()) return value.toString()
    var scale = 1L
    repeat(decimals) { scale *= 10 }
    val negative = value < 0
    val scaled = kotlin.math.floor(kotlin.math.abs(value) * scale + 0.5).toLong()
    val whole = scaled / scale
    val fraction = scaled % scale
    val text = StringBuilder()
    if (negative) text.append('-')
    text.append(whole)
    if (decimals > 0) {
        text.append('.')
        val digits = fraction.toString()
        repeat(decimals - digits.length) { text.append('0') }
        text.append(digits)
    }
    return text.toString()
}

/** `%,d`: commas every three digits, as `Locale.ROOT` groups them. */
private fun String.grouped(): String {
    val negative = startsWith('-')
    val digits = if (negative) substring(1) else this
    val out = StringBuilder(digits.length + digits.length / 3)
    digits.forEachIndexed { index, c ->
        if (index > 0 && (digits.length - index) % 3 == 0) out.append(',')
        out.append(c)
    }
    return if (negative) "-$out" else out.toString()
}
