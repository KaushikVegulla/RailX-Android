package com.kaushik.railway.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ConfirmationNumber
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Luggage
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Pin
import androidx.compose.material.icons.filled.Train
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
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
import com.kaushik.railway.ui.components.KeyValue
import com.kaushik.railway.ui.components.LabeledField
import com.kaushik.railway.ui.components.OrangeButton
import com.kaushik.railway.ui.components.RailCard
import com.kaushik.railway.ui.components.SectionTitle
import com.kaushik.railway.ui.components.RailTopBar
import com.kaushik.railway.ui.components.GlassSlider
import com.kaushik.railway.ui.components.GroupedDivider
import com.kaushik.railway.ui.components.GroupedSection
import com.kaushik.railway.ui.components.ListRow
import com.kaushik.railway.ui.components.SegmentedControl
import com.kaushik.railway.ui.components.StatusChip
import com.kaushik.railway.ui.theme.Mute
import com.kaushik.railway.ui.theme.Navy
import com.kaushik.railway.ui.theme.Orange

@Composable
fun PnrScreen(vm: AppViewModel, onBack: () -> Unit) {
    var pnr by remember { mutableStateOf("") }
    Scaffold(topBar = { RailTopBar("PNR status", onBack) }) { pad ->
        Column(
            Modifier.fillMaxSize().padding(pad).background(Color.Transparent).verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 120.dp)
        ) {
            LabeledField("10-digit PNR", pnr, onValue = { value ->
                pnr = value.filter { ch -> ch.isDigit() }.take(10)
            })
            Spacer(Modifier.height(12.dp))
            OrangeButton("GET LIVE STATUS", enabled = pnr.length == 10 && !vm.pnrLoading) { vm.lookupPnr(pnr) }
            if (vm.pnrLoading) Text("Searching your bookings…", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
            vm.pnrError?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp)) }
            if (vm.pnrNote.isNotBlank()) {
                Text(vm.pnrNote, fontWeight = FontWeight.SemiBold, color = Navy, modifier = Modifier.padding(top = 12.dp))
            }
            vm.pnrResult?.let { b ->
                Spacer(Modifier.height(16.dp))
                RailCard {
                    Column {
                        Text("PNR ${b.pnr}", fontWeight = FontWeight.Bold, color = Navy, fontSize = 18.sp)
                        KeyValue("Train", "${b.trainNo} ${b.trainName}")
                        KeyValue("From", b.fromName)
                        KeyValue("To", b.toName)
                        KeyValue("Date", b.date)
                        KeyValue("Class / Quota", "${b.travelClass} / ${b.quota}")
                        KeyValue("Chart", b.chart)
                        if (b.fare > 0) KeyValue("Fare", "₹${b.fare}")
                        Spacer(Modifier.height(8.dp))
                        b.passengers.forEach { Text(it, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                }
                vm.wlEstimateForPnr(b)?.let { estimate ->
                    Spacer(Modifier.height(14.dp))
                    WlEstimateCard(estimate)
                }
            }
            Spacer(Modifier.height(96.dp))
        }
    }
}

@Composable
fun RunningStatusScreen(vm: AppViewModel, onBack: () -> Unit) {
    var trainNo by remember { mutableStateOf("") }
    Scaffold(topBar = { RailTopBar("Live train status", onBack) }) { pad ->
        Column(
            Modifier.fillMaxSize().padding(pad).background(Color.Transparent).verticalScroll(rememberScrollState()).padding(16.dp)
        ) {
            LabeledField("Train number", trainNo, onValue = { trainNo = it.filter { c -> c.isDigit() }.take(5) })
            Spacer(Modifier.height(12.dp))
            OrangeButton("TRACK NOW", enabled = trainNo.length == 5 && !vm.runningLoading) { vm.loadRunning(trainNo) }
            if (vm.runningLoading) Text("Fetching live status…", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
            vm.runningError?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp)) }
            if (vm.runningNote.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(vm.runningNote, fontWeight = FontWeight.Bold, color = Navy)
            }
            vm.runningStops.forEach { stop ->
                RailCard(Modifier.padding(bottom = 8.dp)) {
                    Row(Modifier.fillMaxWidth()) {
                        Column(Modifier.weight(1f)) {
                            Text("${stop.station} (${stop.code})", fontWeight = FontWeight.SemiBold, color = Navy)
                            Text("Arr ${stop.schArr}   Dep ${stop.schDep}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            if (stop.delayMin != 0) {
                                Text("Delay: ${stop.delayMin} min", fontSize = 12.sp, color = Orange)
                            }
                        }
                        StatusChip(stop.status)
                    }
                }
            }
        }
    }
}

