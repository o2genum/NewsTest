package com.news.news;

import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.TransitionDrawable;
import android.os.AsyncTask;
import android.support.v7.widget.RecyclerView;
import android.text.Html;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import com.news.api.RssReader;
import com.news.dto.rss.Item;

import java.net.URL;
import java.util.LinkedList;
import java.util.List;

/**
 * Created by o2genum on 25/06/15.
 */
public class NewsAdapter extends RecyclerView.Adapter<NewsAdapter.ViewHolder> {
    private Item[] mItems;

    private LinkedList<DownloadOrder> mOrders;
    int mMaxImageDownloadListLength = 0;

    private Resources mResources;

    public class ViewHolder extends RecyclerView.ViewHolder implements View.OnClickListener {
        private View mCardView;
        private TextView mTitleTextView;
        private TextView mDescriptionTextView;
        private ImageView mImageView;
        private View mCollapsedContentEnclosingLayout;

        private Item item;
        private boolean mNeedsImageDownload = false;

        public ViewHolder(View v) {
            super(v);
            mCardView = v.findViewById(R.id.news_card_view);
            mTitleTextView = (TextView) v.findViewById(R.id.news_item_title);
            mDescriptionTextView = (TextView) v.findViewById(R.id.news_item_description);
            mImageView = (ImageView) v.findViewById(R.id.news_item_photo);
            mCollapsedContentEnclosingLayout = v.findViewById(R.id.news_collapsed_content_enclosing_layout);

            mCardView.setOnClickListener(this);
        }

        public boolean needsImageDownload() {
            return mNeedsImageDownload;
        }

        public URL getImageDownloadUrl() {
            return item.enclosure;
        }

        public void setImage(Bitmap image, boolean withAnimation) {
            if (withAnimation) {
                final TransitionDrawable td =
                        new TransitionDrawable(new Drawable[]{
                                new ColorDrawable(Color.TRANSPARENT),
                                new BitmapDrawable(mResources, image)
                        });
                mImageView.setImageDrawable(td);
                td.startTransition(200);
            } else {
                mImageView.setImageBitmap(image);
            }
            mNeedsImageDownload = false;
        }

        @Override
        public void onClick(View v) {
            if (mDescriptionTextView.getVisibility() == View.GONE) {
                ViewExpanderCollapser.expand(mDescriptionTextView, 500, mCollapsedContentEnclosingLayout.getWidth());
            } else {
                ViewExpanderCollapser.collapse(mDescriptionTextView, 500);
            }
        }
    }

    public NewsAdapter(List<Item> items) {
        mItems = items.toArray(new Item[]{});
        mOrders = new LinkedList<DownloadOrder>();
    }

    public void setMaxImageDownloadListLength(int maxImageDownloadListLength) {
        mMaxImageDownloadListLength = maxImageDownloadListLength;
    }

    public void setResources(Resources resources) {
        mResources = resources;
    }

    public void setItems(List<Item> items) {
        // Computing difference between the new dataset and the old one
        int indexOfOldFirstInNew = -1;
        if ((mItems != null && mItems.length >= 1) && (items != null && items.size() >= 1)) {
            indexOfOldFirstInNew = items.indexOf(mItems[0]);
        }
        mItems = items.toArray(new Item[]{});
        if (indexOfOldFirstInNew > 0) {
            notifyItemRangeInserted(0, indexOfOldFirstInNew);
        } else {
            notifyDataSetChanged();
        }
    }

    @Override
    public NewsAdapter.ViewHolder onCreateViewHolder(ViewGroup parent,
                                                   int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.news_item, parent, false);
        ViewHolder vh = new ViewHolder(v);
        return vh;
    }

    @Override
    public void onBindViewHolder(ViewHolder holder, int position) {
        holder.item = mItems[position];

        holder.mDescriptionTextView.setVisibility(View.GONE);
        holder.mNeedsImageDownload = false;
        if (holder.item.enclosure == null) {
            holder.mImageView.setVisibility(View.GONE);
        } else {
            holder.mImageView.setVisibility(View.VISIBLE);
            holder.mImageView.setImageBitmap(null);
            try {
                Bitmap image = RssReader.getImage(holder.item.enclosure, RssReader.Source.CACHE);
                holder.setImage(image, false);
                holder.mNeedsImageDownload = false;
            } catch (Exception ex) {
                holder.mNeedsImageDownload = true;
            }
        }
        holder.mTitleTextView.setText(Html.fromHtml(holder.item.title.trim() + " <small>—&nbsp;" +
                (holder.item.link.getHost().toString().equals("lenta.ru") ? "Лента.ру" : "Газета.ру") + "</small>"));
        holder.mDescriptionTextView.setText(Html.fromHtml(holder.item.description.replaceAll("\n", " ").trim()));

        if (holder.needsImageDownload()) {
            mOrders.add(new DownloadOrder(holder.getImageDownloadUrl(), holder));
            while (mOrders.size() > mMaxImageDownloadListLength && mMaxImageDownloadListLength != 0) {
                mOrders.remove(0).viewHolder.setIsRecyclable(true);
            }
        }
    }

    public void downloadImages() {
        new DownloadImagesTask().execute((LinkedList<DownloadOrder>) mOrders.clone());
        mOrders.clear();
    }

    @Override
    public int getItemCount() {
        return mItems.length;
    }

    private class DownloadImagesTask extends AsyncTask<List<DownloadOrder>, FinishedOrder,  Void> {
        @Override
        protected Void doInBackground(List<DownloadOrder>... downloadOrders) {
            if (downloadOrders.length != 1) { throw new IllegalArgumentException("Only one argument allowed"); }

            for (DownloadOrder order : downloadOrders[0]) {
                Bitmap image = null;
                try {
                    image = RssReader.getImage(order.url, RssReader.Source.NETWORK);
                } catch (Exception ex) {
                } finally {
                    if (image != null) {
                        publishProgress(new FinishedOrder(order, image));
                    }
                }
            }
            return null;
        }

        @Override
        protected void onProgressUpdate(FinishedOrder... finishedOrder) {
            if (finishedOrder.length != 1) { throw new IllegalArgumentException("Only one argument allowed"); }

            finishedOrder[0].downloadOrder.viewHolder.setImage(finishedOrder[0].image, true);
            finishedOrder[0].downloadOrder.viewHolder.setIsRecyclable(true);
        }
    }

    private class DownloadOrder {
        public URL url;
        public NewsAdapter.ViewHolder viewHolder;

        public DownloadOrder(URL url, NewsAdapter.ViewHolder viewHolder) {
            this.url = url;
            this.viewHolder = viewHolder;

            viewHolder.setIsRecyclable(false);
        }
    }

    private class FinishedOrder {
        public DownloadOrder downloadOrder;
        public Bitmap image;

        public FinishedOrder(DownloadOrder downloadOrder, Bitmap image) {
            this.downloadOrder = downloadOrder;
            this.image = image;
        }
    }
}
