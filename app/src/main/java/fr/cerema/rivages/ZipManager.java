package fr.cerema.rivages;

import android.util.Log;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class ZipManager{

    private int BUFFER = 1024;

    public ZipManager() {
    }

    public boolean zip(String[] _files, String zipFileName) {

        boolean result = true ;

        try {
        BufferedInputStream origin = null;
        FileOutputStream dest = new FileOutputStream(zipFileName);
        ZipOutputStream out = new ZipOutputStream(new BufferedOutputStream(dest));
        byte data[] = new byte[BUFFER];

            Log.d("Files", String.valueOf(_files));

        for (int i = 0; i < _files.length; i++) {
            if (_files[i]!=null) {
                Log.v("Compress", "Adding: " + _files[i]);
                FileInputStream fi = new FileInputStream(_files[i]);
                origin = new BufferedInputStream(fi, BUFFER);

                ZipEntry entry = new ZipEntry(_files[i].substring(_files[i].lastIndexOf("/") + 1));
                out.putNextEntry(entry);
                int count;

                while ((count = origin.read(data, 0, BUFFER)) != -1) {
                    out.write(data, 0, count);
                }
                origin.close();
            }
        }
        out.close();
        } catch (Exception e) {
        e.printStackTrace();
        result = false;
        }

        return result;
        }
}