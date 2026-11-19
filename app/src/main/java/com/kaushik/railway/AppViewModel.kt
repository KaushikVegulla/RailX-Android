package com.kaushik.railway

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kaushik.railway.data.AccountIdentity
import com.kaushik.railway.data.AccountProvider
import com.kaushik.railway.data.AccountStore
import com.kaushik.railway.data.AvailabilityResult
import com.kaushik.railway.data.Booking
import com.kaushik.railway.data.CancellationAdvice
import com.kaushik.railway.data.CancellationAdvisor
import com.kaushik.railway.data.IrctcRules
import com.kaushik.railway.data.RefundPolicy
import com.kaushik.railway.data.RefundQuote
import com.kaushik.railway.data.LastJourney
import com.kaushik.railway.data.MockData
import com.kaushik.railway.data.Passenger
import com.kaushik.railway.data.PaymentApi
import com.kaushik.railway.data.PnrResult
import com.kaushik.railway.data.ProfileDraft
import com.kaushik.railway.data.RailProfile
import com.kaushik.railway.data.RunningStop
import com.kaushik.railway.data.SessionStore
import com.kaushik.railway.data.Station
import com.kaushik.railway.data.RemoteFavouriteRoute
import com.kaushik.railway.data.SupabaseCall
import com.kaushik.railway.data.SupabaseSession
import com.kaushik.railway.data.Train
import com.kaushik.railway.data.TrainClassAvail
import com.kaushik.railway.data.db.AppDatabase
import com.kaushik.railway.data.repository.AuthRepository
import com.kaushik.railway.data.repository.BookingRepository
import com.kaushik.railway.data.repository.SupabaseAuthRepository
import com.kaushik.railway.data.repository.SupabaseSyncRepository
import com.kaushik.railway.data.repository.TrainRepository
import com.kaushik.railway.nav.AccountGate
import com.kaushik.railway.util.UiState
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import kotlin.random.Random

class AppViewModel : ViewModel() {

    private val sessionStore = SessionStore(RailApp.instance)
    private val accountStore = AccountStore(RailApp.instance)
    private val bookingRepo = BookingRepository(AppDatabase.getInstance(RailApp.instance))
    private val trainRepo = TrainRepository()
    private val authRepo = AuthRepository(sessionStore)
    private val supabaseAuth = SupabaseAuthRepository(RailApp.instance)
    private val supabaseSync = SupabaseSyncRepository(supabaseAuth)

    private var bookingHistoryJob: Job? = null
    var bookingOwnerId by mutableStateOf("")
        private set

    // Auth / session
    var loggedIn by mutableStateOf(false)
    var userName by mutableStateOf("")
    var userEmail by mutableStateOf("")
    var userMobile by mutableStateOf("")
    var userId by mutableStateOf("")
    var profileComplete by mutableStateOf(false)
    var mpinSet by mutableStateOf(false)
    var mpinDeferred by mutableStateOf(false)
    var biometricOn by mutableStateOf(false)
    var aadhaarLinked by mutableStateOf(false)
    var aadhaarLast4 by mutableStateOf("")
    var aadhaarKind by mutableStateOf("")
    var currentProfile by mutableStateOf<RailProfile?>(null)
    var loginPrefill by mutableStateOf("")
    var regVisuallyImpaired by mutableStateOf(false)
    var regUserId by mutableStateOf("")
    var regFullName by mutableStateOf("")
    var regPassword by mutableStateOf("")
    var regMobile by mutableStateOf("")
    var regEmail by mutableStateOf("")
    var regLanguage by mutableStateOf("English")
    var regCaptcha by mutableStateOf("")
    var regEmailOtp by mutableStateOf("")
    var regMobileOtp by mutableStateOf("")
    var regStep by mutableStateOf(0)
    var regIdMessage by mutableStateOf("")
    var regIdChecked by mutableStateOf("")
    private var profilesReady by mutableStateOf(false)
    var otpEmailed by mutableStateOf(true)
    var otpDisplayCode by mutableStateOf<String?>(null)
    var authLoading by mutableStateOf(false)
    var authError by mutableStateOf<String?>(null)
    var needsVerification by mutableStateOf(false)
    var pendingVerifyEmail by mutableStateOf("")
    var gateReady by mutableStateOf(false)
    var appUnlocked by mutableStateOf(false)
    var cloudBusy by mutableStateOf(false)
    // Appearance (persisted): liquid glass intensity + theme mode.
    private val uiPrefs = RailApp.instance.getSharedPreferences("railx_ui", android.content.Context.MODE_PRIVATE)
    var glassIntensity by mutableStateOf(uiPrefs.getFloat("glass_intensity", 0.55f))
        private set
    var themeMode by mutableStateOf(uiPrefs.getString("theme_mode", "system") ?: "system")
        private set
    val darkThemeEnabled: Boolean?
        get() = when (themeMode) {
            "light" -> false
            "dark" -> true
            else -> null
        }

    fun updateGlassIntensity(value: Float) {
        glassIntensity = value.coerceIn(0f, 1f)
        uiPrefs.edit().putFloat("glass_intensity", glassIntensity).apply()
    }

    fun updateThemeMode(mode: String) {
        themeMode = mode
        uiPrefs.edit().putString("theme_mode", mode).apply()
    }

    var useSupabaseConfirm by mutableStateOf(false)

    // Deep links from Supabase emails (railx://auth-callback#...).
    var pendingResetToken by mutableStateOf("")
        private set
    var showPasswordReset by mutableStateOf(false)
    var authLinkNotice by mutableStateOf<String?>(null)

    fun handleAuthLink(uri: String?) {
        if (uri.isNullOrBlank() || !uri.startsWith("railx://auth-callback")) return
        val params = uri.substringAfter('#', "")
            .split('&')
            .mapNotNull { part ->
                val idx = part.indexOf('=')
                if (idx <= 0) null else part.substring(0, idx) to part.substring(idx + 1)
            }
            .toMap()
        when (params["type"]) {
            "recovery" -> {
                val token = params["access_token"].orEmpty()
                if (token.isNotBlank()) {
                    pendingResetToken = token
                    showPasswordReset = true
                }
            }
            "signup" -> authLinkNotice =
                "Email confirmed. Sign in with your email and password to finish."
            else -> Unit
        }
    }

