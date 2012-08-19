package com.googlecode.btj;

/**
 * Project type.
 */
public enum ProjectType {
    COMMAND_LINE, SERVICE, LIBRARY, WAR;

    public static String toString(ProjectType type) {
        String r;
        switch (type) {
        case COMMAND_LINE:
            r = "command-line";
            break;
        case SERVICE:
            r = "service";
            break;
        case LIBRARY:
            r = "library";
            break;
        case WAR:
            r = "web";
            break;
        default:
            throw new InternalError("Unknown type");
        }
        return r;
    }
}
