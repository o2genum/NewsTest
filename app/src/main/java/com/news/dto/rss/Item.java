package com.news.dto.rss;

import java.net.URL;
import java.util.Date;

/**
 * Created by o2genum on 23/06/15.
 */
public class Item implements Comparable<Item> {

    public String title;
    public URL link;
    public String author;
    public Date pubDate;
    public String description;
    public URL enclosure;

    @Override
    public int compareTo(Item another) {
        return this.pubDate.compareTo(another.pubDate);
    }

    @Override
    public boolean equals(Object another) {
        return this.pubDate.equals(((Item) another).pubDate);
    }
}