    fun dismissAuthLinkNotice() {
        authLinkNotice = null
    }

    fun dismissPasswordReset() {
        showPasswordReset = false
        pendingResetToken = ""
    }

    fun completePasswordReset(newPassword: String, onDone: (String?) -> Unit) {
        val token = pendingResetToken
        if (token.isBlank()) {
            onDone("This reset link has expired. Request a new one from the sign-in screen.")
            return
        }
        viewModelScope.launch {
            when (val call = supabaseAuth.updatePassword(token, newPassword)) {
                is SupabaseCall.Err -> onDone(call.message)
                else -> {
                    pendingResetToken = ""
                    showPasswordReset = false
                    authLinkNotice = "Password updated. Sign in with your new password."
                    onDone(null)
                }
            }
        }
    }
    var supabaseDelivery by mutableStateOf("")
    private var sessionSeen = false
    private var pendingSupabaseEmail = ""

    // Search
    var fromCode by mutableStateOf("NDLS")
    var toCode by mutableStateOf("MMCT")

    // Favourite routes live in Supabase (favourite_routes) and only exist
    // for Supabase sessions; demo/local accounts simply never see them.
    var favouriteRoutes by mutableStateOf<List<RemoteFavouriteRoute>>(emptyList())
        private set
    var supabaseLinked by mutableStateOf(false)
        private set

    val currentRouteIsFavourite: Boolean
        get() = favouriteRoutes.any {
            it.fromCode.equals(fromCode, ignoreCase = true) &&
                it.toCode.equals(toCode, ignoreCase = true)
        }

    fun loadFavouriteRoutes() {
        viewModelScope.launch {
            if (supabaseAuth.currentSession() != null) {
                supabaseLinked = true
                favouriteRoutes = supabaseSync.pullFavouriteRoutes()
            }
        }
    }

    fun toggleFavouriteRoute() {
        val existing = favouriteRoutes.firstOrNull {
            it.fromCode.equals(fromCode, ignoreCase = true) &&
                it.toCode.equals(toCode, ignoreCase = true)
        }
        viewModelScope.launch {
            if (existing != null) {
                if (supabaseSync.deleteFavouriteRoute(existing.id)) {
                    favouriteRoutes = favouriteRoutes.filterNot { it.id == existing.id }
                }
            } else {
                val added = supabaseSync.addFavouriteRoute(fromCode, toCode)
                if (added != null) favouriteRoutes = favouriteRoutes + added
            }
        }
    }

    fun removeFavouriteRoute(route: RemoteFavouriteRoute) {
        viewModelScope.launch {
            if (supabaseSync.deleteFavouriteRoute(route.id)) {
                favouriteRoutes = favouriteRoutes.filterNot { it.id == route.id }
            }
        }
    }
    var journeyDate by mutableStateOf(defaultDate())
    var selectedClass by mutableStateOf("All Classes")
    var selectedQuota by mutableStateOf("GN - General")
    var returnDate by mutableStateOf<String?>(null)
    var lastJourney by mutableStateOf<LastJourney?>(null)
    var savedPassengers = mutableStateListOf<Passenger>()
    var isOfflineHint by mutableStateOf(false)

    var selectedTrain by mutableStateOf<Train?>(null)
    var selectedTravelClass by mutableStateOf<TrainClassAvail?>(null)
    var vacancy by mutableStateOf<AvailabilityResult?>(null)
    var passengers = mutableStateListOf(Passenger())
    var mobile by mutableStateOf("")
    var email by mutableStateOf("")
    var insurance by mutableStateOf(true)
    val selectedSeatIds = mutableStateListOf<String>()
    var selectedSeatLabels by mutableStateOf("")
    var lastBooking by mutableStateOf<Booking?>(null)

    /** PNR whose Trip Day companion is open, set from My journeys. */
    var selectedTripPnr by mutableStateOf<String?>(null)
    val bookings = mutableStateListOf<Booking>()

    val trains = mutableStateListOf<Train>()
    var searchLoading by mutableStateOf(false)
    var searchError by mutableStateOf<String?>(null)

    val classRows = mutableStateListOf<TrainClassAvail>()
    var availLoading by mutableStateOf(false)
    var availError by mutableStateOf<String?>(null)

    var pnrLoading by mutableStateOf(false)
    var pnrError by mutableStateOf<String?>(null)
    var pnrResult by mutableStateOf<PnrResult?>(null)
    var pnrNote by mutableStateOf("")

    var runningLoading by mutableStateOf(false)
    var runningError by mutableStateOf<String?>(null)
    val runningStops = mutableStateListOf<RunningStop>()
    var runningNote by mutableStateOf("")

    val stationSuggestions = mutableStateListOf<Station>()
    var stationSuggestionField by mutableStateOf("from")
        private set
    var vacancyLoading by mutableStateOf(false)
        private set
    var vacancyError by mutableStateOf<String?>(null)
        private set
    private var stationSearchJob: Job? = null
    private var trainSearchJob: Job? = null
    private var availabilityJob: Job? = null
    private var vacancyJob: Job? = null
    private var stationSearchVersion = 0
    private var trainSearchVersion = 0
    private var availabilityVersion = 0
    private var vacancyVersion = 0
    private var lastStationQuery = ""


    // Seat / berth preferences (enhanced)
    val berthOptions = listOf("No Preference", "Lower", "Middle", "Upper", "Side Lower", "Side Upper")
    val genderOptions = listOf("Male", "Female", "Other")
    val concessionOptions = listOf("None", "Senior Citizen", "Student", "Divyangjan")

    init {
        viewModelScope.launch {
            authRepo.sessionFlow.collectLatest { session ->
                val owner = AccountIdentity.ownerId(session.loggedIn, session.userId, session.email)
                if (owner != bookingOwnerId) switchBookingOwner(owner)
                loggedIn = session.loggedIn
                userName = session.name
                userEmail = session.email
                userMobile = session.mobile
                userId = session.userId
                mobile = session.mobile
                email = session.email
                if (!session.loggedIn) {
                    clearAccountFlags()
                    supabaseLinked = false
                    favouriteRoutes = emptyList()
                } else {
                    refreshAccount()
                    loadFavouriteRoutes()
                }
                sessionSeen = true
                if (profilesReady) gateReady = true
            }
        }
        viewModelScope.launch {
            accountStore.profiles.collectLatest {
                profilesReady = true
                refreshAccount()
                if (sessionSeen) gateReady = true
            }
        }
    }

