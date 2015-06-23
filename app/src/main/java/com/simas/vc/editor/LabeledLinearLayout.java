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
package com.simas.vc.editor;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.os.Build;
import android.support.annotation.Nullable;
import android.util.AttributeSet;
import android.widget.LinearLayout;
import com.simas.vc.R;
import com.simas.vc.helpers.Utils;

/**
 * Notes:
 * - Default orientation: VERTICAL.
 * - This layout doesn't have any padding. If specified, it will be ignored.
 * - Container is drawn with a hairline paint, i.e. 1px stroke.
 */
public class LabeledLinearLayout extends LinearLayout {

	private final String TAG = getClass().getName();
	private static final float DEFAULT_LABEL_SIZE = Utils.spToPx(16);
	private static final float DEFAULT_LABEL_HORIZONTAL_PADDING = Utils.dpToPx(5);
	private static final float DEFAULT_CONTAINER_PADDING = Utils.dpToPx(10);
	private static final int DEFAULT_LABEL_COLOR = Color.RED;
	private static final int DEFAULT_CONTAINER_STROKE_COLOR = Color.RED;
	private final Paint mContainerPaint = new Paint();
	private final Paint mLabelPaint = new Paint();
	private String mLabel;
	private float mLabelHeight;
	private float mLabelWidth;
	private float mContainerPadding = DEFAULT_CONTAINER_PADDING;
	private int mLabelHorizontalPadding = (int) DEFAULT_LABEL_HORIZONTAL_PADDING;

	public LabeledLinearLayout(Context context) {
		super(context);
		init(null);
	}

	public LabeledLinearLayout(Context context, AttributeSet attrs) {
		super(context, attrs);
		init(attrs);
	}

	public LabeledLinearLayout(Context context, AttributeSet attrs, int defStyleAttr) {
		super(context, attrs, defStyleAttr);
		init(attrs);
	}

	@TargetApi(Build.VERSION_CODES.LOLLIPOP)
	public LabeledLinearLayout(Context context, AttributeSet attrs, int styleAttr, int styleRes) {
		super(context, attrs, styleAttr, styleRes);
		init(attrs);
	}

	/**
	 * Initializes the layout. Enables the use of attributes, paint and drawing.
	 * @param attrs    attributes that will be parsed.
	 */
	private void init(@Nullable AttributeSet attrs) {
		if (!isInEditMode()) {
			// Initialize container paint
			mContainerPaint.setStrokeWidth(0); // Hairline mode
			mContainerPaint.setStyle(Paint.Style.STROKE);

			// Initialize label paint
			mLabelPaint.setFlags(Paint.ANTI_ALIAS_FLAG | Paint.LINEAR_TEXT_FLAG);

			// Defaults
			setLabelSize(DEFAULT_LABEL_SIZE);
			setLabelColor(DEFAULT_LABEL_COLOR);
			setContainerStrokeColor(DEFAULT_CONTAINER_STROKE_COLOR);
			setOrientation(VERTICAL);

			// Parse attributes
			if (attrs != null) {
				TypedArray ta = getContext().obtainStyledAttributes(attrs,
						R.styleable.LabeledLinearLayout);
				final int attrCount = ta.getIndexCount();
				for (int i = 0; i < attrCount; ++i) {
					int attr = ta.getIndex(i);
					switch (attr) {
						case R.styleable.LabeledLinearLayout_label:
							setLabel(ta.getString(attr));
							break;
						case R.styleable.LabeledLinearLayout_containerPadding:
							setContainerPadding(ta.getDimensionPixelSize(i,
									(int) DEFAULT_CONTAINER_PADDING));
							break;
						case R.styleable.LabeledLinearLayout_containerStrokeColor:
							setContainerStrokeColor(ta.getColor(i, DEFAULT_CONTAINER_STROKE_COLOR));
							break;
						case R.styleable.LabeledLinearLayout_labelColor:
							setLabelColor(ta.getColor(i, DEFAULT_LABEL_COLOR));
							break;
						case R.styleable.LabeledLinearLayout_labelHorizontalPadding:
							setLabelHorizontalPadding(ta.getDimensionPixelSize(i,
									(int) DEFAULT_LABEL_HORIZONTAL_PADDING));
							break;
						case R.styleable.LabeledLinearLayout_labelSize:
							setLabelSize(ta.getDimensionPixelSize(i, (int) DEFAULT_LABEL_SIZE));
							break;
					}
				}
				ta.recycle();
			}

			// Enable manual drawing
			setWillNotDraw(false);

			// Invoke the overriden setPadding method
			setPadding(0, 0, 0, 0);
		}
	}

	public void setLabelColor(int color) {
		mLabelPaint.setColor(color);
		invalidate();
	}

	public void setLabelHorizontalPadding(int padding) {
		mLabelHorizontalPadding = padding;
		invalidate();
	}

	public void setContainerPadding(int padding) {
		mContainerPadding = padding;
		invalidate();
	}

	public void setContainerStrokeColor(int color) {
		mContainerPaint.setColor(color);
		invalidate();
	}

	public void setLabel(String label) {
		mLabel = label;

		// Re-calculate the width for this new label
		calculateLabelWidth();

		invalidate();
	}

	public void setLabelSize(float size) {
		mLabelPaint.setTextSize(size);

		// Re-calculate the width with the new paint
		calculateLabelWidth();

		// Calculate label height
		calculateLabelHeight();

		invalidate();
	}

	/**
	 * (Re)-calculates and sets the current label's width. Does not re-draw the layout.
	 */
	protected void calculateLabelWidth() {
		// If label is set, measure its width
		if (mLabel != null) {
			mLabelWidth = mLabelPaint.measureText(mLabel);
		}
	}

	/**
	 * (Re)-calculates and sets the label's height. The calculating is done for the tallest
	 * letter that can be used for the label. Currently "I". Does not re-draw the layout.
	 */
	protected void calculateLabelHeight() {
		Rect rect = new Rect();
		mLabelPaint.getTextBounds("I", 0, 1, rect);
		mLabelHeight = rect.bottom + rect.height();
	}

	@Override
	public void setPadding(int left, int top, int right, int bottom) {
		if (!isInEditMode()) {
			// Padding is always increased to fit the text
			super.setPadding((int) mLabelHeight, (int) mLabelHeight,
					(int) mLabelHeight, (int) mLabelHeight);
		} else {
			super.setPadding(left, top, right, bottom);
		}
	}

	@Override
	protected void onDraw(Canvas canvas) {
		super.onDraw(canvas);
		if (!isInEditMode()) {
			/* Draw container */
			float left = mContainerPadding,
					right = canvas.getWidth() - mContainerPadding,
					top = mLabelHeight / 2,
					bottom = canvas.getHeight() - mContainerPadding;
			// Draw top-left line
			canvas.drawLine(left, top, mContainerPadding * 3 - mLabelHorizontalPadding, top,
					mContainerPaint);
			// Draw top-right line
			canvas.drawLine(mLabelWidth + mContainerPadding * 3 + mLabelHorizontalPadding,
					top, right, top, mContainerPaint);
			// Draw right line
			canvas.drawLine(right, top, right, bottom, mContainerPaint);
			// Draw left line
			canvas.drawLine(left, top, left, bottom, mContainerPaint);
			// Draw bottom line
			canvas.drawLine(left, bottom, right, bottom, mContainerPaint);

			/* Draw label */
			canvas.drawText(mLabel, mContainerPadding * 3, mLabelHeight, mLabelPaint);
		}
	}

}