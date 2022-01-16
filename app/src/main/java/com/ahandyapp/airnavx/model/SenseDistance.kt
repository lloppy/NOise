package com.ahandyapp.airnavx.model

class SenseDistance {

    val distanceInFeetWhenObjectFillsCameraFieldOfView = 40.0
    var objectHorizontalSizeInPixels = 83.0
    var imageHorizontalSizeInPixels = 3024.0

    fun calculateSampleDistance() {
        var distance = calculateDistance(
            distanceInFeetWhenObjectFillsCameraFieldOfView,
            objectHorizontalSizeInPixels,
            imageHorizontalSizeInPixels)
    }
    fun calculateDistance(
        distanceInFeetWhenObjectFillsCameraFieldOfView:Double,
        objectHorizontalSizeInPixels:Double,
        imageHorizontalSizeInPixels:Double) : Double {

        var objectFieldOfViewInImage = objectHorizontalSizeInPixels / imageHorizontalSizeInPixels

        var distance = distanceInFeetWhenObjectFillsCameraFieldOfView/objectFieldOfViewInImage

        return distance
    }
}