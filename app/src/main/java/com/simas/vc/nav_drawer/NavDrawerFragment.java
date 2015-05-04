package com.simas.vc.nav_drawer;

import android.app.AlertDialog;
import android.content.Context;
import android.database.DataSetObserver;
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
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import com.simas.vc.MainActivity;
import com.simas.vc.R;
import com.simas.vc.VCException;
import com.simas.vc.file_chooser.FileChooser;
import com.simas.vc.Utils;
import com.simas.vc.attributes.Attributes;
import com.simas.vc.background_tasks.Ffmpeg;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

// ToDo change the opened NavItem and currentSelectedPosition when item when CAB action done
// ToDo clicking on drawer header doesn't remove the selection

public class NavDrawerFragment extends Fragment implements FileChooser.OnFileChosenListener {

	/**
	 * Remember the position of the selected item.
	 */
	private static final String STATE_SELECTED_POSITION = "selected_navigation_drawer_position";
	private static final String STATE_ADAPTER_ITEMS = "adapter_items";
	private static final String STATE_CAB_SELECTED_ITEMS = "cab_selected_items";

	/**
	 * Per the design guidelines, you should show the drawer on launch until the user manually
	 * expands it. This shared preference tracks this.
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
	private MenuItem mMenuConcatItem;

	// States
	private int mCurrentSelectedPosition = 0;
	private boolean mFromSavedInstanceState;
	private boolean mUserLearnedDrawer;

	// SavedInstanceObjects
	private List<NavItem> mPreviousAdapterItems;
	private List<Integer> mPreviouslySelectedItems;

	public NavDrawerFragment() {
	}

	@Override
	@SuppressWarnings("unchecked")
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		// Read in the flag indicating whether or not the user has demonstrated awareness of the
		// drawer. See PREF_USER_LEARNED_DRAWER for details.
		SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(getActivity());
		mUserLearnedDrawer = sp.getBoolean(PREF_USER_LEARNED_DRAWER, false);

		if (savedInstanceState != null) {
			mCurrentSelectedPosition = savedInstanceState.getInt(STATE_SELECTED_POSITION);
			mPreviousAdapterItems = savedInstanceState.getParcelableArrayList(STATE_ADAPTER_ITEMS);
			mPreviouslySelectedItems = (ArrayList<Integer>) savedInstanceState
					.getSerializable(STATE_CAB_SELECTED_ITEMS);
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
		selectItem(mCurrentSelectedPosition);
	}

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container,
	                         Bundle savedInstanceState) {
		mDrawerList = (ListView) inflater.inflate(
				R.layout.fragment_navigation_drawer, container, false);

		View header = createHeader(inflater);
		mDrawerList.addHeaderView(header);

		return mDrawerList;
	}

	private View createHeader(LayoutInflater inflater) {
		LinearLayout header = (LinearLayout) inflater.inflate(R.layout.nav_item, null);
		ImageView preview = (ImageView) header.findViewById(R.id.preview_image);
		preview.setImageResource(R.drawable.ic_action_new);
		return header;
	}

	private void hideKeyboard() {
		// Check if no view has focus:
		View view = getActivity().getCurrentFocus();
		if (view != null) {
			InputMethodManager inputManager = (InputMethodManager) getActivity()
					.getSystemService(Context.INPUT_METHOD_SERVICE);
			inputManager.hideSoftInputFromWindow(view.getWindowToken(),
					InputMethodManager.HIDE_NOT_ALWAYS);
		}
	}

	public boolean isDrawerOpen() {
		return mDrawerLayout != null && mDrawerLayout.isDrawerOpen(mFragmentContainerView);
	}

	/**
	 * Opens or closes the drawer. Will do nothing if it's not yet initialized or is already in
	 * required state.
	 * @param open    true if the drawer should be opened, false if closed
	 */
	public void setDrawerOpen(boolean open) {
		if (mDrawerLayout == null) return;
		if (open) {
			mDrawerLayout.openDrawer(mFragmentContainerView);
		} else {
			mDrawerLayout.closeDrawer(mFragmentContainerView);
		}
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
				mDrawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED);
				mDrawerList.setChoiceMode(ListView.CHOICE_MODE_SINGLE);
				if (!isAdded()) {
					return;
				}

