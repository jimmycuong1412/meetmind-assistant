package com.meetmind.assistant.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Tests for [LlmVariantDownloadStatus.resolve] — the download-completeness decision
 * for an LLM variant. A vision-capable variant is only fully downloaded when both
 * the base GGUF and the mmproj vision adapter are on disk; devices that fetched the
 * base model before the camera-vision feature landed must surface the missing adapter
 * instead of reporting "Downloaded".
 */
class LlmVariantDownloadStatusTest {

    @Test
    fun `base model missing means not downloaded`() {
        assertEquals(
            LlmVariantDownloadStatus.NOT_DOWNLOADED,
            LlmVariantDownloadStatus.resolve(
                baseModelDownloaded = false,
                visionAdapterRequired = false,
                visionAdapterDownloaded = false
            )
        )
    }

    @Test
    fun `base model missing means not downloaded even if adapter is present`() {
        assertEquals(
            LlmVariantDownloadStatus.NOT_DOWNLOADED,
            LlmVariantDownloadStatus.resolve(
                baseModelDownloaded = false,
                visionAdapterRequired = true,
                visionAdapterDownloaded = true
            )
        )
    }

    @Test
    fun `text-only variant with base model is downloaded`() {
        assertEquals(
            LlmVariantDownloadStatus.DOWNLOADED,
            LlmVariantDownloadStatus.resolve(
                baseModelDownloaded = true,
                visionAdapterRequired = false,
                visionAdapterDownloaded = false
            )
        )
    }

    @Test
    fun `vision variant with base model but no adapter reports adapter missing`() {
        assertEquals(
            LlmVariantDownloadStatus.VISION_ADAPTER_MISSING,
            LlmVariantDownloadStatus.resolve(
                baseModelDownloaded = true,
                visionAdapterRequired = true,
                visionAdapterDownloaded = false
            )
        )
    }

    @Test
    fun `vision variant with both files is downloaded`() {
        assertEquals(
            LlmVariantDownloadStatus.DOWNLOADED,
            LlmVariantDownloadStatus.resolve(
                baseModelDownloaded = true,
                visionAdapterRequired = true,
                visionAdapterDownloaded = true
            )
        )
    }

    @Test
    fun `isUsable is true whenever the base model exists`() {
        assertEquals(false, LlmVariantDownloadStatus.NOT_DOWNLOADED.isUsable)
        assertEquals(true, LlmVariantDownloadStatus.VISION_ADAPTER_MISSING.isUsable)
        assertEquals(true, LlmVariantDownloadStatus.DOWNLOADED.isUsable)
    }
}