    private fun switchBookingOwner(owner: String) {
        bookingHistoryJob?.cancel()
        bookingOwnerId = owner
        bookings.clear()
        lastBooking = null
        lastJourney = null
        savedPassengers.clear()
        passengers.clear()
        passengers.add(Passenger())
        clearSeats()
        mobile = ""
        email = ""
        PaymentApi.bearerToken = null
        if (owner.isBlank()) return
        bookingHistoryJob = viewModelScope.launch {
            launch {
                bookingRepo.observeBookings(owner).collectLatest { list ->
                    if (bookingOwnerId == owner) {
                        bookings.clear()
                        bookings.addAll(list)
                    }
                }
            }
            launch {
                sessionStore.observeLastJourney(owner).collectLatest { journey ->
                    if (bookingOwnerId == owner) lastJourney = journey
                }
            }
            launch {
                sessionStore.observeSavedPassengers(owner).collectLatest { list ->
                    if (bookingOwnerId == owner) {
                        savedPassengers.clear()
                        savedPassengers.addAll(list)
                    }
                }
            }
        }
    }

    // ── Auth ──────────────────────────────────────────────
    fun register(name: String, email: String, password: String, onSuccess: () -> Unit) {
        authLoading = true
        authError = null
        viewModelScope.launch {
            val result = authRepo.register(name, email, password)
            authLoading = false
            if (result.success) {
                pendingVerifyEmail = email
                needsVerification = true
                otpEmailed = result.displayCode == null
                otpDisplayCode = result.displayCode
                onSuccess()
            } else {
                authError = result.error ?: "Registration failed"
            }
        }
    }

    fun verifyEmail(code: String, onDone: (String?) -> Unit) {
        authLoading = true
        authError = null
        viewModelScope.launch {
            val result = authRepo.verifyEmail(pendingVerifyEmail, code)
            authLoading = false
            if (result.success) {
                needsVerification = false
                appUnlocked = true
                onDone(null)
            } else {
                authError = result.error ?: "Verification failed"
                onDone(authError)
            }
        }
    }

    fun login(email: String, password: String, onSuccess: () -> Unit) {
        authLoading = true
        authError = null
        viewModelScope.launch {
            val result = authRepo.login(email, password)
            authLoading = false
            if (result.success) {
                onSuccess()
            } else if (result.needsVerification) {
                pendingVerifyEmail = result.email.ifBlank { email }
                needsVerification = true
                otpEmailed = result.displayCode == null
                otpDisplayCode = result.displayCode
                authError = result.error
            } else {
                authError = result.error ?: "Login failed"
            }
        }
    }

    fun resendCode(onDone: (String?) -> Unit = {}) {
        viewModelScope.launch {
            val result = authRepo.resendCode(pendingVerifyEmail)
            if (result.success) {
                otpEmailed = result.displayCode == null
                otpDisplayCode = result.displayCode
                onDone(null)
            } else {
                onDone(result.error)
            }
        }
    }

    fun logout(onDone: () -> Unit = {}) {
        viewModelScope.launch {
            runCatching { supabaseAuth.signOut() }
            PaymentApi.bearerToken = null
            pendingSupabaseEmail = ""
            useSupabaseConfirm = false
            authRepo.logout()
            appUnlocked = false
            onDone()
        }
    }

    fun gateDestination(): String = AccountGate.destination(
        loggedIn = loggedIn,
        profileComplete = profileComplete,
        mpinSet = mpinSet,
        mpinDeferred = mpinDeferred,
        needsUnlock = loggedIn && biometricOn && !appUnlocked
    )

    fun verifyMpin(pin: String): Boolean {
        val profile = currentProfile ?: return false
        return IrctcRules.secretHash(profile.userId, "mpin:$pin") == profile.mpinHash
    }

    fun markUnlocked() {
        appUnlocked = true
    }

    suspend fun refreshPaymentSession() {
        PaymentApi.bearerToken = supabaseAuth.currentSession()?.accessToken
    }

    fun updateProfile(name: String, mobile: String) {
        viewModelScope.launch {
            authRepo.updateProfile(name, mobile)
            this@AppViewModel.mobile = mobile
            if (userId.isNotBlank()) {
                val updated = accountStore.update(userId) { it.copy(fullName = name, mobile = mobile.filter { ch -> ch.isDigit() }) }
                applyProfile(updated)
                // Keep the server profile row in step when signed in
                // there (no-op without a session; failures are ignored).
                supabaseSync.pushLocalProfile(updated)
            }
        }
    }

    fun needsTatkalAadhaar(): Boolean = IrctcRules.isTatkalQuota(selectedQuota) && !aadhaarLinked

    fun startRegistration(visuallyImpaired: Boolean) {
        regVisuallyImpaired = visuallyImpaired
        regUserId = ""
        regFullName = ""
        regPassword = ""
        regMobile = ""
        regEmail = ""
        regLanguage = "English"
        regCaptcha = IrctcRules.newCaptcha()
        regEmailOtp = ""
        regMobileOtp = ""
        regStep = 0
        regIdMessage = ""
        regIdChecked = ""
        useSupabaseConfirm = false
        pendingSupabaseEmail = ""
        supabaseDelivery = ""
        cloudBusy = false
    }

