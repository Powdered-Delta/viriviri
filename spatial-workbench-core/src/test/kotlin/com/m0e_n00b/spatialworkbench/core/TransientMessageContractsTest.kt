package com.m0e_n00b.spatialworkbench.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class TransientMessageContractsTest {
  @Test
  fun enqueueUsesFifoAndAdvancePromotesTheNextMessage() {
    val first = TransientMessage("first", "First")
    val second = TransientMessage("second", "Second")
    val third = TransientMessage("third", "Third")
    var state = TransientMessageState()

    state = TransientMessageReducer.reduce(state, TransientMessageEvent.Enqueue(first))
    state = TransientMessageReducer.reduce(state, TransientMessageEvent.Enqueue(second))
    state = TransientMessageReducer.reduce(state, TransientMessageEvent.Enqueue(third))

    assertSame(first, state.current)
    assertEquals(listOf(second, third), state.pending)

    state = TransientMessageReducer.reduce(state, TransientMessageEvent.Advance)
    assertSame(second, state.current)
    assertEquals(listOf(third), state.pending)

    state = TransientMessageReducer.reduce(state, TransientMessageEvent.Dismiss("second"))
    assertSame(third, state.current)
    assertEquals(emptyList<TransientMessage>(), state.pending)

    state = TransientMessageReducer.reduce(state, TransientMessageEvent.Advance)
    assertNull(state.current)
  }

  @Test
  fun invalidMessagesAndUnknownEventsDoNotChangeState() {
    val current = TransientMessage("current", "Current", action = TransientMessageAction("retry", "Retry"))
    val state = TransientMessageState(current = current)

    assertEquals(
        state,
        TransientMessageReducer.reduce(state, TransientMessageEvent.Enqueue(TransientMessage("", "Missing id"))),
    )
    assertEquals(state, TransientMessageReducer.reduce(state, TransientMessageEvent.Dismiss("other")))
    assertEquals(state, TransientMessageReducer.reduce(state, TransientMessageEvent.ActionTriggered("other")))
  }

  @Test
  fun actionAdvancesOnlyWhenTheCurrentMessageDeclaresAnAction() {
    val actionable = TransientMessage("retry", "Failed", action = TransientMessageAction("retry", "Retry"))
    val plain = TransientMessage("plain", "Later")
    val state = TransientMessageState(current = actionable, pending = listOf(plain))

    val result = TransientMessageReducer.reduce(state, TransientMessageEvent.ActionTriggered("retry"))

    assertSame(plain, result.current)
    assertEquals(3_000L, TransientMessageSeverity.INFO.defaultDurationMs())
    assertEquals(3_000L, TransientMessageSeverity.SUCCESS.defaultDurationMs())
    assertEquals(4_000L, TransientMessageSeverity.WARNING.defaultDurationMs())
    assertEquals(6_000L, TransientMessageSeverity.ERROR.defaultDurationMs())
  }
}
