# Reporte de Pruebas Funcionales y Gestión de Defectos del PMV (F-13)

**Proyecto:** MediRecord App (Frontend Móvil Android)  
**Requerimiento:** F-13 - Pruebas funcionales de la aplicación móvil  
**Estado General del PMV:** **APROBADO (100% de criterios cumplidos)**

---

## 1. Matriz de Ejecución de Pruebas Funcionales

| ID Caso | Funcionalidad Probada | Escenario | Datos de Prueba | Resultado Esperado | Resultado Obtenido | Estado |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **CP-01** | Registro de usuario (F-02) | Exitoso | Nombre: `Aurora Bautista`, Correo: `aurora.pmv@medirecord.com`, Pass: `passwordSegura123` | El sistema valida los campos, envía el registro a `POST /api/auth/register` y confirma la creación de la cuenta. | Cuenta registrada y mensaje de confirmación mostrado. | ✅ APROBADO |
| **CP-02** | Registro de usuario (F-02 / F-10) | Datos inválidos | Nombre: `"A"`, Correo: `"correo_invalido.com"`, Pass: `"123"` | El sistema bloquea el envío y muestra mensajes de error por nombre corto, formato de correo inválido y contraseña menor a 6 caracteres. | Mensajes claros mostrados en cada campo sin enviar petición. | ✅ APROBADO |
| **CP-03** | Inicio de sesión (F-03 / F-11) | Exitoso | Correo: `aurora.pmv@medirecord.com`, Pass: `passwordSegura123` | Autenticación en `POST /api/auth/login`, almacenamiento de token JWT (`Bearer`) y navegación a `HomeScreen`. | Token JWT almacenado en `TokenManager` y acceso concedido. | ✅ APROBADO |
| **CP-04** | Inicio de sesión (F-03 / F-10) | Datos inválidos / Error 401 | Correo: `"sin-arroba"`, Pass: `""` / Credenciales incorrectas | Validación previa de formato y mensaje amigable ante error `401` sin exponer códigos técnicos. | Mensaje comprensible mostrado sin bloqueo de la app. | ✅ APROBADO |
| **CP-05** | Registro de medicamentos (F-05) | Exitoso | Nombre: `Naproxeno`, Dosis: `500 mg - 1 tableta`, Inicio: `22/10/2026`, Fin: `30/10/2026`, Hora: `08:00 AM` | Se valida la información, se envía a `POST /api/medications` y se muestra diálogo de confirmación. | Medicamento registrado y sincronizado en la agenda. | ✅ APROBADO |
| **CP-06** | Registro de medicamentos (F-05 / F-10) | Datos inválidos | Inicio: `25/10/2026`, Fin: `20/10/2026` (fecha fin menor a inicio) | El sistema detecta la incoherencia de fechas e informa al usuario antes de guardar. | Error mostrado: *"La fecha de finalización no puede ser anterior a la de inicio"*. | ✅ APROBADO |
| **CP-07** | Configuración de horarios (F-06) | Exitoso | Medicamento: `Paracetamol`, Hora: `08:30 AM`, Frecuencia: `Días específicos`, Días: `Lunes, Miércoles, Viernes` | Se guarda el horario en `POST /api/schedules`, se programa la notificación y se muestra confirmación. | Horario actualizado y notificación programada. | ✅ APROBADO |
| **CP-08** | Configuración de horarios (F-06 / F-10) | Datos inválidos | Frecuencia: `Días específicos de la semana`, Días seleccionados: `ninguno` | Se impide el guardado indicando que debe seleccionarse al menos un día. | Error mostrado: *"Selecciona al menos un día de la semana para esta frecuencia"*. | ✅ APROBADO |
| **CP-09** | Visualización de recordatorios (F-07) | Exitoso | Usuario autenticado: `aurora.pmv@medirecord.com` | Se obtienen recordatorios de `GET /api/reminders`, filtrados por usuario, ordenados por hora (`07:30 AM` → `09:00 PM`) y distinguiendo pendientes vs. realizados. | Lista ordenada cronológicamente con distinción visual clara. | ✅ APROBADO |
| **CP-10** | Consulta de medicamentos del día (F-09) | Exitoso y Caso Vacío | Fecha actual (`yyyy-MM-dd`), Usuario: `aurora.pmv@medirecord.com` | Se consultan las tomas del día actual ordenadas por horario; si no existen tomas, se muestra `EmptyMedicationsView`. | Tomas del día mostradas en orden y vista vacía validada. | ✅ APROBADO |
| **CP-11** | Marcar como realizado (F-08) | Exitoso e Inválido | Recordatorio pendiente `rem-200`, Hora: `09:05 AM` | Envía `POST /api/reminders/{id}/complete`, cambia el estado visual a **"Realizado"** y conserva el cambio tras recargar. | Estado actualizado a **"Realizado"** y persistente tras recarga. | ✅ APROBADO |
| **CP-12** | Notificaciones de recordatorios (F-12) | Exitoso e Inválido | Medicamento: `Omeprazol 20 mg`, Horario: `09:00 AM` | Canal configurado, envío y recepción comprobados con datos del medicamento y horario configurado. | Envío y recepción verificados en `ReminderNotificationManager`. | ✅ APROBADO |

