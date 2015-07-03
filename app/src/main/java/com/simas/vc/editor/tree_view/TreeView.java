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

package com.simas.vc.editor.tree_view;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.Build;
import android.os.Bundle;
import android.os.Parcelable;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import com.simas.vc.helpers.Utils;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * This layout adds the children views as soon as an adapter is connected. It does not re-use the
 * views because of the possible diversity of the types (and the lines of code :)).<br/><br/>
 * Children which have no other siblings, have their children expand automatically. This is to
 * prevent boring single node expansion.<br/><br/>
 * The visibility (expansion) of the views is saved between configuration changes and later
 * restored. Restoring the items backs into the TreeView, i.e. re-setting the adapter, is left
 * for the user.<br/>
 */
public class TreeView extends LinearLayout {

	private static final String TAG = TreeView.class.getName();
	private TreeAdapter mAdapter;
	private final Set<Integer> mDrawnLevels = new HashSet<>();
	/**
	 * Note that we only loop the direct children in the hierarchy.
	 */
	private final OnClickListener mNodeClickListener = new OnClickListener() {
		@Override
		public void onClick(View v) {
			int nodeIndex = indexOfChild(v);
			// Make sure the node is found inside the TreeView
			if (nodeIndex < 0) {
				Log.e(TAG, String.format("Couldn't find child %s in %s", v.toString(), TAG));
				return;
			}

			TreeLinearLayout container = (TreeLinearLayout) v;

			// Use the opposite visibility of the next view
			View nextView = getChildAt(nodeIndex + 1);
			if (nextView == null) {
				// There are no views ahead
				return;
			}

			// Toggle visibility of the children
			if (nextView.getVisibility() == GONE) {
				// Show only the direct children containers
				for (int i=nodeIndex+1; i<getChildCount(); ++i) {
					View child = getChildAt(i);
					if (child instanceof TreeLinearLayout) {
						TreeLinearLayout subContainer = (TreeLinearLayout) child;
						if (subContainer.getLevel() == container.getLevel() + 1) {
							subContainer.setVisibility(VISIBLE);
							// If this is the only child, call expand it too
							if (subContainer.isSingleSibling()) {
								mNodeClickListener.onClick(subContainer);
							}
						} else if (subContainer.getLevel() <= container.getLevel()) {
							// End the loop if a sibling or a parent is found
							break;
						}
					}
				}
			} else {
				// Hide all lower leveled (deeper) containers until a sibling or parent is found
				for (int i=nodeIndex+1; i<getChildCount(); ++i) {
					View child = getChildAt(i);
					if (child instanceof TreeLinearLayout) {
						TreeLinearLayout subContainer = (TreeLinearLayout) child;
						if (subContainer.getLevel() > container.getLevel()) {
							subContainer.setVisibility(GONE);
						} else if (subContainer.getLevel() <= container.getLevel()) {
							// End the loop if a sibling or a parent is found
							break;
						}
					}
				}
			}
		}
	};

	public TreeView(Context context) {
		super(context);
	}

	public TreeView(Context context, AttributeSet attrs) {
		super(context, attrs);
	}

	public TreeView(Context context, AttributeSet attrs, int defStyleAttr) {
		super(context, attrs, defStyleAttr);
	}

	@TargetApi(Build.VERSION_CODES.LOLLIPOP)
	public TreeView(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
		super(context, attrs, defStyleAttr, defStyleRes);
	}

	private static final String STATE_INSTANCE = "instance_state";
	private static final String STATE_CHILDREN_VISIBILITY = "children_visibility";

	@Override
	protected Parcelable onSaveInstanceState() {
		Bundle state = new Bundle(2);
		// Save default instance state
		state.putParcelable(STATE_INSTANCE, super.onSaveInstanceState());

		// Save visibility of each child
		int[] childrenVisibility = new int[getChildCount()];
		for (int i=0; i<getChildCount(); ++i) {
			childrenVisibility[i] = getChildAt(i).getVisibility();
		}
		state.putIntArray(STATE_CHILDREN_VISIBILITY, childrenVisibility);

		return state;
	}

