package com.m0e_n00b.viriviri

import android.app.Application

class ViriViriApplication : Application() {
  override fun onCreate() {
    super.onCreate()
    appState = ViriViriAppState(applicationContext)
  }

  companion object {
    lateinit var appState: ViriViriAppState
      private set
  }
}