				getActivity().supportInvalidateOptionsMenu(); // calls onPrepareOptionsMenu()
			}

			@Override
			public void onDrawerOpened(View drawerView) {
				super.onDrawerOpened(drawerView);
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

		// Sadly adapter needs a reference to the list its connected to
		adapter = new NavAdapter(getActivity());
		// Update adapter's list
		adapter.attachToList(mDrawerList);

		// Listen to added/removed items
		adapter.registerDataSetObserver(new DataSetObserver() {
			@Override
			public void onChanged() {
				super.onChanged();
				if (mMenuConcatItem != null) {
					mMenuConcatItem.setEnabled(isConcatenatable());
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
		mDrawerList.setAdapter(adapter);


		// CAB previewListener
		mNavCAB = new NavCAB(getActivity(), mDrawerLayout, mDrawerList, adapter);
		mDrawerList.setMultiChoiceModeListener(mNavCAB);
		mDrawerList.post(new Runnable() {
			@Override
			public void run() {
				mNavCAB.changeSelections(mPreviouslySelectedItems);
			}
		});

		// Click listeners
		mDrawerList.setOnItemClickListener(new AdapterView.OnItemClickListener() {
			@Override
			public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
				// If it's not the header that's been clicked
				if (position != 0) {
					selectItem(position);
				} else {
					// Make sure it's a new instance
					FileChooser fileChooser = (FileChooser) getActivity()
							.getSupportFragmentManager().findFragmentByTag(FileChooser.TAG);

					if (fileChooser == null) {
						fileChooser = FileChooser.getInstance();
						fileChooser.setOnFileChosenListener(NavDrawerFragment.this);
						fileChooser.show(getActivity().getSupportFragmentManager(), FileChooser.TAG);
					}
				}
			}
		});

		mDrawerList.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
			@Override
			public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
				// If it's not the header that's been clicked
				if (position != 0) {
					mDrawerList.setChoiceMode(ListView.CHOICE_MODE_MULTIPLE_MODAL);
					mDrawerList.setItemChecked(position, true);
					return true;
				} else {
					return false;
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


	private void selectItem(int position) {
		mCurrentSelectedPosition = position;
		if (mDrawerList != null) {
			mDrawerList.setItemChecked(position, true);
		}
		if (mDrawerLayout != null) {
			mDrawerLayout.closeDrawer(mFragmentContainerView);
		}
		if (mCallbacks != null) {
			mCallbacks.onNavigationDrawerItemSelected(position);
		}
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
		out.putInt(STATE_SELECTED_POSITION, mCurrentSelectedPosition);
		// Save adapter items
		if (adapter != null) {
			out.putParcelableArrayList(STATE_ADAPTER_ITEMS, (ArrayList<NavItem>) adapter.getItems());
		}
		// Save selected items (CAB)
		if (mNavCAB != null) {
			out.putSerializable(STATE_CAB_SELECTED_ITEMS, (java.io.Serializable) mNavCAB
					.getSelectedItemPositions());
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
			mMenuConcatItem = menu.findItem(R.id.action_concat);
			mMenuConcatItem.setEnabled(isConcatenatable());
			showGlobalContextActionBar();
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
				// Extract sources
				List<String> sources = new ArrayList<>();
				for (NavItem navItem : adapter.getItems()) {
					// Only add videos
					if (navItem.getType() == NavItem.Type.VIDEO) {
						sources.add(navItem.getFile().getPath());
					}
				}

				// ToDo ask user for a destination
				String destination = Environment
						.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES).getPath();
				File output = new File(destination + File.separator + "output" + (++num) + ".mp4");
				if (output.exists()) {
					output.delete();
				}
				try {
					// Calculate total video length
					int duration = -1;
					for (NavItem item : adapter.getItems()) {
						Attributes attrs = item.getAttributes();
						if (attrs != null && item.getState() == NavItem.State.VALID) {
							duration += attrs.getDuration();
						} else {
							Log.w(TAG, "Encountered an invalid item when fetching duration! "+item);
							duration = -1;
							break;
						}
					}
					// Concat videos
					Ffmpeg.concat(output, sources, duration);
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
			case R.id.action_add_video:
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

	private MainActivity getMainActivity() {
		if (getActivity() == null) {
			return null;
		} else {
			return (MainActivity) getActivity();
		}
	}

	@Override
	public void onChosen(File file) {
		// ToDo encode filenames with spaces?
		final NavItem item = new NavItem(adapter, file);
		// Enable concat action when state validates
		item.registerUpdateListener(new NavItem.OnUpdatedListener() {
			@Override
			public void onUpdated(NavItem.ItemAttribute attribute,
			                      Object oldValue, Object newValue) {
				if (attribute == NavItem.ItemAttribute.STATE && newValue == NavItem.State.VALID) {
					Utils.runOnUiThread(new Runnable() {
						@Override
						public void run() {
							mMenuConcatItem.setEnabled(isConcatenatable());
						}
					});
					// Upon reaching the VALID state, remove this listener
					item.unregisterUpdateListener(this);
				}
			}
		});
		adapter.addItem(item);
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

}