	@Override
	protected void onRestoreInstanceState(Parcelable state) {
		Parcelable instanceState;
		if (state instanceof Bundle) {
			final Bundle bundle = (Bundle) state;
			state = bundle.getParcelable(STATE_INSTANCE);

			// Restore children visibility
			int[] childrenVisibility = bundle.getIntArray(STATE_CHILDREN_VISIBILITY);
			if (childrenVisibility == null || childrenVisibility.length != getChildCount()) {
				Log.e(TAG, String
						.format("Incorrect visibility array! Got %d states while have %d children",
								(childrenVisibility != null) ? childrenVisibility.length : 0,
								getChildCount()));
			} else {
				for (int i = 0; i < getChildCount(); ++i) {
					//noinspection ResourceType
					getChildAt(i).setVisibility(childrenVisibility[i]);
				}
			}
		}
		super.onRestoreInstanceState(state);
	}

	public void setAdapter(TreeAdapter adapter) {
		// Disconnect from the previous adapter
		if (getAdapter() != null) {
			getAdapter().disconnect();
		}
		mAdapter = adapter;
		adapter.connect(this);
		initTree();
	}

	public TreeAdapter getAdapter() {
		return mAdapter;
	}

	private void initTree() {
		// Loop root nodes
		int rootNodeCount = getAdapter().getChildCount(0, null);
		for (int i=0; i<rootNodeCount; ++i) {
			loopChildNodes(0, i, null, i == rootNodeCount - 1, null);
		}
	}

	/**
	 * Loop children of the node located at the given level and at the position of the parentNode
	 * @param level          level of the node, whose children will be looped
	 * @param pos            position of the node in the parent
	 * @param parentNode     parent node will be used to look up its child (null for root)
	 * @param last           specifies whether this is the last the siblings
	 * @param parent         child's parent view (null for root nodes)
	 */
	private void loopChildNodes(int level, int pos, Object parentNode, boolean last,
	                            ViewGroup parent) {
		// Node we're currently looping
		Object node = getAdapter().getNode(level, pos, parentNode);
		// Current node's children count
		int childrenCount = getAdapter().getChildCount(level + 1, node);
		// If there are no children, don't show the parent either
		if (childrenCount == 0) return;

		if (!last) {
			mDrawnLevels.add(level);
		} else {
			mDrawnLevels.remove(level);
		}

		// Create a container for this node
		final TreeLinearLayout container =
				new TreeLinearLayout(getContext(), level, last, pos == 0 && last, mDrawnLevels);
		// Hide all but root nodes
		if (level > 0) {
			container.setVisibility(VISIBLE);
		}

		// Add this node's view to the container
		View content = getAdapter().getNodeView(level, pos, node, container);
		container.addView(content);

		// Add an expander listener if this node has something to expand
		if (childrenCount > 0) {
			// Intercept touches from content and send them to container
			content.setOnClickListener(new OnClickListener() {
				@Override
				public void onClick(View v) {
					mNodeClickListener.onClick(container);
				}
			});
			content.setOnLongClickListener(new OnLongClickListener() {
				@Override
				public boolean onLongClick(View v) {
					return false;
				}
			});
			// Listen to touches on the container too
//			content.setOnClickListener(mNodeClickListener);
		}

		// Add the container to the TreeView
		addView(container);

		// Node's padding left should be equal to parent's padding left
		switch (level) {
			case 0:
				// Root nodes don't have a padding therefore no padding needs to be added
				break;
			case 1:
				// First level node's need to fetch the parent's padding. However since the
				// parent's (root's) full left padding can't be fetched only via the paddingLeft,
				// we need to check its content too
				int leftSpace = parent.getPaddingLeft() + parent.getChildAt(0).getPaddingLeft();
				container.setPadding(container.getPaddingLeft() + leftSpace, 0, 0, 0);
				break;
			default:
				// Other level children can fetch the parent's left padding normally
				container.setPadding(container.getPaddingLeft() + parent.getPaddingLeft(), 0, 0, 0);
				break;
		}


		// Loop this node's children
		for (int i=0; i<childrenCount; ++i) {
			loopChildNodes(level + 1, i, node, i == childrenCount - 1, container);
		}
	}

