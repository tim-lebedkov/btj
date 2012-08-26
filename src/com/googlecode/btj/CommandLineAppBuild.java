package com.googlecode.btj;

/**
 * Build for command line based appilcations.
 */
public class CommandLineAppBuild extends BasicBuild {
    /**
     * -
     */
    public CommandLineAppBuild() {
        this.setType(ProjectType.COMMAND_LINE);
    }
}
