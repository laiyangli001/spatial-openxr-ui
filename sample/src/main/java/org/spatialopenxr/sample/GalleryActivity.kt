package org.spatialopenxr.sample
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import android.content.Intent
import com.moonlightxr.nativeui.*

class GalleryActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { SpatialTheme { Gallery() } }
    }
}
@Composable
fun Gallery() {
    val context = LocalContext.current
    var count by remember { mutableStateOf(0) }
    var checked by remember { mutableStateOf(true) }
    var value by remember { mutableStateOf(0.5f) }
    SpatialPanel(Modifier.fillMaxSize()) {
        Column(Modifier.padding(32.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("Spatial OpenXR UI — component gallery")
            Text("Android preview. Use the XR sample for spatial composition.")
            if (context is GalleryActivity) SpatialButton("Start OpenXR", {
                context.startActivity(Intent(context, XrActivity::class.java))
            })
            SpatialButton("Clicks: $count", { count++ })
            SpatialSwitch(checked, { checked = it })
            SpatialRangeSlider(value, { value = it })
            SpatialNumberField(count, { count = it }, range = 0..100)
            SpatialButton("Disabled", {}, enabled = false)
        }
    }
}
