package com.aba.medirecord.ui.reminders

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aba.medirecord.data.model.MedicationReminder

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RemindersScreen(
    userEmail: String?,
    onNavigateBack: () -> Unit,
    onNavigateToConfigureSchedule: (String?) -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: RemindersViewModel = viewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val completingReminderId by viewModel.completingReminderId.collectAsState()
    val actionFeedback by viewModel.actionFeedback.collectAsState()

    LaunchedEffect(userEmail) {
        viewModel.loadReminders(userEmail)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Mis Recordatorios",
                            fontWeight = FontWeight.Bold
                        )
                        if (!userEmail.isNullOrBlank()) {
                            Text(
                                text = userEmail,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Regresar"
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.loadReminders(userEmail) }) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Actualizar recordatorios",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { onNavigateToConfigureSchedule(null) },
                icon = {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Configurar horario"
                    )
                },
                text = {
                    Text(
                        text = "Configurar Horario",
                        fontWeight = FontWeight.Bold
                    )
                },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            )
        }
    ) { innerPadding ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 20.dp)
        ) {
            // Mensaje de confirmación o error al marcar como realizado (Criterios 4 y 7)
            AnimatedVisibility(visible = actionFeedback != null) {
                val feedback = actionFeedback
                if (feedback != null) {
                    val isError = feedback is ReminderActionFeedback.Error
                    val message = when (feedback) {
                        is ReminderActionFeedback.Success -> feedback.message
                        is ReminderActionFeedback.Error -> feedback.message
                    }
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp, bottom = 4.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isError)
                                MaterialTheme.colorScheme.errorContainer
                            else
                                Color(0xFFE8F5E9)
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (isError) Icons.Default.Warning else Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = if (isError) MaterialTheme.colorScheme.error else Color(0xFF2E7D32),
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = message,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold,
                                color = if (isError)
                                    MaterialTheme.colorScheme.onErrorContainer
                                else
                                    Color(0xFF1B5E20),
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(
                                onClick = { viewModel.clearFeedback() },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Cerrar aviso",
                                    modifier = Modifier.size(16.dp),
                                    tint = if (isError)
                                        MaterialTheme.colorScheme.onErrorContainer
                                    else
                                        Color(0xFF1B5E20)
                                )
                            }
                        }
                    }
                }
            }

            when (val state = uiState) {
                is RemindersUiState.Loading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }

                is RemindersUiState.Error -> {
                    RemindersErrorView(
                        message = state.message,
                        onRetry = { viewModel.loadReminders(userEmail) }
                    )
                }

                is RemindersUiState.Empty -> {
                    RemindersEmptyView(
                        userEmail = state.userEmail,
                        onConfigureScheduleClick = { onNavigateToConfigureSchedule(null) },
                        onReload = { viewModel.loadReminders(userEmail) }
                    )
                }

                is RemindersUiState.Success -> {
                    RemindersSuccessContent(
                        allReminders = state.allReminders,
                        displayedReminders = state.displayedReminders,
                        selectedFilter = state.selectedFilter,
                        userEmail = state.userEmail,
                        completingReminderId = completingReminderId,
                        onFilterSelected = { filter -> viewModel.setFilter(filter) },
                        onMarkAsCompleted = { id -> viewModel.markReminderAsCompleted(id) },
                        onSendNotification = { rem -> viewModel.sendReminderNotificationNow(rem) },
                        onEditSchedule = { medId -> onNavigateToConfigureSchedule(medId) }
                    )
                }
            }
        }
    }
}

@Composable
private fun RemindersSuccessContent(
    allReminders: List<MedicationReminder>,
    displayedReminders: List<MedicationReminder>,
    selectedFilter: ReminderFilter,
    userEmail: String,
    completingReminderId: String?,
    onFilterSelected: (ReminderFilter) -> Unit,
    onMarkAsCompleted: (String) -> Unit,
    onSendNotification: (MedicationReminder) -> Unit = {},
    onEditSchedule: (String) -> Unit,
) {
    val pendingCount = allReminders.count { !it.isCompleted }
    val completedCount = allReminders.count { it.isCompleted }

    Column(modifier = Modifier.fillMaxSize()) {
        Spacer(modifier = Modifier.height(8.dp))

        // Banner de usuario autenticado (Criterio 7 de F-07)
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Recordatorios activos de: $userEmail",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Tarjetas de resumen visual: Pendientes vs Realizados
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            StatusSummaryCard(
                title = "Pendientes",
                count = pendingCount,
                accentColor = Color(0xFFE65100),
                bgColor = Color(0xFFFFF3E0),
                modifier = Modifier.weight(1f)
            )
            StatusSummaryCard(
                title = "Realizados",
                count = completedCount,
                accentColor = Color(0xFF2E7D32),
                bgColor = Color(0xFFE8F5E9),
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Chips de filtro para visualizar Todos, Pendientes o Realizados
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = selectedFilter == ReminderFilter.ALL,
                onClick = { onFilterSelected(ReminderFilter.ALL) },
                label = { Text("Todos (${allReminders.size})") }
            )
            FilterChip(
                selected = selectedFilter == ReminderFilter.PENDING,
                onClick = { onFilterSelected(ReminderFilter.PENDING) },
                label = { Text("Pendientes ($pendingCount)") }
            )
            FilterChip(
                selected = selectedFilter == ReminderFilter.COMPLETED,
                onClick = { onFilterSelected(ReminderFilter.COMPLETED) },
                label = { Text("Realizados ($completedCount)") }
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (displayedReminders.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 32.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No hay recordatorios en esta categoría.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            // Lista ordenada cronológicamente
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(bottom = 84.dp)
            ) {
                items(
                    items = displayedReminders,
                    key = { it.id }
                ) { reminder ->
                    ReminderItemCard(
                        reminder = reminder,
                        isCompleting = completingReminderId == reminder.id,
                        onMarkAsCompleted = { onMarkAsCompleted(reminder.id) },
                        onSendNotification = { onSendNotification(reminder) },
                        onEditSchedule = { onEditSchedule(reminder.medicationId) }
                    )
                }
            }
        }
    }
}

