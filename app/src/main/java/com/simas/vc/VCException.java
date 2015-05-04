package com.simas.vc;

/**
 * Created by Simas Abramovas on 2015 Mar 02.
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
