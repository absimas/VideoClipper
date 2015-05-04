package com.simas.vc.nav_drawer;

import android.app.Activity;
import android.support.v4.widget.DrawerLayout;
import android.view.ActionMode;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.widget.AbsListView;
import android.widget.ListView;

import com.simas.wvc.R;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Created by Simas Abramovas on 2015 Mar 10.
 */

public class NavCAB implements AbsListView.MultiChoiceModeListener {

	private final String TAG = getClass().getName();
	private final ListView mListView;
	private final NavAdapter mAdapter;
	private final DrawerLayout mDrawer;
	private final Activity mActivity;
	private List<Integer> mSelections = new ArrayList<>();

	public NavCAB(Activity activity, DrawerLayout drawerLayout, ListView listView,
	              NavAdapter adapter) {
		mActivity = activity;
		mDrawer = drawerLayout;
		mListView = listView;
		mAdapter = adapter;
	}

	/**
	 * Shows the CAB and checks the specified items in the {@code ListView}. Used to re-call the
	 * previous CAB state. If {@code selectedItems} is null or empty,
	 * will do nothing and CAB will not show. Must be called after attached to a {@code ListView}!
	 * @param selectedItems    items used only for parsing. Will be cleared at the end of the
	 *                            method. Can be null or empty.
	 */
	public void changeSelections(List<Integer> selectedItems) {
		if (selectedItems != null && selectedItems.size() > 0) {
			getListView().setChoiceMode(ListView.CHOICE_MODE_MULTIPLE_MODAL);
			for (Integer position : selectedItems) {
				getListView().setItemChecked(position, true);
			}
			selectedItems.clear();
		}
	}

	@Override
	public void onItemCheckedStateChanged(ActionMode mode, int position, final long id,
	                                      boolean checked) {
		// Clicking header closes CAB, and opens the FileChooser
		if (position == 0) {
			mode.finish();
			mListView.post(new Runnable() {
				@Override
				public void run() {
					mListView.performItemClick(null, 0, id);
				}
			});
			mSelections.clear();
		} else {
			if (checked) {
				// Remove duplicates
				while (mSelections.remove((Integer) position));
				mSelections.add(position);
			} else {
				mSelections.remove((Integer) position);
			}
		}
	}

	@Override
	public boolean onCreateActionMode(ActionMode mode, Menu menu) {
		mSelections.clear();
		// When CAB is open, drawer is un-closeable
		getDrawer().setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_OPEN);

		MenuInflater inflater = getActivivty().getMenuInflater();
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
			case R.id.nav_contextual_action_select_all: {
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
				for (int position : mSelections) {
					int adapterItemPos = position - getListView().getHeaderViewsCount();
					// Create a semi-deep item copy
					NavItem navItem = new NavItem(mAdapter.getItem(adapterItemPos));
					items.add(navItem);
				}
				// Add the list to other items in adapter
				mAdapter.addItems(items);

				mode.finish();
				return true;
			}
			case R.id.nav_contextual_action_remove: {
				// Sort selected item positions in descending order
				Collections.sort(mSelections, new Comparator<Integer>() {
					@Override
					public int compare(Integer lhs, Integer rhs) {
						return rhs - lhs;
					}
				});

				// Remove positions from the end of the adapter list,
				// because the selected positions are sorted in a desceding order
				for (int position : mSelections) {
					int adapterItemPos = position - getListView().getHeaderViewsCount();
					getAdapter().getItems().remove(adapterItemPos);
				}

				// Update the adapter
				getAdapter().notifyDataSetChanged();

				// The following will clear the selected item array (invokes onDestroyActionMode)
				mode.finish();
				return true;
			}
			default:
				return false;
		}
	}

	@Override
	public void onDestroyActionMode(ActionMode mode) {
		mSelections.clear();

		// Drawer is closeable again
		getDrawer().setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED);

		// Only a single item may be selected
		getListView().post(new Runnable() {
			@Override
			public void run() {
				getListView().setChoiceMode(ListView.CHOICE_MODE_SINGLE);
			}
		});
	}

	private ListView getListView() {
		return mListView;
	}

	private NavAdapter getAdapter() {
		return mAdapter;
	}

	private Activity getActivivty() {
		return mActivity;
	}

	private DrawerLayout getDrawer() {
		return mDrawer;
	}

	public List<Integer> getSelectedItemPositions() {
		return mSelections;
	}

}
