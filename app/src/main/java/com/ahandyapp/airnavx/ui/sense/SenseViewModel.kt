///////////////////////////////////////////////////////////////////////////////
package com.ahandyapp.airnavx.ui.sense

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

class SenseViewModel : ViewModel() {

    private val _textTitle = MutableLiveData<String>().apply {
        value = "Отслеживание параметров"
    }
    val textTitle: LiveData<String> = _textTitle

    private var _editCameraAngle = MutableLiveData<Int>().apply {
        value = 45
    }
    var editCameraAngle: MutableLiveData<Int> = _editCameraAngle

    private var _editDecibelLevel = MutableLiveData<Double>().apply {
        value = 0.0
    }
    var editDecibelLevel: MutableLiveData<Double> = _editDecibelLevel
}
///////////////////////////////////////////////////////////////////////////////
