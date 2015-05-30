package com.simas.vc.nav_drawer;

import android.app.Activity;
import android.os.Parcel;
import android.os.Parcelable;
import android.support.v4.widget.DrawerLayout;
import android.util.Log;
import android.view.ActionMode;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.widget.AbsListView;
import android.widget.ListView;
import com.simas.vc.R;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Created by Simas Abramovas on 2015 Mar 10.
 */

// ToDo you could possibly duplicate an in-progress item => should prevent copying non-valid items
// ToDo however removing invalid or "stuck" items should still be available, so deleting should be always possible
	// ToDo deleted progressing item, should have it's parse processes cancelled.

/**
 * Navigation Contextual Action Bar (CAB) which is displayed when the user long clicks an item.
 * Provides additional actions like selection, duplication and removal.
 */
public class NavCAB implements AbsListView.MultiChoiceModeListener, Parcelable {

	public NavDrawerFragment navDrawerFragment;
	public List<Integer> checkedPositions = new ArrayList<>();

	private final String TAG = getClass().getName();
	private int mInitiallySelectedPosition = ListView.INVALID_POSITION;
	private boolean mModifiedDataSet;
	private Object mInitiallySelectedItem;

	public NavCAB(NavDrawerFragment drawerFragment) {
		navDrawerFragment = drawerFragment;
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
		int curSelection = navDrawerFragment.getCurrentlySelectedPosition();
		if (curSelection != ListView.INVALID_POSITION) {
			getListView().setAdapter(getAdapter());
			// Wtf ListView what items are you using?? childs - ok, adapter - ok, lv - fucked
			mInitiallySelectedItem = getListView().getItemAtPosition(curSelection);
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
				getAdapter().addItems(items);

				mode.finish();
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

				// Remove positions from the end of the adapter list,
				// because the checked positions are sorted in a descending order
				for (int position : checkedPositions) {
					int adapterItemPos = position - getListView().getHeaderViewsCount();
					getAdapter().getItems().remove(adapterItemPos);
					mModifiedDataSet = true;
				}

				// The following will clear the checked item array (invokes onDestroyActionMode)
				mode.finish();
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
					for (int i=0; i<getListView().getCount(); ++i) {
						Object item = getListView().getItemAtPosition(i);
						if (item == mInitiallySelectedItem) {
							navDrawerFragment.selectItem(i);
							break;
						}
					}
					mInitiallySelectedItem = null;
					// Notify the adapter. Right now the previous item will be re-selected or gone
					getAdapter().notifyDataSetChanged();
				}
			}
		});
	}

	private ListView getListView() {
		return navDrawerFragment.getList();
	}

	private NavAdapter getAdapter() {
		return navDrawerFragment.adapter;
	}

	private Activity getActivity() {
		return navDrawerFragment.getActivity();
	}

	private DrawerLayout getDrawer() {
		return navDrawerFragment.getDrawerLayout();
	}

	private NavDrawerFragment getNavDrawerFragment() {
		return navDrawerFragment;
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
