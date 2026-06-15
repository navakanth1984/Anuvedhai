package com.example

import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.TranslationScreen
import com.example.ui.TranslationViewModel
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {
  private var translationViewModel: TranslationViewModel? = null

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    setContent {
      val viewModel: TranslationViewModel = viewModel()
      translationViewModel = viewModel
      val darkThemeConfig by viewModel.darkThemeConfig.collectAsState()
      val useDarkTheme = when (darkThemeConfig) {
        "light" -> false
        "dark" -> true
        else -> isSystemInDarkTheme()
      }
      MyApplicationTheme(darkTheme = useDarkTheme) {
        TranslationScreen(viewModel = viewModel)
      }
    }
  }

  override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
    val vm = translationViewModel ?: return super.onKeyUp(keyCode, event)

    // Trigger record: Ctrl+R or Alt+R
    val isRecordShortcut = (keyCode == KeyEvent.KEYCODE_R && (event.isCtrlPressed || event.isAltPressed))
    if (isRecordShortcut) {
      handleGlobalRecordShortcut()
      return true
    }

    // Clear chat history: Ctrl+L or Alt+C
    val isClearShortcut = (keyCode == KeyEvent.KEYCODE_L && event.isCtrlPressed) ||
                          (keyCode == KeyEvent.KEYCODE_C && event.isAltPressed)
    if (isClearShortcut) {
      vm.clearTurnsInCurrentConversation()
      return true
    }

    return super.onKeyUp(keyCode, event)
  }

  private fun handleGlobalRecordShortcut() {
    val vm = translationViewModel ?: return
    val hasPermission = androidx.core.content.ContextCompat.checkSelfPermission(
      this,
      android.Manifest.permission.RECORD_AUDIO
    ) == android.content.pm.PackageManager.PERMISSION_GRANTED

    if (hasPermission || vm.isRecording.value) {
      vm.toggleRecording()
    } else {
      androidx.core.app.ActivityCompat.requestPermissions(
        this,
        arrayOf(android.Manifest.permission.RECORD_AUDIO),
        RECORD_AUDIO_REQUEST_CODE
      )
    }
  }

  override fun onRequestPermissionsResult(
    requestCode: Int,
    permissions: Array<String>,
    grantResults: IntArray
  ) {
    super.onRequestPermissionsResult(requestCode, permissions, grantResults)
    if (requestCode == RECORD_AUDIO_REQUEST_CODE) {
      if (grantResults.isNotEmpty() && grantResults[0] == android.content.pm.PackageManager.PERMISSION_GRANTED) {
        translationViewModel?.toggleRecording()
      }
    }
  }

  companion object {
    private const val RECORD_AUDIO_REQUEST_CODE = 1001
  }
}