    fun beginCloudSignUp(onDone: (String?) -> Unit) {
        if (useSupabaseConfirm) {
            onDone(null)
            return
        }
        cloudBusy = true
        viewModelScope.launch {
            val phone = IrctcRules.toIndianE164(regMobile)
            if (phone == null) {
                cloudBusy = false
                onDone("Enter a valid 10-digit mobile number")
                return@launch
            }
            val call = withTimeoutOrNull(15_000) {
                supabaseAuth.signUp(regEmail.trim(), regPassword, regFullName.trim(), regMobile.trim(), regUserId.trim())
            }
            cloudBusy = false
            when (call) {
                is SupabaseCall.SignedIn -> {
                    useSupabaseConfirm = true
                    pendingSupabaseEmail = regEmail.trim()
                    supabaseDelivery = "Supabase account created for ${regEmail.trim()}. Tap VERIFY to finish setting up this device, then sign in."
                    onDone(null)
                }
                is SupabaseCall.NeedsConfirmation -> {
                    useSupabaseConfirm = true
                    pendingSupabaseEmail = regEmail.trim()
                    supabaseDelivery = "We emailed a confirmation link to ${regEmail.trim()}. Open it to activate your account, then tap VERIFY here and sign in with your email and password."
                    onDone(null)
                }
                is SupabaseCall.Err -> {
                    when {
                        call.message.contains("already", ignoreCase = true) ->
                            onDone("This email already has a Supabase account. Go back and sign in with it.")
                        call.message.startsWith("Could not reach") ->
                            onDone("Could not reach the server. Check your connection and try again.")
                        else -> onDone(call.message)
                    }
                }
                null -> onDone("Could not reach the server. Check your connection and try again.")
            }
        }
    }

    fun resendRegistrationCode(onDone: (String?) -> Unit) {
        viewModelScope.launch {
            if (useSupabaseConfirm) {
                val email = pendingSupabaseEmail.ifBlank { regEmail.trim() }
                when (val call = supabaseAuth.resendConfirmation(email)) {
                    is SupabaseCall.Err -> onDone(call.message)
                    else -> {
                        supabaseDelivery = "Confirmation link re-sent to $email. Open it, then tap VERIFY here."
                        onDone(null)
                    }
                }
            } else {
                issueOtps()
                onDone(null)
            }
        }
    }

    fun submitRegistrationCodes(emailCode: String, mobileCode: String, onDone: (String?) -> Unit) {
        viewModelScope.launch {
            if (!useSupabaseConfirm) {
                // Demo accounts confirm with the on-screen codes. Supabase
                // accounts confirm through the email link, so there is no
                // code to check for them.
                val problem = when {
                    !IrctcRules.otpMatches(regEmailOtp, emailCode) -> "Email OTP does not match"
                    !IrctcRules.otpMatches(regMobileOtp, mobileCode) -> "Mobile OTP does not match"
                    else -> null
                }
                if (problem != null) {
                    onDone(problem)
                    return@launch
                }
            }
            try {
                val profile = accountStore.create(
                    userId = regUserId,
                    fullName = regFullName,
                    email = regEmail,
                    mobile = regMobile,
                    password = regPassword,
                    language = regLanguage,
                    authProvider = if (useSupabaseConfirm) AccountProvider.SUPABASE else AccountProvider.LOCAL_DEMO,
                    cloudUsername = ""
                )
                loginPrefill = profile.userId
                regPassword = ""
                regEmailOtp = ""
                regMobileOtp = ""
                useSupabaseConfirm = false
                pendingSupabaseEmail = ""
                onDone(null)
            } catch (e: Exception) {
                onDone(e.message ?: "Could not create the account")
            }
        }
    }

    fun checkUserId() {
        val err = IrctcRules.validateUserId(regUserId)
        if (err != null) {
            regIdMessage = err
            regIdChecked = ""
            return
        }
        viewModelScope.launch {
            val taken = accountStore.isUserIdTaken(regUserId)
            regIdChecked = if (taken) "" else regUserId.trim()
            regIdMessage = if (taken) "User ID is already taken" else "User ID is available"
        }
    }

    fun refreshCaptcha() {
        regCaptcha = IrctcRules.newCaptcha()
    }

    fun issueOtps() {
        regEmailOtp = IrctcRules.newOtp()
        regMobileOtp = IrctcRules.newOtp()
    }

    fun validateNewContact(onResult: (String?) -> Unit) {
        viewModelScope.launch {
            val message = when {
                accountStore.isEmailTaken(regEmail) -> "Email is already registered"
                accountStore.isMobileTaken(regMobile) -> "Mobile is already registered"
                else -> null
            }
            onResult(message)
        }
    }

    fun finishRegistration(onDone: (String?) -> Unit) {
        viewModelScope.launch {
            try {
                val profile = accountStore.create(
                    userId = regUserId,
                    fullName = regFullName,
                    email = regEmail,
                    mobile = regMobile,
                    password = regPassword,
                    language = regLanguage,
                    authProvider = AccountProvider.LOCAL_DEMO
                )
                loginPrefill = profile.userId
                regPassword = ""
                regEmailOtp = ""
                regMobileOtp = ""
                onDone(null)
            } catch (e: Exception) {
                onDone(e.message ?: "Could not create the account")
            }
        }
    }

    fun signIn(
        idOrEmail: String,
        password: String,
        onSuccess: () -> Unit,
        onNeedVerify: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        authLoading = true
        authError = null
        viewModelScope.launch {
            // Cloud accounts only. Sign-in goes to Supabase with the
            // account email; the server profile is then pulled down and
            // mirrored on this device. There is no on-device demo or
            // legacy sign-in anymore.
            val savedProfile = accountStore.findProfile(idOrEmail)
            var supabaseEmail = when {
                idOrEmail.contains("@") -> idOrEmail.trim()
                else -> savedProfile?.email?.takeIf { it.contains("@") }.orEmpty()
            }
            if (supabaseEmail.isBlank() && !idOrEmail.contains("@")) {
                // A bare user ID with no on-device mirror (fresh install):
                // resolve it against the server account directory, so the
                // user ID signs in on any device, not just this one.
                supabaseEmail = withTimeoutOrNull(12_000) {
                    supabaseAuth.lookupEmailForUsername(idOrEmail)
                }.orEmpty()
            }
            if (supabaseEmail.isBlank()) {
                val message = "No account found for that user ID. Check the ID, or sign in with your registered email."
                authLoading = false
                authError = message
                onError(message)
                return@launch
            }
            when (val sb = withTimeoutOrNull(12_000) {
                supabaseAuth.signIn(supabaseEmail, password)
            }) {
                is SupabaseCall.SignedIn -> {
                    adoptSupabaseUser(sb.session, password)
                    appUnlocked = true
                    authLoading = false
                    onSuccess()
                    return@launch
                }
                is SupabaseCall.Err -> {
                    val message = if (sb.message.contains("not confirmed", ignoreCase = true)) {
                        "Confirm your email first: open the confirmation link Supabase sent you, then sign in."
                    } else {
                        sb.message
                    }
                    authLoading = false
                    authError = message
                    onError(message)
                    return@launch
                }
                else -> {
                    val message = "Could not reach the server. Check your connection and try again."
                    authLoading = false
                    authError = message
                    onError(message)
                    return@launch
                }
            }
        }
    }

