package com.simas.vc.editor;

import android.os.Bundle;
import android.os.Handler;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import com.simas.vc.R;
import com.simas.vc.ResumableHandler;

/**
 * Created by Simas Abramovas on 2015 May 07.
 */

/**
 * All the posted messages will be run after onCreateView
 */
public class HelperFragment extends Fragment {

	private final String TAG = getClass().getName();
	private View mHelpAddItem, mHelpDrawer;
	private ResumableHandler mResumableHandler = new ResumableHandler(new Handler());

	public HelperFragment() {}

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, final Bundle savedState) {
		// Inflate and fetch the views
		View rootView = inflater.inflate(R.layout.fragment_helper, container, false);
		mHelpAddItem = rootView.findViewById(R.id.help_add_item);
		mHelpDrawer = rootView.findViewById(R.id.help_drawer);

		// Resume the handler since all the views are ready now
		mResumableHandler.resume();

		return rootView;
	}

	public void post(Runnable runnable) {
		mResumableHandler.add(runnable);
	}

	public void moveAddItemHelper(int x) {
		mHelpAddItem.setX(x);
	}

}
