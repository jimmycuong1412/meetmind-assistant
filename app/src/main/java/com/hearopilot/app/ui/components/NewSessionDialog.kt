package com.hearopilot.app.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.hearopilot.app.ui.R
import com.hearopilot.app.domain.model.AppSettings
import com.hearopilot.app.domain.model.InsightStrategy
import com.hearopilot.app.domain.model.RecordingMode
import com.hearopilot.app.domain.model.SessionTemplate
import com.hearopilot.app.ui.icons.AppIcons
import com.hearopilot.app.ui.ui.theme.*

/**
 * Custom dialog for creating a new transcription session.
 *
 * Layout: gradient header with title + close icon, scrollable body with:
 * output language selector (first), topic/name field, mode cards,
 * insight strategy chips, and primary CTA.
 *
 * Output language defaults to the device locale if it is among the 25 supported
 * languages, or falls back to English. The user can always override it.
 * For REAL_TIME_TRANSLATION the language selector acts as the translation target
 * and is seeded from [AppSettings.translationTargetLanguage].
 *
 * @param onDismiss Called when user dismisses the dialog.
 * @param onConfirm Called when user confirms; receives (sessionName, mode, outputLanguage, insightStrategy, topic).
 *                  outputLanguage is always a non-null BCP-47 code (never empty).
 * @param settings Current app settings used to show the insight interval per mode and
 *                 to seed the default language selection for translation mode.
 */
