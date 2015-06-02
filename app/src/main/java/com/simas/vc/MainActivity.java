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
package com.simas.vc;

import android.database.DataSetObserver;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.support.v4.widget.DrawerLayout;
import android.view.View;
import android.widget.ListView;
import com.simas.vc.file_chooser.FileChooser;
import com.simas.vc.nav_drawer.NavItem;
import com.simas.vc.editor.EditorFragment;
import com.simas.vc.nav_drawer.NavDrawerFragment;
import java.io.File;

// ToDo use dimensions in xml instead of hard-coded values
// ToDo after rotate helper text gets re-set. E.g. with added items you see slide to open list
// ToDo sometimes helper doesn't get initialized. Then ActionBar acts weirdly but also more
	// correctly: you can add items and when you select one of them, you get the proper title set
		// unlike it usually does

/**
 * Activity that contains all the top-level fragments and manages their transitions.
 */
public class MainActivity extends AppCompatActivity
		implements NavDrawerFragment.NavigationDrawerCallbacks {

	private final String TAG = getClass().getName();
	/**
	 * Fragment managing the behaviors, interactions and presentation of the navigation drawer.
	 */
	private NavDrawerFragment mNavDrawerFragment;
	private EditorFragment mEditorFragment;
	private HelperFragment mHelperFragment;
	/**
	 * true when {@code modifyHelperForActivity} has been successfully completed
	 * false when {@code modifyHelperForDrawer} has been successfully completed
	 * null if either was cancelled or neither yet run
	 */
	public Boolean modifiedMenuForActivity;

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
		mNavDrawerFragment.setOptionsMenuCreationListener(new OptionMenuCreationListener() {
			@Override
			public void onOptionsMenuCreated(Menu menu) {
				modifyHelperForDrawer(menu);
			}
		});
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

		// Change drawer helper text when dataset changes
		mNavDrawerFragment.adapter.registerDataSetObserver(new DataSetObserver() {
			@Override
			public void onChanged() {
				super.onChanged();
				if (mHelperFragment.isVisible() && mHelperFragment.isDrawerHelperVisible()) {
					// If fragment and the helper is visible, animate the changes
					mHelperFragment.setDrawerHelperVisibility(false, new Runnable() {
						@Override
						public void run() {
							if (mNavDrawerFragment.adapter.getCount() < 1) {
								mHelperFragment.setDrawerHelperText(
										getText(R.string.help_drawer_no_videos).toString());
							} else {
								mHelperFragment.setDrawerHelperText(
										getText(R.string.help_drawer).toString());
							}
							mHelperFragment.setDrawerHelperVisibility(true, null);
						}
					});
				} else {
					// Change the text immediately // Invoke when helper fragment is ready
					mHelperFragment.post(new Runnable() {
						@Override
						public void run() {
							if (mNavDrawerFragment.adapter.getCount() < 1) {
								mHelperFragment.setDrawerHelperText(
										getText(R.string.help_drawer_no_videos).toString());
							} else {
								mHelperFragment.setDrawerHelperText(
										getText(R.string.help_drawer).toString());
							}
						}
					});
				}
			}
		});

		// ToDo default item test
//		mNavDrawerFragment.onChosen(new File("/sdcard/Movies/1.mp4"));
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

		// Re-open this item in the editor fragment, only if it's new
		if (mEditorFragment.getCurrentItem() != item) {
			// Removing currently selected item, doesn't closet
			if (item != null) {
				mNavDrawerFragment.setDrawerOpen(false);
			}

			mEditorFragment.setCurrentItem(item);

			// Hide/Show the Editor/Helper
			if (item == null) {
				// Hide if visible
				if (mEditorFragment.isVisible()) {
					getSupportFragmentManager().beginTransaction()
							.setCustomAnimations(android.R.anim.fade_in, android.R.anim.slide_out_right)
							.hide(mEditorFragment)
							.show(mHelperFragment)
							.commit();
				}
			} else {
				// Show if hidden
				if (!mEditorFragment.isVisible()) {
					getSupportFragmentManager().beginTransaction()
							.setCustomAnimations(android.R.anim.slide_in_left, android.R.anim.fade_out)
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

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		if (!mNavDrawerFragment.isDrawerOpen()) {
			// Only show items in the action bar relevant to this screen
			// if the drawer is not showing. Otherwise, let the drawer
			// decide what to show in the action bar.
			getMenuInflater().inflate(R.menu.menu_main, menu);
			final MenuItem addItem = menu.findItem(R.id.action_add_item);
			addItem.getActionView().setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					onOptionsItemSelected(addItem);
				}
			});
			restoreActionBar();

			modifyHelperForActivity(menu);
			return true;
		}

		// Let super create the drawer menu
		return super.onCreateOptionsMenu(menu);
	}


	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
			case R.id.action_add_item:
				showFileChooser();
				return true;
			case R.id.action_settings:
				return true;
		}

		return super.onOptionsItemSelected(item);
	}

	private void modifyHelperForActivity(@NonNull Menu menu) {
		if (modifiedMenuForActivity != null && modifiedMenuForActivity) return;

		MenuItem item = menu.findItem(R.id.action_add_item);
		final View actionView = item.getActionView();
		final Runnable runnable = new Runnable() {
			@Override
			public void run() {
				int[] coordinates = new int[2];
				actionView.getLocationInWindow(coordinates);
				final int x = coordinates[0];
				Log.v(TAG, "Action button's X position: " + x);

				// Invoke when helper fragment is ready
				mHelperFragment.post(new Runnable() {
					@Override
					public void run() {
						// Fade out
						mHelperFragment.setActionHelperVisibility(false, new Runnable() {
							@Override
							public void run() {
								// Modify
								mHelperFragment.setActionHelperText(
										getText(R.string.help_add_item).toString());
								mHelperFragment.moveActionHelper(x);
								// Fade in
								mHelperFragment.setActionHelperVisibility(true, new Runnable() {
									@Override
									public void run() {
										modifiedMenuForActivity = true;
									}
								});
							}
						});

						// Make sure the drawer helper is shown too
						mHelperFragment.moveDrawerHelper(0);
						mHelperFragment.setDrawerHelperVisibility(true, null);
					}
				});
			}
		};
		// Queue if addItemView is not yet measured
		if (actionView.getWidth() == 0) {
			actionView.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
				@Override
				public void onLayoutChange(View v, int left, int top, int right, int bottom,
				                           int oldLeft, int oldTop, int oldRight, int oldBottom) {
					runnable.run();
				}
			});
		} else {
			runnable.run();
		}
	}

	void modifyHelperForDrawer(@NonNull Menu menu) {
		if (modifiedMenuForActivity != null && !modifiedMenuForActivity) return;

		MenuItem item = menu.findItem(R.id.action_concat);
		if (item == null) return;
		final View actionView = item.getActionView();
		final Runnable runnable = new Runnable() {
			@Override
			public void run() {
				int[] coordinates = new int[2];
				actionView.getLocationInWindow(coordinates);
				final int x = coordinates[0];
				Log.v(TAG, "Action button's X position: " + x);

				// Invoke when helper fragment is ready
				mHelperFragment.post(new Runnable() {
					@Override
					public void run() {
						// Fade out
						mHelperFragment.setActionHelperVisibility(false, new Runnable() {
							@Override
							public void run() {
								// Modify
								mHelperFragment.setActionHelperText(
										getText(R.string.help_concatenate).toString());
								mHelperFragment.moveActionHelper(x);
								// Fade in
								mHelperFragment.setActionHelperVisibility(true, new Runnable() {
									@Override
									public void run() {
										modifiedMenuForActivity = false;
									}
								});
							}
						});
						mHelperFragment.setDrawerHelperVisibility(false, null);
						// ToDo move drawer helper and show item specific helper data
						// ToDo different drawer messages
							// Based on item count
							// Based on item positions too ?
						}
					});
			}
		};
		// Queue if addItemView is not yet measured
		if (actionView.getWidth() == 0) {
			actionView.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
				@Override
				public void onLayoutChange(View v, int left, int top, int right, int bottom,
				                           int oldLeft, int oldTop, int oldRight, int oldBottom) {
					runnable.run();
				}
			});
		} else {
			runnable.run();
		}
	}

	public void showFileChooser() {
		// Make sure it's a new instance
		FileChooser fileChooser = (FileChooser) getSupportFragmentManager()
				.findFragmentByTag(FileChooser.TAG);

		if (fileChooser == null) {
			fileChooser = FileChooser.getInstance();
			fileChooser.setOnFileChosenListener(mNavDrawerFragment);
			fileChooser.show(getSupportFragmentManager(), FileChooser.TAG);
		}
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

	public interface OptionMenuCreationListener {
		void onOptionsMenuCreated(Menu menu);
	}

}
