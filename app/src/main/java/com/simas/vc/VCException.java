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
package com.simas.vc;

/**
 * Exceptions with a message that can be displayed to the user, i.e. is not an internal problem.
 */
public class VCException extends Exception {

	private String mMessage;

	/**
	 * Default VCException
	 * @param message    message that will be shown to the user
	 */
	public VCException(String message) {
		mMessage = message;
	}

	@Override
	public String getMessage() {
		return mMessage;
	}

}
