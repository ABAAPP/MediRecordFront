package com.aba.medirecord.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

data class PasswordCriterion(
    val label: String,
    val isMet: Boolean,
)

object PasswordValidator {
    fun checkCriteria(password: String): List<PasswordCriterion> {
        val trimmed = password.trim()
        return listOf(
            PasswordCriterion(
                label = "Mínimo 6 caracteres",
                isMet = trimmed.length >= 6
            ),
            PasswordCriterion(
                label = "Al menos una letra mayúscula (A-Z)",
                isMet = trimmed.any { it.isUpperCase() }
            ),
            PasswordCriterion(
                label = "Al menos una letra minúscula (a-z)",
                isMet = trimmed.any { it.isLowerCase() }
            ),
            PasswordCriterion(
                label = "Al menos un número (0-9)",
                isMet = trimmed.any { it.isDigit() }
            ),
            PasswordCriterion(
                label = "Al menos un carácter especial (!@#$%...)",
                isMet = trimmed.any { !it.isLetterOrDigit() }
            )
        )
    }
}

@Composable
fun PasswordRequirementsChecklist(
    password: String,
    modifier: Modifier = Modifier,
    title: String = "Requisitos de la contraseña:"
) {
    val criteria = PasswordValidator.checkCriteria(password)

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            )

            criteria.forEach { item ->
                val iconColor by animateColorAsState(
                    targetValue = if (item.isMet) Color(0xFF2E7D32) else MaterialTheme.colorScheme.outline,
                    label = "iconColor"
                )
                val textColor by animateColorAsState(
                    targetValue = if (item.isMet) Color(0xFF1B5E20) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    label = "textColor"
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (item.isMet) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                        contentDescription = if (item.isMet) "Cumplido" else "Pendiente",
                        tint = iconColor,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = item.label,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontSize = 12.sp,
                            fontWeight = if (item.isMet) FontWeight.Medium else FontWeight.Normal,
                            color = textColor
                        )
                    )
                }
            }
        }
    }
}
