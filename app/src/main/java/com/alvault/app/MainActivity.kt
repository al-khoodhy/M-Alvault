package com.alvault.app

import android.os.Bundle
import android.view.View
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.fragment.app.FragmentActivity
import com.alvault.app.ui.AlVaultRoot
import com.alvault.app.ui.theme.AlVaultTheme

/** [FragmentActivity] (bukan ComponentActivity biasa) karena BiometricPrompt membutuhkannya. */
class MainActivity : FragmentActivity() {

    private val container: AppContainer
        get() = (application as AlVaultApp).container

    override fun onCreate(savedInstanceState: Bundle?) {
        // Anti-screen capture: diset sebelum konten apa pun dibuat. Juga menyembunyikan isi di daftar Recents.
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AlVaultTheme {
                AlVaultRoot(container = container, activity = this)
            }
        }
        // ZKA: cegah layanan autofill sistem menawarkan menyimpan master password ke luar aplikasi.
        window.decorView.importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS
    }

    /** Setiap sentuhan/tombol menunda auto-lock karena idle. */
    override fun onUserInteraction() {
        super.onUserInteraction()
        container.session.onUserInteraction()
    }
}
