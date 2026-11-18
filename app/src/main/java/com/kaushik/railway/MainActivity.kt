package com.kaushik.railway

import android.content.Intent
import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.fragment.app.FragmentActivity
import androidx.compose.foundation.layout.navigationBarsPadding
import com.kaushik.railway.data.PaymentBridge
import com.razorpay.PaymentData
import com.razorpay.PaymentResultWithDataListener
import androidx.compose.foundation.background
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ConfirmationNumber
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Train
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.sp
import com.kaushik.railway.ui.theme.GlassFill
import com.kaushik.railway.ui.theme.GlassStroke
import com.kaushik.railway.ui.theme.LiquidGlassBackdrop
import com.kaushik.railway.ui.theme.Navy
import com.kaushik.railway.ui.theme.Orange
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.kaushik.railway.nav.Routes
import com.kaushik.railway.ui.screens.AadhaarEnableScreen
import com.kaushik.railway.ui.screens.AccountRegisterScreen
import com.kaushik.railway.ui.screens.AvailabilityScreen
import com.kaushik.railway.ui.screens.BookingsScreen
import com.kaushik.railway.ui.screens.HomeScreen
import com.kaushik.railway.ui.screens.LoginScreen
import com.kaushik.railway.ui.screens.MoreScreen
import com.kaushik.railway.ui.screens.MpinScreen
import com.kaushik.railway.ui.screens.PassengerScreen
import com.kaushik.railway.ui.screens.PaymentScreen
import com.kaushik.railway.ui.screens.PnrScreen
import com.kaushik.railway.ui.screens.ProfileActivationScreen
import com.kaushik.railway.ui.screens.ProfileScreen
import com.kaushik.railway.ui.screens.ReviewScreen
import com.kaushik.railway.ui.screens.RunningStatusScreen
import com.kaushik.railway.ui.screens.SplashScreen
import com.kaushik.railway.ui.screens.VerifyEmailScreen
import com.kaushik.railway.ui.screens.TicketScreen
import com.kaushik.railway.ui.screens.TrainListScreen
import com.kaushik.railway.ui.screens.UnlockScreen
import com.kaushik.railway.ui.screens.VacancyChartScreen
import com.kaushik.railway.ui.theme.RailwayTheme
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.sp
import com.kaushik.railway.util.CrashLog

