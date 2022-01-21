package com.ahandyapp.airnavx

import com.ahandyapp.airnavx.api.MapJson
import retrofit2.Call
import retrofit2.http.GET


interface ApiRequests {



    @GET("/facts/random/")
//    @GET("/points/?format=json")
    fun getMapPoint(): Call<MapJson>
}