# Offline input engines

Wolvic source baseline: `37c7c28b1b269dca9356f449d798dcd1b6bcc8a8`
from https://github.com/Igalia/wolvic.

* PinyinCandidates.kt and WolvicInputEngine.kt adapt the candidate lookup and
  Romaji/OpenWnn calls in `ui/keyboards/ChinesePinyinKeyboard.java` and
  `ui/keyboards/JapaneseKeyboard.java`. Derived files retain MPL-2.0 notices.
  Full Wolvic license: licenses/Wolvic-MPL-2.0.txt.
* google_pinyin.db is the unmodified database shipped in that Wolvic revision,
  3,023,872 bytes. SHA-256:
  920f621c690355d33ed6b9732bb7fa3e297b36c379d6b352ef3643be43b83635.
  This is the distribution's database filename, not a Google service integration.
* openwnn-1.3.7.aar is the unmodified binary shipped in Wolvic libs/openwnn,
  3,592,696 bytes. SHA-256:
  89f7dc5bc96632fb718d5cfb228edb96182e604569b2ae45d43a14a46dbdaf37.
  Its local Maven POM only supplies coordinates for Gradle; it does not modify
  the binary. Includes arm64-v8a libwnndict.so and libwnnjpndic.so (and the other
  architectures in the original archive). Original AAR minSdk is 24.
* OpenWnn copyright OMRON SOFTWARE Co., Ltd. Upstream README and Apache notice:
  https://android.googlesource.com/platform/packages/inputmethods/OpenWnn/+/refs/heads/main/README.txt
  https://android.googlesource.com/platform/packages/inputmethods/OpenWnn/+/refs/heads/main/NOTICE
  These identify upstream notices; no claim is made that the vendored 1.3.7
  binary can be reproduced from AOSP main. Binary provenance is the pinned Wolvic revision.

No browser engine, speech recognizer, Vosk model, external dictionary download,
voice telemetry or user-dictionary learning is included. All conversion is offline.
The controller confines SQLite/JNI to a single worker. Instrumentation tests are
provided for the real assets and must be run separately on an authorized Android target.
