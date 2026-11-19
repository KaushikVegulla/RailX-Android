package com.kaushik.railway.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kaushik.railway.AppViewModel
import com.kaushik.railway.data.RefundPolicy
import com.kaushik.railway.data.TdrCoach
import com.kaushik.railway.ui.components.KeyValue
import com.kaushik.railway.ui.components.OrangeButton
import com.kaushik.railway.ui.components.RailCard
import com.kaushik.railway.ui.components.RailTopBar
import com.kaushik.railway.ui.components.StatusChip
import com.kaushik.railway.ui.theme.Navy
import com.kaushik.railway.ui.theme.Orange
import kotlinx.coroutines.delay
import java.time.Duration
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

private fun durationText(minutes: Long): String {
    val m = kotlin.math.abs(minutes)
    return when {
        m >= 24 * 60 -> "${m / (24 * 60)}d ${(m % (24 * 60)) / 60}h"
        m >= 60 -> "${m / 60}h ${m % 60}m"
        else -> "${m}m"
    }
}

/**
 * Trip Day companion: everything about one booked journey on a single
 * screen — countdown to departure, when to reach the station, the
 * passengers, live running status, and the delay-refund coach that
 * explains the full-refund TDR rule when a train runs very late.
 */
@Composable
fun TripDayScreen(vm: AppViewModel, onBack: () -> Unit) {
    val booking = vm.bookings.firstOrNull { it.pnr == vm.selectedTripPnr }
        ?: vm.lastBooking?.takeIf { it.pnr == vm.selectedTripPnr }

    var now by remember { mutableStateOf(LocalDateTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000)
            now = LocalDateTime.now()
        }
    }
    LaunchedEffect(booking?.pnr) {
        vm.runningStops.clear()
        vm.runningNote = ""
        vm.runningError = null
    }

    Scaffold(topBar = { RailTopBar("Trip Day", onBack) }) { pad ->
        Column(
            Modifier.fillMaxSize().padding(pad).background(Color.Transparent)
                .verticalScroll(rememberScrollState()).padding(16.dp)
        ) {
            if (booking == null) {
                RailCard {
                    Column {
                        Text("Journey not found", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Open Trip Day from one of your journeys in My journeys.",
                            modifier = Modifier.padding(top = 8.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                return@Column
            }

            val departure = RefundPolicy.departureOf(booking.date, booking.departTime)
            val timeFmt = DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH)

            RailCard {
                Column {
                    Text(
                        "${booking.train.number} · ${booking.train.name}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        "${booking.fromName} → ${booking.toName}",
                        modifier = Modifier.padding(top = 6.dp),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(Modifier.height(10.dp))
                    KeyValue("Date", booking.date)
                    KeyValue("Class", "${booking.travelClass.code} · ${booking.status}")
                    KeyValue("PNR", booking.pnr)
                    if (departure != null) {
                        val mins = Duration.between(now, departure).toMinutes()
                        KeyValue(
                            "Countdown",
                            if (mins >= 0) "Departs in ${durationText(mins)}" else "Departed ${durationText(mins)} ago"
                        )
                        KeyValue(
                            "Reach station by",
                            departure.minusMinutes(45).format(timeFmt) +
                                " (${booking.train.depart} departure)"
                        )
                    }
                }
            }

            Spacer(Modifier.height(14.dp))
            RailCard {
                Column {
                    Text("Passengers", fontWeight = FontWeight.Bold, color = Navy, fontSize = 16.sp)
                    Spacer(Modifier.height(8.dp))
                    booking.passengers.forEach { p ->
                        KeyValue(
                            p.name.ifBlank { "Passenger" },
                            listOf(p.age, p.gender, p.berth).filter { it.isNotBlank() }.joinToString(" · ")
                        )
                    }
                }
            }

            Spacer(Modifier.height(14.dp))
            RailCard {
                Column {
                    Text("Live running status", fontWeight = FontWeight.Bold, color = Navy, fontSize = 16.sp)
                    Spacer(Modifier.height(8.dp))
                    OrangeButton(
                        "CHECK LIVE STATUS",
                        enabled = !vm.runningLoading
                    ) { vm.loadRunning(booking.train.number) }
                    if (vm.runningLoading) {
                        Text(
                            "Fetching live status…",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                    vm.runningError?.let {
                        Text(it, color = MaterialTheme.colorScheme.error, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp))
                    }
                    if (vm.runningNote.isNotBlank()) {
                        Text(vm.runningNote, fontWeight = FontWeight.Bold, color = Navy, modifier = Modifier.padding(top = 8.dp))
                    }
                    vm.runningStops.forEach { stop ->
                        Row(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                            Column(Modifier.weight(1f)) {
                                Text("${stop.station} (${stop.code})", fontWeight = FontWeight.SemiBold, color = Navy)
                                Text(
                                    "Arr ${stop.schArr}   Dep ${stop.schDep}",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (stop.delayMin != 0) {
                                    Text("Delay: ${stop.delayMin} min", fontSize = 12.sp, color = Orange)
                                }
                            }
                            StatusChip(stop.status)
                        }
                    }
                }
            }

            // Delay-refund coach. The delay at the boarding station is
            // what the rule looks at; fall back to the worst reported.
            val delayMin = vm.runningStops.firstOrNull { it.code == booking.train.fromCode }?.delayMin
                ?: vm.runningStops.maxOfOrNull { it.delayMin }
            val fullRefund = delayMin != null && TdrCoach.isFullRefundDelay(delayMin)

            Spacer(Modifier.height(14.dp))
            RailCard {
                Column {
                    Text(
                        "Delay-refund coach",
                        fontWeight = FontWeight.Bold,
                        color = Navy,
                        fontSize = 16.sp
                    )
                    Spacer(Modifier.height(8.dp))
                    if (fullRefund) {
                        Text(
                            "This train is running ${durationText(delayMin!!.toLong())} late. " +
                                "If you don't travel, you are owed a FULL refund of the fare.",
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.error
                        )
                        Spacer(Modifier.height(8.dp))
                    } else {
                        Text(
                            "Train running 3 hours late or more? If you don't travel, the fare comes back in full. Here's how the claim works:",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Spacer(Modifier.height(8.dp))
                    }
                    TdrCoach.steps.forEachIndexed { index, step ->
                        Text(
                            "${index + 1}. $step",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                    Text(
                        TdrCoach.NOTE,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 10.dp)
                    )
                }
            }
            Spacer(Modifier.height(96.dp))
        }
    }
}
