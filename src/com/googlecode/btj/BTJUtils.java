package com.googlecode.btj;

import java.io.BufferedReader;
import java.io.Console;
import java.io.IOException;
import java.io.InputStreamReader;

/**
 * Utilities.
 */
public class BTJUtils {
    /**
     * Throws an InternalError
     * 
     * @param e cause
     */
    public static void throwInternal(Throwable e) {
        throw (InternalError) new InternalError(e.getMessage()).initCause(e);
    }

    /**
     * Converts a string representation of a project type to a value of type
     * ProjectType
     * 
     * @param t string
     * @return parsed value or null if unknown
     */
    public static ProjectType parseProjectType(String t) {
        ProjectType r;
        if (t.equals("command-line"))
            r = ProjectType.COMMAND_LINE;
        else if (t.equals("service"))
            r = ProjectType.SERVICE;
        else if (t.equals("library"))
            r = ProjectType.LIBRARY;
        else if (t.equals("web"))
            r = ProjectType.WAR;
        else
            r = null;
        return r;
    }

    /**
     * Inputs some text in the console.
     * 
     * @param prompt prompt without :
     * @return null if the EOF has been reached
     */
    public static String inputText(String prompt) {
        Console console = System.console();
        String r = null;
        System.out.print(prompt + ": ");
        if (console != null) {
            r = console.readLine();
        } else {
            InputStreamReader reader = new InputStreamReader(System.in);
            BufferedReader br = new BufferedReader(reader);
            try {
                r = br.readLine();
            } catch (IOException e) {
                throwInternal(e);
            }
        }
        return r;
    }

    /**
     * Throws a BuildException
     * 
     * @param e cause
     */
    public static void throwBuild(Exception e) throws BuildException {
        throw (BuildException) new BuildException(e.getMessage()).initCause(e);
    }
}
