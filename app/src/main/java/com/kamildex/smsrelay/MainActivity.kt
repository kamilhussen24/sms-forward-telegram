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
        override fun onReceive(c: Context?, i: Intent?) { updateUI() }
    }
    private val smsReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, i: Intent?) { refreshLog() }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupRecyclerView()
        loadSettings()
        updateUI()
        setupListeners()

        if (!hasPermissions()) showPermissionDialog()
    }

    private fun hasPermissions() =
        ContextCompat.checkSelfPermission(this, Manifest.permission.RECEIVE_SMS) == PackageManager.PERMISSION_GRANTED &&
        ContextCompat.checkSelfPermission(this, Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED

    private fun showPermissionDialog() {
        AlertDialog.Builder(this)
            .setTitle("Permissions Required")
            .setMessage("SMS Relay needs:\n\n• Receive SMS\n• Read SMS\n• Notifications\n\nWithout these, the app cannot forward messages.")
            .setPositiveButton("Grant") { _, _ -> requestPermissions() }
            .setCancelable(false).show()
    }

    private fun requestPermissions() {
        val perms = mutableListOf(Manifest.permission.RECEIVE_SMS, Manifest.permission.READ_SMS)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            perms.add(Manifest.permission.POST_NOTIFICATIONS)
        ActivityCompat.requestPermissions(this, perms.toTypedArray(), 100)
    }

    override fun onRequestPermissionsResult(rc: Int, perms: Array<out String>, results: IntArray) {
        super.onRequestPermissionsResult(rc, perms, results)
        if (rc == 100) {
            if (results.all { it == PackageManager.PERMISSION_GRANTED }) {
                askBatteryOptimization()
            } else {
                Snackbar.make(binding.root, "Permissions denied. App may not work.", Snackbar.LENGTH_LONG)
                    .setAction("Settings") {
                        startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                            .apply { data = Uri.fromParts("package", packageName, null) })
                    }.show()
            }
        }
    }

    private fun askBatteryOptimization() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val pm = getSystemService(PowerManager::class.java)
            if (!pm.isIgnoringBatteryOptimizations(packageName)) {
                AlertDialog.Builder(this)
                    .setTitle("Battery Optimization")
                    .setMessage("Disable battery optimization to ensure SMS Relay works in background even when screen is off.")
                    .setPositiveButton("Disable") { _, _ ->
                        startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                            .apply { data = Uri.parse("package:$packageName") })
                    }
                    .setNegativeButton("Skip", null).show()
            }
        }
    }

    private fun setupRecyclerView() {
        logAdapter = LogAdapter(emptyList())
        binding.rvLog.layoutManager = LinearLayoutManager(this)
        binding.rvLog.adapter = logAdapter
        refreshLog()
    }

    private fun setupListeners() {
        binding.toggleGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                val all = checkedId == binding.btnAll.id
                Prefs.setForwardAll(this, all)
                binding.filterSection.visibility = if (all) View.GONE else View.VISIBLE
            }
        }

        binding.btnStart.setOnClickListener {
            val token = binding.etToken.text.toString().trim()
            val chatId = binding.etChatId.text.toString().trim()
            if (token.isEmpty() || chatId.isEmpty()) {
                Snackbar.make(binding.root, "Bot Token and Chat ID are required", Snackbar.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (!hasPermissions()) { showPermissionDialog(); return@setOnClickListener }
            Prefs.save(this, token, chatId,
                binding.etKeywords.text.toString().trim(),
                binding.etSenders.text.toString().trim())
            ContextCompat.startForegroundService(this, Intent(this, SmsForwarderService::class.java))
        }

        binding.btnStop.setOnClickListener {
            startService(Intent(this, SmsForwarderService::class.java).apply { action = SmsForwarderService.ACTION_STOP })
        }

        binding.btnClearLog.setOnClickListener {
            SmsLog.clear(this); refreshLog()
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
                    Snackbar.make(binding.root,
                        if (success) "✅ Test sent!" else "❌ Failed. Check token and Chat ID.",
                        Snackbar.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun loadSettings() {
        binding.etToken.setText(Prefs.getBotToken(this))
        binding.etChatId.setText(Prefs.getChatId(this))
        binding.etKeywords.setText(Prefs.getKeywords(this))
        binding.etSenders.setText(Prefs.getSenders(this))
        val all = Prefs.isForwardAll(this)
        if (all) { binding.btnAll.isChecked = true; binding.filterSection.visibility = View.GONE }
        else { binding.btnFilter.isChecked = true; binding.filterSection.visibility = View.VISIBLE }
    }

    private fun updateUI() {
        val active = Prefs.isActive(this)
        binding.statusDot.setBackgroundResource(if (active) R.drawable.dot_green else R.drawable.dot_red)
        binding.tvStatus.text = if (active) "Forwarding Active" else "Forwarding Stopped"
        binding.tvStatus.setTextColor(ContextCompat.getColor(this, if (active) R.color.green else R.color.red))
        binding.btnStart.visibility = if (active) View.GONE else View.VISIBLE
        binding.btnStop.visibility = if (active) View.VISIBLE else View.GONE
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
        updateUI(); refreshLog()
    }

    override fun onPause() {
        super.onPause()
        try { unregisterReceiver(statusReceiver); unregisterReceiver(smsReceiver) } catch (e: Exception) {}
    }
}
