package com.m0e_n00b.spatialworkbench.core

/*
 * Minimal hand-rolled JSON model + reader (R3.1).
 *
 * `:spatial-workbench-core` is a pure Kotlin JVM module with no serialization dependency, and adding
 * one would change the build (PRD §7 risk row). The reader therefore stays deliberately small: it
 * only has to parse a locally bundled theme document. There is no file, asset, or network access
 * here — callers pass a `String` (R2.4).
 */

sealed interface ThemeJsonNode {
  data class Obj(val values: Map<String, ThemeJsonNode>) : ThemeJsonNode {
    fun get(key: String): ThemeJsonNode? = values[key]

    fun put(key: String, value: ThemeJsonNode): Obj = Obj(values + (key to value))

    fun without(key: String): Obj = Obj(values - key)
  }

  data class Arr(val values: List<ThemeJsonNode>) : ThemeJsonNode

  data class Str(val value: String) : ThemeJsonNode

  data class Num(val value: Double) : ThemeJsonNode

  data class Bool(val value: Boolean) : ThemeJsonNode

  data object Null : ThemeJsonNode
}

sealed interface ThemeJsonReadResult {
  data class Parsed(val node: ThemeJsonNode) : ThemeJsonReadResult

  data class Malformed(val offset: Int, val reason: String) : ThemeJsonReadResult
}

object ThemeJsonReader {
  fun read(text: String): ThemeJsonReadResult =
      try {
        val reader = Cursor(text)
        reader.skipWhitespace()
        val node = reader.readValue()
        reader.skipWhitespace()
        if (!reader.isAtEnd()) {
          ThemeJsonReadResult.Malformed(reader.offset, "trailing-content")
        } else {
          ThemeJsonReadResult.Parsed(node)
        }
      } catch (failure: ThemeJsonSyntaxException) {
        ThemeJsonReadResult.Malformed(failure.offset, failure.reason)
      }

  private class ThemeJsonSyntaxException(val offset: Int, val reason: String) : Exception(reason)

  private class Cursor(private val text: String) {
    var offset: Int = 0
      private set

    fun isAtEnd(): Boolean = offset >= text.length

    fun skipWhitespace() {
      while (offset < text.length && text[offset].isWhitespace()) offset += 1
    }

    fun readValue(): ThemeJsonNode {
      skipWhitespace()
      if (isAtEnd()) fail("unexpected-end")
      return when (val char = text[offset]) {
        '{' -> readObject()
        '[' -> readArray()
        '"' -> ThemeJsonNode.Str(readString())
        't' -> readLiteral("true", ThemeJsonNode.Bool(true))
        'f' -> readLiteral("false", ThemeJsonNode.Bool(false))
        'n' -> readLiteral("null", ThemeJsonNode.Null)
        else -> if (char == '-' || char.isDigit()) readNumber() else fail("unexpected-token")
      }
    }

    private fun readObject(): ThemeJsonNode.Obj {
      expect('{')
      val values = linkedMapOf<String, ThemeJsonNode>()
      skipWhitespace()
      if (peek() == '}') {
        offset += 1
        return ThemeJsonNode.Obj(values)
      }
      while (true) {
        skipWhitespace()
        if (peek() != '"') fail("expected-key")
        val key = readString()
        skipWhitespace()
        expect(':')
        // Duplicate keys keep the last declaration; JSON has no defined semantics for them.
        values[key] = readValue()
        skipWhitespace()
        when (peek()) {
          ',' -> offset += 1
          '}' -> {
            offset += 1
            return ThemeJsonNode.Obj(values)
          }
          else -> fail("expected-object-separator")
        }
      }
    }

    private fun readArray(): ThemeJsonNode.Arr {
      expect('[')
      val items = mutableListOf<ThemeJsonNode>()
      skipWhitespace()
      if (peek() == ']') {
        offset += 1
        return ThemeJsonNode.Arr(items)
      }
      while (true) {
        items += readValue()
        skipWhitespace()
        when (peek()) {
          ',' -> offset += 1
          ']' -> {
            offset += 1
            return ThemeJsonNode.Arr(items)
          }
          else -> fail("expected-array-separator")
        }
      }
    }

    private fun readString(): String {
      expect('"')
      val builder = StringBuilder()
      while (true) {
        if (isAtEnd()) fail("unterminated-string")
        when (val char = text[offset]) {
          '"' -> {
            offset += 1
            return builder.toString()
          }
          '\\' -> {
            offset += 1
            if (isAtEnd()) fail("unterminated-escape")
            when (val escape = text[offset]) {
              '"' -> builder.append('"')
              '\\' -> builder.append('\\')
              '/' -> builder.append('/')
              'b' -> builder.append('\b')
              'f' -> builder.append('\u000C')
              'n' -> builder.append('\n')
              'r' -> builder.append('\r')
              't' -> builder.append('\t')
              'u' -> {
                if (offset + 4 >= text.length) fail("unterminated-unicode-escape")
                val hex = text.substring(offset + 1, offset + 5)
                val code = hex.toIntOrNull(16) ?: fail("invalid-unicode-escape")
                builder.append(code.toChar())
                offset += 4
              }
              else -> fail("invalid-escape")
            }
            offset += 1
          }
          else -> {
            if (char.code < 0x20) fail("unescaped-control-character")
            builder.append(char)
            offset += 1
          }
        }
      }
    }

    private fun readLiteral(literal: String, node: ThemeJsonNode): ThemeJsonNode {
      if (!text.startsWith(literal, offset)) fail("invalid-literal")
      offset += literal.length
      return node
    }

    private fun readNumber(): ThemeJsonNode.Num {
      val start = offset
      if (peek() == '-') offset += 1
      if (isAtEnd() || !text[offset].isDigit()) fail("invalid-number")
      while (!isAtEnd() && text[offset].isDigit()) offset += 1
      if (!isAtEnd() && text[offset] == '.') {
        offset += 1
        if (isAtEnd() || !text[offset].isDigit()) fail("invalid-number")
        while (!isAtEnd() && text[offset].isDigit()) offset += 1
      }
      if (!isAtEnd() && (text[offset] == 'e' || text[offset] == 'E')) {
        offset += 1
        if (!isAtEnd() && (text[offset] == '+' || text[offset] == '-')) offset += 1
        if (isAtEnd() || !text[offset].isDigit()) fail("invalid-number")
        while (!isAtEnd() && text[offset].isDigit()) offset += 1
      }
      val literal = text.substring(start, offset)
      val value = literal.toDoubleOrNull() ?: fail("invalid-number")
      return ThemeJsonNode.Num(value)
    }

    private fun peek(): Char = if (isAtEnd()) '\u0000' else text[offset]

    private fun expect(char: Char) {
      if (isAtEnd() || text[offset] != char) fail("expected-$char")
      offset += 1
    }

    private fun fail(reason: String): Nothing = throw ThemeJsonSyntaxException(offset, reason)
  }
}
