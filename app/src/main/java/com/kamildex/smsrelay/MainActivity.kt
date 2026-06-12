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
    private val networkReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, i: Intent?) {
            val online = i?.getBooleanExtra("online", true) ?: true
            updateNetworkUI(online)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupToolbar()
        setupRecyclerView()
        loadSettings()
        updateUI()
        setupListeners()

        if (!hasPermissions()) showPermissionDialog()
    }

    private fun setupToolbar() {
        try {
            binding.toolbar.setOnMenuItemClickListener { item ->
                if (item.itemId == R.id.action_about) { showAboutDialog(); true } else false
            }
        } catch (e: Exception) {}
    }

    private fun showAboutDialog() {
        AlertDialog.Builder(this)
            .setTitle("About SMS Relay")
            .setMessage(
                "SMS Relay\n\n" +
                "Automatically forwards incoming SMS to Telegram.\n\n" +
                "Developed by Kamil Hussen\n\n" +
                "Version 1.0.0"
            )
            .setPositiveButton("OK", null).show()
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
            if (results.all { it == PackageManager.PERMISSION_GRANTED }) askBatteryOptimization()
            else Snackbar.make(binding.root, "Permissions denied. App may not work.", Snackbar.LENGTH_LONG)
                .setAction("Settings") {
                    startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                        .apply { data = Uri.fromParts("package", packageName, null) })
                }.show()
        }
    }

    private fun askBatteryOptimization() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val pm = getSystemService(PowerManager::class.java)
                if (!pm.isIgnoringBatteryOptimizations(packageName)) {
                    AlertDialog.Builder(this)
                        .setTitle("Battery Optimization")
                        .setMessage("Disable battery optimization to ensure SMS Relay works in background.")
                        .setPositiveButton("Disable") { _, _ ->
                            startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                                .apply { data = Uri.parse("package:$packageName") })
                        }
                        .setNegativeButton("Skip", null).show()
                }
            }
        } catch (e: Exception) {}
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
            val online = try { NetworkMonitor.isAvailable(this) } catch (e: Exception) { true }
            if (!online) {
                AlertDialog.Builder(this)
                    .setTitle("⚠️ No Internet Connection")
                    .setMessage("You are starting without internet. SMS Relay will forward messages once connection is restored.")
                    .setPositiveButton("Start Anyway") { _, _ -> startForwarding(token, chatId) }
                    .setNegativeButton("Cancel", null).show()
                return@setOnClickListener
            }
            startForwarding(token, chatId)
        }

        binding.btnStop.setOnClickListener {
            startService(Intent(this, SmsForwarderService::class.java).apply {
                action = SmsForwarderService.ACTION_STOP
            })
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
            val online = try { NetworkMonitor.isAvailable(this) } catch (e: Exception) { true }
            if (!online) {
                Snackbar.make(binding.root, "⚠️ No internet connection", Snackbar.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val msg = "✅ <b>SMS Relay Connected!</b>\n\nYour bot is working correctly.\n🕐 ${
                java.text.SimpleDateFormat("hh:mm a", java.util.Locale.getDefault()).format(java.util.Date())
            }"
            TelegramSender.send(token, chatId, msg) { success ->
                runOnUiThread {
                    Snackbar.make(binding.root,
                        if (success) "✅ Test sent!" else "❌ Failed. Check token and Chat ID.",
                        Snackbar.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun startForwarding(token: String, chatId: String) {
        Prefs.save(this, token, chatId,
            binding.etKeywords.text.toString().trim(),
            binding.etSenders.text.toString().trim())
        ContextCompat.startForegroundService(this, Intent(this, SmsForwarderService::class.java))
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
        val online = try { NetworkMonitor.isAvailable(this) } catch (e: Exception) { true }
        binding.statusDot.setBackgroundResource(when {
            !active -> R.drawable.dot_red
            !online -> R.drawable.dot_yellow
            else -> R.drawable.dot_green
        })
        binding.tvStatus.text = when {
            !active -> "Forwarding Stopped"
            !online -> "⚠️ No Network — Waiting..."
            else -> "Forwarding Active"
        }
        binding.tvStatus.setTextColor(ContextCompat.getColor(this, when {
            !active -> R.color.red
            !online -> R.color.yellow
            else -> R.color.green
        }))
        binding.btnStart.visibility = if (active) View.GONE else View.VISIBLE
        binding.btnStop.visibility = if (active) View.VISIBLE else View.GONE
    }

    private fun updateNetworkUI(online: Boolean) {
        if (!Prefs.isActive(this)) return
        binding.statusDot.setBackgroundResource(if (online) R.drawable.dot_green else R.drawable.dot_yellow)
        binding.tvStatus.text = if (online) "Forwarding Active" else "⚠️ No Network — Waiting..."
        binding.tvStatus.setTextColor(ContextCompat.getColor(this,
            if (online) R.color.green else R.color.yellow))
    }

    private fun refreshLog() {
        val logs = SmsLog.getAll(this)
        logAdapter.update(logs)
        binding.tvEmptyLog.visibility = if (logs.isEmpty()) View.VISIBLE else View.GONE
        binding.tvLogCount.text = "${logs.size}/20"
    }

    private fun registerReceiverCompat(receiver: BroadcastReceiver, filter: IntentFilter) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(receiver, filter)
        }
    }

    override fun onResume() {
        super.onResume()
        try {
            registerReceiverCompat(statusReceiver, IntentFilter("com.kamildex.smsrelay.STATUS_CHANGED"))
            registerReceiverCompat(smsReceiver, IntentFilter("com.kamildex.smsrelay.SMS_FORWARDED"))
            registerReceiverCompat(networkReceiver, IntentFilter("com.kamildex.smsrelay.NETWORK_CHANGED"))
        } catch (e: Exception) {}
        updateUI()
        refreshLog()
    }

    override fun onPause() {
        super.onPause()
        try {
            unregisterReceiver(statusReceiver)
            unregisterReceiver(smsReceiver)
            unregisterReceiver(networkReceiver)
        } catch (e: Exception) {}
    }
}