    fun sendSupabasePasswordReset(email: String, onDone: (String) -> Unit) {
        viewModelScope.launch {
            val target = email.trim()
            if (!target.contains("@")) {
                onDone("Enter the account email to get a reset link.")
                return@launch
            }
            // Same message whatever Supabase answers, so this dialog cannot
            // be used to probe which emails have accounts.
            supabaseAuth.sendPasswordReset(target)
            onDone("If a Supabase account exists for $target, a password reset link is on its way.")
        }
    }

    fun lookupUserId(emailOrMobile: String, onResult: (String?) -> Unit) {
        viewModelScope.launch {
            onResult(accountStore.findUserId(emailOrMobile))
        }
    }

    fun completeProfile(draft: ProfileDraft, onDone: (String?) -> Unit) {
        val problem = IrctcRules.validateProfile(draft)
        if (problem != null) {
            onDone(problem)
            return
        }
        if (userId.isBlank()) {
            onDone("Sign in with your user ID before saving the profile")
            return
        }
        viewModelScope.launch {
            try {
                val updated = accountStore.update(userId) {
                    it.copy(
                        gender = draft.gender,
                        dob = draft.dob.trim(),
                        occupation = draft.occupation,
                        maritalStatus = draft.maritalStatus,
                        nationality = draft.nationality.trim(),
                        addressLine = draft.addressLine.trim(),
                        city = draft.city.trim(),
                        state = draft.state,
                        country = draft.country.trim(),
                        pinCode = draft.pinCode,
                        profileComplete = true
                    )
                }
                sessionStore.saveSession(updated.fullName, updated.email, updated.mobile, updated.userId)
                applyProfile(updated)
                // The profile lives on the server now: push the completed
                // activation so it is recognised on every device.
                supabaseSync.pushLocalProfile(updated)
                onDone(null)
            } catch (e: Exception) {
                onDone(e.message ?: "Could not save the profile")
            }
        }
    }

    fun saveMpin(pin: String, confirm: String, biometric: Boolean, onDone: (String?) -> Unit) {
        val problem = IrctcRules.validateMpin(pin, confirm)
        if (problem != null) {
            onDone(problem)
            return
        }
        if (userId.isBlank()) {
            onDone("Sign in again to save the PIN")
            return
        }
        viewModelScope.launch {
            try {
                val updated = accountStore.update(userId) {
                    it.copy(
                        mpinHash = IrctcRules.secretHash(it.userId, "mpin:$pin"),
                        biometricEnabled = biometric,
                        mpinDeferred = false
                    )
                }
                applyProfile(updated)
                onDone(null)
            } catch (e: Exception) {
                onDone(e.message ?: "Could not save the PIN")
            }
        }
    }

    fun deferMpin(onDone: () -> Unit) {
        viewModelScope.launch {
            if (userId.isNotBlank()) {
                val updated = accountStore.update(userId) { it.copy(mpinDeferred = true) }
                applyProfile(updated)
            } else {
                mpinDeferred = true
            }
            onDone()
        }
    }

    fun saveAadhaarLink(kind: String, last4: String, onDone: (String?) -> Unit) {
        if (userId.isBlank()) {
            onDone("Register an account before linking Aadhaar")
            return
        }
        if (last4.length != 4 || last4.any { !it.isDigit() }) {
            onDone("Could not save the link")
            return
        }
        viewModelScope.launch {
            try {
                val updated = accountStore.update(userId) {
                    it.copy(aadhaarLinked = true, aadhaarLast4 = last4, aadhaarKind = kind)
                }
                applyProfile(updated)
                onDone(null)
            } catch (e: Exception) {
                onDone(e.message ?: "Could not save the link")
            }
        }
    }

    /**
     * Mirrors a signed-in Supabase user into a local profile: bookings stay
     * owned by the local user ID, while the Supabase UUID lives in
     * cloudUsername and the Supabase tokens stay in SupabaseTokenStore for
     * backend sync.
     */
    private suspend fun adoptSupabaseUser(session: SupabaseSession, password: String) {
        val mail = session.email.trim()
        val name = session.fullName
        val list = accountStore.profiles.first()
        val match = list.find { it.cloudUsername == session.userId }
            ?: list.find { mail.isNotBlank() && it.email.equals(mail, ignoreCase = true) }
        // The server profile is the source of truth. Pull it and overlay
        // it on the on-device mirror, so a user who already finished
        // activation is recognised on any device instead of being asked
        // to complete the profile again after a reinstall.
        val remote = supabaseSync.pullProfile()
        fun overlay(base: RailProfile): RailProfile {
            if (remote == null) return base
            return base.copy(
                fullName = remote.fullName.ifBlank { base.fullName },
                email = remote.email.ifBlank { base.email },
                mobile = remote.mobile.ifBlank { base.mobile },
                gender = remote.gender.ifBlank { base.gender },
                dob = remote.dob.ifBlank { base.dob },
                occupation = remote.occupation.ifBlank { base.occupation },
                maritalStatus = remote.maritalStatus.ifBlank { base.maritalStatus },
                nationality = remote.nationality.ifBlank { base.nationality },
                addressLine = remote.addressLine.ifBlank { base.addressLine },
                city = remote.city.ifBlank { base.city },
                state = remote.state.ifBlank { base.state },
                country = remote.country.ifBlank { base.country },
                pinCode = remote.pinCode.ifBlank { base.pinCode },
                profileComplete = base.profileComplete || remote.profileComplete
            )
        }
        val profile = if (match != null) {
            accountStore.update(match.userId) {
                overlay(it).copy(
                    email = mail.ifBlank { it.email },
                    passwordHash = IrctcRules.secretHash(it.userId, password),
                    authProvider = AccountProvider.SUPABASE,
                    cloudUsername = session.userId
                )
            }
        } else {
            val id = accountStore.unusedUserId(IrctcRules.userIdFromEmail(mail.ifBlank { "railxuser" }))
            val created = accountStore.create(
                userId = id,
                fullName = name.ifBlank { id },
                email = mail.ifBlank { "$id@users.railx.local" },
                mobile = "",
                password = password,
                language = "English",
                profileComplete = false,
                authProvider = AccountProvider.SUPABASE,
                cloudUsername = session.userId
            )
            if (remote != null) accountStore.update(id) { overlay(it) } else created
        }
        // If this device was ahead of the server (activation finished
        // here first), push the completed profile up so it stays synced.
        if (remote != null && profile.profileComplete && !remote.profileComplete) {
            supabaseSync.pushLocalProfile(profile)
        }
        sessionStore.saveSession(profile.fullName, profile.email, profile.mobile, profile.userId)
        applyProfile(profile)
        loggedIn = true
        supabaseLinked = true
        favouriteRoutes = supabaseSync.pullFavouriteRoutes()
    }