@Composable
private fun StatusSummaryCard(
    title: String,
    count: Int,
    accentColor: Color,
    bgColor: Color,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = bgColor
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = accentColor
                )
                Text(
                    text = if (count == 1) "1 toma" else "$count tomas",
                    style = MaterialTheme.typography.bodySmall,
                    color = accentColor.copy(alpha = 0.8f)
                )
            }
            Text(
                text = count.toString(),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.ExtraBold,
                color = accentColor
            )
        }
    }
}

@Composable
private fun ReminderItemCard(
    reminder: MedicationReminder,
    isCompleting: Boolean,
    onMarkAsCompleted: () -> Unit,
    onSendNotification: () -> Unit = {},
    onEditSchedule: () -> Unit,
) {
    val isCompleted = reminder.isCompleted
    val periodLabel = RemindersViewModel.getTimePeriodLabel(reminder.scheduledTime)

    val cardBgColor by animateColorAsState(
        targetValue = if (isCompleted)
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
        else
            MaterialTheme.colorScheme.surface,
        label = "reminderCardBg"
    )

    val borderColor = if (isCompleted)
        Color(0xFF2E7D32).copy(alpha = 0.35f)
    else
        MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = borderColor,
                shape = RoundedCornerShape(18.dp)
            ),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = cardBgColor),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isCompleted) 0.dp else 3.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Fila superior: Horario destacado + Estado visual ("Pendiente" vs "Realizado")
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = if (isCompleted)
                        Color(0xFFE8F5E9)
                    else
                        MaterialTheme.colorScheme.primaryContainer
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (isCompleted) Icons.Default.CheckCircle else Icons.Default.Notifications,
                            contentDescription = null,
                            tint = if (isCompleted) Color(0xFF2E7D32) else MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "${reminder.scheduledTime} • $periodLabel",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = if (isCompleted) Color(0xFF2E7D32) else MaterialTheme.colorScheme.primary
                        )
                    }
                }

                // Badge de estado visual: cambia a "Realizado" cuando se completa (Criterio 5)
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (isCompleted) Color(0xFFE8F5E9) else Color(0xFFFFF3E0)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(if (isCompleted) Color(0xFF2E7D32) else Color(0xFFE65100))
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (isCompleted) "Realizado" else "Pendiente",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isCompleted) Color(0xFF2E7D32) else Color(0xFFE65100)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Nombre del medicamento
            Text(
                text = reminder.medicationName,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    textDecoration = if (isCompleted) TextDecoration.LineThrough else TextDecoration.None
                ),
                color = if (isCompleted)
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                else
                    MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(4.dp))

            // Información necesaria para identificar la toma
            Text(
                text = "💊 Dosis: ${reminder.dosage}",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (!reminder.instructions.isNullOrBlank()) {
                Text(
                    text = "📋 Indicaciones: ${reminder.instructions}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }

            val daysSummary = when {
                reminder.selectedDays.isEmpty() || reminder.selectedDays.size == 7 -> "Todos los días"
                else -> reminder.selectedDays.joinToString(", ") { it.take(3) }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Default.DateRange,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "${reminder.frequency} ($daysSummary)",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                TextButton(
                    onClick = onEditSchedule,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                    modifier = Modifier.height(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "Editar horario",
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Ajustar",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Acción "Marcar como realizado" y prueba de notificación en cada recordatorio pendiente
            if (!isCompleted) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onSendNotification,
                        modifier = Modifier
                            .weight(0.45f)
                            .height(44.dp),
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Notifications,
                            contentDescription = "Enviar notificación",
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Notificar",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 12.sp
                        )
                    }

                    Button(
                        onClick = onMarkAsCompleted,
                        enabled = !isCompleting,
                        modifier = Modifier
                            .weight(0.55f)
                            .height(44.dp),
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        if (isCompleting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Guardando...",
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Marcar realizado",
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            } else {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    color = Color(0xFFE8F5E9).copy(alpha = 0.85f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = Color(0xFF2E7D32),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        val completedText = if (!reminder.completedAt.isNullOrBlank()) {
                            "Toma completada (${reminder.completedAt})"
                        } else {
                            "Toma completada y registrada"
                        }
                        Text(
                            text = completedText,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF2E7D32)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RemindersEmptyView(
    userEmail: String,
    onConfigureScheduleClick: () -> Unit,
    onReload: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 32.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(36.dp)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Sin recordatorios configurados",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "No se encontraron recordatorios activos para la cuenta $userEmail.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(20.dp))

                Button(
                    onClick = onConfigureScheduleClick,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Configurar un horario")
                }

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedButton(onClick = onReload) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Actualizar")
                }
            }
        }
    }
}

@Composable
private fun RemindersErrorView(
    message: String,
    onRetry: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = message,
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(modifier = Modifier.height(12.dp))
        Button(onClick = onRetry) {
            Text("Reintentar")
        }
    }
}
