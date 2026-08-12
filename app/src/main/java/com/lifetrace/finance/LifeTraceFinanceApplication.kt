package com.lifetrace.finance

import android.app.Application
import android.app.Activity
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import android.os.Bundle

class LifeTraceFinanceApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        com.lifetrace.finance.domain.DataPortabilityManager.applyPendingRestore(this)
        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityCreated(activity: Activity, state: Bundle?) = com.lifetrace.finance.domain.AppearanceSettings(this@LifeTraceFinanceApplication).applyPrivacy(activity)
            override fun onActivityStarted(activity: Activity) = Unit
            override fun onActivityResumed(activity: Activity) = com.lifetrace.finance.domain.AppearanceSettings(this@LifeTraceFinanceApplication).applyPrivacy(activity)
            override fun onActivityPaused(activity: Activity) = Unit
            override fun onActivityStopped(activity: Activity) = Unit
            override fun onActivitySaveInstanceState(activity: Activity, state: Bundle) = Unit
            override fun onActivityDestroyed(activity: Activity) = Unit
        })
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            getSystemService(NotificationManager::class.java).apply {
                createNotificationChannel(NotificationChannel("budget_alerts", "预算提醒", NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = "预算接近上限或已经超支时提醒"
                })
                createNotificationChannel(NotificationChannel("daily_reminders", "每日记账提醒", NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = "在设置的时间提醒补记当天收支"
                })
            }
        }
    }
}
