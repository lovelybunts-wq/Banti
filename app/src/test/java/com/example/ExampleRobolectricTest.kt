package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.LocalOfflineStore
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @Test
    fun `read string from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("Learn Talent", appName)
    }

    @Test
    fun `offline store seeds 7 subjects and persists revision diary with source`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val store = LocalOfflineStore(context)

        assertEquals(7, store.subjects.value.size)

        store.addRevisionDiaryEntry(
            dateKey = "2026-11-16",
            title = "Constitutional Amendments",
            subjectName = "Polity",
            sourceName = "Laxmikant Book Ch 10",
            content = "42nd Amendment added Socialist, Secular, Integrity."
        )

        val entries = store.revisionDiary.value
        assertTrue(entries.any { it.title == "Constitutional Amendments" && it.sourceName == "Laxmikant Book Ch 10" })
    }
}
