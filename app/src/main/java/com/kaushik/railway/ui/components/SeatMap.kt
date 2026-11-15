package com.kaushik.railway.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kaushik.railway.ui.theme.Navy
import com.kaushik.railway.ui.theme.Orange

enum class SeatStatus { Available, Booked, Selected, Ladies }

data class SeatCell(
    val id: String,
    val berth: String, // LB, MB, UB, SL, SU
    val status: SeatStatus
)

/**
 * Simplified coach seat map (demo).
 * 3-tier layout: Lower / Middle / Upper + side berths.
 */
@Composable
fun SeatMapGrid(
    seats: List<SeatCell>,
    selectedIds: Set<String>,
    onToggle: (SeatCell) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier.fillMaxWidth()) {
        Text("Select berth (demo map)", fontWeight = FontWeight.SemiBold, color = Navy, fontSize = 14.sp)
        Spacer(Modifier.height(8.dp))
        val colors = MaterialTheme.colorScheme
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            LegendDot(colors.primaryContainer, "Free")
            LegendDot(colors.secondary, "Selected ✓")
            LegendDot(colors.surfaceVariant, "Booked ×")
        }
        Spacer(Modifier.height(12.dp))
        seats.chunked(8).forEachIndexed { bay, baySeats ->
            Text("Bay ${bay + 1}", fontSize = 12.sp, color = colors.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 6.dp))
            baySeats.chunked(4).forEach { row ->
                Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { seat ->
                        val selected = seat.id in selectedIds
                        val booked = seat.status == SeatStatus.Booked
                        val status = when {
                            selected -> "Selected"
                            booked -> "Booked"
                            seat.status == SeatStatus.Ladies -> "Ladies preference"
                            else -> "Available"
                        }
                        val background = when {
                            selected -> colors.secondary
                            booked -> colors.surfaceVariant
                            else -> colors.primaryContainer
                        }
                        val foreground = when {
                            selected -> colors.onSecondary
                            booked -> colors.onSurfaceVariant
                            else -> colors.onPrimaryContainer
                        }
                        Box(
                            Modifier.weight(1f).height(64.dp)
                                .clip(RoundedCornerShape(12.dp)).background(background)
                                .border(1.dp, if (selected) colors.secondary else colors.outlineVariant, RoundedCornerShape(12.dp))
                                .selectable(selected = selected, enabled = !booked, role = Role.Checkbox) { onToggle(seat) }
                                .semantics(mergeDescendants = true) {
                                    contentDescription = "Berth ${seat.id}, ${seat.berth}"
                                    stateDescription = status
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(seat.id, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = foreground)
                                Text(seat.berth + when { selected -> " ✓"; booked -> " ×"; else -> "" }, fontSize = 11.sp, color = foreground)
                                if (seat.status == SeatStatus.Ladies) Text("Ladies", fontSize = 10.sp, color = foreground)
                            }
                        }
                    }
                    repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
            Spacer(Modifier.height(4.dp))
        }
    }
}

@Composable
private fun LegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).clip(RoundedCornerShape(2.dp)).background(color))
        Spacer(Modifier.width(4.dp))
        Text(label, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Generate a demo seat map for a coach class */
fun demoSeatMap(coachCode: String, seed: Int = 42): List<SeatCell> {
    val berths = when (coachCode) {
        "SL", "3A", "3E" -> listOf("LB", "MB", "UB", "LB", "MB", "UB", "SL", "SU")
        "2A" -> listOf("LB", "UB", "LB", "UB", "SL", "SU", "LB", "UB")
        "1A" -> listOf("LB", "UB", "LB", "UB", "LB", "UB", "LB", "UB")
        else -> listOf("WS", "MS", "AS", "WS", "MS", "AS", "WS", "MS")
    }
    val rnd = java.util.Random(seed.toLong())
    return (1..24).map { i ->
        val bay = (i - 1) / 8 + 1
        val pos = (i - 1) % 8
        val id = "${coachCode.take(2)}${bay}${('A' + pos)}"
        val status = when {
            rnd.nextFloat() < 0.25f -> SeatStatus.Booked
            rnd.nextFloat() < 0.08f -> SeatStatus.Ladies
            else -> SeatStatus.Available
        }
        SeatCell(id = id, berth = berths[pos % berths.size], status = status)
    }
}
