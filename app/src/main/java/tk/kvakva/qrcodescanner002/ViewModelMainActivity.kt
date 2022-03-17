package tk.kvakva.qrcodescanner002

import android.app.Application
import android.content.Context
import android.util.Log
import android.util.Size
import android.widget.ImageButton
import android.widget.Toast
import androidx.databinding.BindingAdapter
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val TAG = "ViewModelMainActivity"
private const val DIR_URI = "dir_uri"
private const val PIC_SIZE = "pic_size"

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "urlsanduri")

class ViewModelMainActivity(private val apl: Application) : AndroidViewModel(apl) {

    private val _qrScnActive = MutableLiveData(false)
    val qrScnActive: LiveData<Boolean> = _qrScnActive
//    private val _qrScnActive = MutableStateFlow(false)
//    val qrScnActive: StateFlow<Boolean> = _qrScnActive

    private val _qrTvTx = MutableLiveData("XXXXXX XXXXXX XXXXXX")
    val qrTvTx: LiveData<String> = _qrTvTx

    fun qrTvTxSet(s: String) {
        _qrTvTx.value = s
    }

    private val _qrTvVis = MutableLiveData(false)
    val qrTvVis: LiveData<Boolean> = _qrTvVis

    //    private val _flashActive = MutableLiveData(false)
//    val flashActive: LiveData<Boolean>
//        get() = _flashActive
    private val _flashActive = MutableStateFlow(false)
    val flashActive: StateFlow<Boolean>
        get() = _flashActive

    fun flashOnOff() {
        _flashActive.tryEmit(!flashActive.value)

        //_flashActive.value = when (flashActive.value) {
        //    true -> false
        //    false -> true
        //null -> false
        //}
    }

    fun qrScnOnOff() {
        _qrScnActive.value = when (qrScnActive.value) {
            true -> false
            false -> true
            null -> false
        }
    }

    fun qrScnOff() {
        _qrScnActive.value = false
    }

    enum class PhotoAction {
        Non,
        GettingPhoto,
        GettingVideo
    }

    //    private val _photoAction = MutableLiveData(PhotoAction.Non)
//    val photoAction: LiveData<PhotoAction> = _photoAction
    val photoAction = MutableStateFlow(PhotoAction.Non)
    //private val _photoAction = MutableStateFlow(PhotoAction.Non)
    //val photoAction: StateFlow<PhotoAction> = _photoAction

    val takingAPhotoActionBoolean = MutableStateFlow(false)

    fun takeAPhotoAction() {
        if (photoAction.value == PhotoAction.Non) {
            photoAction.value = PhotoAction.GettingPhoto
            viewModelScope.launch(Dispatchers.IO) {
                // end take photo
                if (photoAction.value == PhotoAction.GettingPhoto) {
                    //photoAction.emit(PhotoAction.GettingPhoto)
                    //photoAction.value = PhotoAction.Non
                    //tost("We got a photo shot!")
                } else {
                    Log.e(
                        TAG,
                        "takeAPhotoAction: photoAction in takeAPhotoAction is not PhotoAction.getting_photo"
                    )
                    tost("ERROR! takeAPhotoAction: photoAction in takeAPhotoAction is not PhotoAction.getting_photo")
                }

            }

        }
    }

    fun takeAVideoAction(): Boolean {
        if (photoAction.value == PhotoAction.Non) {
            photoAction.value = PhotoAction.GettingVideo
            viewModelScope.launch(Dispatchers.IO) {
                delay(7000)

                // end take Video
                if (photoAction.value == PhotoAction.GettingVideo) {
                    photoAction.value = PhotoAction.Non
                    tost("We just made a video!")
                } else {
                    Log.e(
                        TAG,
                        "takeAVideoAction: photoAction in takeAVideoAction is not PhotoAction.getting_photo"
                    )
                    tost("ERROR! takeAVideoAction: photoAction in takeAVideoAction is not PhotoAction.getting_video")
                }
            }

        }
        return true
    }

    fun saveLocalUri(s: String) {
        Log.e(TAG, "saveLocalUri: $s")
        _dirUri.value = s
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                apl.dataStore.edit {
                    it[stringPreferencesKey(DIR_URI)] = s
                }
            }
        }
    }

    fun savePicSize(s: String) {
        Log.e(TAG, "savePicSize: $s")
        //picSize.value = Size.parseSize(s)
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                apl.dataStore.edit {
                    it[stringPreferencesKey(PIC_SIZE)] = s
                }
            }
        }
    }

    private val _dirUri = MutableStateFlow<String?>(null)
    val dirUri: StateFlow<String?> = _dirUri

    init {
        viewModelScope.launch(Dispatchers.IO) {
            val data = try {
                apl.dataStore.data.first()
            } catch (e: NoSuchElementException) {
                Log.e(TAG, e.stackTraceToString())
                tost(e.stackTraceToString())
                null
            }

            Log.i(
                TAG,
                "!!!!!!!!!!!!!!!!!!!!!!!!!!! ${data?.get(stringPreferencesKey(DIR_URI))} !!!!!!!!!!!!!!!!!!!!!!!!!: !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!"
            )
            try {
                _dirUri.value = data?.get(stringPreferencesKey(DIR_URI))
            } catch (e: ClassCastException) {
                Log.e(TAG, e.stackTraceToString())
                tost(e.stackTraceToString())
            }

            //get pic sizes
            try {
                data?.get(stringPreferencesKey(PIC_SIZE))?.let {
                    Size.parseSize(it)
                        ?.also {
                            picSize.value = it
                        }
                }
            } catch (e: Exception) {
                Log.e(TAG, e.stackTraceToString())
                tost(e.stackTraceToString())
            }

        }
    }

    suspend fun tost(s: String) {
        withContext(Dispatchers.Main) {
            Toast.makeText(apl.applicationContext, s, Toast.LENGTH_LONG).show()
        }
    }


    val sizes = MutableLiveData(arrayOf<Size>())
    val sizesStrings = MutableLiveData(arrayListOf<String>())

    /*val sizesStrings = Transformations.map(sizes){
        it.map {
          it.toString()
        }.toTypedArray()
    }*/
    val picSize = MutableStateFlow(Size(1280, 720))


}

@BindingAdapter("threePictures")
fun threePictures(imageButton: ImageButton, action: ViewModelMainActivity.PhotoAction) {
    val r = when (action) {
        ViewModelMainActivity.PhotoAction.Non -> R.drawable.ic_baseline_camera_alt_24
        ViewModelMainActivity.PhotoAction.GettingPhoto -> R.drawable.ic_baseline_photo_camera_back_24_red
        ViewModelMainActivity.PhotoAction.GettingVideo -> R.drawable.ic_baseline_video_camera_back_24_red
    }
    imageButton.setImageResource(r)
}
