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
import android.database.DataSetObserver;
import android.os.Bundle;
import android.os.Environment;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentStatePagerAdapter;
import android.support.v4.view.ViewPager;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.util.SparseArray;
import android.view.Menu;
import android.view.MenuItem;
import android.support.v4.widget.DrawerLayout;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ListView;

import com.simas.vc.background_tasks.FFmpeg;
import com.simas.vc.editor.player.Player;
import com.simas.vc.editor.player.PlayerFragment;
import com.simas.vc.file_chooser.FileChooser;
import com.simas.vc.nav_drawer.NavItem;
import com.simas.vc.editor.EditorFragment;
import com.simas.vc.nav_drawer.NavDrawerFragment;
import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

// ToDo use dimensions in xml instead of hard-coded values
// ToDo link onPageScrolled with drawer list's onScroll
// ToDo empty view for ViewPager
// ToDo properly link drawer and viewPager adapters. rotation causes a crash.

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
	private ViewPager mViewPager;

	private class PagerPoolAdapter extends FragmentStatePagerAdapter {

		private SparseArray<Fragment> mFragments = new SparseArray<>();

		public PagerPoolAdapter(FragmentManager fm) {
			super(fm);
		}

		@Override
		public Fragment getItem(final int position) {
			final EditorFragment editor = new EditorFragment();
			mFragments.put(position, editor);

			editor.post(new Runnable() {
				@Override
				public void run() {
					editor.setCurrentItem(mNavDrawerFragment.adapter.getItem(position));
				}
			});

			return editor;
		}

		@Override
		public void destroyItem(ViewGroup container, int position, Object object) {
			super.destroyItem(container, position, object);
			mFragments.put(position, null);
		}

		/**
		 * Returns the fragment associated with this position or null if the fragment hasn't been
		 * set yet or was destroyed.
		 */
		public Fragment getCreatedItem(int position) {
			return mFragments.get(position);
		}

		@Override
		public int getCount() {
			if (mNavDrawerFragment != null && mNavDrawerFragment.adapter != null) {
				return mNavDrawerFragment.adapter.getCount();
			} else {
				return 0;
			}
		}
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);

		mToolbar = (Toolbar) findViewById(R.id.toolbar);
		mViewPager = (ViewPager) findViewById(R.id.view_pager);
		final PagerPoolAdapter pagerAdapter = new PagerPoolAdapter(getSupportFragmentManager());
		mViewPager.setAdapter(pagerAdapter);
		mViewPager.addOnPageChangeListener(new ViewPager.OnPageChangeListener() {
			private int mPosition;
			private float mPositionOffset;

			@Override
			public void onPageSelected(int position) {
				try {
					setTitle(mNavDrawerFragment.adapter.getItem(position).getFile().getName());
				} catch (NullPointerException ignored) {
					setTitle(VC.getStr(R.string.app_name));
				}
			}

			@Override
			public void onPageScrolled(int position, float positionOffset, int positionOffsetPxs) {
				final float previousOffset = mPositionOffset;
				mPosition = position;
				mPositionOffset = positionOffset;
				// onPageScrollStateChanged ignores 0 offsets which would mean an invalid drag.
				// However, it might change to a valid offset. Then, a re-invoke is necessary.
				if (previousOffset == 0 && previousOffset != positionOffset) {
					onPageScrollStateChanged(ViewPager.SCROLL_STATE_DRAGGING);
				}
			}

			@Override
			public void onPageScrollStateChanged(int state) {
				Fragment fragment;
				switch (state) {
					case ViewPager.SCROLL_STATE_IDLE:
						// Hide player preview for currently visible page
						fragment = pagerAdapter.getCreatedItem(mPosition);
						if (fragment != null && fragment instanceof EditorFragment) {
							final EditorFragment editor = (EditorFragment) fragment;
							editor.post(new Runnable() {
								@Override
								public void run() {
									final PlayerFragment player = editor.getPlayer();
									if (player != null) {
										player.post(new Runnable() {
											@Override
											public void run() {
												player.resetPreviewVisibility();
											}
										});
									}
								}
							});
						}
						break;
					case ViewPager.SCROLL_STATE_DRAGGING:
						// Ignore 0 offset to avoid invalid drags
						if (mPositionOffset == 0) return;

						// Show player preview for currently visible page
						fragment = pagerAdapter.getCreatedItem(mPosition);
						if (fragment != null && fragment instanceof EditorFragment) {
							final EditorFragment editor = (EditorFragment) fragment;
							editor.post(new Runnable() {
								@Override
								public void run() {
									final PlayerFragment player = editor.getPlayer();
									if (player != null) {
										player.post(new Runnable() {
											@Override
											public void run() {
												if (player.getPlayer().getState() ==
														Player.State.STARTED) {
													player.getPlayer().pause();
												}
												player.setPreviewTemporarilyVisible(true);
											}
										});
									}
								}
							});
						}

						// Show player preview for the next visible page
						int nextPosition = (mPositionOffset > 0) ? mPosition + 1 : mPosition - 1;
						fragment = pagerAdapter.getCreatedItem(nextPosition);
						if (fragment != null && fragment instanceof EditorFragment) {
							final EditorFragment editor = (EditorFragment) fragment;
							editor.post(new Runnable() {
								@Override
								public void run() {
									final PlayerFragment player = editor.getPlayer();
									if (player != null) {
										player.post(new Runnable() {
											@Override
											public void run() {
												if (player.getPlayer().getState() ==
														Player.State.STARTED) {
													player.getPlayer().pause();
												}
												player.setPreviewTemporarilyVisible(true);
											}
										});
									}
								}
							});
						}
						break;
				}
			}
		});