@Composable
fun ProfileScreen(
    vm: AppViewModel,
    onBack: () -> Unit,
    onLogout: () -> Unit,
    onActivate: () -> Unit = {},
    onMpin: () -> Unit = {},
    onAadhaar: () -> Unit = {}
) {
    var editName by remember { mutableStateOf(vm.userName) }
    var editMobile by remember { mutableStateOf(vm.userMobile.ifBlank { vm.mobile }) }
    var saved by remember { mutableStateOf(false) }

    Scaffold(topBar = { RailTopBar("My profile", onBack) }) { pad ->
        Column(
            Modifier.fillMaxSize().padding(pad).background(Color.Transparent)
                .verticalScroll(rememberScrollState()).padding(16.dp)
        ) {
            RailCard {
                Column {
                    Text("Account", fontWeight = FontWeight.Bold, color = Navy, fontSize = 16.sp)
                    Spacer(Modifier.height(8.dp))
                    KeyValue("User ID", vm.userId.ifBlank { "—" })
                    KeyValue("Email", vm.userEmail.ifBlank { "—" })
                    KeyValue("Profile", if (vm.profileComplete) "Complete" else "Incomplete")
                    KeyValue("MPIN", if (vm.mpinSet) "Set" else if (vm.mpinDeferred) "Later" else "Not set")
                    KeyValue(
                        "Tatkal",
                        if (vm.aadhaarLinked) "${vm.aadhaarKind} ••••${vm.aadhaarLast4}" else "Not linked"
                    )
                    KeyValue("Status", if (vm.loggedIn) "Signed in" else "Guest")
                }
            }
            Spacer(Modifier.height(16.dp))
            RailCard {
                Column {
                    Text("Edit profile", fontWeight = FontWeight.Bold, color = Navy, fontSize = 16.sp)
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = editName,
                        onValueChange = { editName = it; saved = false },
                        label = { Text("Full name") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = editMobile,
                        onValueChange = { editMobile = it.filter { c -> c.isDigit() }.take(10); saved = false },
                        label = { Text("Mobile") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Spacer(Modifier.height(12.dp))
                    OrangeButton("SAVE CHANGES") {
                        vm.updateProfile(editName.trim(), editMobile)
                        saved = true
                    }
                    if (saved) {
                        Text("Profile updated", color = com.kaushik.railway.ui.theme.Success, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp))
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            if (!vm.profileComplete) OrangeButton("COMPLETE PROFILE", onClick = onActivate)
            Spacer(Modifier.height(8.dp))
            OrangeButton(if (vm.mpinSet) "CHANGE PIN" else "GENERATE PIN", onClick = onMpin)
            Spacer(Modifier.height(8.dp))
            OrangeButton(if (vm.aadhaarLinked) "UPDATE TATKAL LINK" else "ENABLE TATKAL", onClick = onAadhaar)
            Spacer(Modifier.height(24.dp))
            OrangeButton("LOGOUT", onClick = onLogout)
            Text(
                "RailX academic demo. Bookings are stored locally on this device.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 16.dp)
            )
        }
    }
}

@Composable
fun MoreScreen(
    vm: AppViewModel,
    onPnr: () -> Unit,
    onRunning: () -> Unit,
    onProfile: () -> Unit,
    onBookings: () -> Unit,
    onAadhaar: () -> Unit = {},
    onMpin: () -> Unit = {},
    onWatch: () -> Unit = {}
) {
    val colors = MaterialTheme.colorScheme
    Column(
        Modifier.fillMaxSize().background(Color.Transparent).statusBarsPadding()
            .verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)
    ) {
        Spacer(Modifier.height(14.dp))
        Text("More", fontSize = 34.sp, fontWeight = FontWeight.Bold, color = colors.onBackground)
        Text(
            "RailX  •  academic demo",
            color = colors.onSurfaceVariant,
            fontSize = 13.sp,
            modifier = Modifier.padding(top = 2.dp)
        )
        Spacer(Modifier.height(20.dp))
        GroupedSection("Enquiries") {
            ListRow("PNR enquiry", Icons.Default.ConfirmationNumber, Color(0xFF0A84FF)) { onPnr() }
            GroupedDivider()
            ListRow("Live train status", Icons.Default.Train, Color(0xFF30D158)) { onRunning() }
            GroupedDivider()
            ListRow("Train Watch", Icons.Default.Notifications, Color(0xFFFF6482)) { onWatch() }
            GroupedDivider()
            ListRow("My bookings", Icons.Default.Luggage, Color(0xFFFF9F0A)) { onBookings() }
        }
        Spacer(Modifier.height(22.dp))
        GroupedSection("Account") {
            ListRow("Profile & logout", Icons.Default.Person, Color(0xFFBF5AF2)) { onProfile() }
            GroupedDivider()
            ListRow("Generate PIN", Icons.Default.Pin, Color(0xFF5E5CE6)) { onMpin() }
            GroupedDivider()
            ListRow("Tatkal Aadhaar link", Icons.Default.Fingerprint, Color(0xFFFF6482)) { onAadhaar() }
        }
        Spacer(Modifier.height(22.dp))
        GroupedSection("Appearance") {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                Text("Theme", fontSize = 16.sp, color = colors.onSurface)
                Spacer(Modifier.height(10.dp))
                SegmentedControl(
                    options = listOf("System", "Light", "Dark"),
                    selectedIndex = listOf("system", "light", "dark").indexOf(vm.themeMode).coerceAtLeast(0),
                    onSelect = { vm.updateThemeMode(listOf("system", "light", "dark")[it]) }
                )
                Spacer(Modifier.height(18.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Liquid Glass", fontSize = 16.sp, color = colors.onSurface)
                    Text("${(vm.glassIntensity * 100).toInt()}%", fontSize = 16.sp, color = colors.onSurfaceVariant)
                }
                GlassSlider(value = vm.glassIntensity, onValueChange = { vm.updateGlassIntensity(it) })
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Fully tinted", color = Mute, fontSize = 12.sp)
                    Text("Ultra-clear", color = Mute, fontSize = 12.sp)
                }
            }
        }
        Text(
            "Inspired by RailOne (CRIS). Academic demo only — not an official Indian Railways app.",
            color = colors.onSurfaceVariant,
            fontSize = 12.sp,
            modifier = Modifier.padding(top = 20.dp, start = 20.dp, end = 20.dp)
        )
        Spacer(Modifier.height(110.dp))
    }
}
