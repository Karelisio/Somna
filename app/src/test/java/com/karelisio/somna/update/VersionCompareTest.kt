package com.karelisio.somna.update

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VersionCompareTest {
    @Test
    fun `newer versions are detected`() {
        assertTrue(UpdateRepository.isNewer("1.2.0", "1.1.9"))
        assertTrue(UpdateRepository.isNewer("v2.0.0", "1.9.9"))
        assertTrue(UpdateRepository.isNewer("1.10.0", "1.9.0"))
        assertTrue(UpdateRepository.isNewer("0.1.0", "0.1.0-dev"))
    }

    @Test
    fun `same or older versions are not updates`() {
        assertFalse(UpdateRepository.isNewer("1.2.0", "1.2.0"))
        assertFalse(UpdateRepository.isNewer("1.2.0", "1.3.0"))
        assertFalse(UpdateRepository.isNewer("1.2", "1.2.0"))
        assertFalse(UpdateRepository.isNewer("1.2.0-beta", "1.2.0"))
    }
}
