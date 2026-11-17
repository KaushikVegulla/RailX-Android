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
import com.kaushik.railway.data.IrctcRules
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
    var useSupabaseConfirm by mutableStateOf(false)
    var supabaseDelivery by mutableStateOf("")
    private var sessionSeen = false
    private var pendingSupabaseEmail = ""

    // Search
    var fromCode by mutableStateOf("NDLS")
    var toCode by mutableStateOf("MMCT")
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
                if (!session.loggedIn) clearAccountFlags() else refreshAccount()
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
                supabaseAuth.signUp(regEmail.trim(), regPassword, regFullName.trim(), regMobile.trim())
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
                        call.message.startsWith("Could not reach") -> {
                            // Supabase unreachable: fall back to an on-device demo account.
                            useSupabaseConfirm = false
                            if (regEmailOtp.isBlank() || regMobileOtp.isBlank()) issueOtps()
                            supabaseDelivery = "Cloud registration is unavailable. This creates a demo account stored only on this device. Use the codes shown here."
                            onDone(null)
                        }
                        else -> onDone(call.message)
                    }
                }
                null -> {
                    useSupabaseConfirm = false
                    if (regEmailOtp.isBlank() || regMobileOtp.isBlank()) issueOtps()
                    supabaseDelivery = "Cloud registration is unavailable. This creates a demo account stored only on this device. Use the codes shown here."
                    onDone(null)
                }
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
            val savedProfile = accountStore.findProfile(idOrEmail)
            if (savedProfile?.authProvider == AccountProvider.LOCAL_DEMO) {
                try {
                    val profile = accountStore.authenticate(idOrEmail, password)
                        ?: error("No demo account on this device. Register again.")
                    // Demo credentials never authenticate a cloud session or payment request.
                    PaymentApi.bearerToken = null
                    sessionStore.saveSession(profile.fullName, profile.email, profile.mobile, profile.userId)
                    applyProfile(profile)
                    loggedIn = true
                    appUnlocked = true
                    authLoading = false
                    onSuccess()
                } catch (e: Exception) {
                    authLoading = false
                    authError = e.message ?: "Demo sign-in failed"
                    onError(authError!!)
                }
                return@launch
            }
            // Supabase accounts sign in against the Supabase backend first.
            // A failed or unreachable Supabase attempt falls through to the
            // local / legacy flow so nobody is locked out by the migration.
            val supabaseAccount = savedProfile?.authProvider == AccountProvider.SUPABASE
            val trySupabase = supabaseAccount ||
                (savedProfile == null && idOrEmail.contains("@"))
            if (trySupabase) {
                when (val sb = withTimeoutOrNull(12_000) {
                    supabaseAuth.signIn(idOrEmail.trim(), password)
                }) {
                    is SupabaseCall.SignedIn -> {
                        adoptSupabaseUser(sb.session, password)
                        appUnlocked = true
                        authLoading = false
                        onSuccess()
                        return@launch
                    }
                    is SupabaseCall.Err -> {
                        if (sb.message.contains("not confirmed", ignoreCase = true)) {
                            val message = "Confirm your email first: open the confirmation link Supabase sent you, then sign in."
                            authLoading = false
                            authError = message
                            onError(message)
                            return@launch
                        }
                        if (supabaseAccount) {
                            authLoading = false
                            authError = sb.message
                            onError(sb.message)
                            return@launch
                        }
                    }
                    else -> Unit
                }
            }
            if (localProfile != null) {
                sessionStore.saveSession(localProfile.fullName, localProfile.email, localProfile.mobile, localProfile.userId)
                applyProfile(localProfile)
                loggedIn = true
                appUnlocked = true
                authLoading = false
                onSuccess()
                return@launch
            }
            val result = authRepo.login(idOrEmail, password)
            if (result.success) {
                try {
                    bridgeLegacyAccount(result.name, result.email.ifBlank { idOrEmail }, password)
                } catch (_: Exception) {
                    profileComplete = true
                    userName = result.name
                    userEmail = result.email.ifBlank { idOrEmail }
                    email = userEmail
                    loggedIn = true
                }
                appUnlocked = true
                authLoading = false
                onSuccess()
                return@launch
            }
            if (result.needsVerification) {
                pendingVerifyEmail = result.email.ifBlank { idOrEmail }
                needsVerification = true
                otpEmailed = result.displayCode == null
                otpDisplayCode = result.displayCode
                authError = result.error
                authLoading = false
                onNeedVerify(pendingVerifyEmail)
                return@launch
            }
            val message = localError ?: result.error ?: "No account for that user ID. Register first."
            authLoading = false
            authError = message
            onError(message)
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
        val profile = if (match != null) {
            accountStore.update(match.userId) {
                it.copy(
                    fullName = name.ifBlank { it.fullName },
                    email = mail.ifBlank { it.email },
                    passwordHash = IrctcRules.secretHash(it.userId, password),
                    authProvider = AccountProvider.SUPABASE,
                    cloudUsername = session.userId
                )
            }
        } else {
            val id = accountStore.unusedUserId(IrctcRules.userIdFromEmail(mail.ifBlank { "railxuser" }))
            accountStore.create(
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
        }
        sessionStore.saveSession(profile.fullName, profile.email, profile.mobile, profile.userId)
        applyProfile(profile)
        loggedIn = true
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
                }.onFailure {
                    trains.addAll(MockData.trains(from, to))
                    isOfflineHint = true
                    searchError = "Live search is unavailable. Showing demo trains."
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
        pnrLoading = true
        pnrError = null
        pnrResult = null
        viewModelScope.launch {
            val result = trainRepo.checkPnr(pnr)
            pnrLoading = false
            result.onSuccess { pnrResult = it }
                .onFailure { pnrError = it.message ?: "PNR lookup failed" }
        }
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
            orderId = orderId
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

    fun cancel(pnr: String) {
        val owner = bookingOwnerId.takeIf { it.isNotBlank() } ?: return
        viewModelScope.launch {
            bookingRepo.cancel(owner, pnr)
            supabaseSync.pushBookingStatus(pnr, "CANCELLED")
            if (bookingOwnerId == owner && lastBooking?.pnr == pnr) {
                lastBooking = lastBooking?.copy(status = "CANCELLED")
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
