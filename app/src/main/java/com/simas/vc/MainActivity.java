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

import android.app.AlertDialog;
import android.content.Intent;
import android.database.DataSetObserver;
import android.os.Bundle;
import android.os.Environment;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.support.v4.widget.DrawerLayout;
import android.view.View;
import android.widget.ListView;

import com.simas.vc.background_tasks.FFmpeg;
import com.simas.vc.background_tasks.ProgressActivity;
import com.simas.vc.file_chooser.FileChooser;
import com.simas.vc.nav_drawer.NavItem;
import com.simas.vc.editor.EditorFragment;
import com.simas.vc.nav_drawer.NavDrawerFragment;

import java.io.File;
import java.io.IOException;

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
	private Toolbar mToolbar;
	/**
	 * true when {@code modifyHelperForActivity} has been successfully completed
	 * false when {@code modifyHelperForDrawer} has been successfully completed
	 * null if either was cancelled or neither yet run
	 */
	public Boolean modifiedMenuForActivity;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);

		mToolbar = (Toolbar) findViewById(R.id.toolbar);
		addTooltips();
		setSupportActionBar(mToolbar);

		mNavDrawerFragment = (NavDrawerFragment) getSupportFragmentManager()
				.findFragmentById(R.id.navigation_drawer);

		// Set up the drawer.
		mNavDrawerFragment.setUp(R.id.navigation_drawer,
				(DrawerLayout) findViewById(R.id.drawer_layout));

		// Set up editor
		mEditorFragment = (EditorFragment) getSupportFragmentManager()
				.findFragmentById(R.id.editor_fragment);

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

		// ToDo default item test
//		mNavDrawerFragment.onChosen(new File("/sdcard/Movies/1.mp4"));
	}

	/**
	 * Adds helper tooltips if they haven't yet been closed. Must be called after the toolbar is
	 * set.
	 */
	private void addTooltips() {
		getToolbar().addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
			@Override
			public void onLayoutChange(View v, int left, int top, int right, int bottom,
			                           int oldLeft, int oldTop, int oldRight, int oldBottom) {
				View concat = mToolbar.findViewById(R.id.action_concat);
				View add = mToolbar.findViewById(R.id.action_add_item);
				if (concat != null && add != null) {
					new Tooltip(MainActivity.this, concat, getString(R.string.help_concatenate));
					new Tooltip(MainActivity.this, add, getString(R.string.help_add_item));
					mToolbar.removeOnLayoutChangeListener(this);
				}
			}
		});
		// Force re-draw
		getToolbar().requestLayout();
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
//
//			// Hide/Show the Editor/Helper
//			if (item == null) {
//				// Hide if visible
//				if (mEditorFragment.isVisible()) {
//					getSupportFragmentManager().beginTransaction()
//							.setCustomAnimations(android.R.anim.fade_in, android.R.anim.slide_out_right)
//							.hide(mEditorFragment)
//							.show(mHelperFragment)
//							.commit();
//				}
//			} else {
//				// Show if hidden
//				if (!mEditorFragment.isVisible()) {
//					getSupportFragmentManager().beginTransaction()
//							.setCustomAnimations(android.R.anim.slide_in_left, android.R.anim.fade_out)
//							.hide(mHelperFragment)
//							.show(mEditorFragment)
//							.commit();
//				}
//			}
		}
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		boolean result = super.onCreateOptionsMenu(menu);
		getMenuInflater().inflate(R.menu.menu_main, menu);
		return result;
	}

	@Override
	public boolean onPrepareOptionsMenu(Menu menu) {
		if (mNavDrawerFragment != null) {
			menu.findItem(R.id.action_concat).setEnabled(mNavDrawerFragment.isConcatenatable());
		}
		return super.onPrepareOptionsMenu(menu);
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
			case R.id.action_concat:
				// ToDo ask user for a destination
				String destination = Environment
						.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES).getPath();
				File output = new File(destination + File.separator +
						"output" + (++NavDrawerFragment.num) + ".mp4");
				if (output.exists()) {
					output.delete();
				}
				try {
					// Concat videos
					FFmpeg.concat(output, mNavDrawerFragment.adapter.getItems());
				} catch (IOException e) {
					Log.e(TAG, "Error!", e);
					new AlertDialog.Builder(this)
							.setTitle(getString(R.string.error))
							.setMessage("Unrecoverable error! Please try again.")
							.setPositiveButton("OK...", null)
							.show();
				} catch (VCException e) {
					Log.e(TAG, "Error with " + e.getExtra(), e);
					new AlertDialog.Builder(this)
							.setTitle(getString(R.string.error))
							.setMessage(e.getMessage())
							.setPositiveButton("OK", null)
							.show();
				}
				break;
			case R.id.action_add_item:
				showFileChooser();
				return true;
			case R.id.action_settings:
				return true;
		}

		return super.onOptionsItemSelected(item);
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

	public Toolbar getToolbar() {
		return mToolbar;
	}

}