	@SuppressLint("ViewConstructor")
	private static final class TreeLinearLayout extends LinearLayout {

		private final String TAG = getClass().getName();
		public static final int LEFT_SPACE_PER_LEVEL = (int) Utils.dpToPx(15);
		private static final int LEFT_SPACE = 7;
		private static final int LINE_COLOR = Color.BLACK;
		private static final Paint LINE_PAINT;
		static {
			LINE_PAINT = new Paint();
			LINE_PAINT.setFlags(Paint.ANTI_ALIAS_FLAG);
			LINE_PAINT.setColor(LINE_COLOR);
			LINE_PAINT.setStrokeWidth(3);
		}

		private final int mLevel, mStartX;
		private final boolean mLastSibling, mSingleSibling;
		private final Integer[] mDrawnLevels;
		private static final Map<Integer, Integer> LEVEL_START_X = new HashMap<>();

		/**
		 * Standard TreeLinearLayout constructor.
		 * @param context        context to supply for the default {@link LinearLayout} constructor
		 * @param level          level in which the node, that this view represents, resides in
		 * @param lastSibling    is this the last node of the parent node
		 * @param singleSibling  specifies whether this node is the only child
		 * @param drawnLevels    levels that need to be drawn
		 */
		public TreeLinearLayout(Context context, int level, boolean lastSibling,
		                        boolean singleSibling, @NonNull Set<Integer> drawnLevels) {
			super(context);
			mLevel = level;
			mLastSibling = lastSibling;
			mSingleSibling = singleSibling;
			mDrawnLevels = drawnLevels.toArray(new Integer[drawnLevels.size()]);

			// Disable automatic drawing
			setWillNotDraw(false);

			// Calculate the padding
			int leftSpace = getPaddingLeft() + (getLevel()) * LEFT_SPACE_PER_LEVEL;

			// Calculate vertical line startX and save it so it can be used by other TreeLinearLayouts
			mStartX = (getLevel() - 1) * LEFT_SPACE_PER_LEVEL + leftSpace;
			LEVEL_START_X.put(getLevel(), mStartX);

			// No additional padding is needed for root nodes
			if (getLevel() != 0) {
				setPadding(leftSpace, 0, 0, 0);
			}
		}

		public int getLevel() {
			return mLevel;
		}

		public boolean isSingleSibling() {
			return mSingleSibling;
		}

		@Override
		protected void onDraw(@NonNull Canvas canvas) {
			super.onDraw(canvas);

			// No additional drawing needs to be done for root nodes
			if (getLevel() == 0) return;

			int stopX  = getPaddingLeft() - LEFT_SPACE;
			int mid = getHeight() / 2;

			canvas.drawLine(mStartX, mid, stopX, mid, LINE_PAINT);

			// If this is the last of the siblings, don't continue the sibling line
			int stopY = (mLastSibling) ? mid : getHeight();
			canvas.drawLine(mStartX, 0, mStartX, stopY, LINE_PAINT);

			for (int level : mDrawnLevels) {
				if (level == 0) continue;
				// Get startX for this level from the static variable
				final int startX = LEVEL_START_X.get(level);
				canvas.drawLine(startX, 0, startX, getHeight(), LINE_PAINT);
			}
		}

		@Override
		public void addView(@NonNull View child, int index, ViewGroup.LayoutParams params) {
			super.addView(child, index, params);
			// Make it's the only child
			if (getChildCount() > 1) {
				throw new IllegalStateException(TAG + " can only contain a single child!");
			}
		}

	}

}
