package com.m0e_n00b.viriviri

import android.app.Application

class ViriViriApplication : Application() {
  override fun onCreate() {
    super.onCreate()
    // The debug geometry Activity owns an isolated player, without booting product state.
    if (getProcessName().endsWith(":geometry_experiment")) return
    appState = ViriViriAppState(applicationContext)
    Thread(
        {
          DefaultSearchInputMethods.warmUp()
          DefaultSearchInputMethods.preloadDictionary(this)
        },
        "OfflinePinyinWarmUp",
      )
      .start()
  }

  companion object {
    lateinit var appState: ViriViriAppState
      private set
  }
}
