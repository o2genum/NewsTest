package com.news.api.http;

import android.net.http.HttpResponseCache;
import android.util.Log;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * Created by o2genum on 23/06/15.
 */
public class HttpClient {
    private static boolean cacheEnabled = false;
    private static String cacheDir;

    public static InputStream fromNetwork(URL url) throws IOException {
        HttpURLConnection urlConnection = (HttpURLConnection) url.openConnection();
        // Fake user agent to avoid redirects to mobile versions
        urlConnection.setRequestProperty("User-Agent",
                "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_10_3) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/43.0.2357.130 Safari/537.36");
        InputStream is = urlConnection.getInputStream();
        if (cacheEnabled) {
            return new BufferedInputStream(new FileCachingInputStream(is, urlToFile(url)));
        } else {
            return new BufferedInputStream(is);
        }
    }

    public static InputStream fromCache(URL url) throws IOException {
        return new FileInputStream(urlToFile(url));
    }

    private static File urlToFile(URL url) {
        return new File(cacheDir + new Integer(url.toString().hashCode()).toString() + ".tmp");
    }

    public static void setCacheDir(String cacheDir) {
        HttpClient.cacheDir = cacheDir;
    }

    public static void enableCache(boolean cacheEnabled) {
        HttpClient.cacheEnabled = cacheEnabled;
    }
}
