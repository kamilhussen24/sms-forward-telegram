package com.kamildex.relaygram

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.View
import android.view.inputmethod.InputMethodManager
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.snackbar.Snackbar
import com.kamildex.relaygram.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var logAdapter: LogAdapter
    private var lastTestTime = 0L
    private var isTestPending = false

    private val statusReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, i: Intent?) { updateUI() }
    }
    private val smsReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, i: Intent?) { refreshLog() }
    }
    private val networkReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, i: Intent?) {
            updateNetworkUI(i?.getBooleanExtra("online", true) ?: true)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        window.decorView.post { hideKeyboard() }
        setupToolbar()
        setupRecyclerView()
        loadSettings()
        updateUI()
        setupListeners()
        if (!hasPermissions()) showPermissionDialog()
    }

    private fun hideKeyboard() {
        try {
            val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
            currentFocus?.let { imm.hideSoftInputFromWindow(it.windowToken, 0) }
            binding.root.clearFocus()
        } catch (e: Exception) {}
    }

    private fun setupToolbar() {
        binding.toolbar.setOnMenuItemClickListener { item ->
            if (item.itemId == R.id.action_about) { showAboutDialog(); true } else false
        }
    }

    private fun showAboutDialog() {
        AlertDialog.Builder(this)
            .setTitle("About SMS Relay")
            .setMessage("SMS Relay\n\nAutomatically forwards incoming SMS to Telegram.\n\nDeveloped by Kamil Hussen\n\nVersion 1.0.0")
            .setPositiveButton("OK", null)
            .show()
    }

    private fun isOnline(): Boolean {
        return try {
            val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val net = cm.activeNetwork ?: return false
            cm.getNetworkCapabilities(net)
                ?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
        } catch (e: Exception) { true }
    }

    private fun hasPermissions() =
        ContextCompat.checkSelfPermission(this, Manifest.permission.RECEIVE_SMS) ==
                PackageManager.PERMISSION_GRANTED &&
        ContextCompat.checkSelfPermission(this, Manifest.permission.READ_SMS) ==
                PackageManager.PERMISSION_GRANTED

    private fun showPermissionDialog() {
        AlertDialog.Builder(this)
            .setTitle("Permissions Required")
            .setMessage("SMS Relay needs:\n\n- Receive SMS\n- Read SMS\n- Notifications\n\nWithout these, the app cannot forward messages.")
            .setPositiveButton("Grant") { _, _ -> requestPermissions() }
            .setCancelable(false)
            .show()
    }

    private fun requestPermissions() {
        val perms = mutableListOf(
            Manifest.permission.RECEIVE_SMS,
            Manifest.permission.READ_SMS
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            perms.add(Manifest.permission.POST_NOTIFICATIONS)
        ActivityCompat.requestPermissions(this, perms.toTypedArray(), 100)
    }

    override fun onRequestPermissionsResult(
        rc: Int, perms: Array<out String>, results: IntArray
    ) {
        super.onRequestPermissionsResult(rc, perms, results)
        if (rc == 100) {
            if (results.isNotEmpty() && results.all { it == PackageManager.PERMISSION_GRANTED })
                askBatteryOptimization()
            else
                Snackbar.make(
                    binding.root,
                    "Some permissions denied. App may not work correctly.",
                    Snackbar.LENGTH_LONG
                ).setAction("Settings") {
                    startActivity(
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                            .apply { data = Uri.fromParts("package", packageName, null) }
                    )
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
                        .setMessage("Disable battery optimization to ensure SMS Relay works in background even when the screen is off.")
                        .setPositiveButton("Disable") { _, _ ->
                            startActivity(
                                Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                                    .apply { data = Uri.parse("package:$packageName") }
                            )
                        }
                        .setNegativeButton("Skip", null)
                        .show()
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

        // Save button
        binding.btnSave.setOnClickListener {
            val token = binding.etToken.text.toString().trim()
            val chatId = binding.etChatId.text.toString().trim()
            if (token.isEmpty() || chatId.isEmpty()) {
                Snackbar.make(binding.root,
                    "Bot Token and Chat ID cannot be empty",
                    Snackbar.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            // Save all fields including forward mode
            val isForwardAll = binding.btnAll.isChecked
            Prefs.save(this, token, chatId,
                binding.etKeywords.text.toString().trim(),
                binding.etSenders.text.toString().trim(),
                forwardAll = isForwardAll)
            hideKeyboard()
            binding.tvSaveHint.visibility = View.GONE
            binding.tvSavedIndicator.visibility = View.VISIBLE
            Snackbar.make(binding.root, "Configuration saved!", Snackbar.LENGTH_SHORT).show()
        }

        // Forward mode toggle
        binding.toggleGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                val all = checkedId == binding.btnAll.id
                Prefs.setForwardAll(this, all)
                binding.filterSection.visibility = if (all) View.GONE else View.VISIBLE
            }
        }

        // Start
        binding.btnStart.setOnClickListener {
            val token = binding.etToken.text.toString().trim()
            val chatId = binding.etChatId.text.toString().trim()
            if (token.isEmpty() || chatId.isEmpty()) {
                Snackbar.make(binding.root,
                    "Bot Token and Chat ID are required",
                    Snackbar.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (!hasPermissions()) {
                showPermissionDialog()
                return@setOnClickListener
            }
            Prefs.save(this, token, chatId,
                binding.etKeywords.text.toString().trim(),
                binding.etSenders.text.toString().trim())
            hideKeyboard()
            if (!isOnline()) {
                AlertDialog.Builder(this)
                    .setTitle("No Internet Connection")
                    .setMessage("Starting without internet. Messages will be forwarded once connection is restored.")
                    .setPositiveButton("Start Anyway") { _, _ -> doStartService() }
                    .setNegativeButton("Cancel", null)
                    .show()
            } else {
                doStartService()
            }
        }

        // Stop
        binding.btnStop.setOnClickListener {
            startService(Intent(this, SmsForwarderService::class.java).apply {
                action = SmsForwarderService.ACTION_STOP
            })
        }

        // Clear log
        binding.btnClearLog.setOnClickListener {
            SmsLog.clear(this)
            refreshLog()
            Snackbar.make(binding.root, "Log cleared", Snackbar.LENGTH_SHORT).show()
        }

        // Test
        binding.btnTest.setOnClickListener {
            if (isTestPending) {
                Snackbar.make(binding.root, "Test in progress...", Snackbar.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val token = binding.etToken.text.toString().trim()
            val chatId = binding.etChatId.text.toString().trim()

            if (token.isEmpty() || chatId.isEmpty()) {
                Snackbar.make(binding.root,
                    "Enter Bot Token and Chat ID first",
                    Snackbar.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (!isOnline()) {
                Snackbar.make(binding.root,
                    "No internet connection",
                    Snackbar.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val now = System.currentTimeMillis()
            if (now - lastTestTime < 3000) {
                Snackbar.make(binding.root,
                    "Please wait before testing again",
                    Snackbar.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            lastTestTime = now
            isTestPending = true
            hideKeyboard()

            // Show loading
            binding.btnTest.isEnabled = false
            binding.btnTest.text = "..."

            val msg = "<b>SMS Relay Connected!</b>\n\nYour bot is working correctly.\nTime: ${
                java.text.SimpleDateFormat("hh:mm a", java.util.Locale.getDefault())
                    .format(java.util.Date())
            }"

            TelegramSender.send(token, chatId, msg) { success, error ->
                runOnUiThread {
                    isTestPending = false
                    binding.btnTest.isEnabled = true
                    binding.btnTest.text = "Test"

                    if (success) {
                        Snackbar.make(binding.root,
                            "Test sent successfully!",
                            Snackbar.LENGTH_LONG).show()
                    } else {
                        val errorMsg = when {
                            error?.contains("chat not found", ignoreCase = true) == true ->
                                "Chat ID not found. Make sure you sent /start to your bot."
                            error?.contains("Unauthorized", ignoreCase = true) == true ->
                                "Invalid Bot Token. Please check and re-enter."
                            error?.contains("bot was blocked", ignoreCase = true) == true ->
                                "Bot was blocked. Unblock the bot in Telegram."
                            error?.contains("Too Many Requests", ignoreCase = true) == true ->
                                "Too many requests. Wait a moment and try again."
                            error?.contains("Network error", ignoreCase = true) == true ->
                                "Network error. Check your internet connection."
                            !error.isNullOrEmpty() -> "Error: $error"
                            else -> "Failed. Check token and Chat ID."
                        }
                        Snackbar.make(binding.root, errorMsg, Snackbar.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    private fun doStartService() {
        try {
            ContextCompat.startForegroundService(
                this,
                Intent(this, SmsForwarderService::class.java)
            )
        } catch (e: Exception) {
            Snackbar.make(binding.root, "Failed to start service", Snackbar.LENGTH_LONG).show()
        }
    }

    private fun loadSettings() {
        binding.etToken.setText(Prefs.getBotToken(this))
        binding.etChatId.setText(Prefs.getChatId(this))
        binding.etKeywords.setText(Prefs.getKeywords(this))
        binding.etSenders.setText(Prefs.getSenders(this))
        if (Prefs.isConfigSaved(this)) {
            binding.tvSavedIndicator.visibility = View.VISIBLE
            binding.tvSaveHint.visibility = View.GONE
        }
        val all = Prefs.isForwardAll(this)
        if (all) {
            binding.btnAll.isChecked = true
            binding.filterSection.visibility = View.GONE
        } else {
            binding.btnFilter.isChecked = true
            binding.filterSection.visibility = View.VISIBLE
        }
    }

    private fun updateUI() {
        val active = Prefs.isActive(this)
        val online = isOnline()
        binding.statusDot.setBackgroundResource(when {
            !active -> R.drawable.dot_red
            !online -> R.drawable.dot_yellow
            else -> R.drawable.dot_green
        })
        binding.tvStatus.text = when {
            !active -> "Forwarding Stopped"
            !online -> "No Network — Waiting..."
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
        binding.statusDot.setBackgroundResource(
            if (online) R.drawable.dot_green else R.drawable.dot_yellow)
        binding.tvStatus.text =
            if (online) "Forwarding Active" else "No Network — Waiting..."
        binding.tvStatus.setTextColor(ContextCompat.getColor(this,
            if (online) R.color.green else R.color.yellow))
    }

    private fun refreshLog() {
        val logs = SmsLog.getAll(this)
        logAdapter.update(logs)
        binding.tvEmptyLog.visibility = if (logs.isEmpty()) View.VISIBLE else View.GONE
        binding.tvLogCount.text = "${logs.size}/20"
    }

    private fun safeRegister(receiver: BroadcastReceiver, filter: IntentFilter) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
            else
                registerReceiver(receiver, filter)
        } catch (e: Exception) {}
    }

    override fun onResume() {
        super.onResume()
        safeRegister(statusReceiver, IntentFilter("com.kamildex.smsrelay.STATUS_CHANGED"))
        safeRegister(smsReceiver, IntentFilter("com.kamildex.smsrelay.SMS_FORWARDED"))
        safeRegister(networkReceiver, IntentFilter("com.kamildex.smsrelay.NETWORK_CHANGED"))
        updateUI()
        refreshLog()
    }

    override fun onPause() {
        super.onPause()
        try { unregisterReceiver(statusReceiver) } catch (e: Exception) {}
        try { unregisterReceiver(smsReceiver) } catch (e: Exception) {}
        try { unregisterReceiver(networkReceiver) } catch (e: Exception) {}
    }
}