class MainActivity : FragmentActivity(), PaymentResultWithDataListener {
    private val vm: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        vm.handleAuthLink(intent?.data?.toString())
        setContent {
            RailwayTheme(glassIntensity = vm.glassIntensity, darkOverride = vm.darkThemeEnabled) {
                RailwayApp(vm)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        vm.handleAuthLink(intent.data?.toString())
    }

    override fun onPaymentSuccess(razorpayPaymentId: String?, paymentData: PaymentData?) {
        val data = paymentData?.data
        PaymentBridge.onSuccess?.invoke(
            razorpayPaymentId.orEmpty(),
            data?.optString("razorpay_order_id").orEmpty(),
            data?.optString("razorpay_signature").orEmpty()
        )
    }

    override fun onPaymentError(code: Int, response: String?, paymentData: PaymentData?) {
        PaymentBridge.onError?.invoke(response ?: "Payment failed ($code)")
    }
}

private val IosEase = CubicBezierEasing(0.32f, 0.72f, 0.24f, 1f)

@Composable
fun RailwayApp(vm: AppViewModel = viewModel()) {
    val nav = rememberNavController()
    val back by nav.currentBackStackEntryAsState()
    val route = back?.destination?.route
    val tabs = setOf(Routes.Home, Routes.Bookings, Routes.Pnr, Routes.More)
    val showBar = route in tabs
    val barFill = GlassFill
    val barStroke = GlassStroke

    LiquidGlassBackdrop {
    if (vm.showPasswordReset) {
        PasswordResetDialog(vm)
    }
    val crashContext = LocalContext.current
    var crashText by remember { mutableStateOf(CrashLog.read(crashContext)) }
    crashText?.let { trace ->
        AlertDialog(
            onDismissRequest = { CrashLog.clear(crashContext); crashText = null },
            title = { Text("Last crash report") },
            text = {
                Text(
                    trace,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                    modifier = Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState())
                )
            },
            confirmButton = {
                TextButton(onClick = { CrashLog.clear(crashContext); crashText = null }) { Text("Dismiss", color = Orange) }
            }
        )
    }
    vm.authLinkNotice?.let { notice ->
        AlertDialog(
            onDismissRequest = { vm.dismissAuthLinkNotice() },
            title = { Text("RailX") },
            text = { Text(notice) },
            confirmButton = {
                TextButton(onClick = { vm.dismissAuthLinkNotice() }) { Text("OK", color = Orange) }
            }
        )
    }
    Box(Modifier.fillMaxSize()) {
        NavHost(
            navController = nav,
            startDestination = Routes.Splash,
            modifier = Modifier.fillMaxSize(),
            enterTransition = {
                if (initialState.destination.route in tabs && targetState.destination.route in tabs) {
                    fadeIn(tween(260))
                } else {
                    fadeIn(tween(320)) + slideInHorizontally(tween(380, easing = IosEase)) { it }
                }
            },
            exitTransition = {
                if (initialState.destination.route in tabs && targetState.destination.route in tabs) {
                    fadeOut(tween(200))
                } else {
                    fadeOut(tween(320)) + slideOutHorizontally(tween(380, easing = IosEase)) { -(it * 0.3f).toInt() }
                }
            },
            popEnterTransition = {
                if (initialState.destination.route in tabs && targetState.destination.route in tabs) {
                    fadeIn(tween(260))
                } else {
                    fadeIn(tween(320)) + slideInHorizontally(tween(380, easing = IosEase)) { -(it * 0.3f).toInt() }
                }
            },
            popExitTransition = {
                if (initialState.destination.route in tabs && targetState.destination.route in tabs) {
                    fadeOut(tween(200))
                } else {
                    fadeOut(tween(320)) + slideOutHorizontally(tween(380, easing = IosEase)) { it }
                }
            }
        ) {
            composable(Routes.Splash) {
                SplashScreen(ready = vm.gateReady) {
                    nav.navigate(vm.gateDestination()) {
                        popUpTo(Routes.Splash) { inclusive = true }
                    }
                }
            }
            composable(Routes.Unlock) {
                UnlockScreen(vm) {
                    nav.navigate(vm.gateDestination()) {
                        popUpTo(Routes.Unlock) { inclusive = true }
                    }
                }
            }
            composable(Routes.Login) {
                LoginScreen(
                    vm,
                    onLogin = {
                        nav.navigate(vm.gateDestination()) { popUpTo(Routes.Login) { inclusive = true } }
                    },
                    onRegister = { nav.navigate(Routes.Register) },
                    onNeedVerify = { nav.navigate(Routes.Verify) }
                )
            }
            composable(Routes.Register) {
                AccountRegisterScreen(
                    vm,
                    onDone = { nav.popBackStack() },
                    onBack = { nav.popBackStack() }
                )
            }
            composable(Routes.Activate) {
                ProfileActivationScreen(
                    vm,
                    onDone = {
                        nav.navigate(Routes.Mpin) { popUpTo(Routes.Activate) { inclusive = true } }
                    },
                    onLater = {
                        if (!nav.popBackStack()) {
                            nav.navigate(Routes.Home) { popUpTo(0) { inclusive = true } }
                        }
                    }
                )
            }
            composable(Routes.Mpin) {
                MpinScreen(vm) {
                    if (!nav.popBackStack(Routes.Home, inclusive = false)) {
                        nav.navigate(Routes.Home) { popUpTo(0) { inclusive = true } }
                    }
                }
            }
            composable(Routes.Aadhaar) {
                AadhaarEnableScreen(
                    vm,
                    onDone = { nav.popBackStack() },
                    onBack = { nav.popBackStack() }
                )
            }
            composable(Routes.Verify) {
                VerifyEmailScreen(
                    vm,
                    email = vm.email,
                    onVerified = {
                        nav.navigate(vm.gateDestination()) { popUpTo(Routes.Login) { inclusive = true } }
                    },
                    onBack = { nav.popBackStack() }
                )
            }
            composable(Routes.Home) {
                HomeScreen(
                    vm,
                    onSearch = { nav.navigate(Routes.Trains) },
                    onPnr = { nav.navigate(Routes.Pnr) },
                    onRunning = { nav.navigate(Routes.Running) },
                    onBookings = { nav.navigate(Routes.Bookings) },
                    onNeedProfile = { nav.navigate(Routes.Activate) },
                    onNeedAadhaar = { nav.navigate(Routes.Aadhaar) }
                )
            }
            composable(Routes.Trains) {
                TrainListScreen(vm, onBack = { nav.popBackStack() }, onSelect = {
                    vm.selectedTrain = it
                    nav.navigate(Routes.Availability)
                })
            }
            composable(Routes.Availability) {
                AvailabilityScreen(vm, onBack = { nav.popBackStack() }, onVacancy = {
                    vm.selectedTravelClass = it
                    nav.navigate(Routes.Vacancy)
                })
            }
            composable(Routes.Vacancy) {
                VacancyChartScreen(vm, onBack = { nav.popBackStack() }, onBook = {
                    when {
                        !vm.profileComplete -> nav.navigate(Routes.Activate)
                        vm.needsTatkalAadhaar() -> nav.navigate(Routes.Aadhaar)
                        else -> nav.navigate(Routes.Passengers)
                    }
                })
            }
            composable(Routes.Passengers) {
                PassengerScreen(vm, onBack = { nav.popBackStack() }, onContinue = { nav.navigate(Routes.Review) })
            }
            composable(Routes.Review) {
                ReviewScreen(vm, onBack = { nav.popBackStack() }, onPay = { nav.navigate(Routes.Payment) })
            }
            composable(Routes.Payment) {
                PaymentScreen(vm, onBack = { nav.popBackStack() }, onSuccess = {
                    nav.navigate(Routes.Ticket) { popUpTo(Routes.Home) }
                })
            }
            composable(Routes.Ticket) { TicketScreen(vm) { nav.navigate(Routes.Home) { popUpTo(Routes.Home) { inclusive = true } } } }
            composable(Routes.Pnr) { PnrScreen(vm, onBack = { if (!nav.popBackStack()) nav.navigate(Routes.Home) }) }
            composable(Routes.Running) { RunningStatusScreen(vm, onBack = { nav.popBackStack() }) }
            composable(Routes.Bookings) { BookingsScreen(vm, onBack = { if (!nav.popBackStack()) nav.navigate(Routes.Home) }) }
            composable(Routes.Profile) {
                ProfileScreen(
                    vm = vm,
                    onBack = { nav.popBackStack() },
                    onLogout = {
                        vm.logout {
                            nav.navigate(Routes.Login) { popUpTo(0) { inclusive = true } }
                        }
                    },
                    onActivate = { nav.navigate(Routes.Activate) },
                    onMpin = { nav.navigate(Routes.Mpin) },
                    onAadhaar = { nav.navigate(Routes.Aadhaar) }
                )
            }
            composable(Routes.More) {
                MoreScreen(
                    vm = vm,
                    onPnr = { nav.navigate(Routes.Pnr) },
                    onRunning = { nav.navigate(Routes.Running) },
                    onProfile = { nav.navigate(Routes.Profile) },
                    onBookings = { nav.navigate(Routes.Bookings) },
                    onAadhaar = { nav.navigate(Routes.Aadhaar) },
                    onMpin = { nav.navigate(Routes.Mpin) }
                )
            }
        }
        if (showBar) {
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 10.dp)
                    .shadow(18.dp, RoundedCornerShape(36.dp))
                    .clip(RoundedCornerShape(36.dp))
                    .background(barFill)
                    .border(1.dp, barStroke, RoundedCornerShape(36.dp))
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(6.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    NavItem("Home", route == Routes.Home, Icons.Default.Home, Modifier.weight(1f)) {
                        nav.navigate(Routes.Home) { popUpTo(Routes.Home) { saveState = true }; launchSingleTop = true; restoreState = true }
                    }
                    NavItem("Trips", route == Routes.Bookings, Icons.Default.Train, Modifier.weight(1f)) {
                        nav.navigate(Routes.Bookings) { popUpTo(Routes.Home) { saveState = true }; launchSingleTop = true; restoreState = true }
                    }
                    NavItem("PNR", route == Routes.Pnr, Icons.Default.ConfirmationNumber, Modifier.weight(1f)) {
                        nav.navigate(Routes.Pnr) { popUpTo(Routes.Home) { saveState = true }; launchSingleTop = true; restoreState = true }
                    }
                    NavItem("More", route == Routes.More, Icons.Default.MoreHoriz, Modifier.weight(1f)) {
                        nav.navigate(Routes.More) { popUpTo(Routes.Home) { saveState = true }; launchSingleTop = true; restoreState = true }
                    }
                }
            }
        }
    }
    }
}

