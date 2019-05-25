package com.example.myapplication

//import androidx.work.Worker
//import androidx.work.WorkerParameters
import android.annotation.SuppressLint
import android.app.KeyguardManager
import android.content.ComponentCallbacks2
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.os.Environment
import android.support.v7.app.AppCompatActivity
import android.util.Log
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.ToggleButton
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import kotlin.collections.ArrayList
import kotlin.math.abs
import kotlin.math.max
import kotlin.properties.Delegates


const val EXTRA_MESSAGE = "com.example.myfirstapp.MESSAGE"

data class Measurement(val time: String, val acceleration: List<Float>, val isScreenUnlocked: Boolean)

class MainActivity : AppCompatActivity(), SensorEventListener, WakeLockListener {


    private var mManager: SensorManager by Delegates.notNull()
    private var mSensor: Sensor by Delegates.notNull()
    private var screenOnFlag: Boolean = true
    private var maximumValueInPast: Float = 0.0F
    var cnt: Int = 1

    // xyzの過去の値を保持する


    private var accelerationX = 0f
    private var accelerationY = 0f
    private var accelerationZ = 0f
    private var nowMeasurement = Measurement(
        "19700101235959.999",
        listOf(accelerationX, accelerationY, accelerationZ),
        true
    )

    private var prevValues: ArrayList<Measurement> = arrayListOf(nowMeasurement)

    private var start: Long = System.currentTimeMillis()
    private var filePath = "/testfile.txt"


    override fun onScreenOff() {
        val loggingTextView = findViewById<TextView>(R.id.loggingTextView)
        loggingTextView.text = (loggingTextView.text.toString() + "\n" + getToday() + " Screen Off.")
        screenOnFlag = false
    }

    override fun onScreenOn() {
        val loggingTextView = findViewById<TextView>(R.id.loggingTextView)
        loggingTextView.text = (loggingTextView.text.toString() + "\n" + getToday() + " Screen On.")
        screenOnFlag = true
    }

    // 起動時の処理
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        filePath = Environment.getExternalStorageDirectory().path + filePath
        //センサーマネージャーを取得する
        mManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        //加速度計のセンサーを取得する
        //その他のセンサーを取得する場合には引数を違うものに変更する
        mSensor = mManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

