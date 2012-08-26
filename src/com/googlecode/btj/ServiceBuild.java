package com.googlecode.btj;

/**
 * Build for a Windows service.
 */
public class ServiceBuild extends BasicBuild {
    /**
     * -
     */
    public ServiceBuild() {
        this.setType(ProjectType.SERVICE);
    }
}
