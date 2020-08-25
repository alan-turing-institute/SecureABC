package com.example.secureabcverifier

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.ImageFormat.*
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Base64.decode
import android.util.Log
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.zxing.*
import com.google.zxing.common.HybridBinarizer
import kotlinx.android.synthetic.main.activity_main.*
import java.io.File
import java.nio.BufferUnderflowException
import java.nio.ByteBuffer
import java.nio.charset.Charset
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

private fun ByteBuffer.toByteArray(): ByteArray {
    rewind()
    val data = ByteArray(remaining())
    get(data)
    return data
}

class QRAnalyzer(private val onQrCodesDetected: (qrCode: Result) -> Unit) : ImageAnalysis.Analyzer {

    private val yuvFormats = mutableListOf(YUV_420_888)

    init {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            yuvFormats.addAll(listOf(YUV_422_888, YUV_444_888))
        }
    }

    private val reader = MultiFormatReader().apply {
        val map = mapOf(
                DecodeHintType.POSSIBLE_FORMATS to arrayListOf(BarcodeFormat.QR_CODE),
                DecodeHintType.TRY_HARDER to true, // Spend more time to try to find a barcode; optimise for accuracy, not speed.
                DecodeHintType.ALLOWED_LENGTHS to intArrayOf(25)
        )
        setHints(map)
    }

    override fun analyze(image: ImageProxy) {
        // We are using YUV format because, ImageProxy internally uses ImageReader to get the image
        // by default ImageReader uses YUV format unless changed.
        if (image.format !in yuvFormats) {
            Log.e("QRCodeAnalyzer", "Expected YUV, now = ${image.format}")
            return
        }

        val data = image.planes[0].buffer.toByteArray()

        val source = PlanarYUVLuminanceSource(
                data,
                image.width,
                image.height,
                0,
                0,
                image.width,
                image.height,
                false
        )

        val binaryBitmap = BinaryBitmap(HybridBinarizer(source))
        try {
            // Whenever reader fails to detect a QR code in image
            // it throws NotFoundException
            val result = reader.decode(binaryBitmap)
            onQrCodesDetected(result)
        } catch (e: NotFoundException) {
            // No need to do anything unless we DO find a QR code
            //e.printStackTrace()
        }
        image.close()
    }
}

const val SECUREABC_QR_DATA = "com.example.secureabcverifier.QRDATA"
const val SECUREABC_VERIFY_DATA = "com.example.secureabcverifier.VERIFYDATA"
const val SECUREABC_VERIFY_SIGNATURE = "com.example.secureabcverifier.VERIFYSIGNATURE"
const val SECUREABC_QR_PHOTO = "com.example.secureabcverifier.QRPHOTO"

class MainActivity : AppCompatActivity() {
    private var imageCapture: ImageCapture? = null
    private var isProcessing = false

    private lateinit var outputDirectory: File
    private lateinit var cameraExecutor: ExecutorService

