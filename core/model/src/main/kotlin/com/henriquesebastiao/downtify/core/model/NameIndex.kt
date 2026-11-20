package com.henriquesebastiao.downtify.core.model

import java.text.Normalizer
import java.util.Locale

/** A run of names under one initial: what the TV's artist grid heads with a letter. */
data class NameSection<T>(val initial: String, val items: List<T>)

/**
 * Names in alphabetical order, sectioned by their initial, as a phone's contacts are: accents
 * don't count ("Ávila" sits with the A's, not after Z), case doesn't either, nor punctuation
 * before the first letter or digit ("(What's the Story)" with the W's, "...É Isso" with the E's),
 * and a name that starts with a digit or has neither goes under [OTHER], first from A to Z and
 * last from Z to A.
 * Letters of other alphabets (Cyrillic, Greek) are initials of their own, after Z.
 */
object NameIndex {
    const val OTHER = "#"

    private val marks = Regex("\\p{Mn}+")

    /** What names are compared by: no accents, lower case, nothing before the first letter or digit. */
    fun sortKey(name: String): String = Normalizer.normalize(name, Normalizer.Form.NFD)
        .replace(marks, "")
        .lowercase(Locale.ROOT)
        .trimStart { !it.isLetterOrDigit() }

    /** The section [name] goes under: its first letter, upper case and without accents, or [OTHER]. */
    fun initial(name: String): String {
        val first = sortKey(name).firstOrNull() ?: return OTHER
        return if (first.isLetter()) first.uppercase(Locale.ROOT) else OTHER
    }

    /** [items] sorted by [name] ([descending] for Z to A) and cut into sections by initial. */
    fun <T> sections(items: List<T>, descending: Boolean = false, name: (T) -> String): List<NameSection<T>> {
        // Each name is normalized once, not at every comparison: a library has thousands of songs.
        val keyed = items.map { item ->
            val raw = name(item)
            val key = sortKey(raw)
            val first = key.firstOrNull()
            Keyed(item, raw, key, if (first != null && first.isLetter()) first.uppercase(Locale.ROOT) else OTHER)
        }
        val order = compareBy<Keyed<T>> { if (it.initial == OTHER) 0 else 1 }
            .thenBy { it.key }
            .thenBy { it.raw }
        val sorted = keyed.sortedWith(if (descending) order.reversed() else order)
        val sections = mutableListOf<NameSection<T>>()
        var run = mutableListOf<T>()
        sorted.forEachIndexed { index, entry ->
            run += entry.item
            if (index == sorted.lastIndex || sorted[index + 1].initial != entry.initial) {
                sections += NameSection(entry.initial, run)
                run = mutableListOf()
            }
        }
        return sections
    }

    private class Keyed<T>(val item: T, val raw: String, val key: String, val initial: String)
}
