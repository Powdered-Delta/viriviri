package com.m0e_n00b.inputmethod.engine

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.m0e_n00b.spatialworkbench.core.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Runs the actual packaged SQLite resource and JNI, not a JVM/native mock. */
@RunWith(AndroidJUnit4::class)
class OfflineEngineTest {
  @Test fun bundledPinyinWorksWithoutAnyDownload() {
    WolvicInputEngine(InstrumentationRegistry.getInstrumentation().targetContext).use { engine ->
      for ((raw, word) in mapOf("nihao" to "你好", "woshi" to "我是", "shurufa" to "输入法", "xi'an" to "西安")) {
        val result = engine.evaluate(InputLanguage.CHINESE, raw)
        assertTrue("Missing phrase for $raw", result.candidates.any { it.text == word })
      }
      assertTrue(engine.evaluate(InputLanguage.CHINESE, "qiu").candidates.isNotEmpty())
      assertTrue(engine.evaluate(InputLanguage.CHINESE, "n").candidates.isNotEmpty())
    }
  }
  @Test fun bundledJapaneseJniConvertsRomajiAndProducesCandidates() {
    WolvicInputEngine(InstrumentationRegistry.getInstrumentation().targetContext).use { engine ->
      for (raw in listOf("nihongo", "konnichiha", "kitte", "nihon")) {
        val result = engine.evaluate(InputLanguage.JAPANESE, raw)
        assertTrue("No native candidates for $raw", result.candidates.isNotEmpty())
        assertNotEquals(raw, result.display)
        assertTrue(result.candidates.all { it.consumedRange == CompositionRange(0, raw.length) })
      }
      assertEquals("きって", engine.evaluate(InputLanguage.JAPANESE, "kitte").display)
      assertEquals("にほん", engine.evaluate(InputLanguage.JAPANESE, "nihon").display)
    }
  }
}
