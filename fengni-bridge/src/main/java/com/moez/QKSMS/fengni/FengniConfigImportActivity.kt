package com.moez.QKSMS.fengni

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import dagger.android.AndroidInjection
import dev.octoshrimpy.quik.fengni.R
import java.io.File
import javax.inject.Inject

/**
 * Simple file picker activity for importing fengni configuration.
 *
 * Flow:
 *   1. Tap "Select key file" → system file picker for .key
 *   2. Tap "Select config file" → system file picker for .conf
 *   3. Both selected → auto-import via FengniConfigManager
 *   4. Show success/failure toast → finish
 */
class FengniConfigImportActivity : AppCompatActivity() {

    @Inject lateinit var configManager: FengniConfigManager

    private var selectedKeyUri: Uri? = null
    private var selectedConfUri: Uri? = null
    private lateinit var keyBtn: Button
    private lateinit var confBtn: Button
    private lateinit var statusText: TextView

    private val keyPicker = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let { onKeySelected(it) } }

    private val confPicker = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let { onConfSelected(it) } }

    override fun onCreate(savedInstanceState: Bundle?) {
        AndroidInjection.inject(this)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.fengni_config_import_activity)

        keyBtn = findViewById(R.id.selectKeyBtn)
        confBtn = findViewById(R.id.selectConfBtn)
        statusText = findViewById(R.id.statusText)

        keyBtn.setOnClickListener {
            keyPicker.launch(arrayOf("*/*"))
        }

        confBtn.setOnClickListener {
            confPicker.launch(arrayOf("*/*"))
        }
    }

    private fun onKeySelected(uri: Uri) {
        selectedKeyUri = uri
        keyBtn.text = getString(R.string.fengni_import_key_selected)
        updateStatus()
        tryImportIfReady()
    }

    private fun onConfSelected(uri: Uri) {
        selectedConfUri = uri
        confBtn.text = getString(R.string.fengni_import_conf_selected)
        updateStatus()
        tryImportIfReady()
    }

    private fun updateStatus() {
        val keyOk = selectedKeyUri != null
        val confOk = selectedConfUri != null
        statusText.text = when {
            keyOk && confOk -> getString(R.string.fengni_import_importing)
            keyOk -> getString(R.string.fengni_import_waiting_conf)
            confOk -> getString(R.string.fengni_import_waiting_key)
            else -> getString(R.string.fengni_import_select_both)
        }
    }

    private fun tryImportIfReady() {
        val keyUri = selectedKeyUri ?: return
        val confUri = selectedConfUri ?: return

        try {
            // Copy picked files to temp location for File access
            val keyFile = copyToTemp(keyUri, "fengni-import.key")
            val confFile = copyToTemp(confUri, "fengni-import.conf")

            if (keyFile == null || confFile == null) {
                showResult(false, "Failed to read selected files")
                return
            }

            val config = configManager.importConfig(confFile, keyFile)

            // Clean up temp files
            keyFile.delete()
            confFile.delete()

            if (config != null) {
                showResult(true, "Config imported: ${config.remoteHost}:${config.remotePort}")
            } else {
                showResult(false, "Import failed — check file format and key")
            }
        } catch (e: Exception) {
            showResult(false, "Import error: ${e.message}")
        }
    }

    private fun copyToTemp(uri: Uri, name: String): File? {
        return try {
            val tempFile = File(cacheDir, name)
            contentResolver.openInputStream(uri)?.use { input ->
                tempFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            tempFile.takeIf { it.exists() }
        } catch (e: Exception) {
            null
        }
    }

    private fun showResult(success: Boolean, message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        if (success) {
            // Start the MQTT service now that config is available
            startService(Intent(this, FengniMqttService::class.java))
            setResult(RESULT_OK, Intent().putExtra("imported", true))
        }
        finish()
    }
}
