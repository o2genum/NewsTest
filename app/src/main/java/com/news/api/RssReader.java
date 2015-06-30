package com.news.api;

import java.text.DateFormat;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Log;

import com.news.api.http.HttpClient;
import com.news.dto.rss.Item;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserFactory;

import java.io.InputStream;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

/**
 * Created by o2genum on 23/06/15.
 */
public class RssReader {

    private static final String ns = null;
    private static DateFormat formatter = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz");
    private static Map<URL, Bitmap> bitmapCache = new HashMap<URL, Bitmap>();

    public enum Source {CACHE, NETWORK};

    public static List<Item> getFeeds(List<URL> urls, Source from) throws Exception {
        List<List<Item>> listOfFeeds = new ArrayList<List<Item>>();
        for (URL url : urls) {
            listOfFeeds.add(getFeed(url, from));
        }
        return mergeFeeds(listOfFeeds);
    }

    public static List<Item> getFeed(URL url, Source from) throws Exception {
        InputStream is = null;
        switch (from) {
            case CACHE:
                is = HttpClient.fromCache(url);
                break;
            case NETWORK:
                is = HttpClient.fromNetwork(url);
                break;
        }

        List<Item> items = parse(is);
        is.close();
        return items;
    }

    public static Bitmap getImage(URL url, Source from) throws Exception {
        InputStream is = null;
        switch (from) {
            case CACHE:
                Bitmap image = bitmapCache.get(url);
                if (image != null) {
                    return scaleBitmap(image);
                }
                is = HttpClient.fromCache(url);
                break;
            case NETWORK:
                is = HttpClient.fromNetwork(url);
                break;
        }

        Bitmap image = scaleBitmap(BitmapFactory.decodeStream(is));
        bitmapCache.put(url, image);
        is.close();
        return image;
    }

    private static Bitmap scaleBitmap(Bitmap src) {
        Bitmap cropped = null;
        if (src.getWidth() != src.getHeight()) {
            if (src.getWidth() >= src.getHeight()) {
                cropped = Bitmap.createBitmap(
                        src,
                        src.getWidth() / 2 - src.getHeight() / 2,
                        0,
                        src.getHeight(),
                        src.getHeight()
                );
            } else {
                cropped = Bitmap.createBitmap(
                        src,
                        0,
                        src.getHeight() / 2 - src.getWidth() / 2,
                        src.getWidth(),
                        src.getWidth()
                );
            }
        } else {
            cropped = src;
        }
        return Bitmap.createScaledBitmap(cropped, 60, 60, true);
    }

    private static List<Item> mergeFeeds(List<List<Item>> lists) {
        ArrayList<Item> res = new ArrayList<Item>();
        for (List<Item> list : lists) {
            res.addAll(list);
        }
        Collections.sort(res, Collections.reverseOrder());
        return res;
    }

    private static List<Item> parse(InputStream is) throws Exception {
        List<Item> items = new LinkedList<Item>();

        XmlPullParserFactory xmlFactoryObject = XmlPullParserFactory.newInstance();
        XmlPullParser parser = xmlFactoryObject.newPullParser();
        parser.setInput(is, null);

        while (parser.next() != XmlPullParser.END_DOCUMENT) {
            if (parser.getEventType() != XmlPullParser.START_TAG) {
                continue;
            }

            if (parser.getName().equals("item")) {
                items.add(parseItem(parser));
            }
        }
        return items;
    }

    private static Item parseItem(XmlPullParser parser) throws Exception {
        parser.require(XmlPullParser.START_TAG, ns, "item");

        Item item = new Item();
        while (parser.next() != XmlPullParser.END_TAG) {
            if (parser.getEventType() != XmlPullParser.START_TAG) {
                continue;
            }
            String name = parser.getName();
            if (name.equals("title")) {
                item.title = parseTextTag(parser, "title");
            } else if (name.equals("link")) {
                item.link = new URL(parseTextTag(parser, "link"));
            } else if (name.equals("author")) {
                item.author = parseTextTag(parser, "author");
            } else if (name.equals("pubDate")) {
                item.pubDate = formatter.parse(parseTextTag(parser, "pubDate"));
            } else if (name.equals("description")) {
                item.description = parseTextTag(parser, "description");
            } else if (name.equals("enclosure")) {
                item.enclosure = new URL(parseEnclosureTag(parser));
            } else {
                skip(parser);
            }
        }

        parser.require(XmlPullParser.END_TAG, ns, "item");
        return item;
    }

    private static String parseEnclosureTag(XmlPullParser parser) throws Exception {
        parser.require(XmlPullParser.START_TAG, ns, "enclosure");
        String url = parser.getAttributeValue(null, "url");
        skip(parser);
        parser.require(XmlPullParser.END_TAG, ns, "enclosure");
        return url;
    }

    private static String parseTextTag(XmlPullParser parser, String name) throws Exception {
        parser.require(XmlPullParser.START_TAG, ns, name);
        String text = readText(parser);
        parser.require(XmlPullParser.END_TAG, ns, name);
        return text;
    }

    private static String readText(XmlPullParser parser) throws Exception {
        String result = null;
        if (parser.next() == XmlPullParser.TEXT) {
            result = parser.getText();
            parser.nextTag();
        }
        return result;
    }

    private static void skip(XmlPullParser parser) throws Exception {
        if (parser.getEventType() != XmlPullParser.START_TAG) {
            throw new IllegalStateException();
        }

        int depth = 1;
        while (depth != 0) {
            switch (parser.next()) {
                case XmlPullParser.END_TAG:
                    depth--;
                    break;
                case XmlPullParser.START_TAG:
                    depth++;
                    break;
            }
        }
    }
}
