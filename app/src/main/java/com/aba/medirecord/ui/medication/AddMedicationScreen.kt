package com.aba.medirecord.ui.medication

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddMedicationScreen(
    onNavigateBack: () -> Unit,
    onMedicationAdded: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AddMedicationViewModel = viewModel(),
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val formErrors by viewModel.formErrors.collectAsState()

    // Form states
    var name by remember { mutableStateOf("") }
    var dosage by remember { mutableStateOf("") }

    val todayCalendar = remember { Calendar.getInstance() }
    val initialDateStr = remember {
        SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(todayCalendar.time)
    }

    var startDate by remember { mutableStateOf(initialDateStr) }
    var endDate by remember { mutableStateOf("") }
    var isContinuous by remember { mutableStateOf(false) }

    var scheduledTime by remember { mutableStateOf("08:00 AM") }

    val frequencyOptions = listOf(
        "Cada 4 horas (6 veces al día)",
        "Cada 6 horas (4 veces al día)",
        "Cada 8 horas (3 veces al día)",
        "Cada 12 horas (2 veces al día)",
        "Cada 24 horas (1 vez al día)",
        "Según necesidad"
    )
    var expandedFrequency by remember { mutableStateOf(false) }
    var selectedFrequency by remember { mutableStateOf(frequencyOptions[2]) } // Cada 8 horas default

    var instructions by remember { mutableStateOf("") }

    // Date & Time Picker functions
    fun showStartDatePicker() {
        val c = Calendar.getInstance()
        try {
            val parsed = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).parse(startDate)
            if (parsed != null) c.time = parsed
        } catch (_: Exception) {}

        DatePickerDialog(
            context,
            { _, year, month, dayOfMonth ->
                val selectedCal = Calendar.getInstance().apply {
                    set(year, month, dayOfMonth)
                }
                startDate = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(selectedCal.time)
                viewModel.clearErrors()
            },
            c.get(Calendar.YEAR),
            c.get(Calendar.MONTH),
            c.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    fun showEndDatePicker() {
        val c = Calendar.getInstance()
        try {
            val parsed = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).parse(endDate)
            if (parsed != null) c.time = parsed
        } catch (_: Exception) {}

        DatePickerDialog(
            context,
            { _, year, month, dayOfMonth ->
                val selectedCal = Calendar.getInstance().apply {
                    set(year, month, dayOfMonth)
                }
                endDate = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(selectedCal.time)
                viewModel.clearErrors()
            },
            c.get(Calendar.YEAR),
            c.get(Calendar.MONTH),
            c.get(Calendar.DAY_OF_MONTH)
        ).show()
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
                        text = "Registrar Medicamento",
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
            // Header explicativo
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
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(32.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "Ingresa los datos del medicamento y programa tus horarios para recibir recordatorios.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }

            // Mensaje de Error (Criterio 8: Se muestra un mensaje cuando el registro falla)
            AnimatedVisibility(visible = uiState is AddMedicationUiState.Error) {
                val errorState = uiState as? AddMedicationUiState.Error
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
                            text = errorState?.message ?: "Ocurrió un error al registrar el medicamento",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // SECCIÓN 1: INFORMACIÓN BÁSICA
            Text(
                text = "Información del Medicamento",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )

            // Nombre del medicamento (Criterio 2)
            OutlinedTextField(
                value = name,
                onValueChange = {
                    name = it
                    viewModel.clearErrors()
                },
                label = { Text("Nombre del medicamento *") },
                placeholder = { Text("Ej. Paracetamol, Amoxicilina, Omeprazol") },
                isError = formErrors.nameError != null,
                supportingText = formErrors.nameError?.let { { Text(it, color = MaterialTheme.colorScheme.error) } },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(12.dp)
            )

            // Dosis y Presentación
            OutlinedTextField(
                value = dosage,
                onValueChange = {
                    dosage = it
                    viewModel.clearErrors()
                },
                label = { Text("Dosis / Presentación *") },
                placeholder = { Text("Ej. 500 mg - 1 tableta, 20 mg - 1 cápsula") },
                isError = formErrors.dosageError != null,
                supportingText = formErrors.dosageError?.let { { Text(it, color = MaterialTheme.colorScheme.error) } },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(12.dp)
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

            // SECCIÓN 2: FECHAS (Criterio 3: El usuario puede establecer las fechas correspondientes)
            Text(
                text = "Fechas del Tratamiento",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )

            // Fecha de Inicio
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        showStartDatePicker()
                    }
            ) {
                OutlinedTextField(
                    value = startDate,
                    onValueChange = {},
                    readOnly = true,
                    enabled = false,
                    label = { Text("Fecha de inicio *") },
                    trailingIcon = {
                        IconButton(onClick = { showStartDatePicker() }) {
                            Icon(Icons.Default.DateRange, contentDescription = "Seleccionar fecha de inicio")
                        }
                    },
                    isError = formErrors.startDateError != null,
                    supportingText = formErrors.startDateError?.let { { Text(it, color = MaterialTheme.colorScheme.error) } },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        disabledTextColor = MaterialTheme.colorScheme.onSurface,
                        disabledBorderColor = if (formErrors.startDateError != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outline,
                        disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        disabledTrailingIconColor = MaterialTheme.colorScheme.primary
                    )
                )
            }

            // Switch Tratamiento Continuo
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Tratamiento continuo",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "El medicamento no tiene una fecha de fin establecida",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = isContinuous,
                    onCheckedChange = {
                        isContinuous = it
                        if (it) endDate = ""
                        viewModel.clearErrors()
                    }
                )
            }

            // Fecha de Fin (si no es continuo)
            if (!isContinuous) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {
                            showEndDatePicker()
                        }
                ) {
                    OutlinedTextField(
                        value = endDate,
                        onValueChange = {},
                        readOnly = true,
                        enabled = false,
                        label = { Text("Fecha de finalización *") },
                        placeholder = { Text("Selecciona la fecha límite") },
                        trailingIcon = {
                            IconButton(onClick = { showEndDatePicker() }) {
                                Icon(Icons.Default.DateRange, contentDescription = "Seleccionar fecha final")
                            }
                        },
                        isError = formErrors.endDateError != null,
                        supportingText = formErrors.endDateError?.let { { Text(it, color = MaterialTheme.colorScheme.error) } },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            disabledTextColor = MaterialTheme.colorScheme.onSurface,
                            disabledBorderColor = if (formErrors.endDateError != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outline,
                            disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            disabledTrailingIconColor = MaterialTheme.colorScheme.primary
                        )
                    )
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

            // SECCIÓN 3: HORARIO Y FRECUENCIA (Criterio 4: El usuario puede configurar la información necesaria para el horario)
            Text(
                text = "Horario y Frecuencia",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )

            // Hora de la toma / Primera toma
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
                    label = { Text("Hora de la toma / Primera toma *") },
                    trailingIcon = {
                        IconButton(onClick = { showTimePicker() }) {
                            Icon(Icons.Default.Notifications, contentDescription = "Seleccionar hora")
                        }
                    },
                    isError = formErrors.scheduledTimeError != null,
                    supportingText = formErrors.scheduledTimeError?.let { { Text(it, color = MaterialTheme.colorScheme.error) } },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        disabledTextColor = MaterialTheme.colorScheme.onSurface,
                        disabledBorderColor = if (formErrors.scheduledTimeError != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outline,
                        disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        disabledTrailingIconColor = MaterialTheme.colorScheme.primary
                    )
                )
            }

            // Selector de Frecuencia
            ExposedDropdownMenuBox(
                expanded = expandedFrequency,
                onExpandedChange = { expandedFrequency = !expandedFrequency },
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedTextField(
                    value = selectedFrequency,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Frecuencia de toma *") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedFrequency) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(MenuAnchorType.PrimaryNotEditable),
                    shape = RoundedCornerShape(12.dp)
                )
                ExposedDropdownMenu(
                    expanded = expandedFrequency,
                    onDismissRequest = { expandedFrequency = false }
                ) {
                    frequencyOptions.forEach { option ->
                        DropdownMenuItem(
                            text = { Text(option) },
                            onClick = {
                                selectedFrequency = option
                                expandedFrequency = false
                                viewModel.clearErrors()
                            }
                        )
                    }
                }
            }

            // Instrucciones adicionales (opcional)
            OutlinedTextField(
                value = instructions,
                onValueChange = { instructions = it },
                label = { Text("Instrucciones o notas adicionales (opcional)") },
                placeholder = { Text("Ej. Tomar en ayunas, con alimentos, después de comer") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
                maxLines = 3,
                shape = RoundedCornerShape(12.dp)
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Botón de Enviar (Criterios 5 y 6)
            Button(
                onClick = {
                    viewModel.validateAndRegister(
                        name = name,
                        dosage = dosage,
                        startDate = startDate,
                        endDate = endDate,
                        isContinuous = isContinuous,
                        frequency = selectedFrequency,
                        scheduledTime = scheduledTime,
                        instructions = instructions
                    )
                },
                enabled = uiState !is AddMedicationUiState.Loading,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                shape = RoundedCornerShape(14.dp)
            ) {
                if (uiState is AddMedicationUiState.Loading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.5.dp
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text("Registrando medicamento...")
                } else {
                    Text(
                        text = "Guardar Medicamento",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
        }
    }

    // Modal de Confirmación Exitosa (Criterio 7: Se muestra una confirmación cuando el medicamento se registra correctamente)
    if (uiState is AddMedicationUiState.Success) {
        val successMessage = (uiState as AddMedicationUiState.Success).message
        AlertDialog(
            onDismissRequest = {
                viewModel.resetState()
                onMedicationAdded()
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
                    text = "¡Registro Exitoso!",
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
                        onMedicationAdded()
                    }
                ) {
                    Text("Continuar")
                }
            }
        )
    }
}
