package fr.cerema.rivages;

import android.app.Activity;
import android.os.Build;
import android.os.Bundle;
import android.view.KeyEvent;
import android.webkit.WebView;

/**
 * Created by chris on 30/06/2016.
 */
public class DisplayActivity extends Activity {

    private WebView webView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // affichage de la vue
        setContentView(R.layout.activity_display);

        webView = (WebView) findViewById(R.id.wv_display);

        webView.getSettings().setBuiltInZoomControls(true);

        if (getString(R.string.langage).equals("en")) webView.loadUrl("file:///android_asset/protocole_en.html");
        else webView.loadUrl("file:///android_asset/protocole_fr.html");
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            switch (keyCode) {
                case KeyEvent.KEYCODE_BACK:
                    if (webView.canGoBack()) {
                        webView.goBack();
                    } else {
                        finish();
                    }
                    return true;
            }
        }
        return super.onKeyDown(keyCode, event);
    }
}
