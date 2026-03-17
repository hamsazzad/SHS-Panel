package com.shspanel.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.shspanel.app.databinding.ActivityMainBinding
import com.shspanel.app.ui.filemanager.FileManagerFragment

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var fileManagerFragment: FileManagerFragment? = null
    private var navListenerAttached = false

    private val manageStorageLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && Environment.isExternalStorageManager()) {
            fileManagerFragment?.reload()
        }
    }

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.values.all { it }
        if (allGranted) {
            fileManagerFragment?.reload()
        } else {
            Toast.makeText(this, "Storage permission needed for file access", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        if (savedInstanceState == null) {
            fileManagerFragment = FileManagerFragment.newInstance()
            supportFragmentManager.beginTransaction()
                .replace(R.id.fragmentContainer, fileManagerFragment!!, "file_manager")
                .commitNow()
        } else {
            fileManagerFragment = supportFragmentManager
                .findFragmentByTag("file_manager") as? FileManagerFragment
        }

        binding.bottomNav.post {
            if (!navListenerAttached) {
                navListenerAttached = true
                binding.bottomNav.setOnItemSelectedListener { item ->
                    when (item.itemId) {
                        R.id.nav_files -> {
                            val existing = supportFragmentManager
                                .findFragmentByTag("file_manager") as? FileManagerFragment
                            if (existing != null && existing.isAdded) {
                                return@setOnItemSelectedListener true
                            }
                            fileManagerFragment = FileManagerFragment.newInstance()
                            supportFragmentManager.beginTransaction()
                                .setCustomAnimations(R.anim.slide_in_right, R.anim.slide_out_left)
                                .replace(R.id.fragmentContainer, fileManagerFragment!!, "file_manager")
                                .commit()
                            true
                        }
                        else -> false
                    }
                }
            }
        }

        checkAndRequestStoragePermissions()
    }

    private fun checkAndRequestStoragePermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                binding.root.post { showPermissionRationale() }
            }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val needsRead = ContextCompat.checkSelfPermission(
                this, Manifest.permission.READ_EXTERNAL_STORAGE
            ) != PackageManager.PERMISSION_GRANTED
            val needsWrite = ContextCompat.checkSelfPermission(
                this, Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) != PackageManager.PERMISSION_GRANTED
            if (needsRead || needsWrite) {
                val perms = mutableListOf(Manifest.permission.READ_EXTERNAL_STORAGE)
                if (needsWrite) perms.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                requestPermissionLauncher.launch(perms.toTypedArray())
            }
        }
    }

    private fun showPermissionRationale() {
        if (isFinishing || isDestroyed) return
        AlertDialog.Builder(this)
            .setTitle("Storage Access Needed")
            .setMessage(
                "SHS Panel needs access to all files to manage your storage.\n\n" +
                "Tap 'Allow' then enable 'Allow access to manage all files'."
            )
            .setPositiveButton("Allow") { _, _ -> requestAllFilesAccess() }
            .setNegativeButton("Not Now") { _, _ ->
                Toast.makeText(this, "Limited functionality without storage access", Toast.LENGTH_LONG).show()
            }
            .setCancelable(false)
            .show()
    }

    private fun requestAllFilesAccess() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                    data = Uri.parse("package:$packageName")
                }
                manageStorageLauncher.launch(intent)
            } catch (e: Exception) {
                try {
                    manageStorageLauncher.launch(
                        Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                    )
                } catch (e2: Exception) {
                    Toast.makeText(this, "Please grant storage permission in Settings", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    @Suppress("OVERRIDE_DEPRECATION")
    override fun onBackPressed() {
        val fm = supportFragmentManager.findFragmentByTag("file_manager") as? FileManagerFragment
        if (fm?.onBackPressed() == true) return
        @Suppress("DEPRECATION")
        super.onBackPressed()
    }
}
