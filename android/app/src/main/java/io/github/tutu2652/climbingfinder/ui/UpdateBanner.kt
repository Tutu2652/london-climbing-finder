package io.github.tutu2652.climbingfinder.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/** Shown above the tab bar when the website announces a newer release. */
@Composable
fun UpdateBanner() {
    val model = LocalModel.current
    val ctx = LocalContext.current
    val u = model.update
    if (u == null || !model.showUpdateBanner) return
    Surface(color = lerp(MaterialTheme.colorScheme.surface, Brand, 0.16f), modifier = Modifier.fillMaxWidth()) {
        Row(
            Modifier.padding(start = 16.dp, end = 12.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text(u.versionName?.let { "Version $it is out" } ?: "A new version is out", fontWeight = FontWeight.SemiBold)
                Text("Install it over this one; your logbook stays.", style = MaterialTheme.typography.bodySmall)
            }
            TextButton(onClick = { model.dismissUpdate() }) { Text("Later") }
            Button(onClick = { openUrl(ctx, u.url) }) { Text("Update") }
        }
    }
}