@Composable
fun NewSessionDialog(
    onDismiss: () -> Unit,
    onConfirm: (String?, RecordingMode, String, String?, InsightStrategy, String?) -> Unit,
    onSaveTemplate: ((String, RecordingMode, String, String?, InsightStrategy, String?) -> Unit)? = null,
    onDeleteTemplate: ((String) -> Unit)? = null,
    settings: AppSettings = AppSettings(),
    templates: List<SessionTemplate> = emptyList()
) {
    val languages = com.hearopilot.app.domain.model.SupportedLanguages.ALL
    val supportedCodes = remember { languages.map { it.code }.toSet() }

    // Resolve the device locale to a supported BCP-47 code (e.g. "it"), with "en" fallback.
    val deviceLanguageCode = remember {
        val lang = java.util.Locale.getDefault().language
        if (lang in supportedCodes) lang else "en"
    }

    // Single combined input: used as both session name and topic
    var sessionTopic by remember { mutableStateOf("") }
    var selectedMode by remember { mutableStateOf(RecordingMode.SIMPLE_LISTENING) }
    // Interview role — used as the topic when INTERVIEW mode is selected
    var interviewRole by remember { mutableStateOf("Developer") }
    // Input language (what the user will speak)
    var inputLanguage by remember { mutableStateOf(deviceLanguageCode) }
    // Always a valid BCP-47 code — device locale for analysis modes, translation target for translation mode.
    var outputLanguage by remember { mutableStateOf(deviceLanguageCode) }
    // Default strategy for the selected mode, updated when mode changes.
    var insightStrategy by remember {
        mutableStateOf(settings.simpleListeningDefaultStrategy)
    }

    // Template save dialog state
    var showSaveTemplateDialog by remember { mutableStateOf(false) }
    var templateName by remember { mutableStateOf("") }

    // When mode changes: update language default and strategy default.
    LaunchedEffect(selectedMode) {
        outputLanguage = if (selectedMode == RecordingMode.REAL_TIME_TRANSLATION) {
            settings.translationTargetLanguage
        } else {
            deviceLanguageCode
        }
        insightStrategy = when (selectedMode) {
            RecordingMode.SIMPLE_LISTENING      -> settings.simpleListeningDefaultStrategy
            RecordingMode.SHORT_MEETING         -> settings.shortMeetingDefaultStrategy
            RecordingMode.LONG_MEETING          -> settings.longMeetingDefaultStrategy
            RecordingMode.REAL_TIME_TRANSLATION -> settings.translationDefaultStrategy
            RecordingMode.INTERVIEW             -> settings.interviewDefaultStrategy
        }
    }

    // For INTERVIEW mode, role is the effective topic; otherwise use the text field value.
    val effectiveTopic = if (selectedMode == RecordingMode.INTERVIEW) interviewRole
                         else sessionTopic.trim().takeIf { it.isNotBlank() }

    val maxHeight = LocalConfiguration.current.screenHeightDp.dp * 0.88f

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = maxHeight)
                .padding(horizontal = 20.dp),
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 0.dp
        ) {
            Column {
                // ── Gradient header ──────────────────────────────────────────
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
                        .background(BrandPurpleDark)
                        .padding(horizontal = 20.dp, vertical = 16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.new_session_dialog_title),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Text(
                                text = stringResource(R.string.new_session_recording_mode),
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(alpha = 0.75f)
                            )
                        }
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Pulsing Start button — gentle breathe animation draws attention
                            val infiniteTransition = rememberInfiniteTransition(label = "start_pulse")
                            val startScale by infiniteTransition.animateFloat(
                                initialValue = 1f,
                                targetValue = 1.06f,
                                animationSpec = infiniteRepeatable(
                                    animation = tween(900, easing = FastOutSlowInEasing),
                                    repeatMode = RepeatMode.Reverse
                                ),
                                label = "start_scale"
                            )
                            Button(
                                onClick = {
                                    val langArg = outputLanguage.ifEmpty { null }
                                    onConfirm(effectiveTopic, selectedMode, inputLanguage, langArg, insightStrategy, effectiveTopic)
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color.White,
                                    contentColor = BrandPurpleDark
                                ),
                                shape = MaterialTheme.shapes.medium,
                                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                                modifier = Modifier.scale(startScale)
                            ) {
                                Icon(
                                    imageVector = AppIcons.Mic,
                                    contentDescription = null,
                                    modifier = Modifier
                                        .size(16.dp)
                                        .padding(end = 0.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = stringResource(R.string.start),
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.labelLarge
                                )
                            }
                            if (onSaveTemplate != null) {
                                IconButton(onClick = { showSaveTemplateDialog = true }) {
                                    Icon(AppIcons.Bookmark, contentDescription = stringResource(R.string.templates_save), tint = Color.White)
                                }
                            }
                            IconButton(onClick = onDismiss) {
                                Icon(AppIcons.Close, contentDescription = stringResource(R.string.cancel), tint = Color.White)
                            }
                        }
                    }
                }

                // ── Scrollable body ──────────────────────────────────────────
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Templates row — quick-pick presets
                    if (templates.isNotEmpty()) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = stringResource(R.string.templates_title),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                contentPadding = PaddingValues(horizontal = 0.dp)
                            ) {
                                items(templates, key = { it.id }) { template ->
                                    TemplateChip(
                                        template = template,
                                        onClick = {
                                            selectedMode = template.mode
                                            inputLanguage = template.inputLanguage
                                            outputLanguage = template.outputLanguage ?: deviceLanguageCode
                                            insightStrategy = template.insightStrategy
                                            if (template.mode == RecordingMode.INTERVIEW) {
                                                interviewRole = template.topic ?: "Developer"
                                            } else {
                                                sessionTopic = template.topic ?: ""
                                            }
                                        },
                                        onDelete = { onDeleteTemplate?.invoke(template.id) }
                                    )
                                }
                            }
                        }
                    }

                    // Input language selector — what language is being spoken
                    LanguageSelector(
                        label = stringResource(R.string.new_session_input_language_label),
                        languages = languages,
                        selectedCode = inputLanguage,
                        showAutoOption = false,
                        onLanguageSelected = { inputLanguage = it }
                    )

                    // Language selector — shown first so the user picks the output language
                    // before choosing a mode. For REAL_TIME_TRANSLATION it is the translation
                    // target; for all other modes it selects which locale's system prompt is used.
                    LanguageSelector(
                        label = if (selectedMode == RecordingMode.REAL_TIME_TRANSLATION) {
                            stringResource(R.string.new_session_output_language)
                        } else {
                            stringResource(R.string.new_session_output_language_label)
                        },
                        languages = languages,
                        selectedCode = outputLanguage,
                        showAutoOption = false,
                        onLanguageSelected = { outputLanguage = it }
                    )

                    // Combined name/topic field
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        OutlinedTextField(
                            value = sessionTopic,
                            onValueChange = { sessionTopic = it },
                            label = { Text(stringResource(R.string.new_session_name_label)) },
                            placeholder = { Text(stringResource(R.string.new_session_name_hint)) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            shape = MaterialTheme.shapes.medium
                        )
                        if (settings.llmEnabled) {
                            Text(
                                text = stringResource(R.string.session_topic_helper),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // Mode cards
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        RecordingMode.values().forEach { mode ->
                            val intervalLabel = if (settings.llmEnabled) {
                                if (insightStrategy == InsightStrategy.END_OF_SESSION && mode == selectedMode) {
                                    stringResource(R.string.insight_strategy_batch)
                                } else {
                                    when (mode) {
                                        RecordingMode.SIMPLE_LISTENING      -> "${settings.simpleListeningIntervalSeconds}s"
                                        RecordingMode.SHORT_MEETING         -> "${settings.shortMeetingIntervalSeconds}s"
                                        RecordingMode.LONG_MEETING          -> "${settings.longMeetingIntervalMinutes}min"
                                        RecordingMode.REAL_TIME_TRANSLATION -> "${settings.translationIntervalSeconds}s"
                                        RecordingMode.INTERVIEW             -> "${settings.interviewIntervalSeconds}s"
                                    }
                                }
                            } else null

                            ModeCard(
                                mode = mode,
                                isSelected = mode == selectedMode,
                                onClick = { selectedMode = mode },
                                insightIntervalLabel = intervalLabel
                            )
                        }
                    }

                    // Interview role selector — shown only in INTERVIEW mode
                    if (selectedMode == RecordingMode.INTERVIEW) {
                        InterviewRoleSelector(
                            selectedRole = interviewRole,
                            onRoleSelected = { interviewRole = it }
                        )
                    }

                    // Insight strategy selector — shown only when LLM is enabled.
                    if (settings.llmEnabled) {
                        InsightStrategySelector(
                            selected = insightStrategy,
                            onStrategySelected = { insightStrategy = it }
                        )
                    }

                }
            }
        }
    }

    // Save-template name dialog
    if (showSaveTemplateDialog) {
        AlertDialog(
            onDismissRequest = { showSaveTemplateDialog = false },
            title = { Text(stringResource(R.string.templates_save)) },
            text = {
                OutlinedTextField(
                    value = templateName,
                    onValueChange = { templateName = it },
                    label = { Text(stringResource(R.string.templates_save_hint)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (templateName.isNotBlank()) {
                            val langArg = outputLanguage.ifEmpty { null }
                            onSaveTemplate?.invoke(templateName.trim(), selectedMode, inputLanguage, langArg, insightStrategy, effectiveTopic)
                            templateName = ""
                            showSaveTemplateDialog = false
                        }
                    }
                ) { Text(stringResource(R.string.save)) }
            },
            dismissButton = {
                TextButton(onClick = { showSaveTemplateDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}

/**
 * Chip displaying a saved session template. Tap to apply, long-press shows delete.
 */
@Composable
private fun TemplateChip(
    template: SessionTemplate,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    var showDeleteMenu by remember { mutableStateOf(false) }
    Box {
        Surface(
            onClick = onClick,
            shape = RoundedCornerShape(20.dp),
            color = BrandPurple.copy(alpha = 0.10f),
            border = androidx.compose.foundation.BorderStroke(1.dp, BrandPurple.copy(alpha = 0.3f)),
            modifier = Modifier.clickable(onClickLabel = stringResource(R.string.use_template)) {
                onClick()
            }
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    AppIcons.Bookmark,
                    contentDescription = null,
                    tint = BrandPurple,
                    modifier = Modifier.size(14.dp)
                )
                Text(
                    text = template.name,
                    style = MaterialTheme.typography.labelMedium,
                    color = BrandPurple
                )
                // Delete button inside chip
                IconButton(
                    onClick = { showDeleteMenu = true },
                    modifier = Modifier.size(16.dp)
                ) {
                    Icon(
                        AppIcons.Close,
                        contentDescription = stringResource(R.string.delete),
                        tint = BrandPurple.copy(alpha = 0.6f),
                        modifier = Modifier.size(12.dp)
                    )
                }
            }
        }
        DropdownMenu(expanded = showDeleteMenu, onDismissRequest = { showDeleteMenu = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error) },
                onClick = { onDelete(); showDeleteMenu = false }
            )
        }
    }
}

/**
 * Selectable card for a recording mode.
 * Optionally shows an insight-interval badge when the LLM is active.
 */
@Composable
private fun ModeCard(
    mode: RecordingMode,
    isSelected: Boolean,
    onClick: () -> Unit,
    insightIntervalLabel: String? = null
) {
    val accentColor = when (mode) {
        RecordingMode.SIMPLE_LISTENING      -> ModeSkyBlueTint
        RecordingMode.SHORT_MEETING         -> ModeShortMeetingTint
        RecordingMode.LONG_MEETING          -> ModeAmberTint
        RecordingMode.REAL_TIME_TRANSLATION -> ModeEmeraldTint
        RecordingMode.INTERVIEW             -> ModeInterviewTint
    }
    val borderColor = if (isSelected) accentColor else MaterialTheme.colorScheme.outlineVariant
    val bgColor = if (isSelected) accentColor.copy(alpha = 0.07f) else MaterialTheme.colorScheme.surface

    val icon = when (mode) {
        RecordingMode.SIMPLE_LISTENING      -> AppIcons.ModeSimpleListening
        RecordingMode.SHORT_MEETING         -> AppIcons.ModeShortMeeting
        RecordingMode.LONG_MEETING          -> AppIcons.ModeLongMeeting
        RecordingMode.REAL_TIME_TRANSLATION -> AppIcons.ModeTranslation
        RecordingMode.INTERVIEW             -> AppIcons.ModeInterview
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(bgColor)
            .border(
                width = if (isSelected) 1.5.dp else 1.dp,
                color = borderColor,
                shape = MaterialTheme.shapes.medium
            )
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Icon container
        Box(
            modifier = Modifier
                .size(40.dp)
                .background(
                    color = if (isSelected) accentColor.copy(alpha = 0.15f)
                            else MaterialTheme.colorScheme.surfaceVariant,
                    shape = MaterialTheme.shapes.small
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isSelected) accentColor else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = when (mode) {
                    RecordingMode.SIMPLE_LISTENING      -> stringResource(R.string.mode_simple_listening)
                    RecordingMode.SHORT_MEETING         -> stringResource(R.string.mode_short_meeting)
                    RecordingMode.LONG_MEETING          -> stringResource(R.string.mode_long_meeting)
                    RecordingMode.REAL_TIME_TRANSLATION -> stringResource(R.string.mode_translation_live)
                    RecordingMode.INTERVIEW             -> stringResource(R.string.mode_interview)
                },
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                color = if (isSelected) accentColor else MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = when (mode) {
                    RecordingMode.SIMPLE_LISTENING      -> stringResource(R.string.mode_simple_listening_desc)
                    RecordingMode.SHORT_MEETING         -> stringResource(R.string.mode_short_meeting_desc)
                    RecordingMode.LONG_MEETING          -> stringResource(R.string.mode_long_meeting_desc)
                    RecordingMode.REAL_TIME_TRANSLATION -> stringResource(R.string.mode_translation_desc)
                    RecordingMode.INTERVIEW             -> stringResource(R.string.mode_interview_desc)
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Insight interval badge — shown only when LLM is active
            if (insightIntervalLabel != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = AppIcons.Schedule,
                        contentDescription = null,
                        tint = if (isSelected) accentColor else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(12.dp)
                    )
                    Text(
                        text = stringResource(R.string.new_session_insight_interval, insightIntervalLabel),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isSelected) accentColor else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        if (isSelected) {
            Icon(
                imageVector = AppIcons.CheckCircle,
                contentDescription = null,
                tint = accentColor,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

/**
 * Two-chip selector for the insight generation strategy.
 */
@Composable
private fun InsightStrategySelector(
    selected: InsightStrategy,
    onStrategySelected: (InsightStrategy) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = stringResource(R.string.insight_strategy_label),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = selected == InsightStrategy.REAL_TIME,
                onClick = { onStrategySelected(InsightStrategy.REAL_TIME) },
                label = { Text(stringResource(R.string.insight_strategy_realtime)) },
                leadingIcon = {
                    Icon(AppIcons.Schedule, contentDescription = null, modifier = Modifier.size(16.dp))
                }
            )
            FilterChip(
                selected = selected == InsightStrategy.END_OF_SESSION,
                onClick = { onStrategySelected(InsightStrategy.END_OF_SESSION) },
                label = { Text(stringResource(R.string.insight_strategy_batch)) },
                leadingIcon = {
                    Icon(AppIcons.Summarize, contentDescription = null, modifier = Modifier.size(16.dp))
                }
            )
        }
    }
}

/**
 * Dropdown selector for the interview target role.
 * The selected role is injected as `{role}` into the interview system prompt.
 */
@Composable
private fun InterviewRoleSelector(
    selectedRole: String,
    onRoleSelected: (String) -> Unit
) {
    val roles = listOf(
        stringResource(R.string.interview_role_developer),
        stringResource(R.string.interview_role_devops),
        stringResource(R.string.interview_role_tester),
        stringResource(R.string.interview_role_product_manager),
        stringResource(R.string.interview_role_designer),
        stringResource(R.string.interview_role_data_scientist),
        stringResource(R.string.interview_role_other)
    )
    var expanded by remember { mutableStateOf(false) }
    var buttonSize by remember { mutableStateOf(IntSize.Zero) }
    val density = LocalDensity.current

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = stringResource(R.string.interview_role_label),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Box(modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(
                onClick = { expanded = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .onGloballyPositioned { buttonSize = it.size },
                shape = MaterialTheme.shapes.medium
            ) {
                Text(selectedRole, modifier = Modifier.weight(1f))
                Icon(
                    imageVector = AppIcons.ExpandMore,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
            }
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier.width(with(density) { buttonSize.width.toDp() })
            ) {
                roles.forEachIndexed { index, role ->
                    DropdownMenuItem(
                        text = { Text(role) },
                        onClick = { onRoleSelected(role); expanded = false },
                        leadingIcon = if (role == selectedRole) {
                            { Icon(AppIcons.CheckCircle, null, modifier = Modifier.size(16.dp)) }
                        } else null
                    )
                    if (index < roles.lastIndex) {
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 12.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun LanguageSelector(
    languages: List<com.hearopilot.app.domain.model.SupportedLanguage>,
    selectedCode: String,
    onLanguageSelected: (String) -> Unit,
    label: String = stringResource(R.string.new_session_output_language),
    showAutoOption: Boolean = false
) {
    var expanded by remember { mutableStateOf(false) }
    var buttonSize by remember { mutableStateOf(IntSize.Zero) }
    val density = LocalDensity.current

    val autoLabel = stringResource(R.string.new_session_language_auto)
    val displayName = if (selectedCode.isEmpty()) autoLabel
                      else languages.find { it.code == selectedCode }?.nativeName ?: selectedCode

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Box(modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(
                onClick = { expanded = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .onGloballyPositioned { buttonSize = it.size },
                shape = MaterialTheme.shapes.medium
            ) {
                Text(displayName, modifier = Modifier.weight(1f))
                Icon(
                    imageVector = AppIcons.ExpandMore,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
            }
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier
                    .width(with(density) { buttonSize.width.toDp() })
                    .heightIn(max = 320.dp)
            ) {
                if (showAutoOption) {
                    DropdownMenuItem(
                        text = { Text(autoLabel) },
                        onClick = { onLanguageSelected(""); expanded = false },
                        leadingIcon = if (selectedCode.isEmpty()) {
                            { Icon(AppIcons.CheckCircle, null, modifier = Modifier.size(16.dp)) }
                        } else null
                    )
                    HorizontalDivider()
                }
                languages.forEachIndexed { index, lang ->
                    DropdownMenuItem(
                        text = { Text(lang.nativeName) },
                        onClick = { onLanguageSelected(lang.code); expanded = false },
                        leadingIcon = if (lang.code == selectedCode) {
                            { Icon(AppIcons.CheckCircle, null, modifier = Modifier.size(16.dp)) }
                        } else null
                    )
                    if (index < languages.lastIndex) {
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 12.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                        )
                    }
                }
            }
        }
    }
}
