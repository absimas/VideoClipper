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

import android.graphics.Color;
import android.app.Activity;
import android.support.v7.app.ActionBar;
import android.support.v4.app.Fragment;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v4.view.GravityCompat;
import android.support.v4.widget.DrawerLayout;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.Toast;
import com.simas.vc.MainActivity;
import com.simas.vc.R;
import com.simas.vc.VC;
import com.simas.vc.file_chooser.FileChooser;
import com.simas.vc.helpers.Utils;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Navigation drawer fragment that contains all added items. Also manages the CAB.
 */
public class NavDrawerFragment extends Fragment {

	private static final String STATE_SELECTED_POSITION = "list_view_selected_position";
	private static final String STATE_CAB = "cab_state";

	/**
	 * Per the design guidelines, you should show the drawer on launch until the user manually
	 * collapses it. This shared preference tracks this.
	 */
	private static final String PREF_USER_LEARNED_DRAWER = "navigation_drawer_learned";
	private static final String TAG = "NavDrawerFragment";

	/**
	 * A pointer to the current callbacks instance (the Activity).
	 */
	private NavigationDrawerCallbacks mCallbacks;
	private ActionBarDrawerToggle mDrawerToggle;
	private NavCAB mNavCAB;
	private DrawerLayout mDrawerLayout;
	private HeadlessListView mDrawerList;
	private NavAdapter mAdapter;
	private View mFragmentContainerView;
	/**
	 * Specific group of listeners that are notified about the open and close states of the drawer.
	 */
	private CopyOnWriteArrayList<DrawerLayout.DrawerListener>
			mDrawerStateListeners = new CopyOnWriteArrayList<>();

	// States
	private int mPreviousSelection = ListView.INVALID_POSITION;
	private boolean mFromSavedInstanceState;
	private boolean mUserLearnedDrawer;
	public enum DrawerState {
		OPEN, OPENING, CLOSED, CLOSING
	}
	private DrawerState mDrawerState = DrawerState.CLOSED;

	public NavDrawerFragment() {}

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		// Read in the flag indicating whether or not the user has demonstrated awareness of the
		// drawer. See PREF_USER_LEARNED_DRAWER for details.
		SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(getActivity());
		mUserLearnedDrawer = sp.getBoolean(PREF_USER_LEARNED_DRAWER, false);

