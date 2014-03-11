package com.android.camera.widget;

import android.widget.AbsListView;

import com.android.camera.debug.Log;

import java.util.Collections;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * Responsible for controlling preloading logic. Intended usage is for ListViews that
 * benefit from initiating a load before the row appear on screen.
 * @param <T> The type of items this class preload.
 * @param <Y> The type of load tokens that can be used to cancel loads for the items this class
 *           preloads.
 */
public class Preloader<T, Y> implements AbsListView.OnScrollListener {
    private static final Log.Tag TAG = new Log.Tag("Preloader");

    /**
     * Implemented by the source for items that should be preloaded.
     */
    public interface ItemSource<T> {
        /**
         * Returns the objects in the range [startPosition; endPosition).
         */
        public List<T> getItemsInRange(int startPosition, int endPosition);

        /**
         * Returns the total number of items in the source.
         */
        public int getCount();
    }

    /**
     * Responsible for the loading of items.
     */
    public interface ItemLoader<T, Y> {
        /**
         * Initiates a load for the specified items and returns a list of 0 or more load tokens that
         * can be used to cancel the loads for the given items. Should preload the items in the list
         * order,preloading the 0th item in the list fist.
         */
        public List<Y> preloadItems(List<T> items);

        /**
         * Cancels all of the loads represented by the given load tokens.
         */
        public void cancelItems(List<Y> loadTokens);
    }

    private final int mMaxConcurrentPreloads;

    /**
     * Keep track of the largest/smallest item we requested (depending on scroll direction) so
     *  we don't preload the same items repeatedly. Without this var, scrolling down we preload
     *  0-5, then 1-6 etc. Using this we instead preload 0-5, then 5-6, 6-7 etc.
     */
    private int mLastEnd = -1;
    private int mLastStart;

    private final int mLoadAheadItems;
    private ItemSource<T> mItemSource;
    private ItemLoader<T, Y> mItemLoader;
    private Queue<List<Y>> mItemLoadTokens = new LinkedBlockingQueue<List<Y>>();

    private int mLastVisibleItem;
    private boolean mScrollingDown = false;

    public Preloader(int loadAheadItems, ItemSource<T> itemSource, ItemLoader<T, Y> itemLoader) {
        mItemSource = itemSource;
        mItemLoader = itemLoader;
        mLoadAheadItems = loadAheadItems;
        // Add an additional item so we don't cancel a preload before we start a real load.
        mMaxConcurrentPreloads = loadAheadItems + 1;
    }

    /**
     * Initiates a pre load.
     *
     * @param first The source position to load from
     * @param increasing The direction we're going in (increasing -> source positions are
     *                   increasing -> we're scrolling down the list)
     */
    private void preload(int first, boolean increasing) {
        final int start;
        final int end;
        if (increasing) {
            start = Math.max(first, mLastEnd);
            end = Math.min(first + mLoadAheadItems, mItemSource.getCount());
        } else {
            start = Math.max(0, first - mLoadAheadItems);
            end = Math.min(first, mLastStart);
        }

        Log.v(TAG, "preload first=" + first + " increasing=" + increasing + " start=" + start +
                " end=" + end);

        mLastEnd = end;
        mLastStart = start;

        if (start == 0 && end == 0) {
            return;
        }

        final List<T> items = mItemSource.getItemsInRange(start, end);
        if (!increasing) {
            Collections.reverse(items);
        }
        registerLoadTokens(mItemLoader.preloadItems(items));
    }

    private void registerLoadTokens(List<Y> loadTokens) {
        mItemLoadTokens.offer(loadTokens);
        // We pretend that one batch of load tokens corresponds to one item in the list. This isn't
        // strictly true because we may batch preload multiple items at once when we first start
        // scrolling in the list or change the direction we're scrolling in. In those cases, we will
        // have a single large batch of load tokens for multiple items, and then go back to getting
        // one batch per item as we continue to scroll. This means we may not cancel as many
        // preloads as we expect when we change direction, but we can at least be sure we won't
        // cancel preloads for items we still care about. We can't be more precise here because
        // there is no guarantee that there is a one to one relationship between load tokens
        // and list items.
        if (mItemLoadTokens.size() > mMaxConcurrentPreloads) {
            final List<Y> loadTokensToCancel = mItemLoadTokens.poll();
            mItemLoader.cancelItems(loadTokensToCancel);
        }
    }

    public void cancelAllLoads() {
        for (List<Y> loadTokens : mItemLoadTokens) {
            mItemLoader.cancelItems(loadTokens);
        }
        mItemLoadTokens.clear();
    }

    @Override
    public void onScrollStateChanged(AbsListView absListView, int i) {
        // Do nothing.
    }

    @Override
    public void onScroll(AbsListView absListView, int firstVisible, int visibleItemCount,
            int totalItemCount) {
        boolean wasScrollingDown = mScrollingDown;
        int preloadStart = -1;
        if (firstVisible > mLastVisibleItem) {
            // Scrolling list down
            mScrollingDown = true;
            preloadStart = firstVisible + visibleItemCount;
        } else if (firstVisible < mLastVisibleItem) {
            // Scrolling list Up
            mScrollingDown = false;
            preloadStart = firstVisible;
        }

        if (wasScrollingDown != mScrollingDown) {
            // If we've changed directions, we don't care about any of our old preloads, so cancel
            // all of them.
            cancelAllLoads();
        }

        // onScroll can be called multiple times with the same arguments, so we only want to preload
        // if we've actually scrolled at least an item in either direction.
        if (preloadStart != -1) {
            preload(preloadStart, mScrollingDown);
        }

        mLastVisibleItem = firstVisible;
    }
}
