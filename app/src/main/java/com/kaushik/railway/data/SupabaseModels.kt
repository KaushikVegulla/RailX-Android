package com.kaushik.railway.data

/**
 * Row models for the Supabase tables in supabase/schema.sql.
 * toRow()/fromRow() are pure Map conversions so they can be unit-tested
 * without Android or a network call.
 */

data class RemoteProfile(
    val id: String,
    val fullName: String = "",
    val email: String = "",
    val mobile: String = "",
    val gender: String = "",
    val dob: String = "",
    val occupation: String = "",
    val maritalStatus: String = "",
    val nationality: String = "",
    val addressLine: String = "",
    val city: String = "",
    val state: String = "",
    val country: String = "",
    val pinCode: String = "",
    val profileComplete: Boolean = false
) {
    fun toRow(): Map<String, Any?> = mapOf(
        "id" to id,
        "full_name" to fullName,
        "email" to email,
        "mobile" to mobile,
        "gender" to gender,
        "dob" to dob,
        "occupation" to occupation,
        "marital_status" to maritalStatus,
        "nationality" to nationality,
        "address_line" to addressLine,
        "city" to city,
        "state" to state,
        "country" to country,
        "pin_code" to pinCode,
        "profile_complete" to profileComplete
    )

    companion object {
        fun fromRow(row: Map<String, Any?>): RemoteProfile = RemoteProfile(
            id = row.jsonString("id"),
            fullName = row.jsonString("full_name"),
            email = row.jsonString("email"),
            mobile = row.jsonString("mobile"),
            gender = row.jsonString("gender"),
            dob = row.jsonString("dob"),
            occupation = row.jsonString("occupation"),
            maritalStatus = row.jsonString("marital_status"),
            nationality = row.jsonString("nationality"),
            addressLine = row.jsonString("address_line"),
            city = row.jsonString("city"),
            state = row.jsonString("state"),
            country = row.jsonString("country"),
            pinCode = row.jsonString("pin_code"),
            profileComplete = row.jsonBool("profile_complete")
        )
    }
}

/** The server row for an on-device profile, under the given auth user id. */
fun RailProfile.toRemote(id: String): RemoteProfile = RemoteProfile(
    id = id,
    fullName = fullName,
    email = email,
    mobile = mobile,
    gender = gender,
    dob = dob,
    occupation = occupation,
    maritalStatus = maritalStatus,
    nationality = nationality,
    addressLine = addressLine,
    city = city,
    state = state,
    country = country,
    pinCode = pinCode,
    profileComplete = profileComplete
)

