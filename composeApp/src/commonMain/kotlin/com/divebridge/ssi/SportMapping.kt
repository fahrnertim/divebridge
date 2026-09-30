package com.divebridge.ssi

import com.divebridge.dive.DiveSport

fun DiveSport.toSsiDiveType(): Int = when (this) {
    DiveSport.SCUBA -> DiveType.SCUBA
    DiveSport.FREEDIVING -> DiveType.FREEDIVING
    DiveSport.EXTENDED_RANGE -> DiveType.EXTENDED_RANGE
    DiveSport.REBREATHER_SCR -> DiveType.REBREATHER_SCR
    DiveSport.REBREATHER_CCR -> DiveType.REBREATHER_CCR
    DiveSport.UNKNOWN -> DiveType.SCUBA
}