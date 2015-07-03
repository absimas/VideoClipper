/*
 * Copyright (c) 2015. Simas Abramovas
 *
 * This file is part of VideoClipper.
 *
 * VideoClipper is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * VideoClipper is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with VideoClipper. If not, see <http://www.gnu.org/licenses/>.
 */
package com.simas.vc.editor;

import android.os.Bundle;
import android.os.Handler;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.simas.vc.MainActivity;
import com.simas.vc.editor.tree_view.TreeAdapter;
import com.simas.vc.editor.tree_view.TreeView;
import com.simas.vc.helpers.DelayedHandler;
import com.simas.vc.helpers.Utils;
import com.simas.vc.attributes.FileAttributes;
import com.simas.vc.editor.player.PlayerFragment;
import com.simas.vc.nav_drawer.NavItem;
import com.simas.vc.R;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Fragment containing the information about the opened NavItem, as well as actions that can be
 * invoked. The fragment doesn't save its state because {@link #mItem} is a <b>shared</b>
 * object that's assigned by {@link com.simas.vc.MainActivity}.
 */
public class EditorFragment extends Fragment {

	private static final String STATE_PREVIOUS_ITEM = "previous_item";
	private final String TAG = getClass().getName();

	private NavItem mItem;
	private View mProgressOverlay;
	private PlayerFragment mPlayerFragment;

	private enum Data {
		ACTIONS, FILENAME, DURATION, SIZE, STREAM_TREE
	}
	private Map<Data, View> mDataMap = new HashMap<>();
	/**
	 * Handler runs all the messages posted to it only when the fragment layout is ready, i.e. at
	 * the end of {@code onCreateView}.
	 */
	private DelayedHandler mDelayedHandler = new DelayedHandler(new Handler());


	/**
	 * For progress overlay to be hidden, multiple states must be fulfilled
	 * ({@link #modifyProgressOverlayStates(boolean, ProgressStates)}). To save the states we use
	 * a {@link com.simas.vc.helpers.Utils.FlagContainer} with respective bits set for each flag.
	 * Flags have a specific value of 1, 2, 4, 8, etc. (as every bit does). They are specified at
	 * {@link com.simas.vc.editor.EditorFragment.ProgressStates}.
	 */
	private Utils.FlagContainer mProgressOverlayStates = new Utils.FlagContainer();
	/**
	 * @see #mProgressOverlayStates
	 */
	private enum ProgressStates {
		PLAYER_CONTAINER_DRAWN(1),
		ITEM_VALID(2),
		PLAYER_ITEM_SET(4);

		private int mBit;

		ProgressStates(int bit) {
			mBit = bit;
		}

		public int getBit() {
			return mBit;
		}

		public static int getCombinedBitValue() {
			return (int) (Math.pow(2, values().length) - 1);
		}

	}