@Composable
private fun NavItem(label: String, selected: Boolean, icon: ImageVector, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val tint by animateColorAsState(if (selected) colors.onPrimary else colors.onSurfaceVariant, label = "Navigation tint")
    val background by animateColorAsState(if (selected) colors.primary else Color.Transparent, label = "Navigation selection")
    Column(
        modifier = modifier.clip(RoundedCornerShape(24.dp)).background(background)
            .selectable(selected = selected, role = Role.Tab, onClick = onClick)
            .heightIn(min = 56.dp).padding(horizontal = 4.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(icon, contentDescription = null, tint = tint)
        Text(label, color = tint, style = MaterialTheme.typography.labelSmall)
    }
}


@Composable
private fun PasswordResetDialog(vm: AppViewModel) {
    var newPw by remember { mutableStateOf("") }
    var confirmPw by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = { if (!busy) vm.dismissPasswordReset() },
        title = { Text("Set a new password") },
        text = {
            Column {
                Text(
                    "You opened a password reset link. Choose a new password for your Supabase account.",
                    fontSize = 13.sp
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = newPw,
                    onValueChange = { newPw = it },
                    label = { Text("New password") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = confirmPw,
                    onValueChange = { confirmPw = it },
                    label = { Text("Confirm new password") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth()
                )
                error?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp))
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !busy,
                onClick = {
                    error = when {
                        newPw.length < 8 -> "Use at least 8 characters."
                        newPw != confirmPw -> "Passwords do not match."
                        else -> null
                    }
                    if (error == null) {
                        busy = true
                        vm.completePasswordReset(newPw) { message ->
                            busy = false
                            if (message != null) error = message
                        }
                    }
                }
            ) { Text(if (busy) "PLEASE WAIT" else "UPDATE PASSWORD", color = Orange) }
        },
        dismissButton = {
            TextButton(enabled = !busy, onClick = { vm.dismissPasswordReset() }) {
                Text("Cancel", color = Navy)
            }
        }
    )
}
