package fr.cerema.rivages;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;

import java.io.File;


public class Splash extends Activity {

    private final String TAG = this.getClass().getSimpleName();

    private Context context;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);

        // penser à effacer le répertoire tampon au lancement de l'appli (splash)

        context =this ;

        Handler x = new Handler();
        x.postDelayed(new splashhandler(), 4000);

        // Efface le cache de l'application ; le système peut également s'en charger si cela est nécessaire
        deleteCache(context);
    }

    class splashhandler implements Runnable {
        public void run() {
            startActivity(new Intent(getApplication(), MainActivity.class));
            finish();
        }
    }

    public static void deleteCache(Context context) {
        try {
            File dir = context.getCacheDir();
            if (dir != null && dir.isDirectory()) {
                deleteDir(dir);
            }
        } catch (Exception e) {}
    }

    public static boolean deleteDir(File dir) {
        if (dir != null && dir.isDirectory()) {
            String[] children = dir.list();
            for (int i = 0; i < children.length; i++) {
                boolean success = deleteDir(new File(dir, children[i]));
                if (!success) {
                    return false;
                }
            }
        }
        return dir != null && dir.delete();
    }
}