    private suspend fun bridgeLegacyAccount(name: String, email: String, password: String) {
        val mail = email.trim()
        if (!mail.contains("@")) {
            profileComplete = true
            userName = name
            userEmail = mail
            loggedIn = true
            return
        }
        val seed = IrctcRules.userIdFromEmail(mail)
        val id = accountStore.unusedUserId(seed)
        val profile = accountStore.create(
            userId = id,
            fullName = name.ifBlank { id },
            email = mail,
            mobile = userMobile.filter { it.isDigit() },
            password = password,
            language = "English",
            profileComplete = true
        )
        sessionStore.saveSession(profile.fullName, profile.email, profile.mobile, profile.userId)
        applyProfile(profile)
        loggedIn = true
    }

    private suspend fun refreshAccount() {
        if (!profilesReady || !loggedIn) return
        val list = accountStore.profiles.first()
        val match = list.find { userId.isNotBlank() && it.userId.equals(userId, ignoreCase = true) }
            ?: list.find { userEmail.isNotBlank() && it.email.equals(userEmail, ignoreCase = true) }
        if (match != null) applyProfile(match) else if (userId.isBlank()) profileComplete = true
    }

    private fun applyProfile(profile: RailProfile) {
        currentProfile = profile
        userId = profile.userId
        userName = profile.fullName
        userEmail = profile.email
        userMobile = profile.mobile
        email = profile.email
        mobile = profile.mobile
        profileComplete = profile.profileComplete
        mpinSet = profile.mpinSet
        mpinDeferred = profile.mpinDeferred
        biometricOn = profile.biometricEnabled
        aadhaarLinked = profile.aadhaarLinked
        aadhaarLast4 = profile.aadhaarLast4
        aadhaarKind = profile.aadhaarKind
    }

    private fun clearAccountFlags() {
        currentProfile = null
        appUnlocked = false
        profileComplete = false
        mpinSet = false
        mpinDeferred = false
        biometricOn = false
        aadhaarLinked = false
        aadhaarLast4 = ""
        aadhaarKind = ""
    }

    // ── Search / Trains ───────────────────────────────────
    fun stationName(code: String) =
        MockData.stations.find { it.code == code }?.let { "${it.name} (${it.code})" }
            ?: com.kaushik.railway.data.StationDirectory.byCode(code)?.let { "${it.name} (${it.code})" }
            ?: stationSuggestions.find { it.code == code }?.let { "${it.name} (${it.code})" }
            ?: code

    fun quotaCode() = selectedQuota.take(2)

    fun searchTrainsLive() {
        trainSearchJob?.cancel()
        val request = ++trainSearchVersion
        val from = fromCode
        val to = toCode
        val date = journeyDate
        searchLoading = true
        searchError = null
        isOfflineHint = false
        trains.clear()
        trainSearchJob = viewModelScope.launch {
            try {
                val result = trainRepo.searchTrains(from, to, toApiDate(date))
                if (request != trainSearchVersion || from != fromCode || to != toCode || date != journeyDate) return@launch
                result.onSuccess { list ->
                    trains.addAll(list)
                }.onFailure { e ->
                    trains.addAll(MockData.trains(from, to))
                    isOfflineHint = true
                    val msg = e.message.orEmpty()
                    searchError = if (msg.contains("quota", ignoreCase = true) || msg.contains("TOO_MANY", ignoreCase = true)) {
                        "Live search limit reached — the RailRadar free plan allows 100 requests a month and this month's quota is used up. Showing demo trains."
                    } else {
                        "Live search is unavailable. Showing demo trains."
                    }
                }
            } finally {
                if (request == trainSearchVersion) searchLoading = false
            }
        }
    }

    fun loadAvailability(train: Train) {
        availabilityJob?.cancel()
        vacancyJob?.cancel()
        ++vacancyVersion
        val request = ++availabilityVersion
        val date = journeyDate
        val quota = quotaCode()
        val classFilter = selectedClass
        selectedTrain = train
        availLoading = true
        availError = null
        isOfflineHint = false
        classRows.clear()
        vacancy = null
        vacancyLoading = false
        vacancyError = null
        val classes = if (classFilter == "All Classes") {
            train.classes.map { it.code }.ifEmpty { listOf("1A", "2A", "3A", "3E", "SL", "CC", "EC", "2S") }
        } else listOf(classFilter)
        fun isCurrent() = request == availabilityVersion && selectedTrain == train &&
            date == journeyDate && quota == quotaCode() && classFilter == selectedClass
        availabilityJob = viewModelScope.launch {
            val failed = mutableListOf<String>()
            try {
                val result = trainRepo.loadClassAvailability(train, toApiDate(date), classes, quota) { cls, row ->
                    if (isCurrent()) {
                        row.onSuccess { value ->
                            val position = classRows.indexOfFirst { classes.indexOf(it.code) > classes.indexOf(cls) }
                            if (position < 0) classRows.add(value) else classRows.add(position, value)
                        }.onFailure {
                            failed.add(cls)
                            availError = "Could not load ${failed.joinToString()}. Other classes remain available."
                        }
                    }
                }
                if (!isCurrent()) return@launch
                if (result.isFailure && classRows.isEmpty()) {
                    classRows.addAll(train.classes.ifEmpty {
                        MockData.trains(train.fromCode, train.toCode).firstOrNull()?.classes.orEmpty()
                    }.filter { it.code in classes })
                    isOfflineHint = true
                    availError = "Live availability is unavailable. Showing demo availability."
                }
            } finally {
                if (request == availabilityVersion) availLoading = false
            }
        }
    }

