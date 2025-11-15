package com.k2fsa.sherpa.onnx

import android.Manifest
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Bundle
import android.text.TextUtils
import android.widget.ImageView
import androidx.core.app.ActivityCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.k2fsa.sherpa.onnx.databinding.ActivityMainBinding
import kotlin.concurrent.thread

private const val REQUEST_RECORD_AUDIO_PERMISSION = 200

class MainActivity : BaseActivity() {
    private val permissions: Array<String> = arrayOf(Manifest.permission.RECORD_AUDIO)

    private lateinit var recognizer: OnlineRecognizer
    private var audioRecord: AudioRecord? = null
    private var recordingThread: Thread? = null

    private val audioSource = MediaRecorder.AudioSource.MIC
    private val sampleRateInHz = 16000
    private val channelConfig = AudioFormat.CHANNEL_IN_MONO

    private val audioFormat = AudioFormat.ENCODING_PCM_16BIT

    @Volatile
    private var isRecording: Boolean = false
    private lateinit var messageList: RecyclerView
    private lateinit var dialImage: ImageView
    private lateinit var binding: ActivityMainBinding
    private lateinit var messageAdapter: MessageAdapter
    private var currentMessage = ""
    private var isNewLine = true

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        val permissionToRecordAccepted = if (requestCode == REQUEST_RECORD_AUDIO_PERMISSION) {
            grantResults[0] == PackageManager.PERMISSION_GRANTED
        } else {
            false
        }

        if (!permissionToRecordAccepted) {
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        initViews()
        ActivityCompat.requestPermissions(this, permissions, REQUEST_RECORD_AUDIO_PERMISSION)
        initModel()
    }

    override fun onDestroy() {
        super.onDestroy()
        reset()
    }

    private fun initViews() {
        messageList = binding.listMessages
        dialImage = binding.btnDial
        dialImage.setOnClickListener { dial() }
        messageList = binding.listMessages
        val layoutManager = LinearLayoutManager(this)
        messageList.layoutManager = layoutManager
        messageAdapter = MessageAdapter(applicationContext)
        messageList.adapter = messageAdapter
    }

    private fun dial() {
        if (!isRecording) {
            val ret = initMicrophone()
            if (!ret) {
                return
            }
            isNewLine = true
            currentMessage = ""
            audioRecord!!.startRecording()
            isRecording = true
            recordingThread = thread(true) {
                processSamples()
            }
            dialImage.setImageResource(R.mipmap.hangup)
        } else {
            dialImage.setImageResource(R.mipmap.dial)
            reset()
        }
    }

    private fun reset() {
        isRecording = false
        audioRecord!!.stop()
        audioRecord!!.release()
        audioRecord = null
    }

    private fun processSamples() {
        val stream = recognizer.createStream()

        val interval = 0.1 // i.e., 100 ms
        val bufferSize = (interval * sampleRateInHz).toInt() // in samples
        val buffer = ShortArray(bufferSize)

        while (isRecording) {
            val ret = audioRecord?.read(buffer, 0, buffer.size)
            if (ret != null && ret > 0) {
                val samples = FloatArray(ret) { buffer[it] / 32768.0f }
                stream.acceptWaveform(samples, sampleRate = sampleRateInHz)
                while (recognizer.isReady(stream)) {
                    recognizer.decode(stream)
                }

                val isEndpoint = recognizer.isEndpoint(stream)
                var text = recognizer.getResult(stream).text

                // For streaming parformer, we need to manually add some
                // paddings so that it has enough right context to
                // recognize the last word of this segment
                if (isEndpoint && recognizer.config.modelConfig.paraformer.encoder.isNotBlank()) {
                    val tailPaddings = FloatArray((0.8 * sampleRateInHz).toInt())
                    stream.acceptWaveform(tailPaddings, sampleRate = sampleRateInHz)
                    while (recognizer.isReady(stream)) {
                        recognizer.decode(stream)
                    }
                    text = recognizer.getResult(stream).text
                }
                if (isEndpoint) {
                    recognizer.reset(stream)
                    runOnUiThread {
                        if (text.isNotEmpty()) {
                            if (currentMessage != text) {
                                currentMessage = text
                                messageAdapter.replaceLatestItem(currentMessage)
                            }
                        }
                        isNewLine = true
                        currentMessage = ""
                    }
                } else {
                    runOnUiThread {
                        if (text.isNotEmpty()) {
                            if (isNewLine) {
                                currentMessage = text
                                messageAdapter.addNewItem(currentMessage)
                                messageList.smoothScrollToPosition(messageAdapter.itemCount - 1)
                            } else {
                                if (currentMessage != text) {
                                    currentMessage = text
                                    messageAdapter.replaceLatestItem(currentMessage)
                                }
                            }
                            isNewLine = false
                        }
                    }
                }
            }
        }
        stream.release()
    }


    private fun initMicrophone(): Boolean {
        if (ActivityCompat.checkSelfPermission(
                this, Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(this, permissions, REQUEST_RECORD_AUDIO_PERMISSION)
            return false
        }

        val numBytes = AudioRecord.getMinBufferSize(sampleRateInHz, channelConfig, audioFormat)

        audioRecord = AudioRecord(
            audioSource,
            sampleRateInHz,
            channelConfig,
            audioFormat,
            numBytes * 2 // a sample has two bytes as we are using 16-bit PCM
        )
        return true
    }

    private fun initModel() {
        // Please change getModelConfig() to add new models
        // See https://k2-fsa.github.io/sherpa/onnx/pretrained_models/index.html
        // for a list of available models
        val type = 0
        var ruleFsts: String?
        ruleFsts = null

        val useHr = false
        val hr = HomophoneReplacerConfig(
            // Used only when useHr is true
            // Please download the following 3 files from
            // https://github.com/k2-fsa/sherpa-onnx/releases/tag/hr-files
            //
            // dict and lexicon.txt can be shared by different apps
            //
            // replace.fst is specific for an app
            lexicon = "lexicon.txt",
            ruleFsts = "replace.fst",
        )

        var config = OnlineRecognizerConfig(
            featConfig = getFeatureConfig(sampleRate = sampleRateInHz, featureDim = 80),
            modelConfig = getModelConfig(type = type)!!,
            // lmConfig = getOnlineLMConfig(type = type),
            endpointConfig = getEndpointConfig(),
            enableEndpoint = true,
        )

        if (ruleFsts != null) {
            config.ruleFsts = ruleFsts
        }

        if (useHr) {
            config.hr = hr
        }

        recognizer = OnlineRecognizer(
            assetManager = application.assets,
            config = config,
        )
    }
}
