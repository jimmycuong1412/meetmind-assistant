package com.meetmind.assistant.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.meetmind.assistant.domain.model.SessionGroup
import com.meetmind.assistant.ui.R
import com.meetmind.assistant.ui.icons.AppIcons
import com.meetmind.assistant.ui.ui.theme.BrandPrimary

/**
 * Bottom sheet that lets the user assign a session to an existing group,
 * remove it from any group, or create a new group on the fly.
 *
 * @param groups       All existing groups (ordered by creation time)
 * @param currentGroupId  The groupId currently assigned to the session (null = ungrouped)
 * @param onAssign     Called with a group ID to assign, or null to unassign
 * @param onCreateGroup  Called with the new group name; the caller is responsible
 *                     for creating the group and optionally assigning the session
 * @param onDismiss    Called when the sheet should close
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AssignGroupSheet(
    groups: List<SessionGroup>,
    currentGroupId: String?,
    onAssign: (groupId: String?) -> Unit,
    onCreateGroup: (name: String) -> Unit,
    onDismiss: () -> Unit
) {
    var showNewGroupField by remember { mutableStateOf(false) }
    var newGroupName by remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(bottom = 16.dp)
        ) {
            // Title
            Text(
                text = stringResource(R.string.group_assign_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp)
            )

            HorizontalDivider()

            LazyColumn {
                // "No group" option — unassigns the session
                item {
                    GroupRow(
                        label = stringResource(R.string.group_no_group),
                        isSelected = currentGroupId == null,
                        leadingIcon = {
                            Icon(
                                AppIcons.FolderOpen,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                        },
                        onClick = { onAssign(null) }
                    )
                }

                // Existing groups
                items(groups, key = { it.id }) { group ->
                    GroupRow(
                        label = group.name,
                        isSelected = group.id == currentGroupId,
                        leadingIcon = {
                            Icon(
                                AppIcons.Folder,
                                contentDescription = null,
                                tint = BrandPrimary,
                                modifier = Modifier.size(20.dp)
                            )
                        },
                        onClick = { onAssign(group.id) }
                    )
                }

                // "New group" row / inline creation field
                item {
                    if (showNewGroupField) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 24.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = newGroupName,
                                onValueChange = { newGroupName = it },
                                placeholder = { Text(stringResource(R.string.group_name_hint)) },
                                singleLine = true,
                                modifier = Modifier
                                    .weight(1f)
                                    .focusRequester(focusRequester),
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                                keyboardActions = KeyboardActions(onDone = {
                                    val name = newGroupName.trim()
                                    if (name.isNotBlank()) {
                                        onCreateGroup(name)
                                    }
                                })
                            )
                            TextButton(
                                onClick = {
                                    val name = newGroupName.trim()
                                    if (name.isNotBlank()) onCreateGroup(name)
                                },
                                enabled = newGroupName.isNotBlank()
                            ) {
                                Text(stringResource(R.string.group_create_confirm))
                            }
                        }
                        LaunchedEffect(Unit) { focusRequester.requestFocus() }
                    } else {
                        GroupRow(
                            label = stringResource(R.string.group_new),
                            isSelected = false,
                            leadingIcon = {
                                Icon(
                                    AppIcons.Add,
                                    contentDescription = null,
                                    tint = BrandPrimary,
                                    modifier = Modifier.size(20.dp)
                                )
                            },
                            onClick = { showNewGroupField = true }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun GroupRow(
    label: String,
    isSelected: Boolean,
    leadingIcon: @Composable () -> Unit,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        leadingIcon()
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
            color = MaterialTheme.colorScheme.onSurface
        )
        if (isSelected) {
            Icon(
                AppIcons.CheckCircle,
                contentDescription = null,
                tint = BrandPrimary,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
