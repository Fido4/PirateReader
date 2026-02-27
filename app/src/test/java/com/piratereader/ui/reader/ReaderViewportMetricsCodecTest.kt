package com.piratereader.ui.reader

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ReaderViewportMetricsCodecTest {
    @Test
    fun parseEvaluateJavascriptResult_decodesV4MetricsAnchorTocAnchorAndTextHint() {
        val raw =
            "\"v4|3|9|2160|42|8640|12000|chapter-1%3Aintro|chapter-1|Visible%20paragraph%20text\""

        val metrics = ReaderViewportMetricsCodec.parseEvaluateJavascriptResult(raw)

        assertThat(metrics).isNotNull()
        assertThat(metrics?.pageIndex).isEqualTo(3)
        assertThat(metrics?.pageCount).isEqualTo(9)
        assertThat(metrics?.scrollX).isEqualTo(2160)
        assertThat(metrics?.scrollY).isEqualTo(42)
        assertThat(metrics?.maxScrollX).isEqualTo(8640)
        assertThat(metrics?.maxScrollY).isEqualTo(12000)
        assertThat(metrics?.anchorFragment).isEqualTo("chapter-1:intro")
        assertThat(metrics?.tocAnchorFragment).isEqualTo("chapter-1")
        assertThat(metrics?.visibleTextHint).isEqualTo("Visible paragraph text")
    }

    @Test
    fun parseEvaluateJavascriptResult_supportsLegacyV1PayloadWithoutMaxScrolls() {
        val raw = "\"v1|2|5|1080|24|frag-a\""

        val metrics = ReaderViewportMetricsCodec.parseEvaluateJavascriptResult(raw)

        assertThat(metrics).isNotNull()
        assertThat(metrics?.pageIndex).isEqualTo(2)
        assertThat(metrics?.pageCount).isEqualTo(5)
        assertThat(metrics?.maxScrollX).isNull()
        assertThat(metrics?.maxScrollY).isNull()
        assertThat(metrics?.anchorFragment).isEqualTo("frag-a")
        assertThat(metrics?.tocAnchorFragment).isNull()
        assertThat(metrics?.visibleTextHint).isNull()
    }

    @Test
    fun parseEvaluateJavascriptResult_supportsV2PayloadWithoutTextHint() {
        val raw = "\"v2|2|5|1080|24|4320|9000|frag-a\""

        val metrics = ReaderViewportMetricsCodec.parseEvaluateJavascriptResult(raw)

        assertThat(metrics).isNotNull()
        assertThat(metrics?.maxScrollX).isEqualTo(4320)
        assertThat(metrics?.maxScrollY).isEqualTo(9000)
        assertThat(metrics?.tocAnchorFragment).isNull()
        assertThat(metrics?.visibleTextHint).isNull()
    }

    @Test
    fun parseEvaluateJavascriptResult_supportsV3PayloadWithoutTocAnchor() {
        val raw = "\"v3|2|5|1080|24|4320|9000|frag-a|Visible%20text\""

        val metrics = ReaderViewportMetricsCodec.parseEvaluateJavascriptResult(raw)

        assertThat(metrics).isNotNull()
        assertThat(metrics?.anchorFragment).isEqualTo("frag-a")
        assertThat(metrics?.tocAnchorFragment).isNull()
        assertThat(metrics?.visibleTextHint).isEqualTo("Visible text")
    }

    @Test
    fun parseEvaluateJavascriptResult_returnsNullForNullOrInvalidPayload() {
        assertThat(ReaderViewportMetricsCodec.parseEvaluateJavascriptResult(null)).isNull()
        assertThat(ReaderViewportMetricsCodec.parseEvaluateJavascriptResult("null")).isNull()
        assertThat(ReaderViewportMetricsCodec.parseEvaluateJavascriptResult("\"bad\"")).isNull()
    }

    @Test
    fun parseEvaluateJavascriptResult_handlesEscapedQuotesAndBlankAnchor() {
        val raw = "\"v4|1|1|0|0|0|0|||\""

        val metrics = ReaderViewportMetricsCodec.parseEvaluateJavascriptResult(raw)

        assertThat(metrics).isNotNull()
        assertThat(metrics?.anchorFragment).isNull()
        assertThat(metrics?.pageIndex).isEqualTo(1)
        assertThat(metrics?.pageCount).isEqualTo(1)
        assertThat(metrics?.maxScrollX).isEqualTo(0)
        assertThat(metrics?.maxScrollY).isEqualTo(0)
        assertThat(metrics?.tocAnchorFragment).isNull()
        assertThat(metrics?.visibleTextHint).isNull()
    }
}
