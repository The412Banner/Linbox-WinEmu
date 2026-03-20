package com.example.terminaltest.ui.home

import android.content.Context
import com.termux.shared.settings.preferences.AppSharedPreferences
import com.termux.shared.settings.preferences.SharedPreferenceUtils


class EmuAppSharedPreferences(ctx:Context): AppSharedPreferences(ctx,
    ctx.getSharedPreferences("main_pref",Context.MODE_PRIVATE),
    ctx.getSharedPreferences("main_pref", Context.MODE_PRIVATE or Context.MODE_MULTI_PROCESS)) {


    fun getCurrentSession(): String? {
        return SharedPreferenceUtils.getString(mSharedPreferences, "current_session", null, true)
    }

    fun setCurrentSession(value: String?) {
        SharedPreferenceUtils.setString(mSharedPreferences, "current_session", value, false)
    }
}