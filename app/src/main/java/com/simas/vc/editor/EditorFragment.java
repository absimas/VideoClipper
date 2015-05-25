package com.simas.vc.editor;

import android.content.res.Configuration;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import com.simas.vc.DelayedHandler;
import com.simas.vc.Utils;
import com.simas.vc.editor.tree_view.TreeParser;
import com.simas.vc.editor.player.PlayerFragment;
import com.simas.vc.nav_drawer.NavItem;
import com.simas.vc.R;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by Simas Abramovas on 2015 Mar 12.
 */

public class EditorFragment extends Fragment {

	private final String TAG = getClass().getName();

	public NavItem currentItem;
	private PlayerFragment mPlayerFragment;

	private enum Data {
		ACTIONS, FILENAME, DURATION, SIZE, STREAMS, AUDIO_STREAMS, VIDEO_STREAMS
	}
	private Map<Data, View> mDataMap = new HashMap<>();
	/**
	 * Handler runs all the messages posted to it only when the fragment is ready, i.e. at the end
	 * of {@code onCreateView}. Messages can be added by calling fragment's {@code post} method.
	 */
	private DelayedHandler mDelayedHandler = new DelayedHandler(new Handler());

	public EditorFragment() {}

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, final Bundle savedState) {
		final View rootView = inflater.inflate(R.layout.fragment_editor, container, false);

		// Create a nested fragment
		final View playerFragmentContainer = rootView.findViewById(R.id.player_fragment_container);
		mPlayerFragment = (PlayerFragment) getChildFragmentManager()
				.findFragmentById(R.id.player_fragment_container);
		// Recreate the fragment only if it doesn't exist
		if (mPlayerFragment == null) {
			mPlayerFragment = new PlayerFragment();
			getChildFragmentManager().beginTransaction()
					.add(R.id.player_fragment_container, mPlayerFragment)
					.commit();
		}

		// Display a black window while working
		final ViewGroup root = (ViewGroup) getActivity().getWindow().getDecorView().getRootView();
		final View black = new View(getActivity());
		black.setBackgroundColor(Color.RED);

		// Fragment won't be visible when HelperFragment is shown on top.
		// No need for a black view then.
		final boolean visibleOnCreation = isVisible();
		if (visibleOnCreation) {
			root.addView(black,
					ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
		}

		// Queue PlayerContainer modifications to when its first measured
		playerFragmentContainer.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
			@Override
			public void onLayoutChange(View v, int left, int top, int right, int bottom,
			                           int oldLeft, int oldTop, int oldRight, int oldBottom) {
				playerFragmentContainer.removeOnLayoutChangeListener(this);

				// Show the black view only if it wasn't added yet
				if (root.getParent() == null) {
					root.addView(black, ViewGroup.LayoutParams.MATCH_PARENT,
							ViewGroup.LayoutParams.MATCH_PARENT);
				}

				playerFragmentContainer.post(new Runnable() {
					@Override
					public void run() {
						// Set width to be equal to height (or the other way round)
						ViewGroup.LayoutParams params = playerFragmentContainer.getLayoutParams();
						int width = playerFragmentContainer.getWidth();
						int height = playerFragmentContainer.getHeight();
						if (getResources().getConfiguration()
								.orientation == Configuration.ORIENTATION_LANDSCAPE) {
							if (height <= 0) {
								Log.w(TAG, "Height is 0 in landscape! Using screen's height...");
								params.width = params.height = Utils.getScreenSize().getHeight();
							} else {
								params.height = height;
								//noinspection SuspiciousNameCombination
								params.width = height;
							}
						} else {
							if (width <= 0) {
								Log.w(TAG, "Width is 0 in portrait! Using screen's width...");
								params.width = params.height = Utils.getScreenSize().getWidth();
							} else {
								params.width = width;
								//noinspection SuspiciousNameCombination
								params.height = width;
							}
						}
						playerFragmentContainer.setLayoutParams(params);

						// Queue the removal of the black view
						playerFragmentContainer.post(new Runnable() {
							@Override
							public void run() {
								root.removeView(black);
								if (!visibleOnCreation) {
									mDelayedHandler.resume();
								}
							}
						});
					}
				});
			}
		});

		View actions = rootView.findViewById(R.id.editor_actions);
		mDataMap.put(Data.ACTIONS, actions);
		mDataMap.put(Data.FILENAME, actions.findViewById(R.id.filename_value));
		mDataMap.put(Data.SIZE, actions.findViewById(R.id.size_value));
		mDataMap.put(Data.DURATION, actions.findViewById(R.id.duration_value));
		mDataMap.put(Data.STREAMS, actions.findViewById(R.id.stream_container));

		if (visibleOnCreation) {
			mDelayedHandler.resume();
		}

		return rootView;
	}

	private NavItem.OnUpdatedListener mItemUpdateListener = new NavItem.OnUpdatedListener() {
		@Override
		public void onUpdated(final NavItem.ItemAttribute attribute, final Object oldValue,
		                      final Object newValue) {
			post(new Runnable() {
				@Override
				public void run() {
					switch (attribute) {
						case STATE:
							// Full update if changed to valid from in-progress
							if (newValue == NavItem.State.VALID &&
									oldValue == NavItem.State.INPROGRESS) {
								updateEditorToCurrentItem();
							}
							break;
					}
				}
			});
		}
	};

	public void setCurrentItem(final NavItem newItem) {
		// Change item
		final NavItem previousItem = currentItem;
		currentItem = newItem;

		// Clear previous item listener
		if (previousItem != null && previousItem != newItem) {
			previousItem.unregisterUpdateListener(mItemUpdateListener);
		}

		if (newItem == null) {
			return;
		}

		// Present the new item if it's ready, otherwise
		switch (newItem.getState()) {
			case VALID:
				updateEditorToCurrentItem();
				break;
			case INPROGRESS:
				mPlayerFragment.setProgressVisible(true);
				break;
		}

		// Add an update listener
		newItem.registerUpdateListener(mItemUpdateListener);
	}

	private void updateEditorToCurrentItem() {
		if (getActivity() == null) return;
		final NavItem curItem = currentItem;
		final TextView filename = (TextView) mDataMap.get(Data.FILENAME);
		final TextView size = (TextView) mDataMap.get(Data.SIZE);
		final TextView duration = (TextView) mDataMap.get(Data.DURATION);
		final ViewGroup streams = (ViewGroup) mDataMap.get(Data.STREAMS);
		final View actions = mDataMap.get(Data.ACTIONS);

		// Prep strings
		double mb = curItem.getAttributes().getSize() / 1024.0 / 1024.0;
		final String sizeStr = String.format("%.2f mb", mb);
		final String durationStr = Utils
				.secsToFullTime(curItem.getAttributes().getDuration().intValue());

		final TreeParser slp = new TreeParser(getActivity(), curItem.getAttributes());

		getActivity().runOnUiThread(new Runnable() {
			@Override
			public void run() {
				mPlayerFragment.post(new Runnable() {
					@Override
					public void run() {
						mPlayerFragment.setVideoPath(curItem.getFile().getPath());
					}
				});

				filename.setText(curItem.getFile().getName());
				size.setText(sizeStr);
				duration.setText(durationStr);
				streams.removeAllViews();
				streams.addView(slp.layout);
				actions.setVisibility(View.VISIBLE);
			}
		});
	}

	public NavItem getCurrentItem() {
		return currentItem;
	}

	/**
	 * Queues the given runnable
	 * @param runnable    message to be queued
	 */
	public void post(Runnable runnable) {
		mDelayedHandler.add(runnable);
	}

}