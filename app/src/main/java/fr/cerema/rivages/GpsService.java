package fr.cerema.rivages;

import android.Manifest;
import android.app.Notification;
import android.app.PendingIntent;
import android.app.ProgressDialog;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.location.GpsSatellite;
import android.location.GpsStatus;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Binder;
import android.os.Bundle;
import android.os.Environment;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.NotificationCompat;
import android.util.Log;
import android.view.WindowManager;
import android.widget.Toast;

import org.osmdroid.util.GeoPoint;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.LinkedList;
import java.util.Locale;
import java.util.Queue;

import net.gotev.uploadservice.*;


// Christophe MOULIN - Cerema Med / DREC / SVGC - 23 juin 2016
//
// GpsService - service lancé par l'activité et vivant en tache de fond
// il est démarré, lié et au premier plan, ce qui le rend non éligible à la destruction par le système en cas de ressources faibles



public class GpsService extends Service  implements LocationListener,GpsStatus.NmeaListener,GpsStatus.Listener {

    private final String TAG = this.getClass().getSimpleName();

    // gestion du GPS
    private LocationManager locationManager;

    // file de points OSMDROID définis par latitude et longitude en microdegrés
    private Queue<GeoPoint> mListOfGeoPoint ;

    // liste des noms de photos
    private ArrayList<Photo> photos ;

    // données nécessaire à la construction des chaines nmea à enregistrer
    private StringBuilder sb ;
    //private ArrayList<String> nmeas;

    // création d'une liste de segments consitués de points, pour le cas où le nmea n'est pas supporté
    private ArrayList<Segment> segments;

    // création d'une liste de segments consitués de points, pour le cas où le nmea n'est pas supporté
    private ArrayList<Integer> limType;

    private boolean recording, isGpsOn, hasFix, hasNmeaData, started;
    private double lat, lon ;
    private float accuracy ;
    private int numberOfSatellites, numberOfPoint, currentLim;

    // nécessaire à la liaison du service à l'activité en demande de connexion
    private final IBinder mBinder = new LocalBinder();

    private Context context ;

    // intention pour prévenir l'activité de la disponibilité de données
    private Intent broadcastIntent ;

    // liste des types de limite
    private String[] limitName ;

    // préférences
    private  SharedPreferences preferences ;

    // mel de destination
    private String CEREMA_MAIL = "rivages@cerema.fr";

    public final static String DATA_AVAILABLE =
            "fr.cerema.rivages.DATA_AVAILABLE";

    public final static int LATITUDE = 10 ;
    public final static int LONGITUDE = 11 ;
    public final static int ACCURACY = 12 ;
    public final static int NUMBER_OF_SATELLITE = 13 ;
    public final static int NUMBER_OF_SEGMENT = 14 ;
    public final static int NUMBER_OF_POINT = 15 ;
    public final static int LIMIT_TYPE = 16 ;

    public interface NOTIFICATION_ID {
        public static int FOREGROUND_SERVICE = 101;
    }



    public class LocalBinder extends Binder {
        GpsService getService() {
            return GpsService.this;
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }




    @Override
    public void onCreate() {
        Log.i(TAG, "onCreate");
        super.onCreate();

        context = this;

        // initiation de l'intention pour le broadcast
        broadcastIntent = new Intent(DATA_AVAILABLE);

        // initialisation des tableaux utilisés
        mListOfGeoPoint=new LinkedList<>();
        photos =  new ArrayList<>();
        limType = new ArrayList<>();
        segments = new ArrayList<>();
        segments.add(new Segment());

        // nom des limites
        limitName = getResources().getStringArray(R.array.type);

        // instance du gestionnaire de gps
        locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);

        // chargement des preférences
        preferences = PreferenceManager.getDefaultSharedPreferences(context);

        // type de relevé à partir de ces préférences
        currentLim = preferences.getInt("TYPETRAIT",0);