---

## 2. Documentación de Errores Encontrados y Re-pruebas (Criterios 10 y 11)

Durante la ejecución de las pruebas funcionales con datos límite e inválidos se identificaron, documentaron, corrigieron y volvieron a probar los siguientes defectos:

### DEF-01: Validación de formato de hora permitía valores numéricos fuera de rango horario
- **Módulo afectado:** `ValidationUtils.isValidTimeFormat` y `ScheduleConfigViewModel.validateScheduleFields`.
- **Descripción del error encontrado:** La expresión regular inicial validaba únicamente que existieran 1 o 2 dígitos para la hora y 2 dígitos para los minutos (`^\d{1,2}:\d{2}`), permitiendo ingresar horas inexistentes como `"15:00 AM"`, `"00:30 PM"` o minutos fuera de rango como `"10:75 AM"`.
- **Corrección implementada:** Se actualizó `ValidationUtils.isValidTimeFormat` para verificar explícitamente que los minutos estén en el rango `0..59` y que las horas estén en `1..12` cuando se especifica `AM/PM` (o `0..23` en formato de 24 horas), integrando además esta validación en `ScheduleConfigViewModel`.
- **Resultado de la Re-prueba (Re-test):** Ejecutado en `MvpFunctionalTestSuite.testFunctionalMarkCompletedAndRetestFixedDefects` → **APROBADO**.

### DEF-02: Sensibilidad a mayúsculas y espacios en el correo del usuario autenticado al filtrar recordatorios
- **Módulo afectado:** `AuthRepository.saveSession` y `MedicationRepository.registerMedication`.
- **Descripción del error encontrado:** Si el usuario ingresaba su correo con espacios laterales o letras mayúsculas al autenticarse (ej. `"  AURORA.PMV@MediRecord.com "`), el almacenamiento sin normalizar podía generar discrepancias al asociar nuevos medicamentos al usuario.
- **Corrección implementada:** Se normalizó el correo electrónico (`email.trim().lowercase()`) al guardar la sesión en `AuthRepository.saveSession` y al registrar medicamentos en `MedicationRepository.registerMedication`.
- **Resultado de la Re-prueba (Re-test):** Ejecutado en `MvpFunctionalTestSuite.testFunctionalMarkCompletedAndRetestFixedDefects` → **APROBADO**.

### DEF-03: Actualización de marca de tiempo (`completedAt`) al confirmar nuevamente una toma realizada
- **Módulo afectado:** `MedicationRepository.applyPersistedCompletionToReminder` y `applyPersistedCompletionToDose`.
- **Descripción del error encontrado:** Cuando un recordatorio tenía un `completedAt` previo y se volvía a registrar su confirmación con una nueva hora, prevalecía el valor antiguo en lugar del más reciente almacenado en `completedRemindersRegistry`.
- **Corrección implementada:** Se priorizó el valor actualizado de `completedRemindersRegistry` al sincronizar el estado de los recordatorios y tomas del día.
- **Resultado de la Re-prueba (Re-test):** Ejecutado en `MvpFunctionalTestSuite.testFunctionalMarkCompletedAndRetestFixedDefects` → **APROBADO**.
