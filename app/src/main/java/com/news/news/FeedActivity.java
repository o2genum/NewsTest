package com.news.news;

import android.graphics.Bitmap;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.support.design.widget.Snackbar;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;

import com.news.api.RssReader;
import com.news.api.http.HttpClient;
import com.news.dto.rss.Item;

import java.net.URL;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

public class FeedActivity extends AppCompatActivity {
    private SwipeRefreshLayout mSwipeRefreshLayout;
    private RecyclerView mRecyclerView;
    private NewsAdapter mAdapter;
    private RecyclerView.LayoutManager mLayoutManager;

    List<URL> feedUrls = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_feed);

        mSwipeRefreshLayout = (SwipeRefreshLayout) findViewById(R.id.swipe_refresh_layout);
        mRecyclerView = (RecyclerView) findViewById(R.id.news_recycler_view);
        mLayoutManager = new LinearLayoutManager(this) {

            @Override
            protected int getExtraLayoutSpace(RecyclerView.State state) {
                return getHeight();
            }
        };
        mRecyclerView.setLayoutManager(mLayoutManager);

        try {
            feedUrls = new ArrayList<URL>();
            feedUrls.add(new URL("http://lenta.ru/rss"));
            feedUrls.add(new URL("http://www.gazeta.ru/export/rss/lenta.xml"));
        } catch (Exception ex) {}

        mSwipeRefreshLayout.setEnabled(true);
        mSwipeRefreshLayout.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
            @Override
            public void onRefresh() {
                new DownloadNewsTask().execute(feedUrls);
            }
        });

        mRecyclerView.setHasFixedSize(true);

        HttpClient.setCacheDir(getCacheDir().getAbsolutePath());
        HttpClient.enableCache(true);

        try {
            // Load last cached news
            List<Item> items = RssReader.getFeeds(feedUrls, RssReader.Source.CACHE);
            mAdapter = new NewsAdapter(items);
        } catch (Exception ex) {
            // News not found in cache
            mAdapter = new NewsAdapter(new LinkedList<Item>());
        } finally {
            // Get fresh news anyway
            mAdapter.setResources(getResources());
            mRecyclerView.setAdapter(mAdapter);
            new DownloadNewsTask().execute(feedUrls);
        }

        mRecyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(RecyclerView recyclerView, int newState) {
                if (newState != RecyclerView.SCROLL_STATE_DRAGGING) {
                    mAdapter.setMaxImageDownloadListLength(getVisibleItemsCount() * 3);
                    mAdapter.downloadImages();
                }
            }
        });
    }

    public int getVisibleItemsCount() {
        if (mLayoutManager instanceof LinearLayoutManager) {
            LinearLayoutManager llm = (LinearLayoutManager) mLayoutManager;
            return llm.findLastVisibleItemPosition() - llm.findFirstVisibleItemPosition() + 1;
        }
        return 0;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_feed, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    private void displayError(int id) {
        Snackbar.make(mSwipeRefreshLayout, id, Snackbar.LENGTH_LONG).show();
    }

    private class DownloadNewsTask extends AsyncTask<List<URL>, Void,  List<Item>> {
        private Exception mException;

        @Override
        protected  void onPreExecute()
        {
            // A workaround for a bug in SwipeRefreshLayout
            // http://stackoverflow.com/questions/26858692/
            new Handler().postDelayed(new Runnable() {
                @Override
                public void run() {
                    mSwipeRefreshLayout.setRefreshing(true);
                }
            }, 100);

            // Cancel the task if runs too long
            new Handler().postDelayed(new Runnable() {
                @Override
                public void run() {
                    if (DownloadNewsTask.this.getStatus() != Status.FINISHED) {
                        DownloadNewsTask.this.cancel(true);
                        mSwipeRefreshLayout.setRefreshing(false);
                        displayError(R.string.network_error);
                    }
                }
            }, 10000);
        }

        protected List<Item> doInBackground(List<URL>... urls) {
            if (urls.length != 1) { throw new IllegalArgumentException("Only one argument allowed"); }

            try {
                return RssReader.getFeeds(urls[0], RssReader.Source.NETWORK);
            } catch (Exception ex) {
                mException = ex;
            }
            return null;
        }

        protected void onPostExecute(List<Item> result) {
            if (mException != null) {
                mException.printStackTrace();
                displayError(R.string.network_error);
            } else {
                mAdapter.setItems(result);
                // Smooth scrolling to top, not on UI thread
                new Handler().post(new Runnable() {
                    @Override
                    public void run() {
                        mRecyclerView.smoothScrollToPosition(0);
                    }
                });
            }

            new Handler().postDelayed(new Runnable() {
                @Override
                public void run() {
                    mSwipeRefreshLayout.setRefreshing(false);
                    mAdapter.downloadImages();
                }
            }, 100);
        }
    }
}
