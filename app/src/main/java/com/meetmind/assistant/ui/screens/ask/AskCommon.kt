package com.meetmind.assistant.ui.screens.ask

import android.text.format.DateUtils
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.meetmind.assistant.domain.model.RecordingMode
import com.meetmind.assistant.domain.usecase.ask.AskContextBuilder
import com.meetmind.assistant.ui.R
import com.meetmind.assistant.ui.icons.AppIcons
import com.meetmind.assistant.ui.ui.theme.GradientTop
import com.meetmind.assistant.ui.ui.theme.semanticColors
import java.text.DateFormat
import java.util.Calendar
import java.util.Date

/** Brand-gradient top bar shared by the Ask MeetMind screens (same style as Search). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AskTopBar(
    title: String,
    onNavigateBack: () -> Unit,
    subtitle: (@Composable () -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {}
) {
    val onGradient = MaterialTheme.semanticColors.onGradient
    Box(modifier = Modifier.background(GradientTop)) {
        TopAppBar(
            title = {
                Column {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleLarge,
                        color = onGradient,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    subtitle?.invoke()
                }
            },
            navigationIcon = {
                IconButton(onClick = onNavigateBack) {
                    Icon(
                        imageVector = AppIcons.Back,
                        contentDescription = stringResource(R.string.back),
                        tint = onGradient
                    )
                }
            },
            actions = actions,
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = Color.Transparent,
                titleContentColor = onGradient,
                navigationIconContentColor = onGradient,
                actionIconContentColor = onGradient
            )
        )
    }
}

/** Sparkle badge marking MeetMind's own content. */
@Composable
internal fun AskAssistantBadge(size: Dp = 28.dp) {
    Box(
        modifier = Modifier
            .size(size)
            .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(size / 3)),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = AppIcons.AutoAwesome,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.size(size * 0.6f)
        )
    }
}

/** `1h 18m`, `32m`; matches the compact durations used on the Sessions list. */
internal fun formatAskDuration(ms: Long): String {
    val totalMinutes = ms.coerceAtLeast(0) / 60_000
    return when {
        totalMinutes < 1 -> "<1m"
        totalMinutes < 60 -> "${totalMinutes}m"
        else -> "${totalMinutes / 60}h ${totalMinutes % 60}m"
    }
}

internal fun formatAskOffset(ms: Long): String = AskContextBuilder.formatOffset(ms)

/** A meeting's name, or "Meeting on <date>" for one the user never named. */
@Composable
internal fun askMeetingTitle(name: String?, createdAt: Long?): String {
    val date = remember(createdAt) {
        createdAt?.let { DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(it)) }
    }
    return name?.takeIf { it.isNotBlank() }
        ?: date?.let { stringResource(R.string.ask_untitled_meeting, it) }
        ?: stringResource(R.string.ask_meeting_generic)
}

/** "Today, 09:30" / "Yesterday" / "Oct 12" in the device locale. */
internal fun askRelativeDate(timestamp: Long, now: Long = System.currentTimeMillis()): String =
    DateUtils.getRelativeTimeSpanString(
        timestamp,
        now,
        DateUtils.DAY_IN_MILLIS,
        DateUtils.FORMAT_ABBREV_MONTH
    ).toString()

/** True when [timestamp] falls in the current calendar week (locale's first weekday). */
internal fun isInCurrentWeek(timestamp: Long, now: Long = System.currentTimeMillis()): Boolean {
    val start = Calendar.getInstance().apply {
        timeInMillis = now
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
        set(Calendar.DAY_OF_WEEK, firstDayOfWeek)
        if (timeInMillis > now) add(Calendar.WEEK_OF_YEAR, -1)
    }
    return timestamp >= start.timeInMillis
}

@Composable
internal fun askModeLabel(mode: RecordingMode): String = when (mode) {
    RecordingMode.SIMPLE_LISTENING -> stringResource(R.string.mode_simple_listening)
    RecordingMode.SHORT_MEETING -> stringResource(R.string.mode_short_meeting)
    RecordingMode.LONG_MEETING -> stringResource(R.string.mode_long_meeting)
    RecordingMode.REAL_TIME_TRANSLATION -> stringResource(R.string.mode_translation_live)
    RecordingMode.INTERVIEW -> stringResource(R.string.mode_interview)
    RecordingMode.ENGLISH_COACH -> stringResource(R.string.mode_english_coach)
}

private val CITATION = Regex("\\[(\\d+)]")
private val BOLD = Regex("\\*\\*(.+?)\\*\\*")
private val BULLET = Regex("^\\s*[-*•]\\s+")

/**
 * An answer as styled text: `[n]` markers become tappable citation chips calling
 * [onCitation], `**bold**` is bolded and `-`/`*` list items get bullets. Small
 * models emit a little Markdown even when asked not to.
 */
@Composable
internal fun rememberAnswerText(text: String, onCitation: (Int) -> Unit): AnnotatedString {
    val currentOnCitation by rememberUpdatedState(onCitation)
    val chip = SpanStyle(
        background = MaterialTheme.colorScheme.primaryContainer,
        color = MaterialTheme.colorScheme.onPrimaryContainer,
        fontWeight = FontWeight.SemiBold,
        fontSize = 12.sp
    )
    return remember(text, chip) {
        buildAnnotatedString {
            text.lines().forEachIndexed { lineIndex, rawLine ->
                if (lineIndex > 0) append('\n')
                val line = if (BULLET.containsMatchIn(rawLine)) "•  " + rawLine.replaceFirst(BULLET, "") else rawLine
                var cursor = 0
                BOLD.findAll(line).forEach { match ->
                    appendCitations(line.substring(cursor, match.range.first), chip) { currentOnCitation(it) }
                    withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) {
                        appendCitations(match.groupValues[1], chip) { currentOnCitation(it) }
                    }
                    cursor = match.range.last + 1
                }
                appendCitations(line.substring(cursor), chip) { currentOnCitation(it) }
            }
        }
    }
}

private fun AnnotatedString.Builder.appendCitations(text: String, chip: SpanStyle, onCitation: (Int) -> Unit) {
    var cursor = 0
    CITATION.findAll(text).forEach { match ->
        append(text.substring(cursor, match.range.first))
        val number = match.groupValues[1].toInt()
        withLink(
            LinkAnnotation.Clickable(
                tag = "citation-$number",
                styles = TextLinkStyles(style = chip),
                linkInteractionListener = { onCitation(number) }
            )
        ) {
            // Thin spaces pad the chip background around the number.
            append(" $number ")
        }
        cursor = match.range.last + 1
    }
    append(text.substring(cursor))
}

/** Answer text for copying: markers and Markdown emphasis removed. */
internal fun plainAnswerText(text: String): String =
    text.replace(Regex("\\s*\\[\\d+]"), "").replace(BOLD, "$1")

/** Compact one-line row: icon-sized slot plus label, used for small status lines. */
@Composable
internal fun AskInlineNote(text: String, color: Color, modifier: Modifier = Modifier, icon: @Composable () -> Unit) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        icon()
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = color,
            modifier = Modifier.padding(start = 8.dp)
        )
    }
}
