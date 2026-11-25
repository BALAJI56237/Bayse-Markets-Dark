package com.venice.baysemarket;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.view.View;
import android.webkit.*;
import android.widget.*;
import java.io.BufferedReader;
import java.io.InputStreamReader;

public class MainActivity extends Activity {

    public class ThemeBridge {
        @JavascriptInterface
        public void toggle() {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    darkMode = !darkMode;
                    getPreferences(0).edit()
                        .putBoolean("dark", darkMode).apply();
                    apply();
                }
            });
        }
    }

    private boolean darkMode = true;

    private static final String START_URL = "https://www.bayse.markets/";
    private WebView web;
    private ProgressBar bar;
    private View offline;
    private String css = "";

    @SuppressLint("SetJavaScriptEnabled")
    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_main);
        web = (WebView) findViewById(R.id.webView);
        bar = (ProgressBar) findViewById(R.id.progress);
        offline = findViewById(R.id.offline);
        css = readAsset("dark.css");

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        darkMode = getPreferences(0)
            .getBoolean("dark", true);
        web.addJavascriptInterface(
            new ThemeBridge(), "BayseTheme");
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setLoadWithOverviewMode(true);
        s.setUseWideViewPort(true);
        s.setJavaScriptCanOpenWindowsAutomatically(true);

        web.setBackgroundColor(0xFF0B0F14);
        CookieManager cm = CookieManager.getInstance();
        cm.setAcceptCookie(true);
        cm.setAcceptThirdPartyCookies(web, true);

        web.setWebViewClient(new WebViewClient() {
            @Override public void onPageStarted(WebView v, String u, Bitmap f) {
                bar.setVisibility(View.VISIBLE);
            }
            @Override public void onPageCommitVisible(WebView v, String u) {
                apply();
            }
            @Override public void onPageFinished(WebView v, String u) {
                bar.setVisibility(View.GONE);
                apply();
            }
            @Override public void doUpdateVisitedHistory(WebView v, String u, boolean r) {
                apply();
            }
            @Override public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest r) {
                String host = r.getUrl().getHost();
                if (host != null && (host.equals("bayse.markets") || host.endsWith(".bayse.markets"))) return false;
                startActivity(new Intent(Intent.ACTION_VIEW, r.getUrl()));
                return true;
            }
            @Override public void onReceivedError(WebView v, WebResourceRequest r, WebResourceError e) {
                if (r.isForMainFrame()) offline.setVisibility(View.VISIBLE);
            }
        });
        web.setWebChromeClient(new WebChromeClient());

        findViewById(R.id.retry).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { offline.setVisibility(View.GONE); web.reload(); }
        });

        if (b != null) web.restoreState(b); else web.loadUrl(START_URL);
    }

    private void apply() {
        String js;

        if (!darkMode) {
            js = "(function(){var s=document.getElementById('vn-dark');if(s)s.remove();})()";
        } else {
            js = "(function(){var st=document.getElementById('vn-dark');"
               + "if(!st){st=document.createElement('style');"
               + "st.id='vn-dark';document.documentElement.appendChild(st);}"
               + "st.textContent=" + org.json.JSONObject.quote(css) + ";})()";
        }

        web.evaluateJavascript(js, null);

        int bg = darkMode ? 0xFF0B0F14 : 0xFFFFFFFF;
        web.setBackgroundColor(bg);
        getWindow().setStatusBarColor(bg);
        getWindow().setNavigationBarColor(bg);

        getWindow().getDecorView().setSystemUiVisibility(
            darkMode ? 0 : 8192 | 16
        );

        injectThemeToggle();
        fixHeader();
    }

    private String readAsset(String name) {
        StringBuilder sb = new StringBuilder();
        try {
            BufferedReader r = new BufferedReader(new InputStreamReader(getAssets().open(name)));
            String line;
            while ((line = r.readLine()) != null) sb.append(line).append('\n');
            r.close();
        } catch (Exception e) { }
        return sb.toString();
    }



    private void fixHeader() {
        web.evaluateJavascript("\n(function(){\n    if(!document.body)return;\n\n    function isLight(value){\n        var rgb=value.match(/[\\d.]+/g);\n        if(!rgb || rgb.length<3)return false;\n\n        var r=+rgb[0],g=+rgb[1],b=+rgb[2];\n        var a=rgb.length>3?+rgb[3]:1;\n\n        return a>0.15 &&\n               Math.min(r,g,b)>190 &&\n               Math.max(r,g,b)-Math.min(r,g,b)<45;\n    }\n\n    function fix(){\n        if(!window.vnDark){\n            document.querySelectorAll('[data-vn-header]')\n                .forEach(function(el){\n                    el.removeAttribute('data-vn-header');\n                });\n            return;\n        }\n\n        document.querySelectorAll('body *').forEach(function(el){\n            if(el.matches('img,svg,svg *,video,canvas,picture,iframe'))\n                return;\n\n            if(el.hasAttribute('data-vn-header'))return;\n\n            var c=getComputedStyle(el);\n\n            if(isLight(c.backgroundColor)){\n                el.setAttribute('data-vn-header','true');\n            }\n        });\n    }\n\n    window.vnFixHeader=fix;\n\n    if(window.vnHeaderTimer)\n        clearInterval(window.vnHeaderTimer);\n\n    if(window.vnStableHeaderObserver)\n        window.vnStableHeaderObserver.disconnect();\n\n    if(window.vnUniversalObserver)\n        window.vnUniversalObserver.disconnect();\n\n    var pending=false;\n\n    function schedule(){\n        if(pending)return;\n        pending=true;\n\n        requestAnimationFrame(function(){\n            pending=false;\n            fix();\n        });\n    }\n\n    window.vnUniversalObserver=new MutationObserver(schedule);\n\n    window.vnUniversalObserver.observe(document.body,{\n        childList:true,\n        subtree:true,\n        attributes:true,\n        attributeFilter:['class','style']\n    });\n\n    window.vnHeaderTimer=setInterval(fix,2000);\n\n    fix();\n})();\n", null);
    }

    private void injectThemeToggle() {
        String js = "\n(function(){\n    if(!document.body)return; window.vnDark = DARK_STATE;\n\n    function mount(){\n        var old = document.getElementById('vn-theme-toggle');\n\n        if(old){\n            old.style.background = window.vnDark\n                ? '#252d38' : '#e5e7eb';\n            old.style.color = window.vnDark\n                ? '#ffffff' : '#334155';\n            old.children[0].style.opacity =\n                window.vnDark ? '.45' : '1';\n            old.children[1].style.opacity =\n                window.vnDark ? '1' : '.45';\n            return;\n        }\n\n        var items = Array.from(document.querySelectorAll(\n            'a,button,span,div'\n        ));\n\n        var portfolio = items.find(function(e){\n            return e.textContent.trim() === 'Portfolio'\n                && e.children.length === 0\n                && e.getBoundingClientRect().width > 0;\n        });\n\n        if(!portfolio) return;\n\n        var anchor = portfolio.closest('a,button')\n            || portfolio;\n\n        var toggle = document.createElement('button');\n        toggle.id = 'vn-theme-toggle';\n        toggle.type = 'button';\n        toggle.setAttribute('aria-label','Toggle dark mode');\n\n        toggle.style.cssText =\n            'display:inline-flex!important;' +\n            'align-items:center;' +\n            'justify-content:center;' +\n            'gap:7px;' +\n            'border:1px solid #64748b55;' +\n            'border-radius:24px;' +\n            'padding:6px 9px;' +\n            'margin-left:8px;' +\n            'vertical-align:middle;' +\n            'flex-shrink:0;' +\n            'font-size:16px;' +\n            'cursor:pointer;';\n\n        toggle.innerHTML =\n            '<span>\u2600</span><span>\u263e</span>';\n\n        toggle.onclick = function(e){\n            e.preventDefault();\n            e.stopPropagation();\n            BayseTheme.toggle();\n        };\n\n        anchor.insertAdjacentElement('afterend',toggle);\n        mount();\n    }\n\n    mount();\n\n    if(!window.vnThemeObserver){\n        window.vnThemeObserver =\n            new MutationObserver(mount);\n        window.vnThemeObserver.observe(\n            document.body,\n            {childList:true,subtree:true}\n        );\n    }\n})();\n";
        js = js.replace("DARK_STATE",
            darkMode ? "true" : "false");
        web.evaluateJavascript(js, null);
    }

    @Override public void onBackPressed() {
        if (web.canGoBack()) web.goBack(); else super.onBackPressed();
    }
    @Override protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        web.saveState(out);
    }
    @Override protected void onPause() { web.onPause(); super.onPause(); }
    @Override protected void onResume() { super.onResume(); web.onResume(); }
    @Override protected void onDestroy() { web.destroy(); super.onDestroy(); }
}
