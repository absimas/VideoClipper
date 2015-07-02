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
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.support.annotation.NonNull;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;

import com.simas.vc.helpers.Utils;

import java.util.Set;

// ToDo abstractize with an adapter?
// ToDo abstract click listener in TreeParser
// ToDo save expanded nodes views in onSaveInstanceStance
// ToDo animate node expansion

/**
 * When added to a {@link TreeView}, this layout's leftMargin shouldn't be modified. Other
 * attributes, can with the condition that the {@link LayoutParams} are preserved.
 */
@SuppressLint("ViewConstructor")
public final class TreeLinearLayout2 extends LinearLayout {

	private final String TAG = getClass().getName();
	private static final int LEFT_MARGIN_PER_LEVEL = (int) Utils.dpToPx(15);
	private static final int LEFT_SPACE = 7;
	private static final int LINE_COLOR = TreeOverlay.LINE_COLOR;
	private static final Paint LINE_PAINT;
	static {
		LINE_PAINT = new Paint();
		LINE_PAINT.setFlags(Paint.ANTI_ALIAS_FLAG);
		LINE_PAINT.setColor(LINE_COLOR);
		LINE_PAINT.setStrokeWidth(3);
	}

	private final int mLevel;
	private final boolean mLastSibling;
	private final Integer[] mDrawnLevels;

	/**
	 * ToDo
	 * @param context
	 * @param level
	 * @param lastSibling
	 * @param drawnLevels
	 */
	public TreeLinearLayout2(Context context, int level, boolean lastSibling,
	                         @NonNull Set<Integer> drawnLevels) {
		super(context);
		mLevel = level;
		mLastSibling = lastSibling;
		mDrawnLevels = drawnLevels.toArray(new Integer[drawnLevels.size()]);

		// Disable automatic drawing
		setWillNotDraw(false);
	}

	@Override
	protected void onDraw(@NonNull Canvas canvas) {
		super.onDraw(canvas);

		int startX = (mLevel) * LEFT_MARGIN_PER_LEVEL + (int) LINE_PAINT.getStrokeWidth();
		int stopX  = getPaddingLeft() - LEFT_SPACE;
		startX += 24 * (mLevel + 1);
		Log.e(TAG, "startX: " + startX + " stopX: " + stopX +
				" leftPad: " + getPaddingLeft() + " real: " +
				super.getPaddingLeft());

		int mid = getHeight() / 2;

		canvas.drawLine(startX, mid, stopX, mid, LINE_PAINT);

		// If this is the last of the siblings, don't continue the sibling line
		int stopY = (mLastSibling) ? mid : getHeight();
		canvas.drawLine(startX, 0, startX, stopY, LINE_PAINT);

		for (int level : mDrawnLevels) {
			Log.e(TAG, "mLevel: " + mLevel + " draws for: " + level);
			startX = (level) * LEFT_MARGIN_PER_LEVEL + (int) LINE_PAINT.getStrokeWidth();
			startX += 24 * (level + 1);
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

	@Override
	public int getPaddingLeft() {
		int paddingLeft = super.getPaddingLeft();
		if (getChildCount() > 0) {
			paddingLeft += getChildAt(getChildCount() - 1).getPaddingLeft();
		}
		return paddingLeft;
	}
}
