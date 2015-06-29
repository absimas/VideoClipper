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
package com.simas.vc.nav_drawer;

import android.app.Activity;
import android.os.Parcel;
import android.os.Parcelable;
import android.support.v4.widget.DrawerLayout;
import android.view.ActionMode;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.widget.AbsListView;
import android.widget.HeaderViewListAdapter;
import android.widget.ListView;

import com.simas.vc.MainActivity;
import com.simas.vc.R;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Navigation Contextual Action Bar (CAB) which is displayed when the user long clicks an item.
 * Provides additional actions like selection, duplication and removal.
 */
public class NavCAB implements AbsListView.MultiChoiceModeListener, Parcelable {

	NavDrawerFragment mNavDrawerFragment;
	public List<Integer> checkedPositions = new ArrayList<>();

	private final String TAG = getClass().getName();
	private int mInitiallySelectedPosition = ListView.INVALID_POSITION;
	private boolean mModifiedDataSet;
	private Object mInitiallySelectedItem;

	public NavCAB(NavDrawerFragment drawerFragment) {
		mNavDrawerFragment = drawerFragment;
	}

	@Override
	public void onItemCheckedStateChanged(ActionMode mode, int position, final long id,
	                                      boolean checked) {
		// Clicking header closes CAB, and opens the FileChooser
		if (position == 0) {
			mode.finish();
			getListView().post(new Runnable() {
				@Override
				public void run() {
					getListView().performItemClick(null, 0, id);
				}
			});
			checkedPositions.clear();
		} else {
			if (checked) {
				// If an item is not valid -- don't select it
				int adapterPos = position-1;
				if (getAdapter().getItem(adapterPos).getState() != NavItem.State.VALID) {
					getListView().setItemChecked(position, false);
					return;
				}
				// Remove duplicates
				while (checkedPositions.remove((Integer) position));
				checkedPositions.add(position);
			} else {
				checkedPositions.remove((Integer) position);
			}
		}
	}

	@Override
	public boolean onCreateActionMode(ActionMode mode, Menu menu) {
		// Save the previously saved item pointer
		mInitiallySelectedPosition = getListView().getSelectedPosition();
		if (mInitiallySelectedPosition != ListView.INVALID_POSITION) {
			mInitiallySelectedItem = getListView().getItemAtPosition(mInitiallySelectedPosition);
		}

		// When CAB is open, drawer is un-closeable
		getDrawer().setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_OPEN);

