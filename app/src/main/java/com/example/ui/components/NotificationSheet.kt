package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.data.model.AppNotification
import com.example.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationCenterSheet(
    notifications: List<AppNotification>,
    onMarkRead: (String) -> Unit,
    onMarkAllRead: () -> Unit,
    onDelete: (String) -> Unit,
    onClearAll: () -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = SilangSurface) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 320.dp, max = 560.dp)
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Notification Center", style = MaterialTheme.typography.titleLarge)
                    Text("${notifications.count { !it.isRead }} unread alerts", style = MaterialTheme.typography.bodySmall, color = SilangSlate600)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = onMarkAllRead, modifier = Modifier.testTag("mark_all_read_button")) {
                        Text("Read All")
                    }
                    TextButton(onClick = onClearAll, modifier = Modifier.testTag("clear_notifications_button")) {
                        Text("Clear", color = SilangRed)
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            if (notifications.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.NotificationsNone, contentDescription = null, tint = SilangSlate400, modifier = Modifier.size(44.dp))
                        Text("No notifications yet", style = MaterialTheme.typography.titleSmall, color = SilangSlate600)
                    }
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(notifications, key = { it.id }) { notif ->
                        val badgeColor = when (notif.type) {
                            "danger" -> SilangRed
                            "warning" -> SilangAmber
                            "success" -> SilangGreen
                            else -> SilangBlue
                        }
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onMarkRead(notif.id) },
                            colors = CardDefaults.cardColors(
                                containerColor = if (notif.isRead) SilangWhite else SilangBlueContainer.copy(alpha = 0.35f)
                            ),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.Top,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .padding(top = 4.dp)
                                        .size(10.dp)
                                        .clip(RoundedCornerShape(5.dp))
                                        .background(badgeColor)
                                )
                                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text(notif.title, style = MaterialTheme.typography.titleSmall)
                                    Text(notif.message, style = MaterialTheme.typography.bodySmall, color = SilangSlate600)
                                    Text(notif.category.uppercase(), style = MaterialTheme.typography.labelSmall, color = badgeColor)
                                }
                                IconButton(onClick = { onDelete(notif.id) }) {
                                    Icon(Icons.Default.DeleteOutline, contentDescription = "Remove notification", tint = SilangSlate400)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
