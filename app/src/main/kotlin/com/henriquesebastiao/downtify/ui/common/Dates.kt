package com.henriquesebastiao.downtify.ui.common

import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/** `Sep 18, 2026` in the phone's language, from an ISO 8601 time; null when it isn't one. */
fun formatDate(iso: String, locale: Locale = Locale.getDefault()): String? = runCatching {
    OffsetDateTime.parse(iso).toLocalDate()
        .format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale))
}.getOrNull()

/** `Sep 18, 2026` in the phone's language and time zone, from epoch milliseconds. */
fun formatDate(epochMillis: Long, locale: Locale = Locale.getDefault()): String =
    Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()).toLocalDate()
        .format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale))
