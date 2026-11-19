package com.kaushik.railway.ui.screens

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kaushik.railway.AppViewModel
import com.kaushik.railway.data.RefundPolicy
import com.kaushik.railway.data.StationDirectory
import com.kaushik.railway.ui.components.KeyValue
import com.kaushik.railway.ui.components.LabeledField
import com.kaushik.railway.ui.components.OrangeButton
import com.kaushik.railway.ui.components.RailCard
import com.kaushik.railway.ui.components.RailTopBar
import com.kaushik.railway.ui.theme.Navy
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val WATCH_CLASSES = listOf("SL", "3A", "2A", "1A", "CC", "2S", "3E", "EC")

/**
 * Train Watch: watch a train, date and class, and RailX re-checks its
 * availability status in the background, notifying when it improves.
 */
@Composable
fun WatchScreen(vm: AppViewModel, onBack: () -> Unit) {
    var trainNo by remember { mutableStateOf("") }
    var trainName by remember { mutableStateOf("") }
    var fromCode by remember { mutableStateOf("") }
    var toCode by remember { mutableStateOf("") }
    var date by remember { mutableStateOf("") }
    var classCode by remember { mutableStateOf("3A") }
    var formError by remember { mutableStateOf<String?>(null) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33) {
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    Scaffold(topBar = { RailTopBar("Train Watch", onBack) }) { pad ->
        Column(
            Modifier.fillMaxSize().padding(pad).background(Color.Transparent)
                .verticalScroll(rememberScrollState()).padding(16.dp)
        ) {
            RailCard {
                Column {
                    Text("Watch a train", fontWeight = FontWeight.Bold, color = Navy, fontSize = 16.sp)
                    Text(
                        "RailX checks the status about every 6 hours and notifies you when it improves, for example WL 14 moving to RAC 2.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                    Spacer(Modifier.height(12.dp))
                    LabeledField(
                        "Train number", trainNo,
                        keyboardType = KeyboardType.Number,
                        onValue = { trainNo = it.filter { c -> c.isDigit() }.take(5) }
                    )
                    Spacer(Modifier.height(10.dp))
                    LabeledField("Train name (optional)", trainName, onValue = { trainName = it })
                    Spacer(Modifier.height(10.dp))
                    Row(Modifier.fillMaxWidth()) {
                        Column(Modifier.weight(1f)) {
                            LabeledField(
                                "From (code)", fromCode,
                                onValue = { fromCode = it.uppercase(Locale.ENGLISH).take(5) }
                            )
                        }
                        Spacer(Modifier.padding(horizontal = 6.dp))
                        Column(Modifier.weight(1f)) {
                            LabeledField(
                                "To (code)", toCode,
                                onValue = { toCode = it.uppercase(Locale.ENGLISH).take(5) }
                            )
                        }
                    }
                    val fromStation = StationDirectory.byCode(fromCode)
                    val toStation = StationDirectory.byCode(toCode)
                    if (fromStation != null || toStation != null) {
                        Text(
                            listOfNotNull(
                                fromStation?.let { "${it.name} (${it.code})" },
                                toStation?.let { "${it.name} (${it.code})" }
                            ).joinToString("  →  "),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 6.dp)
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    LabeledField("Journey date (e.g. 20 Nov 2026)", date, onValue = { date = it })
                    Spacer(Modifier.height(10.dp))
                    Text("Class", style = MaterialTheme.typography.labelMedium)
                    Row(
                        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(top = 6.dp)
                    ) {
                        WATCH_CLASSES.forEach { cls ->
                            FilterChip(
                                selected = classCode == cls,
                                onClick = { classCode = cls },
                                label = { Text(cls) },
                                modifier = Modifier.padding(end = 8.dp)
                            )
                        }
                    }
                    formError?.let {
                        Text(it, color = MaterialTheme.colorScheme.error, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp))
                    }
                    Spacer(Modifier.height(12.dp))
                    OrangeButton("START WATCHING") {
                        formError = when {
                            trainNo.length != 5 -> "Enter the 5-digit train number."
                            fromStation == null -> "From: enter a valid station code, like BBS."
                            toStation == null -> "To: enter a valid station code, like VSKP."
                            RefundPolicy.departureOf(date.trim(), "") == null ->
                                "Date must look like 20 Nov 2026."
                            else -> null
                        }
                        if (formError == null) {
                            vm.addWatch(trainNo, trainName.trim(), fromCode, toCode, date.trim(), classCode)
                            trainNo = ""
                            trainName = ""
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth()) {
                Text(
                    "Your watches",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                TextButton(
                    onClick = { vm.checkWatchesNow() },
                    enabled = !vm.watchChecking && vm.watches.isNotEmpty()
                ) { Text(if (vm.watchChecking) "Checking…" else "Check now") }
            }
            if (vm.watchNote.isNotBlank()) {
                Text(
                    vm.watchNote,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }
            if (vm.watches.isEmpty()) {
                RailCard {
                    Text(
                        "No watches yet. Add one above and RailX will keep an eye on it for you.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            vm.watches.forEach { w ->
                RailCard(Modifier.padding(bottom = 10.dp)) {
                    Column {
                        Text(
                            "${w.trainNumber}${if (w.trainName.isNotBlank()) " · ${w.trainName}" else ""}",
                            fontWeight = FontWeight.SemiBold,
                            color = Navy
                        )
                        Spacer(Modifier.height(6.dp))
                        KeyValue("Route", "${w.fromCode} → ${w.toCode}")
                        KeyValue("Date / Class", "${w.date} · ${w.classCode}")
                        KeyValue(
                            "Status",
                            if (w.lastStatus.isBlank()) "Not checked yet" else w.lastStatus
                        )
                        if (w.lastCheckedAt > 0) {
                            KeyValue(
                                "Last checked",
                                SimpleDateFormat("d MMM, h:mm a", Locale.ENGLISH).format(Date(w.lastCheckedAt))
                            )
                        }
                        TextButton(onClick = { vm.deleteWatch(w) }) {
                            Text("Stop watching", color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
            Text(
                "Checks run on the live train search and follow the same data limits as search. Watches are stored on this device for your account.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp)
            )
            Spacer(Modifier.height(96.dp))
        }
    }
}
