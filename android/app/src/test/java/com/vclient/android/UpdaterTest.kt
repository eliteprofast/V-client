package com.vclient.android

import com.vclient.android.data.ApiClient
import com.vclient.android.update.Updater
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdaterTest {
    @Test
    fun versionsCompareNumerically() {
        assertTrue(Updater.isNewer("1.10.0", "1.9.9"))
        assertTrue(Updater.isNewer("1.1", "1.0.9"))
        assertFalse(Updater.isNewer("1.0.0", "1.0"))
        assertFalse(Updater.isNewer("0.9", "1.0.0"))
        assertEquals(0, Updater.compare("2.0", "2.0.0"))
    }

    @Test
    fun backendAddressValidationOnlyAcceptsHttpUrls() {
        assertTrue(ApiClient.isValidUrl("https://vclient.example.com/"))
        assertTrue(ApiClient.isValidUrl("http://10.0.2.2:8080"))
        assertFalse(ApiClient.isValidUrl("file:///sdcard/x"))
        assertFalse(ApiClient.isValidUrl("javascript:alert(1)"))
        assertFalse(ApiClient.isValidUrl("not a url"))
        assertFalse(ApiClient.isValidUrl(""))
        assertEquals("https://x.test", ApiClient.normalize(" https://x.test// "))
    }
}
