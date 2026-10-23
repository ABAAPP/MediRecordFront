package com.aba.medirecord.ui.schedule

import android.app.TimePickerDialog
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ScheduleConfigScreen(
    onNavigateBack: () -> Unit,
    onScheduleSaved: () -> Unit,
    preselectedMedicationId: String? = null,
    modifier: Modifier = Modifier,
    viewModel: ScheduleConfigViewModel = viewModel(),
) {
    val context = LocalContext.current
    val medications by viewModel.medications.collectAsState()
    val uiState by viewModel.uiState.collectAsState()
    val formErrors by viewModel.formErrors.collectAsState()

    var selectedMedicationId by remember { mutableStateOf("") }
    var selectedMedicationName by remember { mutableStateOf("") }
    var selectedMedicationDosage by remember { mutableStateOf("") }
    var expandedMedicationDropdown by remember { mutableStateOf(false) }

    var scheduledTime by remember { mutableStateOf("08:00 AM") }

    var expandedFrequencyDropdown by remember { mutableStateOf(false) }
    var selectedFrequency by remember { mutableStateOf(ScheduleConfigViewModel.FREQ_DAILY) }

    var selectedDays by remember {
        mutableStateOf(ScheduleConfigViewModel.WEEK_DAYS)
    }

    var reminderEnabled by remember { mutableStateOf(true) }
    var notes by remember { mutableStateOf("") }

    // Preseleccionar medicamento si se envió por navegación
    LaunchedEffect(medications, preselectedMedicationId) {
        if (selectedMedicationId.isEmpty() && medications.isNotEmpty()) {
            val target = if (!preselectedMedicationId.isNullOrBlank()) {
                medications.find { it.id == preselectedMedicationId }
            } else {
                null
            }
            if (target != null) {
                selectedMedicationId = target.id
                selectedMedicationName = target.name
                selectedMedicationDosage = target.dosage
                scheduledTime = target.scheduledTime.ifBlank { "08:00 AM" }
                selectedFrequency = target.frequency?.takeIf { it.isNotBlank() }
                    ?: ScheduleConfigViewModel.FREQ_DAILY
                selectedDays = if (target.selectedDays.isNotEmpty()) {
                    target.selectedDays
                } else {
                    ScheduleConfigViewModel.defaultDaysForFrequency(selectedFrequency)
                }
                notes = target.instructions.orEmpty()
            }
        }
    }

    fun showTimePicker() {
        val c = Calendar.getInstance()
        TimePickerDialog(
            context,
            { _, hourOfDay, minute ->
                val cal = Calendar.getInstance().apply {
                    set(Calendar.HOUR_OF_DAY, hourOfDay)
                    set(Calendar.MINUTE, minute)
                }
                scheduledTime = SimpleDateFormat("hh:mm a", Locale.US).format(cal.time)
                viewModel.clearErrors()
            },
            c.get(Calendar.HOUR_OF_DAY),
            c.get(Calendar.MINUTE),
            false
        ).show()
    }

    val scrollState = rememberScrollState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Configurar Horario",
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Regresar"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(scrollState)
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Card informativa superior
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
                )
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Notifications,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(32.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "Selecciona un medicamento y personaliza la hora, frecuencia y los días en que debes tomarlo.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }

            // Mensaje de Error (Criterio 8: Se informa al usuario cuando ocurre un error)
            AnimatedVisibility(visible = uiState is ScheduleConfigUiState.Error) {
                val errorState = uiState as? ScheduleConfigUiState.Error
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = "Error",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = errorState?.message ?: "Ocurrió un error al guardar la configuración del horario",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // SECCIÓN 1: SELECCIONAR MEDICAMENTO (Criterio 1)
            Text(
                text = "1. Medicamento",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )

            ExposedDropdownMenuBox(
                expanded = expandedMedicationDropdown,
                onExpandedChange = { expandedMedicationDropdown = !expandedMedicationDropdown },
                modifier = Modifier.fillMaxWidth()
            ) {
                val displayValue = if (selectedMedicationName.isNotBlank()) {
                    if (selectedMedicationDosage.isNotBlank()) {
                        "$selectedMedicationName ($selectedMedicationDosage)"
                    } else {
                        selectedMedicationName
                    }
                } else {
                    ""
                }

                OutlinedTextField(
                    value = displayValue,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Seleccionar medicamento *") },
                    placeholder = { Text("Elige un medicamento de tu lista") },
                    trailingIcon = {
                        ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedMedicationDropdown)
                    },
                    isError = formErrors.medicationError != null,
                    supportingText = formErrors.medicationError?.let {
                        { Text(it, color = MaterialTheme.colorScheme.error) }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(MenuAnchorType.PrimaryNotEditable),
                    shape = RoundedCornerShape(12.dp)
                )

                ExposedDropdownMenu(
                    expanded = expandedMedicationDropdown,
                    onDismissRequest = { expandedMedicationDropdown = false }
                ) {
                    if (medications.isEmpty()) {
                        DropdownMenuItem(
                            text = { Text("No hay medicamentos registrados") },
                            onClick = { expandedMedicationDropdown = false }
                        )
                    } else {
                        medications.forEach { med ->
                            DropdownMenuItem(
                                text = {
                                    Column {
                                        Text(
                                            text = med.name,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = "${med.dosage} • Horario actual: ${med.scheduledTime}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                },
                                onClick = {
                                    selectedMedicationId = med.id
                                    selectedMedicationName = med.name
                                    selectedMedicationDosage = med.dosage
                                    scheduledTime = med.scheduledTime.ifBlank { "08:00 AM" }
                                    val medFreq = med.frequency?.takeIf { it.isNotBlank() }
                                    if (medFreq != null) {
                                        selectedFrequency = medFreq
                                    }
                                    selectedDays = if (med.selectedDays.isNotEmpty()) {
                                        med.selectedDays
                                    } else {
                                        ScheduleConfigViewModel.defaultDaysForFrequency(selectedFrequency)
                                    }
                                    notes = med.instructions.orEmpty()
                                    expandedMedicationDropdown = false
                                    viewModel.clearErrors()
                                }
                            )
                        }
                    }
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

            // SECCIÓN 2: HORA DE TOMA (Criterio 2)
            Text(
                text = "2. Hora de Toma",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        showTimePicker()
                    }
            ) {
                OutlinedTextField(
                    value = scheduledTime,
                    onValueChange = {},
                    readOnly = true,
                    enabled = false,
                    label = { Text("Hora programada *") },
                    trailingIcon = {
                        IconButton(onClick = { showTimePicker() }) {
                            Icon(Icons.Default.Notifications, contentDescription = "Seleccionar hora")
                        }
                    },
                    isError = formErrors.scheduledTimeError != null,
                    supportingText = formErrors.scheduledTimeError?.let {
                        { Text(it, color = MaterialTheme.colorScheme.error) }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        disabledTextColor = MaterialTheme.colorScheme.onSurface,
                        disabledBorderColor = if (formErrors.scheduledTimeError != null)
                            MaterialTheme.colorScheme.error
                        else
                            MaterialTheme.colorScheme.outline,
                        disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        disabledTrailingIconColor = MaterialTheme.colorScheme.primary
                    )
                )
            }

            // Chips rápidos de hora
            val quickTimes = listOf("08:00 AM", "12:00 PM", "02:00 PM", "08:00 PM", "10:00 PM")
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                quickTimes.forEach { presetTime ->
                    FilterChip(
                        selected = scheduledTime == presetTime,
                        onClick = {
                            scheduledTime = presetTime
                            viewModel.clearErrors()
                        },
                        label = { Text(presetTime, fontSize = 12.sp) }
                    )
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

            // SECCIÓN 3: FRECUENCIA CORRESPONDIENTE (Criterio 3)
            Text(
                text = "3. Frecuencia de Toma",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )

            ExposedDropdownMenuBox(
                expanded = expandedFrequencyDropdown,
                onExpandedChange = { expandedFrequencyDropdown = !expandedFrequencyDropdown },
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedTextField(
                    value = selectedFrequency,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Frecuencia *") },
                    trailingIcon = {
                        ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedFrequencyDropdown)
                    },
                    isError = formErrors.frequencyError != null,
                    supportingText = formErrors.frequencyError?.let {
                        { Text(it, color = MaterialTheme.colorScheme.error) }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(MenuAnchorType.PrimaryNotEditable),
                    shape = RoundedCornerShape(12.dp)
                )

                ExposedDropdownMenu(
                    expanded = expandedFrequencyDropdown,
                    onDismissRequest = { expandedFrequencyDropdown = false }
                ) {
                    ScheduleConfigViewModel.FREQUENCY_OPTIONS.forEach { option ->
                        DropdownMenuItem(
                            text = { Text(option) },
                            onClick = {
                                selectedFrequency = option
                                selectedDays = ScheduleConfigViewModel.defaultDaysForFrequency(option)
                                expandedFrequencyDropdown = false
                                viewModel.clearErrors()
                            }
                        )
                    }
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

            // SECCIÓN 4: DÍAS DE LA SEMANA (Criterio 4: El usuario puede seleccionar los días necesarios cuando corresponda)
            val isSpecificDays = ScheduleConfigViewModel.requiresDaySelection(selectedFrequency)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "4. Días de Toma",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = if (isSpecificDays) {
                            "Selecciona los días específicos en que debes tomar el medicamento *"
                        } else {
                            "Días programados según la frecuencia (puedes ajustarlos si lo necesitas)"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Botones redondos para los 7 días de la semana
            val dayShortLabels = mapOf(
                "Lunes" to "L",
                "Martes" to "M",
                "Miércoles" to "X",
                "Jueves" to "J",
                "Viernes" to "V",
                "Sábado" to "S",
                "Domingo" to "D"
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                ScheduleConfigViewModel.WEEK_DAYS.forEach { dayName ->
                    val isSelected = dayName in selectedDays
                    val shortLabel = dayShortLabels[dayName] ?: dayName.take(1)

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .clickable {
                                selectedDays = if (isSelected) {
                                    selectedDays - dayName
                                } else {
                                    selectedDays + dayName
                                }
                                viewModel.clearErrors()
                            }
                            .padding(vertical = 4.dp, horizontal = 2.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(
                                    if (isSelected)
                                        MaterialTheme.colorScheme.primary
                                    else
                                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                                )
                                .border(
                                    width = 1.dp,
                                    color = if (formErrors.selectedDaysError != null && !isSelected)
                                        MaterialTheme.colorScheme.error
                                    else if (isSelected)
                                        MaterialTheme.colorScheme.primary
                                    else
                                        MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                                    shape = CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = shortLabel,
                                fontWeight = FontWeight.Bold,
                                color = if (isSelected)
                                    MaterialTheme.colorScheme.onPrimary
                                else
                                    MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = dayName.take(3),
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isSelected)
                                MaterialTheme.colorScheme.primary
                            else
                                MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Atajos rápidos para selección de días
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                AssistChip(
                    onClick = {
                        selectedDays = listOf("Lunes", "Martes", "Miércoles", "Jueves", "Viernes")
                        viewModel.clearErrors()
                    },
                    label = { Text("Lunes a Viernes", fontSize = 12.sp) }
                )
                AssistChip(
                    onClick = {
                        selectedDays = listOf("Lunes", "Miércoles", "Viernes")
                        viewModel.clearErrors()
                    },
                    label = { Text("Lun / Mié / Vie", fontSize = 12.sp) }
                )
                AssistChip(
                    onClick = {
                        selectedDays = listOf("Martes", "Jueves", "Sábado")
                        viewModel.clearErrors()
                    },
                    label = { Text("Mar / Jue / Sáb", fontSize = 12.sp) }
                )
                AssistChip(
                    onClick = {
                        selectedDays = ScheduleConfigViewModel.WEEK_DAYS
                        viewModel.clearErrors()
                    },
                    label = { Text("Todos los días", fontSize = 12.sp) }
                )
            }

            if (formErrors.selectedDaysError != null) {
                Text(
                    text = formErrors.selectedDaysError!!,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

            // SECCIÓN 5: RECORDATORIOS Y NOTAS
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Activar alerta de horario",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "Notificar en los días y hora seleccionados",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = reminderEnabled,
                    onCheckedChange = { reminderEnabled = it }
                )
            }

            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                label = { Text("Indicaciones de la toma (opcional)") },
                placeholder = { Text("Ej. Tomar con un vaso lleno de agua, antes de dormir") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
                maxLines = 3,
                shape = RoundedCornerShape(12.dp)
            )

            // Resumen visual del horario antes de guardar
            if (selectedMedicationName.isNotBlank()) {
                val orderedSelectedDays = ScheduleConfigViewModel.WEEK_DAYS.filter { it in selectedDays }
                val daysSummary = when {
                    orderedSelectedDays.size == 7 -> "Todos los días"
                    orderedSelectedDays.isEmpty() -> "Sin días seleccionados"
                    else -> orderedSelectedDays.joinToString(", ")
                }

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    )
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.DateRange,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Resumen de programación",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Medicamento: $selectedMedicationName ($selectedMedicationDosage)",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Horario: $scheduledTime • $selectedFrequency",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "Días: $daysSummary",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Botón de Guardar Horario (Criterios 5 y 6)
            Button(
                onClick = {
                    viewModel.validateAndSaveSchedule(
                        medicationId = selectedMedicationId,
                        medicationName = selectedMedicationName,
                        scheduledTime = scheduledTime,
                        frequency = selectedFrequency,
                        selectedDays = selectedDays,
                        reminderEnabled = reminderEnabled,
                        notes = notes
                    )
                },
                enabled = uiState !is ScheduleConfigUiState.Loading,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                shape = RoundedCornerShape(14.dp)
            ) {
                if (uiState is ScheduleConfigUiState.Loading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.5.dp
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text("Guardando horario...")
                } else {
                    Text(
                        text = "Guardar Configuración de Horario",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
        }
    }

    // Modal de Confirmación Exitosa (Criterio 7: Se muestra una confirmación cuando el horario se guarda correctamente)
    if (uiState is ScheduleConfigUiState.Success) {
        val successMessage = (uiState as ScheduleConfigUiState.Success).message
        AlertDialog(
            onDismissRequest = {
                viewModel.resetState()
                onScheduleSaved()
            },
            icon = {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(48.dp)
                )
            },
            title = {
                Text(
                    text = "¡Horario Guardado!",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = successMessage,
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.resetState()
                        onScheduleSaved()
                    }
                ) {
                    Text("Aceptar")
                }
            }
        )
    }
}