    override fun onRequestPermissionsResult(
            requestCode: Int, permissions: Array<String>, grantResults:
            IntArray) {
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) {
                startCamera()
            } else {
                Toast.makeText(this,
                        "Permissions not granted by the user.",
                        Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Request camera permissions
        if (allPermissionsGranted()) {
            startCamera()
        } else {
            ActivityCompat.requestPermissions(
                    this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS)
        }

        outputDirectory = getOutputDirectory()

        cameraExecutor = Executors.newSingleThreadExecutor()
    }


    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener(Runnable {
            // Used to bind the lifecycle of cameras to the lifecycle owner
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

            // Preview
            val preview = Preview.Builder()
                    .build()
                    .also {
                        it.setSurfaceProvider(viewFinder.createSurfaceProvider())
                    }

            val imageAnalyzer = ImageAnalysis.Builder()
                    //.setTargetResolution(Size(previewView.width, previewView.height))
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                    .also {
                        it.setAnalyzer(cameraExecutor, QRAnalyzer { qrResult ->
                            if(!isProcessing) {
                                isProcessing = true

                                Log.d("QRCodeAnalyzer", "Barcode scanned (${qrResult.numBits} bits): ${qrResult.text}")

                                val messagelength = qrResult.numBits
                                if (messagelength >= 18672) {
                                    try {

                                        val qrData = decode(qrResult.text, android.util.Base64.DEFAULT)
                                        Log.d("QRCodeAnalyzer", "(${qrData.size} bytes QR payload..)")

                                        var readOffset = 0
                                        val imglen = ByteBuffer.wrap(qrData, readOffset, 2).getShort().toInt()
                                        readOffset += 2
                                        Log.d("QRCodeAnalyzer", "(imgLen = ${imglen} )")
                                        val imgbytes = qrData.copyOfRange(readOffset, readOffset+imglen)
                                        readOffset += imglen
                                        Log.d("QRCodeAnalyzer", "(readOffset = ${readOffset} )")
                                        val namelen = ByteBuffer.wrap(qrData, readOffset, 2).getShort().toInt()
                                        readOffset += 2
                                        Log.d("QRCodeAnalyzer", "(namelen = ${namelen} )")
                                        val username = String(qrData.copyOfRange(readOffset, readOffset+namelen), Charsets.UTF_8)
                                        readOffset+=namelen
                                        Log.d("QRCodeAnalyzer", "(name = ${username})")
                                        val datelen = ByteBuffer.wrap(qrData, readOffset, 2).getShort().toInt()
                                        readOffset += 2
                                        Log.d("QRCodeAnalyzer", "(datelen = ${datelen} )")
                                        val userdate = String(qrData.copyOfRange(readOffset, readOffset+datelen), Charsets.UTF_8)
                                        readOffset += datelen
                                        Log.d("QRCodeAnalyzer", "(date = ${userdate} )")
                                        val CIDlen = ByteBuffer.wrap(qrData, readOffset, 2).getShort().toInt()
                                        readOffset += 2
                                        Log.d("QRCodeAnalyzer", "(CIDlen = ${CIDlen} )")
                                        val userCID = String(qrData.copyOfRange(readOffset, readOffset+CIDlen), Charsets.UTF_8)
                                        readOffset += CIDlen
                                        Log.d("QRCodeAnalyzer", "(date = ${userCID} )")

                                        // Read signature
                                        val rawVerifyBytes = qrData.copyOfRange(0, readOffset)
                                        val certSigLen = ByteBuffer.wrap(qrData, readOffset, 2).getShort().toInt()
                                        readOffset += 2
                                        Log.d("QRCodeAnalyzer", "(cert sig length = ${certSigLen} )")
                                        val sigBytes = qrData.copyOfRange(readOffset, readOffset+certSigLen)


                                        val qrdata = arrayOf(username, userdate, userCID)
                                        val intent = Intent(this, VerifyActivity::class.java).apply {
                                            putExtra(SECUREABC_QR_PHOTO, imgbytes)
                                            putExtra(SECUREABC_QR_DATA, qrdata)
                                            putExtra(SECUREABC_VERIFY_DATA, rawVerifyBytes)
                                            putExtra(SECUREABC_VERIFY_SIGNATURE, sigBytes)
                                        }

                                        startActivity(intent)
                                    } catch(e: IllegalArgumentException) {
                                        // Bad base64 in input; try again
                                        isProcessing=false
                                    } catch(e: BufferUnderflowException) {
                                        // Read bad lengths from QR code and try to read past buffer
                                        isProcessing=false
                                    } catch(e: IndexOutOfBoundsException) {
                                        // Read too far
                                        isProcessing=false
                                    }
                                } else{
                                    isProcessing=false
                                }


                            }
                        })
                    }


            imageCapture = ImageCapture.Builder()
                    .build()

            // Select back camera as a default
            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                // Unbind use cases before rebinding
                cameraProvider.unbindAll()

                // Bind use cases to camera
                cameraProvider.bindToLifecycle(
                        this, cameraSelector, preview, imageCapture, imageAnalyzer)

            } catch (exc: Exception) {
                Log.e(TAG, "Use case binding failed", exc)
            }

        }, ContextCompat.getMainExecutor(this))
    }

    private fun takePhoto() {
        // Get a stable reference of the modifiable image capture use case
        val imageCapture = imageCapture ?: return

        // Create time-stamped output file to hold the image
        val photoFile = File(
                outputDirectory,
                SimpleDateFormat(FILENAME_FORMAT, Locale.US
                ).format(System.currentTimeMillis()) + ".jpg")

        // Create output options object which contains file + metadata
        val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

        // Set up image capture listener, which is triggered after photo has
        // been taken
        imageCapture.takePicture(
                outputOptions, ContextCompat.getMainExecutor(this), object : ImageCapture.OnImageSavedCallback {
            override fun onError(exc: ImageCaptureException) {
                Log.e(TAG, "Photo capture failed: ${exc.message}", exc)
            }

            override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                val savedUri = Uri.fromFile(photoFile)
                val msg = "Photo capture succeeded: $savedUri"
                Toast.makeText(baseContext, msg, Toast.LENGTH_SHORT).show()
                Log.d(TAG, msg)
            }
        })
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(
                baseContext, it) == PackageManager.PERMISSION_GRANTED
    }

    private fun getOutputDirectory(): File {
        val mediaDir = externalMediaDirs.firstOrNull()?.let {
            File(it, resources.getString(R.string.app_name)).apply { mkdirs() } }
        return if (mediaDir != null && mediaDir.exists())
            mediaDir else filesDir
    }

    override fun onResume() {
        super.onResume()
        isProcessing=false
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }

    companion object {
        private const val TAG = "CameraXBasic"
        private const val FILENAME_FORMAT = "yyyy-MM-dd-HH-mm-ss-SSS"
        private const val REQUEST_CODE_PERMISSIONS = 10
        private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA)
    }
}
