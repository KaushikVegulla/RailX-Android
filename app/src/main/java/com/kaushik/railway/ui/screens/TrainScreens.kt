package com.kaushik.railway.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kaushik.railway.AppViewModel
import com.kaushik.railway.data.*
import com.kaushik.railway.ui.components.OrangeButton
import com.kaushik.railway.ui.components.SelectionExpiredScreen
import com.kaushik.railway.ui.components.RailCard
import com.kaushik.railway.ui.components.RailTopBar
import com.kaushik.railway.ui.components.GlassSwitch
import androidx.compose.ui.draw.alpha
import com.kaushik.railway.ui.components.StatusChip
import com.kaushik.railway.ui.components.SeatMapGrid
import com.kaushik.railway.ui.components.demoSeatMap
import com.kaushik.railway.ui.theme.Navy
import com.kaushik.railway.ui.theme.Orange

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrainListScreen(vm: AppViewModel, onBack: () -> Unit, onSelect: (Train) -> Unit) {
    val trains = vm.trains.toList()
    var sortName by rememberSaveable(vm.fromCode, vm.toCode, vm.journeyDate) { mutableStateOf(TrainSort.Recommended.name) }
    var windowName by rememberSaveable(vm.fromCode, vm.toCode, vm.journeyDate) { mutableStateOf(DepartureWindow.Any.name) }
    var classCode by rememberSaveable(vm.fromCode, vm.toCode, vm.journeyDate) { mutableStateOf("") }
    var availableOnly by rememberSaveable(vm.fromCode, vm.toCode, vm.journeyDate) { mutableStateOf(false) }
    var showFilters by rememberSaveable { mutableStateOf(false) }
    val classes = remember(trains) { trains.flatMap { it.classes }.map { it.code }.distinct().sorted() }
    val hasFares = trains.any { train -> train.classes.any { it.fare > 0 } }
    val hasAvailability = trains.any { train -> train.classes.any { it.status.isNotBlank() && it.status != "N/A" } }
    val filters = TrainFilters(TrainSort.valueOf(sortName), DepartureWindow.valueOf(windowName), classCode, availableOnly)
    val visible = remember(trains, filters) { filterTrains(trains, filters) }
    val resultScroll = rememberLazyListState()
    LaunchedEffect(filters) { resultScroll.scrollToItem(0) }
    val activeCount = listOf(windowName != DepartureWindow.Any.name, classCode.isNotBlank(), availableOnly).count { it }
    val reset = { sortName = TrainSort.Recommended.name; windowName = DepartureWindow.Any.name; classCode = ""; availableOnly = false }
    Scaffold(topBar = { RailTopBar("${vm.fromCode} → ${vm.toCode}", onBack) }) { pad ->
        Column(Modifier.fillMaxSize().padding(pad)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(if (vm.searchLoading) "Finding your journey" else "${visible.size} ${if (visible.size == 1) "train" else "trains"}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text("${vm.journeyDate} · ${vm.quotaCode()}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                FilledTonalButton(onClick = { showFilters = true }) {
                    Icon(Icons.Default.Tune, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(if (activeCount == 0) "Sort & filter" else "Filters · $activeCount")
                }
            }
            if (vm.isOfflineHint) {
                Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = RoundedCornerShape(14.dp), modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)) {
                    Text("Demo results · live search is unavailable or returned no trains.", modifier = Modifier.padding(12.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSecondaryContainer)
                }
            }
            if (vm.searchLoading) LinearProgressIndicator(Modifier.fillMaxWidth().padding(horizontal = 20.dp))
            vm.searchError?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(20.dp)) }
            LazyColumn(Modifier.weight(1f), state = resultScroll, contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                if (!vm.searchLoading && visible.isEmpty()) {
                    item {
                        RailCard {
                            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("No matching trains", style = MaterialTheme.typography.titleMedium)
                                Text("Try a different departure time or clear your filters.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 12.dp))
                                TextButton(onClick = reset) { Text("Reset filters") }
                            }
                        }
                    }
                }
                items(visible, key = { "${it.number}:${it.depart}:${it.fromCode}:${it.toCode}" }) { train ->
                    TrainResultCard(train) { onSelect(train) }
                }
            }
        }
    }
    if (showFilters) {
        ModalBottomSheet(onDismissRequest = { showFilters = false }) {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Find your train", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
                    TextButton(onClick = reset) { Text("Reset") }
                }
                Text("Sort by", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 12.dp))
                TrainSort.entries.forEach { option ->
                    val enabled = option != TrainSort.Fare || hasFares
                    Row(Modifier.fillMaxWidth().clickable(enabled = enabled) { sortName = option.name }.padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = sortName == option.name, onClick = { sortName = option.name }, enabled = enabled)
                        Text(option.label, color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Text("Departure time", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 12.dp))
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DepartureWindow.entries.forEach { option -> FilterChip(selected = windowName == option.name, onClick = { windowName = option.name }, label = { Text(option.label) }) }
                }
                Text("Travel class", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 12.dp))
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = classCode.isBlank(), onClick = { classCode = "" }, label = { Text("Any class") })
                    classes.forEach { code -> FilterChip(selected = classCode == code, onClick = { classCode = code }, label = { Text(code) }) }
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Available seats only", style = MaterialTheme.typography.titleSmall)
                        Text("Uses availability supplied in these results", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    GlassSwitch(checked = availableOnly, onCheckedChange = { if (hasAvailability) availableOnly = it }, modifier = Modifier.alpha(if (hasAvailability) 1f else 0.45f))
                }
                if (!hasFares || classes.isEmpty()) Text("Some live searches omit class, fare and seat details. Open a train to check those details; missing values are never treated as available or free.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 12.dp))
                OrangeButton("Show ${visible.size} ${if (visible.size == 1) "train" else "trains"}") { showFilters = false }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun TrainResultCard(train: Train, onClick: () -> Unit) {
    RailCard(Modifier.clickable(onClick = onClick)) {
        Column {
            Text(train.number, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium)
            Text(train.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Row(Modifier.fillMaxWidth().padding(vertical = 18.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(train.depart.ifBlank { "—" }, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text(train.fromCode, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(train.duration.ifBlank { "Duration unavailable" }, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        HorizontalDivider(Modifier.weight(1f))
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.outline)
                    }
                }
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                    Text(train.arrive.ifBlank { "—" }, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text(train.toCode, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (train.classes.isNotEmpty()) {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    train.classes.forEach { cls ->
                        Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(12.dp)) {
                            Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                                Text(cls.code + if (cls.fare > 0) " · ₹${cls.fare}" else " · Fare pending", style = MaterialTheme.typography.labelLarge)
                                if (cls.status.isNotBlank()) Text(cls.status, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(train.days, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                Text("Check seats →", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
fun AvailabilityScreen(vm: AppViewModel, onBack: () -> Unit, onVacancy: (TrainClassAvail) -> Unit) {
    val train = vm.selectedTrain
    if (train == null) {
        SelectionExpiredScreen("Seat availability", onBack)
        return
    }
    LaunchedEffect(train.number, vm.journeyDate, vm.selectedClass, vm.selectedQuota) { vm.loadAvailability(train) }
    Scaffold(topBar = { RailTopBar(train.name, onBack) }) { pad ->
        LazyColumn(Modifier.fillMaxSize().padding(pad), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Text("${train.number} · ${train.fromCode} → ${train.toCode}", style = MaterialTheme.typography.titleMedium)
                Text("${vm.journeyDate} · ${vm.quotaCode()}", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                Text(if (vm.isOfflineHint) "Demo availability" else "Seat availability · RailRadar", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 6.dp))
            }
            if (vm.availLoading) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            vm.availError?.let { message -> item { Text(message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) } }
            items(vm.classRows, key = { it.code }) { cls ->
                RailCard(Modifier.clickable { onVacancy(cls) }) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(cls.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            Text(cls.code, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelMedium)
                            Spacer(Modifier.height(8.dp))
                            StatusChip(cls.status)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(if (cls.fare > 0) "₹${cls.fare}" else "Fare pending", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                            Text("per adult", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("View seats →", modifier = Modifier.padding(top = 10.dp), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
            if (!vm.availLoading && vm.classRows.isEmpty()) item {
                Text("No class availability returned. Try again in a moment.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                TextButton(onClick = { vm.loadAvailability(train) }) { Text("Retry") }
            }
        }
    }
}

@Composable
fun VacancyChartScreen(vm: AppViewModel, onBack: () -> Unit, onBook: () -> Unit) {
    val train = vm.selectedTrain
    val cls = vm.selectedTravelClass
    if (train == null || cls == null) {
        SelectionExpiredScreen("Vacancy chart", onBack)
        return
    }
    LaunchedEffect(train.number, cls.code, vm.journeyDate, vm.selectedQuota) { vm.loadVacancyChart(cls) }
    Scaffold(topBar = { RailTopBar("Vacancy chart • ${cls.code}", onBack) }) { pad ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(pad)
                .background(Color.Transparent)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            Text("${train.number} ${train.name}", fontWeight = FontWeight.Bold, color = Navy)
            Text(
                "Quota ${vm.quotaCode()}  •  fare ₹${vm.vacancy?.fare ?: cls.fare}",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 13.sp
            )
            Spacer(Modifier.height(12.dp))
            val days = vm.vacancy?.days.orEmpty()
            if (vm.vacancyLoading) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
            }
            vm.vacancyError?.let { message ->
                Text(message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = { vm.loadVacancyChart(cls) }) { Text("Retry availability") }
            }
            if (!vm.vacancyLoading && vm.vacancyError == null && days.isEmpty()) {
                Text("No availability calendar returned for this class.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (days.isNotEmpty()) {
                days.forEach { day ->
                    RailCard(Modifier.padding(bottom = 8.dp)) {
                        Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(day.date, fontWeight = FontWeight.Bold, color = Navy)
                                Text(day.prediction, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            StatusChip(day.text.ifBlank { day.status })
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            RailCard {
                Column {
                    val seats = remember(cls.code) { demoSeatMap(cls.code) }
                    SeatMapGrid(
                        seats = seats,
                        selectedIds = vm.selectedSeatIds.toSet(),
                        onToggle = { cell ->
                            if (cell.status.name != "Booked") {
                                vm.toggleSeat(cell.id, "${cell.id}/${cell.berth}")
                            }
                        }
                    )
                    if (vm.selectedSeatLabels.isNotBlank()) {
                        Text(
                            "Selected: ${vm.selectedSeatLabels}",
                            fontSize = 12.sp,
                            color = Navy,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    } else {
                        Text(
                            "Select up to ${vm.passengers.size} seat(s) (demo map)",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            OrangeButton("Continue to passengers", enabled = cls.fare > 0 && !vm.vacancyLoading) { onBook() }
            if (cls.fare <= 0) Text("A fare is required to continue. Retry availability.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            Text(
                "RailRadar does not issue IRCTC tickets. Booking here saves a local e-ticket using live fare/availability.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}
