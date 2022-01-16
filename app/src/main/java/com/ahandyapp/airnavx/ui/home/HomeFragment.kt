package com.ahandyapp.airnavx.ui.home

import android.app.Activity.RESULT_CANCELED
import android.app.Activity.RESULT_OK
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Camera
import android.media.ThumbnailUtils
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import com.ahandyapp.airnavx.databinding.FragmentHomeBinding
import com.ahandyapp.airnavx.model.AirCapture
import com.ahandyapp.airnavx.ui.sense.AngleMeter
import com.ahandyapp.airnavx.ui.sense.SoundMeter
import com.google.gson.Gson
import java.io.File
import java.io.IOException
import java.net.URI
import java.text.SimpleDateFormat
import java.util.*
import kotlin.properties.Delegates

class HomeFragment : Fragment() {

    private val TAG = "HomeFragment"

    private lateinit var homeViewModel: HomeViewModel
    private var _binding: FragmentHomeBinding? = null

    // property is only valid between onCreateView and onDestroyView.
    private val binding get() = _binding!!

    //////////////////
    // angle & location meters
    private var angleMeter = AngleMeter()
    private var soundMeter = SoundMeter()

    private var decibel by Delegates.notNull<Double>()
    private var cameraAngle by Delegates.notNull<Int>()

    // photo thumb
    private lateinit var imageView: ImageView       // thumb photo display
    private lateinit var photoFile: File            // photo file
    private lateinit var photoUri: Uri              // photo URI

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        homeViewModel =
            ViewModelProvider(this).get(HomeViewModel::class.java)

        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        val root: View = binding.root

        val textView: TextView = binding.textHome
        homeViewModel.text.observe(viewLifecycleOwner, Observer {
            textView.text = it
        })

        // get reference to button
        val buttonCameraIdString = "button_camera"
        val packageName = this.context?.getPackageName()
        val buttonCameraId = resources.getIdentifier(buttonCameraIdString, "id", packageName)
        val buttonCamera = root.findViewById(buttonCameraId) as Button
        // set on-click listener
        buttonCamera.setOnClickListener {
            Toast.makeText(this.context, "launching camera...", Toast.LENGTH_SHORT).show()
            Log.d(TAG, "buttonCamera.setOnClickListener launching camera...")
            dispatchTakePictureIntent()
        }
        val imageViewIdString = "imageView2"
        val imageViewId = resources.getIdentifier(imageViewIdString, "id", packageName)
        imageView = root.findViewById(imageViewId) as ImageView

        //////////////////
        // angle meter one-time init
        angleMeter.create(requireActivity())
        //////////////////

        return root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    //////////////////
    // onResume
    override fun onResume() {
        super.onResume()
//        // start angle meter
//        angleMeter.start()
//        Log.d(TAG, "onResume angleMeter started.")
//        // TODO: init soundMeter - works if SenseFrag run 1st!
//        // start sound meter
//        this.context?.let { soundMeter.start(it) }
//        Log.d(TAG, "onResume soundMeter started.")

    }

    override fun onPause() {
        super.onPause()

//        // stop angle meter
//        angleMeter.stop()
//        // stop sound meter
//        soundMeter.stop()
    }

    val REQUEST_IMAGE_CAPTURE = 1

