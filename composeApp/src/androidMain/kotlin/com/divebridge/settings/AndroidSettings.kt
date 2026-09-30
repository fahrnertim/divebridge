package com.divebridge.settings

import android.content.Context
import android.content.SharedPreferences
import com.divebridge.ssi.DiveSubType
import com.divebridge.ssi.DiveType
import com.divebridge.ssi.SsiDiveParams
import com.divebridge.ssi.SsiUserInfo
import com.divebridge.ssi.WaterType

class AndroidSettings(context: Context) : Settings {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("divebridge", Context.MODE_PRIVATE)

    override fun getUserInfo(): SsiUserInfo = SsiUserInfo(
        masterId = prefs.getString("user_master_id", "") ?: "",
        firstName = prefs.getString("user_first_name", "") ?: "",
        lastName = prefs.getString("user_last_name", "") ?: "",
    )

    override fun saveUserInfo(info: SsiUserInfo) {
        prefs.edit()
            .putString("user_master_id", info.masterId)
            .putString("user_first_name", info.firstName)
            .putString("user_last_name", info.lastName)
            .apply()
    }

    override fun getLastDiveParams(): SsiDiveParams = SsiDiveParams(
        diveType = prefs.getInt("dive_type", DiveType.SCUBA),
        siteId = prefs.getString("site_id", null),
        waterTypeId = prefs.getInt("water_type_id", WaterType.FRESH).takeIf {
            prefs.contains("water_type_id")
        },
        diveSubTypeId = prefs.getInt("dive_sub_type_id", DiveSubType.FUN_DIVE).takeIf {
            prefs.contains("dive_sub_type_id")
        },
    )

    override fun saveLastDiveParams(params: SsiDiveParams) {
        prefs.edit().apply {
            putInt("dive_type", params.diveType)
            if (params.siteId != null) putString("site_id", params.siteId) else remove("site_id")
            if (params.waterTypeId != null) putInt("water_type_id", params.waterTypeId) else remove("water_type_id")
            if (params.diveSubTypeId != null) putInt("dive_sub_type_id", params.diveSubTypeId) else remove("dive_sub_type_id")
            apply()
        }
    }

    override fun getRecentSiteIds(): List<String> {
        val csv = prefs.getString("recent_site_ids", null) ?: return emptyList()
        return csv.split(",").filter { it.isNotEmpty() }
    }

    override fun addRecentSiteId(siteId: String) {
        if (siteId.isEmpty()) return
        val existing = getRecentSiteIds().toMutableList()
        existing.remove(siteId)
        existing.add(0, siteId)
        val trimmed = existing.take(10)
        prefs.edit().putString("recent_site_ids", trimmed.joinToString(",")).apply()
    }
}