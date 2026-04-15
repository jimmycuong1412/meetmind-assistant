// T008: Drives the cloud-inference badge UI per constitution v2.0 (mandatory badge rule)
package com.meetmind.assistant.data.model

enum class CloudBadgeState {
    /** No badge shown — on-device inference is active */
    HIDDEN,

    /** "☁ Cloud" badge visible — suggestion came from cloud provider */
    CLOUD_ACTIVE,

    /** "⚠ Local" badge visible — cloud timed out / failed, fell back to on-device */
    FALLBACK
}
