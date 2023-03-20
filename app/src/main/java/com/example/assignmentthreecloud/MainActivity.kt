package com.example.assignmentthreecloud

import android.app.ProgressDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.assignmentthreecloud.databinding.ActivityMainBinding
import com.google.android.gms.tasks.Task
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.google.firebase.storage.FirebaseStorage
import java.io.FileOutputStream

class MainActivity : AppCompatActivity() {

    private lateinit var progressDialog: ProgressDialog
    private lateinit var binding: ActivityMainBinding
    private var pdfUri: Uri? = null
    private val TAG = "PDF_ADD_TAG"
    private var bookUrl = ""
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
//        setContentView(R.layout.activity_main)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        progressDialog = ProgressDialog(this)
        progressDialog.setTitle("Please Wait...")
        progressDialog.setCanceledOnTouchOutside(false)

        val ref = FirebaseDatabase.getInstance().getReference("PDF")
        ref.addListenerForSingleValueEvent(object : ValueEventListener{
            override fun onDataChange(snapshot: DataSnapshot) {
                bookUrl = "${snapshot.child("url").value}"
            }
            override fun onCancelled(error: DatabaseError) {
            }
        })
        binding.uploadPdf.setOnClickListener {
            pdfPickIntent()
        }

        binding.downloadPdf.setOnClickListener {
            if (ContextCompat.checkSelfPermission(
                    this,
                    android.Manifest.permission.WRITE_EXTERNAL_STORAGE
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                Log.d(TAG, "onCreate: Storage permission is already granted")
                downloadPDf()
            } else {
                Log.d(TAG, "onCreate: Storage permission was not granted, LETS Request it")
                requestStoragePermissionLauncher.launch(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
        }


    }

    private fun pdfPickIntent() {
        Log.d(TAG, "pdfPickIntent: Starting pdf ick intent")

        val intent = Intent()
        intent.type = "application/pdf"
        intent.action = Intent.ACTION_GET_CONTENT
        pdfActivityResultLauncher.launch(intent)
    }

    val pdfActivityResultLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
        ActivityResultCallback<ActivityResult> { result ->
            if (result.resultCode == RESULT_OK) {

                Log.d(TAG, "DF Picked: ")
                pdfUri = result.data!!.data
                uploadPdfToStorage()
            } else {

                Log.d(TAG, "PDF Pick cancelled : ")
                Toast.makeText(this@MainActivity, "Cancelled", Toast.LENGTH_SHORT).show()
            }
        }
    )

    private fun uploadPdfToStorage() {
        Log.d(TAG, "uploadPdfToStorage: uploading to storage ")
        progressDialog.setMessage("Uploading PDF...")
        progressDialog.show()
        val timestamp = System.currentTimeMillis()
        val filePathAndName = "PDF/$timestamp"
        val storageReference = FirebaseStorage.getInstance().getReference(filePathAndName)
        storageReference.putFile(pdfUri!!)
            .addOnSuccessListener { taskSnapshot ->

                Log.d(TAG, "uploadPdfToStorage:  PDF uploaded now getting url")
                val uriTask: Task<Uri> = taskSnapshot.storage.downloadUrl
                while (!uriTask.isSuccessful);
                val uploadedPdfUri = "${uriTask.result}"
                val hashMap: HashMap<String, Any> = HashMap()
                hashMap["url"] = "$uploadedPdfUri"
                val ref = FirebaseDatabase.getInstance().getReference("PDF")
                ref.setValue(hashMap)
                    .addOnSuccessListener {
                        Log.d(TAG, "uploadPdfInfoToDB: Uploaded to db")
                        progressDialog.dismiss()
                        Toast.makeText(this@MainActivity, "Uploaded...", Toast.LENGTH_SHORT).show()
                    }
                    .addOnFailureListener { e ->
                        Log.d(TAG, "uploadPdfToStorage: Failed to upload due  to ${e.message}")
                        progressDialog.dismiss()
                        Toast.makeText(
                            this@MainActivity,
                            "Failed to upload due  to ${e.message}",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                progressDialog.dismiss()
                Toast.makeText(this@MainActivity, "Uploaded PDF Successfully", Toast.LENGTH_SHORT)
                    .show()
            }
            .addOnFailureListener { e ->

                Log.d(TAG, "uploadPdfToStorage: Failed to upload due  to ${e.message}")
                progressDialog.dismiss()
                Toast.makeText(
                    this@MainActivity,
                    "Failed to upload due  to ${e.message}",
                    Toast.LENGTH_SHORT
                ).show()
            }
    }

    private val requestStoragePermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->

            if (isGranted) {

                Log.d(TAG, "onCreate: Storage permission is  granted")
                downloadPDf()
            } else {

                Log.d(TAG, "onCreate: Storage permission  is denied")
                Toast.makeText(this, "Permission denied", Toast.LENGTH_SHORT).show()
            }
        }

    private fun downloadPDf() {

        Log.d(TAG, "downloadBook: DownLoading Book")
        progressDialog.setMessage("DownLoading Book")
        progressDialog.show()
        val storageReference = FirebaseStorage.getInstance().getReferenceFromUrl(bookUrl)
        storageReference.getBytes(50000000)
            .addOnSuccessListener { bytes ->

                Log.d(TAG, "downloadBook: Book downloaded...")
                saveToDownloadFolder(bytes)
            }
            .addOnFailureListener { e ->

                progressDialog.dismiss()
                Log.d(TAG, "downloadBook: Failed to download book due to ${e.message}")
                Toast.makeText(
                    this,
                    " Failed to download book due to ${e.message}",
                    Toast.LENGTH_SHORT
                ).show()
            }
    }

    private fun saveToDownloadFolder(bytes: ByteArray?) {
        Log.d(TAG, "saveToDownloadFolder: saving download book")
        val nameWithExtention = "${System.currentTimeMillis()}.pdf"

        try {
            val downloadsFolder =
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            downloadsFolder.mkdirs()
            val filePath = downloadsFolder.path + "/" + nameWithExtention
            val out = FileOutputStream(filePath)
            out.write(bytes)
            out.close()
            Toast.makeText(this, " saved to Downloads Folder", Toast.LENGTH_SHORT).show()
            Log.d(TAG, "saveToDownloadFolder: saved to Downloads Folder")
            progressDialog.dismiss()
        } catch (e: Exception) {
            progressDialog.dismiss()
            Log.d(TAG, "saveToDownloadFolder: Failed to save due to ${e.message}")
            Toast.makeText(this, "  Failed to save due to ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun requestPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            //Android is 11(R) or above
            try {
                Log.d(TAG, "requestPermission: try")
                val intent = Intent()
                intent.action = Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION
                val uri = Uri.fromParts("package", this.packageName, null)
                intent.data = uri
                storageActivityResultLauncher.launch(intent)
            } catch (e: Exception) {
                Log.e(TAG, "requestPermission: ", e)
                val intent = Intent()
                intent.action = Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION
                storageActivityResultLauncher.launch(intent)
            }
        } else {
            //Android is below 11(R)
            ActivityCompat.requestPermissions(
                this,
                arrayOf(
                    android.Manifest.permission.WRITE_EXTERNAL_STORAGE,
                    android.Manifest.permission.READ_EXTERNAL_STORAGE
                ), 100
            )
        }
    }

    private val storageActivityResultLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            Log.d(TAG, "storageActivityResultLauncher: ")
            //here we will handle the result of our intent
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                //Android is 11(R) or above
                if (Environment.isExternalStorageManager()) {
                    //Manage External Storage Permission is granted
                    Log.d(
                        TAG,
                        ":storageActivityResultLauncher Manage External Storage Permission is granted"
                    )
                } else {

                    Log.d(
                        TAG,
                        "storageActivityResultLauncher: Manage External Storage Permission is denied...."
                    )
                }
            } else {
                //Android is below 11(R)
            }
        }

}