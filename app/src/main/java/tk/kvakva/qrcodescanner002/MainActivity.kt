package tk.kvakva.qrcodescanner002

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.ImageFormat
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.params.StreamConfigurationMap
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.util.Size
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.core.*
import androidx.camera.core.ImageCapture
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.databinding.DataBindingUtil
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.lifecycleScope
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import tk.kvakva.qrcodescanner002.databinding.ActivityMainBinding
import java.io.File
import java.time.LocalDateTime
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

// typealias LumaListener = (luma: Double) -> Unit

private const val TAG = "MainActivity"

class MainActivity : AppCompatActivity() {
    //val sizesArray: ArrayList<String> = arrayListOf<String>()
    private var imageCapture: ImageCapture? = null
    private var videoCapture: VideoCapture? = null
    private var camera: Camera? = null
    private lateinit var cameraExecutor: ExecutorService
    private var cameraProvider: ProcessCameraProvider? = null
    private var imageAnalyzer: ImageAnalysis? = null

    //    private lateinit var outputDirectory: File
    val scanner = BarcodeScanning.getClient(
        BarcodeScannerOptions.Builder()
            .setBarcodeFormats(
                Barcode.FORMAT_ALL_FORMATS
                //Barcode.FORMAT_QR_CODE,
                //Barcode.FORMAT_AZTEC
            )
            .build()
    )

    private val viewModelMaAc by viewModels<ViewModelMainActivity>()
    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            if (allPermissionsGranted()) {

                startCamera()
            } else {
                Toast.makeText(
                    this,
                    "Permissions not granted by the user.",
                    Toast.LENGTH_SHORT
                ).show()
                finish()
            }
        }

    private var dirpicker = registerForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri == null) {
            Log.i(TAG, "No catalog selected")
            Toast.makeText(this, "No catalog selected", Toast.LENGTH_SHORT).show()
            return@registerForActivityResult
        }
        Log.i(TAG, "selected: $uri")
        viewModelMaAc.saveLocalUri("$uri")
        myMenu.findItem(R.id.select_dir).setIcon(R.drawable.ic_baseline_folder_24)

        val takeFlags: Int = Intent.FLAG_GRANT_READ_URI_PERMISSION or
                Intent.FLAG_GRANT_WRITE_URI_PERMISSION
