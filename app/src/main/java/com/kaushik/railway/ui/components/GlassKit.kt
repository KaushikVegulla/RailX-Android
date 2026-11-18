package com.kaushik.railway.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * A grouped glass list: small section header above one glass card whose
 * rows are separated by inset hairlines.
 */
@Composable
fun GroupedSection(
    title: String? = null,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(modifier.fillMaxWidth()) {
        if (title != null) {
            Text(
                title,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 20.dp, bottom = 7.dp)
            )
        }
        Column(glassModifier().padding(vertical = 5.dp), content = content)
    }
}

/** Hairline between grouped rows, inset to align with the row text. */
@Composable
fun GroupedDivider(inset: Dp = 58.dp) {
    Box(
        Modifier.padding(start = inset).fillMaxWidth().height(0.6.dp)
            .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
    )
}

/** One row of a grouped list: coloured icon tile, label, optional value, chevron. */
@Composable
fun ListRow(
    label: String,
    icon: ImageVector? = null,
    iconColor: Color = Color(0xFF0A84FF),
    value: String? = null,
    chevron: Boolean = true,
    onClick: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Box(
                Modifier.size(30.dp).clip(RoundedCornerShape(8.dp)).background(iconColor),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, null, tint = Color.White, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.width(12.dp))
        }
        Text(label, fontSize = 16.sp, color = colors.onSurface, modifier = Modifier.weight(1f))
        if (value != null) {
            Text(value, fontSize = 16.sp, color = colors.onSurfaceVariant)
            Spacer(Modifier.width(4.dp))
        }
        if (chevron) {
            Icon(
                Icons.Default.ChevronRight, null,
                tint = colors.onSurfaceVariant.copy(alpha = 0.55f),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

/** Sliding-thumb segmented control with a spring-animated thumb. */
@Composable
fun SegmentedControl(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    val dark = colors.background.luminance() < 0.3f
    var trackWidth by remember { mutableStateOf(0) }
    val density = LocalDensity.current
    val segmentWidth = with(density) { if (trackWidth == 0) 0.dp else (trackWidth / options.size).toDp() }
    val thumbOffset by animateDpAsState(
        targetValue = segmentWidth * selectedIndex,
        animationSpec = spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow),
        label = "Segment thumb"
    )
    Box(
        modifier.fillMaxWidth()
            .clip(RoundedCornerShape(11.dp))
            .background(if (dark) Color.White.copy(alpha = 0.10f) else Color(0xFF767680).copy(alpha = 0.14f))
            .padding(2.dp)
            .onSizeChanged { trackWidth = it.width }
    ) {
        if (trackWidth > 0) {
            Box(
                Modifier.offset(x = thumbOffset).width(segmentWidth).height(30.dp)
                    .shadow(3.dp, RoundedCornerShape(9.dp))
                    .clip(RoundedCornerShape(9.dp))
                    .background(if (dark) Color(0xFF636366) else Color.White)
            )
        }
        Row(Modifier.fillMaxWidth()) {
            options.forEachIndexed { index, label ->
                val selected = index == selectedIndex
                Box(
                    Modifier.weight(1f).height(30.dp)
                        .clip(RoundedCornerShape(9.dp))
                        .clickable { onSelect(index) },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        label,
                        fontSize = 13.sp,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                        color = if (selected) colors.onSurface else colors.onSurfaceVariant
                    )
                }
            }
        }
    }
}

/** Slim track slider with a large white thumb; drag or tap anywhere on the track. */
@Composable
fun GlassSlider(value: Float, onValueChange: (Float) -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    var trackWidth by remember { mutableStateOf(1) }
    fun fractionFor(x: Float): Float = (x / trackWidth).coerceIn(0f, 1f)
    Box(
        modifier.fillMaxWidth().height(34.dp)
            .onSizeChanged { trackWidth = it.width }
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { offset -> onValueChange(fractionFor(offset.x)) },
                    onDrag = { change, _ -> onValueChange(fractionFor(change.position.x)); change.consume() }
                )
            }
            .pointerInput(Unit) {
                detectTapGestures { offset -> onValueChange(fractionFor(offset.x)) }
            },
        contentAlignment = Alignment.CenterStart
    ) {
        Box(
            Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(50))
                .background(colors.surfaceVariant)
        )
        Box(
            Modifier.fillMaxWidth(value.coerceIn(0.001f, 1f)).height(5.dp)
                .clip(RoundedCornerShape(50)).background(colors.primary)
        )
        Box(
            Modifier.offset(x = with(LocalDensity.current) { (trackWidth * value).toDp() } - 14.dp)
                .size(28.dp)
                .shadow(4.dp, CircleShape)
                .clip(CircleShape)
                .background(Color.White)
                .border(0.5.dp, Color.Black.copy(alpha = 0.08f), CircleShape)
        )
    }
}

/** Pill switch with a spring thumb; green when on. */
@Composable
fun GlassSwitch(checked: Boolean, onCheckedChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    val trackColor by animateColorAsState(
        if (checked) Color(0xFF34C759) else MaterialTheme.colorScheme.surfaceVariant,
        label = "Switch track"
    )
    val thumbOffset by animateDpAsState(
        if (checked) 22.dp else 2.dp,
        spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessMedium),
        label = "Switch thumb"
    )
    Box(
        modifier.width(52.dp).height(32.dp)
            .clip(RoundedCornerShape(50))
            .background(trackColor)
            .clickable { onCheckedChange(!checked) },
        contentAlignment = Alignment.CenterStart
    ) {
        Box(
            Modifier.offset(x = thumbOffset).size(28.dp)
                .shadow(3.dp, CircleShape)
                .clip(CircleShape)
                .background(Color.White)
        )
    }
}
