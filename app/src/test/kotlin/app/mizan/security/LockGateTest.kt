// Copyright (c) 2026 Mizan contributors
package app.mizan.security

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LockGateTest {
    @Test fun cold_start_and_process_death_require_authentication() {
        val gate = LockGate { 0L }
        assertTrue(gate.state.value.blocksShell)
        gate.configure(true, 30)
        assertTrue(gate.state.value.blocksShell)
        gate.authenticated()
        assertFalse(gate.state.value.blocksShell)
        gate.configure(true, 60)
        assertFalse(gate.state.value.blocksShell)
        val recreated = LockGate { 0L }
        recreated.configure(true, 60)
        assertTrue(recreated.state.value.blocksShell)
    }

    @Test fun timeout_boundary_locks_at_exactly_thirty_seconds() {
        var now = 0L
        val gate = LockGate { now }
        gate.configure(true, 30)
        gate.authenticated()
        gate.onBackground()
        now = 29_999
        gate.onForeground()
        assertFalse(gate.state.value.blocksShell)
        gate.onBackground()
        now += 30_000
        gate.onForeground()
        assertTrue(gate.state.value.blocksShell)
    }

    @Test fun disabled_lock_and_immediate_lock_follow_settings() {
        val gate = LockGate { 0L }
        gate.configure(false, 0)
        gate.onBackground()
        gate.onForeground()
        assertFalse(gate.state.value.blocksShell)
        gate.configure(true, 0)
        assertTrue(gate.state.value.blocksShell)
        gate.authenticated()
        gate.onBackground()
        gate.onForeground()
        assertTrue(gate.state.value.blocksShell)
        gate.configure(false, 0)
        assertFalse(gate.state.value.blocksShell)
    }

    @Test fun device_pin_success_during_background_does_not_relock_on_resume() {
        var now = 0L
        val gate = LockGate { now }
        gate.configure(true, 0)
        gate.onBackground()
        now = 60_000
        gate.authenticated()
        gate.onForeground()
        assertFalse(gate.state.value.blocksShell)
    }
}
