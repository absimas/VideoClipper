package com.simas.vc;

import android.animation.Animator;
import android.animation.ValueAnimator;
import android.os.Bundle;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.TextView;

/**
 * Created by Simas Abramovas on 2015 May 07.
 */

/**
 * All the posted messages will be run after onCreateView
 */
public class HelperFragment extends Fragment {

	private static final int DEFAULT_FADE_DURATION = 300;
	private final String TAG = getClass().getName();
	/**
	 * Handler runs all the messages posted to it only when the fragment is ready, i.e. at the end
	 * of {@code onCreateView}. Messages can be added by calling fragment's {@code post} method.
	 */
	private DelayedHandler mDelayedHandler = new DelayedHandler(new Handler());
	private Helper mDrawerHelper, mActionHelper;

	public HelperFragment() {}

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, final Bundle savedState) {
		Animation blink = AnimationUtils.loadAnimation(getActivity(), R.anim.blink);

		View rootView = inflater.inflate(R.layout.fragment_helper, container, false);
		// Action helper
		mActionHelper = new Helper(rootView.findViewById(R.id.help_action));
		mActionHelper.mText = (TextView) mActionHelper.mLayout.findViewById(R.id.help_action_text);
		mActionHelper.mArrow = mActionHelper.mLayout.findViewById(R.id.help_action_arrow);
		mActionHelper.mArrow.setAnimation(blink);

		// Drawer helper
		mDrawerHelper = new Helper(rootView.findViewById(R.id.help_drawer));
		mDrawerHelper.mText = (TextView) mDrawerHelper.mLayout.findViewById(R.id.help_drawer_text);
		mDrawerHelper.mArrow = mDrawerHelper.mLayout.findViewById(R.id.help_drawer_arrow);
		mDrawerHelper.mArrow.setAnimation(blink);

		// Resume the handler since all the views are ready now
		mDelayedHandler.resume();

		// Start blinking both of the arrows
		blink.start();

		return rootView;
	}

	/**
	 * Queues the given runnable to be run after the fragment is ready.
	 * @param runnable    message to be queued
	 */
	public void post(Runnable runnable) {
		mDelayedHandler.add(runnable);
	}

	public void setActionHelperVisibility(boolean visible, @Nullable Runnable onComplete) {
		animateLayoutAlpha(mActionHelper, visible, onComplete);
	}

	public void setDrawerHelperVisibility(boolean visible, @Nullable Runnable onComplete) {
		animateLayoutAlpha(mDrawerHelper, visible, onComplete);
	}

	public void moveActionHelper(int x) {
		mActionHelper.mLayout.setX(x);
	}

	public void moveDrawerHelper(int y) {
		mDrawerHelper.mLayout.setY(y);
	}

	public void setActionHelperText(@NonNull String text) {
		mActionHelper.mText.setText(text);
	}

	public void setDrawerHelperText(@NonNull String text) {
		mDrawerHelper.mText.setText(text);
	}

	/**
	 * Uses a {@code Helper} wrapper to cancel and continue animators for specific helpers.
	 * Previous animation is stopped.
	 * @param helper     helper that will be animated
	 * @param visible    the visibility to which the helper's view will be animated
	 */
	private void animateLayoutAlpha(@NonNull final Helper helper, boolean visible,
	                                @Nullable final Runnable onComplete) {
		if (helper.mAnimator != null) {
			// Reset the state
			((MainActivity)getActivity()).modifiedMenuForActivity = null;
			// First remove the listeners, because cancel will call onAnimationEnd
			helper.mAnimator.removeAllListeners();
			helper.mAnimator.cancel();
		}

		float currentValue = helper.mLayout.getAlpha();
		float finalValue;
		int duration;

		if (visible) {
			duration = (int) (DEFAULT_FADE_DURATION * (1 - currentValue));
			finalValue = 1f;
		} else {
			duration = (int) (DEFAULT_FADE_DURATION * currentValue);
			finalValue = 0f;
		}
		helper.mAnimator = ValueAnimator
				.ofFloat(currentValue, finalValue)
				.setDuration(duration);

		helper.mAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
			@Override
			public void onAnimationUpdate(ValueAnimator animation) {
				helper.mLayout.setAlpha((float) animation.getAnimatedValue());
			}
		});
		if (onComplete != null) {
			helper.mAnimator.addListener(new Animator.AnimatorListener() {
				@Override
				public void onAnimationStart(Animator animation) {}

				@Override
				public void onAnimationEnd(Animator animation) {
					onComplete.run();
				}

				@Override
				public void onAnimationCancel(Animator animation) {}

				@Override
				public void onAnimationRepeat(Animator animation) {}
			});
		}
		helper.mAnimator.start();
	}
	public boolean isDrawerHelperVisible() {
		return mDrawerHelper.mLayout.getAlpha() == 1;
	}

	private class Helper {

		private ValueAnimator mAnimator;
		private View mLayout;
		private TextView mText;
		private View mArrow;

		public Helper(@NonNull View view) {
			mLayout = view;
		}

	}

}
