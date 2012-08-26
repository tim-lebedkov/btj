package com.googlecode.btj;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.jar.Attributes;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import java.util.zip.Deflater;
import java.util.zip.ZipOutputStream;

import org.apache.commons.io.FileUtils;

/**
 * Builds web applications.
 */
public class WebAppBuild extends BasicBuild {
    /**
     * -
     */
    public WebAppBuild() {
        this.setType(ProjectType.WAR);
    }

    @Override
    public void eclipse() throws BuildException {
        throw new BuildException(
                "Cannot create Eclipse project files for a web application");
    }

    @Override
    public void profile() throws BuildException {
        throw new BuildException("Cannot profile a web application");
    }

    @Override
    public void run_() throws BuildException {
        throw new BuildException("Cannot run a web application");
    }

    public void jar() throws BuildException {
        File projectDir = getProjectDir();
        File buildDir = getBuildDir();
        String projectName = getProjectName();
        File warDir = new File(buildDir, "war");
        File webInfDir = new File(warDir, "WEB-INF");
        File webInfLibDir = new File(webInfDir, "lib");
        forceMkDir(webInfLibDir);

        File jarFile = new File(webInfLibDir, projectName + ".jar");
        OutputStream os;
        try {
            os = new FileOutputStream(jarFile);

            Manifest manifest = new Manifest();
            manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION,
                    "1.0");

            JarOutputStream jar = new JarOutputStream(os, manifest);
            try {
                jar.setMethod(JarOutputStream.DEFLATED);
                jar.setLevel(Deflater.BEST_COMPRESSION);
                Set<String> entries = new HashSet<>();
                addToJar(new File(buildDir, "classes"), new File(buildDir,
                        "classes"), jar, entries);
                File resources = new File(projectDir, "resources");
                if (resources.exists())
                    addToJar(resources, resources, jar, entries);
                addToJar(new File(buildDir, "props"), new File(buildDir,
                        "props"), jar, entries);
            } finally {
                jar.close();
            }
        } catch (IOException e) {
            throw (BuildException) new BuildException(
                    "Cannot create the .jar file: " + e.getMessage())
                    .initCause(e);
        }
    }

    @Override
    public void build() throws BuildException {
        compile();

        File projectDir = getProjectDir();
        File buildDir = getBuildDir();
        String version = getVersion();
        String topPackage = getTopPackage();

        // create Version.properties
        File versionFile = new File(new File(buildDir, "classes\\" +
                topPackage.replace('.', '\\')), "Version.properties");
        write(versionFile, "version=" + version + "\r\n");

        extractTexts();

        File targetDir = new File(buildDir, "target");
        forceMkDir(targetDir);

        jar();

        List<File> jars = getJars();
        File webInfLibDir = new File(buildDir, "war\\WEB-INF\\lib");
        if (jars != null) {
            for (File from : jars) {
                File to = new File(webInfLibDir, from.getName());
                copyFile(from, to);
            }
        }

        war();

        try {
            File installDir = new File(projectDir, "install");
            if (installDir.exists())
                FileUtils.copyDirectory(installDir, targetDir);
        } catch (IOException e) {
            throw (BuildException) new BuildException(
                    "Cannot copy \"install\" directory: " + e.getMessage())
                    .initCause(e);
        }

        zip();
    }

    private void war() throws BuildException {
        File warDir = new File(getBuildDir(), "war");
        OutputStream os;
        File zipFile = new File(getBuildDir(), "target\\" + getProjectName() +
                ".war");
        try {
            os = new FileOutputStream(zipFile);
            ZipOutputStream war = new ZipOutputStream(os);
            try {
                war.setMethod(JarOutputStream.DEFLATED);
                war.setLevel(Deflater.BEST_COMPRESSION);
                HashSet<String> entries = new HashSet<String>();
                addToJar(warDir, warDir, war, entries);

                File webDir = new File(getProjectDir(), "web");
                if (webDir.exists())
                    addToJar(webDir, webDir, war, entries);
            } finally {
                war.close();
            }
        } catch (IOException e) {
            throw (BuildException) new BuildException(
                    "Cannot create the .zip file: " + e.getMessage())
                    .initCause(e);
        }
    }
}