//		addTooltips();
		setSupportActionBar(mToolbar);

		mNavDrawerFragment = (NavDrawerFragment) getSupportFragmentManager()
				.findFragmentById(R.id.navigation_drawer);

		// Set up the drawer.
		mNavDrawerFragment.setUp(R.id.navigation_drawer,
				(DrawerLayout) findViewById(R.id.drawer_layout));

//		// Set up editor
//		mEditorFragment = (EditorFragment) getSupportFragmentManager()
//				.findFragmentById(R.id.editor_fragment);

//		// Hidden by default
//		getSupportFragmentManager().beginTransaction()
//				.hide(mEditorFragment)
//				.commit();

		// Make sure editor item is == to the LV's current selection (e.g. on adapter data deletion)
		mNavDrawerFragment.adapter.registerDataSetObserver(new DataSetObserver() {
			@Override
			public void onChanged() {
				super.onChanged();
				// Connect drawer list and pager adapters
				Log.e(TAG, "Adapter size changed to: " + mNavDrawerFragment.adapter.getCount());
				pagerAdapter.notifyDataSetChanged();
				ListView lv = mNavDrawerFragment.getList();
				// Make sure we're not in CAB mode (multiple selections)
				if (lv.getChoiceMode() == ListView.CHOICE_MODE_SINGLE) {
					// Make sure the editor's item is the same as the currently checked one
					Object checkedItem = lv.getItemAtPosition(lv.getCheckedItemPosition());
					if (mEditorFragment != null &&
							mEditorFragment.getCurrentItem() != checkedItem) {
						mNavDrawerFragment.selectItem(ListView.INVALID_POSITION);
					}
				}
			}
		});

//		// ToDo default item test
//		new Handler().postDelayed(new Runnable() {
//			@Override
//			public void run() {
//				mNavDrawerFragment.onChosen(new File("/sdcard/Movies/1.mp4"));
//				mNavDrawerFragment.onChosen(new File("/sdcard/Movies/1.mp4"));
//				mNavDrawerFragment.onChosen(new File("/sdcard/Movies/1.mp4"));
//				mNavDrawerFragment.onChosen(new File("/sdcard/Movies/1.mp4"));
//			}
//		}, 1000);
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
					concat.setOnClickListener(new View.OnClickListener() {
						@Override
						public void onClick(View v) {
							ObjectAnimator animator = ObjectAnimator.ofFloat(v, "rotation", 180);
							animator.setDuration(2000);
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

	@Override
	public void onNavigationDrawerItemSelected(int position) {
		ListView lv = mNavDrawerFragment.getList();

		// Fetch the NavItem corresponding to the given position. null if the position is invalid
		// or if it belongs to a header/footer
		NavItem item = null;
		//noinspection StatementWithEmptyBody
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

		// If editor's item was updated show/hide the editor and the drawer
		if (mEditorFragment != null && mEditorFragment.setCurrentItem(item)) {
			// Close drawer if a new and non-null item is selected
			if (item != null) mNavDrawerFragment.setDrawerOpen(false);

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
					//noinspection ResultOfMethodCallIgnored
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
