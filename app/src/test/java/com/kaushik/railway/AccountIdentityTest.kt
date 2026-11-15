package com.kaushik.railway

import com.kaushik.railway.data.AccountIdentity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import java.util.Locale

class AccountIdentityTest {
    @Test fun signedOutSessionHasNoStorageOwner() {
        assertEquals("", AccountIdentity.ownerId(false, "alice", "alice@example.com"))
        assertEquals("", AccountIdentity.ownerId(true, " ", " "))
    }

    @Test fun stableUserIdWinsOverChangingEmail() {
        assertEquals("user:alice", AccountIdentity.ownerId(true, " Alice ", "old@example.com"))
        assertEquals("user:alice", AccountIdentity.ownerId(true, "alice", "new@example.com"))
    }

    @Test fun legacyEmailNamespaceCannotCollideWithUserId() {
        assertEquals("email:alice@example.com", AccountIdentity.ownerId(true, "", " Alice@Example.COM "))
        assertNotEquals(
            AccountIdentity.ownerId(true, "alice@example.com", ""),
            AccountIdentity.ownerId(true, "", "alice@example.com")
        )
    }

    @Test fun normalizationDoesNotDependOnDeviceLocale() {
        val original = Locale.getDefault()
        try {
            Locale.setDefault(Locale("tr", "TR"))
            assertEquals("user:irail", AccountIdentity.ownerId(true, "IRAIL", ""))
        } finally {
            Locale.setDefault(original)
        }
    }
}
