package com.simas.vc.attributes;

import com.simas.vc.VCException;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by Simas Abramovas on 2015 Mar 11.
 */

/**
 * Required fields are specified as the constructor parameters. The setters used in the
 * constructor should throw exceptions if an invalid value is given.
 */
public class VideoStream extends Stream {

	public static final String FIELD_WIDTH = "Width";
	public static final String FIELD_HEIGHT = "Height";
	public static final String FIELD_ASPECT_RATIO = "Aspect ratio";
	private static final String FIELD_TBN = "TBN";
	private static final String FIELD_TBR = "TBR";
	private static final String FIELD_TBC = "TBC";

	/**
	 * Values are displayed to the user in a specific order. This order is saved in an ArrayList.
	 * Index 0 will be displayed first.
	 */
	public static final List<String> KEY_PRIORITIES = new ArrayList<String>() {{
		addAll(Stream.KEY_PRIORITIES);
		add(FIELD_WIDTH);
		add(FIELD_HEIGHT);
		add(FIELD_ASPECT_RATIO);
	}};

	public VideoStream(Integer width, Integer height, String codecName) throws VCException {
		super(codecName);
		// Add values
		setWidth(width);
		setHeight(height);
	}

	public Integer getWidth() {
		return (Integer) getValue(FIELD_WIDTH);
	}

	public Integer getHeight() {
		return (Integer) getValue(FIELD_HEIGHT);
	}

	public String getAspectRatio() {
		return (String) getValue(FIELD_ASPECT_RATIO);
	}

	public String getTBN() {
		return (String) getValue(FIELD_TBN);
	}

	public String getTBR() {
		return (String) getValue(FIELD_TBR);
	}

	public String getTBC() {
		return (String) getValue(FIELD_TBC);
	}

	public VideoStream setWidth(Integer width) throws VCException {
		if (width == null) throw new VCException("Video stream width must be valid!");
		setValue(FIELD_WIDTH, width);
		return this;
	}

	public VideoStream setHeight(Integer height) throws VCException {
		if (height == null) throw new VCException("Video stream height must be valid!");
		setValue(FIELD_HEIGHT, height);
		return this;
	}

	public VideoStream setAspectRatio(String aspectRatio) {
		setValue(FIELD_ASPECT_RATIO, aspectRatio);
		return this;
	}

	public VideoStream setTBN(String tbn) {
		setValue(FIELD_TBN, tbn);
		return this;
	}

	public VideoStream setTBC(String tbc) {
		setValue(FIELD_TBC, tbc);
		return this;
	}

	public VideoStream setTBR(String tbr) {
		setValue(FIELD_TBR, tbr);
		return this;
	}

	@Override
	public List<String> getKeyPriorities() {
		return KEY_PRIORITIES;
	}

	@Override
	public String toString() {
		return String.format("%s\nVideoStream Width: %d, Height: %d, AspectRation: %s",
				super.toString(), getWidth(), getHeight(), getAspectRatio());
	}

}
