package com.divebridge.settings

import com.divebridge.ssi.SsiDiveParams
import com.divebridge.ssi.SsiUserInfo

interface Settings {
    fun getUserInfo(): SsiUserInfo
    fun saveUserInfo(info: SsiUserInfo)
    fun getLastDiveParams(): SsiDiveParams
    fun saveLastDiveParams(params: SsiDiveParams)
}