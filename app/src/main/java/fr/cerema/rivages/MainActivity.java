package fr.cerema.rivages;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Paint;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.provider.MediaStore;
import android.support.v4.content.ContextCompat;
import android.telephony.TelephonyManager;
import android.text.method.LinkMovementMethod;
import android.util.Log;
import android.view.View;
import android.webkit.PermissionRequest;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import com.androidnetworking.AndroidNetworking;
import com.androidnetworking.common.Priority;
import com.androidnetworking.error.ANError;
import com.androidnetworking.interfaces.JSONObjectRequestListener;

import net.gotev.uploadservice.MultipartUploadRequest;
import net.gotev.uploadservice.ServerResponse;
import net.gotev.uploadservice.UploadInfo;
import net.gotev.uploadservice.UploadNotificationConfig;
import net.gotev.uploadservice.UploadStatusDelegate;

import org.json.JSONObject;
import org.osmdroid.api.IMapController;
import org.osmdroid.events.MapAdapter;
import org.osmdroid.events.ScrollEvent;
import org.osmdroid.tileprovider.constants.OpenStreetMapTileProviderConstants;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.PathOverlay;
import org.osmdroid.views.overlay.ScaleBarOverlay;
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.Locale;
import java.util.Queue;
import java.util.concurrent.atomic.AtomicBoolean;

import me.leolin.shortcutbadger.ShortcutBadger;

import com.nabinbhandari.android.permissions.*;

// Christophe MOULIN - Cerema Med / DREC / SVGC - 23 juin 2016
// Version 0.3 - 31/08/2016
// Cette activité affiche une carte via l'API OSMDROID 5.1, ainsi que 4 boutons, et un ensemble de données concernant la géolocalisation
// Elle fait appel à un service en tache de fond, GpsService, et à un BroadCastReceiver, qui lui permet d'être informé de la disponibilité de nouvelles données à afficher

// Stéphane Zucatti - Cerema Med / SG / SII - 27 février 2018
// Version 1.93
// ADD: Post user data to https server
// ADD: lang in file
// ADD: dynamic versioning
// ADD: reminder to upload
// ADD: badge counter on some device

public class MainActivity extends Activity implements View.OnClickListener{



    // pour les journaux Log
    private final String TAG = this.getClass().getSimpleName();

    // déclaration paramètres

    private Context context;

    private TextView tv1, tv2, tv3, tv4, tv5, tv6, tv7, tvLink;
    private ImageButton btn0, btn1, btn1b, btn2, btn3, btnLogo;
    private Button btn8;

    private Dialog warningDialog;

    // données pour la carte OSMDROID
    private MapView mapView;
    private ArrayList<PathOverlay> paths;
    private IMapController mOsmvController;
    private MyLocationNewOverlay myLocationOverlay;

    private boolean isFirst=true, exiting, stopFollowing, takingPhoto;
    private AtomicBoolean myLocationOverlayExists ;

    private long timeStop ;

    private String mCurrentPhotoPath;

    private  SharedPreferences preferences ;

    private GpsService mGpsService;
    private boolean bound = false, hasToRestart=false;

    private static final int REQUEST_IMAGE_CAPTURE=1;
    private static final int  PICK_LOGO1 = 97;
    private static final int  PICK_LOGO2 = 98;
    private static final int  PICK_LOGO_KITKAT1 = 99;
    private static final int  PICK_LOGO_KITKAT2 = 100;



    // broadcast appelé chaque fois que le service déclare la disponibilité de nouvelles données ; l'affichage est alors mis à jour

    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();

