package com.chatroom.app.ui.components

import android.annotation.SuppressLint
import android.graphics.Color
import android.util.Base64
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
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

private const val MD_IT_VER = "14.1.0"
private const val KATEX_VER = "0.16.11"
private const val HLJS_VER = "11.10.0"

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun MarkdownContent(
    content: String,
    modifier: Modifier = Modifier
) {
    val isDark = isSystemInDarkTheme()
    val context = LocalContext.current
    var measuredHeight by remember { mutableIntStateOf(40) }

    val textColor = if (isDark) "#e0e0e0" else "#1a1a1a"
    val codeBg = if (isDark) "#2d2d2d" else "#f5f5f5"
    val linkColor = if (isDark) "#82aaff" else "#1976d2"
    val borderColor = if (isDark) "#444444" else "#e0e0e0"
    val quoteBg = if (isDark) "#2a2a2a" else "#f8f9fa"
    val thBg = if (isDark) "#2a2a2a" else "#f5f5f5"
    val trAlt = if (isDark) "#252525" else "#fafafa"

    // Encode content as base64 to avoid all escaping issues
    val b64 = Base64.encodeToString(content.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)

    val html = """
<!DOCTYPE html>
<html><head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width,initial-scale=1.0,maximum-scale=1.0,user-scalable=no">
<link rel="stylesheet" href="https://cdn.jsdelivr.net/npm/katex@$KATEX_VER/dist/katex.min.css">
<link rel="stylesheet" href="https://cdn.jsdelivr.net/gh/highlightjs/cdn-release@$HLJS_VER/build/styles/github.min.css">
<script src="https://cdn.jsdelivr.net/npm/markdown-it@$MD_IT_VER/dist/markdown-it.min.js"></script>
<script src="https://cdn.jsdelivr.net/npm/katex@$KATEX_VER/dist/katex.min.js"></script>
<script src="https://cdn.jsdelivr.net/gh/highlightjs/cdn-release@$HLJS_VER/build/highlight.min.js"></script>
<style>
*{box-sizing:border-box;margin:0;padding:0}
body{margin:0;padding:8px;font-size:15px;line-height:1.6;background:transparent;color:$textColor;font-family:-apple-system,BlinkMacSystemFont,"Segoe UI",Roboto,sans-serif;overflow-wrap:break-word;word-break:break-word}
a{color:$linkColor;text-decoration:none}
a:hover{text-decoration:underline}
p{margin:4px 0}
h1{margin:12px 0 6px;font-size:1.5em;border-bottom:1px solid $borderColor;padding-bottom:4px}
h2{margin:10px 0 5px;font-size:1.3em;border-bottom:1px solid $borderColor;padding-bottom:3px}
h3{margin:8px 0 4px;font-size:1.15em}
h4,h5,h6{margin:6px 0 3px}
ul,ol{margin:4px 0;padding-left:24px}
li{margin:2px 0}
blockquote{margin:4px 0;padding:4px 12px;border-left:4px solid $linkColor;background:$quoteBg;border-radius:0 4px 4px 0}
code{font-family:"SF Mono",Consolas,"Liberation Mono",Menlo,monospace;font-size:0.9em;padding:1px 4px;border-radius:3px;background:$codeBg}
pre{margin:6px 0;border-radius:8px;overflow-x:auto;background:$codeBg;border:1px solid $borderColor}
pre code{padding:0;background:none;font-size:0.85em}
pre .hljs{padding:12px;background:transparent}
table{border-collapse:collapse;margin:6px 0;width:100%;font-size:0.9em}
th,td{border:1px solid $borderColor;padding:6px 10px;text-align:left}
th{background:$thBg;font-weight:600}
tr:nth-child(even){background:$trAlt}
img{max-width:100%;border-radius:8px;margin:4px 0}
hr{margin:12px 0;border:none;border-top:1px solid $borderColor}
input[type="checkbox"]{margin-right:6px;transform:scale(1.1)}
.katex{font-size:1.05em}
</style>
</head><body>
<div id="content"></div>
<script>
var src = atob("$b64");
var md = window.markdownit({
  html: true, linkify: true, typographer: true, breaks: true,
  highlight: function(str, lang){
    if(lang && hljs.getLanguage(lang)){
      try{return '<pre><code class="hljs">'+hljs.highlight(str,{language:lang,ignoreIllegals:true}).value+'</code></pre>';}
      catch(e){}
    }
    return '<pre><code>'+md.utils.escapeHtml(str)+'</code></pre>';
  }
});
// Inline math $$ and $ via text token override
var defaultText = md.renderer.rules.text || function(t,e,n,s){return t.content};
md.renderer.rules.text = function(tokens, idx, options, env, self) {
  var c = tokens[idx].content;
  c = c.replace(/\$\$(.+?)\$\$/g, function(m,g){try{return katex.renderToString(g.trim(),{throwOnError:false,displayMode:true})}catch(e){return g}});
  c = c.replace(/\$(.+?)\$/g, function(m,g){try{return katex.renderToString(g,{throwOnError:false,displayMode:false})}catch(e){return g}});
  return c;
};
// Math fence ```math
var origFence = md.renderer.rules.fence || function(t,e,n,s){return ''};
md.renderer.rules.fence = function(tokens, idx, options, env, self) {
  var t = tokens[idx], info = (t.info||'').trim().toLowerCase();
  if(info==='math'||info==='latex'){
    try{return '<div style="text-align:center;padding:8px 0">'+katex.renderToString(t.content.trim(),{throwOnError:false,displayMode:true})+'</div>';}
    catch(e){return '<pre><code>'+md.utils.escapeHtml(t.content)+'</code></pre>';}
  }
  return origFence(tokens,idx,options,env,self);
};
document.getElementById('content').innerHTML = md.render(src);
</script>
</body></html>
""".trimIndent()

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
                        if (h != null && h in 20..5000) measuredHeight = h
                    }
                }
            }
            webChromeClient = WebChromeClient()
            loadDataWithBaseURL(null, html, "text/html", "UTF-8", null)
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
    ) {
        AndroidView(
            factory = { webView },
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 40.dp, max = 5000.dp)
        )
    }
}
