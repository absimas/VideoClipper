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

	private String mMessage, mExtra;

	/**
	 * Default VCException
	 * @param message    message that will be shown to the user
	 */
	public VCException(String message) {
		mMessage = message;
	}

	/**
	 * VCException with a extra information that will be written to the log
	 * @param message    message that will be shown to the user
	 * @param extraInfo    extra information that won't be shown to the user,
	 *                        only written in the log
	 */
	public VCException(String message, String extraInfo) {
		mMessage = message;
		mExtra = extraInfo;
	}

	@Override
	public String getMessage() {
		return mMessage;
	}

	/**
	 * Returns the extra information (if set)
	 * @return returns the extra information. If it's not set, returns "unspecified".
	 */
	public String getExtra() {
		return (mExtra == null) ? "unspecified" : mExtra;
	}

}
