package tk.kvakva.qrcodescanner002

import android.Manifest
import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.*
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.params.StreamConfigurationMap
import android.media.Image
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.util.Size
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.core.*
import androidx.camera.core.Camera
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.databinding.DataBindingUtil
import androidx.documentfile.provider.DocumentFile
import androidx.exifinterface.media.ExifInterface
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.divider.MaterialDividerItemDecoration
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import kotlinx.coroutines.launch
import tk.kvakva.qrcodescanner002.databinding.ActivityMainBinding
import java.io.IOException
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
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

    private val mYuvToRgbConverter by lazy { YuvToRgbConverter(this) }

    //    private lateinit var outputDirectory: File
    private val scanner = BarcodeScanning.getClient(
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

    private var picpicker = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { picUri ->
        if (picUri == null) {
            Toast.makeText(this, "No PicTure Selected!", Toast.LENGTH_LONG).show()
        } else {            // binding.picImageView.setImageURI(picUri)

            val mBitmap: Bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                ImageDecoder.decodeBitmap(ImageDecoder.createSource(contentResolver, picUri))
            } else {

                val m = Matrix()
                contentResolver.openInputStream(picUri)?.use { inStream ->
                    ExifInterface(inStream).run {
                        m.postRotate(rotationDegrees.toFloat())
                        // If lat/long is null, fall back to the coordinates (0, 0).
                        //val latLong = latLong ?: doubleArrayOf(0.0, 0.0)
                    }

                }
                //val b = MediaStore.Images.Media.getBitmap(contentResolver, picUri)
                //                                                     API 29  Android 10
                val b = if(Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                    MediaStore.Images.Media.getBitmap(contentResolver, picUri)
                } else {
                    ImageDecoder.decodeBitmap(ImageDecoder.createSource(contentResolver, picUri))
                }

                Bitmap.createBitmap(b, 0, 0, b.width, b.height, m, true)
            }

//            val mBitmap: Bitmap = BitmapFactory
//                .decodeStream(
//                    contentResolver
//                        .openInputStream(
//                            picUri
//                        )
//                )

            val bitmap = mBitmap.copy(Bitmap.Config.ARGB_8888, true)
            val canvas = Canvas(bitmap)
            val paint = Paint()
            paint.color = Color.RED
            paint.strokeWidth = 10f
            paint.style = Paint.Style.STROKE
            paint.alpha = 50

            binding.picImageView.setImageBitmap(bitmap)
            binding.picImageView.visibility = View.VISIBLE
            val image: InputImage
            try { //image = InputImage.fromFilePath(this, picUri)
                image = InputImage.fromBitmap(bitmap, 0)
                Log.e(TAG, "${image.height}x${image.width}")
                //val scanner = BarcodeScanning.getClient()
                val result = scanner.process(image)
                    .addOnSuccessListener { barcodes ->
                        Log.e(TAG, "////////////\\\\\\\\ scanBarcodes: size-> ${barcodes.size}")
                        fillData(barcodes)
//                        viewModelMaAc.qrTvTxSet("")
//                        viewModelMaAc.listOfScannedTexts.postValue(listOf())
//                        val r = mutableListOf<DecodedText>()
//                        for (barcode in barcodes) {
//
//                            val rawValue = barcode.rawValue
//                            //viewModelMaAc.qrTvTxSet(viewModelMaAc.qrTvTx.value + "\n--------\n" + rawValue)
//                            r.add(
//                                DecodedText(
//                                    txt = (rawValue ?: "").replace(Char(29), '\n'),
//                                )
//                            )
//                            Log.v(TAG, "scanBarcodes: barcode.rawValue ====== ${barcode.rawValue}")
//                            Log.v(
//                                TAG,
//                                "scanBarcodes: barcode.displayValue ====== ${barcode.displayValue}"
//                            )
//                            val valueType = barcode.valueType
//                            when (valueType) {
//                                Barcode.TYPE_CALENDAR_EVENT -> {
//
//                                }
//
//                                Barcode.TYPE_WIFI -> {
//                                    val ssid = barcode.wifi!!.ssid
//                                    val password = barcode.wifi!!.password
//                                    val type = barcode.wifi!!.encryptionType
//                                }
//
//                                Barcode.TYPE_URL -> {
//                                    val title = barcode.url!!.title
//                                    val url = barcode.url!!.url
//                                }
//                            }
//                        }
                        if (barcodes.size > 0) {
                            for (barcode in barcodes) {
                                val bounds = barcode.boundingBox
                                val corners = barcode.cornerPoints

                                Log.e(TAG, bounds.toString())
                                if (bounds != null) {
                                    Log.e(TAG, "**** $bounds")
                                    canvas.drawRect(bounds, paint)
                                    canvas.drawLine(0f, 0f, 100f, 100f, paint)
                                }
                                binding.picImageView.invalidate()
                            }
                        }
                        //viewModelMaAc.listOfScannedTexts.postValue(r)
                    }
                    .addOnFailureListener {
                        Log.e(TAG, "scanBarcodes: ${it.stackTraceToString()}")
                    }
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }
    }

    lateinit var binding: ActivityMainBinding

    lateinit var recViewAdapter: ScannedCodesRecViewAdapter

    //@RequiresApi(Build.VERSION_CODES.M)
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

        recViewAdapter = ScannedCodesRecViewAdapter(::shareText)
        binding.scannedTextsRecyclerView.adapter = recViewAdapter
        binding.scannedTextsRecyclerView.addItemDecoration(
            MaterialDividerItemDecoration(
                this,
                LinearLayout.VERTICAL
            ).apply {
                this.isLastItemDecorated = false
                this.dividerThickness = 10
            })
        viewModelMaAc.listOfScannedTexts.observe(this) {
            recViewAdapter.data = it
        }

        binding.photoBtn.setOnClickListener(::takePhoto)

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                viewModelMaAc.flashActive.collect {
                    if (it)
                        camera?.cameraControl?.enableTorch(true)
                    else
                        camera?.cameraControl?.enableTorch(false)
                }
            }
        }