		MenuInflater inflater = getActivity().getMenuInflater();
		inflater.inflate(R.menu.nav_cab, menu);
		return true;
	}

	@Override
	public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
		return false;
	}

	@Override
	public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
		switch (item.getItemId()) {
			case R.id.nav_contextual_action_check_all: {
				int headerCount = getListView().getHeaderViewsCount();
				int childrenCount = getListView().getCount() - getListView().getFooterViewsCount();

				// Headers (at the top) and footers (at the bottom) will be skipped with this loop
				for (int i=headerCount; i<childrenCount; ++i) {
					getListView().setItemChecked(i, true);
				}
			}
				return true;
			case R.id.nav_contextual_action_copy: {
				// Clone items and add to a list
				List<NavItem> items = new ArrayList<>();
				for (int position : checkedPositions) {
					int adapterItemPos = position - getListView().getHeaderViewsCount();
					// Create a semi-deep item copy
					NavItem navItem = new NavItem(getAdapter().getItem(adapterItemPos));
					items.add(navItem);
							mModifiedDataSet = true;
				}
				// Add the list to other items in adapter
				MainActivity.sItems.addAll(items);

				mode.finish(); // Invoke onDestroyActionMode
				return true;
			}
			case R.id.nav_contextual_action_remove: {
				// Sort checked item positions in descending order
				Collections.sort(checkedPositions, new Comparator<Integer>() {
					@Override
					public int compare(Integer lhs, Integer rhs) {
						return rhs - lhs;
					}
				});

				// Remove positions from the end of the adapter list
				for (int position : checkedPositions) {
					int adapterItemPos = position - getListView().getHeaderViewsCount();
					MainActivity.sItems.remove(adapterItemPos);
					mModifiedDataSet = true;
				}

				mode.finish(); // Invoke onDestroyActionMode
				return true;
			}
			default:
				return false;
		}
	}

	@Override
	public void onDestroyActionMode(ActionMode mode) {
		checkedPositions.clear();

		// Drawer is closeable again
		getDrawer().setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED);

		// Return to a single-item selection mode
		getListView().post(new Runnable() {
			@Override
			public void run() {
				// If DataSet was changed, notify the adapter
				if (mModifiedDataSet) {
					// Update the adapter
					getAdapter().notifyDataSetChanged();
					mModifiedDataSet = false;
				}

				getListView().setChoiceMode(ListView.CHOICE_MODE_SINGLE);

				// Revert to the previous selection (if anything was selected)
				if (mInitiallySelectedItem != null) {
					// Loop items to find the one with the correct id (if it's still there)
					int newPosition = ListView.INVALID_POSITION;
					for (int i=0; i<getListView().getCount(); ++i) {
						Object item = getListView().getItemAtPosition(i);
						if (item == mInitiallySelectedItem) {
							newPosition = i;
							break;
						}
					}
					if (newPosition != ListView.INVALID_POSITION) {
						// The previously selected item was found, select it again
						mNavDrawerFragment.selectItem(newPosition);
					} else {
						// Previously selected item was removed.
						if (mInitiallySelectedPosition - getListView().getHeaderViewsCount()
								< getAdapter().getCount() - 1) {
							// There are enough items, select previously selected position
							selectListItemImplicitly(mInitiallySelectedPosition);
						} else if (getAdapter().getCount() > 0) {
							// There are item, select the last one
							selectListItemImplicitly(getAdapter().getCount());
						} else {
							// There are no items, don't select anything
						}
					}
					mInitiallySelectedItem = null;
					// Notify the adapter. The previous item will be re-selected or gone
					getAdapter().notifyDataSetChanged();
				}
			}
		});
	}

	/**
	 * Selects ListView's item implicitly without invoking {@link NavDrawerFragment#selectItem(int)}
	 */
	private void selectListItemImplicitly(int position) {
		getListView().setItemChecked(position, true);
		getListView().setSelection(position);
		getListView().setSelectedPosition(position);
	}

	private HeadlessListView getListView() {
		return mNavDrawerFragment.getListView();
	}

	private NavAdapter getAdapter() {
		if (getListView().getAdapter() instanceof HeaderViewListAdapter) {
			HeaderViewListAdapter hvla = (HeaderViewListAdapter) getListView().getAdapter();
			return (NavAdapter) hvla.getWrappedAdapter();
		} else {
			return (NavAdapter) getListView().getAdapter();
		}
	}

	private Activity getActivity() {
		return mNavDrawerFragment.getActivity();
	}

	private DrawerLayout getDrawer() {
		return mNavDrawerFragment.getDrawerLayout();
	}

	private NavDrawerFragment getNavDrawerFragment() {
		return mNavDrawerFragment;
	}

	/* Parcelable */
	public void writeToParcel(Parcel out, int flags) {
		// Save the selected position
		out.writeInt(mInitiallySelectedPosition);
		// Save the items that were checked by the CAB
		out.writeSerializable((java.io.Serializable) checkedPositions);
	}

	@Override
	public int describeContents() {
		return 0;
	}

	public static final Parcelable.Creator<NavCAB> CREATOR = new Parcelable.Creator<NavCAB>() {
		public NavCAB createFromParcel(Parcel in) {
			return new NavCAB(in);
		}

		public NavCAB[] newArray(int size) {
			return new NavCAB[size];
		}
	};

	@SuppressWarnings("unchecked")
	private NavCAB(Parcel in) {
		mInitiallySelectedPosition = in.readInt();
		checkedPositions = (List<Integer>) in.readSerializable();
	}



}
