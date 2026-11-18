package com.kaushik.railway.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.ConfirmationNumber
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material.icons.filled.Train
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kaushik.railway.AppViewModel
import com.kaushik.railway.data.MockData
import com.kaushik.railway.ui.components.OrangeButton
import com.kaushik.railway.ui.components.RailCard
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

private val journeyDateFormat = DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.ENGLISH)

@Composable
fun HomeScreen(
    vm: AppViewModel,
    onSearch: () -> Unit,
    onPnr: () -> Unit,
    onRunning: () -> Unit,
    onBookings: () -> Unit,
    onNeedProfile: () -> Unit,
    onNeedAadhaar: () -> Unit
) {
    val focusManager = LocalFocusManager.current
    var stationReset by rememberSaveable { mutableStateOf(0) }
    var dateOpen by rememberSaveable { mutableStateOf(false) }
    var searchError by rememberSaveable { mutableStateOf<String?>(null) }
    var fromEdited by rememberSaveable(vm.fromCode) { mutableStateOf(false) }
    var toEdited by rememberSaveable(vm.toCode) { mutableStateOf(false) }
    val colors = MaterialTheme.colorScheme
    LazyColumn(
        modifier = Modifier.fillMaxSize().statusBarsPadding().imePadding(),
        verticalArrangement = Arrangement.spacedBy(20.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 112.dp)
    ) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("RailX", style = MaterialTheme.typography.titleMedium, color = colors.primary)
                    Spacer(Modifier.height(6.dp))
                    Text("Where to next?", style = MaterialTheme.typography.headlineLarge, color = colors.onBackground)
                    Text(
                        "Welcome, ${vm.userName.trim().substringBefore(' ').ifBlank { "traveller" }}",
                        style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant
                    )
                }
                Box(
                    Modifier.size(50.dp).clip(RoundedCornerShape(15.dp))
                        .background(Brush.linearGradient(listOf(Color(0xFF0A2E63), Color(0xFF0A5BD7), Color(0xFF0E86E8)))),
                    contentAlignment = Alignment.Center
                ) { Icon(Icons.Default.Train, null, tint = Color.White, modifier = Modifier.size(28.dp)) }
            }
        }
        item {
            RailCard {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Find your train", style = MaterialTheme.typography.titleLarge, color = colors.onSurface)
                    StationSearch("From station", "from", vm.fromCode, stationReset, vm,
                        onEditing = { fromEdited = true; searchError = null },
                        onSelect = { vm.fromCode = it; fromEdited = false })
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                        IconButton(
                            onClick = { vm.swapStations(); vm.clearStationSuggestions(); stationReset++; fromEdited = false; toEdited = false; focusManager.clearFocus() },
                            modifier = Modifier.background(colors.primaryContainer, CircleShape)
                        ) { Icon(Icons.Default.SwapVert, "Swap departure and destination", tint = colors.primary) }
                    }
                    StationSearch("To station", "to", vm.toCode, stationReset, vm,
                        onEditing = { toEdited = true; searchError = null },
                        onSelect = { vm.toCode = it; toEdited = false })
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                            .background(colors.surfaceVariant.copy(alpha = 0.6f))
                            .clickable(role = Role.Button) { focusManager.clearFocus(); dateOpen = true }
                            .heightIn(min = 60.dp).padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(Icons.Default.CalendarMonth, null, tint = colors.primary)
                        Column {
                            Text("Journey date", style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant)
                            Text(vm.journeyDate, style = MaterialTheme.typography.titleMedium, color = colors.onSurface)
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Box(Modifier.weight(1f)) {
                            SimpleSelect("Class", vm.selectedClass, listOf("All Classes") + MockData.classes) { vm.selectedClass = it }
                        }
                        Box(Modifier.weight(1f)) {
                            SimpleSelect("Quota", vm.selectedQuota, MockData.quotas) { vm.selectedQuota = it }
                        }
                    }
                    if (vm.supabaseLinked) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { vm.toggleFavouriteRoute() }) {
                                Icon(
                                    if (vm.currentRouteIsFavourite) Icons.Default.Star else Icons.Default.StarBorder,
                                    "Save route as favourite",
                                    tint = if (vm.currentRouteIsFavourite) colors.primary else colors.onSurfaceVariant
                                )
                            }
                            Text(
                                if (vm.currentRouteIsFavourite) "Saved as a favourite route" else "Save this route as a favourite",
                                style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant
                            )
                        }
                    }
                    if (!vm.profileComplete) {
                        Text("Complete your profile to continue.", style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                    } else if (vm.needsTatkalAadhaar()) {
                        Text("Tatkal requires a local Aadhaar or VID link.", style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                    }
                    searchError?.let { Text(it, color = colors.error, style = MaterialTheme.typography.bodySmall) }
                    OrangeButton(if (vm.searchLoading) "Searching…" else "Search trains", enabled = !vm.searchLoading) {
                        val date = runCatching { LocalDate.parse(vm.journeyDate, journeyDateFormat) }.getOrNull()
                        searchError = when {
                            fromEdited || toEdited || vm.fromCode.isBlank() || vm.toCode.isBlank() -> "Choose both stations from the suggestions."
                            vm.fromCode.equals(vm.toCode, ignoreCase = true) -> "Choose different departure and arrival stations."
                            date == null || date.isBefore(LocalDate.now()) -> "Choose today or a future journey date."
                            else -> null
                        }
                        if (searchError == null) {
                            when {
                                !vm.profileComplete -> onNeedProfile()
                                vm.needsTatkalAadhaar() -> onNeedAadhaar()
                                else -> { focusManager.clearFocus(); vm.clearStationSuggestions(); vm.searchTrainsLive(); onSearch() }
                            }
                        }
                    }
                }
            }
        }
        if (vm.favouriteRoutes.isNotEmpty()) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Favourite routes", style = MaterialTheme.typography.titleMedium, color = colors.onBackground)
                    vm.favouriteRoutes.forEach { route ->
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                                .background(colors.surfaceVariant.copy(alpha = 0.6f))
                                .clickable(role = Role.Button) {
                                    vm.fromCode = route.fromCode
                                    vm.toCode = route.toCode
                                    stationReset++
                                    fromEdited = false
                                    toEdited = false
                                }
                                .padding(horizontal = 12.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Default.Star, null, tint = colors.primary, modifier = Modifier.size(18.dp))
                            Text("${route.fromCode} \u2192 ${route.toCode}", style = MaterialTheme.typography.bodyMedium, color = colors.onSurface, modifier = Modifier.weight(1f))
                            TextButton(onClick = { vm.removeFavouriteRoute(route) }) { Text("Remove", color = colors.primary) }
                        }
                    }
                }
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Your travel toolkit", style = MaterialTheme.typography.titleMedium, color = colors.onBackground)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ServiceTile("PNR status", Icons.Default.ConfirmationNumber, Modifier.weight(1f), onPnr)
                    ServiceTile("Live train", Icons.Default.Timeline, Modifier.weight(1f), onRunning)
                    ServiceTile("My trips", Icons.Default.Train, Modifier.weight(1f), onBookings)
                }
            }
        }
        val upcoming = vm.bookings.filter { booking ->
            booking.status != "CANCELLED" && runCatching {
                !LocalDate.parse(booking.date, journeyDateFormat).isBefore(LocalDate.now())
            }.getOrDefault(false)
        }.minByOrNull { LocalDate.parse(it.date, journeyDateFormat) }
        upcoming?.let { booking ->
            item {
                RailCard(Modifier.clickable(role = Role.Button, onClick = onBookings)) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("NEXT JOURNEY · DEMO", style = MaterialTheme.typography.labelSmall, color = colors.primary)
                        Text("${booking.train.fromCode} → ${booking.train.toCode}", style = MaterialTheme.typography.titleLarge, color = colors.onSurface)
                        Text("${booking.date} · ${booking.train.name}", style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
                        Text("View your trips", style = MaterialTheme.typography.labelLarge, color = colors.primary)
                    }
                }
            }
        }
        vm.lastJourney?.takeIf { it.fromCode.isNotBlank() && it.toCode.isNotBlank() }?.let { journey ->
            item {
                RailCard {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Icon(Icons.Default.History, null, tint = colors.primary)
                        Column(Modifier.weight(1f)) {
                            Text("Travel again", style = MaterialTheme.typography.titleMedium, color = colors.onSurface)
                            Text("${journey.fromCode} → ${journey.toCode}", style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
                        }
                        TextButton(onClick = { vm.applyLastJourney(); stationReset++; fromEdited = false; toEdited = false; searchError = null; vm.clearStationSuggestions(); focusManager.clearFocus() }) { Text("Use route") }
                    }
                }
            }
        }
        item {
            Text("Academic demo · Tickets are not valid for travel.", style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
        }
    }
    if (dateOpen) {
        JourneyDateDialog(vm.journeyDate, onDismiss = { dateOpen = false }) {
            vm.journeyDate = it
            dateOpen = false
            searchError = null
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun JourneyDateDialog(current: String, onDismiss: () -> Unit, onSelect: (String) -> Unit) {
    val today = remember { LocalDate.now() }
    val date = remember(current) { runCatching { LocalDate.parse(current, journeyDateFormat) }.getOrDefault(today).coerceAtLeast(today) }
    val todayMillis = remember(today) { today.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli() }
    val state = rememberDatePickerState(
        initialSelectedDateMillis = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        yearRange = today.year..today.year + 1,
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long) = utcTimeMillis >= todayMillis
            override fun isSelectableYear(year: Int) = year >= today.year
        }
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(enabled = state.selectedDateMillis != null, onClick = {
                state.selectedDateMillis?.let { onSelect(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().format(journeyDateFormat)) }
            }) { Text("Choose date") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    ) { DatePicker(state = state) }
}

@Composable
private fun StationSearch(label: String, field: String, code: String, reset: Int, vm: AppViewModel, onEditing: () -> Unit, onSelect: (String) -> Unit) {
    val focusManager = LocalFocusManager.current
    var query by rememberSaveable(code, reset) { mutableStateOf(vm.stationName(code)) }
    val colors = MaterialTheme.colorScheme
    Column {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it; onEditing(); vm.searchStations(it, field) },
            label = { Text(label) },
            modifier = Modifier.fillMaxWidth(), singleLine = true,
            shape = RoundedCornerShape(16.dp)
        )
        if (vm.stationSuggestionField == field && query.length >= 2) {
            vm.stationSuggestions.take(6).forEach { station ->
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                        .clickable(role = Role.Button) {
                            onSelect(station.code)
                            query = "${station.name} (${station.code})"
                            focusManager.clearFocus()
                            vm.clearStationSuggestions()
                        }.heightIn(min = 48.dp).padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(station.name, modifier = Modifier.weight(1f), color = colors.onSurface, style = MaterialTheme.typography.bodyMedium)
                    Text(station.code, color = colors.primary, style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

@Composable
private fun SimpleSelect(label: String, value: String, options: List<String>, onPick: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
            .clickable(role = Role.Button) { open = true }.heightIn(min = 60.dp).padding(12.dp)
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach { option -> DropdownMenuItem(text = { Text(option) }, onClick = { onPick(option); open = false }) }
        }
    }
}

@Composable
private fun ServiceTile(label: String, icon: ImageVector, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier.clip(RoundedCornerShape(22.dp)).background(MaterialTheme.colorScheme.surface)
            .clickable(role = Role.Button, onClick = onClick).padding(horizontal = 8.dp, vertical = 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface)
    }
}
