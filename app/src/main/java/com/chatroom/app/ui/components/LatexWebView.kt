package com.chatroom.app.ui.components

import android.annotation.SuppressLint
import android.graphics.Color
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView

private const val KATEX_VER = "0.16.11"

/**
 * Escape a string for use inside a JavaScript string literal (double-quoted).
 */
private fun escapeJs(raw: String): String = raw
    .replace("\\", "\\\\")
    .replace("\"", "\\\"")
    .replace("\n", "\\n")
    .replace("\r", "\\r")
    .replace("\t", "\\t")

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun LatexBlock(
    latex: String,
    modifier: Modifier = Modifier
) {
    val isDark = isSystemInDarkTheme()
    val context = LocalContext.current
    var measuredHeight by remember { mutableIntStateOf(80) }

    val html = remember(latex, isDark) {
        val jsLatex = escapeJs(latex)
        val color = if (isDark) "#e0e0e0" else "#1a1a1a"
        """
<!DOCTYPE html>
<html><head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width,initial-scale=1.0">
<link rel="stylesheet" href="https://cdn.jsdelivr.net/npm/katex@$KATEX_VER/dist/katex.min.css">
<script src="https://cdn.jsdelivr.net/npm/katex@$KATEX_VER/dist/katex.min.js"></script>
<style>
body{margin:0;padding:10px 8px;font-size:18px;background:transparent;color:$color;text-align:center}
.katex{font-size:1.1em}
</style></head><body>
<div id="c"></div>
<script>
try{
  katex.render("$jsLatex",document.getElementById('c'),{throwOnError:false,displayMode:true});
}catch(e){
  document.getElementById('c').innerHTML='<span style="color:red;font-size:14px">Render error</span>';
}
</script></body></html>
""".trimIndent()
    }

    val webView = remember {
        WebView(context).apply {
            setBackgroundColor(Color.TRANSPARENT)
            isVerticalScrollBarEnabled = false
            isHorizontalScrollBarEnabled = false
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.allowFileAccess = false
            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    view?.evaluateJavascript(
                        "document.body.scrollHeight + 'px'"
                    ) { result ->
                        val h = result.replace("\"", "").replace("px", "").trim().toIntOrNull()
                        if (h != null && h in 20..800) measuredHeight = h
                    }
                }
            }
            webChromeClient = WebChromeClient()
            loadDataWithBaseURL(null, html, "text/html", "UTF-8", null)
        }
    }

    val bg = if (isDark) MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.2f)
             else MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.15f)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(bg)
            .padding(4.dp)
    ) {
        AndroidView(
            factory = { webView },
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 60.dp, max = 400.dp)
        )
    }
}
