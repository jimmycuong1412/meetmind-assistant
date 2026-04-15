// T006: Inference mode enum — tracks whether a suggestion was produced on-device or via cloud
package com.meetmind.assistant.data.model

enum class InferenceMode {
    /** llama.cpp / Phi-3-mini running locally on device */
    ON_DEVICE,

    /** Gemini or Claude via cloud API (opt-in, badge shown per constitution v2.0) */
    CLOUD
}
