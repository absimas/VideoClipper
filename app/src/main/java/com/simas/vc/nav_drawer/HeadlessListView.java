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

/**
 * Classes connected to the navigation drawer, e.g. Navigation Item, Drawer Fragment, etc.
 */
package com.simas.vc.nav_drawer;

import android.annotation.TargetApi;
import android.content.Context;
import android.os.Build;
import android.util.AttributeSet;
import android.view.View;
import android.widget.ListView;

/**
 * Class that invokes the {@code OnItemClickListener} directly if clicked on a header or a footer
 * view. This is to make sure that such views do not mess up the {@code mCheckState} of the
 * {@code AbsListView}. Particularly line 1127 of {@code AbsListView}.
 */
public class HeadlessListView extends ListView {

	OnItemClickListener mItemClickListener;

	public HeadlessListView(Context context) {
		super(context);
	}

	public HeadlessListView(Context context, AttributeSet attrs) {
		super(context, attrs);
	}

	public HeadlessListView(Context context, AttributeSet attrs, int defStyleAttr) {
		super(context, attrs, defStyleAttr);
	}

	@TargetApi(Build.VERSION_CODES.LOLLIPOP)
	public HeadlessListView(Context context, AttributeSet attrs, int defAttr, int defRes) {
		super(context, attrs, defAttr, defRes);
	}

	@Override
	public void setOnItemClickListener(OnItemClickListener listener) {
		super.setOnItemClickListener(listener);
		mItemClickListener = listener;
	}

	@Override
	public boolean performItemClick(View view, int position, long id) {
		if (position < getHeaderViewsCount() || position >= getCount() - getFooterViewsCount()) {
			// If clicked on a header or a footer, invoke the listener directly
			if (mItemClickListener != null) {
				mItemClickListener.onItemClick(this, view, position, id);
				return true;
			} else {
				return false;
			}
		} else {
			// Otherwise let the parent handle it
			return super.performItemClick(view, position, id);
		}
	}

}