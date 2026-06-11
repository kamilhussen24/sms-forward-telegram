package com.kamildex.smsrelay

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
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
        override fun onReceive(context: Context?, intent: Intent?) {
            updateUI()
        }
    }

    private val smsReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            refreshLog()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupRecyclerView()
        loadSavedSettings()
        updateUI()
        setupClickListeners()
        requestPermissions()
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
        binding.btnStart.setOnClickListener {
            val token = binding.etToken.text.toString().trim()
            val chatId = binding.etChatId.text.toString().trim()
            val keywords = binding.etKeywords.text.toString().trim()
            val senders = binding.etSenders.text.toString().trim()

            if (token.isEmpty() || chatId.isEmpty()) {
                Snackbar.make(binding.root, "Bot Token and Chat ID are required", Snackbar.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            Prefs.save(this, token, chatId, keywords, senders)

            val intent = Intent(this, SmsForwarderService::class.java)
            ContextCompat.startForegroundService(this, intent)
        }

        binding.btnStop.setOnClickListener {
            val intent = Intent(this, SmsForwarderService::class.java).apply {
                action = SmsForwarderService.ACTION_STOP
            }
            startService(intent)
        }

        binding.btnClearLog.setOnClickListener {
            SmsLog.clear(this)
            refreshLog()
        }

        binding.btnTest.setOnClickListener {
            val token = binding.etToken.text.toString().trim()
            val chatId = binding.etChatId.text.toString().trim()
            if (token.isEmpty() || chatId.isEmpty()) {
                Snackbar.make(binding.root, "Enter Bot Token and Chat ID first", Snackbar.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val testMsg = "✅ <b>SMS Relay Test</b>\n\nYour bot is connected and working correctly!\n\n🕐 ${java.text.SimpleDateFormat("hh:mm a", java.util.Locale.getDefault()).format(java.util.Date())}"
            TelegramSender.send(token, chatId, testMsg) { success ->
                runOnUiThread {
                    val msg = if (success) "✅ Test message sent!" else "❌ Failed. Check token and chat ID."
                    Snackbar.make(binding.root, msg, Snackbar.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun loadSavedSettings() {
        binding.etToken.setText(Prefs.getBotToken(this))
        binding.etChatId.setText(Prefs.getChatId(this))
        binding.etKeywords.setText(Prefs.getKeywords(this))
        binding.etSenders.setText(Prefs.getSenders(this))
    }

    private fun updateUI() {
        val active = Prefs.isActive(this)
        binding.apply {
            statusIndicator.setBackgroundResource(
                if (active) R.drawable.dot_green else R.drawable.dot_red
            )
            tvStatus.text = if (active) "Forwarding Active" else "Forwarding Stopped"
            tvStatus.setTextColor(
                ContextCompat.getColor(
                    this@MainActivity,
                    if (active) R.color.green else R.color.red
                )
            )
            btnStart.visibility = if (active) View.GONE else View.VISIBLE
            btnStop.visibility = if (active) View.VISIBLE else View.GONE
        }
    }

    private fun refreshLog() {
        val logs = SmsLog.getAll(this)
        logAdapter.update(logs)
        binding.tvEmptyLog.visibility = if (logs.isEmpty()) View.VISIBLE else View.GONE
        binding.tvLogCount.text = "${logs.size} messages"
    }

    private fun requestPermissions() {
        val permissions = mutableListOf(
            Manifest.permission.RECEIVE_SMS,
            Manifest.permission.READ_SMS
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        val missing = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missing.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, missing.toTypedArray(), 100)
        }
    }

    override fun onResume() {
        super.onResume()
        val filter1 = IntentFilter("com.kamildex.smsrelay.STATUS_CHANGED")
        val filter2 = IntentFilter("com.kamildex.smsrelay.SMS_FORWARDED")
        registerReceiver(statusReceiver, filter1)
        registerReceiver(smsReceiver, filter2)
        updateUI()
        refreshLog()
    }

    override fun onPause() {
        super.onPause()
        try {
            unregisterReceiver(statusReceiver)
            unregisterReceiver(smsReceiver)
        } catch (e: Exception) {}
    }
}
