package com.meetmind.assistant.domain.usecase.sync

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ThermalGateTest {

    @Test
    fun `turns on at the first hot reading`() {
        val gate = ThermalGate(coolReadingsToRelease = 3)

        assertTrue(gate.onReading(overheating = true))
        assertTrue(gate.wasActive)
    }

    @Test
    fun `turns off only after enough consecutive cool readings`() {
        val gate = ThermalGate(coolReadingsToRelease = 3)
        gate.onReading(true)

        assertTrue(gate.onReading(false))
        assertTrue(gate.onReading(false))
        assertFalse(gate.onReading(false))
        assertTrue("wasActive survives the release", gate.wasActive)
    }

    @Test
    fun `a hot reading restarts the cool-down count`() {
        val gate = ThermalGate(coolReadingsToRelease = 3)
        gate.onReading(true)
        gate.onReading(false)
        gate.onReading(false)
        gate.onReading(true)

        gate.onReading(false)
        gate.onReading(false)
        assertTrue(gate.isActive)
        assertFalse(gate.onReading(false))
    }

    @Test
    fun `cool readings never activate it`() {
        val gate = ThermalGate()
        repeat(100) { gate.onReading(false) }

        assertFalse(gate.isActive)
        assertFalse(gate.wasActive)
    }

    @Test
    fun `reset clears both flags`() {
        val gate = ThermalGate()
        gate.onReading(true)
        gate.reset()

        assertFalse(gate.isActive)
        assertFalse(gate.wasActive)
    }
}
