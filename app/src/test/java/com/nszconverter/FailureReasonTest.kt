package com.nszconverter

import com.google.common.truth.Truth.assertThat
import com.nszconverter.domain.model.FailureReason
import org.junit.Test

class FailureReasonTest {

    @Test fun `input_not_found maps to FILE_NOT_FOUND`() {
        assertThat(FailureReason.fromRawError("input_not_found")).isEqualTo(FailureReason.FILE_NOT_FOUND)
    }

    @Test fun `input_empty maps to FILE_EMPTY`() {
        assertThat(FailureReason.fromRawError("input_empty")).isEqualTo(FailureReason.FILE_EMPTY)
    }

    @Test fun `keys_outdated maps to KEYS_OUTDATED`() {
        assertThat(FailureReason.fromRawError("keys_outdated_or_invalid")).isEqualTo(FailureReason.KEYS_OUTDATED)
    }

    @Test fun `missing keys maps to INVALID_KEYS`() {
        assertThat(FailureReason.fromRawError("missing:master_key_00,aes_kek_generation_source"))
            .isEqualTo(FailureReason.INVALID_KEYS)
    }

    @Test fun `nsz_import_failed maps to PYTHON_ERROR`() {
        assertThat(FailureReason.fromRawError("nsz_import_failed:No module named 'nsz'"))
            .isEqualTo(FailureReason.PYTHON_ERROR)
    }

    @Test fun `unknown text falls back to UNKNOWN`() {
        assertThat(FailureReason.fromRawError("something_weird")).isEqualTo(FailureReason.UNKNOWN)
    }
}