            if (GpsService.DATA_AVAILABLE.equals(action)) {
                if (bound) upDateDisplay();
            }
        }
    };



    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // affichage de la vue principale
        setContentView(R.layout.activity_main);



        context = this ;


        // initialisation du tableau paths, ou sont stockés les segments rouges affichés sur la cartes
        paths = new ArrayList<>();

        // chargement des preférences
        preferences = PreferenceManager.getDefaultSharedPreferences(context);

        // initialisation des widgets
        tv1 = (TextView) findViewById(R.id.tv_main_1);
        tv2 = (TextView) findViewById(R.id.tv_main_2);
        tv3 = (TextView) findViewById(R.id.tv_main_3);
        tv4 = (TextView) findViewById(R.id.tv_main_4);
        tv5 = (TextView) findViewById(R.id.tv_main_5);
        tv6 = (TextView) findViewById(R.id.tv_main_6);
        tv7 = (TextView) findViewById(R.id.tv_main_7);
        tvLink=(TextView)findViewById(R.id.tv_osm_link);
        btn0 = (ImageButton) findViewById(R.id.btn_main_0);
        btn1 = (ImageButton) findViewById(R.id.btn_main_1);
        btn1b = (ImageButton) findViewById(R.id.btn_main_1b);
        btn2 = (ImageButton) findViewById(R.id.btn_main_2);
        btn3 = (ImageButton) findViewById(R.id.btn_main_3);
        btnLogo = (ImageButton) findViewById(R.id.btn_main_4);
        btn8 = (Button) findViewById(R.id.btn_main_8);
        mapView = (MapView) findViewById(R.id.mv_compteur);

        // écouteurs
        btn0.setOnClickListener(this);
        btn1.setOnClickListener(this);
        btn1b.setOnClickListener(this);
        btn2.setOnClickListener(this);
        btn3.setOnClickListener(this);
        btnLogo.setOnClickListener(this);
        btn8.setOnClickListener(this);

        String[] permissions = {android.Manifest.permission.CAMERA, android.Manifest.permission.WRITE_EXTERNAL_STORAGE, android.Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.READ_PHONE_STATE,Manifest.permission.INTERNET};
        Permissions.check(this/*context*/, permissions, null/*rationale*/, null/*options*/, new PermissionHandler() {
            @Override
            public void onGranted() {
                // do your task.


                // OSMdroid MapView

                // enregistrement de l'application auprès de OSMDROID
                OpenStreetMapTileProviderConstants.setUserAgentValue(BuildConfig.APPLICATION_ID);


                // nécessite la désactivation de l'accélération graphique matérielle à partir de Honeycomb
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
                    mapView.setLayerType(View.LAYER_TYPE_SOFTWARE, null);
                }

                mOsmvController = mapView.getController();

                // fonds de plan MAPNIK
                mapView.setTileSource(TileSourceFactory.MAPNIK);

                // permet le zoom à deux doigts
                mapView.setBuiltInZoomControls(false);
                mapView.setMultiTouchControls(true);

                // centrer sur la France
                mOsmvController.setZoom(6);
                GeoPoint startingPoint = new GeoPoint(46177548, 2639465);
                mOsmvController.setCenter(startingPoint);

                // échelle graphique
                ScaleBarOverlay myScaleBarOverlay = new ScaleBarOverlay(mapView);
                mapView.getOverlays().add(myScaleBarOverlay);

                // listener
                mapView.setMapListener(new MapAdapter() {
                    @Override
                    public boolean onScroll(ScrollEvent event) {
                        stopFollowing = true ;
                        timeStop = System.currentTimeMillis();
                        return super.onScroll(event);
                    }
                });

                myLocationOverlayExists = new AtomicBoolean(false); // ajouté à V0.3 pour gérer l'affichage de la position



                //lien internet OSM
                tvLink.setMovementMethod(LinkMovementMethod.getInstance());

                // Démarrage service en tache de fond, démarré et lié (accés aux méthodes du service)
                startAndBindService();


// Avertissement sur la consommation de batterie - affiché 5 fois
                int numberWarnings = preferences.getInt("NOMBRE_DIALOG", 0);
                prepareWarningDialog();
                if (numberWarnings<5) {
                    SharedPreferences.Editor editor = preferences.edit();
                    editor.putInt("NOMBRE_DIALOG", numberWarnings+1);
                    editor.apply();
                    warningDialog();
                }

                // Afficher le logo si présent
                String path = preferences.getString("LOGOPATH", "") ;
                if (path.length()>5) {
                    int factor = (getResources().getDimensionPixelSize(R.dimen.picture));
                    btnLogo.setImageBitmap(decodeSampledBitmap(path, factor, factor));
                    btnLogo.setVisibility(View.VISIBLE);
                    Log.i(TAG, "setting btnLogo");
                }





            }
        });