		if (savedInstanceState != null) {
			mPreviousSelection = savedInstanceState
					.getInt(STATE_SELECTED_POSITION, ListView.INVALID_POSITION);
			mNavCAB = savedInstanceState.getParcelable(STATE_CAB);
			mFromSavedInstanceState = true;
		}
	}

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container,
	                         Bundle savedInstanceState) {
		mDrawerList = (HeadlessListView) inflater
				.inflate(R.layout.fragment_navigation_drawer, container, false);

		final View header = createHeader(inflater, mDrawerList);
		// When available, fetch the item width and height
		if (MainActivity.sPreviewSize == 0) {
			header.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
				@Override
				public void onLayoutChange(View v, int left, int top, int right, int bottom,
				                           int oldLeft, int oldTop, int oldRight, int oldBottom) {
					int width = header.getWidth(),
							height = header.getHeight();
					if (width > 0 && height > 0) {
						header.removeOnLayoutChangeListener(this);
						MainActivity.sPreviewSize = (width > height) ? width : height;
					}
				}
			});
		}
		getListView().addHeaderView(header);

		return mDrawerList;
	}

	private View createHeader(LayoutInflater inflater, ViewGroup parent) {
		return inflater.inflate(R.layout.nav_header, parent, false);
	}

	/**
	 * Specifies if the drawer is in the open state.
	 */
	public boolean isDrawerOpen() {
		return getDrawerLayout().isDrawerOpen(mFragmentContainerView);
	}

	/**
	 * Opens or closes the drawer. Will do nothing if it's not yet initialized or is already in
	 * required state.
	 * @param open    true if the drawer should be opened, false if closed
	 */
	public void setDrawerOpen(boolean open) {
		if (open) {
			if (!isDrawerOpen()) mDrawerState = DrawerState.OPENING;
			getDrawerLayout().openDrawer(mFragmentContainerView);
		} else {
			if (isDrawerOpen()) mDrawerState = DrawerState.CLOSING;
			getDrawerLayout().closeDrawer(mFragmentContainerView);
		}
	}

	public DrawerState getDrawerState() {
		return mDrawerState;
	}

	/**
	 * Users of this fragment must call this method to set up the navigation drawer interactions.
	 * @param fragmentId   The android:id of this fragment in its activity's layout.
	 * @param drawerLayout The DrawerLayout containing this fragment's UI.
	 */
	public void setUp(int fragmentId, DrawerLayout drawerLayout) {
		mFragmentContainerView = getActivity().findViewById(fragmentId);
		mDrawerLayout = drawerLayout;

		// Set a custom shadow that overlays the main content when the drawer opens
		getDrawerLayout().setDrawerShadow(R.drawable.drawer_shadow, GravityCompat.START);
		// Unlock the drawer
		getDrawerLayout().setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED);

		// Enable drawer arrow
		ActionBar actionBar = getActionBar();
		actionBar.setDisplayHomeAsUpEnabled(true);
		actionBar.setHomeButtonEnabled(true);

		// ActionBarDrawerToggle ties together the the proper interactions
		// between the navigation drawer and the action bar app icon.
		mDrawerToggle = new ActionBarDrawerToggle(getActivity(), getDrawerLayout(), getToolbar(),
				R.string.navigation_drawer_open, R.string.navigation_drawer_close) {
			@Override
			public void onDrawerClosed(View drawerView) {
				super.onDrawerClosed(drawerView);
				mDrawerState = DrawerState.CLOSED;

				for (DrawerLayout.DrawerListener listener : mDrawerStateListeners) {
					listener.onDrawerClosed(drawerView);
				}
				getDrawerLayout().setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED);
				// Set the choice mode ONLY IF IT'S NOT SINGLE
				// Otherwise, will cause the AbsListView to reset its mCheckState array!!!
				if (getListView().getChoiceMode() != ListView.CHOICE_MODE_SINGLE) {
					getListView().setChoiceMode(ListView.CHOICE_MODE_SINGLE);
				}
				if (!isAdded()) {
					return;
				}

				getActivity().supportInvalidateOptionsMenu(); // calls onPrepareOptionsMenu()
			}

			@Override
			public void onDrawerOpened(View drawerView) {
				super.onDrawerOpened(drawerView);
				mDrawerState = DrawerState.OPEN;

				for (DrawerLayout.DrawerListener listener : mDrawerStateListeners) {
					listener.onDrawerOpened(drawerView);
				}
				if (!isAdded()) {
					return;
				}

				if (!mUserLearnedDrawer) {
					// The user manually opened the drawer; store this flag to prevent auto-showing
					// the navigation drawer automatically in the future.
					mUserLearnedDrawer = true;
					SharedPreferences sp = PreferenceManager
							.getDefaultSharedPreferences(getActivity());
					sp.edit().putBoolean(PREF_USER_LEARNED_DRAWER, true).apply();
				}

				getActivity().supportInvalidateOptionsMenu(); // calls onPrepareOptionsMenu()
			}

			@Override
			public void onDrawerSlide(View drawerView, float slideOffset) {
				super.onDrawerSlide(drawerView, slideOffset);
				for (DrawerLayout.DrawerListener listener : mDrawerStateListeners) {
					listener.onDrawerSlide(drawerView, slideOffset);
				}
			}

			@Override
			public void onDrawerStateChanged(int newState) {
				super.onDrawerStateChanged(newState);
				for (DrawerLayout.DrawerListener listener : mDrawerStateListeners) {
					listener.onDrawerStateChanged(newState);
				}
			}
		};

		// If the user hasn't 'learned' about the drawer, open it to introduce them to the drawer,
		// per the navigation drawer design guidelines.
		if (!mUserLearnedDrawer && !mFromSavedInstanceState) {
			setDrawerOpen(true);
		}

		// Defer code dependent on restoration of previous instance state.
		getDrawerLayout().post(new Runnable() {
			@Override
			public void run() {
				mDrawerToggle.syncState();
			}
		});

		getDrawerLayout().setDrawerListener(mDrawerToggle);

		// The adapter needs a reference to the list its connected to
		mAdapter = new NavAdapter(getActivity(), getListView());
		getListView().setAdapter(mAdapter);

		// Select either the default item (0) or the last selected item.
		if (mPreviousSelection != ListView.INVALID_POSITION) {
			selectItem(mPreviousSelection);
		}

		if (mNavCAB == null) {
			// Newly created CAB
			mNavCAB = new NavCAB(this);
		} else {
			// Previous CAB
			mNavCAB.mNavDrawerFragment = this;
			getListView().post(new Runnable() {
				@Override
				public void run() {
					// Update list if CAB has checked items
					if (mNavCAB.checkedPositions != null && mNavCAB.checkedPositions.size() > 0) {
						// Copy the array, because it will be modified when checking the LV items
						List<Integer> positions = new ArrayList<>();
						positions.addAll(mNavCAB.checkedPositions);

						// Update the LV's item checked state
						getListView().setChoiceMode(ListView.CHOICE_MODE_MULTIPLE_MODAL);
						for (Integer position : positions) {
							getListView().setItemChecked(position, true);
						}
					}
				}
			});
		}

		getListView().setMultiChoiceModeListener(mNavCAB);

		// Click listeners
		getListView().setOnItemClickListener(new AdapterView.OnItemClickListener() {
			@Override
			public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
				// Make sure it's not the header
				if (position != 0) {
					selectItem(position);
					// Change the item's background based on its checked state
					if (getListView().isItemChecked(position)) {
						view.setBackgroundColor(Color.DKGRAY);
					} else {
						view.setBackgroundColor(Color.TRANSPARENT);
					}
				} else {
					((MainActivity) getActivity()).showFileChooser(false);
				}
			}
		});

		getListView().setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
			@Override
			public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
				// Make sure it's not the header that's been clicked
				if (position == 0) {
					return false;
				} else {
					// Make sure a valid item has been clicked
					boolean valid = (mAdapter.getItem(position - 1).getState() == NavItem.State.VALID);
					if (!valid) return false;

					// Need to enable multiple mode and force-check manually, so CAB is called
					getListView().setChoiceMode(ListView.CHOICE_MODE_MULTIPLE_MODAL);
					getListView().setItemChecked(position, true);
					return true;
				}
			}
		});

