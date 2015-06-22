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

import android.animation.ObjectAnimator;
import android.app.AlertDialog;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.support.v4.view.ViewPager;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.support.v4.widget.DrawerLayout;
import android.view.View;
import android.widget.ListView;
import com.simas.vc.background_tasks.FFmpeg;
import com.simas.vc.editor.player.Player;
import com.simas.vc.editor.player.PlayerFragment;
import com.simas.vc.file_chooser.FileChooser;
import com.simas.vc.helpers.ObservableList;
import com.simas.vc.nav_drawer.NavItem;
import com.simas.vc.editor.EditorFragment;
import com.simas.vc.nav_drawer.NavDrawerFragment;
import com.simas.vc.pager.PagerAdapter;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;

// ToDo empty view for ViewPager
// ToDo animate toolbar action item icons, i.e. rotate on click (use AnimationDrawable)
// ToDo use dimensions in xml instead of hard-coded values

/**
 * Activity that contains all the top-level fragments and manages their transitions.
 */
public class MainActivity extends AppCompatActivity
		implements NavDrawerFragment.NavigationDrawerCallbacks {

	private static final String STATE_ITEMS = "items_list";
	private final String TAG = getClass().getName();
	private NavDrawerFragment mNavDrawerFragment;
	private EditorFragment mEditorFragment;
	private Toolbar mToolbar;
	private ViewPager mViewPager;
	private PagerScrollListener mPagerScrollListener;
	private View mProgressBarContainer;
	private PagerAdapter mPagerAdapter;
	/**
	 * A list that contains all the added items, shared throughout the app. It's used by
	 * {@link NavDrawerFragment}, {@link com.simas.vc.nav_drawer.NavAdapter},
	 * {@link PagerAdapter} and individual {@link NavItem}s.
	 */
	public static ObservableList sItems = new ObservableList();

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		// Always reset observers when (re-)creating the activity
		sItems.unregisterAllObservers();
		// Restore items if available, otherwise make sure the list is empty
		if (savedInstanceState != null) {
			ArrayList<NavItem> items = savedInstanceState.getParcelableArrayList(STATE_ITEMS);
			if (items != null) {
				sItems = new ObservableList();
				sItems.addAll(items);
			} else {
				sItems.clear();
			}
		} else {
			sItems.clear();
		}

		// Init a ProgressBar (to be used by PlayerFragments)
		mProgressBarContainer = getLayoutInflater().inflate(R.layout.progress_bar_overlay, null);

		setContentView(R.layout.activity_main);

		/* Toolbar */
		mToolbar = (Toolbar) findViewById(R.id.toolbar);
		setSupportActionBar(mToolbar);
//		addTooltips();

		/* Pager */
		mViewPager = (ViewPager) findViewById(R.id.view_pager);

		mPagerAdapter = new PagerAdapter(getSupportFragmentManager());
		mPagerScrollListener = new PagerScrollListener();
		mViewPager.addOnPageChangeListener(mPagerScrollListener);
		mViewPager.setAdapter(mPagerAdapter);
		// This is to avoid a black screen flash when the SurfaceView is setting up another window
		// https://code.google.com/p/gmaps-api-issues/issues/detail?id=4639#c2
		mViewPager.requestTransparentRegion(mViewPager);

		/* Drawer */
		mNavDrawerFragment = (NavDrawerFragment) getSupportFragmentManager()
				.findFragmentById(R.id.navigation_drawer);
		mNavDrawerFragment.setUp(R.id.navigation_drawer,
				(DrawerLayout) findViewById(R.id.drawer_layout));


		// First item needs explicit selection via the scroll listener
		final String FIRST_ITEM_OBSERVER = "first_item_observer";
		sItems.registerDataSetObserver(new ObservableList.Observer() {
			@Override
			public void onChanged() {
				if (sItems.size() == 1) {
					sItems.unregisterDataSetObserver(FIRST_ITEM_OBSERVER);
					mPagerScrollListener.onPageSelected(0);
					mPagerScrollListener.onPageScrollStateChanged(ViewPager.SCROLL_STATE_IDLE);
				}
			}
		}, FIRST_ITEM_OBSERVER);

		// Make sure editor item is == to the LV's current selection (e.g. on adapter data deletion)
//		mNavDrawerFragment.adapter.registerDataSetObserver(new DataSetObserver() {
//			@Override
//			public void onChanged() {
//				super.onChanged();
//				// Connect drawer list and pager adapters
//				Log.e(TAG, "Adapter size changed to: " + mNavDrawerFragment.adapter.getCount());
//				ListView lv = mNavDrawerFragment.getListView();
//				// Make sure we're not in CAB mode (multiple selections)
//				if (lv.getChoiceMode() == ListView.CHOICE_MODE_SINGLE) {
//					// Make sure the editor's item is the same as the currently checked one
//					Object checkedItem = lv.getItemAtPosition(lv.getCheckedItemPosition());
//					if (mEditorFragment != null &&
//							mEditorFragment.getItem() != checkedItem) {
//						mNavDrawerFragment.selectItem(ListView.INVALID_POSITION);
//					}
//				}
//			}
//		});

//		// ToDo default item test
		if (sItems.size() == 0) {
			new Handler().postDelayed(new Runnable() {
				@Override
				public void run() {
					mNavDrawerFragment.onChosen(new File("/sdcard/Movies/iwatch.mp4"));
//					mNavDrawerFragment.onChosen(new File("/sdcard/Movies/1.mp4"));
//					mNavDrawerFragment.onChosen(new File("/sdcard/Movies/1.mp4"));
//					mNavDrawerFragment.onChosen(new File("/sdcard/Movies/1.mp4"));
				}
			}, 1000);
		}
	}

	private class PagerScrollListener extends ViewPager.SimpleOnPageChangeListener {

		private class IntegerPair {
			Integer current, previous;
		}

		private final String TAG = getClass().getName();
		private static final float MIN_SCROLL_OFFSET = 0.01f;
		private IntegerPair mVisiblePositions = new IntegerPair();
		private float mPositionOffset;

		@Override
		public void onPageSelected(int position) {
			super.onPageSelected(position);
			// Set title
			setTitle(sItems.get(position).getFile().getName());

			// Update the EditorFragment and invalidate the PlayerFragment
			mEditorFragment = (EditorFragment) mPagerAdapter.getCreatedItem(position);
			if (mEditorFragment != null) {
				mEditorFragment.getPlayerFragment().setInitialized(false);
			}
		}

		@Override
		public void onPageScrolled(int position, float positionOffset, int positionOffsetPxs) {
			final float previousOffset = mPositionOffset;
			mPositionOffset = positionOffset;

			// There can only be 2 pages visible at a time
			if (mVisiblePositions.current == null || mVisiblePositions.current != position) {
				mVisiblePositions.previous = mVisiblePositions.current;
				mVisiblePositions.current = position;
			}

			// Re-invoke onPageScrollStateChanged when switched to a valid scroll offset
			if (Math.abs(mPositionOffset) >= MIN_SCROLL_OFFSET &&
					Math.abs(previousOffset) < MIN_SCROLL_OFFSET) {
				onPageScrollStateChanged(ViewPager.SCROLL_STATE_DRAGGING);
			} else if (Math.abs(mPositionOffset) < MIN_SCROLL_OFFSET) {
				// Hide previews for the visible positions
				showPreviewsTemporarily(false);
			}
		}

		@Override
		public void onPageScrollStateChanged(int state) {
			switch (state) {
				case ViewPager.SCROLL_STATE_DRAGGING:
					// Ignore low offsets, to avoid invalid drags
					if (Math.abs(mPositionOffset) < MIN_SCROLL_OFFSET) {
						return;
					}

					// Pause player
					Player player = PlayerFragment.getPlayer();
					if (player != null && player.getState() == Player.State.STARTED) {
						player.pause();
					}

					// Show previews on the 2 visible editor fragments
					showPreviewsTemporarily(true);
					break;
			}
		}

		private void showPreviewsTemporarily(boolean show) {
			if (mVisiblePositions.current != null) {
				EditorFragment editor = (EditorFragment) mPagerAdapter
						.getCreatedItem(mVisiblePositions.current);
				if (editor != null) {
					editor.getPlayerFragment().showPreviewTemporarily(show);
				}
				if (mVisiblePositions.previous != null) {
					editor = (EditorFragment) mPagerAdapter
							.getCreatedItem(mVisiblePositions.previous);
					if (editor != null) {
						editor.getPlayerFragment().showPreviewTemporarily(show);
					}
				}
			}
		}

	}

	public EditorFragment getEditorFragment() {
		return mEditorFragment;
	}

	public View getProgressOverlay() {
		return mProgressBarContainer;
	}

	@Override
	protected void onSaveInstanceState(Bundle outState) {
		super.onSaveInstanceState(outState);
		outState.putParcelableArrayList(STATE_ITEMS, sItems);
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
					add.setOnClickListener(new View.OnClickListener() {
						private boolean mRotated;
						@Override
						public void onClick(View v) {
							ObjectAnimator animator = ObjectAnimator.ofFloat(v, "rotation",
									(mRotated = !mRotated) ? 360 : 0);
							animator.setDuration(300);
							animator.start();
						}
					});
					new Tooltip(MainActivity.this, concat, getString(R.string.help_concatenate));
					new Tooltip(MainActivity.this, add, getString(R.string.help_add_item));
					mToolbar.removeOnLayoutChangeListener(this);
				}
			}
		});
		// Force re-draw
		getToolbar().requestLayout();
	}

	public boolean isConcatenatable() {
		if (sItems == null) {
			// Adapter not available yet
			return false;
		} else if (sItems.size() < 2) {
			// There must be at least 2 videos to concatenate
			return false;
		} else {
			// Loop and look for invalid items
			for (NavItem item : sItems) {
				if (item.getState() != NavItem.State.VALID) {
					return false;
				}
			}
		}
		return true;
	}

	@Override
	public void onNavigationDrawerItemSelected(int position) {
		ListView lv = mNavDrawerFragment.getListView();

		if (position == ListView.INVALID_POSITION ||
				position < lv.getHeaderViewsCount() ||
				position >= lv.getCount() - lv.getFooterViewsCount()) {
			// Skip headers and footers
			//noinspection UnnecessaryReturnStatement
			return;
		} else {
			// Check the item in the drawer
			lv.setItemChecked(position, true);

			mNavDrawerFragment.setDrawerOpen(false);

			// Open selected item in the view pager
			int itemPosition = position - lv.getHeaderViewsCount();
			mViewPager.setCurrentItem(itemPosition);
		}

//		if (mEditorFragment != null && mEditorFragment.setItem(item)) {
			// Close drawer if a new and non-null item is selected

			// Hide/Show the Editor/Helper
//			if (item == null && mEditorFragment.isVisible()) {
//				setTitle(getString(R.string.app_name));
//
//				// Hide if visible
//				getSupportFragmentManager().beginTransaction()
//						.setCustomAnimations(android.R.anim.fade_in, android.R.anim.slide_out_right)
//						.hide(mEditorFragment)
//						.commit();
//
//				View view = findViewById(R.id.no_items_notifier);
//				Animation fadeOut = AnimationUtils.loadAnimation(this, android.R.anim.fade_in);
//				fadeOut.setFillAfter(true);
//				view.startAnimation(fadeOut);
//			} else if ((item != null && !mEditorFragment.isVisible())) {
//				getSupportFragmentManager().beginTransaction()
//						.setCustomAnimations(android.R.anim.slide_in_left, android.R.anim.fade_out)
//						.show(mEditorFragment)
//						.commit();
//
//				View view = findViewById(R.id.no_items_notifier);
//				Animation fadeOut = AnimationUtils.loadAnimation(this, android.R.anim.fade_out);
//				fadeOut.setFillAfter(true);
//				view.startAnimation(fadeOut);
//			}
//		}
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		boolean result = super.onCreateOptionsMenu(menu);
		getMenuInflater().inflate(R.menu.menu_main, menu);


		final String CONCAT_OBSERVER_TAG = "concatenation_observer";
		sItems.registerDataSetObserver(new ObservableList.Observer() {
			@Override
			public void onChanged() {
				getToolbar().getMenu().findItem(R.id.action_concat).setEnabled(isConcatenatable());
			}
		}, CONCAT_OBSERVER_TAG);

		return result;
	}

	@Override
	protected void onDestroy() {
		super.onDestroy();
		sItems.unregisterAllObservers();
		if (isFinishing()) {
			if (PlayerFragment.getPlayer() != null) {
				PlayerFragment.getPlayer().release(); // ToDo need to release earlier
			}
		}
	}

	@Override
	public boolean onPrepareOptionsMenu(Menu menu) {
		if (mNavDrawerFragment != null) {
			menu.findItem(R.id.action_concat).setEnabled(isConcatenatable());
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
					//noinspection ResultOfMethodCallIgnored
					output.delete();
				}
				try {
					// Concat videos
					FFmpeg.concat(output, sItems);
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
				return true;
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