	public EditorFragment() {}
	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedState) {
		View root = inflater.inflate(R.layout.fragment_editor, container, false);
		// Overlay a ProgressBar when preparing the PlayerFragment for the first time
		mProgressOverlay = root.findViewById(R.id.editor_progress_overlay);

		mPlayerFragment = (PlayerFragment) getChildFragmentManager()
				.findFragmentById(R.id.player_fragment);

		// Queue container modifications until it's measured
		final View playerContainer = mPlayerFragment.getContainer();
		final Runnable playerResize = new Runnable() {
			@Override
			public void run() {
				ViewGroup.LayoutParams params = playerContainer.getLayoutParams();
				params.width = MainActivity.sPlayerContainerSize;
				params.height = MainActivity.sPlayerContainerSize;
				playerContainer.setLayoutParams(params);

				playerContainer.post(new Runnable() {
					@Override
					public void run() {
						modifyProgressOverlayStates(true, ProgressStates.PLAYER_CONTAINER_DRAWN);
					}
				});
			}
		};
		if (MainActivity.sPlayerContainerSize != 0) {
			playerResize.run();
		} else {
			playerContainer.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
				@Override
				public void onLayoutChange(View v, int left, int top, int right, int bottom,
				                           int oldLeft, int oldTop, int oldRight, int oldBottom) {
					int size = Math.max(playerContainer.getWidth(), playerContainer.getHeight());
					if (size <= 0) return;
					MainActivity.sPlayerContainerSize = size;
					playerContainer.removeOnLayoutChangeListener(this);
					playerResize.run();
				}
			});
		}


		mDelayedHandler.resume();

		View actions = root.findViewById(R.id.editor_actions);
		mDataMap.put(Data.ACTIONS, actions);
		mDataMap.put(Data.FILENAME, actions.findViewById(R.id.filename_value));
		mDataMap.put(Data.SIZE, actions.findViewById(R.id.size_value));
		mDataMap.put(Data.DURATION, actions.findViewById(R.id.duration_value));
		mDataMap.put(Data.STREAM_TREE, actions.findViewById(R.id.tree_view));

		if (savedState != null) {
			NavItem previousItem = savedState.getParcelable(STATE_PREVIOUS_ITEM);
			if (previousItem != null) {
				setItem(previousItem);
			}
		}

		return root;
	}

	private NavItem.OnUpdatedListener mItemValidationListener = new NavItem.OnUpdatedListener() {
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
								updateFields();
								modifyProgressOverlayStates(true, ProgressStates.ITEM_VALID);
							}
							break;
					}
				}
			});
		}
	};

	/**
	 * Set the item. Editor attributes will be changed once the item has been validated.
	 * @return true if the item was changed, false otherwise
	 */
	public boolean setItem(final NavItem newItem) {
		if (getItem() == newItem) {
			return false;
		}

		// Clear listener from the previous item (if it's set)
		if (getItem() != null) {
			getItem().unregisterUpdateListener(mItemValidationListener);
		}

		// 2 flags will be re-set, remove them now to reveal the progress overlay
		modifyProgressOverlayStates(false, ProgressStates.PLAYER_ITEM_SET);
		modifyProgressOverlayStates(false, ProgressStates.ITEM_VALID);

		mItem = newItem;

		// Update attributes and add listeners if the new item is not null
		if (getItem() != null) {
			// Present the new item if it's ready, otherwise wait for it
			switch (getItem().getState()) {
				case VALID:
					updateFields();
					modifyProgressOverlayStates(true, ProgressStates.ITEM_VALID);
					break;
				case INPROGRESS:
					modifyProgressOverlayStates(false, ProgressStates.ITEM_VALID);
					break;
			}

			// Add an update listener
			getItem().registerUpdateListener(mItemValidationListener);
		}

		return true;
	}

	@Override
	public void onSaveInstanceState(Bundle outState) {
		super.onSaveInstanceState(outState);
		outState.putParcelable(STATE_PREVIOUS_ITEM, mItem);
	}

	public PlayerFragment getPlayerFragment() {
		return mPlayerFragment;
	}

	/**
	 * Update attributes to match {@link #mItem}.
	 */
	private void updateFields() {
		if (getActivity() == null) return;

		final NavItem item = getItem();
		final FileAttributes attributes = item.getAttributes();
		final TreeView treeView = (TreeView) mDataMap.get(Data.STREAM_TREE);
		final View actions = mDataMap.get(Data.ACTIONS);

		/* Update text fields */
		final TextView filename = (TextView) mDataMap.get(Data.FILENAME);
		final TextView size = (TextView) mDataMap.get(Data.SIZE);
		final TextView duration = (TextView) mDataMap.get(Data.DURATION);

		filename.setText(item.getFile().getName());
		size.setText(Utils.bytesToMb(attributes.getSize()));
		duration.setText(Utils.secsToFullTime(attributes.getDuration().intValue()));

		/* Parse attributes to a TreeView*/
		List<Object> data = new ArrayList<>(2);
		// Order is important here so the TreeAdapter can distinguish the types properly!
		data.add(attributes.getAudioStreams());
		data.add(attributes.getVideoStreams());
		TreeAdapter treeAdapter = new TreeAdapter(data);
		treeView.setAdapter(treeAdapter);

		getActivity().runOnUiThread(new Runnable() {
			@Override
			public void run() {
				actions.setVisibility(View.VISIBLE);

				getPlayerFragment().post(new Runnable() {
					@Override
					public void run() {
						getPlayerFragment().setItem(item);
						modifyProgressOverlayStates(true, ProgressStates.PLAYER_ITEM_SET);
					}
				});
			}
		});
	}

	public NavItem getItem() {
		return mItem;
	}

	/**
	 * @see #mProgressOverlayStates
	 */
	private void modifyProgressOverlayStates(boolean fulfill, ProgressStates state) {
		if (fulfill) {
			if (!mProgressOverlayStates.addFlag(state.getBit())) {
				Log.e(TAG, state.name() + " flag was already set.");
			}
			// If all flags set, hide the progress overlay
			if (mProgressOverlayStates.getFlags() == ProgressStates.getCombinedBitValue()) {
				mProgressOverlay.setVisibility(View.GONE);
			}
		} else {
			mProgressOverlayStates.removeFlag(state.getBit());
			mProgressOverlay.setVisibility(View.VISIBLE);
		}
	}

	/**
	 * Queues the given runnable
	 * @param runnable    message to be queued
	 */
	public void post(Runnable runnable) {
		mDelayedHandler.add(runnable);
	}

}