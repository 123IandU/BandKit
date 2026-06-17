import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.miband.app.App

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "Miband",
    ) {
        App()
    }
}
