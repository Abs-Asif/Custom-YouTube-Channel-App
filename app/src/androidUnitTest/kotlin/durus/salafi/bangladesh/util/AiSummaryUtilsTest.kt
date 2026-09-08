package durus.salafi.bangladesh.util

import kotlin.test.Test
import kotlin.test.assertEquals

class AiSummaryUtilsTest {

    @Test
    fun testCleanSubtitleText() {
        val rawVtt = """
            WEBVTT
            Kind: captions
            Language: bn

            1
            00:00:01.000 --> 00:00:03.000
            আসসালামু আলাইকুম

            2
            00:00:03.050 --> 00:00:05.000
            আসসালামু আলাইকুম

            3
            00:00:05.100 --> 00:00:08.000
            ওয়া রহমতুল্লাহ &amp; বারাকাতুহু
        """.trimIndent()

        val cleaned = AiSummaryUtils.cleanSubtitleText(rawVtt)
        assertEquals("আসসালামু আলাইকুম ওয়া রহমতুল্লাহ & বারাকাতুহু", cleaned)
    }

    @Test
    fun testAiSummarySteps() {
        assertEquals(1, AiSummaryStep.FETCHING_TRANSCRIPT.stepNumber)
        assertEquals(2, AiSummaryStep.CLEANING_TRANSCRIPT.stepNumber)
        assertEquals(3, AiSummaryStep.SENDING_REQUEST.stepNumber)
        assertEquals(4, AiSummaryStep.PROCESSING_RESPONSE.stepNumber)
    }
}
