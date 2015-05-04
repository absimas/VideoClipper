package com.simas.vc.editor;

import android.content.res.Configuration;
import android.graphics.Color;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import com.simas.vc.attributes.Attributes;
import com.simas.vc.editor.player.PlayerFragment;
import com.simas.vc.nav_drawer.NavItem;
import com.simas.vc.R;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by Simas Abramovas on 2015 Mar 12.
 */

// ToDo weird super padding on TableLayout on galaxy S2
// ToDo When NavItem removed, clear and hide VideoView together with actions.
	// On NavItem removed, scroll stays

public class EditorFragment extends Fragment {

	private final String TAG = getClass().getName();

	public NavItem currentItem;
	public static int sPreviewSize;
	private PlayerFragment mPlayerFragment;
	private static final int DEFAULT_PLAYER_CONTAINER_SIZE = 300;

	private enum Data {
		ACTIONS, FILENAME, LENGTH
	}
	private Map<Data, View> mDataMap = new HashMap<>();

	public EditorFragment() {}

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, final Bundle savedState) {
		View rootView = inflater.inflate(R.layout.fragment_editor, container, false);

		// Create a nested fragment
		final View playerFragmentContainer = rootView.findViewById(R.id.player_fragment_container);
		mPlayerFragment = (PlayerFragment) getChildFragmentManager()
				.findFragmentById(R.id.player_fragment_container);
		// Recreate the fragment only if it doesn't exist
		if (mPlayerFragment == null) {
			mPlayerFragment = new PlayerFragment();
			getChildFragmentManager().beginTransaction()
					.add(R.id.player_fragment_container, mPlayerFragment)
					.addToBackStack("A")
					.commit();
		}

		// Display a black window while working
		final ViewGroup root = (ViewGroup) getActivity().getWindow().getDecorView().getRootView();
		final View black = new View(getActivity());
		black.setBackgroundColor(Color.BLACK);
		root.addView(black,ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.MATCH_PARENT);

		// Queue PlayerContainer modifications
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
						params.width = height;
					}
					sPreviewSize = params.width;
				} else {
					if (width <= 0) {
						Log.e(TAG, "width is 0 in portrait mode! Using the default...");
						params.width = params.height = DEFAULT_PLAYER_CONTAINER_SIZE;
					} else {
						params.height = width;
					}
					sPreviewSize = params.height;
				}
				Log.e(TAG, "size set to: " + sPreviewSize);
				playerFragmentContainer.setLayoutParams(params);

				// Can now remove the hiding view
				root.removeView(black);
			}
		});

		mDataMap.put(Data.FILENAME, rootView.findViewById(R.id.filename_value));
		mDataMap.put(Data.LENGTH, rootView.findViewById(R.id.length_value));
		mDataMap.put(Data.ACTIONS, rootView.findViewById(R.id.editor_actions));

		return rootView;
	}

	private NavItem.OnUpdatedListener mItemUpdateListener = new NavItem.OnUpdatedListener() {
		@Override
		public void onUpdated(NavItem.ItemAttribute attribute, Object oldValue, Object newValue) {
			switch (attribute) {
				case STATE:
					// Full update if changed to valid from in-progress
					if (newValue == NavItem.State.VALID && oldValue == NavItem.State.INPROGRESS) {
						updateEditorToCurrentItem();
					}
					break;
			}
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
			// ToDo hide player too and show some helper window or even another fragment
			mDataMap.get(Data.ACTIONS).setVisibility(View.GONE);
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
		final Attributes attrs = curItem.getAttributes();
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
				length.setText(String.format("%02f", attrs.getDuration()));
				actions.setVisibility(View.VISIBLE);
			}
		});
	}

	public NavItem getCurrentItem() {
		return currentItem;
	}

}