// display badge on some device that support it!

        int badgeCount = 0;
        String _path = Environment.getExternalStorageDirectory().toString()+"/Documents";
        File directory = new File(_path);
        File[] files = directory.listFiles();
        if (files!=null) {
            for (int i = 0; i < files.length; i++) {
                final File currentfile = files[i];
                if (currentfile.getName().indexOf("rivages_") > -1) {
                    badgeCount++;
                }
            }
        }

        boolean success = ShortcutBadger.applyCount(MainActivity.this, badgeCount);

        // display a reminder

        if (badgeCount>0) {

            SharedPreferences prefs = PreferenceManager
                    .getDefaultSharedPreferences(MainActivity.this);
            boolean dialog_status = prefs
                    .getBoolean("dialog_status", false);
            if (!dialog_status) {

                View checkBoxView = View.inflate(this, R.layout.checkbox, null);
                CheckBox checkBox = (CheckBox) checkBoxView.findViewById(R.id.checkbox);
                int id = Resources.getSystem().getIdentifier("btn_check_holo_light", "drawable", "android");
                checkBox.setButtonDrawable(id);

                checkBox.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                    @Override
                    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {

                        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(MainActivity.this);
                        SharedPreferences.Editor editor = prefs.edit();
                        editor.putBoolean("dialog_status", isChecked);

                    }
                });

                checkBox.setText(getString(R.string.logo_ask));

                AlertDialog.Builder builder = new AlertDialog.Builder(context);
                builder.setTitle("Informations")
                        .setMessage(getString(R.string.reminder).replace("%n", String.valueOf(badgeCount)))
                        .setView(checkBoxView)
                        .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                dialog.dismiss();
                            }
                        });
                Dialog dialog = builder.create();
                dialog.show();


            }


        }


    }


    private void startAndBindService(){
        Intent mIntent = new Intent(MainActivity.this, GpsService.class);
        startService(mIntent);
        bindService(mIntent, mConnection, Context.BIND_AUTO_CREATE);
    }


    @Override
    protected void onStart() {
        super.onStart();



        takingPhoto=false;

        if(hasToRestart){
            hasToRestart=false;

        }

        // enregistrer le broadcast receiver dès apparition de l'affichage
        registerReceiver(mReceiver, makeGpsServiceIntentFilter());

        // ajouter les points enregistrés pendant que l'activité n'était plus visible
        addLastPoints();
    }


    @Override
    protected void onStop() {
        super.onStop();

        //if (warningDialog.isShowing()) warningDialog.dismiss();

        if (bound && !mGpsService.hasStarted() && !exiting && !takingPhoto) {
            mGpsService.exit();
            Log.v(TAG, "onStop - unbindService");
            unbindService(mConnection);
            hasToRestart=true;
        }

        // stopper le broadcast à la disparition de l'affichage
        unregisterReceiver(mReceiver);
    }


    @Override
    protected void onDestroy() {
        Log.i(TAG, "onDestroy");
        super.onDestroy();
    }


    public void upDateDisplay() {

        // mise à jour des textes sur les données GPS
        tv1.setText(mGpsService.getDataString(GpsService.LATITUDE));
        tv2.setText(mGpsService.getDataString(GpsService.LONGITUDE));
        tv3.setText(mGpsService.getDataString(GpsService.ACCURACY));
        tv4.setText(mGpsService.getDataString(GpsService.NUMBER_OF_SATELLITE));
        tv5.setText(mGpsService.getDataString(GpsService.NUMBER_OF_SEGMENT));
        tv6.setText(mGpsService.getDataString(GpsService.NUMBER_OF_POINT));
        tv7.setText(mGpsService.getDataString(GpsService.LIMIT_TYPE));

        // Affichage du bouton de choix des types de limites, si pas déjà affiché
        if (btn8.getVisibility()!=View.VISIBLE) btn8.setVisibility(View.VISIBLE);

        // mise à jour de la carte, en ajoutant les derniers points, et en animant la carte sur la position actuelle
        if (mGpsService.hasFix() && mGpsService.getLatE6()!=0) {
            if (isFirst) {mOsmvController.setZoom(16); isFirst=false;}
            if (mGpsService.isRecording()){
                addLastPoints();
            }
            if (stopFollowing && System.currentTimeMillis()>timeStop+30000) stopFollowing = false ;
            if (!stopFollowing) mOsmvController.animateTo(new GeoPoint(mGpsService.getLatE6(), mGpsService.getLonE6()));
        }
    }


    public void addLastPoints() {

        // la méthode getmListOfGeoPoint() permet de récupérer les derniers points, souvent un seul
        // mais éventuellement tout ceux enregistrés pendant que l'application n'était plus au premier plan

        if (bound && mGpsService.getmListOfGeoPoint().size() > 0 && paths.size()>0) {

            // une copie de la queue est créée pour éviter une exception due à la concurrence des données
            Queue<GeoPoint> geoPoints = new LinkedList<>(mGpsService.getmListOfGeoPoint());

            // ajout au dernier chemin graphique des points de la queue
            for (GeoPoint gp : geoPoints) {
                paths.get(paths.size() - 1).addPoint(mGpsService.getmListOfGeoPoint().poll());
            }

            mapView.invalidate(); // mise à jour de la carte (peut-être pas nécessaire)
        }
    }


    public void onClick(View v) {

        switch (v.getId()) {

            case R.id.btn_main_0:
                displayInfo();
                break;

            case R.id.btn_main_1:
                if (bound) {
                    if (!mGpsService.isRecording()) startRecord();
                    else stopRecord();
                }
                break;

            case R.id.btn_main_1b:
                takePhoto();
                break;

            case R.id.btn_main_2:
                send();
                break;

            case R.id.btn_main_3:
                exit();
                break;

            case R.id.btn_main_4:
                logoDialog(false);
                break;

            case R.id.btn_main_8:
                typeTraitDialog();
                break;
        }
    }

    private void displayInfo() {
        startActivity(new Intent(this, DisplayActivity.class));
    }


    private void startRecord() {
        Log.i(TAG, "startRecord");
        if (!isGpsOn()) {
            // si le gps n'est pas en route, on vérifie qu'il est disponible et actif
            createGpsDisabledAlert();
        }
        else {
            if (bound) {
                Log.i(TAG, "startRecord - bound = true");
                if (!myLocationOverlayExists.getAndSet(true)) {
                    // Modification v0.3
                    // La position ne s'affichait pas sur certains appareils
                    // lorsque le GPS n'était pas en route lors du démarrage
                    // On s'assure ici que le GPS fonctionne pour
                    // représenter la position actuelle et l'imprécision
                    myLocationOverlay = new MyLocationNewOverlay(mapView);
                    myLocationOverlay.enableMyLocation();
                    myLocationOverlay.enableFollowLocation();
                    myLocationOverlay.setDrawAccuracyEnabled(true);
                    mapView.getOverlays().add(myLocationOverlay);
                }
                // démarrage de l'enregistrement dans le service
                mGpsService.startRecord();
                // changer play en pause sur bouton 1
                btn1.setImageResource(R.drawable.ic_pause_white_48dp);
                Toast.makeText(context, getString(R.string.start), Toast.LENGTH_SHORT).show();
                // ajout d'un nouveau segment graphique
                paths.add(new PathOverlay(Color.RED, this));
                mapView.getOverlays().add(paths.get(paths.size()-1));
                // définir la largeur du trait de la trace à 3dp en fonction des appareils
                int routeWidth = getResources().getDimensionPixelSize(R.dimen.route_width);
                Paint pPaint = paths.get(paths.size()-1).getPaint();
                pPaint.setStrokeWidth(routeWidth);
                paths.get(paths.size()-1).setPaint(pPaint);
                // ajout d'un premier point correspondant à la position actuelle au segment graphique
                if (mGpsService.getLatE6()!=0) paths.get(paths.size()-1).addPoint(new GeoPoint(mGpsService.getLatE6(), mGpsService.getLonE6()));
            }
        }
    }


    private void stopRecord() {
        if (bound) {
            // arrêt du segment actuel dans le service
            mGpsService.stopRecord();
            // changer pause en play
            btn1.setImageResource(R.drawable.ic_play_arrow_white_48dp);
            Toast.makeText(context, getString(R.string.pause), Toast.LENGTH_SHORT).show(); // ajout de feedback
        }
    }


    private void takePhoto() {

        // vérifie que l'appareil dispose d'un appareil photo
        if (getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA)) {

            // création d'une intention implicite visant à utiliser un appareil photo - si plusieurs sont installés
            // le choix sera proposé à l'utilisateur
            Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);

            // vérifie qu'une activité peut prendre en charge la prise de photo
            if (takePictureIntent.resolveActivity(getPackageManager()) != null) {

                // Création du fichier devant accueillir la photo
                File photoFile = null;
                try {
                    photoFile = createImageFile();
                } catch (IOException ex) {
                    Log.e(TAG, "Erreur à la création du fichier photo", ex);
                }
                //Ne continue que si le fichier a été créé
                if (photoFile != null) {

                    // création de l'URI correspondant
                    Uri photoURI = Uri.fromFile(photoFile);

                    takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoURI);
                    takePictureIntent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

                    takingPhoto=true;

                    // lancement de l'appareil photo - le code REQUEST_IMAGE_CAPTURE permet de récupérer le résultat dans onActivityResult plus bas
                    startActivityForResult(takePictureIntent, REQUEST_IMAGE_CAPTURE);
                }
            }
        }
        // si l'appareil ne dispose pas d'appareil photo (rare!), affichage d'un toast - le fichier manifest n'oblige pas l'utilisateur
        // à disposer d'un appareil - il peut contribuer en utilisant la fonction d'enregistrement uniquement
        else Toast.makeText(context,getString(R.string.no_camera),Toast.LENGTH_LONG).show();
    }


    private File createImageFile() throws IOException {
        // Crée un nom de fichier pour la photo "brute"
        String timeStamp = String.format(Locale.FRANCE, "%d", System.currentTimeMillis());
        String imageFileName = "rivages_" + timeStamp ;
        // enregistrement dans le répertoire photo - si le répertoire n'est pas présent et qu'on arrive
        // pas à le créer, on enregistre sur un répertoire propre à l'application
        File storageDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM);
        if (!storageDir.exists()) {
            if (!storageDir.mkdirs()) storageDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES);
        }
        File image = File.createTempFile(
                imageFileName,  /* prefix */
                ".jpg",         /* suffix */
                storageDir      /* directory */
        );

        // Enregistrement du nom pour pouvoir l'ajouter au retour dans onActivityResult en cas de succés
        mCurrentPhotoPath = image.getAbsolutePath();
        return image;
    }

    private void send() {
        // envoi des données
        if (bound) {

            if (mGpsService.getNumberOfPoint()>1) {
                exiting = true;
                Log.i(TAG, "sending - ask for logo : "+(mGpsService.hasPhoto() && preferences.getString("LOGOPATH", "").length()<5));
                // proposer de choisir un logo si une photo est présente sans vrai chemin pour le logo
                if (mGpsService.hasPhoto() && preferences.getString("LOGOPATH", "").length()<5 && !preferences.getBoolean("ASKLOGO", false)) {
                    logoDialog(true);
                }
                else {
                    try {
                    afterSend();
                    } catch(Exception e) {

                    };
                }
            }
            else {
                exiting = true;
                try {
                afterSend();} catch(Exception e) {

                };
            }
        }
    }


    private void afterSend() throws Exception {
        Log.i(TAG, "afterSend");
        mGpsService.send();  // méthode envoie du service
        // messages de sortie et de remerciement
        //Toast.makeText(context, getString(R.string.thank_text1), Toast.LENGTH_LONG).show();
        Toast.makeText(context, getString(R.string.thank_text2), Toast.LENGTH_LONG).show();
        // déconnexion du service
        Log.v(TAG, "afterSend - unbindService");
        try {
            unbindService(mConnection);
        } catch(Exception e) {

        };
        // rendre le bouton 2 insensible pour éviter de redéclencher la méthode par erreur (résultat incertain !)
        btn2.setOnClickListener(null);
        Log.v(TAG, "afterSend - finish");

        // display badge on some device that support it!

        int badgeCount = 0;
        String _path = Environment.getExternalStorageDirectory().toString()+"/Documents";
        File directory = new File(_path);
        File[] files = directory.listFiles();
        if (files!=null) {
            for (int i = 0; i < files.length; i++) {
                final File currentfile = files[i];
                if (currentfile.getName().indexOf("rivages_") > -1) {
                    badgeCount++;
                }
            }
        };

        boolean success = ShortcutBadger.applyCount(MainActivity.this, badgeCount);

        finish();
    }


    private void exit() {

        if (bound) {

            if (!mGpsService.hasStarted()) {
                exiting=true;
                // sortie sans enregistrer
                mGpsService.exit();
                Log.v(TAG, "exit - unbindService");
                unbindService(mConnection);
                Log.v(TAG, "exit - finish");
                finish();
            } else
                // boite de dialogue pour confirmer la sortie
                exitDialog();
        }
    }


    private void chooseLogo(boolean phot) {
        Log.i(TAG, "chooseLogo - phot="+phot);

        if (Build.VERSION.SDK_INT <19){
            Intent intent = new Intent();
            intent.setType("image/*");
            intent.setAction(Intent.ACTION_GET_CONTENT);
            startActivityForResult(Intent.createChooser(intent, getResources().getString(R.string.select_picture)),(phot)? PICK_LOGO1 : PICK_LOGO2);
        } else {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("image/*");
            startActivityForResult(intent, (phot)? PICK_LOGO_KITKAT1 : PICK_LOGO_KITKAT2);
        }
    }


    private void prepareWarningDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle(getText(R.string.warning_title))
                .setMessage(getText(R.string.warning_text))
                .setPositiveButton(getString(R.string.warning_yes), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                    }
                });
        warningDialog = builder.create();
    }


    private void warningDialog() {
        warningDialog.show();
    }


    private void exitDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle(getText(R.string.exit_title))
                .setMessage(getText(R.string.exit_text))
                .setPositiveButton(getString(R.string.exit_yes), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {

                        // sortie sans enregistrer
                        mGpsService.exit();
                        Log.v(TAG, "exitDialog - unbindService");
                        unbindService(mConnection);
                        dialog.dismiss();
                        Log.v(TAG, "exitDialog - finish");
                        finish();
                    }
                })
                .setNegativeButton(getString(R.string.exit_no), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                    }
                });
        Dialog dialog = builder.create();
        dialog.show();
    }


    private void logoDialog(final boolean hasPhoto) {
        Log.i(TAG, "logoDialog - hasPhoto="+hasPhoto);

        View checkBoxView = View.inflate(this, R.layout.checkbox, null);
        CheckBox checkBox = (CheckBox) checkBoxView.findViewById(R.id.checkbox);
        int id=Resources.getSystem().getIdentifier("btn_check_holo_light", "drawable", "android");
        checkBox.setButtonDrawable(id);

        checkBox.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                SharedPreferences.Editor editor = preferences.edit();
                editor.putBoolean("ASKLOGO", isChecked);
                editor.apply();
            }
        });

        checkBox.setText(getString(R.string.logo_ask));

        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle(getText(R.string.logo_title))
                .setMessage(String.format(Locale.getDefault(), "%1$s%2$s" ,
                        (hasPhoto)? (getText(R.string.logo_text1)+" ") : "",
                        getText(R.string.logo_text2)))
                .setView(checkBoxView)
                .setPositiveButton(getString(R.string.logo_yes), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        chooseLogo(hasPhoto);
                        dialog.dismiss();
                    }
                })
                .setNeutralButton(getString(R.string.logo_cancel), new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    dialog.dismiss();
                }
                })
                .setNegativeButton(getString(R.string.logo_no), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        SharedPreferences.Editor editor = preferences.edit();
                        editor.putString("LOGOPATH", "");
                        editor.apply();
                        btnLogo.setVisibility(View.INVISIBLE);
                        if (hasPhoto) {
                            try {
                                afterSend();
                            } catch(Exception e) {

                            }

                        }
                        dialog.dismiss();
                    }
                })
                ;
        Dialog dialog = builder.create();
        dialog.show();
    }


    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {

        Log.i(TAG, "onActivityResult - requestCode="+requestCode+" - resultCode="+resultCode+" - bound="+bound);

        if (requestCode == REQUEST_IMAGE_CAPTURE && resultCode == RESULT_OK && bound) {
                // réussite de la photo : on ajoute le nom de la photo pour la compresser plus tard et l'ajouter au zip
                Log.i(TAG, "addPhotoName - mCurrentPhotoPath="+mCurrentPhotoPath);
                mGpsService.addPhotoName(mCurrentPhotoPath);
                Toast.makeText(context, getString(R.string.photoAdded), Toast.LENGTH_SHORT).show();
            }

        if ( (requestCode == PICK_LOGO1 || requestCode == PICK_LOGO2
        || requestCode == PICK_LOGO_KITKAT1 || requestCode == PICK_LOGO_KITKAT2) && resultCode == RESULT_OK) {

            String filePath = "";

            if( (requestCode == PICK_LOGO_KITKAT1 || requestCode == PICK_LOGO_KITKAT2)){

                Uri originalUri = data.getData();

                final int takeFlags = data.getFlags()
                        & (Intent.FLAG_GRANT_READ_URI_PERMISSION
                        | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                // Check for the freshest data.
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                    //noinspection WrongConstant
                    getContentResolver().takePersistableUriPermission(originalUri, takeFlags);
                }

                /* now extract ID from Uri path using getLastPathSegment() and then split with ":"
                then call get Uri to for Internal storage or External storage for media I have used getUri()
                */

                String id = originalUri.getLastPathSegment().split(":")[1];
                final String[] imageColumns = {MediaStore.Images.Media.DATA };
                final String imageOrderBy = null;

                Uri uri = getUri();

                Cursor imageCursor = managedQuery(uri, imageColumns,
                        MediaStore.Images.Media._ID + "="+id, null, imageOrderBy);

                if (imageCursor.moveToFirst()) {
                    filePath = imageCursor.getString(imageCursor.getColumnIndex(MediaStore.Images.Media.DATA));
                }

            } else {
                // for older version use existing code here
                
            Uri selectedLogo = data.getData();

            Log.i(TAG, "reception du résultat du choix de logo : URI=" + selectedLogo.toString());

            String[] filePathColumn = {MediaStore.Images.Media.DATA};

            Cursor cursor = getContentResolver().query(
                    selectedLogo, filePathColumn, null, null, null);
            if (cursor != null) {
                cursor.moveToFirst();
                int columnIndex = 0;
                columnIndex = cursor.getColumnIndex(filePathColumn[0]);
                filePath = cursor.getString(columnIndex);
                cursor.close();
            }
            }
                Log.i(TAG, "logoPath="+filePath);
            
                // stockage du chemin du logo dans les préférences
                SharedPreferences.Editor editor = preferences.edit();
                editor.putString("LOGOPATH", filePath);
                editor.apply();

                // mettre le logo
                int logoSize = (getResources().getDimensionPixelSize(R.dimen.picture));
                btnLogo.setImageBitmap(decodeSampledBitmap(filePath, logoSize, logoSize));
                btnLogo.setVisibility(View.VISIBLE);
            }

            if (requestCode == PICK_LOGO1 || requestCode == PICK_LOGO_KITKAT1) {
            try {
                afterSend();
            } catch(Exception e) {

            }
            }


        if ( (requestCode == PICK_LOGO1 || requestCode == PICK_LOGO2 || requestCode == PICK_LOGO_KITKAT1 || requestCode == PICK_LOGO_KITKAT2) && resultCode != RESULT_OK) {
            Toast.makeText(context, getString(R.string.invalidLogo), Toast.LENGTH_LONG).show();
        }
    }

    // By using this method get the Uri of Internal/External Storage for Media
    private Uri getUri() {
        String state = Environment.getExternalStorageState();
        if(!state.equalsIgnoreCase(Environment.MEDIA_MOUNTED))
            return MediaStore.Images.Media.INTERNAL_CONTENT_URI;

        return MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
    }

    private ServiceConnection mConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName className,
                                       IBinder service) {
            Log.i(TAG, "on service connected");
            // appelé à la connexion du service à l'activité
            GpsService.LocalBinder binder = (GpsService.LocalBinder) service;
            mGpsService = binder.getService();

            if (mGpsService.isRecording()) btn1.setImageResource(R.drawable.ic_pause_white_48dp);
            if (isGpsOn() && !myLocationOverlayExists.getAndSet(true)) {
                // représenter la position actuelle et l'imprécision
                myLocationOverlay = new MyLocationNewOverlay(mapView);
                myLocationOverlay.enableMyLocation();
                myLocationOverlay.enableFollowLocation();
                myLocationOverlay.setDrawAccuracyEnabled(true);
                mapView.getOverlays().add(myLocationOverlay);
            }
            bound = true;
        }

        @Override
        public void onServiceDisconnected(ComponentName arg0) {
            bound = false;
        }
    };


    private boolean isGpsOn() {
        final LocationManager manager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        return manager.isProviderEnabled(LocationManager.GPS_PROVIDER);
    }


    private void createGpsDisabledAlert() {
        // si le gps n'est pas en route, propose à l'utilisateur de basculer sur les règlages du gps
        AlertDialog.Builder localBuilder = new AlertDialog.Builder(this);
        localBuilder
                .setMessage(getText(R.string.sort_alert_text))
                .setTitle(getText(R.string.sort_alert_title))
                .setCancelable(false)
                .setPositiveButton(getText(R.string.sort_alert_yes),
                        new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface paramDialogInterface, int paramInt) {
                                MainActivity.this.showGpsOptions();
                            }
                        }
                );
        localBuilder.setNegativeButton(getText(R.string.sort_alert_no),
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface paramDialogInterface, int paramInt) {
                        paramDialogInterface.cancel();
                    }
                }
        );
        localBuilder.create().show();
    }


    private void showGpsOptions() {        // renvoie sur le menu android du gps
        startActivity(new Intent("android.settings.LOCATION_SOURCE_SETTINGS"));
    }


    private void typeTraitDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle(getText(R.string.trait_title))
                .setItems(R.array.type, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                if (bound) {
                    mGpsService.setCurrentLim(which);
                    // stockage du chemin du logo dans les préférences
                    SharedPreferences.Editor editor = preferences.edit();
                    editor.putInt("TYPETRAIT", which);
                    editor.apply();
                    upDateDisplay();
                }
            }
        });
        Dialog dialog = builder.create();
        dialog.show();
    }


    private static IntentFilter makeGpsServiceIntentFilter() {
        // créé le filtre nécessaire au broadcast receiver
        final IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(GpsService.DATA_AVAILABLE);
        return intentFilter;
    }


    // Méthode de redimmensionnement de Bitmap pour le logo

    public static Bitmap decodeSampledBitmap(String path,
                                                         int reqWidth, int reqHeight) {

        // First decode with inJustDecodeBounds=true to check dimensions
        final BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(path, options);

        // Calculate inSampleSize
        options.inSampleSize = calculateInSampleSize(options, reqWidth, reqHeight);

        // Decode bitmap with inSampleSize set
        options.inJustDecodeBounds = false;
        return BitmapFactory.decodeFile(path, options);
    }



    public static int calculateInSampleSize(
            BitmapFactory.Options options, int reqWidth, int reqHeight) {
        // Raw height and width of image
        final int height = options.outHeight;
        final int width = options.outWidth;
        int inSampleSize = 1;

        if (height > reqHeight || width > reqWidth) {

            final int halfHeight = height / 2;
            final int halfWidth = width / 2;

            // Calculate the largest inSampleSize value that is a power of 2 and keeps both
            // height and width larger than the requested height and width.
            while ((halfHeight / inSampleSize) >= reqHeight
                    && (halfWidth / inSampleSize) >= reqWidth) {
                inSampleSize *= 2;
            }
        }
        return inSampleSize;
    }
}
