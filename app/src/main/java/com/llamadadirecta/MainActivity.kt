package com.llamadadirecta

import android.Manifest
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

class MainActivity : AppCompatActivity() {
    private var isCalling = false
    private var audioRecord: AudioRecord? = null
    private var audioTrack: AudioTrack? = null
    private var sendSocket: DatagramSocket? = null
    private var recvSocket: DatagramSocket? = null
    private val sampleRate = 16000
    private val portSend = 50005
    private val portRecv = 50006 // invertido en el otro cel

    private lateinit var statusText: TextView
    private lateinit var btnCall: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        
        statusText = findViewById(R.id.statusText)
        btnCall = findViewById(R.id.btnCall)
        val btnSwapPort: Button = findViewById(R.id.btnSwapPort)
        val ipInput: TextView = findViewById(R.id.ipInput)

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.ACCESS_FINE_LOCATION), 1)
        }

        statusText.text = "1. Un celular activa Zona WiFi
2. El otro se conecta
3. Escribe IP: 192.168.43.1
4. Dale a LLAMAR"

        btnSwapPort.setOnClickListener {
            // El celular que crea hotspot usa 50005->50006, el cliente al revés
            Toast.makeText(this, "Si eres el del Hotspot, deja IP 192.168.43.1 y puerto 50005. Si eres cliente, deja IP del otro y puerto 50006", Toast.LENGTH_LONG).show()
        }

        btnCall.setOnClickListener {
            if (!isCalling) {
                val targetIpStr = ipInput.text.toString().ifEmpty { "192.168.43.1" }
                startCall(targetIpStr)
            } else {
                stopCall()
            }
        }
    }

    fun startCall(targetIpStr: String) {
        isCalling = true
        btnCall.text = "COLGAR"
        statusText.text = "En llamada con $targetIpStr..."

        val bufferSize = AudioRecord.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        
        // --- RECEIVER THREAD ---
        Thread {
            try {
                recvSocket = DatagramSocket(portRecv)
                audioTrack = AudioTrack(
                    AudioManager.STREAM_VOICE_CALL, sampleRate,
                    AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT,
                    bufferSize, AudioTrack.MODE_STREAM
                )
                audioTrack?.play()
                val buf = ByteArray(1024)
                while (isCalling) {
                    val packet = DatagramPacket(buf, buf.size)
                    recvSocket?.receive(packet)
                    audioTrack?.write(packet.data, 0, packet.length)
                }
            } catch (e: Exception) { e.printStackTrace() }
        }.start()

        // --- SENDER THREAD ---
        Thread {
            try {
                if (ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) return@Thread
                audioRecord = AudioRecord(
                    MediaRecorder.AudioSource.MIC, sampleRate,
                    AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, bufferSize
                )
                sendSocket = DatagramSocket()
                val targetIp = InetAddress.getByName(targetIpStr)
                audioRecord?.startRecording()
                val buf = ByteArray(1024)
                while (isCalling) {
                    val read = audioRecord?.read(buf, 0, buf.size) ?: 0
                    if (read > 0) {
                        val packet = DatagramPacket(buf, read, targetIp, portSend)
                        sendSocket?.send(packet)
                    }
                }
            } catch (e: Exception) { e.printStackTrace() }
        }.start()
    }

    fun stopCall() {
        isCalling = false
        btnCall.text = "LLAMAR"
        statusText.text = "Llamada terminada"
        audioRecord?.stop(); audioRecord?.release()
        audioTrack?.stop(); audioTrack?.release()
        sendSocket?.close(); recvSocket?.close()
    }

    override fun onDestroy() {
        super.onDestroy()
        stopCall()
    }
}
