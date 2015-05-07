package com.simas.vc;

import android.database.DataSetObserver;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.support.v4.widget.DrawerLayout;
import android.view.View;
import android.widget.ListView;
import com.simas.vc.editor.HelperFragment;
import com.simas.vc.nav_drawer.NavItem;
import com.simas.vc.editor.EditorFragment;
import com.simas.vc.nav_drawer.NavDrawerFragment;

// ToDo FFprobe should queue, otherwise with 2 caalls it fails (probly coz of the same report file?)
// ToDo use dimensions in xml instead of hard-coded values

public class MainActivity extends AppCompatActivity
		implements NavDrawerFragment.NavigationDrawerCallbacks {

	private final String TAG = getClass().getName();
	/**
	 * Fragment managing the behaviors, interactions and presentation of the navigation drawer.
	 */
	private NavDrawerFragment mNavDrawerFragment;
	private EditorFragment mEditorFragment;
	private HelperFragment mHelperFragment;
	private int mSelectedItemPosition;

	/**
	 * Used to store the last screen title. For use in {@link #restoreActionBar()}.
	 */
	private CharSequence mTitle;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);

		mNavDrawerFragment = (NavDrawerFragment) getSupportFragmentManager()
				.findFragmentById(R.id.navigation_drawer);
		mTitle = getTitle();

		// Set up the drawer.
		mNavDrawerFragment.setUp(R.id.navigation_drawer,
				(DrawerLayout) findViewById(R.id.drawer_layout));

		// Helper
		mHelperFragment = (HelperFragment) getSupportFragmentManager()
				.findFragmentById(R.id.helper_fragment);

		// Set up editor
		mEditorFragment = (EditorFragment) getSupportFragmentManager()
				.findFragmentById(R.id.editor_fragment);

		// Editor hidden by default
		getSupportFragmentManager().beginTransaction()
				.hide(mEditorFragment)
				.commit();

		// Make sure editor item is == to the LV's current selection (e.g. on adapter data deletion)
		mNavDrawerFragment.adapter.registerDataSetObserver(new DataSetObserver() {
			@Override
			public void onChanged() {
				super.onChanged();
				ListView lv = mNavDrawerFragment.getList();
				// Make sure we're not in CAB mode (multiple selections)
				if (lv.getChoiceMode() == ListView.CHOICE_MODE_SINGLE) {
					// Make sure the editor's item is the same as the currently checked one
					Object checkedItem = lv.getItemAtPosition(lv.getCheckedItemPosition());
					if (mEditorFragment.currentItem != checkedItem) {
						mNavDrawerFragment.selectItem(ListView.INVALID_POSITION);
					}
				}
			}
		});
	}

	@Override
	public void onNavigationDrawerItemSelected(int position) {
		ListView lv = mNavDrawerFragment.getList();
		NavItem item = null;
		if (position == ListView.INVALID_POSITION) {
			// Invalidate editor fragment on an invalid position
		} else if (position < lv.getHeaderViewsCount() ||
				position >= lv.getCount() - lv.getFooterViewsCount()) {
			// Skip headers and footers
			return;
		} else {
			// Position is fine, fetch the item
			item = (NavItem) lv.getItemAtPosition(position);
			if (item != null) setTitle(item.getFile().getName());

			// Check the item in the drawer
			lv.setItemChecked(position, true);

		}
		mSelectedItemPosition = position;

		// Re-open this item in the editor fragment, only if it's new
		if (mEditorFragment.getCurrentItem() != item) {
			mNavDrawerFragment.setDrawerOpen(false);
			mEditorFragment.setCurrentItem(item);

			// Hide/Show the Editor/Helper
			if (item == null) {
				// Hide if visible
				if (mEditorFragment.isVisible()) {
					getSupportFragmentManager().beginTransaction()
							.hide(mEditorFragment)
							.show(mHelperFragment)
							.commit();
				}
			} else {
				// Show if hidden
				if (!mEditorFragment.isVisible()) {
					getSupportFragmentManager().beginTransaction()
							.hide(mHelperFragment)
							.show(mEditorFragment)
							.commit();
				}
			}
		}
	}

	public void restoreActionBar() {
		if (getSupportActionBar() != null) {
			getSupportActionBar().setDisplayShowTitleEnabled(true);
			getSupportActionBar().setTitle(mTitle);
		}
	}

	/**
	 * Add Video Action button's location on the window. 0 if still un-set.
	 */
	int mAddActionXLocation;

	@Override
	public boolean onCreateOptionsMenu(final Menu menu) {
		if (!mNavDrawerFragment.isDrawerOpen()) {
			// Only show items in the action bar relevant to this screen
			// if the drawer is not showing. Otherwise, let the drawer
			// decide what to show in the action bar.
			getMenuInflater().inflate(R.menu.menu_main, menu);
			restoreActionBar();

			// Save the addVideo button's position:
			final MenuItem addVideo = menu.findItem(R.id.action_add_item);
			if (addVideo != null && addVideo.getActionView() != null) {
				addVideo.getActionView().addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
					@Override
					public void onLayoutChange(View v, int left, int top, int right, int bottom,
					                           int oldLeft, int oldTop, int oldRight, int oldBottom) {
						if (addVideo.getActionView() != null) {
							int[] coordinates = new int[2];
							addVideo.getActionView().getLocationInWindow(coordinates);
							setAddActionLocation(coordinates[0]);
							Log.i(TAG, "AddVideo button's X position: " + mAddActionXLocation);
						}
					}
				});
			}
			return true;
		}
		// Drawer items
		return super.onCreateOptionsMenu(menu);
	}

	/**
	 * Setter that also announces the change to {@code mHelperFragment}. Announcing is skipped if
	 * the new value is equal to the old one.
	 * @param x    x axis position of the Add Video action button
	 */
	private void setAddActionLocation(final int x) {
		// Modify only if changed
		if (mAddActionXLocation != x) {
			mAddActionXLocation = x;
			mHelperFragment.post(new Runnable() {
				@Override
				public void run() {
					mHelperFragment.moveAddItemHelper(x);
				}
			});
		}
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		// Handle action bar item clicks here. The action bar will
		// automatically handle clicks on the Home/Up button, so long
		// as you specify a parent activity in AndroidManifest.xml.
		int id = item.getItemId();

		//noinspection SimplifiableIfStatement
		if (id == R.id.action_settings) {
			return true;
		}

		return super.onOptionsItemSelected(item);
	}

	@Override
	public void onBackPressed() {
		// Close drawer on back click
		if (mNavDrawerFragment.isDrawerOpen()) {
			mNavDrawerFragment.setDrawerOpen(false);
		} else {
			super.onBackPressed();
		}
	}
}
