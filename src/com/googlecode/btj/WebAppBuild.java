package com.googlecode.btj;

/**
 * Builds web applications.
 */
public class WebAppBuild extends BasicBuild {
    @Override
    public void profile() throws BuildException {
        throw new BuildException("Cannot profile a web application");
    }

    @Override
    public void run_() throws BuildException {
        throw new BuildException("Cannot run a web application");
    }
}