//		// Scroll to previous ScrollY
//		Log.e(TAG, "selection: " + mPreviousSelection);
//		if (mFromSavedInstanceState && mPreviousSelection != ListView.INVALID_POSITION) {
//			getListView().setSelection(mPreviousSelection);
//		}
	}

	public void selectItem(int position) {
		getListView().setSelectedPosition(position);
		// Scroll to the selected item if in single mode
		if (getListView().getChoiceMode() == AbsListView.CHOICE_MODE_SINGLE) {
			getListView().setSelection(position);
		}
		// Notify the activity
		mCallbacks.onNavigationDrawerItemSelected(position);
	}

	@Override
	public void onAttach(Activity activity) {
		super.onAttach(activity);
		try {
			mCallbacks = (NavigationDrawerCallbacks) activity;
		} catch (ClassCastException e) {
			throw new ClassCastException("Activity must implement NavigationDrawerCallbacks.");
		}
	}

	@Override
	public void onDetach() {
		super.onDetach();
		mCallbacks = null;
	}

	@Override
	public void onSaveInstanceState(Bundle savedState) {
		super.onSaveInstanceState(savedState);
		// Save current ListView selection
		savedState.putInt(STATE_SELECTED_POSITION, getListView().getSelectedPosition());

		// Save selected items (CAB)
		if (mNavCAB != null) {
			savedState.putParcelable(STATE_CAB, mNavCAB);
		}
	}

	@Override
	public void onConfigurationChanged(Configuration newConfig) {
		super.onConfigurationChanged(newConfig);
		// Forward the new configuration the drawer toggle component.
		mDrawerToggle.onConfigurationChanged(newConfig);
	}

	private ActionBar getActionBar() {
		return ((AppCompatActivity) getActivity()).getSupportActionBar();
	}

	private Toolbar getToolbar() {
		return ((MainActivity) getActivity()).getToolbar();
	}

	/**
	 * Callbacks interface that all activities using this fragment must implement.
	 */
	public interface NavigationDrawerCallbacks {
		/**
		 * Called when an item in the navigation drawer is selected.
		 */
		void onNavigationDrawerItemSelected(int position);
	}

	public HeadlessListView getListView() {
		return mDrawerList;
	}

	public DrawerLayout getDrawerLayout() {
		return mDrawerLayout;
	}

	public void addDrawerStateListener(DrawerLayout.DrawerListener listener) {
		mDrawerStateListeners.add(listener);
	}

	public void removeDrawerStateListener(DrawerLayout.DrawerListener listener) {
		mDrawerStateListeners.remove(listener);
	}

}
