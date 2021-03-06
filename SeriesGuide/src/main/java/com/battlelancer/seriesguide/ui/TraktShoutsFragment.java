/*
 * Copyright 2014 Uwe Trottmann
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.battlelancer.seriesguide.ui;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.app.ListFragment;
import android.support.v4.app.LoaderManager.LoaderCallbacks;
import android.support.v4.content.Loader;
import android.text.TextUtils;
import android.text.format.DateUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.animation.AnimationUtils;
import android.widget.AdapterView;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.TextView;
import butterknife.ButterKnife;
import butterknife.InjectView;
import com.actionbarsherlock.app.SherlockFragment;
import com.battlelancer.seriesguide.R;
import com.battlelancer.seriesguide.adapters.TraktCommentsAdapter;
import com.battlelancer.seriesguide.enums.TraktAction;
import com.battlelancer.seriesguide.loaders.TraktCommentsLoader;
import com.battlelancer.seriesguide.util.TraktTask;
import com.battlelancer.seriesguide.util.Utils;
import com.jakewharton.trakt.entities.Comment;
import com.uwetrottmann.androidutils.AndroidUtils;
import de.greenrobot.event.EventBus;
import java.util.List;

/**
 * A custom {@link ListFragment} to display show or episode shouts and for posting own shouts.
 */