data class RemoteBooking(
    val pnr: String,
    val trainNumber: String = "",
    val trainName: String = "",
    val fromCode: String = "",
    val toCode: String = "",
    val fromName: String = "",
    val toName: String = "",
    val travelClassCode: String = "",
    val travelClassName: String = "",
    val fare: Int = 0,
    val journeyDate: String = "",
    val quota: String = "",
    val passengers: List<Passenger> = emptyList(),
    val contact: String = "",
    val status: String = "CONFIRMED",
    val amount: Int = 0,
    val paymentId: String = "",
    val orderId: String = ""
) {
    fun toRow(userId: String): Map<String, Any?> = mapOf(
        "user_id" to userId,
        "pnr" to pnr,
        "train_number" to trainNumber,
        "train_name" to trainName,
        "from_code" to fromCode,
        "to_code" to toCode,
        "from_name" to fromName,
        "to_name" to toName,
        "travel_class_code" to travelClassCode,
        "travel_class_name" to travelClassName,
        "fare" to fare,
        "journey_date" to journeyDate,
        "quota" to quota,
        "passengers" to passengers.map { p ->
            mapOf<String, Any?>(
                "name" to p.name,
                "age" to p.age,
                "gender" to p.gender,
                "berth" to p.berth,
                "concession" to p.concession
            )
        },
        "contact" to contact,
        "status" to status,
        "amount" to amount,
        "payment_id" to paymentId,
        "order_id" to orderId
    )

    /** Same field mapping BookingRepository applies to Room rows. */
    fun toDomain(): Booking {
        val train = Train(
            number = trainNumber,
            name = trainName,
            from = fromName,
            to = toName,
            fromCode = fromCode,
            toCode = toCode,
            depart = "",
            arrive = "",
            duration = "",
            days = ""
        )
        val cls = TrainClassAvail(
            code = travelClassCode,
            name = travelClassName,
            fare = fare,
            status = status,
            seats = 0
        )
        return Booking(
            pnr = pnr,
            train = train,
            travelClass = cls,
            date = journeyDate,
            quota = quota,
            passengers = passengers,
            contact = contact,
            status = status,
            amount = amount,
            fromName = fromName,
            toName = toName,
            paymentId = paymentId,
            orderId = orderId
        )
    }

    companion object {
        fun fromRow(row: Map<String, Any?>): RemoteBooking = RemoteBooking(
            pnr = row.jsonString("pnr"),
            trainNumber = row.jsonString("train_number"),
            trainName = row.jsonString("train_name"),
            fromCode = row.jsonString("from_code"),
            toCode = row.jsonString("to_code"),
            fromName = row.jsonString("from_name"),
            toName = row.jsonString("to_name"),
            travelClassCode = row.jsonString("travel_class_code"),
            travelClassName = row.jsonString("travel_class_name"),
            fare = row.jsonInt("fare"),
            journeyDate = row.jsonString("journey_date"),
            quota = row.jsonString("quota"),
            passengers = row.jsonMapList("passengers").map { p ->
                Passenger(
                    name = p.jsonString("name"),
                    age = p.jsonString("age"),
                    gender = p.jsonString("gender").ifBlank { "Male" },
                    berth = p.jsonString("berth").ifBlank { "No Preference" },
                    concession = p.jsonString("concession").ifBlank { "None" }
                )
            },
            contact = row.jsonString("contact"),
            status = row.jsonString("status").ifBlank { "CONFIRMED" },
            amount = row.jsonInt("amount"),
            paymentId = row.jsonString("payment_id"),
            orderId = row.jsonString("order_id")
        )

        fun fromDomain(booking: Booking): RemoteBooking = RemoteBooking(
            pnr = booking.pnr,
            trainNumber = booking.train.number,
            trainName = booking.train.name,
            fromCode = booking.train.fromCode,
            toCode = booking.train.toCode,
            fromName = booking.fromName,
            toName = booking.toName,
            travelClassCode = booking.travelClass.code,
            travelClassName = booking.travelClass.name,
            fare = booking.travelClass.fare,
            journeyDate = booking.date,
            quota = booking.quota,
            passengers = booking.passengers,
            contact = booking.contact,
            status = booking.status,
            amount = booking.amount,
            paymentId = booking.paymentId,
            orderId = booking.orderId
        )
    }
}

data class RemotePassenger(
    val id: String = "",
    val fullName: String = "",
    val age: Int? = null,
    val gender: String = "",
    val berthPreference: String = "No Preference",
    val concession: String = "None"
) {
    fun toRow(userId: String): Map<String, Any?> {
        val row = LinkedHashMap<String, Any?>()
        if (id.isNotBlank()) row["id"] = id
        row["user_id"] = userId
        row["full_name"] = fullName
        row["age"] = age
        row["gender"] = gender
        row["berth_preference"] = berthPreference
        row["concession"] = concession
        return row
    }

    companion object {
        fun fromRow(row: Map<String, Any?>): RemotePassenger = RemotePassenger(
            id = row.jsonString("id"),
            fullName = row.jsonString("full_name"),
            age = if (row["age"] == null) null else row.jsonInt("age"),
            gender = row.jsonString("gender"),
            berthPreference = row.jsonString("berth_preference").ifBlank { "No Preference" },
            concession = row.jsonString("concession").ifBlank { "None" }
        )

        fun fromDomain(passenger: Passenger): RemotePassenger = RemotePassenger(
            fullName = passenger.name,
            age = passenger.age.toIntOrNull(),
            gender = passenger.gender,
            berthPreference = passenger.berth,
            concession = passenger.concession
        )
    }
}

data class RemoteFavouriteRoute(
    val id: String = "",
    val fromCode: String = "",
    val toCode: String = ""
) {
    fun toRow(userId: String): Map<String, Any?> {
        val row = LinkedHashMap<String, Any?>()
        if (id.isNotBlank()) row["id"] = id
        row["user_id"] = userId
        row["from_code"] = fromCode
        row["to_code"] = toCode
        return row
    }

    companion object {
        fun fromRow(row: Map<String, Any?>): RemoteFavouriteRoute = RemoteFavouriteRoute(
            id = row.jsonString("id"),
            fromCode = row.jsonString("from_code"),
            toCode = row.jsonString("to_code")
        )
    }
}
