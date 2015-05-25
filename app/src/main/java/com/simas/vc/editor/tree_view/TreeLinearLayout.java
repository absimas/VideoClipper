package com.simas.vc.editor.tree_view;

import android.annotation.TargetApi;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.os.Build;
import android.support.annotation.NonNull;
import android.util.AttributeSet;
import android.widget.LinearLayout;

import com.simas.vc.Utils;

/**
 * Created by Simas Abramovas on 2015 May 24.
 */

// ToDo abstractize with an adapter?
// ToDo abstract click listener in TreeParser
// ToDo save expanded nodes views in onSaveInstanceStance

/**
 * Notes:
 * - This layout has default padding. If anything else is specified, it will be ignored.
 */
public class TreeLinearLayout extends LinearLayout {

	private static final int DEFAULT_PADDING = (int) Utils.dpToPx(4);
	private static final int LEFT_PADDING_PER_LEVEL = (int) Utils.dpToPx(30);
	private static final int LEFT_SPACE = 7;
	private static final int LINE_COLOR = TreeOverlay.LINE_COLOR;
	private final Paint mLinePaint = new Paint();
	private TreeParser.Node mNode;

	public TreeLinearLayout(Context context) {
		super(context);
		init();
	}

	public TreeLinearLayout(Context context, AttributeSet attrs) {
		super(context, attrs);
		init();
	}

	public TreeLinearLayout(Context context, AttributeSet attrs, int defStyleAttr) {
		super(context, attrs, defStyleAttr);
		init();
	}

	@TargetApi(Build.VERSION_CODES.LOLLIPOP)
	public TreeLinearLayout(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
		super(context, attrs, defStyleAttr, defStyleRes);
		init();
	}

	private void init() {
		if (!isInEditMode()) {
			// Enable manual drawing
			setWillNotDraw(false);

			mLinePaint.setFlags(Paint.ANTI_ALIAS_FLAG);
			mLinePaint.setColor(LINE_COLOR);
			mLinePaint.setStrokeWidth(3);

			// Invoke overridden setPadding
			setPadding(0,0,0,0);
		}
	}

	public void setNode(TreeParser.Node node) {
		mNode = node;
		// Invoke overridden setPadding
		setPadding(0,0,0,0);
		// Re-draw
		invalidate();
	}

	@Override
	public void setPadding(int left, int top, int right, int bottom) {
		super.setPadding((mNode != null) ? mNode.level * LEFT_PADDING_PER_LEVEL : DEFAULT_PADDING,
				DEFAULT_PADDING, DEFAULT_PADDING, DEFAULT_PADDING);
	}

	@Override
	protected void onDraw(@NonNull Canvas canvas) {
		super.onDraw(canvas);

		int startX = mNode.parentLeftPadding;
		int stopX  = mNode.level * LEFT_PADDING_PER_LEVEL - LEFT_SPACE;
		int y = getHeight() / 2;

		canvas.drawLine(startX, y, stopX, y, mLinePaint);
	}

}
