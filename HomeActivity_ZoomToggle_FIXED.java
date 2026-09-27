package com.mistahub.htplayer;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ActivityInfo;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.WindowManager;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public class HomeActivity extends AppCompatActivity {

    private BackgroundWebView webView;
    private FrameLayout fullscreenContainer;
    private View mCustomView;
    private WebChromeClient.CustomViewCallback mCustomViewCallback;
    private int mOriginalSystemUiVisibility;
    private int mOriginalOrientation;

    private final BroadcastReceiver mediaControlReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String command = intent.getStringExtra("command");

            if (command != null && webView != null) {
                switch (command) {
                    case BackgroundMediaService.ACTION_PLAY:
                        webView.evaluateJavascript("javascript:androidPlay();", null);
                        break;

                    case BackgroundMediaService.ACTION_PAUSE:
                        webView.evaluateJavascript("javascript:androidPause();", null);
                        break;
                }
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);

        WindowInsetsControllerCompat controller =
                WindowCompat.getInsetsController(
                        getWindow(),
                        getWindow().getDecorView()
                );

        if (controller != null) {
            controller.hide(WindowInsetsCompat.Type.systemBars());
            controller.setSystemBarsBehavior(
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            );
        }

        setContentView(R.layout.home);

        getWindow().addFlags(
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
        );

        webView = findViewById(R.id.webview1);
        fullscreenContainer = findViewById(R.id.fullscreen_container);

        setupWebView();

        String videoId = getIntent().getStringExtra("Id");

        if (videoId == null || videoId.isEmpty()) {
            videoId = getIntent().getStringExtra("id");
        }

        if (videoId == null || videoId.isEmpty()) {
            videoId = "dQw4w9WgXcQ";
        }

        loadCustomHtmlPlayer(videoId);
        startBackgroundMediaService();
        fetchVideoDetailsFromOembed(videoId);

        IntentFilter filter = new IntentFilter("MEDIA_CONTROL");

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(
                    mediaControlReceiver,
                    filter,
                    Context.RECEIVER_NOT_EXPORTED
            );
        } else {
            registerReceiver(mediaControlReceiver, filter);
        }
    }

    private void fetchVideoDetailsFromOembed(String videoId) {
        new Thread(() -> {
            try {
                URL url = new URL(
                        "https://www.youtube.com/oembed?url=https://www.youtube.com/watch?v="
                                + videoId
                                + "&format=json"
                );

                HttpURLConnection conn =
                        (HttpURLConnection) url.openConnection();

                conn.setConnectTimeout(10000);
                conn.setReadTimeout(10000);

                BufferedReader reader =
                        new BufferedReader(
                                new InputStreamReader(conn.getInputStream())
                        );

                StringBuilder response = new StringBuilder();
                String line;

                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }

                reader.close();

                String json = response.toString();

                String title =
                        json.split("\"title\":\"")[1].split("\"")[0];

                String author =
                        json.split("\"author_name\":\"")[1].split("\"")[0];

                Intent intent =
                        new Intent(
                                HomeActivity.this,
                                BackgroundMediaService.class
                        );

                intent.setAction(
                        BackgroundMediaService.ACTION_UPDATE_META
                );

                intent.putExtra("title", title);
                intent.putExtra("author", author);

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    startForegroundService(intent);
                } else {
                    startService(intent);
                }

            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }

    private void startBackgroundMediaService() {
        Intent serviceIntent =
                new Intent(
                        this,
                        BackgroundMediaService.class
                );

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent);
        } else {
            startService(serviceIntent);
        }
    }

    private void setupWebView() {
        WebSettings settings = webView.getSettings();

        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);

        webView.setWebViewClient(new WebViewClient());

        webView.setWebChromeClient(new WebChromeClient() {

            @Override
            public void onShowCustomView(
                    View view,
                    CustomViewCallback callback
            ) {
                if (mCustomView != null) {
                    callback.onCustomViewHidden();
                    return;
                }

                mCustomView = view;
                mCustomViewCallback = callback;

                mOriginalSystemUiVisibility =
                        getWindow()
                                .getDecorView()
                                .getSystemUiVisibility();

                mOriginalOrientation =
                        getRequestedOrientation();

                if (fullscreenContainer != null) {
                    fullscreenContainer.removeAllViews();

                    fullscreenContainer.addView(
                            mCustomView,
                            new FrameLayout.LayoutParams(
                                    -1,
                                    -1
                            )
                    );

                    fullscreenContainer.setVisibility(
                            View.VISIBLE
                    );

                    fullscreenContainer.bringToFront();
                }

                webView.setVisibility(View.GONE);

                getWindow()
                        .getDecorView()
                        .setSystemUiVisibility(
                                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                                        | View.SYSTEM_UI_FLAG_FULLSCREEN
                                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        );

                setRequestedOrientation(
                        ActivityInfo.SCREEN_ORIENTATION_SENSOR
                );
            }

            @Override
            public void onHideCustomView() {
                if (fullscreenContainer != null) {
                    fullscreenContainer.removeAllViews();
                    fullscreenContainer.setVisibility(View.GONE);
                }

                mCustomView = null;

                webView.setVisibility(View.VISIBLE);

                getWindow()
                        .getDecorView()
                        .setSystemUiVisibility(
                                mOriginalSystemUiVisibility
                        );

                setRequestedOrientation(
                        mOriginalOrientation
                );

                if (mCustomViewCallback != null) {
                    mCustomViewCallback.onCustomViewHidden();
                    mCustomViewCallback = null;
                }
            }
        });
    }

    private void loadCustomHtmlPlayer(String videoId) {

        String htmlCode =
                "<!DOCTYPE html>\n" +
                "<html lang=\"en\">\n" +
                "<head>\n" +
                "<meta charset=\"UTF-8\">\n" +
                "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no\">\n" +
                "<title>Mista Player</title>\n" +

                "<style>\n" +

                "*{margin:0;padding:0;box-sizing:border-box;}\n" +

                "html,body{\n" +
                "width:100%;\n" +
                "height:100%;\n" +
                "background:#000;\n" +
                "overflow:hidden;\n" +
                "}\n" +

                "#player-stage{\n" +
                "position:relative;\n" +
                "width:100vw;\n" +
                "height:100vh;\n" +
                "background:#000;\n" +
                "overflow:hidden;\n" +
                "display:flex;\n" +
                "align-items:center;\n" +
                "justify-content:center;\n" +
                "}\n" +

                "/* NORMAL: TRUE 16:9 */\n" +
                ".mista-embed{\n" +
                "position:relative;\n" +
                "width:100vw;\n" +
                "height:56.25vw;\n" +
                "max-width:177.7778vh;\n" +
                "max-height:100vh;\n" +
                "background:#000;\n" +
                "overflow:hidden;\n" +
                "flex-shrink:0;\n" +
                "}\n" +

                ".mista-embed iframe{\n" +
                "position:absolute !important;\n" +
                "left:0 !important;\n" +
                "top:0 !important;\n" +
                "width:100% !important;\n" +
                "height:100% !important;\n" +
                "border:0 !important;\n" +
                "}\n" +

                "/* ZOOM: container fills screen */\n" +
                "#player-stage.zoomed .mista-embed{\n" +
                "position:absolute;\n" +
                "left:0;\n" +
                "top:0;\n" +
                "width:100vw;\n" +
                "height:100vh;\n" +
                "max-width:none;\n" +
                "max-height:none;\n" +
                "overflow:hidden;\n" +
                "}\n" +

                "/* LANDSCAPE: 16:9 video fills width, crops top/bottom */\n" +
                "@media (orientation:landscape){\n" +
                "#player-stage.zoomed .mista-embed iframe{\n" +
                "position:absolute !important;\n" +
                "width:100vw !important;\n" +
                "height:56.25vw !important;\n" +
                "max-width:none !important;\n" +
                "max-height:none !important;\n" +
                "left:50% !important;\n" +
                "top:50% !important;\n" +
                "transform:translate(-50%,-50%) !important;\n" +
                "}\n" +
                "}\n" +

                "/* PORTRAIT: 16:9 video fills height, crops sides */\n" +
                "@media (orientation:portrait){\n" +
                "#player-stage.zoomed .mista-embed iframe{\n" +
                "position:absolute !important;\n" +
                "width:177.7778vh !important;\n" +
                "height:100vh !important;\n" +
                "max-width:none !important;\n" +
                "max-height:none !important;\n" +
                "left:50% !important;\n" +
                "top:50% !important;\n" +
                "transform:translate(-50%,-50%) !important;\n" +
                "}\n" +
                "}\n" +

                "/* ZOOM BUTTON */\n" +
                "#zoom-button{\n" +
                "position:absolute;\n" +
                "right:14px;\n" +
                "bottom:14px;\n" +
                "width:46px;\n" +
                "height:46px;\n" +
                "border:0;\n" +
                "border-radius:50%;\n" +
                "background:rgba(0,0,0,.72);\n" +
                "color:#fff;\n" +
                "font-size:23px;\n" +
                "line-height:46px;\n" +
                "text-align:center;\n" +
                "padding:0;\n" +
                "z-index:999999;\n" +
                "-webkit-tap-highlight-color:transparent;\n" +
                "}\n" +

                "#zoom-button:active{transform:scale(.92);}\n" +

                "</style>\n" +
                "</head>\n" +

                "<body>\n" +

                "<div id=\"player-stage\">\n" +

                "<div class=\"mista-embed\" id=\"main-player\" data-vid=\"" +
                videoId +
                "\"></div>\n" +

                "<button id=\"zoom-button\" onclick=\"toggleZoom()\" aria-label=\"Zoom\">⤢</button>\n" +

                "</div>\n" +

                "<script src=\"https://sdmntprindiasocentral.oaiusercontent.com/files/00000000-e9ac-8211-a068-4b17c9897c39/raw?se=2026-09-27T09%3A26%3A38Z&sp=r&sv=2026-02-06&sr=b&scid=6b049a56-953d-4bd3-bbf1-d8e5c54c1066&skoid=5bfb38a5-43fb-4c63-80a1-6ae1e97e2e16&sktid=a48cca56-e6da-484e-a814-9c849652bcb3&skt=2026-09-27T08%3A58%3A19Z&ske=2026-09-28T08%3A58%3A19Z&sks=b&skv=2026-02-06&sig=NvD5yXeTDOU6mw9vPbiNpwMglPSosZjVRWxrm41xl38%3D\"></script>\n" +

                "<script>\n" +

                "var mistaZoomed=false;\n" +

                "function toggleZoom(){\n" +
                "var stage=document.getElementById('player-stage');\n" +
                "var button=document.getElementById('zoom-button');\n" +
                "if(!stage)return;\n" +
                "mistaZoomed=!mistaZoomed;\n" +
                "if(mistaZoomed){\n" +
                "stage.classList.add('zoomed');\n" +
                "if(button)button.innerHTML='⤡';\n" +
                "}else{\n" +
                "stage.classList.remove('zoomed');\n" +
                "if(button)button.innerHTML='⤢';\n" +
                "}\n" +
                "}\n" +

                "function androidPlay(){\n" +
                "var iframe=document.querySelector('iframe');\n" +
                "if(iframe){\n" +
                "iframe.contentWindow.postMessage('{\"event\":\"command\",\"func\":\"playVideo\",\"args\":\"\"}','*');\n" +
                "}\n" +
                "}\n" +

                "function androidPause(){\n" +
                "var iframe=document.querySelector('iframe');\n" +
                "if(iframe){\n" +
                "iframe.contentWindow.postMessage('{\"event\":\"command\",\"func\":\"pauseVideo\",\"args\":\"\"}','*');\n" +
                "}\n" +
                "}\n" +

                "</script>\n" +

                "</body>\n" +
                "</html>";

        webView.loadDataWithBaseURL(
                "https://mistafy.pages.dev/",
                htmlCode,
                "text/html",
                "UTF-8",
                null
        );
    }

    @Override
    public void onBackPressed() {

        if (mCustomView != null) {

            if (webView.getWebChromeClient() != null) {
                webView.getWebChromeClient()
                        .onHideCustomView();
            }

        } else if (webView != null && webView.canGoBack()) {

            webView.goBack();

        } else {

            super.onBackPressed();
        }
    }

    @Override
    protected void onDestroy() {

        try {
            unregisterReceiver(mediaControlReceiver);
        } catch (Exception ignored) {
        }

        stopService(
                new Intent(
                        this,
                        BackgroundMediaService.class
                )
        );

        if (webView != null) {
            webView.stopLoading();
            webView.destroy();
        }

        super.onDestroy();
    }
}
