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

import com.simas.wvc.R;

/**
 * Created by Simas Abramovas on 2015 Mar 12.
 */

public class LabeledLinearLayout extends LinearLayout {

	private final String TAG = getClass().getName();
	private float mContainerPadding;
	private Paint mContainerPaint;
	private Paint mTextPaint;
	private String mLabel;
	private float mTextHeight;
	private float mTextWidth;
	private int mContainerStrokeColor = Color.RED;
	private int mLabelColor = Color.RED;
	private int mLabelPadding;
	private int mLabelSize;

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
			// Parse attributes
			if (attrs != null) {
				TypedArray ta = getContext().obtainStyledAttributes(attrs,
						R.styleable.LabeledLinearLayout);
				final int attrCount = ta.getIndexCount();
				for (int i = 0; i < attrCount; ++i) {
					int attr = ta.getIndex(i);
					switch (attr) {
						case R.styleable.LabeledLinearLayout_label:
							mLabel = ta.getString(attr);
							break;
						case R.styleable.LabeledLinearLayout_containerPadding:
							mContainerPadding = ta.getDimensionPixelSize(i, 1);
							break;
						case R.styleable.LabeledLinearLayout_containerStrokeColor:
							mContainerStrokeColor = ta.getColor(i, Color.RED);
							break;
						case R.styleable.LabeledLinearLayout_labelColor:
							mLabelColor = ta.getColor(i, Color.RED);
							break;
						case R.styleable.LabeledLinearLayout_labelHorizontalPadding:
							mLabelPadding = ta.getDimensionPixelSize(i, 1);
							break;
						case R.styleable.LabeledLinearLayout_labelSize:
							mLabelSize = ta.getDimensionPixelSize(i, 1);
							break;
					}
				}
				ta.recycle();
			}

			// Enable manual drawing
			setWillNotDraw(false);

			// Initialize paint
			mContainerPaint = new Paint();
			mContainerPaint.setColor(mContainerStrokeColor);
			mContainerPaint.setStrokeWidth(0);
			mContainerPaint.setStyle(Paint.Style.STROKE);

			mTextPaint = new Paint();
			mTextPaint.setColor(mLabelColor);
			mTextPaint.setFlags(Paint.ANTI_ALIAS_FLAG | Paint.LINEAR_TEXT_FLAG);
			mTextPaint.setTextSize(mLabelSize);

			// Measure text height
			Rect rect = new Rect();
			mTextPaint.getTextBounds("C", 0, 1, rect);
			mTextHeight = rect.height();
			mTextWidth = mTextPaint.measureText(mLabel);

			// Invoke the overriden setPadding method
			setPadding(0, 0, 0, 0);
		}
	}

	@Override
	public void setPadding(int left, int top, int right, int bottom) {
		// Padding is always increased to fit the text
		super.setPadding((int) (left + mTextHeight), (int) (top + mTextHeight),
				(int) (right + mTextHeight), (int) (bottom + mTextHeight));
	}

	@Override
	protected void onDraw(Canvas canvas) {
		super.onDraw(canvas);
		if (!isInEditMode()) {
			// Draw container
			{
				float left = mContainerPadding,
						right = canvas.getWidth() - mContainerPadding,
						top = mTextHeight / 2,
						bottom = canvas.getHeight() - mContainerPadding;
				// Draw top-left line
				canvas.drawLine(left, top, mContainerPadding * 3 - mLabelPadding, top,
						mContainerPaint);
				// Draw top-right line
				canvas.drawLine(mTextWidth + mContainerPadding * 3 + mLabelPadding, top,
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
				canvas.drawText(mLabel, mContainerPadding * 3, mTextHeight, mTextPaint);
			}
		}
	}

}