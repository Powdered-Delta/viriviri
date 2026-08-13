package com.m0e_n00b.spatialworkbench.core

enum class TransientMessageSeverity {
  INFO,
  SUCCESS,
  WARNING,
  ERROR,
}

data class TransientMessageAction(
    val id: String,
    val label: String,
)

data class TransientMessage(
    val id: String,
    val text: String,
    val severity: TransientMessageSeverity = TransientMessageSeverity.INFO,
    val durationMs: Long = severity.defaultDurationMs(),
    val action: TransientMessageAction? = null,
)

data class TransientMessageState(
    val current: TransientMessage? = null,
    val pending: List<TransientMessage> = emptyList(),
)

sealed interface TransientMessageEvent {
  data class Enqueue(val message: TransientMessage) : TransientMessageEvent

  data class Dismiss(val id: String) : TransientMessageEvent

  data class ActionTriggered(val id: String) : TransientMessageEvent

  data object Advance : TransientMessageEvent
}

object TransientMessageReducer {
  fun reduce(state: TransientMessageState, event: TransientMessageEvent): TransientMessageState =
      when (event) {
        is TransientMessageEvent.Enqueue ->
            if (event.message.isValid()) {
              state.enqueue(event.message)
            } else {
              state
            }
        is TransientMessageEvent.Dismiss ->
            if (state.current?.id == event.id) state.advance() else state
        is TransientMessageEvent.ActionTriggered ->
            if (state.current?.id == event.id && state.current.action != null) state.advance() else state
        TransientMessageEvent.Advance -> state.advance()
      }

  private fun TransientMessageState.enqueue(message: TransientMessage): TransientMessageState =
      if (current == null) copy(current = message) else copy(pending = pending + message)

  private fun TransientMessageState.advance(): TransientMessageState =
      if (pending.isEmpty()) {
        copy(current = null)
      } else {
        copy(current = pending.first(), pending = pending.drop(1))
      }
}

fun TransientMessageSeverity.defaultDurationMs(): Long =
    when (this) {
      TransientMessageSeverity.INFO,
      TransientMessageSeverity.SUCCESS -> 3_000L
      TransientMessageSeverity.WARNING -> 4_000L
      TransientMessageSeverity.ERROR -> 6_000L
    }

private fun TransientMessage.isValid(): Boolean =
    id.isNotBlank() &&
        text.isNotBlank() &&
        durationMs > 0L &&
        (action == null || (action.id.isNotBlank() && action.label.isNotBlank()))
