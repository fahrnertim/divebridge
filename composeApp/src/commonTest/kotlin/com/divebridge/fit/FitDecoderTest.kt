package com.divebridge.fit

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FitDecoderTest {

    @Test
    fun isValidFitFile_rejectsTooSmall() {
        assertFalse(FitDecoder.isValidFitFile(ByteArray(5)))
    }

    @Test
    fun isValidFitFile_rejectsWrongSignature() {
        val data = ByteArray(14)
        data[0] = 14 // header size
        // No .FIT signature
        assertFalse(FitDecoder.isValidFitFile(data))
    }

    @Test
    fun isValidFitFile_acceptsValidHeader() {
        val data = ByteArray(14)
        data[0] = 14 // header size
        data[1] = 0x10 // protocol version
        data[2] = 0x00 // profile version low
        data[3] = 0x08 // profile version high
        // data size = 0
        data[4] = 0; data[5] = 0; data[6] = 0; data[7] = 0
        // .FIT signature
        data[8] = '.'.code.toByte()
        data[9] = 'F'.code.toByte()
        data[10] = 'I'.code.toByte()
        data[11] = 'T'.code.toByte()
        assertTrue(FitDecoder.isValidFitFile(data))
    }

    // TODO: Add tests with real .fit files from /testdata
}