    private fun dispatchTakePictureIntent() {
        val THUMBNAIL_ONLY = false
        try {
            // start angle meter
            angleMeter.start()
            Log.d(TAG, "onResume angleMeter started.")
            // TODO: init soundMeter - works if SenseFrag run 1st!
            // start sound meter
            this.context?.let { soundMeter.start(it) }
            Log.d(TAG, "onResume soundMeter started.")

            // exercise meters
            cameraAngle = angleMeter.getAngle()
            Log.d(TAG, "dispatchTakePictureIntent angleMeter.getAngle ->${cameraAngle.toString()}")
            decibel = soundMeter.deriveDecibel(forceFormat = true)
            Log.d(TAG, "dispatchTakePictureIntent soundMeter.deriveDecibel db->${decibel.toString()}")
        } catch (ex: Exception) {
            Log.e(TAG, "dispatchTakePictureIntent Meter Exception ${ex.stackTrace}")
        }
        if (!THUMBNAIL_ONLY) {
            val takePictureIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
            try {
                // create the photo File
//                val photoFile: File? = try {
                try {
                    photoFile = createImageFile()
                } catch (ex: IOException) {
                    // Error occurred while creating the File
                    Log.e(TAG, "dispatchTakePictureIntent IOException ${ex.stackTrace}")
                    null
                }
                // Continue only if the File was successfully created
                photoFile?.also {
//                    val photoURI: Uri = FileProvider.getUriForFile(
                    photoUri = FileProvider.getUriForFile(
                        requireContext(),
                        "com.ahandyapp.airnavx",
                        it
                    )
                    takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoUri)
                    startActivityForResult(takePictureIntent, REQUEST_IMAGE_CAPTURE)
                }
            } catch (e: ActivityNotFoundException) {
                // display error state
                Toast.makeText(this.context, "NO camera launch...", Toast.LENGTH_SHORT).show()
                Log.d(TAG, "dispatchTakePictureIntent -> NO camera launch...")
            }
        }
        else {  // THUMBNAIL_ONLY
            val takePictureIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
            try {
                startActivityForResult(takePictureIntent, REQUEST_IMAGE_CAPTURE)
            } catch (e: ActivityNotFoundException) {
                // display error state
                Toast.makeText(this.context, "NO camera launch...", Toast.LENGTH_SHORT).show()
                Log.d(TAG, "dispatchTakePictureIntent -> NO camera launch...")
            }
        }
    }
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        Log.d(TAG, "dispatchTakePictureIntent onActivityResult requestCode ${requestCode}, resultCode ${resultCode}")
        var imageBitmap: Bitmap? = null
        if (requestCode == REQUEST_IMAGE_CAPTURE && resultCode == RESULT_OK) {
            Toast.makeText(this.context, "camera image captured...", Toast.LENGTH_SHORT).show()
            Log.d(TAG, "dispatchTakePictureIntent onActivityResult camera image captured...")
            // extra will contain thumbnail image if image capture not in play
            // if (data != null) {
            data?.let {
                data.extras?.let {
                        val extraPhotoUri: Uri = data.extras?.get(MediaStore.EXTRA_OUTPUT) as Uri
                        Log.d(TAG, "dispatchTakePictureIntent onActivityResult URI $extraPhotoUri")

                        imageBitmap = data.extras?.get("data") as Bitmap
                        imageView.setImageBitmap(imageBitmap)
                    } ?: run {
                        Log.e(TAG, "dispatchTakePictureIntent onActivityResult data.extras NULL.")
                    }
                } ?: run {
                    Log.e(TAG, "dispatchTakePictureIntent onActivityResult data NULL.")
                }
            // generate thumbnail from file uri if not available as thumbnail
            Log.d(TAG, "dispatchTakePictureIntent onActivityResult generate thumbnail...")
            // TODO: read photo into bitmap

            // Bitmap resized = ThumbnailUtils.extractThumbnail(sourceBitmap, width, height);
            //imageBitmap = ThumbnailUtils.extractThumbnail()
            // Bitmap resized = ThumbnailUtils.extractThumbnail(BitmapFactory.decodeFile(file.getPath()), width, height);

            try {
                // capture meters
                cameraAngle = angleMeter.getAngle()
                Log.d(TAG, "dispatchTakePictureIntent angleMeter.getAngle ->${cameraAngle.toString()}")
                decibel = soundMeter.deriveDecibel(forceFormat = true)
                Log.d(TAG, "dispatchTakePictureIntent soundMeter.deriveDecibel db->${decibel.toString()}")
//                var angle = angleMeter.getAngle()
//                Log.d(TAG, "dispatchTakePictureIntent onActivityResult angleMeter.getAngle ->${angle.toString()}")
//                var db = soundMeter.deriveDecibel(forceFormat = true)
//                Log.d(TAG, "dispatchTakePictureIntent onActivityResult soundMeter.deriveDecibel db->${db.toString()}")
            } catch (ex: Exception) {
                Log.e(TAG, "dispatchTakePictureIntent Meter Exception ${ex.stackTrace}")
            }

            // create AirCapture data class
            //val timeStamp = c
            //val location =
            val airCapture = AirCapture(currentPhotoPath, decibel, cameraAngle)
            val jsonCapture = Gson().toJson(airCapture)
            Log.d(TAG, "dispatchTakePictureIntent onActivityResult $jsonCapture")
            // loop dispatch until cancelled
            Log.d(TAG, "dispatchTakePictureIntent onActivityResult launching camera...")
            dispatchTakePictureIntent()
        }
        else if (requestCode == REQUEST_IMAGE_CAPTURE && resultCode == RESULT_CANCELED) {
            Toast.makeText(this.context, "camera canceled...", Toast.LENGTH_SHORT).show()
            Log.d(TAG, "dispatchTakePictureIntent onActivityResult camera canceled...")
            // stop angle meter
            angleMeter.stop()
            // stop sound meter
            soundMeter.stop()
        }
    }

    lateinit var currentPhotoPath: String

    @Throws(IOException::class)
    private fun createImageFile(): File {
        var context = getContext()
        // Create an image file name
        val timeStamp: String = SimpleDateFormat("yyyyMMdd_HHmmss").format(Date())
        val storageDir: File? = context?.getExternalFilesDir(Environment.DIRECTORY_PICTURES)
        Log.d(TAG, "createImageFile storageDir->${storageDir.toString()}")
        return File.createTempFile(
            "JPEG_${timeStamp}_", /* prefix */
            ".jpg", /* suffix */
            storageDir /* directory */
        ).apply {
            // Save a file: path for use with ACTION_VIEW intents
            currentPhotoPath = absolutePath
        }
    }
}