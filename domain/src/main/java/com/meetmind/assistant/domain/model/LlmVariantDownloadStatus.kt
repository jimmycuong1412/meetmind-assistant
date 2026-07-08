package com.meetmind.assistant.domain.model

/**
 * Download completeness of an LLM variant's on-disk files.
 *
 * A vision-capable variant ships two files (base GGUF + mmproj vision adapter); it is
 * only [DOWNLOADED] when both exist. [VISION_ADAPTER_MISSING] identifies devices that
 * fetched the base model before the camera-vision feature landed — the model is usable
 * for text inference, but the vision adapter still needs to be downloaded.
 */
enum class LlmVariantDownloadStatus {
    /** No base model file on disk. */
    NOT_DOWNLOADED,

    /** Base model present, but the required mmproj vision adapter is missing. */
    VISION_ADAPTER_MISSING,

    /** All files the variant needs are on disk. */
    DOWNLOADED;

    /** True when the base model exists — text inference works even without the adapter. */
    val isUsable: Boolean
        get() = this != NOT_DOWNLOADED

    companion object {
        /**
         * Resolves the status from file presence.
         *
         * @param baseModelDownloaded Whether the base GGUF file exists on disk.
         * @param visionAdapterRequired Whether the variant ships an mmproj vision adapter.
         * @param visionAdapterDownloaded Whether the mmproj file exists on disk.
         */
        fun resolve(
            baseModelDownloaded: Boolean,
            visionAdapterRequired: Boolean,
            visionAdapterDownloaded: Boolean
        ): LlmVariantDownloadStatus = when {
            !baseModelDownloaded -> NOT_DOWNLOADED
            visionAdapterRequired && !visionAdapterDownloaded -> VISION_ADAPTER_MISSING
            else -> DOWNLOADED
        }
    }
}
