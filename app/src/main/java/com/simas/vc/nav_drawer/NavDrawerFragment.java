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

import android.app.AlertDialog;
import android.database.DataSetObserver;
import android.graphics.Color;
import android.os.Environment;
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
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.Toast;
import com.simas.vc.MainActivity;
import com.simas.vc.R;
import com.simas.vc.VC;
import com.simas.vc.file_chooser.FileChooser;
import com.simas.vc.Utils;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

// ToDo select video file -> select on drawer icon before parse completion -> if parse fails, progressBar still spins
// ToDo can't play video (big buck) should just make player invalid, but now progressBar spins.
// ToDo remove probe/mpeg process when item is removed!
// ToDo this shouldn't contain methods like isConcatenatable. It's only a drawer.

/**
 * Navigation drawer fragment that contains all added items. Also manages the CAB.
 */
public class NavDrawerFragment extends Fragment implements FileChooser.OnFileChosenListener {

	private static final String STATE_SELECTED_POSITION = "selected_navigation_drawer_position";
	private static final String STATE_ADAPTER_ITEMS = "adapter_items";
	private static final String STATE_CAB = "cab_state";

	/**
	 * Per the design guidelines, you should show the drawer on launch until the user manually
	 * collapses it. This shared preference tracks this.
	 */
	private static final String PREF_USER_LEARNED_DRAWER = "navigation_drawer_learned";
	private static final String TAG = "NavDrawerFragment";

	public static int sPreviewSize;
	public NavAdapter adapter;

	/**
	 * A pointer to the current callbacks instance (the Activity).
	 */
	private NavigationDrawerCallbacks mCallbacks;
	private ActionBarDrawerToggle mDrawerToggle;
	private NavCAB mNavCAB;
	private DrawerLayout mDrawerLayout;
	private ListView mDrawerList;
	private View mFragmentContainerView;
	/**
	 * Specific group of listeners that are notified about the open and close states of the drawer.
	 */
	private CopyOnWriteArrayList<DrawerLayout.DrawerListener>
			mDrawerStateListeners = new CopyOnWriteArrayList<>();

	// States
	private int mCurrentSelectedPosition = ListView.INVALID_POSITION;
	private boolean mFromSavedInstanceState;
	private boolean mUserLearnedDrawer;
	private List<NavItem> mPreviousAdapterItems;
	public enum DrawerState {
		OPEN, OPENING, CLOSED, CLOSING
	}
	private DrawerState mDrawerState = DrawerState.CLOSED;

	public NavDrawerFragment() {}

	@Override
	@SuppressWarnings("unchecked")
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		// Read in the flag indicating whether or not the user has demonstrated awareness of the
		// drawer. See PREF_USER_LEARNED_DRAWER for details.
		SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(getActivity());
		mUserLearnedDrawer = sp.getBoolean(PREF_USER_LEARNED_DRAWER, false);