// Check for the freshest data.
        contentResolver.takePersistableUriPermission(uri, takeFlags)
    }


    lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        //setContentView(R.layout.activity_main)
        cameraExecutor = Executors.newSingleThreadExecutor()

        binding = DataBindingUtil.setContentView(this, R.layout.activity_main)
        binding.viewmodelmainactivity = viewModelMaAc
        binding.lifecycleOwner = this
        // Request camera permissions
        if (allPermissionsGranted()) {
            startCamera()
        } else {
            requestPermissionLauncher.launch(REQUIRED_PERMISSIONS)
        }

        lifecycleScope.launch {
            viewModelMaAc.flashActive.collect {
                if (it)
                    camera?.cameraControl?.enableTorch(true)
                else
                    camera?.cameraControl?.enableTorch(false)
            }
        }

        viewModelMaAc.sizesStrings.value?.let {
            Log.e(TAG, "spinner:!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!! it.asList() $it")

            ArrayAdapter<String>(
                this, android.R.layout.simple_spinner_item, android.R.id.text1,
                it
            ).also {
                binding.sizeSpinner.adapter = it
                Log.e(
                    TAG,
                    "spinner:!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!! viewModelMaAc.sizesStrings.value?.asList( ${viewModelMaAc.sizesStrings.value}"
                )
            }
        }

        binding.sizeSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            /**
             *
             * Callback method to be invoked when an item in this view has been
             * selected. This callback is invoked only when the newly selected
             * position is different from the previously selected position or if
             * there was no selected item.
             *
             * Implementers can call getItemAtPosition(position) if they need to access the
             * data associated with the selected item.
             *
             * @param parent The AdapterView where the selection happened
             * @param view The view within the AdapterView that was clicked
             * @param position The position of the view in the adapter
             * @param id The row id of the item that is selected
             */
            override fun onItemSelected(
                parent: AdapterView<*>?,
                view: View?,
                position: Int,
                id: Long
            ) {
                Log.i(
                    TAG,
                    "onItemSelected: position: $position, id: $id, mode: ${
                        viewModelMaAc.sizes.value?.get(id.toInt())
                    }"
                )
                viewModelMaAc.picSize.value =
                    viewModelMaAc.sizes.value?.get(id.toInt()) ?: Size(1280, 720)
                viewModelMaAc.savePicSize(viewModelMaAc.picSize.value.toString())
                /*val s = resources.getStringArray(R.array.flash_mode)[id.toInt()]
                flashMode = when (s) {
                    "auto" -> ImageCapture.FLASH_MODE_AUTO
                    "on" -> ImageCapture.FLASH_MODE_ON
                    else -> ImageCapture.FLASH_MODE_OFF
                }*/
                startCamera()
                //(view as TextView?)?.setTextColor(Color.RED)
            }

            /**
             * Callback method to be invoked when the selection disappears from this
             * view. The selection can disappear for instance when touch is activated
             * or when the adapter becomes empty.
             *
             * @param parent The AdapterView that now contains no selected item.
             */
            override fun onNothingSelected(parent: AdapterView<*>?) {
                Log.i(TAG, "onNothingSelected: NOTHING")
            }
        }

        viewModelMaAc.qrScnActive.observe(this) {
            if (it)
                addQrAnalyzer()
            else
                delQrAnalyzer()
        }

    }

    lateinit var myMenu: Menu
    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        myMenu = menu
        val inflater: MenuInflater = menuInflater
        inflater.inflate(R.menu.options, menu)
        if (viewModelMaAc.dirUri.value != null)
            myMenu.findItem(R.id.select_dir).setIcon(R.drawable.ic_baseline_folder_24)

        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.select_dir -> {
                dirpicker.launch(null)
                true
            }
            R.id.select_size -> {
                if (binding.previewView.visibility == View.VISIBLE) {
                    binding.previewView.visibility = View.INVISIBLE
                    binding.sizeSpinner.visibility = View.VISIBLE
                } else {
                    binding.previewView.visibility = View.VISIBLE
                    binding.sizeSpinner.visibility = View.GONE
                }
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    @SuppressLint("UnsafeOptInUsageError", "RestrictedApi")
    private fun startCamera() {
        Log.e(TAG, "startCamera: !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!")
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener(Runnable {
            // Used to bind the lifecycle of cameras to the lifecycle owner
            cameraProvider = cameraProviderFuture.get()

            // Preview
            val preview = Preview.Builder()
                .build()
                .also {
                    it.setSurfaceProvider(findViewById<androidx.camera.view.PreviewView>(R.id.previewView).surfaceProvider)
                }

//            if(this.resources.configuration.orientation==Configuration.ORIENTATION_PORTRAIT)
//                Size(viewModelMaAc.picSize.value.height,viewModelMaAc.picSize.value.width)
            imageCapture = ImageCapture.Builder()
                //    .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                //.setFlashMode(flashMode)
                //.setTargetResolution(viewModelMaAc.picSize.value)
                //.setTargetResolution(Size(1080,1920))
                .setTargetResolution(
                    if (this.resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT)
                        Size(viewModelMaAc.picSize.value.height, viewModelMaAc.picSize.value.width)
                    else
                        viewModelMaAc.picSize.value
                )
                .build()


            // Select back camera as a default
            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            /*videoCapture = VideoCapture.Builder()
                .build()*/

            try {
                // Unbind use cases before rebinding
                cameraProvider?.unbindAll()

                // Bind use cases to camera
                camera = cameraProvider?.bindToLifecycle(
                    this, cameraSelector, preview, imageCapture //, imageAnalyzer
                    //this, cameraSelector, preview, imageCapture, videoCapture
                )
                camera?.let { camera ->
                    val cameraCaractristics =
                        Camera2CameraInfo.extractCameraCharacteristics(camera.cameraInfo)
                    cameraCaractristics.keys.forEach {
                        Log.i(
                            TAG,
                            "startCamera: $it\ncameraCaractristics: ${cameraCaractristics.get(it)}"
                        )
                    }
                    val streamConfigurationMap: StreamConfigurationMap? =
                        cameraCaractristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)

                    Log.i(
                        TAG,
                        "------------------------------- streamConfigurationMap?.getOutputSizes(ImageFormat.JPEG)\n${
                            streamConfigurationMap?.getOutputSizes(ImageFormat.JPEG)?.asList()
                        }\n-----------------------------------"
                    )
                    streamConfigurationMap?.getOutputSizes(ImageFormat.JPEG)?.let {
                        viewModelMaAc.sizes.value = it
                        viewModelMaAc.sizesStrings.value?.clear()
                        viewModelMaAc.sizesStrings.value?.addAll(it.map {
                            it.toString()
                        })
                        (binding.sizeSpinner.adapter as ArrayAdapter<String>).notifyDataSetChanged()
                        //(binding.sizeSpinner.adapter as ArrayAdapter<String>).notifyDataSetInvalidated()
                        binding.sizeSpinner.setSelection(
                            viewModelMaAc.sizesStrings.value?.indexOf(
                                viewModelMaAc.picSize.value.toString()
                            ) ?: 0
                        )
                        Log.e(
                            TAG,
                            "startCamera: viewModelMaAc.sizes.value ${viewModelMaAc.sizes.value?.asList()}"
                        )
                    }

                    streamConfigurationMap?.getOutputSizes(MediaRecorder::class.java)?.let {
                        Log.e(
                            TAG,
                            "startCamera: streamConfigurationMap?.getOutputSizes(MediaRecorder::class.java)?.let ---\n${it.asList()}\n---"
                        )
                    }


                    camera.cameraControl.enableTorch(viewModelMaAc.flashActive.value)
                }
            } catch (exc: Exception) {
                Log.e(TAG, "Use case binding failed", exc)
            }

        }, ContextCompat.getMainExecutor(this))
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(
            baseContext, it
        ) == PackageManager.PERMISSION_GRANTED
    }

    companion object {
        private const val FILENAME_FORMAT = "yyyy-MM-dd-HH-mm-ss-SSS"

        //  private const val REQUEST_CODE_PERMISSIONS = 10
        private val REQUIRED_PERMISSIONS =
            arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)
    }


    fun takePhoto(v: View) {

        if (viewModelMaAc.photoAction.value == ViewModelMainActivity.PhotoAction.Non)
            viewModelMaAc.photoAction.value = ViewModelMainActivity.PhotoAction.GettingPhoto
        else
            return
        Log.e(TAG, "takePhoto: RUNNED")
        // Get a stable reference of the modifiable image capture use case
        val imageCapture = imageCapture ?: return
        Log.e(TAG, "takePhoto: RUNNED after imageCapture")

        // Create time-stamped output file to hold the image
        Log.e(TAG, "takePhoto: ${viewModelMaAc.dirUri.value}")
        val docFile: DocumentFile? =
            if (viewModelMaAc.dirUri.value != null) {
                Log.e(TAG, "takePhoto: ${viewModelMaAc.dirUri.value}")
                try {
                    DocumentFile.fromTreeUri(
                        this,
                        Uri.parse(viewModelMaAc.dirUri.value!!)
                    )
                        ?.createFile(
                            "image/jpeg",
                            datelocaltimestring()
                        )
                } catch (e: IllegalArgumentException) {
                    e.printStackTrace()
                    Toast.makeText(this, e.stackTraceToString(), Toast.LENGTH_LONG).show()
                    null
                }
                //File(viewModelMaAc.dirUri.value!!)
            } else {
                Log.e(TAG, "takePhoto: $filesDir")
                null
            }


        val outputOptions = docFile?.let {
            contentResolver.openOutputStream(it.uri)?.let {
                ImageCapture.OutputFileOptions
                    .Builder(
                        it
                    )
                    .build()
            }
        } ?: ImageCapture.OutputFileOptions
            .Builder(File(filesDir, datelocaltimestring() + ".jpeg"))
            .build()


        //val photoFile =
        /*File(
        outputDirectory,
        SimpleDateFormat(
            FILENAME_FORMAT, Locale.US
        ).format(System.currentTimeMillis()) + ".jpg"*/
        //)

        // Create output options object which contains file + metadata
        //val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()


        // Set up image capture listener, which is triggered after photo has
        // been taken
        imageCapture.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageSavedCallback {
                override fun onError(exc: ImageCaptureException) {
                    Log.e(TAG, "Photo capture failed: ${exc.message}", exc)
                    viewModelMaAc.photoAction.value = ViewModelMainActivity.PhotoAction.Non
                }

                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    val savedUri = output.savedUri
                    val msg = "Photo capture succeeded: $savedUri"
                    Toast.makeText(baseContext, msg, Toast.LENGTH_SHORT).show()
                    Log.d(TAG, msg)
                    viewModelMaAc.photoAction.value = ViewModelMainActivity.PhotoAction.Non
                }
            })
    }


    //fun scanBarcodes(i: InputImage) {
    @SuppressLint("UnsafeOptInUsageError")
    fun scanBarcodes(i: ImageProxy) {

        if (i.image == null)
            return
        val image = InputImage.fromMediaImage(i.image!!, i.imageInfo.rotationDegrees)
        // [START set_detector_options]
        val options = BarcodeScannerOptions.Builder()
            .setBarcodeFormats(
                Barcode.FORMAT_ALL_FORMATS
                //Barcode.FORMAT_QR_CODE,
                //Barcode.FORMAT_AZTEC
            )
            .build()
        // [END set_detector_options]

        // [START get_detector]
        //val scanner = BarcodeScanning.getClient()
        // Or, to specify the formats to recognize:
        // val scanner = BarcodeScanning.getClient(options)
        // [END get_detector]

        // [START run_detector]
        val result = scanner.process(image)
            .addOnSuccessListener { barcodes ->
                Log.e(TAG, "//////////// scanBarcodes: $barcodes")
                // Task completed successfully
                // [START_EXCLUDE]
                // [START get_barcodes]
                viewModelMaAc.qrTvTxSet("")
                for (barcode in barcodes) {

                    val bounds = barcode.boundingBox
                    val corners = barcode.cornerPoints

                    val rawValue = barcode.rawValue

                    viewModelMaAc.qrTvTxSet(viewModelMaAc.qrTvTx.value + "\n--------\n" + rawValue)

                    Log.e(TAG, "scanBarcodes: barcode.rawValue ====== ${barcode.rawValue}")
                    val valueType = barcode.valueType
                    // See API reference for complete list of supported types
                    when (valueType) {
                        Barcode.TYPE_WIFI -> {
                            val ssid = barcode.wifi!!.ssid
                            val password = barcode.wifi!!.password
                            val type = barcode.wifi!!.encryptionType
                        }
                        Barcode.TYPE_URL -> {
                            val title = barcode.url!!.title
                            val url = barcode.url!!.url
                        }
                    }
                }
                i.close()
                if (barcodes.size > 0) {
                    delQrAnalyzer()
                    viewModelMaAc.qrScnOff()
                }
                // [END get_barcodes]
                // [END_EXCLUDE]
            }
            .addOnFailureListener {
                // Task failed with an exception
                Log.e(TAG, "scanBarcodes: ${it.stackTraceToString()}")
                // ...
            }


        // [END run_detector]
    }

    fun addQrAnalyzer() {

        if (cameraProvider == null)
            return

        if (imageAnalyzer != null)
            cameraProvider?.unbind(imageAnalyzer)

        imageAnalyzer = ImageAnalysis.Builder()
            .build()
            .also {
                it.setAnalyzer(cameraExecutor) { iprx ->
                    scanBarcodes(iprx)
                }
            }

        cameraProvider?.bindToLifecycle(
            this,
            CameraSelector.DEFAULT_BACK_CAMERA,
            imageAnalyzer
        )
    }

    fun delQrAnalyzer() {
        if (cameraProvider == null)
            return
        if (imageAnalyzer != null)
            cameraProvider?.unbind(imageAnalyzer)
    }


}

fun datelocaltimestring() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
    LocalDateTime.now().toString()
} else {
    Date().toString()
}
