package com.nszconverter

import com.google.common.truth.Truth.assertThat
import com.nszconverter.util.humanBytes
import com.nszconverter.util.humanSeconds
import org.junit.Test

class FormattersTest {

    @Test fun `bytes under 1KB show as B`() {
        assertThat(512L.humanBytes()).isEqualTo("512 B")
    }

    @Test fun `MB formatting`() {
        assertThat((5L * 1024 * 1024).humanBytes()).contains("MB")
    }

    @Test fun `negative seconds is dash`() {
        assertThat((-1).humanSeconds()).isEqualTo("—")
    }

    @Test fun `seconds under a minute`() {
        assertThat(42.humanSeconds()).isEqualTo("42s")
    }

    @Test fun `minutes and seconds`() {
        assertThat(125.humanSeconds()).isEqualTo("2m 5s")
    }
}
