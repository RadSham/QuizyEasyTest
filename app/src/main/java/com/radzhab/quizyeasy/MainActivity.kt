package com.radzhab.quizyeasy

import android.os.Bundle
import android.os.CountDownTimer
import android.util.Log
import android.view.View
import android.webkit.CookieManager
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentTransaction
import com.google.firebase.ktx.Firebase
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.ktx.remoteConfig
import com.google.firebase.remoteconfig.ktx.remoteConfigSettings
import com.radzhab.quizyeasy.databinding.ActivityMainBinding
import com.radzhab.quizyeasy.fragment.ResultFragment
import com.radzhab.quizyeasy.model.QuestionModel
import java.util.*
import java.util.concurrent.TimeUnit


class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    private lateinit var webView: WebView

    var questionsList: ArrayList<QuestionModel> = ArrayList()
    private var index: Int = 0
    lateinit var questionModel: QuestionModel

    private var correctAnswerCount: Int = 0
    private var wrongAnswerCount: Int = 0
    private var url = ""

    private var backPressedTime: Long = 0
    private var backToast: Toast? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        val view = binding.root
        setContentView(view)
        supportActionBar?.hide()
        getConfig(savedInstanceState)
        //TODO: check is local url exist
    }

    override fun onSaveInstanceState(outState: Bundle) {
        webView.saveState(outState)
        super.onSaveInstanceState(outState)
    }

    private fun getConfig(savedInstanceState: Bundle?) {
        val remoteConfig: FirebaseRemoteConfig = Firebase.remoteConfig
        val configSettings = remoteConfigSettings {
            minimumFetchIntervalInSeconds = 3600
        }
        remoteConfig.setConfigSettingsAsync(configSettings)

        //Default url value
//        remoteConfig.setDefaultsAsync(R.xml.remote_config_defaults)

        try {
            remoteConfig.fetchAndActivate()
                .addOnCompleteListener(this) { task ->
                    if (task.isSuccessful) {
                        val updated = task.result
                        Log.d(TAG, "Config params updated: $updated")
                        Toast.makeText(
                            this,
                            "Fetch and activate succeeded",
                            Toast.LENGTH_SHORT,
                        ).show()
                        url = remoteConfig.getString("url")
                    } else {
                        Toast.makeText(
                            this,
                            "Fetch failed",
                            Toast.LENGTH_SHORT,
                        ).show()
                    }
                    //TODO: check is Android or Emulator
                    if (url.isEmpty()) {
                        println("STARTPLUG")
                        startPlug()
                    } else{
                        startWeb(url, savedInstanceState)
                    }
                    displayWelcomeMessage()
                }
        } catch (e:Exception){
            //TODO: display the screen with the output - a network connection is required to continue
            e.printStackTrace()
        }
    }

    private fun displayWelcomeMessage() {
        println("displayWelcomeMessage")
    }

    private fun startWeb(url: String, savedInstanceState: Bundle?) {
        binding.mainWebView.visibility = View.VISIBLE
        webView = binding.mainWebView
        webView.webViewClient = WebViewClient()
        val webSettings:WebSettings = webView.settings
        webSettings.setJavaScriptEnabled(true)
        if (savedInstanceState != null)
            webView.restoreState(savedInstanceState)
        else
            webView.loadUrl(url)
        binding.mainWebView.settings.domStorageEnabled = true
        binding.mainWebView.settings.javaScriptCanOpenWindowsAutomatically = true
        val cookieManager = CookieManager.getInstance()
        cookieManager.setAcceptCookie(true)
        val mWebSettings = binding.mainWebView.settings
        mWebSettings.javaScriptEnabled = true
        mWebSettings.loadWithOverviewMode = true
        mWebSettings.useWideViewPort = true
        mWebSettings.domStorageEnabled = true
        mWebSettings.databaseEnabled = true
        mWebSettings.setSupportZoom(false)
        mWebSettings.allowFileAccess = true
        mWebSettings.allowContentAccess = true
        mWebSettings.loadWithOverviewMode = true
        mWebSettings.useWideViewPort = true
    }


    private fun startPlug(){
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

        object : CountDownTimer(duration, 1000) {
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
        }.start()
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
            if (questionModel.option1 == questionModel.answer) {
                correctAns(option1)
            } else {
                wrongAns(option1)
            }
        }

        option2.setOnClickListener {
            disableButton()
            if (questionModel.option2 == questionModel.answer) {
                correctAns(option2)
            } else {
                wrongAns(option2)
            }
        }

        option3.setOnClickListener {
            disableButton()
            if (questionModel.option3 == questionModel.answer) {
                correctAns(option3)
            } else {
                wrongAns(option3)
            }
        }

        option4.setOnClickListener {
            disableButton()
            if (questionModel.option4 == questionModel.answer) {
                correctAns(option4)
            } else {
                wrongAns(option4)
            }
        }
    }

    // TODO: configure for WebView
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

    companion object {
        private const val TAG = "MyLog"
    }

}