        // démarrage du gps avec précision maximale
        startGps(0,0);
    }


    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        setForegroundNotification();
        return START_NOT_STICKY;
    }


    private void setForegroundNotification() {

        // crée une intention en attente - en cas d'appui sur l'icone du service, l'activité principale est relancée, même si elle a été détruite auparavant
        Intent notificationIntent = new Intent(this, MainActivity.class);
        notificationIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0,
                notificationIntent, 0);

        // icone grande
        Bitmap icon = BitmapFactory.decodeResource(getResources(), R.mipmap.ic_launcher);

        // notification dans la barre d'état
        Notification notification = new NotificationCompat.Builder(this)
                .setContentTitle(getString(R.string.app_name))
                .setTicker(getString(R.string.app_name))
                .setContentText(getString(R.string.running))
                .setSmallIcon(R.drawable.ic_rivages_p)
                .setLargeIcon(
                        Bitmap.createScaledBitmap(icon, 128, 128, false))
                .setContentIntent(pendingIntent)
                .setOngoing(true).build();

        // place le service en premier plan
        startForeground(NOTIFICATION_ID.FOREGROUND_SERVICE,
                notification);
    }


    @Override
    public void onDestroy() {
        Log.i(TAG, "onDestroy");
        super.onDestroy();

        // Désabonnement éventuel du GPS en cas d'arrêt intempestif
        stopGps();
    }


    public void onGpsStatusChanged(int event) {
        int satellites = 0;
        int satellitesInFix = 0;
        int timetofix = locationManager.getGpsStatus(null).getTimeToFirstFix();
        Log.v(TAG, "Temps première localisation = " + timetofix);
        for (GpsSatellite sat : locationManager.getGpsStatus(null).getSatellites()) {
            if(sat.usedInFix()) {
                satellitesInFix++;
            }
            satellites++;
        }
        Log.v(TAG, satellites + " Utilisés pour la dernière position ("+satellitesInFix+")");
        numberOfSatellites = satellitesInFix;
        // un nouveau nombre de satellites est disponible - prévenir l'activité pour mettre l'affichage à jour
        sendBroadcast(broadcastIntent);
    }


    @Override
    public void onStatusChanged(String provider, int status, Bundle extras) {
    }


    @Override
    public void onProviderEnabled(String provider) {
    }


    @Override
    public void onProviderDisabled(String provider) {
    }


    @Override
    public void onLocationChanged(Location location) {
        Log.v(TAG, "onLocationChanged");

        hasFix=true;

        lat = location.getLatitude();
        lon = location.getLongitude();
        accuracy = location.getAccuracy();

        if (recording) {
            // augmenter le nombre de points affichés comme enregistrés
            // en réalité on enregistre le nmea qui ne coincide pas avec la réception d'une nouvelle position
            // mais il est important que l'utilisateur ai un retour qu'il comprenne
            numberOfPoint++;
            mListOfGeoPoint.add(new GeoPoint((int)(lat*1E6),(int) (lon*1E6)));
            // enregistrement d'un point dans le dernier segment pour le cas ou le nmea n'est pas supporté
            segments.get(segments.size()-1).addPoint(new Point(lat, lon,location.getAltitude(), accuracy,location.getTime(),numberOfSatellites));
        }
        // nouvelle position = nouvelles latitude, longitude et précision : prévenir l'activité pour l'affichage
        sendBroadcast(broadcastIntent);
    }

    @Override
    public void onNmeaReceived(long timestamp, String nmea) {
        // si la méthode est appelée, le périphèrique supporte le nmea
        hasNmeaData=true;
        // d'une simplicité extrême : on ajoute la chaine de caractères reçue au stringBuilder, si on est en train d'enregistrer
        if (recording) sb.append(nmea);
    }


    private void startGps(long t, int d) {
        // depuis Android 6, l'utilisateur peut interdire à une application d'utiliser des fonctionnalités
        // il faut donc vérifier qu'elle est bien permise, sous peine de crash
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ) {
            if (isGpsOn) {
                // si la méthode est appelée alors que le GPS est déjà en train de recevoir des données
                stopGps();
            }
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, t, d, this);
            locationManager.addNmeaListener(this);
            locationManager.addGpsStatusListener(this);
            isGpsOn=true;
        }
    }


    private void stopGps() {
        // même remarque que précédemment
        if (isGpsOn && ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ) {
            locationManager.removeUpdates(this);
            locationManager.removeNmeaListener(this);
            locationManager.removeGpsStatusListener(this);
            isGpsOn = false;
        }
    }


    // Méthodes accessibles par l'activité principale

    public void addPhotoName(String photoName) {
        // création d'une nouvelle photo
        Photo photo = new Photo(photoName);
        // enregistrement des coordonnées gps actuelles
        photo.setLatitude(lat);
        photo.setLongitude(lon);
        photos.add(photo);
    }


    public boolean isRecording(){
        return recording;
    }


    public boolean hasPhoto() { return (photos!=null && photos.size()>0) ;}


    public void setCurrentLim(int currentLim) {this.currentLim=currentLim;}


    // formatte les données en vue d'être affichées
    public String getDataString(int type){

        switch (type) {

            case LATITUDE:
                return String.format(Locale.getDefault(),
                        getString(R.string.latitude), lat);

            case LONGITUDE:
                return String.format(Locale.getDefault(),
                        getString(R.string.longitude), lon);

            case ACCURACY:
                return String.format(Locale.getDefault(),
                        getString(R.string.accuracy), (int)accuracy);

            case NUMBER_OF_SATELLITE:
                return String.format(Locale.getDefault(),
                        getString(R.string.satellites), numberOfSatellites);

            case NUMBER_OF_SEGMENT:
                int numberOfSegment = (segments.size()-1)>0? segments.size()-1:0 ;
                return String.format(Locale.getDefault(),
                        getString(R.string.sequences), numberOfSegment);

            case NUMBER_OF_POINT:
                return String.format(Locale.getDefault(),
                        getString(R.string.nbPoints), numberOfPoint);

            case LIMIT_TYPE:
                return limitName[currentLim];

        }
        return "";
    }


    public boolean hasFix() {return hasFix;}


    // renvoie latitude en microdegré qui est le format utilisé par OSMDROID
    public int getLatE6() {
        return (int)(lat*1E6);
    }


    // renvoie longitude en microdegré qui est le format utilisé par OSMDROID
    public int getLonE6() {
        return (int)(lon*1E6);
    }


    public Queue<GeoPoint> getmListOfGeoPoint() {return mListOfGeoPoint;}


    public boolean hasStarted() { return started; }


    public int getNumberOfPoint() {
        return numberOfPoint;
    }


    public void startRecord() {
        Log.i(TAG, "startRecord");
        sb=new StringBuilder();
        if (segments.get(segments.size()-1).getPoints().size()>0) {
            segments.add(new Segment());
        }
        recording=true;
        started=true;
    }


    public void stopRecord() {
        Log.i(TAG, "stopRecord");
        if (sb.length()>0) {
            segments.get(segments.size()-1).setNmea(sb.toString());
            segments.get(segments.size()-1).setLimType(currentLim);
        }
        recording=false;
        // mise à jour de l'affichage
        sendBroadcast(broadcastIntent);
    }


    public void send() {
        Log.i(TAG, "send");
        if (sb!=null && sb.length()>0 && recording) {
            segments.get(segments.size()-1).setNmea(sb.toString());
            segments.get(segments.size()-1).setLimType(currentLim);
        }
        recording=false;
        // lancer la création de tous les fichiers en tâche asynchrône
        if (numberOfPoint>1) {
            // tache asynchrone crée dans une classe ad-hoc pour ne pas bloquer l'interface utilisateur
            Save mSave = new Save();
            //noinspection unchecked
            mSave.execute(segments);
            exit();
        }
    }


    public void exit() {
        Log.i(TAG, "exit");
        stopGps();
        stopForeground(true);
        stopSelf();
    }


    // la classe AsyncTask permet de lancer un fil d'exécution parallèle tout en mettant - éventuellement - un affichage de progression à jour

    class Save extends AsyncTask<ArrayList<Segment>, Void, Void> {

        // récupérer le cache de l'application - répertoire temporaire susceptible d'être effacé par le système.
        File cache = getCacheDir();
        boolean error ;
        int widthImage, heightImage, widthLogo, heightLogo ;
        Intent emailIntent;
        ProgressDialog progressDialog = new ProgressDialog(getApplicationContext());


        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            progressDialog.setMessage(getText(R.string.envoi_title));
            progressDialog.setTitle(getText(R.string.app_name));
            progressDialog.setIndeterminate(true);
            progressDialog.getWindow().setType(WindowManager.LayoutParams.TYPE_SYSTEM_ALERT);
            progressDialog.show();
        }


        @Override
        protected Void doInBackground(ArrayList<Segment>... mSegments) {

            // formattage date
            Date today = Calendar.getInstance().getTime();
            SimpleDateFormat formatter = new SimpleDateFormat("yyyyMMddHHmmss", Locale.FRENCH);
            String dateName = formatter.format(today);

            // création des fichier

            long mTime = System.currentTimeMillis();
            int i=0, j=1;
            if (mSegments[0].size()==0) return null;

            // Vérifier la présence d'un logo
            SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
            boolean hasLogo = (preferences.getString("LOGOPATH", "").length()>1) && hasPhoto() ;

            // ce tableau de noms permet ensuite de créer un zip
            String[] raw_file_names = new String[1+ (hasNmeaData? 2*mSegments[0].size():1)+photos.size() + (hasLogo? 1:0)];
            boolean rawNmeaIsOk = true ;
            // TODO limite dans gpx?

            // écriture du nom de l'appareil dans un .txt dans le cache

                    raw_file_names[0] = String.format(Locale.FRANCE,
                    "%1s/rivages_%2$s_model.txt",
                    cache.getAbsolutePath(),
                            dateName);
            i++;

            try {
                FileWriter writer = new FileWriter(new File(raw_file_names[0]), false);
                writer.append(android.os.Build.MODEL);
                writer.flush();
                writer.close();
            } catch (IOException e) {
                Log.e(TAG, "Error Writting Path", e);
            }

            if (hasNmeaData) {

                // écriture des fichiers bruts nmea dans le cache

                int k = i, l=i ;

                for (Segment segment : mSegments[0]) {

                    Log.i(TAG, "nmea.length=" + segment.getNmea().length());

                    if (!segment.getNmea().equals("")) {

                        // enregistrer le fichier NMEA dans le cache

                        raw_file_names[i] = String.format(Locale.FRANCE,
                                "%1s/rivages_%2$s_seg_%3$d.txt",
                                cache.getAbsolutePath(),
                                dateName,
                                l);

                        try {
                            FileWriter writer = new FileWriter(new File(raw_file_names[i]), false);
                            writer.append(segment.getNmea());
                            writer.flush();
                            writer.close();
                        } catch (IOException e) {
                            Log.e(TAG, "Error Writting Path", e);
                            rawNmeaIsOk = false;
                        }

                        i++;
                        l++;

                        // écriture du type de trait de cote dans un .txt dans le cache

                        raw_file_names[i] = String.format(Locale.FRANCE,
                                "%1s/rivages_%2$s_seg_lim_%3$d.txt",
                                cache.getAbsolutePath(),
                                dateName,
                                k);

                        try {
                            FileWriter writer = new FileWriter(new File(raw_file_names[i]), false);
                            writer.append(getResources().getStringArray(R.array.numero)[segment.getLimType()])
                                    .append("\n")
                                    .append(getResources().getStringArray(R.array.type)[segment.getLimType()]);
                            writer.flush();
                            writer.close();
                        } catch (IOException e) {
                            Log.e(TAG, "Error Writting Type", e);
                        }
                        i++;
                        k++;
                    }
                    }
            }
            else {
                // dans le cas où il n'y a pas de nmea, on crée un fichier gpx

                if (segments!=null && segments.size()>0 && segments.get(0).getPoints().size()>0) {
                    raw_file_names[i] = String.format(Locale.FRANCE,
                            "%1s/rivages_%2$s.gpx",
                            cache.getAbsolutePath(),
                            dateName);

                    String header = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"no\"?>\n" +
                            "<?xml-stylesheet type=\"text/xsl\" href=\"details.xsl\"?>\n" +
                            "<gpx\n" +
                            " version=\"1.1\"\n" +
                            " creator=\"BikePower\"\n" +
                            " xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"\n" +
                            " xmlns=\"http://www.topografix.com/GPX/1/1\"\n" +
                            " xmlns:topografix=\"http://www.topografix.com/GPX/Private/TopoGrafix/0/1\"\n" +
                            " xsi:schemaLocation=\"http://www.topografix.com/GPX/1/1 \n" +
                            "http://www.topografix.com/GPX/1/1/gpx.xsd \n" +
                            "http://www.topografix.com/GPX/Private/TopoGrafix/0/1 \n" +
                            "http://www.topografix.com/GPX/Private/TopoGrafix/0/1/topografix.xsd\">\n" +
                            "<trk>\n";

                    String name = "<name> Rivages recording </name>\n";
                    String number = "<number> 1 </number>\n";
                    String body = "";
                    DateFormat df;
                    df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ");
                    for (Segment segment : segments) {
                        if (segment.getPoints().size()>0) {
                            body += "<trkseg>\n";
                            for (Point point : segment.getPoints()) {
                                body += "<trkpt lat=\"" + (point.getLatitude()) + "\" lon=\"" + (point.getLongitude()) + "\">\n"
                                        + "<ele>" + point.getAltitude() + "</ele>\n"
                                        + "<time>" + df.format(new Date(point.getTime())) + "</time>\n"
                                        + "<cmt>accuracy=" + point.getAccuracy() + "</cmt>\n"
                                        + "<sat>" + point.getNumberOfSatellites() + "</sat>\n"
                                        + "</trkpt>\n";
                            }
                            body += "</trkseg>\n";
                        }
                    }

                    String footer = "</trk>\n</gpx>";

                    try {
                        FileWriter writer = new FileWriter(new File(raw_file_names[i]), false);
                        writer.append(header);
                        writer.append(name);
                        writer.append(number);
                        writer.append(body);
                        writer.append(footer);
                        writer.flush();
                        writer.close();

                    } catch (IOException e) {
                        Log.e(TAG, "Error Writting Path", e);
                    }
                    i++;
                }
            }

            // enregistrer les photos réduites dans le cache ; 800x?

            if (photos!= null && photos.size()>0) {

                for (Photo photo : photos) {

                    try {
                        // Use the compress method on the Bitmap object to write image to
                        // the OutputStream

                        raw_file_names[i] = String.format(Locale.FRANCE,
                                "%1$s/rivages_%2$s_photo_%3$d.jpg",
                                cache.getAbsolutePath(),
                                dateName,
                                j);

                        FileOutputStream fos = new FileOutputStream(new File(raw_file_names[i]));


                        // Récupérer le bitmap brut
                        Log.i(TAG, "pName="+photo.getAbsolutePath());
                        Bitmap largeBitmap = BitmapFactory.decodeFile(photo.getAbsolutePath());

                        // calculer le facteur de redimensionnement
                        int width = largeBitmap.getWidth();
                        int height = largeBitmap.getHeight();
                        int max = Math.max(width,height);
                        float scale = max/800f;
                        Log.i(TAG, "bitmap width = "+width);

                        widthImage = (int)(width / scale);
                        heightImage = (int)(height / scale);

                        // Ecriture d'un bitmap compressé en jpg avec qualité 90% et les dimensions voulues
                        Bitmap smallBitmap = Bitmap.createScaledBitmap(largeBitmap, widthImage, heightImage, true);
                        smallBitmap.compress(Bitmap.CompressFormat.JPEG, 90, fos);
                        fos.close();

                        // Recopie des données EXIF, puis écriture des données EXIF de localisation, même si l'utilisateur n'a pas coché "tag de localisation"
                        copyExif(photo.getAbsolutePath(), raw_file_names[i]);
                        ExifInterface exif = new ExifInterface(raw_file_names[i]);
                        exif.setAttribute(ExifInterface.TAG_GPS_LATITUDE, GPS.convert(photo.getLatitude()));
                        exif.setAttribute(ExifInterface.TAG_GPS_LATITUDE_REF, GPS.latitudeRef(photo.getLatitude()));
                        exif.setAttribute(ExifInterface.TAG_GPS_LONGITUDE, GPS.convert(photo.getLongitude()));
                        exif.setAttribute(ExifInterface.TAG_GPS_LONGITUDE_REF, GPS.longitudeRef(photo.getLongitude()));
                        exif.saveAttributes();

                        i++;
                        j++;

                    } catch (Exception e) {
                        Log.e(TAG, "saveToInternalStorage", e);
                    }
                }
            }


            // Ajout d'un logo éventuel
            if (hasLogo) {

                try {
                    //double RATE_LOGO = 37.65D;
                    String pathLogo = preferences.getString("LOGOPATH", "");
                    BitmapFactory.Options options = new BitmapFactory.Options();
                    options.inPreferredConfig = Bitmap.Config.ARGB_8888;
                    Bitmap rawLogo = BitmapFactory.decodeFile(pathLogo, options);
                    int widthRawLogo = rawLogo.getWidth();
                    int heightRawLogo = rawLogo.getHeight();
                    Bitmap bmOverlay = Bitmap.createBitmap(widthRawLogo, heightRawLogo, rawLogo.getConfig());
                    Canvas canvas = new Canvas(bmOverlay);
                    canvas.drawColor(Color.WHITE);
                    canvas.drawBitmap(rawLogo, 0, 0, null);
                    Log.i(TAG, "paramètres : widthRawLogo="+widthRawLogo+"- heightRawLogo="+heightRawLogo);
                    if (heightRawLogo!=0 && widthRawLogo!=0 ) {
                           widthLogo = (int) (Math.sqrt((143 * 67 * widthRawLogo) / ((double) heightRawLogo)));
                           heightLogo = (int) ((widthLogo * heightRawLogo * 1D) / (widthRawLogo));
                       }

                    Log.i(TAG, "size : widthLogo="+widthLogo+"- heightLogo="+heightLogo);

                    raw_file_names[i] = String.format(Locale.FRANCE,
                            "%1$s/rivages_%2$s_logo.jpg",
                            cache.getAbsolutePath(),
                            dateName);

                    FileOutputStream fos1 = new FileOutputStream(new File(String.format(Locale.FRANCE,
                            "%1$s/rivages_logo_1.jpg",
                            cache.getAbsolutePath())));
                    FileOutputStream fos = new FileOutputStream(new File(raw_file_names[i]));

                    // Ecriture d'un bitmap compressé en jpg avec qualité 90% et les dimensions voulues
                    Bitmap firstBitmap = Bitmap.createScaledBitmap(bmOverlay, widthLogo*4, heightLogo*4, true);
                    firstBitmap.compress(Bitmap.CompressFormat.JPEG, 100, fos1);
                    Bitmap smallBitmap = Bitmap.createScaledBitmap(firstBitmap, widthLogo, heightLogo, true);
                    smallBitmap.compress(Bitmap.CompressFormat.JPEG, 100, fos);
                    fos1.close();
                    fos.close();
                    i++;
                    j++;
                }
                catch (Exception e) {
                    Log.e(TAG, "saveToInternalStorage_logo", e);
                }
            }


            // transformer les fichiers bruts en ZIP, dans le répertoire "Documents"

            boolean zipIsOk=false;

            File exportDir = new File(Environment.getExternalStorageDirectory() + "/Documents",
                    String.format(Locale.FRANCE,
                            "rivages_%s.zip",
                            dateName));

            if (rawNmeaIsOk) {

                // instantiation du créateur de zip
                ZipManager zipManager = new ZipManager();

                // zip des fichiers bruts
                Log.i(TAG, "raw_file_names0=" + raw_file_names[0] );

                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.KITKAT) {
                    exportDir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS),
                            String.format(Locale.FRANCE,
                                    "rivages_%s.zip",
                                    dateName));
                }

                new File(exportDir.getParent()).mkdirs();

                zipIsOk = zipManager.zip(raw_file_names, exportDir.getAbsolutePath());
            }


            // préparation de l'email
            if (zipIsOk) {

                //
                Uri path = Uri.fromFile(exportDir);
                // on upload le fichier
                try {
                    String uploadId =
                            new MultipartUploadRequest(context, "http://omneedia.com:5000/upload")
                                    // starting from 3.1+, you can also use content:// URI string instead of absolute file
                                    .addFileToUpload(path.getPath(), "zip")
                                    .setNotificationConfig(new UploadNotificationConfig())
                                    .setMaxRetries(2)
                                    .startUpload();
                } catch (Exception exc) {
                    Log.e("AndroidUploadService", exc.getMessage(), exc);
                }
                Log.i(TAG, "path:"+exportDir.toString());
                emailIntent = new Intent(Intent.ACTION_SEND);
                // type d'Email
                emailIntent.setType("plain/text");
                // destinataires - ici un seul rivages@cerema.fr
                emailIntent.putExtra(Intent.EXTRA_EMAIL, new String[] {CEREMA_MAIL});
                // ajout de la PJ
                emailIntent.putExtra(Intent.EXTRA_STREAM, path);
                // texte type défini dans les ressources, auquel on ajoute le nom technique de l'appareil
                emailIntent.putExtra(Intent.EXTRA_TEXT, getString(R.string.mail_text));
                // objet du message défini dans les ressources
                emailIntent.putExtra(Intent.EXTRA_SUBJECT, String.format(Locale.FRANCE, "%1$s %2$s",getString(R.string.mail_subject), dateName));
                // lancement du courrielleur par défaut
                //emailIntent.putExtra(Intent.EXTRA_STREAM, Uri.parse("content://path/to/email/attachment"));
                emailIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(emailIntent);
            }
            else error=true;
            return null;
        }


        protected void onProgressUpdate(Integer... progress) {
            super.onProgressUpdate();
        }


        @Override
        protected void onPostExecute(Void Result) {
            // méthode de callback appelé à la fin - si une erreur s'est produite à un moment, un toast est affiché
            if (error) Toast.makeText(context, getString(R.string.error), Toast.LENGTH_LONG).show();
            progressDialog.dismiss();
        }
    }


    public static void copyExif(String oldPath, String newPath) throws IOException
    {
        ExifInterface oldExif = new ExifInterface(oldPath);

        String[] attributes = new String[]
                {
                        ExifInterface.TAG_DATETIME,
                        ExifInterface.TAG_FLASH,
                        ExifInterface.TAG_FOCAL_LENGTH,
                        ExifInterface.TAG_GPS_ALTITUDE,
                        ExifInterface.TAG_GPS_ALTITUDE_REF,
                        ExifInterface.TAG_GPS_DATESTAMP,
                        ExifInterface.TAG_GPS_LATITUDE,
                        ExifInterface.TAG_GPS_LATITUDE_REF,
                        ExifInterface.TAG_GPS_LONGITUDE,
                        ExifInterface.TAG_GPS_LONGITUDE_REF,
                        ExifInterface.TAG_GPS_PROCESSING_METHOD,
                        ExifInterface.TAG_GPS_TIMESTAMP,
                        ExifInterface.TAG_IMAGE_LENGTH,
                        ExifInterface.TAG_IMAGE_WIDTH,
                        ExifInterface.TAG_MAKE,
                        ExifInterface.TAG_MODEL,
                        ExifInterface.TAG_ORIENTATION,
                        ExifInterface.TAG_WHITE_BALANCE
                };

        ExifInterface newExif = new ExifInterface(newPath);
        for (int i = 0; i < attributes.length; i++)
        {
            String value = oldExif.getAttribute(attributes[i]);
            if (value != null)
                newExif.setAttribute(attributes[i], value);
        }
        newExif.saveAttributes();
    }
}