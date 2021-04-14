package dev.m13d.netologyvoiceassistant

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.wolfram.alpha.WAEngine
import com.wolfram.alpha.WAPlainText
import dev.m13d.netologyvoiceassistant.databinding.ActivityMainBinding
import kotlinx.coroutines.*
import java.util.*
import kotlin.collections.ArrayList

@SuppressLint("StaticFieldLeak")
private lateinit var binding: ActivityMainBinding

val key = "your_api_key"

class MainActivity : AppCompatActivity() {

    private lateinit var textToSpeech: TextToSpeech
    var speechRequest = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.topAppBar)

        binding.searchButton.setOnClickListener {

            CoroutineScope(Dispatchers.IO).launch {
                askWolfram(binding.questionInput.text.toString())
            }
        }

        binding.speakButton.setOnClickListener {
            textToSpeech.stop()
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            intent.putExtra(
                    RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                    RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
            )
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.US)
            intent.putExtra(RecognizerIntent.EXTRA_PROMPT, "What do you want to know?")
            try {
                startActivityForResult(intent, 666)
            } catch (a: ActivityNotFoundException) {
                Toast.makeText(
                        applicationContext,
                        "Sorry your device not supported",
                        Toast.LENGTH_SHORT
                ).show()
            }
        }

        textToSpeech = TextToSpeech(this, TextToSpeech.OnInitListener {})
        textToSpeech.language = Locale.US

    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 666) {
            if (resultCode == RESULT_OK && data != null) {
                val result: ArrayList<String>? = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                val question: String? = result?.get(0)

                if (question != null) {
                    binding.questionInput.setText(question)

                    CoroutineScope(Dispatchers.Main).launch {
                        async(Dispatchers.IO) { askWolfram(question) }.await()
                    }
                }
            }
        }
    }

    private suspend fun askWolfram(question: String) {
        val engine = WAEngine()
        engine.appID = key
        engine.addFormat("plaintext")

        val query = engine.createQuery()
        var answer = ""
        query.input = question

        val queryResult = engine.performQuery(query)

        when {
            queryResult.isError -> answer = queryResult.errorMessage
            !queryResult.isSuccess -> answer = "Sorry, I don't understand.\nCan you paraphrase your question?"
            else -> {
                for (pod in queryResult.pods) {
                    if (!pod.isError) {
                        for (subpod in pod.subpods) {
                            for (element in subpod.contents) {
                                if (element is WAPlainText) {
                                    answer += element.text
                                }
                            }
                            answer += "\n"
                        }
                    }
                }
            }
        }

        setTextOnMainThread(answer)
    }

    private suspend fun setTextOnMainThread(text: String) {
        withContext(Dispatchers.Main) {
            binding.answerOutput.text = text
        }

        textToSpeech.speak(text, TextToSpeech.QUEUE_ADD, null, speechRequest.toString())
        speechRequest += 1
    }
}