//        viewModelMaAc.sizesStrings.value?.let {
//            Log.e(TAG, "spinner:!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!! it.asList() $it")
//
//            ArrayAdapter<String>(
//                this, android.R.layout.simple_spinner_item, android.R.id.text1,
//                it
//            ).also {
//                binding.sizeSpinner.adapter = it
//                Log.e(
//                    TAG,
//                    "spinner:!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!! viewModelMaAc.sizesStrings.value?.asList( ${viewModelMaAc.sizesStrings.value}"
//                )
//            }
//        }

//        binding.sizeSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
//            /**
//             *
//             * Callback method to be invoked when an item in this view has been
//             * selected. This callback is invoked only when the newly selected
//             * position is different from the previously selected position or if
//             * there was no selected item.
//             *
//             * Implementers can call getItemAtPosition(position) if they need to access the
//             * data associated with the selected item.
//             *
//             * @param parent The AdapterView where the selection happened
//             * @param view The view within the AdapterView that was clicked
//             * @param position The position of the view in the adapter
//             * @param id The row id of the item that is selected
//             */
//            override fun onItemSelected(
//                parent: AdapterView<*>?,
//                view: View?,
//                position: Int,
//                id: Long
//            ) {
//                Log.i(
//                    TAG,
//                    "onItemSelected: position: $position, id: $id, mode: ${
//                        viewModelMaAc.sizes.value?.get(id.toInt())
//                    }"
//                )
//                viewModelMaAc.picSize.value =
//                    viewModelMaAc.sizes.value?.get(id.toInt()) ?: Size(1280, 720)
//                viewModelMaAc.savePicSize(viewModelMaAc.picSize.value.toString())
//                /*val s = resources.getStringArray(R.array.flash_mode)[id.toInt()]
//                flashMode = when (s) {
//                    "auto" -> ImageCapture.FLASH_MODE_AUTO
//                    "on" -> ImageCapture.FLASH_MODE_ON
//                    else -> ImageCapture.FLASH_MODE_OFF
//                }*/
//                startCamera()
//                //(view as TextView?)?.setTextColor(Color.RED)
//            }
//
//            /**
//             * Callback method to be invoked when the selection disappears from this
//             * view. The selection can disappear for instance when touch is activated
//             * or when the adapter becomes empty.
//             *
//             * @param parent The AdapterView that now contains no selected item.
//             */
//            override fun onNothingSelected(parent: AdapterView<*>?) {
//                Log.i(TAG, "onNothingSelected: NOTHING")
//            }
//        }

        viewModelMaAc.qrScnActive.observe(this) {
            if (it) {
                binding.picImageView.visibility = View.GONE
                addQrAnalyzer()
            } else
                delQrAnalyzer()
        }


        /*        viewModelMaAc.qrTvVis.observe(this) {
                    if (it) {
                        binding.qrResultTv.setBackgroundResource(android.R.drawable.editbox_background)
                    } else {
                        binding.qrResultTv.background = null
                    }
                }

                binding.qrResultTv.setOnClickListener {
                    viewModelMaAc.qrTvVis.value = !viewModelMaAc.qrTvVis.value!!
                }*/

        binding.scanPhotoBtn.setOnClickListener {
            picpicker.launch("image/*")
        }
        binding.picImageView.setOnClickListener {
            it.visibility = View.GONE
        }


    }

    lateinit var myMenu: Menu
    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        myMenu = menu
        val inflater: MenuInflater = menuInflater
        inflater.inflate(R.menu.options, menu)
        if (viewModelMaAc.dirUri.value != null)
            myMenu.findItem(R.id.select_dir).setIcon(R.drawable.ic_baseline_folder_24)

        viewModelMaAc.qrTvVis.observe(this) { scanned_results_visible: Boolean ->
            myMenu.findItem(R.id.show_scanned_results).setIcon(
                when (scanned_results_visible) {
                    true -> R.drawable.baseline_visibility_24
                    false -> R.drawable.baseline_visibility_off_24
                }
            )
        }
        val chsz = myMenu.findItem(R.id.choose_size)
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                viewModelMaAc.picSize.collect {
                   chsz.title="Choose pic size($it)"
                }
            }
        }
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.select_dir -> {
                dirpicker.launch(null)
                true
            }

