package com.example.journal

import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ProcessLifecycleOwner
import com.example.journal.ui.JournalRoot
import com.example.journal.ui.theme.JournalTheme

class MainActivity : ComponentActivity() {

    private val viewModel: JournalViewModel by viewModels {
        JournalViewModelFactory(application as JournalApplication)
    }

    private val processObserver = LifecycleEventObserver { _, event ->
        when (event) {
            Lifecycle.Event.ON_START -> viewModel.cancelPendingLock()
            Lifecycle.Event.ON_STOP -> viewModel.scheduleLock()
            else -> Unit
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (BLOCK_SCREENSHOTS) {
            window.setFlags(
                WindowManager.LayoutParams.FLAG_SECURE,
                WindowManager.LayoutParams.FLAG_SECURE,
            )
        }

        enableEdgeToEdge()
        ProcessLifecycleOwner.get().lifecycle.addObserver(processObserver)

        handleIntent(intent)

        setContent {
            JournalTheme {
                JournalRoot(viewModel = viewModel)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent?.action == ACTION_NEW_ENTRY) viewModel.requestNewEntry()
    }

    override fun onDestroy() {
        ProcessLifecycleOwner.get().lifecycle.removeObserver(processObserver)
        super.onDestroy()
    }

    companion object {
        const val ACTION_NEW_ENTRY = "com.example.journal.NEW_ENTRY"

        /** Set to false if you want to be able to take screenshots. */
        private const val BLOCK_SCREENSHOTS = true
    }
}
