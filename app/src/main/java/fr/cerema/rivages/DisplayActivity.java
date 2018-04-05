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

        String langage = getString(R.string.langage);

        switch (langage) {
            case "en":
                webView.loadUrl("file:///android_asset/protocole_en.html");
                break;
            case "de":
                webView.loadUrl("file:///android_asset/protocole_de.html");
                break;
            default:
                webView.loadUrl("file:///android_asset/protocole_fr.html");
                break;
        }
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