    fun loadVacancyChart(cls: TrainClassAvail) {
        vacancyJob?.cancel()
        val request = ++vacancyVersion
        selectedTravelClass = cls
        val train = selectedTrain ?: return
        val date = journeyDate
        val quota = quotaCode()
        vacancy = null
        vacancyError = null
        vacancyLoading = true
        vacancyJob = viewModelScope.launch {
            try {
                val result = trainRepo.getVacancy(
                    train.number, train.fromCode, train.toCode, toApiDate(date), cls.code, quota
                )
                if (request != vacancyVersion || selectedTrain != train || selectedTravelClass != cls ||
                    date != journeyDate || quota != quotaCode()) return@launch
                result.onSuccess { result ->
                    vacancy = result
                    selectedTravelClass = cls.copy(fare = result.fare, status = result.status)
                }.onFailure { vacancyError = it.message ?: "Could not load availability. Try again." }
            } finally {
                if (request == vacancyVersion) vacancyLoading = false
            }
        }
    }

    fun lookupPnr(pnr: String) {
        val digits = pnr.filter { it.isDigit() }
        pnrLoading = true
        pnrError = null
        pnrResult = null
        pnrNote = ""
        viewModelScope.launch {
            // RailX's own bookings first: every PNR issued after payment is
            // saved on this device and in the account's cloud database, so a
            // RailX PNR always resolves — no external lookup needed.
            val local = bookings.firstOrNull { it.pnr == digits }
                ?: lastBooking?.takeIf { it.pnr == digits }
            val remote = if (local == null) {
                supabaseSync.pullBookings().firstOrNull { it.pnr == digits }
            } else null
            val own = local ?: remote
            if (own != null) {
                pnrResult = own.toPnrResult()
                pnrNote = "From your RailX bookings"
                pnrLoading = false
                return@launch
            }
            // Not one of his bookings: fall back to the live RailRadar PNR
            // endpoint (a paid-plan endpoint; the free plan declines it).
            val result = trainRepo.checkPnr(digits)
            pnrLoading = false
            result.onSuccess { pnrResult = it }
                .onFailure {
                    val msg = it.message.orEmpty()
                    pnrError = if (msg.contains("subscription", ignoreCase = true) ||
                        msg.contains("PLAN_UPGRADE", ignoreCase = true)
                    ) {
                        "This PNR is not one of your RailX bookings, and live IRCTC PNR lookup needs a paid RailRadar plan — so only your own booking PNRs can be shown right now."
                    } else {
                        msg.ifBlank { "PNR lookup failed" }
                    }
                }
        }
    }

    /** Present one of the user's own bookings as a PNR-status result. */
    private fun Booking.toPnrResult(): PnrResult {
        val cancelled = status.equals("CANCELLED", ignoreCase = true)
        val statusText = when {
            cancelled -> "Cancelled" + if (refundAmount > 0) " · Refund ₹$refundAmount" else ""
            classStatus.contains("RAC", ignoreCase = true) -> "RAC"
            classStatus.contains("WL", ignoreCase = true) -> "Waitlisted"
            else -> "Confirmed"
        }
        return PnrResult(
            pnr = pnr,
            trainNo = train.number,
            trainName = train.name,
            fromName = fromName.ifBlank { train.from },
            toName = toName.ifBlank { train.to },
            date = date,
            travelClass = travelClass.code,
            quota = quota,
            chart = statusText,
            fare = amount,
            passengers = passengers.map { p ->
                "${p.name} · ${p.age}/${p.gender} · $statusText"
            }
        )
    }

    fun loadRunning(trainNo: String) {
        runningLoading = true
        runningError = null
        runningStops.clear()
        viewModelScope.launch {
            val result = trainRepo.trackTrain(trainNo, toApiDate(journeyDate))
            runningLoading = false
            result.onSuccess { stops ->
                runningStops.addAll(stops)
                runningNote = if (stops.isEmpty()) "No live timeline for this train." else "Live from RailRadar"
            }.onFailure { runningError = it.message }
        }
    }

    fun searchStations(query: String, field: String = "from") {
        val normalized = query.trim()
        if (normalized == lastStationQuery && field == stationSuggestionField) return
        stationSearchJob?.cancel()
        val request = ++stationSearchVersion
        lastStationQuery = normalized
        stationSuggestionField = field
        stationSuggestions.clear()
        if (normalized.length < 2) return
        // Instant offline suggestions from the bundled station directory —
        // no waiting, no API quota spent. The network fallback below only
        // runs for a station the directory somehow doesn't know.
        val local = com.kaushik.railway.data.StationDirectory.search(normalized)
        if (local.isNotEmpty()) {
            stationSuggestions.addAll(local)
            return
        }
        stationSearchJob = viewModelScope.launch {
            delay(300)
            val found = trainRepo.searchStations(normalized)
            if (request != stationSearchVersion || field != stationSuggestionField) return@launch
            stationSuggestions.addAll(found.ifEmpty {
                val q = normalized.lowercase()
                MockData.stations.filter {
                    it.code.lowercase().contains(q) || it.name.lowercase().contains(q)
                }.take(20)
            })
        }
    }

    fun clearStationSuggestions() {
        stationSearchJob?.cancel()
        ++stationSearchVersion
        lastStationQuery = ""
        stationSuggestions.clear()
    }

    fun payableAmount(): Int {
        val cls = selectedTravelClass ?: return 1
        val ins = if (insurance) passengers.size * 15 else 0
        return (cls.fare * passengers.size + ins).coerceAtLeast(1)
    }

