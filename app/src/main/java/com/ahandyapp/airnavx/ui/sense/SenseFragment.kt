///////////////////////////////////////////////////////////////////////////////
package com.ahandyapp.airnavx.ui.sense

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager.*
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.media.MediaRecorder
import android.os.Bundle
import android.os.CountDownTimer
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import androidx.core.app.ActivityCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import com.ahandyapp.airnavx.databinding.FragmentSenseBinding
import java.io.File
import kotlin.math.PI
import kotlin.math.log10
import kotlin.math.truncate


class SenseFragment : Fragment() {

    private val TAG = "SenseFragment"

    //////////////////
    // angle & location meters
    private var angleMeter = AngleMeter()
    private var soundMeter = SoundMeter()

    private val REQUEST_RECORD_AUDIO_PERMISSION = 200
    // view refresh timer
    private var timerOn = false

    private lateinit var senseViewModel: SenseViewModel
    private var _binding: FragmentSenseBinding? = null

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        senseViewModel =
            ViewModelProvider(this).get(SenseViewModel::class.java)

        _binding = FragmentSenseBinding.inflate(inflater, container, false)
        val root: View = binding.root

        val textViewTitle: TextView = binding.textSense
        senseViewModel.textTitle.observe(viewLifecycleOwner, Observer {
            textViewTitle.text = it
        })

        val editTextAngle: EditText = binding.editCameraAngle
        senseViewModel.editCameraAngle.observe(viewLifecycleOwner, Observer {
            editTextAngle.text = it.toString().toEditable()
        })
        val editTextDecibel: EditText = binding.editDecibelLevel
        senseViewModel.editDecibelLevel.observe(viewLifecycleOwner, Observer {
            editTextDecibel.text = it.toString().toEditable()
        })

        editTextAngle.addTextChangedListener(object : TextWatcher {

            override fun afterTextChanged(s: Editable) {}

            override fun beforeTextChanged(s: CharSequence, start: Int,
                                           count: Int, after: Int) {
            }

            override fun onTextChanged(s: CharSequence, start: Int,
                                       before: Int, count: Int) {
                if (count > 0) {
                    val angleText = s.toString()
                    Log.d(TAG, "onTextChanged incoming angle = $angleText")
                    // constrain infinite loop!
                    if (!senseViewModel.editCameraAngle.value.toString().equals(angleText)) {
                        senseViewModel.editCameraAngle.value = angleText.toInt()
                    }
                    val viewModelText = senseViewModel.editCameraAngle.value.toString()
                    Log.d(TAG, "SenseViewModel angle = $viewModelText")
                }
            }
        })

        //////////////////
        // angle meter one-time init
        angleMeter.create(requireActivity())
        //////////////////

        return root
    }

    fun String.toEditable(): Editable =  Editable.Factory.getInstance().newEditable(this)

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    //////////////////
    // onResume - start angle & sound meters, start timer
    override fun onResume() {
        super.onResume()

        // TODO: why not location permission required?
        // start angle meter
        angleMeter.start()
        Log.d(TAG, "onResume angleMeter started.")

        if (isPermissionAudioGranted()) {
            // start sound meter
            this.context?.let { soundMeter.start(it) }
            Log.d(TAG, "onResume soundMeter started.")
        }

        // start timer
        timerOn = true
        timer.start()
    }

    override fun onPause() {
        super.onPause()

        // stop angle meter
        angleMeter.stop()
        // stop sound meter
        soundMeter.stop()
        // turn off timer
        timerOn = false
    }

    private val timer = object: CountDownTimer(8000, 500) {
        override fun onTick(millisUntilFinished: Long) {
            onTimerUpdateView()
            Log.d(TAG, "timer onTick ${millisUntilFinished.toString()} millisUntilFinished...")
        }

        override fun onFinish() {
            Log.d(TAG, "timer onFinish...")
            if (timerOn) start()
        }
    }

    fun onTimerUpdateView() {
        // update angle UI
        var angle = angleMeter.getAngle()
        Log.d(TAG, "onTimerUpdateView angleMeter.getAngle ->${angle.toString()}")
        senseViewModel.editCameraAngle.value = angle

        // update decibel UI
        if (isPermissionAudioGranted()) {
            var db = soundMeter.deriveDecibel(forceFormat = true)
            Log.d(TAG, "onTimerUpdateView soundMeter.deriveDecibel db->${db.toString()}")
            senseViewModel.editDecibelLevel.value = db
        }

    }
    //////////////////////
    // permissions
    fun isPermissionAudioGranted(): Boolean {
        if (this.context?.let { ActivityCompat.checkSelfPermission(it, Manifest.permission.RECORD_AUDIO) }
            != PERMISSION_GRANTED
        ) {
            this.activity?.let {
                ActivityCompat.requestPermissions(
//                    it, arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.WRITE_EXTERNAL_STORAGE),
                    it, arrayOf(Manifest.permission.RECORD_AUDIO),
                    REQUEST_RECORD_AUDIO_PERMISSION
                )
            }
        } else {
            Log.d(TAG, "onRequestPermissionAudio Manifest.permission.RECORD_AUDIO granted.")
            return true
        }
        Log.d(TAG, "onRequestPermissionAudio Manifest.permission.RECORD_AUDIO denied!")
        return false
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<String?>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_RECORD_AUDIO_PERMISSION) {
            if (grantResults[0] == PERMISSION_GRANTED) {
                Log.d(TAG, "onRequestPermissionsResult Manifest.permission.RECORD_AUDIO granted.")
            } else {
                Log.d(TAG, "onRequestPermissionsResult Manifest.permission.RECORD_AUDIO denied!")
            }
        }
    }
}
///////////////////////////////////////////////////////////////////////////////
