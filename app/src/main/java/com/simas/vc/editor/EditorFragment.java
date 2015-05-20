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
import com.simas.vc.editor.player.PlayerFragment;
import com.simas.vc.nav_drawer.NavItem;
import com.simas.vc.R;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by Simas Abramovas on 2015 Mar 12.
 */

// ToDo weird super padding on TableLayout on galaxy S2
// ToDo test invalid items should be removed from the drawer immediately. Just show an AlertDialog.

public class EditorFragment extends Fragment {

	private final String TAG = getClass().getName();

	public NavItem currentItem;
	private PlayerFragment mPlayerFragment;
	private static final int DEFAULT_PLAYER_CONTAINER_SIZE = 300;
	public static int sPreviewSize = DEFAULT_PLAYER_CONTAINER_SIZE;

	private enum Data {
		ACTIONS, FILENAME, LENGTH
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
								Log.e(TAG, "height is 0 in landscape mode! Using the default...");
								params.width = params.height = DEFAULT_PLAYER_CONTAINER_SIZE;
							} else {
								params.height = height;
								//noinspection SuspiciousNameCombination
								params.width = height;
							}
							sPreviewSize = params.width;
						} else {
							if (width <= 0) {
								Log.e(TAG, "width is 0 in portrait mode! Using the default...");
								params.width = params.height = DEFAULT_PLAYER_CONTAINER_SIZE;
							} else {
								params.width = width;
								//noinspection SuspiciousNameCombination
								params.height = width;
							}
							sPreviewSize = params.height;
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

		mDataMap.put(Data.FILENAME, rootView.findViewById(R.id.filename_value));
		mDataMap.put(Data.LENGTH, rootView.findViewById(R.id.length_value));
		mDataMap.put(Data.ACTIONS, rootView.findViewById(R.id.editor_actions));

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
							if (newValue == NavItem.State.VALID && oldValue == NavItem.State.INPROGRESS) {
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
		final NavItem curItem = currentItem;
		final TextView filename = (TextView) mDataMap.get(Data.FILENAME);
		final TextView length = (TextView) mDataMap.get(Data.LENGTH);
		final View actions = mDataMap.get(Data.ACTIONS);

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
				length.setText(String.format("%.2f", curItem.getAttributes().getDuration()));
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