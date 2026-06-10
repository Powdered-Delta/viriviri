package com.viriviri.app.meta

import android.app.Activity
import android.app.PendingIntent
import android.content.Intent

object HybridTransitionController {
    private const val OCULUS_2D_CATEGORY = "com.oculus.intent.category.2D"
    private const val HOME_PANEL_PENDING_INTENT_EXTRA = "extra_launch_in_home_pending_intent"

    fun launchImmersiveFromPanel(panelActivity: Activity) {
        val immersiveIntent = Intent(panelActivity, ImmersiveActivity::class.java).apply {
            action = Intent.ACTION_MAIN
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        panelActivity.startActivity(immersiveIntent)
        panelActivity.finishAndRemoveTask()
    }

    fun returnToPanelInHome(immersiveActivity: Activity) {
        val panelIntent = Intent(immersiveActivity, PanelActivity::class.java).apply {
            action = Intent.ACTION_MAIN
            addCategory(OCULUS_2D_CATEGORY)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }

        val panelPendingIntent = PendingIntent.getActivity(
            immersiveActivity,
            0,
            panelIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val homeIntent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            putExtra(HOME_PANEL_PENDING_INTENT_EXTRA, panelPendingIntent)
        }

        immersiveActivity.startActivity(homeIntent)
        immersiveActivity.finishAndRemoveTask()
    }
}
