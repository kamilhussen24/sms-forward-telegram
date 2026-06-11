package com.kamildex.smsrelay

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.snackbar.Snackbar
import com.kamildex.smsrelay.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var logAdapter: LogAdapter

    private val statusReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) { updateUI() }
    }

    private val smsReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) { refreshLog() }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupRecyclerView()
        loadSavedSettings()
        updateUI()
        setupClickListeners()

        // Request permissions on first launch
        if (!hasRequiredPermissions()) {
            showPermissionDialog()
        }
    }

    private fun hasRequiredPermissions(): Boolean {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.RECEIVE_SMS) == PackageManager.PERMISSION_GRANTED &&
               ContextCompat.checkSelfPermission(this, Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED
    }

    private fun showPermissionDialog() {
        AlertDialog.Builder(this)
            .setTitle("Permissions Required")
            .setMessage("SMS Relay needs the following permissions to work:\n\n• Receive SMS — to detect incoming messages\n• Read SMS — to read message content\n• Notifications — to show service status\n\nWithout these, the app cannot forward messages.")
            .setPositiveButton("Grant Permissions") { _, _ -> requestAllPermissions() }
            .setNegativeButton("Cancel", null)
            .setCancelable(false)
            .show()
    }

    private fun requestAllPermissions() {
        val permissions = mutableListOf(
            Manifest.permission.RECEIVE_SMS,
            Manifest.permission.READ_SMS
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        ActivityCompat.requestPermissions(this, permissions.toTypedArray(), 100)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 100) {
            val allGranted = grantResults.all { it == PackageManager.PERMISSION_GRANTED }
            if (!allGranted) {
                Snackbar.make(binding.root, "Some permissions denied. App may not work correctly.", Snackbar.LENGTH_LONG)
                    .setAction("Settings") {
                        startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data = Uri.fromParts("package", packageName, null)
                        })
                    }.show()
            } else {
                // Ask to disable battery optimization
                requestBatteryOptimizationExemption()
            }
        }
    }

    private fun requestBatteryOptimizationExemption() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val pm = getSystemService(PowerManager::class.java)
            if (!pm.isIgnoringBatteryOptimizations(packageName)) {
                AlertDialog.Builder(this)
                    .setTitle("Battery Optimization")
                    .setMessage("To ensure SMS Relay works in background, please disable battery optimization for this app.")
                    .setPositiveButton("Disable") { _, _ ->
                        startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                            data = Uri.parse("package:$packageName")
                        })
                    }
                    .setNegativeButton("Skip", null)
                    .show()
            }
        }
    }

    private fun setupRecyclerView() {
        logAdapter = LogAdapter(emptyList())
        binding.rvLog.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = logAdapter
        }
        refreshLog()
    }

    private fun setupClickListeners() {

        // All / Filter toggle
        binding.toggleGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                val isAll = checkedId == binding.btnAll.id
                Prefs.setForwardAll(this, isAll)
                binding.filterSection.visibility = if (isAll) View.GONE else View.VISIBLE
            }
        }

        binding.btnStart.setOnClickListener {
            val token = binding.etToken.text.toString().trim()
            val chatId = binding.etChatId.text.toString().trim()

            if (token.isEmpty() || chatId.isEmpty()) {
                Snackbar.make(binding.root, "Bot Token and Chat ID are required", Snackbar.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (!hasRequiredPermissions()) {
                showPermissionDialog()
                return@setOnClickListener
            }

            val keywords = binding.etKeywords.text.toString().trim()
            val senders = binding.etSenders.text.toString().trim()
            Prefs.save(this, token, chatId, keywords, senders)

            ContextCompat.startForegroundService(this, Intent(this, SmsForwarderService::class.java))
        }

        binding.btnStop.setOnClickListener {
            startService(Intent(this, SmsForwarderService::class.java).apply {
                action = SmsForwarderService.ACTION_STOP
            })
        }

        binding.btnClearLog.setOnClickListener {
            SmsLog.clear(this)
            refreshLog()
            Snackbar.make(binding.root, "Log cleared", Snackbar.LENGTH_SHORT).show()
        }

        binding.btnTest.setOnClickListener {
            val token = binding.etToken.text.toString().trim()
            val chatId = binding.etChatId.text.toString().trim()
            if (token.isEmpty() || chatId.isEmpty()) {
                Snackbar.make(binding.root, "Enter Bot Token and Chat ID first", Snackbar.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val msg = "✅ <b>SMS Relay Connected!</b>\n\nYour bot is working correctly.\n🕐 ${java.text.SimpleDateFormat("hh:mm a", java.util.Locale.getDefault()).format(java.util.Date())}"
            TelegramSender.send(token, chatId, msg) { success ->
                runOnUiThread {
                    val text = if (success) "✅ Test sent successfully!" else "❌ Failed. Check token and chat ID."
                    Snackbar.make(binding.root, text, Snackbar.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun loadSavedSettings() {
        binding.etToken.setText(Prefs.getBotToken(this))
        binding.etChatId.setText(Prefs.getChatId(this))
        binding.etKeywords.setText(Prefs.getKeywords(this))
        binding.etSenders.setText(Prefs.getSenders(this))

        val forwardAll = Prefs.isForwardAll(this)
        if (forwardAll) {
            binding.btnAll.isChecked = true
            binding.filterSection.visibility = View.GONE
        } else {
            binding.btnFilter.isChecked = true
            binding.filterSection.visibility = View.VISIBLE
        }
    }

    private fun updateUI() {
        val active = Prefs.isActive(this)
        binding.apply {
            statusDot.setBackgroundResource(if (active) R.drawable.dot_green else R.drawable.dot_red)
            tvStatus.text = if (active) "Forwarding Active" else "Forwarding Stopped"
            tvStatus.setTextColor(ContextCompat.getColor(this@MainActivity,
                if (active) R.color.green else R.color.red))
            btnStart.visibility = if (active) View.GONE else View.VISIBLE
            btnStop.visibility = if (active) View.VISIBLE else View.GONE
        }
    }

    private fun refreshLog() {
        val logs = SmsLog.getAll(this)
        logAdapter.update(logs)
        binding.tvEmptyLog.visibility = if (logs.isEmpty()) View.VISIBLE else View.GONE
        binding.tvLogCount.text = "${logs.size}/20"
    }

    override fun onResume() {
        super.onResume()
        registerReceiver(statusReceiver, IntentFilter("com.kamildex.smsrelay.STATUS_CHANGED"))
        registerReceiver(smsReceiver, IntentFilter("com.kamildex.smsrelay.SMS_FORWARDED"))
        updateUI()
        refreshLog()
    }

    override fun onPause() {
        super.onPause()
        try { unregisterReceiver(statusReceiver); unregisterReceiver(smsReceiver) } catch (e: Exception) {}
    }
}
