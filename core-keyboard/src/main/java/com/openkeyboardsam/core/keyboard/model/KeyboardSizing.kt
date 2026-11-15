package com.openkeyboardsam.core.keyboard.model

data class KeyPopupSpec(
    val label: String,
    val centerX: Float,
    val topY: Float
)

enum class LayoutSizingMode(val scaleWidth: Float, val alignRight: Boolean) {
    FULL(1.0f, false),
    ONE_HANDED_LEFT(0.82f, false),
    ONE_HANDED_RIGHT(0.82f, true),
    DEX_COMPACT(0.70f, true)
}