        findViewById<TextView>(R.id.loggingTextView).text = ""
        val wakeLockBroadcastReceiver = WakeLockBroadcastReceiver(this)
        registerReceiver(wakeLockBroadcastReceiver, IntentFilter(Intent.ACTION_SCREEN_ON))
        registerReceiver(wakeLockBroadcastReceiver, IntentFilter(Intent.ACTION_SCREEN_OFF))

    }

    private fun Boolean.toInt() = if (this) 1 else 0

    private fun dumpToFile() {
        var string = ""
        for (i in 0 until prevValues.size - 10) {
            string += "%s\t%.6f\t%.6f\t%.6f\t%d\n".format(
                prevValues[i].time,
                prevValues[i].acceleration[0],
                prevValues[i].acceleration[1],
                prevValues[i].acceleration[2],
                prevValues[i].isScreenUnlocked.toInt()
            )
        }
        fileSave(string)
    }

    /* Checks if external storage is available for read and write */
    private fun isExternalStorageWritable(): Boolean {
        return Environment.getExternalStorageState() == Environment.MEDIA_MOUNTED
    }

    /* Checks if external storage is available to at least read */
    fun isExternalStorageReadable(): Boolean {
        return Environment.getExternalStorageState() in
                setOf(Environment.MEDIA_MOUNTED, Environment.MEDIA_MOUNTED_READ_ONLY)
    }

    private fun getPublicDocumentStorageDir(dirName: String): File? {
        // Get the directory for the user's public pictures directory.
        val file = File(
            Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_DOCUMENTS
            ), dirName
        )
        if (!file.mkdirs()) {
            Log.e("WARN", "Directory not created")
        }
        return file
    }


    private fun fileSave(content: String) {
        //var content = prevValues.toString()
        val filename = "output.txt"

        val context = applicationContext

        if (isExternalStorageWritable()) {
            val dir = getPublicDocumentStorageDir("Accelometer")
            val file = File(dir, filename)
            if (file.exists()) {
                file.appendBytes(content.toByteArray())
                Log.d("Append", context.filesDir.toString())
            } else {
                file.writeBytes(content.toByteArray())
                Log.d("Write", context.filesDir.toString())
            }

        } else {
            Log.d("Write", "Failed")
        }
    }


    private fun generateAccelerationString(acc: Float): String {
        return when (acc > 0) {
            true -> "+".repeat(abs(acc.toInt())) + "|" + "+".repeat(abs(acc.toInt()))
            false -> "-".repeat(abs(acc.toInt())) + "|" + "-".repeat(abs(acc.toInt()))

        }
    }

    private fun getToday(): String {
        val date = Date()
        val format = SimpleDateFormat("yyyyMMddHHmmss.SSS", Locale.JAPAN)
        format.timeZone = TimeZone.getTimeZone("Asia/Tokyo")
        return format.format(date)
    }


    private fun isDeviceLocked(): Boolean {
        val keyguardManager = applicationContext.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager //api 23+
        return keyguardManager.isDeviceLocked
    }

    //センサーに何かしらのイベントが発生したときに呼ばれる
    @SuppressLint("SetTextI18n")
    override fun onSensorChanged(event: SensorEvent?) {

        //Log.d("WARN",UserManagerCompat.isUserUnlocked(applicationContext).toString())


        //3つの値が配列で入ってくる
        accelerationX = event?.values!![0]
        accelerationY = event.values[1]
        accelerationZ = event.values[2]
        nowMeasurement = Measurement(
            getToday(),
            listOf(accelerationX, accelerationY, accelerationZ),
            !isDeviceLocked() && screenOnFlag
        )
        prevValues.add(nowMeasurement)

        if (findViewById<ToggleButton>(R.id.toggleButton).isChecked) {
            cnt++
            // 経過時間の更新
            findViewById<TextView>(R.id.elapsed_time).apply {
                text = ((System.currentTimeMillis() - start) / 1000).toInt().toString() + " sec"
            }
        }


        //今回のプロレスバーで使用されるxyz
        val valSize = prevValues.size
        val indices = max(0, valSize - 10) until prevValues.size


        // 過去10回の平均を求める
        var thisX = 0f;
        var thisY = 0f;
        var thisZ = 0f
        prevValues.slice(indices).forEach {
            thisX += it.acceleration[0] / indices.count()
            thisY += it.acceleration[1] / indices.count()
            thisZ += it.acceleration[2] / indices.count()
        }


        // 加速度の表示の更新
        run {
            // 加速度の数値表示
            findViewById<TextView>(R.id.value_acc_x).text = accelerationX.toString()
            findViewById<TextView>(R.id.value_acc_y).text = accelerationY.toString()
            findViewById<TextView>(R.id.value_acc_z).text = accelerationZ.toString()

            // 加速度のビジュアル表示
            findViewById<TextView>(R.id.text_meter_x).text = generateAccelerationString(accelerationX)
            findViewById<TextView>(R.id.text_meter_y).text = generateAccelerationString(accelerationY)
            findViewById<TextView>(R.id.text_meter_z).text = generateAccelerationString(accelerationZ)
        }

        // シークバーの更新
        run {
            //ゲージの最大値の確定．グラフ描画ができないので，やむなく．
            maximumValueInPast = arrayOf(
                abs(thisX), abs(thisY), abs(thisZ)
            ).max()!!

            val barMax = (maximumValueInPast * 200).toInt()
            findViewById<ProgressBar>(R.id.seekBarX).apply {
                max = barMax
                progress = (((thisX) + maximumValueInPast) * 100).toInt()
            }

            findViewById<ProgressBar>(R.id.seekBarY).apply {
                max = barMax
                progress = (((thisY) + maximumValueInPast) * 100).toInt()
            }

            findViewById<ProgressBar>(R.id.seekBarZ).apply {
                max = barMax
                progress = (((thisZ) + maximumValueInPast) * 100).toInt()
            }
        }

        if (prevValues.size % 20 == 0) {
            Log.d("noe Measurement", nowMeasurement.toString())
        }

        if (prevValues.size > 1010) {
            dumpToFile()
            prevValues = ArrayList(prevValues.slice(indices))
        }

    }


    fun toggleRecordingStatus(view: View) {
        val status = findViewById<ToggleButton>(R.id.toggleButton).isChecked
        if (status) {
            findViewById<ProgressBar>(R.id.progressBar9).visibility = View.VISIBLE
            start = System.currentTimeMillis()
        } else {
            findViewById<ProgressBar>(R.id.progressBar9).visibility = View.INVISIBLE
        }
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level == ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN) {
            // do something
        }
    }

    //センサー精度が変更されたときに発生するイベント
    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
    }

    //アクティビティが閉じられたときにリスナーを解除する
    override fun onPause() {
        super.onPause()
        //リスナーを解除しないとバックグラウンドにいるとき常にコールバックされ続ける
        val status = findViewById<ToggleButton>(R.id.toggleButton).isChecked
        if (!status) {
            mManager.unregisterListener(this)
        }
    }


    override fun onResume() {
        super.onResume()
        //リスナーとセンサーオブジェクトを渡す
        //第一引数はインターフェースを継承したクラス、今回はthis
        //第二引数は取得したセンサーオブジェクト
        //第三引数は更新頻度 UIはUI表示向き、FASTはできるだけ早く、GAMEはゲーム向き
        val status = findViewById<ToggleButton>(R.id.toggleButton).isChecked
        if (!status) {
            mManager.registerListener(this, mSensor, SensorManager.SENSOR_DELAY_UI)
        }
    }
}
