package com.potflix.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncLedgerTest {
    private fun urls(ledger: SyncLedger, kind: SyncKind) = ledger.current(kind).map { it.url }

    @Test
    fun localChangesAreStampedAndUnchangedItemsAreNot() {
        val ledger = SyncLedger()
        assertTrue(ledger.recordLocal(SyncKind.Watchlist, listOf("a" to "A1", "b" to "B1"), now = 100))
        assertFalse(ledger.recordLocal(SyncKind.Watchlist, listOf("a" to "A1", "b" to "B1"), now = 200))
        assertTrue(ledger.recordLocal(SyncKind.Watchlist, listOf("a" to "A2", "b" to "B1"), now = 300))
        assertEquals(300, ledger.all().first { it.url == "a" }.updatedAt)
        assertEquals(100, ledger.all().first { it.url == "b" }.updatedAt)
    }

    @Test
    fun twoDevicesConvergeWithTheNewestChangeWinning() {
        val phone = SyncLedger().apply { recordLocal(SyncKind.History, listOf("ep1" to "phone@10min"), now = 100) }
        val tablet = SyncLedger().apply {
            merge(phone.all())
            recordLocal(SyncKind.History, listOf("ep1" to "tablet@25min"), now = 200)
        }
        phone.merge(tablet.all())
        tablet.merge(phone.all())
        assertEquals("tablet@25min", phone.current(SyncKind.History).single().payload)
        assertEquals(phone.all().toSet(), tablet.all().toSet())
    }

    @Test
    fun removalsSyncAndDoNotComeBack() {
        val phone = SyncLedger().apply { recordLocal(SyncKind.Watchlist, listOf("film" to "F"), now = 100) }
        val tablet = SyncLedger().apply { merge(phone.all()) }

        phone.recordLocal(SyncKind.Watchlist, emptyList(), now = 200) // removed on the phone
        tablet.merge(phone.all())
        assertEquals(emptyList<String>(), urls(tablet, SyncKind.Watchlist))

        // The tablet still reporting its stale copy must not bring it back.
        phone.merge(listOf(SyncEntry(SyncKind.Watchlist, "film", "F", updatedAt = 100, deleted = false)))
        assertEquals(emptyList<String>(), urls(phone, SyncKind.Watchlist))
    }

    @Test
    fun addingAgainAfterRemovalWins() {
        val ledger = SyncLedger()
        ledger.recordLocal(SyncKind.Watchlist, listOf("film" to "F"), now = 100)
        ledger.recordLocal(SyncKind.Watchlist, emptyList(), now = 200)
        ledger.recordLocal(SyncKind.Watchlist, listOf("film" to "F"), now = 300)
        assertEquals(listOf("film"), urls(ledger, SyncKind.Watchlist))
    }

    @Test
    fun historyAndWatchlistAreSeparate() {
        val ledger = SyncLedger()
        ledger.recordLocal(SyncKind.History, listOf("x" to "H"), now = 100)
        ledger.recordLocal(SyncKind.Watchlist, emptyList(), now = 200)
        assertEquals(listOf("x"), urls(ledger, SyncKind.History))
    }

    @Test
    fun aClockBehindStillRecordsTheChange() {
        val ledger = SyncLedger(listOf(SyncEntry(SyncKind.History, "x", "old", updatedAt = 500, deleted = false)))
        ledger.recordLocal(SyncKind.History, listOf("x" to "new"), now = 400)
        assertTrue(ledger.all().single().updatedAt > 500)
    }

    @Test
    fun oldRemovalMarkersArePruned() {
        val ledger = SyncLedger()
        ledger.recordLocal(SyncKind.Watchlist, listOf("a" to "A"), now = 100)
        ledger.recordLocal(SyncKind.Watchlist, emptyList(), now = 200)
        ledger.pruneRemovalsBefore(300)
        assertTrue(ledger.all().isEmpty())
    }
}

class SyncCryptoTest {
    @Test
    fun codesAreValidAndNormalizeFromTypedForms() {
        val code = SyncCrypto.newCode()
        assertEquals(code, SyncCrypto.normalize(code))
        assertEquals(code, SyncCrypto.normalize(SyncCrypto.format(code).lowercase()))
        assertNull(SyncCrypto.normalize("too-short"))
        assertNull(SyncCrypto.normalize("O0I1LABCDE")) // look-alike characters are never in a code
    }

    @Test
    fun onlyTheSameCodeCanOpenAMessage() {
        val key = SyncCrypto.keyFor("ABCDE23456")
        val sealed = SyncCrypto.seal(key, "hello".toByteArray())
        assertEquals("hello", SyncCrypto.open(key, sealed)?.toString(Charsets.UTF_8))
        assertNull(SyncCrypto.open(SyncCrypto.keyFor("ABCDE23457"), sealed))
        sealed[sealed.size - 1] = (sealed[sealed.size - 1].toInt() xor 1).toByte()
        assertNull(SyncCrypto.open(key, sealed))
        assertNotNull(SyncCrypto.newCode())
    }
}
