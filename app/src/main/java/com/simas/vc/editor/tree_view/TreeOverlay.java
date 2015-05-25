package com.simas.vc.editor.tree_view;

import android.annotation.TargetApi;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.Build;
import android.support.annotation.NonNull;
import android.util.AttributeSet;
import android.view.View;
import android.widget.RelativeLayout;
import java.util.HashSet;
import java.util.Set;

/**
 * Created by Simas Abramovas on 2015 May 24.
 */

public class TreeOverlay extends RelativeLayout {

	public static final int LINE_COLOR = Color.BLACK;
	private final String TAG = getClass().getName();
	private final Set<Line> mLines = new HashSet<>();
	private Paint mLinePaint;


	protected static class Line {

		TreeParser.Node node;
		int startX, startY, stopX, stopY;

		public Line(@NonNull TreeParser.Node node) {
			this.node = node;
		}

		@Override
		public boolean equals(Object o) {
			return o instanceof Line && node == ((Line) o).node;
		}

		@Override
		public int hashCode() {
			return node.hashCode();
		}

		@Override
		public String toString() {
			return String.format("Start: (%d;%d), Stop: (%d, %d), Node: %s",
					startX, startY, stopX, stopY, node);
		}

	}

	public TreeOverlay(Context context) {
		super(context);
		init();
	}

	public TreeOverlay(Context context, AttributeSet attrs) {
		super(context, attrs);
		init();
	}

	public TreeOverlay(Context context, AttributeSet attrs, int defStyleAttr) {
		super(context, attrs, defStyleAttr);
		init();
	}

	@TargetApi(Build.VERSION_CODES.LOLLIPOP)
	public TreeOverlay(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
		super(context, attrs, defStyleAttr, defStyleRes);
		init();
	}

	private void init() {
		// Enable manual drawing
		setWillNotDraw(false);

		mLinePaint = new Paint();
		mLinePaint.setColor(LINE_COLOR);
		mLinePaint.setStrokeWidth(3);
	}

	/**
	 * Add the line for the given node, recalculates the existing lines and re-draws them.
	 * @param node    node for which the line will be calculated and drawn
	 */
	public void addLineForNode(TreeParser.Node node) {
		// Remove line for this node (if it exists)
		mLines.remove(new Line(node));

		// Recalculate existing lines (for other nodes)
		recalculateLines();

		// Add the (re-)calculated line for this node
		Line calculatedLine = calculateLine(node);
		if (calculatedLine != null) mLines.add(calculatedLine);

		// Re-draw
		invalidate();
	}

	/**
	 * Remove the line for the given node, recalculates all the other ones and finally re-draws.
	 * @param node    lines with this node will be removed
	 */
	public void removeLinesForNode(TreeParser.Node node) {
		mLines.remove(new Line(node));

		// Recalculate existing lines (for other nodes)
		recalculateLines();

		// Re-draw
		invalidate();
	}

	/**
	 * Recalculate existing lines. Does not re-draw them.
	 */
	public void recalculateLines() {
		Set<Line> lines = new HashSet<>(mLines);
		for (Line line : lines) {
			mLines.remove(new Line(line.node));
			Line calculatedLine = calculateLine(line.node);
			if (calculatedLine != null) mLines.add(calculatedLine);
		}
	}

	public Line calculateLine(final TreeParser.Node node) {
		if (!node.expanded || node.children.size() == 0) {
			return null;
		}

		final Line line = new Line(node);

		final TreeParser.Node lastChildNode = node.children.get(node.children.size() - 1);
		final View lastChildView = lastChildNode.view;

		line.startX = lastChildNode.parentLeftPadding;
		line.startY = node.view.getBottom();
		line.stopX  = line.startX;
		line.stopY  = lastChildView.getBottom() - lastChildView.getHeight() / 2;

		if (lastChildView.getBottom() == 0) {
			lastChildView.addOnLayoutChangeListener(new OnLayoutChangeListener() {
				@Override
				public void onLayoutChange(View v, int left, int top, int right, int bottom,
				                           int oldLeft, int oldTop, int oldRight, int oldBottom) {
					removeOnLayoutChangeListener(this);

					// Remove the old line
					mLines.remove(line);

					// Re-calculate and add new line
					mLines.add(calculateLine(node));

					// Re-draw
					TreeOverlay.this.invalidate();
				}
			});
			// Force re-invalidate the child to get the proper measurements
			lastChildView.invalidate();
		}

		return line;
	}

	@Override
	protected void onDraw(Canvas canvas) {
		super.onDraw(canvas);
		if (!isInEditMode()) {
			for (Line line : mLines) {
				canvas.drawLine(line.startX, line.startY, line.stopX, line.stopY, mLinePaint);
			}
		}
	}

}
