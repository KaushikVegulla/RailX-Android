package com.kaushik.railway.ui.screens

import android.content.Intent

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Checkbox
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kaushik.railway.AppViewModel
import com.kaushik.railway.data.PaymentApi
import com.kaushik.railway.data.PaymentBridge
import com.razorpay.Checkout
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import com.kaushik.railway.data.MockData
import com.kaushik.railway.data.Passenger
import com.kaushik.railway.ui.components.KeyValue
import com.kaushik.railway.ui.components.LabeledField
import com.kaushik.railway.ui.components.OrangeButton
import com.kaushik.railway.ui.components.SelectionExpiredScreen
import com.kaushik.railway.ui.components.RailCard
import com.kaushik.railway.ui.components.RailTopBar
import com.kaushik.railway.ui.components.SegmentedControl
import com.kaushik.railway.ui.components.SectionTitle
import com.kaushik.railway.ui.theme.Navy
import com.kaushik.railway.ui.theme.Orange

@Composable
fun PassengerScreen(vm: AppViewModel, onBack: () -> Unit, onContinue: () -> Unit) {
    val passengersValid = vm.passengers.all { it.name.isNotBlank() && (it.age.toIntOrNull() ?: 0) in 1..120 }
    Scaffold(
        topBar = { RailTopBar("Passenger details", onBack) },
        bottomBar = { BookingFooter(vm.payableAmount(), "Review journey", passengersValid, onContinue) }
    ) { pad ->
        Column(
            Modifier.fillMaxSize().padding(pad).background(Color.Transparent).verticalScroll(rememberScrollState()).padding(16.dp)
        ) {
            BookingSteps(0)
            if (vm.savedPassengers.isNotEmpty()) {
                RailCard(Modifier.padding(bottom = 12.dp)) {
                    Column {
                        Text("Saved passengers", fontWeight = FontWeight.Bold, color = Navy)
                        Text(
                            "${vm.savedPassengers.size} passenger(s) from your last booking",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(8.dp))
                        OrangeButton("LOAD SAVED PASSENGERS") { vm.loadSavedPassengers() }
                    }
                }
            }
            Text(
                "Demo booking — not an official IRCTC ticket. For learning / portfolio use only.",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            vm.passengers.forEachIndexed { i, p ->
                RailCard(Modifier.padding(bottom = 12.dp)) {
                    Column {
                        Text("Passenger ${i + 1}", fontWeight = FontWeight.Bold, color = Navy)
                        Spacer(Modifier.height(8.dp))
                        LabeledField("Name (as per ID)", p.name, onValue = { value -> vm.passengers[i] = p.copy(name = value.uppercase()) })
                        Spacer(Modifier.height(8.dp))
                        LabeledField("Age", p.age, onValue = { value ->
                            vm.passengers[i] = p.copy(age = value.filter { ch -> ch.isDigit() }.take(3))
                        })
                        Spacer(Modifier.height(8.dp))
                        LabeledField("Gender (Male/Female/Other)", p.gender, onValue = { value -> vm.passengers[i] = p.copy(gender = value) })
                        Spacer(Modifier.height(8.dp))
                        LabeledField("Berth preference", p.berth, onValue = { value -> vm.passengers[i] = p.copy(berth = value) })
                        Text(
                            "Tap options below or type. Preferred: Lower / Middle / Upper / Side Lower / Side Upper",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                        Row(
                            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(top = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            listOf("Lower", "Middle", "Upper", "Side Lower").forEach { opt ->
                                OutlinedButton(
                                    onClick = { vm.passengers[i] = p.copy(berth = opt) },
                                    modifier = Modifier.height(32.dp)
                                ) {
                                    Text(opt, fontSize = 11.sp)
                                }
                            }
                        }
                    }
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (vm.passengers.size < 6) {
                    OutlinedButton(onClick = { vm.addPassenger() }, modifier = Modifier.weight(1f)) {
                        Text("Add passenger")
                    }
                }
                if (vm.passengers.size > 1) {
                    OutlinedButton(onClick = { vm.removePassenger(vm.passengers.lastIndex) }, modifier = Modifier.weight(1f)) {
                        Text("Remove last")
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            LabeledField("Mobile", vm.mobile, onValue = { vm.mobile = it })
            Spacer(Modifier.height(8.dp))
            LabeledField("Email", vm.email, onValue = { vm.email = it })
            Spacer(Modifier.height(16.dp))
            if (!passengersValid) Text("Enter a name and an age from 1 to 120 for each passenger.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun ReviewScreen(vm: AppViewModel, onBack: () -> Unit, onPay: () -> Unit) {
    val train = vm.selectedTrain
    val cls = vm.selectedTravelClass
    if (train == null || cls == null) {
        SelectionExpiredScreen("Review booking", onBack)
        return
    }
    val ins = if (vm.insurance) vm.passengers.size * 15 else 0
    val total = cls.fare * vm.passengers.size + ins
    Scaffold(
        topBar = { RailTopBar("Review booking", onBack) },
        bottomBar = { BookingFooter(total, "Continue to payment", total > 0, onPay) }
    ) { pad ->
        Column(
            Modifier.fillMaxSize().padding(pad).background(Color.Transparent).verticalScroll(rememberScrollState()).padding(16.dp)
        ) {
            BookingSteps(1)
            RailCard {
                Column {
                    SectionTitle("${train.number} ${train.name}")
                    KeyValue("From", vm.stationName(vm.fromCode))
                    KeyValue("To", vm.stationName(vm.toCode))
                    KeyValue("Date", vm.journeyDate)
                    KeyValue("Class / Quota", "${cls.code} / ${vm.selectedQuota.take(2)}")
                    KeyValue("Dep / Arr", "${train.depart} / ${train.arrive}")
                }
            }
            Spacer(Modifier.height(12.dp))
            RailCard {
                Column {
                    SectionTitle("Passengers")
                    vm.passengers.forEachIndexed { i, p ->
                        Text("${i + 1}. ${p.name.ifBlank { "—" }}  •  ${p.age}  •  ${p.gender}  •  ${p.berth}", fontSize = 13.sp, color = Navy)
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            RailCard {
                Column {
                    SectionTitle("Fare")
                    KeyValue("Ticket", "₹${cls.fare} × ${vm.passengers.size}")
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(vm.insurance, { vm.insurance = it })
                        Text("Travel insurance ₹15 / passenger")
                    }
                    KeyValue("Total payable", "₹$total")
                }
            }
            Spacer(Modifier.height(16.dp))
            Text("Review your journey and passenger details before payment.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun PaymentScreen(vm: AppViewModel, onBack: () -> Unit, onSuccess: () -> Unit) {
    val total = vm.payableAmount()
    val activity = LocalContext.current.findActivity()
    val scope = rememberCoroutineScope()
    var method by remember { mutableStateOf("all") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    DisposableEffect(Unit) {
        onDispose {
            PaymentBridge.onSuccess = null
            PaymentBridge.onError = null
        }
    }

    Scaffold(topBar = { RailTopBar("Payment", onBack) }) { pad ->
        Column(Modifier.fillMaxSize().padding(pad).background(Color.Transparent).padding(16.dp)) {
            BookingSteps(2)
            Text("Amount payable", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("₹$total", fontSize = 32.sp, fontWeight = FontWeight.Bold, color = Navy)
            Spacer(Modifier.height(16.dp))
            Text("Pay using Razorpay (test)", fontWeight = FontWeight.SemiBold, color = Navy)
            Spacer(Modifier.height(8.dp))
            listOf(
                "all" to "All methods (UPI + Cards + Netbanking)",
                "upi" to "UPI (GPay / PhonePe / Paytm)",
                "card" to "Credit / Debit card",
                "netbanking" to "Net banking"
            ).forEach { (id, label) ->
                val selected = method == id
                RailCard(
                    Modifier
                        .padding(bottom = 8.dp)
                        .then(
                            if (selected) Modifier.border(2.dp, Orange, RoundedCornerShape(12.dp))
                            else Modifier
                        )
                        .clickable(enabled = !busy) { method = id }
                ) {
                    Text(label, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium, color = Navy)
                }
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 13.sp, modifier = Modifier.padding(vertical = 8.dp)) }
            if (busy) Text("Opening Razorpay checkout…", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
            Spacer(Modifier.height(12.dp))
            OrangeButton(if (busy) "PLEASE WAIT" else "PAY ₹$total", enabled = !busy) {
                val host = activity
                if (host == null) {
                    error = "Unable to open Razorpay checkout from this screen."
                    return@OrangeButton
                }
                val paymentOwner = vm.bookingOwnerId
                if (paymentOwner.isBlank()) {
                    error = "Sign in before starting payment."
                    return@OrangeButton
                }
                busy = true
                error = null
                PaymentBridge.onSuccess = { payId, orderId, sig ->
                    scope.launch {
                        try {
                            check(vm.bookingOwnerId == paymentOwner) { "Your account changed. Sign in to the account that started payment." }
                            withContext(Dispatchers.IO) {
                                vm.refreshPaymentSession()
                                PaymentApi.verifyPayment(payId, orderId, sig)
                            }
                            vm.confirmBooking(payId, orderId, expectedOwnerId = paymentOwner)
                            busy = false
                            onSuccess()
                        } catch (e: Exception) {
                            busy = false
                            error = e.message ?: "Verify failed"
                        }
                    }
                }
                PaymentBridge.onError = { msg ->
                    busy = false
                    error = payError(msg)
                }
                scope.launch {
                    try {
                        val order = withContext(Dispatchers.IO) {
                            vm.refreshPaymentSession()
                            PaymentApi.createOrder(
                                total,
                                "railx_${vm.fromCode}${vm.toCode}_${System.currentTimeMillis()}"
                            )
                        }
                        val options = JSONObject().apply {
                            put("key", order.keyId)
                            put("amount", order.amountPaise)
                            put("currency", order.currency)
                            put("name", "RailX")
                            put("description", "${vm.fromCode} to ${vm.toCode} ticket")
                            put("order_id", order.orderId)
                            put("theme", JSONObject().put("color", "#0A3D91"))
                            put("retry", JSONObject().put("enabled", true).put("max_count", 1))
                            put(
                                "prefill",
                                JSONObject()
                                    .put("email", vm.email.ifBlank { "passenger@railone.test" })
                                    .put("contact", vm.mobile.filter { it.isDigit() }.ifBlank { "9999999999" })
                            )
                            put(
                                "method",
                                JSONObject().apply {
                                    put("upi", method == "all" || method == "upi")
                                    put("card", method == "all" || method == "card")
                                    put("netbanking", method == "all" || method == "netbanking")
                                    put("wallet", method == "all")
                                }
                            )
                        }
                        withContext(Dispatchers.Main) {
                            check(vm.bookingOwnerId == paymentOwner) { "Your account changed. Start payment again." }
                            val checkout = Checkout()
                            checkout.setKeyID(order.keyId)
                            checkout.open(host, options)
                        }
                    } catch (e: Exception) {
                        busy = false
                        error = payError(e.message)
                    }
                }
            }
            Text(
                "Razorpay test mode. Card 4111 1111 1111 1111, any future expiry, any CVV. UPI: success@razorpay",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}

private fun payError(msg: String?): String {
    val m = msg.orEmpty()
    return when {
        m.contains("Authentication failed", true) ->
            "Razorpay key was rejected. Reinstall the latest app build."
        m.contains("failed to connect", true) || m.contains("timed out", true) ||
            m.contains("Unable to resolve", true) || m.contains("timeout", true) ->
            "Could not reach Razorpay. Turn on mobile data/Wi‑Fi and retry."
        m.contains("cancelled", true) || m.contains("canceled", true) || m.contains("USER_CLOSED", true) ->
            "Payment cancelled. Tap PAY to try again."
        m.contains("no_payment_methods", true) || m.contains("No appropriate payment method", true) ->
            "That payment method is not enabled on this Razorpay account. Choose 'All methods' or pay by card / net banking."
        m.isBlank() -> "Payment could not start. Retry."
        // Never show the user a raw JSON error body from the SDK.
        m.trimStart().startsWith("{") -> "Payment could not be completed. Please try again."
        else -> m
    }
}

private fun Context.findActivity(): Activity? {
    var ctx: Context? = this
    while (ctx is ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}

@Composable
fun TicketScreen(vm: AppViewModel, onHome: () -> Unit) {
    val b = vm.lastBooking
    val context = LocalContext.current
    Scaffold(topBar = { RailTopBar("e-Ticket") }) { pad ->
        Column(
            Modifier.fillMaxSize().padding(pad).background(Color.Transparent).verticalScroll(rememberScrollState()).padding(16.dp)
        ) {
            if (b == null) {
                Text("No ticket yet. Complete a booking first.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(16.dp))
                OrangeButton("BACK TO HOME") { onHome() }
            } else {
                Text(
                    "Demo e-ticket — not valid for travel on Indian Railways.",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                RailCard {
                    Column {
                        Text("PNR  ${b.pnr}", fontSize = 22.sp, fontWeight = FontWeight.Black, color = Orange)
                        Text(b.status, fontWeight = FontWeight.Bold, color = Navy, fontSize = 16.sp)
                        Spacer(Modifier.height(8.dp))
                        KeyValue("Train", "${b.train.number} ${b.train.name}")
                        KeyValue("Route", "${b.fromName} → ${b.toName}")
                        KeyValue("Date", b.date)
                        KeyValue("Class / Quota", "${b.travelClass.code} / ${b.quota}")
                        KeyValue("Amount", "₹${b.amount}")
                        if (b.paymentId.isNotBlank()) KeyValue("Payment ID", b.paymentId)
                        Spacer(Modifier.height(8.dp))
                        SectionTitle("Passengers")
                        b.passengers.forEachIndexed { i, p ->
                            Text(
                                "${i + 1}. ${p.name}  ·  ${p.age}  ·  ${p.gender}  ·  ${p.berth}",
                                fontSize = 13.sp,
                                color = Navy
                            )
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                OrangeButton("SHARE TICKET") {
                    val text = buildString {
                        appendLine("RailX Demo Ticket")
                        appendLine("PNR: ${b.pnr}")
                        appendLine("${b.train.number} ${b.train.name}")
                        appendLine("${b.fromName} → ${b.toName}")
                        appendLine("Date: ${b.date}  Class: ${b.travelClass.code}")
                        appendLine("Amount: ₹${b.amount}")
                        appendLine("Status: ${b.status}")
                        appendLine("(Demo only — not an IRCTC ticket)")
                    }
                    val send = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_SUBJECT, "RailX PNR ${b.pnr}")
                        putExtra(Intent.EXTRA_TEXT, text)
                    }
                    context.startActivity(Intent.createChooser(send, "Share ticket"))
                }
                Spacer(Modifier.height(8.dp))
                OrangeButton("BACK TO HOME") { onHome() }
            }
        }
    }
}


@Composable
fun BookingsScreen(vm: AppViewModel, onBack: () -> Unit) {
    var confirmPnr by rememberSaveable(vm.bookingOwnerId) { mutableStateOf<String?>(null) }
    var showCancelled by rememberSaveable(vm.bookingOwnerId) { mutableStateOf(false) }
    val bookings = vm.bookings.filter { (it.status == "CANCELLED") == showCancelled }
    Scaffold(topBar = { RailTopBar("My journeys", onBack) }) { pad ->
        LazyColumn(
            Modifier.fillMaxSize().padding(pad),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 112.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Text("Your travel collection", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text("Bookings saved for this account", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(12.dp))
                SegmentedControl(
                    options = listOf("Booked", "Cancelled"),
                    selectedIndex = if (showCancelled) 1 else 0,
                    onSelect = { showCancelled = it == 1 }
                )
            }
            if (bookings.isEmpty()) {
                item {
                    RailCard {
                        Column(Modifier.padding(vertical = 12.dp)) {
                            Text(if (showCancelled) "No cancelled journeys" else "Your next journey starts here", style = MaterialTheme.typography.titleMedium)
                            Text(if (showCancelled) "Cancelled demo bookings will appear here." else "Search for a train to create your first demo booking.", modifier = Modifier.padding(top = 8.dp), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            items(bookings, key = { it.pnr }) { b ->
                RailCard {
                    Column {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(b.train.number, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium)
                            Text(if (b.status == "CANCELLED") "Cancelled" else b.status, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text(b.train.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text("${b.fromName} → ${b.toName}", modifier = Modifier.padding(top = 12.dp), style = MaterialTheme.typography.bodyMedium)
                        Text("${b.date} · ${b.travelClass.code}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Row(Modifier.fillMaxWidth().padding(top = 14.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("PNR ${b.pnr}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("₹${b.amount}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        }
                        if (b.status == "CANCELLED" && b.refundAmount > 0) {
                            Text(
                                "Refund ₹${b.refundAmount} · cancellation charge ₹${b.cancelCharge}",
                                modifier = Modifier.padding(top = 8.dp),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        if (b.status != "CANCELLED") {
                            TextButton(onClick = { confirmPnr = b.pnr }) { Text("Cancel booking") }
                        }
                    }
                }
            }
        }
    }
    val cancellation = vm.bookings.firstOrNull { it.pnr == confirmPnr && it.status != "CANCELLED" }
    if (cancellation != null) {
        val quote = vm.refundQuoteFor(cancellation)
        val fareTotal = cancellation.travelClass.fare * cancellation.passengers.size
        val extras = cancellation.amount - fareTotal
        AlertDialog(
            onDismissRequest = { confirmPnr = null },
            title = { Text("Cancel this journey?") },
            text = {
                Column {
                    Text("${cancellation.train.name} · ${cancellation.date}")
                    Spacer(Modifier.height(10.dp))
                    Text("Ticket fare  ₹$fareTotal")
                    Text("Cancellation charge  −₹${quote.totalCharge}")
                    Text("Refund  ₹${quote.refundAmount}", fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "IRCTC rule: ${quote.rule}.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (extras > 0) {
                        Text(
                            "Add-ons (₹$extras, e.g. travel insurance) are not refundable.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        "Demo booking — the refund follows the official IRCTC rules; no real money moves.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = { TextButton(onClick = { vm.cancel(cancellation.pnr); confirmPnr = null }) { Text("Cancel booking", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { confirmPnr = null }) { Text("Keep journey") } }
        )
    }
}

@Composable
private fun BookingSteps(current: Int) {
    Row(Modifier.fillMaxWidth().padding(bottom = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf("Passengers", "Review", "Payment").forEachIndexed { index, label ->
            Surface(
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp),
                color = if (index == current) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
            ) {
                Text("${index + 1} · $label", modifier = Modifier.padding(horizontal = 8.dp, vertical = 10.dp), style = MaterialTheme.typography.labelSmall,
                    color = if (index == current) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun BookingFooter(amount: Int, label: String, enabled: Boolean, onContinue: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 2.dp) {
        Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 20.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.padding(end = 20.dp)) {
                Text("Total", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("₹$amount", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            }
            OrangeButton(label, modifier = Modifier.weight(1f), enabled = enabled, onClick = onContinue)
        }
    }
}
