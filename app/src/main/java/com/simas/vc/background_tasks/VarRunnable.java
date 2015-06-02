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
package com.simas.vc.background_tasks;

/**
 * Runnable that includes a changeable variable, which can later be used in the runnable's run
 * method.
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
