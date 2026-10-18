package com.familywardrobe.app

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import java.time.LocalDate
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

@Composable
fun uiLocale(): Locale = LocalConfiguration.current.locales[0] ?: Locale.getDefault()
@Composable
fun dateLabel(iso: String): String {
    val locale = uiLocale()
    return runCatching { LocalDate.parse(iso).format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale)) }.getOrDefault(iso)
}
@Composable
fun timestampLabel(millis: Long): String = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT).withLocale(uiLocale())
    .format(Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()))
