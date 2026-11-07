package com.fazz.agent.presentation.components

import android.annotation.SuppressLint
import android.content.Intent
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.fazz.agent.domain.models.Artifact
import com.fazz.agent.domain.models.ArtifactType

private fun Artifact.kindLabel() = when (type) {
    ArtifactType.HTML -> "Halaman web"
    ArtifactType.SVG -> "Gambar SVG"
    ArtifactType.MARKDOWN -> "Dokumen"
    ArtifactType.CODE -> if (language.isBlank()) "Kode" else "Kode · $language"
}

/** The card that appears in the chat for a created artifact. Tap to open the viewer. */
@Composable
fun ArtifactCard(a: Artifact, onOpen: () -> Unit) {
    val c = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().clip20().border(1.dp, c.outline, RoundedCornerShape(18.dp)).background(c.surface).clickable(onClick = onOpen).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(40.dp).background(c.surfaceVariant, RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.Description, null, tint = c.secondary)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(a.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("${a.kindLabel()} · ketuk untuk membuka", style = MaterialTheme.typography.bodySmall, color = c.onSurfaceVariant)
        }
    }
}

private fun Modifier.clip20() = this.then(Modifier.clip(RoundedCornerShape(18.dp)))

/** Full-screen viewer: live preview for pages/SVG/markdown, and the source in a Code tab. */
@Composable
fun ArtifactViewer(a: Artifact, onClose: () -> Unit) {
    val c = MaterialTheme.colorScheme
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val previewable = a.type != ArtifactType.CODE
    var tab by rememberSaveable { mutableIntStateOf(0) }
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Surface(Modifier.fillMaxSize(), color = c.background) {
            Column(Modifier.fillMaxSize().padding(top = 28.dp)) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Tutup") }
                    Column(Modifier.weight(1f)) {
                        Text(a.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(a.kindLabel(), style = MaterialTheme.typography.bodySmall, color = c.onSurfaceVariant)
                    }
                    IconButton(onClick = { clipboard.setText(AnnotatedString(a.content)) }) { Icon(Icons.Rounded.ContentCopy, "Salin") }
                    IconButton(onClick = {
                        val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_SUBJECT, a.title).putExtra(Intent.EXTRA_TEXT, a.content)
                        context.startActivity(Intent.createChooser(send, "Bagikan").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    }) { Icon(Icons.Rounded.Share, "Bagikan") }
                }
                if (previewable) {
                    TabRow(selectedTabIndex = tab, containerColor = c.background, contentColor = c.secondary) {
                        Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Pratinjau") })
                        Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Kode") })
                    }
                }
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    if (previewable && tab == 0) Preview(a) else CodeView(a.content)
                }
            }
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun Preview(a: Artifact) {
    if (a.type == ArtifactType.MARKDOWN) {
        val c = MaterialTheme.colorScheme
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)) {
            Text(renderMarkdown(a.content, c.secondary, c.surfaceVariant), style = MaterialTheme.typography.bodyLarge, color = c.onBackground)
        }
        return
    }
    val html = if (a.type == ArtifactType.SVG) "<html><body style=\"margin:0;display:flex;justify-content:center;align-items:center;min-height:100vh;background:#fff\">${a.content}</body></html>" else a.content
    AndroidView(modifier = Modifier.fillMaxSize(), factory = { ctx ->
        WebView(ctx).apply {
            settings.javaScriptEnabled = true
            // The page is untrusted generated content: no local files, no app content.
            settings.allowFileAccess = false
            settings.allowContentAccess = false
            settings.cacheMode = WebSettings.LOAD_NO_CACHE
            settings.builtInZoomControls = true
            settings.displayZoomControls = false
            settings.useWideViewPort = true
            webViewClient = WebViewClient()
            loadDataWithBaseURL(null, html, "text/html", "utf-8", null)
        }
    })
}

@Composable
private fun CodeView(code: String) {
    val c = MaterialTheme.colorScheme
    SelectionContainer {
        Column(Modifier.fillMaxSize().background(c.surfaceVariant).verticalScroll(rememberScrollState()).horizontalScroll(rememberScrollState()).padding(16.dp)) {
            Text(code, fontFamily = FontFamily.Monospace, fontSize = 12.5.sp, lineHeight = 18.sp, color = c.onSurface)
        }
    }
}
