package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import com.example.data.firebase.AuthSessionState
import com.example.ui.MainAppScaffold
import com.example.ui.screens.AuthScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.MainViewModel

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val isDark by viewModel.isDarkMode.collectAsState()
            val authSessionState by viewModel.authSessionState.collectAsState()

            MyApplicationTheme(darkTheme = isDark) {
                when (val session = authSessionState) {
                    is AuthSessionState.Authorized -> {
                        key(session.user.id) {
                            MainAppScaffold(viewModel = viewModel)
                        }
                    }
                    else -> {
                        AuthScreen(viewModel = viewModel)
                    }
                }
            }
        }
    }
}
