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
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ListView;
import com.simas.vc.MainActivity;
import com.simas.vc.R;
import com.simas.vc.VCException;
import com.simas.vc.file_chooser.FileChooser;
import com.simas.vc.Utils;
import com.simas.vc.background_tasks.Ffmpeg;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

// ToDo select video file -> select on drawer icon before parse completion -> if parse fails, progressBar still spins
// ToDo can't play video (big buck) should just make player invalid, but now progressBar spins.
// ToDo remove probe/mpeg process when item is removed!

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
	 * {@code isDrawerOpen} does not provide consistent data on the drawer state:
	 * (closing/opening/closed/opened). The state is saved manually via {@code setDrawerOpen}.
	 */
	private boolean mIsDrawerClosing;
	/**
	 * Specific group of listeners that are notified about the open and close states of the drawer.
	 */
	private CopyOnWriteArrayList<DrawerLayout.DrawerListener>
			mDrawerStateListeners = new CopyOnWriteArrayList<>();
	/**
	 * First initialized when the drawer is opened, so outside of {@code NavDrawerFragment} need
	 * to check if it's not {@code null}.
	 */
	public View mConcatAction;
	private MainActivity.OptionMenuCreationListener mOptionsMenuListener;

	// States
	private int mCurrentSelectedPosition = ListView.INVALID_POSITION;
	private boolean mFromSavedInstanceState;
	private boolean mUserLearnedDrawer;
	private List<NavItem> mPreviousAdapterItems;

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
		// Indicate that this fragment would like to influence the set of actions in the action bar.
		setHasOptionsMenu(true);

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

		View header = createHeader(inflater);
		getList().addHeaderView(header);

		return getList();
	}

	private View createHeader(LayoutInflater inflater) {
		return inflater.inflate(R.layout.nav_header, null);
	}

	/**
	 * Specifies if the drawer is in the open state (closing should considered as open).
	 */
	public boolean isDrawerOpen() {
		return mDrawerLayout != null && mDrawerLayout.isDrawerOpen(mFragmentContainerView);
	}

	/**
	 * Opens or closes the drawer. Will do nothing if it's not yet initialized or is already in
	 * required state.
	 * @param open    true if the drawer should be opened, false if closed
	 */
	public void setDrawerOpen(boolean open) {
		if (mDrawerLayout == null) {
			return;
		}
		if (open) {
			mDrawerLayout.openDrawer(mFragmentContainerView);
		} else {
			// If drawer is open and has been said to close, it's in the closing state
			if (isDrawerOpen()) mIsDrawerClosing = true;

			mDrawerLayout.closeDrawer(mFragmentContainerView);
		}
	}

	/**
	 * Checks if the drawer is closing. This state is determined by the {@code setDrawerOpen}
	 * method invocations.
	 */
	public boolean isDrawerClosing() {
		return mIsDrawerClosing;
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
		mDrawerLayout.setDrawerShadow(R.drawable.drawer_shadow, GravityCompat.START);
		// Unlock the drawer
		mDrawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED);

		ActionBar actionBar = getActionBar();
		actionBar.setDisplayHomeAsUpEnabled(true);
		actionBar.setHomeButtonEnabled(true);

		// ActionBarDrawerToggle ties together the the proper interactions
		// between the navigation drawer and the action bar app icon.
		mDrawerToggle = new ActionBarDrawerToggle(
				getActivity(),                      // Host Activity
				mDrawerLayout,                      // DrawerLayout object
				R.string.navigation_drawer_open,    // "open drawer" description for accessibility
				R.string.navigation_drawer_close    // "close drawer" description for accessibility
		) {
			@Override
			public void onDrawerClosed(View drawerView) {
				super.onDrawerClosed(drawerView);
				mIsDrawerClosing = false;
				for (DrawerLayout.DrawerListener listener : mDrawerStateListeners) {
					listener.onDrawerClosed(drawerView);
				}
				mDrawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED);
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
		};

		// If the user hasn't 'learned' about the drawer, open it to introduce them to the drawer,
		// per the navigation drawer design guidelines.
		if (!mUserLearnedDrawer && !mFromSavedInstanceState) {
			mDrawerLayout.openDrawer(mFragmentContainerView);
		}

		// Defer code dependent on restoration of previous instance state.
		mDrawerLayout.post(new Runnable() {
			@Override
			public void run() {
				mDrawerToggle.syncState();
			}
		});

		mDrawerLayout.setDrawerListener(mDrawerToggle);

		// The adapter needs a reference to the list its connected to
		adapter = new NavAdapter(getActivity());
		// Update adapter's list
		adapter.attachToList(getList());

		// Listen to added/removed items
		adapter.registerDataSetObserver(new DataSetObserver() {
			@Override
			public void onChanged() {
				super.onChanged();
				if (mConcatAction != null) {
					mConcatAction.setEnabled(isConcatenatable());
				}
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

	private boolean isConcatenatable() {
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

	@Override
	public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
		// If the drawer is open, show the global app actions in the action bar. See also
		// showGlobalContextActionBar, which controls the top-left area of the action bar.
		if (mDrawerLayout != null && isDrawerOpen()) {
			inflater.inflate(R.menu.drawer_menu, menu);
			final MenuItem concatItem = menu.findItem(R.id.action_concat);
			mConcatAction = concatItem.getActionView();
			// actionLayout click listener imitates default OptionsItemSelected behaviour
			mConcatAction.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					onOptionsItemSelected(concatItem);
				}
			});
			mConcatAction.setEnabled(isConcatenatable());
			showGlobalContextActionBar();

			// Call listener
			if (mOptionsMenuListener != null) {
				mOptionsMenuListener.onOptionsMenuCreated(menu);
			}
		}

		super.onCreateOptionsMenu(menu, inflater);
	}

	private static int num = 0; // ToDo remove after destination is available

	@Override
	public boolean onOptionsItemSelected(MenuItem menuItem) {
		if (mDrawerToggle.onOptionsItemSelected(menuItem)) {
			return true;
		}

		switch (menuItem.getItemId()) {
			case R.id.action_concat:
				// ToDo ask user for a destination
				String destination = Environment
						.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES).getPath();
				File output = new File(destination + File.separator + "output" + (++num) + ".mp4");
				if (output.exists()) {
					output.delete();
				}
				try {
					// Concat videos
					Ffmpeg.concat(output, adapter.getItems());
				} catch (IOException e) {
					Log.e(TAG, "Error!", e);
					new AlertDialog.Builder(getActivity())
							.setTitle(getString(R.string.error))
							.setMessage("Unrecoverable error! Please try again.")
							.setPositiveButton("OK...", null)
							.show();
				} catch (VCException e) {
					Log.e(TAG, "Error with " + e.getExtra(), e);
					new AlertDialog.Builder(getActivity())
							.setTitle(getString(R.string.error))
							.setMessage(e.getMessage())
							.setPositiveButton("OK", null)
							.show();
				}
				break;
			case R.id.action_add_item:
				// Make sure it's a new instance
				FileChooser fileChooser = (FileChooser) getActivity()
						.getSupportFragmentManager().findFragmentByTag(FileChooser.TAG);

				if (fileChooser == null) {
					fileChooser = FileChooser.getInstance();
					fileChooser.setOnFileChosenListener(this);
					fileChooser.show(getActivity().getSupportFragmentManager(), FileChooser.TAG);
				}
				return true;
		}

		return super.onOptionsItemSelected(menuItem);
	}

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
						if (mConcatAction != null) {
							mConcatAction.setEnabled(isConcatenatable());
						}

						if (newValue == NavItem.State.INVALID) {
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

	public void setOptionsMenuCreationListener(MainActivity.OptionMenuCreationListener listener) {
		mOptionsMenuListener = listener;
	}

	public void addDrawerStateListener(DrawerLayout.DrawerListener listener) {
		mDrawerStateListeners.add(listener);
	}

	public void removeDrawerStateListener(DrawerLayout.DrawerListener listener) {
		mDrawerStateListeners.remove(listener);
	}

}
