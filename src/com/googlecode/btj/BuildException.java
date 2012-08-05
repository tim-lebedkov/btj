package com.googlecode.btj;

/**
 * Build error.
 */
public class BuildException extends Exception {
	private static final long serialVersionUID = 1L;

	/**
	 * @param msg message
	 */
	public BuildException(String msg) {
		super(msg);
	}
}