public class TraktShoutsFragment extends SherlockFragment implements
        LoaderCallbacks<List<Comment>> {

    /**
     * Build a {@link TraktShoutsFragment} for shouts of an episode.
     */
    public static TraktShoutsFragment newInstanceEpisode(int showTvdbId, int seasonNumber,
            int episodeNumber) {
        TraktShoutsFragment f = new TraktShoutsFragment();
        Bundle args = new Bundle();
        args.putInt(InitBundle.SHOW_TVDB_ID, showTvdbId);
        args.putInt(InitBundle.SEASON_NUMBER, seasonNumber);
        args.putInt(InitBundle.EPISODE_NUMBER, episodeNumber);
        f.setArguments(args);
        return f;
    }

    /**
     * Build a {@link TraktShoutsFragment} for shouts of a show.
     */
    public static TraktShoutsFragment newInstanceShow(int tvdbId) {
        TraktShoutsFragment f = new TraktShoutsFragment();
        Bundle args = new Bundle();
        args.putInt(InitBundle.SHOW_TVDB_ID, tvdbId);
        f.setArguments(args);
        return f;
    }

    /**
     * Build a {@link TraktShoutsFragment} for shouts of a movie.
     */
    public static TraktShoutsFragment newInstanceMovie(int tmdbId) {
        TraktShoutsFragment f = new TraktShoutsFragment();
        Bundle args = new Bundle();
        args.putInt(InitBundle.MOVIE_TMDB_ID, tmdbId);
        f.setArguments(args);
        return f;
    }

    public interface InitBundle {
        String MOVIE_TMDB_ID = "tmdbid";
        String SHOW_TVDB_ID = "tvdbid";
        String EPISODE_NUMBER = "episode_number";
        String SEASON_NUMBER = "season_number";
    }

    static final int INTERNAL_EMPTY_ID = 0x00ff0001;

    static final int INTERNAL_LIST_CONTAINER_ID = 0x00ff0003;

    private static final String TRAKT_MOVIE_COMMENT_PAGE_URL = "https://trakt.tv/comment/movie/";

    private static final String TRAKT_EPISODE_COMMENT_PAGE_URL
            = "https://trakt.tv/comment/episode/";

    private static final String TRAKT_SHOW_COMMENT_PAGE_URL = "https://trakt.tv/comment/show/";

    final private Handler mHandler = new Handler();

    final private Runnable mRequestFocus = new Runnable() {
        public void run() {
            mList.focusableViewAvailable(mList);
        }
    };

    final private AdapterView.OnItemClickListener mOnClickListener
            = new AdapterView.OnItemClickListener() {
        public void onItemClick(AdapterView<?> parent, View v, int position, long id) {
            onListItemClick((ListView) parent, v, position, id);
        }
    };

    TraktCommentsAdapter mAdapter;

    ListView mList;

    View mEmptyView;

    TextView mStandardEmptyView;

    View mProgressContainer;

    View mListContainer;

    CharSequence mEmptyText;

    @InjectView(R.id.shoutbutton) ImageButton mButtonShout;

    @InjectView(R.id.shouttext) EditText mEditTextShout;

    @InjectView(R.id.checkIsSpoiler) CheckBox mCheckBoxIsSpoiler;

    boolean mListShown;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.shouts_fragment, container, false);
        ButterKnife.inject(this, v);

        mButtonShout.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                shout();
            }
        });

        return v;
    }

    private void shout() {
        // prevent empty shouts
        String shout = mEditTextShout.getText().toString();
        if (TextUtils.isEmpty(shout)) {
            return;
        }

        // disable the shout button
        mButtonShout.setEnabled(false);

        Bundle args = getArguments();
        boolean isSpoiler = mCheckBoxIsSpoiler.isChecked();

        // shout for a movie?
        int movieTmdbId = args.getInt(InitBundle.MOVIE_TMDB_ID);
        if (movieTmdbId != 0) {
            AndroidUtils.executeAsyncTask(
                    new TraktTask(getActivity()).shoutMovie(movieTmdbId, shout, isSpoiler)
            );
            return;
        }

        // shout for an episode?
        int showTvdbId = args.getInt(InitBundle.SHOW_TVDB_ID);
        int episodeNumber = args.getInt(InitBundle.EPISODE_NUMBER);
        if (episodeNumber != 0) {
            int seasonNumber = args.getInt(InitBundle.SEASON_NUMBER);
            AndroidUtils.executeAsyncTask(
                    new TraktTask(getActivity())
                            .shoutEpisode(showTvdbId, seasonNumber, episodeNumber, shout, isSpoiler)
            );
            return;
        }

        // shout for a show!
        AndroidUtils.executeAsyncTask(
                new TraktTask(getActivity()).shoutShow(showTvdbId, shout, isSpoiler)
        );
    }

    /**
     * Attach to list view once the view hierarchy has been created.
     */
    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        ensureList();
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        mAdapter = new TraktCommentsAdapter(getActivity());
        setListAdapter(mAdapter);

        // nag about no connectivity
        if (!AndroidUtils.isNetworkConnected(getSherlockActivity())) {
            setListShown(true);
            ((TextView) mEmptyView).setText(R.string.offline);
        } else {
            setListShown(false);
            getLoaderManager().initLoader(0, getArguments(), this);
            mHandler.postDelayed(mUpdateShoutsRunnable, DateUtils.MINUTE_IN_MILLIS);
        }
    }

    private Runnable mUpdateShoutsRunnable = new Runnable() {
        @Override
        public void run() {
            getLoaderManager().restartLoader(0, getArguments(), TraktShoutsFragment.this);
            mHandler.postDelayed(mUpdateShoutsRunnable, DateUtils.MINUTE_IN_MILLIS);
        }
    };

    @Override
    public void onResume() {
        super.onResume();

        EventBus.getDefault().register(this);
    }

    @Override
    public void onPause() {
        super.onPause();

        EventBus.getDefault().unregister(this);
    }

    /**
     * Detach from list view.
     */
    @Override
    public void onDestroyView() {
        mHandler.removeCallbacks(mRequestFocus);
        mHandler.removeCallbacks(mUpdateShoutsRunnable);
        mList = null;
        mListShown = false;
        mEmptyView = mProgressContainer = mListContainer = null;
        mStandardEmptyView = null;
        super.onDestroyView();
        ButterKnife.reset(this);
    }

    /**
     * Provide the cursor for the list view.
     */
    public void setListAdapter(TraktCommentsAdapter adapter) {
        boolean hadAdapter = mAdapter != null;
        mAdapter = adapter;
        if (mList != null) {
            mList.setAdapter(adapter);
            if (!mListShown && !hadAdapter) {
                // The list was hidden, and previously didn't have an
                // adapter. It is now time to show it.
                setListShown(true, getView().getWindowToken() != null);
            }
        }
    }

    public void onListItemClick(ListView l, View v, int position, long id) {
        final Comment comment = (Comment) l.getItemAtPosition(position);

        if (comment.spoiler) {
            // if shout is a spoiler, first click will reveal the shout
            comment.spoiler = false;
            TextView shoutText = (TextView) v.findViewById(R.id.shout);
            if (shoutText != null) {
                shoutText.setText(comment.text);
            }
        } else {
            // open shout or review page
            int showTvdbId = getArguments().getInt(InitBundle.SHOW_TVDB_ID);
            int episodeNumber = getArguments().getInt(InitBundle.EPISODE_NUMBER);
            String typeUrl;
            if (showTvdbId == 0) {
                typeUrl = TRAKT_MOVIE_COMMENT_PAGE_URL;
            } else if (episodeNumber == 0) {
                typeUrl = TRAKT_SHOW_COMMENT_PAGE_URL;
            } else {
                typeUrl = TRAKT_EPISODE_COMMENT_PAGE_URL;
            }
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(typeUrl + comment.id));
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_WHEN_TASK_RESET);
            Utils.tryStartActivity(getActivity(), intent, true);
        }
    }

    @Override
    public Loader<List<Comment>> onCreateLoader(int id, Bundle args) {
        return new TraktCommentsLoader(getSherlockActivity(), args);
    }

    @Override
    public void onLoadFinished(Loader<List<Comment>> loader, List<Comment> data) {
        mAdapter.setData(data);

        if (isResumed()) {
            setListShown(true);
        } else {
            setListShownNoAnimation(true);
        }
    }

    @Override
    public void onLoaderReset(Loader<List<Comment>> data) {
        mAdapter.setData(null);
    }

    /**
     * Control whether the list is being displayed. You can make it not displayed if you are
     * waiting
     * for the initial data to show in it. During this time an indeterminant progress indicator
     * will
     * be shown instead. <p> Applications do not normally need to use this themselves. The default
     * behavior of ListFragment is to start with the list not being shown, only showing it once an
     * adapter is given with {@link #setListAdapter(ListAdapter)}. If the list at that point had
     * not
     * been shown, when it does get shown it will be do without the user ever seeing the hidden
     * state.
     *
     * @param shown If true, the list view is shown; if false, the progress indicator. The initial
     *              value is true.
     */
    public void setListShown(boolean shown) {
        setListShown(shown, true);
    }

    /**
     * Like {@link #setListShown(boolean)}, but no animation is used when transitioning from the
     * previous state.
     */
    public void setListShownNoAnimation(boolean shown) {
        setListShown(shown, false);
    }

    /**
     * Control whether the list is being displayed. You can make it not displayed if you are
     * waiting
     * for the initial data to show in it. During this time an indeterminant progress indicator
     * will
     * be shown instead.
     *
     * @param shown   If true, the list view is shown; if false, the progress indicator. The
     *                initial
     *                value is true.
     * @param animate If true, an animation will be used to transition to the new state.
     */
    private void setListShown(boolean shown, boolean animate) {
        ensureList();
        if (mProgressContainer == null) {
            throw new IllegalStateException("Can't be used with a custom content view");
        }
        if (mListShown == shown) {
            return;
        }
        mListShown = shown;
        if (shown) {
            if (animate) {
                mProgressContainer.startAnimation(AnimationUtils.loadAnimation(getActivity(),
                        android.R.anim.fade_out));
                mListContainer.startAnimation(AnimationUtils.loadAnimation(getActivity(),
                        android.R.anim.fade_in));
            } else {
                mProgressContainer.clearAnimation();
                mListContainer.clearAnimation();
            }
            mProgressContainer.setVisibility(View.GONE);
            mListContainer.setVisibility(View.VISIBLE);
        } else {
            if (animate) {
                mProgressContainer.startAnimation(AnimationUtils.loadAnimation(getActivity(),
                        android.R.anim.fade_in));
                mListContainer.startAnimation(AnimationUtils.loadAnimation(getActivity(),
                        android.R.anim.fade_out));
            } else {
                mProgressContainer.clearAnimation();
                mListContainer.clearAnimation();
            }
            mProgressContainer.setVisibility(View.VISIBLE);
            mListContainer.setVisibility(View.GONE);
        }
    }

    private void ensureList() {
        if (mList != null) {
            return;
        }
        View root = getView();
        if (root == null) {
            throw new IllegalStateException("Content view not yet created");
        }
        if (root instanceof ListView) {
            mList = (ListView) root;
        } else {
            mStandardEmptyView = (TextView) root.findViewById(INTERNAL_EMPTY_ID);
            if (mStandardEmptyView == null) {
                mEmptyView = root.findViewById(android.R.id.empty);
            } else {
                mStandardEmptyView.setVisibility(View.GONE);
            }
            mProgressContainer = root.findViewById(R.id.progress_container);
            mListContainer = root.findViewById(R.id.list_container);
            View rawListView = root.findViewById(android.R.id.list);
            if (!(rawListView instanceof ListView)) {
                if (rawListView == null) {
                    throw new RuntimeException(
                            "Your content must have a ListView whose id attribute is "
                                    + "'android.R.id.list'");
                }
                throw new RuntimeException(
                        "Content has view with id attribute 'android.R.id.list' "
                                + "that is not a ListView class");
            }
            mList = (ListView) rawListView;
            if (mEmptyView != null) {
                mList.setEmptyView(mEmptyView);
            } else if (mEmptyText != null) {
                mStandardEmptyView.setText(mEmptyText);
                mList.setEmptyView(mStandardEmptyView);
            }
        }
        mListShown = true;
        mList.setOnItemClickListener(mOnClickListener);
        if (mAdapter != null) {
            TraktCommentsAdapter adapter = mAdapter;
            mAdapter = null;
            setListAdapter(adapter);
        } else {
            // We are starting without an adapter, so assume we won't
            // have our data right away and start with the progress indicator.
            if (mProgressContainer != null) {
                setListShown(false, false);
            }
        }
        mHandler.post(mRequestFocus);
    }

    public void onEvent(TraktTask.TraktActionCompleteEvent event) {
        if (event.mTraktAction != TraktAction.SHOUT || getView() == null) {
            return;
        }

        // reenable the shout button
        mButtonShout.setEnabled(true);

        if (event.mWasSuccessful) {
            // clear the text field and show recent shout
            mEditTextShout.setText("");
            getLoaderManager().restartLoader(0, getArguments(), this);
        }
    }
}
