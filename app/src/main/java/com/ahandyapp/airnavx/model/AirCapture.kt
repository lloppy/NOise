package com.ahandyapp.airnavx.model

import android.telephony.CellLocation
import java.sql.Timestamp

data class AirCapture (
    val imagePath: String,
//    val timestamp: Timestamp,
//    val location: CellLocation,
    val decibel: Double,
    val cameraAngle: Int )
