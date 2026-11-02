package io.github.rainbowfl4sh.powerampdiscordrp.account

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import io.github.rainbowfl4sh.powerampdiscordrp.core.account.LinkedAccount
import io.github.rainbowfl4sh.powerampdiscordrp.core.account.PendingLogin
import io.github.rainbowfl4sh.powerampdiscordrp.settings.SettingsStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The linked account and a pending login survive the process in their own file - and nothing but the public profile. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class AccountStoreTest {
    private val app: Application = ApplicationProvider.getApplicationContext()
    private val prefs get() = app.getSharedPreferences(AccountStore.PREFS_NAME, Context.MODE_PRIVATE)

    private val flash = LinkedAccount("80351110224678912", "flash", "Fl4sh", "a_8342729096ea3675442027381ff50dfe", 1_800_000_000_000)
    private val pending = PendingLogin("st4te", "dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk", "1559554394301800528", 1_800_000_000_000)

    @Test
    fun theAccountRoundTrips() {
        val store = AccountStore(app)
        assertNull(store.loadAccount())
        store.saveAccount(flash)
        assertEquals(flash, AccountStore(app).loadAccount())
        // without display name and picture
        val plain = LinkedAccount("80351110224678912", "flash")
        store.saveAccount(plain)
        assertEquals(plain, AccountStore(app).loadAccount())
        store.saveAccount(null)
        assertNull(AccountStore(app).loadAccount())
    }

    @Test
    fun aPendingLoginRoundTripsAndIsDeleted() {
        val store = AccountStore(app)
        assertNull(store.loadPending())
        store.savePending(pending)
        assertEquals(pending, AccountStore(app).loadPending())
        store.savePending(null)
        assertNull(AccountStore(app).loadPending())
        assertTrue("nothing of the login is left", prefs.all.keys.none { it.startsWith("pending") })
    }

    @Test
    fun itsOwnFileWithTheProfileOnly() {
        val store = AccountStore(app)
        store.saveAccount(flash)
        store.savePending(pending)
        assertEquals(
            setOf("id", "username", "globalName", "avatar", "linkedAt", "pendingState", "pendingVerifier", "pendingClientId", "pendingStartedAt"),
            prefs.all.keys,
        )
        // not in the settings (export / import / reset)
        val settings = app.getSharedPreferences(SettingsStore.PREFS_NAME, Context.MODE_PRIVATE)
        assertTrue(settings.all.values.none { it.toString().contains("flash") })
        // no token is ever written: the profile and the login are all there is
        assertTrue(prefs.all.keys.none { it.contains("token", ignoreCase = true) })
    }

    @Test
    fun damagedEntriesAreNoAccount() {
        prefs.edit().putString("id", "x/../y").putString("username", "flash").commit()
        assertNull(AccountStore(app).loadAccount())
        prefs.edit().putString("id", "80351110224678912").putString("username", " ").commit()
        assertNull(AccountStore(app).loadAccount())
        prefs.edit().putString("pendingState", "s").putString("pendingClientId", "1").commit()   // no verifier
        assertNull(AccountStore(app).loadPending())
    }
}