//            R.id.select_size -> {
//                if (binding.previewView.visibility == View.VISIBLE) {
//                    binding.previewView.visibility = View.INVISIBLE
//                    binding.sizeSpinner.visibility = View.VISIBLE
//                } else {
//                    binding.previewView.visibility = View.VISIBLE
//                    binding.sizeSpinner.visibility = View.GONE
//                }
//                true
//            }

            R.id.choose_size -> {
                Log.v(TAG, "onOptionsItemSelected: ${viewModelMaAc.picSize.value}")
                AlertDialog.Builder(this)
                    .setTitle("Choose pictures resolutions")
                    .setItems(viewModelMaAc.sizesStrings.value?.toTypedArray()) { d, i ->
                        Log.i(
                            TAG,
                            "onItemS: i=$i ${
                                viewModelMaAc.sizes.value?.get(i)
                            }"
                        )
                        viewModelMaAc.picSize.value =
                            viewModelMaAc.sizes.value?.get(i) ?: Size(1280, 720)
                        viewModelMaAc.savePicSize(viewModelMaAc.picSize.value.toString())
                        //item.title="Choose pic size (${viewModelMaAc.picSize.value})"
                        startCamera()
                    }
                    .show()
                true
            }


            R.id.show_scanned_results -> {
                viewModelMaAc.qrTvVis.value = !(viewModelMaAc.qrTvVis.value ?: false)
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
                if (viewModelMaAc.qrScnActive.value == true) {
                    if (imageAnalyzer != null)
                        cameraProvider?.unbind(imageAnalyzer)

                    imageAnalyzer = ImageAnalysis.Builder()
                        .build()
                        .also {
                            it.setAnalyzer(cameraExecutor) { iprx ->
                                scanBarcodes(iprx)
                            }
                        }
                    camera = cameraProvider?.bindToLifecycle(
                        this, cameraSelector, preview, imageCapture, imageAnalyzer
                        //this, cameraSelector, preview, imageCapture, videoCapture
                    )
                } else {
                    // Bind use cases to camera
                    camera = cameraProvider?.bindToLifecycle(
                        this, cameraSelector, preview, imageCapture //, imageAnalyzer
                        //this, cameraSelector, preview, imageCapture, videoCapture
                    )
                }
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
                        //(binding.sizeSpinner.adapter as ArrayAdapter<String>).notifyDataSetChanged()
                        //(binding.sizeSpinner.adapter as ArrayAdapter<String>).notifyDataSetInvalidated()
//                        binding.sizeSpinner.setSelection(
//                            viewModelMaAc.sizesStrings.value?.indexOf(
//                                viewModelMaAc.picSize.value.toString()
//                            ) ?: 0
//                        )
                        //myMenu.findItem(R.id.choose_size).title="Choose pic size (${viewModelMaAc.picSize.value})"
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
            .Builder(
                contentResolver,
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                ContentValues().apply {
                    put(
                        MediaStore.MediaColumns.DISPLAY_NAME,
                        LocalDateTime.now().format(DateTimeFormatter.ofPattern(FILENAME_FORMAT))
                    )
                    put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
                    if (Build.VERSION.SDK_INT > Build.VERSION_CODES.P) {
                        put(
                            MediaStore.Images.Media.RELATIVE_PATH,
                            "Pictures/" + resources.getString(R.string.app_name)
                        ) //SevasCameraApp")
                    }
                }//contentValues
            )
            .build()


        //ImageCapture.OutputFileOptions
        //    .Builder(File(filesDir, datelocaltimestring() + ".jpeg"))
        //    .build()


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
        Log.e(
            TAG,
            "scanBarcodes:\nHxW -> ${i.height}X${i.width}\nFormat:${i.format} 0x${
                i.format.toString(16)
            }\n---------------"
        )
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
                // Task completed successfully
                // [START_EXCLUDE]
                // [START get_barcodes]
                //viewModelMaAc.qrTvTxSet("")
                fillData(barcodes)

                if (barcodes.size > 0) {
                    delQrAnalyzer()
                    viewModelMaAc.qrScnOff()
                    if (i.format == ImageFormat.YUV_420_888) {
                        val b0 = Bitmap.createBitmap(i.width, i.height, Bitmap.Config.ARGB_8888)
//                            if (i.imageInfo.rotationDegrees == 90 || i.imageInfo.rotationDegrees == 270) {
//                                Bitmap.createBitmap(i.height, i.width, Bitmap.Config.ARGB_8888)
//                            } else {
//                                Bitmap.createBitmap(i.width, i.height, Bitmap.Config.ARGB_8888)
//                            }
                        mYuvToRgbConverter.yuvToRgb(i.image!!, b0)
                        val b = if (i.imageInfo.rotationDegrees > 0) {
                            val m = Matrix()
                            m.postRotate(i.imageInfo.rotationDegrees.toFloat())
                            Bitmap.createBitmap(b0, 0, 0, i.width, i.height, m, true)
                        } else {
                            b0
                        }
                        val c = Canvas(b)
                        val p = Paint()
                        p.color = Color.GREEN
                        p.strokeWidth = 10f
                        p.style = Paint.Style.STROKE
                        p.alpha = 60
                        for (barcode in barcodes) {
                            if (barcode.boundingBox != null) {
                                Log.e(TAG, "**** ${barcode.boundingBox}")
                                c.drawRect(barcode.boundingBox!!, p)
                            }
                        }
                        binding.picImageView.setImageBitmap(b)
                        binding.picImageView.visibility = View.VISIBLE
                    }
                }
                i.close()
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

    fun fillData(barcodes: List<Barcode>) {
        //viewModelMaAc.listOfScannedTexts.postValue(listOf())
        val r = mutableListOf<DecodedText>()
        for (barcode: Barcode in barcodes) {
            val bounds = barcode.boundingBox
            val corners = barcode.cornerPoints

            Log.v(TAG, "scanBarcodes: barcode.type = ${barcode.valueType}")
            Log.v(TAG, "scanBarcodes: barcode.format = ${barcode.format}")
            //viewModelMaAc.qrTvTxSet(viewModelMaAc.qrTvTx.value + "--------\n" + rawValue + "\n")
            r.add(
                DecodedText(
                    txt = (barcode.rawValue ?: "").replace(Char(29), '\n'),
                    rawBytes = barcode.rawBytes?.joinToString(" ") { "0x%02x".format(it) }
                        ?: "",
                    displayVl = barcode.displayValue ?: "",
                    fomrat = when (barcode.format) {
                        //      FORMAT_UNKNOWN = -1;
                        Barcode.FORMAT_UNKNOWN -> "FORMAT_UNKNOWN"
                        //      FORMAT_ALL_FORMATS = 0;
                        Barcode.FORMAT_ALL_FORMATS -> "FORMAT_ALL_FORMATS"
                        //      FORMAT_CODE_128 = 1;
                        Barcode.FORMAT_CODE_128 -> "FORMAT_CODE_128"
                        //      FORMAT_CODE_39 = 2;
                        Barcode.FORMAT_CODE_39 -> "FORMAT_CODE_39"
                        //      FORMAT_CODE_93 = 4;
                        Barcode.FORMAT_CODE_93 -> "FORMAT_CODE_93"
                        //      FORMAT_CODABAR = 8;
                        Barcode.FORMAT_CODABAR -> "FORMAT_CODABAR"
                        //      FORMAT_DATA_MATRIX = 16;
                        Barcode.FORMAT_DATA_MATRIX -> "FORMAT_DATA_MATRIX"
                        //      FORMAT_EAN_13 = 32;
                        Barcode.FORMAT_EAN_13 -> "FORMAT_EAN_13"
                        //      FORMAT_EAN_8 = 64;
                        Barcode.FORMAT_EAN_8 -> "FORMAT_EAN_8"
                        //      FORMAT_ITF = 128;
                        Barcode.FORMAT_ITF -> "FORMAT_ITF"
                        //      FORMAT_QR_CODE = 256;
                        Barcode.FORMAT_QR_CODE -> "FORMAT_QR_CODE"
                        //      FORMAT_UPC_A = 512;
                        Barcode.FORMAT_UPC_A -> "FORMAT_UPC_A"
                        //      FORMAT_UPC_E = 1024;
                        Barcode.FORMAT_UPC_E -> "FORMAT_UPC_E"
                        //      FORMAT_PDF417 = 2048;
                        Barcode.FORMAT_PDF417 -> "FORMAT_PDF417"
                        //      FORMAT_AZTEC = 4096;
                        Barcode.FORMAT_AZTEC -> "FORMAT_AZTEC"
                        else -> "FORMAT_UNKNOWN_AT_ALL"
                    },
                    type = when (barcode.valueType) {
                        // TYPE_UNKNOWN = 0;
                        Barcode.TYPE_UNKNOWN -> "TYPE_UNKNOWN"
                        // TYPE_CONTACT_INFO = 1;
                        Barcode.TYPE_CONTACT_INFO -> "TYPE_CONTACT_INFO"
                        // TYPE_EMAIL = 2;
                        Barcode.TYPE_EMAIL -> "TYPE_EMAIL"
                        // TYPE_ISBN = 3;
                        Barcode.TYPE_ISBN -> "TYPE_ISBN"
                        // TYPE_PHONE = 4;
                        Barcode.TYPE_PHONE -> "TYPE_PHONE"
                        // TYPE_PRODUCT = 5;
                        Barcode.TYPE_PRODUCT -> "TYPE_PRODUCT"
                        // TYPE_SMS = 6;
                        Barcode.TYPE_SMS -> "TYPE_SMS"
                        // TYPE_TEXT = 7;
                        Barcode.TYPE_TEXT -> "TYPE_TEXT"
                        // TYPE_URL = 8;
                        Barcode.TYPE_URL -> "TYPE_URL"
                        // TYPE_WIFI = 9;
                        Barcode.TYPE_WIFI -> "TYPE_WIFI"
                        // TYPE_GEO = 10;
                        Barcode.TYPE_GEO -> "TYPE_GEO"
                        // TYPE_CALENDAR_EVENT = 11;
                        Barcode.TYPE_CALENDAR_EVENT -> "TYPE_CALENDAR_EVENT"
                        // TYPE_DRIVER_LICENSE = 12;
                        Barcode.TYPE_DRIVER_LICENSE -> "TYPE_DRIVER_LICENSE"
                        else -> "unknown at all"
                    }
                )
            )
            Log.v(TAG, "scanBarcodes: barcode.rawValue ====== ${barcode.rawValue}")
            Log.v(TAG, "scanBarcodes: barcode.displayValue ====== ${barcode.displayValue}")

            val valueType = barcode.valueType

            // See API reference for complete list of supported types
            Log.v(TAG, "valueType = $valueType")
            Log.v(TAG, "raw Bytes = ${barcode.rawValue?.replace(Char(29), '\n')}")
            Log.v(TAG, "raw Bytes = ${barcode.rawBytes?.toList()}")
            Log.v(
                TAG,
                "raw hex Bytes = ${barcode.rawBytes?.joinToString(" ") { "0x%02x".format(it) } ?: ""}"
            )
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
        viewModelMaAc.listOfScannedTexts.postValue(r)
    }

    fun addQrAnalyzer() {

        if (cameraProvider == null)
            return

        if (imageAnalyzer != null)
            cameraProvider?.unbind(imageAnalyzer)

        imageAnalyzer = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setTargetResolution(
                if (this.resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT)
                    Size(viewModelMaAc.picSize.value.height, viewModelMaAc.picSize.value.width)
                else
                    viewModelMaAc.picSize.value
                //Size(1080,1920)
            )
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

    private var pixelCount: Int = -1
    private fun imageToByteArray(image: Image, outputBuffer: ByteArray) {
        assert(image.format == ImageFormat.YUV_420_888)

        val imageCrop = image.cropRect
        val imagePlanes = image.planes

        imagePlanes.forEachIndexed { planeIndex, plane ->
            // How many values are read in input for each output value written
            // Only the Y plane has a value for every pixel, U and V have half the resolution i.e.
            //
            // Y Plane            U Plane    V Plane
            // ===============    =======    =======
            // Y Y Y Y Y Y Y Y    U U U U    V V V V
            // Y Y Y Y Y Y Y Y    U U U U    V V V V
            // Y Y Y Y Y Y Y Y    U U U U    V V V V
            // Y Y Y Y Y Y Y Y    U U U U    V V V V
            // Y Y Y Y Y Y Y Y
            // Y Y Y Y Y Y Y Y
            // Y Y Y Y Y Y Y Y
            val outputStride: Int

            // The index in the output buffer the next value will be written at
            // For Y it's zero, for U and V we start at the end of Y and interleave them i.e.
            //
            // First chunk        Second chunk
            // ===============    ===============
            // Y Y Y Y Y Y Y Y    V U V U V U V U
            // Y Y Y Y Y Y Y Y    V U V U V U V U
            // Y Y Y Y Y Y Y Y    V U V U V U V U
            // Y Y Y Y Y Y Y Y    V U V U V U V U
            // Y Y Y Y Y Y Y Y
            // Y Y Y Y Y Y Y Y
            // Y Y Y Y Y Y Y Y
            var outputOffset: Int

            when (planeIndex) {
                0 -> {
                    outputStride = 1
                    outputOffset = 0
                }

                1 -> {
                    outputStride = 2
                    // For NV21 format, U is in odd-numbered indices
                    outputOffset = pixelCount + 1
                }

                2 -> {
                    outputStride = 2
                    // For NV21 format, V is in even-numbered indices
                    outputOffset = pixelCount
                }

                else -> {
                    // Image contains more than 3 planes, something strange is going on
                    return@forEachIndexed
                }
            }

            val planeBuffer = plane.buffer
            val rowStride = plane.rowStride
            val pixelStride = plane.pixelStride

            // We have to divide the width and height by two if it's not the Y plane
            val planeCrop = if (planeIndex == 0) {
                imageCrop
            } else {
                Rect(
                    imageCrop.left / 2,
                    imageCrop.top / 2,
                    imageCrop.right / 2,
                    imageCrop.bottom / 2
                )
            }

            val planeWidth = planeCrop.width()
            val planeHeight = planeCrop.height()

            // Intermediate buffer used to store the bytes of each row
            val rowBuffer = ByteArray(plane.rowStride)

            // Size of each row in bytes
            val rowLength = if (pixelStride == 1 && outputStride == 1) {
                planeWidth
            } else {
                // Take into account that the stride may include data from pixels other than this
                // particular plane and row, and that could be between pixels and not after every
                // pixel:
                //
                // |---- Pixel stride ----|                    Row ends here --> |
                // | Pixel 1 | Other Data | Pixel 2 | Other Data | ... | Pixel N |
                //
                // We need to get (N-1) * (pixel stride bytes) per row + 1 byte for the last pixel
                (planeWidth - 1) * pixelStride + 1
            }

            for (row in 0 until planeHeight) {
                // Move buffer position to the beginning of this row
                planeBuffer.position(
                    (row + planeCrop.top) * rowStride + planeCrop.left * pixelStride
                )

                if (pixelStride == 1 && outputStride == 1) {
                    // When there is a single stride value for pixel and output, we can just copy
                    // the entire row in a single step
                    planeBuffer.get(outputBuffer, outputOffset, rowLength)
                    outputOffset += rowLength
                } else {
                    // When either pixel or output have a stride > 1 we must copy pixel by pixel
                    planeBuffer.get(rowBuffer, 0, rowLength)
                    for (col in 0 until planeWidth) {
                        outputBuffer[outputOffset] = rowBuffer[col * pixelStride]
                        outputOffset += outputStride
                    }
                }
            }
        }
    }

    fun shareText(text: String) {
        val sendIntent: Intent = Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_TEXT, text)
            type = "text/plain"
        }
        val shareIntent = Intent.createChooser(sendIntent, "Send decoded text")
        startActivity(shareIntent)
    }

}

fun datelocaltimestring() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
    LocalDateTime.now().toString()
} else {
    Date().toString()
}
