package com.example.secureabcverifier

import android.graphics.BitmapFactory
import android.graphics.Color
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.widget.ImageView
import android.widget.TextView
import java.io.ByteArrayInputStream
import java.io.FileInputStream
import java.security.InvalidKeyException
import java.security.KeyStore
import java.security.NoSuchAlgorithmException

import java.security.Signature
import java.security.cert.Certificate
import java.security.cert.CertificateFactory
import java.util.*
import javax.net.ssl.TrustManagerFactory

class VerifyActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_verify)

        var certStatus = false

        // Get the Intent that started this activity and extract the string (b64 data)
        val qrdata  = intent.getStringArrayExtra(SECUREABC_QR_DATA)
        val verifydata = intent.getByteArrayExtra(SECUREABC_VERIFY_DATA)
        val verifySig = intent.getByteArrayExtra(SECUREABC_VERIFY_SIGNATURE)
        val qrphoto = intent.getByteArrayExtra(SECUREABC_QR_PHOTO)

        // Load certificate for verification
        val certFactory = CertificateFactory.getInstance("X.509")
        val certStream =  resources.openRawResource(R.raw.sign_cert)
        val cert = certFactory.generateCertificate(certStream)
        Log.d("QRCodeAnalyzer", "certificate: ${cert.publicKey}")

        // Verify signature
        try {
            val signature = Signature.getInstance("SHA256withECDSA")
            signature.initVerify(cert)
            val benchmark = Date()
            signature.update(verifydata)
            certStatus = signature.verify(verifySig)
            val duration = (Date().time - benchmark.time)
            Log.d("QRCodeAnalyzer", "Time to verify: ${duration} ms")

        } catch (e: InvalidKeyException) {
            Log.d("QRCodeAnalyzer", "Error loading certificate: ${e}")
        } catch (e: NoSuchAlgorithmException) {
            Log.d("QRCodeAnalyzer", "Error verifying signature: ${e}")
        }

        // Set photo
        var photoStream = ByteArrayInputStream(qrphoto)
        val imageView = findViewById<ImageView>(R.id.imageView).apply {
            setImageBitmap(BitmapFactory.decodeStream(photoStream))
        }

        // Capture the layout's TextView's and set the strings using QR data payload
        val nameView = findViewById<TextView>(R.id.nameView).apply {
            text = "Name: " + qrdata[0] //Integer.toString(user_name_length)
        }
        val dateView = findViewById<TextView>(R.id.dateView).apply {
            text = "Validity: " + qrdata[1] //Integer.toString(user_name_length)
        }
        val cidView = findViewById<TextView>(R.id.cidView).apply {
            text = "CID: " + qrdata[2] //Integer.toString(user_name_length)
        }
        val validView = findViewById<TextView>(R.id.validView).apply {
            if (certStatus) {
                setTextColor(Color.GREEN)
                text = "Certificate: Valid"
            } else {
                setTextColor(Color.RED)
                text = "Certificate: NOT VALID"
            }

        }

    }
}