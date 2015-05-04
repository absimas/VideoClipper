package com.simas.vc.background_tasks;

/**
 * Created by Simas Abramovas on 2015 Apr 16.
 */
public abstract class VarRunnable implements Runnable {

	protected Object mVariable;

	public VarRunnable() {

	}

	public VarRunnable(Object variable) {
		setVariable(variable);
	}

	public void setVariable(Object variable) {
		if (mVariable != null) {
			// Make sure changing to same type
			if (mVariable.getClass() != variable.getClass()) {
				throw new IllegalArgumentException("Must change to the same type object as was " +
						"first declared");
			}
		}
		mVariable = variable;
	}

	public Object getVariable() {
		return mVariable;
	}

	public abstract void run();

}
