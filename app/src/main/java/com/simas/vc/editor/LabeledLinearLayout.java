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
import android.util.Log;
import android.widget.LinearLayout;
import com.simas.vc.R;
import com.simas.vc.Utils;

/**
 * Created by Simas Abramovas on 2015 Mar 12.
 */

/**
 * Notes:
 * - Default orientation: VERTICAL.
 */
public class LabeledLinearLayout extends LinearLayout {

	private final String TAG = getClass().getName();
	private static final float DEFAULT_LABEL_SIZE = Utils.spToPx(16);
	private static final float DEFAULT_LABEL_HORIZONTAL_PADDING = Utils.dpToPx(5);
	private static final float DEFAULT_CONTAINER_PADDING = Utils.dpToPx(10);
	private static final int DEFAULT_LABEL_COLOR = Color.RED;
	private static final int DEFAULT_CONTAINER_STROKE_COLOR = Color.RED;
	private Paint mContainerPaint;
	private Paint mLabelPaint;
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
			mContainerPaint = new Paint();
			mContainerPaint.setStrokeWidth(0);
			mContainerPaint.setStyle(Paint.Style.STROKE);

			// Initialize label paint
			mLabelPaint = new Paint();
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

	public void setLabelSize(float size) {
		mLabelPaint.setTextSize(size);

		// Measure label height
		Rect rect = new Rect();
		mLabelPaint.getTextBounds("C", 0, 1, rect);
		mLabelHeight = rect.height();

		invalidate();
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

		// Measure label width
		mLabelWidth = mLabelPaint.measureText(mLabel);

		invalidate();
	}

	@Override
	public void setPadding(int left, int top, int right, int bottom) {
		// Padding is always increased to fit the text
		super.setPadding((int) (left + mLabelHeight), (int) (top + mLabelHeight),
				(int) (right + mLabelHeight), (int) (bottom + mLabelHeight));
	}

	@Override
	protected void onDraw(Canvas canvas) {
		super.onDraw(canvas);
		if (!isInEditMode()) {
			// Draw container
			{
				float left = mContainerPadding,
						right = canvas.getWidth() - mContainerPadding,
						top = mLabelHeight / 2,
						bottom = canvas.getHeight() - mContainerPadding;
				// Draw top-left line
				canvas.drawLine(left, top, mContainerPadding * 3 - mLabelHorizontalPadding, top,
						mContainerPaint);
				// Draw top-right line
				canvas.drawLine(mLabelWidth + mContainerPadding * 3 + mLabelHorizontalPadding, top,
						right, top, mContainerPaint);
				// Draw right line
				canvas.drawLine(right, top, right, bottom, mContainerPaint);
				// Draw left line
				canvas.drawLine(left, top, left, bottom, mContainerPaint);
				// Draw bottom line
				canvas.drawLine(left, bottom, right, bottom, mContainerPaint);
			}

			// Draw label
			{
				canvas.drawText(mLabel, mContainerPadding * 3, mLabelHeight, mLabelPaint);
			}
		}
	}

}