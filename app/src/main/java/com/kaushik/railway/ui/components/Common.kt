package com.kaushik.railway.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kaushik.railway.ui.theme.GlassFill
import com.kaushik.railway.data.isAvailableStatus
import com.kaushik.railway.ui.theme.GlassHighlight
import com.kaushik.railway.ui.theme.GlassStroke
import com.kaushik.railway.ui.theme.Mute
import com.kaushik.railway.ui.theme.Navy
import com.kaushik.railway.ui.theme.NavyDark
import com.kaushik.railway.ui.theme.Orange
import com.kaushik.railway.ui.theme.Rac
import com.kaushik.railway.ui.theme.Success
import com.kaushik.railway.ui.theme.TricolorGreen
import com.kaushik.railway.ui.theme.TricolorSaffron
import com.kaushik.railway.ui.theme.Waitlist

private val GlassShape = RoundedCornerShape(24.dp)

@Composable
fun TricolorStrip() {
    Row(Modifier.fillMaxWidth().height(2.dp)) {
        Box(Modifier.weight(1f).height(2.dp).background(TricolorSaffron.copy(alpha = 0.85f)))
        Box(Modifier.weight(1f).height(2.dp).background(Color.White.copy(alpha = 0.7f)))
        Box(Modifier.weight(1f).height(2.dp).background(TricolorGreen.copy(alpha = 0.85f)))
    }
}

@Composable
fun glassModifier(modifier: Modifier = Modifier): Modifier =
    modifier
        .clip(GlassShape)
        .background(MaterialTheme.colorScheme.surface)
        .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f), GlassShape)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RailTopBar(title: String, onBack: (() -> Unit)? = null) {
    TopAppBar(
        title = { Text(title, style = MaterialTheme.typography.titleLarge) },
        navigationIcon = {
            if (onBack != null) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.background,
            titleContentColor = MaterialTheme.colorScheme.onBackground,
            navigationIconContentColor = MaterialTheme.colorScheme.onBackground
        )
    )
}

@Composable
fun OrangeButton(text: String, modifier: Modifier = Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.fillMaxWidth().heightIn(min = 54.dp),
        shape = RoundedCornerShape(18.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
        )
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
fun RailCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(glassModifier(modifier.fillMaxWidth()).padding(16.dp)) { content() }
}

@Composable
fun StatusChip(status: String) {
    val color = when {
        isAvailableStatus(status) || status.trim().equals("CONFIRMED", true) -> Success
        status.contains("RAC", true) -> Rac
        status.contains("CANCEL", true) || status.contains("WL", true) || status.contains("WAIT", true) || status.contains("AVAILABLE", true) || status.contains("AVBL", true) -> Waitlist
        else -> Mute
    }
    Text(
        text = status,
        color = color,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier
            .background(color.copy(alpha = 0.12f), RoundedCornerShape(50))
            .padding(horizontal = 12.dp, vertical = 6.dp)
    )
}

@Composable
fun LabeledField(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    isPassword: Boolean = false,
    keyboardType: KeyboardType = KeyboardType.Text,
    onValue: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValue,
        label = { Text(label) },
        modifier = modifier.fillMaxWidth(),
        singleLine = true,
        visualTransformation = if (isPassword) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = KeyboardOptions(
            keyboardType = if (isPassword) KeyboardType.Password else keyboardType
        ),
        shape = RoundedCornerShape(16.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
            focusedBorderColor = Navy,
            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
        )
    )
}

@Composable
fun KeyValue(key: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(key, color = Mute, fontSize = 13.sp, modifier = Modifier.width(120.dp))
        Text(value, fontWeight = FontWeight.Medium, fontSize = 13.sp, color = NavyDark)
    }
}

@Composable
fun SectionTitle(text: String) {
    Text(text, fontWeight = FontWeight.Bold, color = Navy, fontSize = 16.sp)
    Spacer(Modifier.height(8.dp))
}

@Composable
fun LoadingSkeleton(lines: Int = 3, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth()) {
        repeat(lines) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .padding(bottom = 8.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            )
        }
    }
}