		if (savedInstanceState != null) {
			mCurrentSelectedPosition = savedInstanceState.getInt(STATE_SELECTED_POSITION,
					ListView.INVALID_POSITION);
			mPreviousAdapterItems = savedInstanceState.getParcelableArrayList(STATE_ADAPTER_ITEMS);
			mNavCAB = savedInstanceState.getParcelable(STATE_CAB);
			mFromSavedInstanceState = true;
		}
	}

	@Override
	public void onActivityCreated(Bundle savedInstanceState) {
		super.onActivityCreated(savedInstanceState);

		// Set the previewListener for the FileChooser (if it's shown, i.e. recreated by the activity)
		FileChooser fileChooser = (FileChooser) getActivity()
				.getSupportFragmentManager().findFragmentByTag(FileChooser.TAG);

		if (fileChooser != null) {
			fileChooser.setOnFileChosenListener(this);
		}

		// Select either the default item (0) or the last selected item.
		if (mCurrentSelectedPosition != ListView.INVALID_POSITION) {
			selectItem(mCurrentSelectedPosition);
		}
	}

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container,
	                         Bundle savedInstanceState) {
		mDrawerList = (ListView) inflater
				.inflate(R.layout.fragment_navigation_drawer, container, false);

		final View header = createHeader(inflater, mDrawerList);
		// When available, fetch the item width and height
		header.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
			@Override
			public void onLayoutChange(View v, int left, int top, int right, int bottom,
			                           int oldLeft, int oldTop, int oldRight, int oldBottom) {
				int width  = header.getWidth(),
						height = header.getHeight();
				Log.e(TAG, "got: " + width + " " + height);
				if (width > 0 && height > 0) {
					header.removeOnLayoutChangeListener(this);
					sPreviewSize = (width > height) ? width : height;
				}
			}
		});
		getList().addHeaderView(header);

		return getList();
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

	/**
	 * Checks if the drawer is closing. This state is determined by the {@code setDrawerOpen}
	 * method invocations.
	 */
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
				if (getList().getChoiceMode() != ListView.CHOICE_MODE_SINGLE) {
					getList().setChoiceMode(ListView.CHOICE_MODE_SINGLE);
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
		adapter = new NavAdapter(getActivity());
		// Update adapter's list
		adapter.attachToList(getList());

		// Listen to added/removed items
		adapter.registerDataSetObserver(new DataSetObserver() {
			@Override
			public void onChanged() {
				super.onChanged();
				getToolbar().getMenu().findItem(R.id.action_concat).setEnabled(isConcatenatable());
			}
		});

		// Update adapter items if already present
		if (mPreviousAdapterItems != null) {
			for (NavItem item : mPreviousAdapterItems) {
				item.setParent(adapter);
			}
			adapter.changeItems(mPreviousAdapterItems);
		}
		getList().setAdapter(adapter);


		if (mNavCAB == null) {
			// Newly created CAB
			mNavCAB = new NavCAB(this);
		} else {
			// Previous CAB
			mNavCAB.navDrawerFragment = this;
			getList().post(new Runnable() {
				@Override
				public void run() {
					// Update list if CAB has checked items
					if (mNavCAB.checkedPositions != null && mNavCAB.checkedPositions.size() > 0) {
						// Copy the array, because it will be modified when checking the LV items
						List<Integer> positions = new ArrayList<>();
						positions.addAll(mNavCAB.checkedPositions);

						// Update the LV's item checked state
						getList().setChoiceMode(ListView.CHOICE_MODE_MULTIPLE_MODAL);
						for (Integer position : positions) {
							getList().setItemChecked(position, true);
						}
					}
				}
			});
		}

		getList().setMultiChoiceModeListener(mNavCAB);

		// Click listeners
		getList().setOnItemClickListener(new AdapterView.OnItemClickListener() {
			@Override
			public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
				// Make sure it's not the header
				if (position != 0) {
					selectItem(position);
					// Change the item's background based on its checked state
					if (getList().isItemChecked(position)) {
						view.setBackgroundColor(Color.DKGRAY);
					} else {
						view.setBackgroundColor(Color.TRANSPARENT);
					}
				} else {
					((MainActivity)getActivity()).showFileChooser();
				}
			}
		});

		getList().setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
			@Override
			public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
				// Make sure it's not the header that's been clicked
				if (position == 0) {
					return false;
				} else {
					// Make sure a valid item has been clicked
					boolean valid = (adapter.getItem(position-1).getState() == NavItem.State.VALID);
					if (!valid) return false;

					// Need to enable multiple mode and force-check manually, so CAB is called
					getList().setChoiceMode(ListView.CHOICE_MODE_MULTIPLE_MODAL);
					getList().setItemChecked(position, true);
					return true;
				}
			}
		});
	}

	// ToDo move outside this fragment
	public boolean isConcatenatable() {
		if (adapter == null) {
			// Adapter not available yet
			return false;
		} else if (adapter.getCount() < 2) {
			// There must be at least 2 videos to concatenate
			return false;
		} else {
			// Loop and look for invalid items
			for (NavItem item : adapter.getItems()) {
				if (item.getState() != NavItem.State.VALID) {
					return false;
				}
			}
		}

		return true;
	}

	public void selectItem(int position) {
		mCurrentSelectedPosition = position;
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
	public void onSaveInstanceState(Bundle out) {
		super.onSaveInstanceState(out);
		// Save current ListView selection
		out.putInt(STATE_SELECTED_POSITION, mCurrentSelectedPosition);
		// Save adapter items
		if (adapter != null) {
			out.putParcelableArrayList(STATE_ADAPTER_ITEMS, (ArrayList<NavItem>) adapter.getItems());
		}
		// Save selected items (CAB)
		if (mNavCAB != null) {
			out.putParcelable(STATE_CAB, mNavCAB);
		}
	}

	@Override
	public void onConfigurationChanged(Configuration newConfig) {
		super.onConfigurationChanged(newConfig);
		// Forward the new configuration the drawer toggle component.
		mDrawerToggle.onConfigurationChanged(newConfig);
	}

	public static int num = 0; // ToDo remove after destination is available

	/**
	 * Per the navigation drawer design guidelines, updates the action bar to show the global app
	 * 'context', rather than just what's in the current screen.
	 */
	private void showGlobalContextActionBar() {
		ActionBar actionBar = getActionBar();
		actionBar.setDisplayShowTitleEnabled(true);
		actionBar.setTitle(R.string.app_name);
	}

	private ActionBar getActionBar() {
		return ((AppCompatActivity) getActivity()).getSupportActionBar();
	}

	private Toolbar getToolbar() {
		return ((MainActivity) getActivity()).getToolbar();
	}

	@Override
	public void onChosen(File file) {
		final NavItem item = new NavItem(adapter, file);
		// Enable concat action when state validates
		item.registerUpdateListener(new NavItem.OnUpdatedListener() {
			@Override
			public void onUpdated(final NavItem.ItemAttribute attribute, final Object oldValue,
			                      final Object newValue) {
				Utils.runOnUiThread(new Runnable() {
					@Override
					public void run() {
						// Make sure the concat action button is initialized (drawer was opened)
						getToolbar().getMenu().findItem(R.id.action_concat).setEnabled(isConcatenatable());

						if (newValue == NavItem.State.INVALID) {
							// Display a toast notifying of the error if item parsing failed
							Toast.makeText(VC.getAppContext(),
									String.format(VC.getStr(R.string.format_parse_failed),
											item.getFile().getName()), Toast.LENGTH_LONG)
									.show();
							// If an invalid state was reached, remove this item from the drawer
							adapter.removeItem(item);
							adapter.notifyDataSetChanged();
						}
					}
				});

				if (newValue == NavItem.State.VALID) {
					// Upon reaching the VALID state, remove this listener
					item.unregisterUpdateListener(this);
				}
			}
		});
		adapter.addItem(item);
		adapter.notifyDataSetChanged();
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

	public ListView getList() {
		return mDrawerList;
	}

	public DrawerLayout getDrawerLayout() {
		return mDrawerLayout;
	}

	public int getCurrentlySelectedPosition() {
		return mCurrentSelectedPosition;
	}

	public void addDrawerStateListener(DrawerLayout.DrawerListener listener) {
		mDrawerStateListeners.add(listener);
	}

	public void removeDrawerStateListener(DrawerLayout.DrawerListener listener) {
		mDrawerStateListeners.remove(listener);
	}

}