    suspend fun confirmBooking(
        paymentId: String = "",
        orderId: String = "",
        expectedOwnerId: String
    ): Booking {
        val session = sessionStore.sessionFlow.first()
        val activeOwner = AccountIdentity.ownerId(session.loggedIn, session.userId, session.email)
        check(expectedOwnerId.isNotBlank() && expectedOwnerId == activeOwner && activeOwner == bookingOwnerId) {
            "Your account changed during checkout. Sign in to the original account to continue."
        }
        val train = checkNotNull(selectedTrain) { "Choose your train again." }
        val cls = checkNotNull(selectedTravelClass) { "Choose your travel class again." }
        val booking = Booking(
            pnr = (1..10).map { Random.nextInt(0, 10) }.joinToString(""),
            train = train,
            travelClass = cls,
            date = journeyDate,
            quota = quotaCode(),
            passengers = passengers.toList(),
            contact = mobile.ifBlank { userMobile },
            status = "PAID",
            amount = payableAmount(),
            fromName = stationName(fromCode),
            toName = stationName(toCode),
            paymentId = paymentId,
            orderId = orderId,
            departTime = train.depart,
            classStatus = cls.status
        )
        val journey = LastJourney(
            fromCode = fromCode,
            toCode = toCode,
            classCode = cls.code,
            quota = selectedQuota,
            trainNumber = train.number,
            trainName = train.name
        )
        // Persist under the captured account before reporting a successful booking.
        bookingRepo.save(expectedOwnerId, booking)
        sessionStore.saveLastJourney(expectedOwnerId, journey)
        sessionStore.savePassengers(expectedOwnerId, booking.passengers)
        if (bookingOwnerId == expectedOwnerId) lastBooking = booking
        // Best-effort mirror to the Supabase backend when a Supabase session
        // exists; a no-op otherwise, and it never blocks the booking result.
        viewModelScope.launch {
            supabaseSync.pushBooking(booking)
            booking.passengers.forEach { supabaseSync.pushPassenger(it) }
        }
        return booking
    }

    /** IRCTC-rule refund quote for cancelling this booking right now. */
    fun refundQuoteFor(booking: Booking): RefundQuote {
        val departure = RefundPolicy.departureOf(booking.date, booking.departTime)
            ?: RefundPolicy.departureOf(booking.date, "")
            ?: java.time.LocalDateTime.now()
        return RefundPolicy.quote(
            classCode = booking.travelClass.code,
            farePerPassenger = booking.travelClass.fare,
            passengers = booking.passengers.size,
            ticketStatus = booking.classStatus.ifBlank { booking.travelClass.status },
            quota = booking.quota,
            departure = departure
        )
    }

    /** Cancellation Advisor: the refund for cancelling at [now], and when it next drops. */
    fun cancellationAdviceFor(
        booking: Booking,
        now: java.time.LocalDateTime = java.time.LocalDateTime.now()
    ): CancellationAdvice {
        val departure = RefundPolicy.departureOf(booking.date, booking.departTime)
            ?: RefundPolicy.departureOf(booking.date, "")
            ?: java.time.LocalDateTime.now()
        return CancellationAdvisor.advise(
            classCode = booking.travelClass.code,
            farePerPassenger = booking.travelClass.fare,
            passengers = booking.passengers.size,
            ticketStatus = booking.classStatus.ifBlank { booking.travelClass.status },
            quota = booking.quota,
            departure = departure,
            now = now
        )
    }

    fun cancel(pnr: String) {
        val owner = bookingOwnerId.takeIf { it.isNotBlank() } ?: return
        val booking = bookings.firstOrNull { it.pnr == pnr }
            ?: lastBooking?.takeIf { it.pnr == pnr }
        val quote = booking?.let { refundQuoteFor(it) }
        viewModelScope.launch {
            bookingRepo.cancel(owner, pnr, quote?.totalCharge ?: 0, quote?.refundAmount ?: 0)
            supabaseSync.pushBookingStatus(pnr, "CANCELLED")
            if (bookingOwnerId == owner && lastBooking?.pnr == pnr) {
                lastBooking = lastBooking?.copy(
                    status = "CANCELLED",
                    cancelCharge = quote?.totalCharge ?: 0,
                    refundAmount = quote?.refundAmount ?: 0
                )
            }
        }
    }

    fun toggleSeat(id: String, label: String) {
        if (id in selectedSeatIds) {
            selectedSeatIds.remove(id)
        } else if (selectedSeatIds.size < passengers.size) {
            selectedSeatIds.add(id)
        }
        selectedSeatLabels = selectedSeatIds.joinToString(", ")
    }

    fun clearSeats() {
        selectedSeatIds.clear()
        selectedSeatLabels = ""
    }

    fun addPassenger() {
        if (passengers.size < 6) passengers.add(Passenger())
    }

    fun removePassenger(index: Int) {
        if (passengers.size > 1 && index in passengers.indices) passengers.removeAt(index)
    }

    fun updatePassenger(index: Int, p: Passenger) {
        if (index in passengers.indices) passengers[index] = p
    }

    /** One-tap rebook: restore last route into search form */
    fun applyLastJourney() {
        val j = lastJourney ?: return
        if (j.fromCode.isNotBlank()) fromCode = j.fromCode
        if (j.toCode.isNotBlank()) toCode = j.toCode
        if (j.quota.isNotBlank()) selectedQuota = j.quota
        if (j.classCode.isNotBlank()) selectedClass = j.classCode
        journeyDate = defaultDate()
    }

    fun loadSavedPassengers() {
        if (savedPassengers.isEmpty()) return
        passengers.clear()
        passengers.addAll(savedPassengers.map { it.copy() })
        if (passengers.isEmpty()) passengers.add(Passenger())
    }

    fun swapStations() {
        val tmp = fromCode
        fromCode = toCode
        toCode = tmp
    }

    companion object {
        fun defaultDate(): String {
            val cal = Calendar.getInstance()
            cal.add(Calendar.DAY_OF_YEAR, 1)
            return SimpleDateFormat("dd MMM yyyy", Locale.ENGLISH).format(cal.time)
        }

        fun upcomingDates(): List<String> {
            val fmt = SimpleDateFormat("dd MMM yyyy", Locale.ENGLISH)
            val cal = Calendar.getInstance()
            return (0 until 14).map {
                if (it > 0) cal.add(Calendar.DAY_OF_YEAR, 1)
                fmt.format(cal.time)
            }
        }

        fun toApiDate(display: String): String {
            return try {
                val inFmt = SimpleDateFormat("dd MMM yyyy", Locale.ENGLISH)
                val outFmt = SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH)
                outFmt.format(inFmt.parse(display)!!)
            } catch (_: Exception) {
                display
            }
        }
    }
}
