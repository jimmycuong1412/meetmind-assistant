package com.meetmind.assistant.domain.usecase.sync

data class ThermalGateState(
    val isActive: Boolean = false,
    val coolReadings: Int = 0
)
