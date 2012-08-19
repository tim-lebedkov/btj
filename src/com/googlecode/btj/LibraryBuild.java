package com.googlecode.btj;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Properties;

/**
 * Build for libraries.
 */
public class LibraryBuild extends BasicBuild {
    @Override
    public void profile() throws BuildException {
        throw new BuildException("Cannot profile a library");
    }

    @Override
    public void run_() throws BuildException {
        throw new BuildException("Cannot run a library");
    }

    @Override
    public void create() throws BuildException {
        String projectName = getProjectName();
        File projectDir = getProjectDir();
        forceMkDir(new File(projectDir, "src\\" + projectName.toLowerCase()));
        try {
            Properties p = new Properties();
            p.put("project.name", projectName);
            p.put("version", "0.1");
            p.put("jdk", "<please enter the path to the JDK here>");
            p.put("project.type", "library");
            OutputStream os = new FileOutputStream(new File(projectDir,
                    "btj.properties"));
            try {
                p.store(os, "");
            } finally {
                os.close();
            }
        } catch (IOException e) {
            throw (BuildException) new BuildException(
                    "Cannot create the project: " + e.getMessage())
                    .initCause(e);
        }
        String source = null;
        source = "package " + projectName.toLowerCase() + ";\r\n\r\n" +
                "public class Utils {\r\n" + "}\r\n";
        write(new File(projectDir, "src\\" + projectName.toLowerCase() +
                "\\Utils.java"), source);
    }
}
