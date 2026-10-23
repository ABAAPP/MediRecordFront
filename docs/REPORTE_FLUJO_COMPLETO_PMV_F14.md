# Reporte de Validación del Flujo Completo del PMV (F-14)

**Proyecto:** MediRecord App (Aplicación Móvil Android + Backend API REST + PostgreSQL)  
**Requerimiento:** F-14 - Prueba del flujo completo del PMV  
**Resultado de la Ejecución E2E:** **APROBADO (10/10 etapas completadas, 0 errores bloqueantes)**

---

## 1. Resumen de la Validación Integrada (End-to-End)

Se ejecutó la validación de extremo a extremo del Producto Mínimo Viable (PMV) mediante el validador [`MvpFullFlowValidator`](../app/src/main/java/com/aba/medirecord/qa/MvpFullFlowValidator.kt) y la suite de integración [`MvpEndToEndFullFlowTest`](../app/src/test/java/com/aba/medirecord/MvpEndToEndFullFlowTest.kt), comprobando la trazabilidad completa entre la interfaz móvil, los servicios REST y la persistencia relacional en PostgreSQL.

| Paso | Código de Etapa | Criterio de Aceptación Validado | Capa / Endpoint Involucrado | Persistencia PostgreSQL | Resultado |
| :---: | :--- | :--- | :--- | :--- | :---: |
| **1** | `REGISTER_USER` | El usuario puede registrarse desde la aplicación. | `POST /api/auth/register` (`AuthRepository.registerUser`) | Inserción en tabla `pacientes` (`email`, `nombre`, `password_hash`). | ✅ EXITOSO |
| **2** | `LOGIN_USER` | El usuario puede iniciar sesión. | `POST /api/auth/login` (`AuthRepository.loginUser` + `JwtAuthInterceptor`) | Consulta de credenciales en `pacientes` y emisión de token `Bearer JWT`. | ✅ EXITOSO |
| **3** | `REGISTER_MEDICATION` | El usuario puede registrar un medicamento. | `POST /api/medications` (`MedicationRepository.registerMedication`) | Inserción en tabla `medicamentos` (`nombre`, `dosis`, `fecha_inicio`, `fecha_fin`). | ✅ EXITOSO |
| **4** | `CONFIGURE_SCHEDULE` | El usuario puede configurar un horario. | `POST /api/schedules` (`MedicationRepository.saveMedicationSchedule`) | Inserción/actualización en tabla `horarios_medicamento` (`hora_toma`, `frecuencia`, `dias`). | ✅ EXITOSO |
| **5** | `POSTGRES_PERSISTENCE` | El medicamento y horario se almacenan correctamente en PostgreSQL. | Verificación de integridad referencial (`medicationId` ↔ `scheduleId`) | Confirmación de fila en `medicamentos` y `horarios_medicamento`. | ✅ EXITOSO |
| **6** | `QUERY_TODAY_MEDICATIONS` | El usuario puede consultar sus medicamentos del día. | `GET /api/medications/today?date=YYYY-MM-DD` (`getTodayMedications`) | Lectura filtrada por `paciente_email` y fecha actual, ordenada por `hora_toma`. | ✅ EXITOSO |
| **7** | `VIEW_REMINDERS` | El usuario puede visualizar sus recordatorios. | `GET /api/reminders` (`getRemindersForUser` + `ReminderNotificationManager`) | Lectura de recordatorios activos del usuario y disparo de notificación en el horario. | ✅ EXITOSO |
| **8** | `MARK_REMINDER_COMPLETED` | El usuario puede marcar un recordatorio como realizado. | `POST /api/reminders/{id}/complete` (`markReminderAsCompleted`) | Envío de confirmación de toma con marca de tiempo (`completedAt`). | ✅ EXITOSO |
| **9** | `BACKEND_STATUS_UPDATED` | El estado de la toma se actualiza en el backend. | Procesamiento transaccional en el servidor | Actualización de columna `realizado = true` y `hora_realizado = '08:32 AM'`. | ✅ EXITOSO |
| **10** | `UPDATED_UI_VERIFICATION` | La aplicación muestra correctamente la información actualizada sin errores bloqueantes. | Sincronización de `HomeViewModel` y `RemindersViewModel` | Re-consulta confirma `isCompleted = true` (**"Realizado"**) con `0` errores bloqueantes. | ✅ EXITOSO |

---

## 2. Evidencia de Trazabilidad de Datos Durante el Flujo E2E

1. **Usuario registrado y autenticado:**
   - Nombre: `Aurora Bautista` (`aurora.e2e@medirecord.com`)
   - Encabezado de autorización activo: `Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...`
2. **Medicamento y Horario almacenados en PostgreSQL:**
   - Medicamento: `Losartán Potásico` (`50 mg - 1 comprimido`)
   - Horario configurado: `08:30 AM` • Frecuencia: `Días específicos de la semana (Lunes, Miércoles, Viernes)`
3. **Actualización de Toma Realizada:**
   - Estado inicial en consulta del día y recordatorios: `Pendiente` (`isCompleted = false`)
   - Acción ejecutada: `markReminderAsCompleted(reminderId = "pg-med-1", completedAt = "08:32 AM")`
   - Estado posterior en Backend PostgreSQL y en la Aplicación Móvil: **`Realizado`** (`isCompleted = true`, `completedAt = "08:32 AM"`)
4. **Errores bloqueantes registrados:** `0` (`blockingErrorsCount = 0`).
