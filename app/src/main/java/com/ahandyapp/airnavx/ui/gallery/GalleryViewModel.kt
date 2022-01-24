package com.ahandyapp.airnavx.ui.gallery

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

class GalleryViewModel : ViewModel() {




    private var stringMutableLiveData: MutableLiveData<String>? = null


    private val _text = MutableLiveData<String>().apply {
        value = "database"
    }
    val text: LiveData<String> = _text

}