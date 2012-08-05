package com.googlecode.btj;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import java.util.zip.Deflater;

import org.apache.commons.exec.CommandLine;
import org.apache.commons.exec.DefaultExecutor;
import org.apache.commons.exec.ExecuteWatchdog;
import org.apache.commons.io.FileUtils;

/**
 * Build tool for Java
 */
public class Main {
    private File projectDir;
    private Properties p;
    private File btjDir;
    private File buildDir;
    private String projectName;
    private String jdkPath;
    private String mainClass;
    private String[] jars;

    /**
     * @param f a file or a directory
     * @return newest modification time for a directory, lastModified() for a
     *         file
     */
    private static long recursiveLastModified(File f) {
        long res = f.lastModified();
        if (f.isDirectory()) {
            for (File e : f.listFiles()) {
                long m = recursiveLastModified(e);
                if (m > res)
                    res = m;
            }
        }
        return res;
    }

    /**
     * Main entry point
     * 
     * @param params not used
     */
    public static void main(String[] params) {
        long start = System.currentTimeMillis();
        try {
            new Main().run(params);
            System.out.println(String.format(
                    "===== SUCCESS in %.1f seconds =====",
                    (System.currentTimeMillis() - start) / 1000.0));
        } catch (BuildException e) {
            System.err.println(e.getMessage());
            System.err.println("===== BUILD FAILED =====");
        }
    }

    private void loadSettings() throws BuildException {
        this.p = new Properties();
        InputStream is;
        try {
            is = new FileInputStream(new File(projectDir, "btj.properties"));
            p.load(is);
        } catch (IOException e) {
            throwBuild(e);
        }

        jdkPath = p.getProperty("jdk");
        if (jdkPath == null || jdkPath.isEmpty())
            throw new BuildException("jdk setting is not defined");

        projectName = p.getProperty("project.name");
        if (projectName == null || projectName.isEmpty())
            throw new BuildException("project.name setting is not defined");

        mainClass = p.getProperty("main.class");
        if (mainClass == null || mainClass.isEmpty())
            throw new BuildException("main.class setting is not defined");

        String jars_ = p.getProperty("jars");
        this.jars = jars_.split(";");

        this.buildDir = new File(projectDir, "build");

        // TODO: use winrun4j Java property to get the directory of the .exe
        this.btjDir = new File("C:\\Users\\t\\projects\\btj\\install");
    }

    private static void throwBuild(Exception e) throws BuildException {
        throw (BuildException) new BuildException(e.getMessage()).initCause(e);
    }

    private void run(String[] params2) throws BuildException {
        System.out.println("BTJ 1.2");

        try {
            projectDir = new File(".").getAbsoluteFile().getCanonicalFile();
        } catch (IOException e) {
            throwInternal(e);
        }

        loadSettings();

        if (params2.length == 0)
            build();
        else if (params2.length == 1 && params2[0].equals("run"))
            run_();
        else
            throw new BuildException("Wrong usage");
    }

    private void run_() throws BuildException {
        build();

        String mainClass = p.getProperty("main.class");
        if (mainClass == null || mainClass.isEmpty())
            throw new BuildException("main.class setting is not defined");

        String cmd = "\"" + jdkPath + "\\bin\\java.exe\"";
        cmd += " -jar " + projectName + ".jar";

        system(cmd, new File(buildDir, "target"));
    }

    private void addToJar(File root, File source, JarOutputStream target)
            throws IOException {
        BufferedInputStream in = null;
        try {
            String canonicalPathRoot = root.getCanonicalPath();
            String canonicalPathSource = source.getCanonicalPath();
            String diff;
            if (canonicalPathSource.startsWith(canonicalPathRoot)) {
                diff = canonicalPathSource
                        .substring(canonicalPathRoot.length());
            } else {
                throw new IOException(source + " is not under " + root);
            }

            diff = diff.replace("\\", "/");
            if (diff.startsWith("/"))
                diff = diff.substring(1);

            if (source.isDirectory()) {
                if (!diff.isEmpty()) {
                    if (!diff.endsWith("/"))
                        diff += "/";
                    JarEntry entry = new JarEntry(diff);
                    entry.setTime(source.lastModified());
                    target.putNextEntry(entry);
                    target.closeEntry();
                }

                for (File nestedFile : source.listFiles())
                    addToJar(root, nestedFile, target);
            } else {
                JarEntry entry = new JarEntry(diff);
                entry.setTime(source.lastModified());
                target.putNextEntry(entry);
                in = new BufferedInputStream(new FileInputStream(source));

                byte[] buffer = new byte[1024];
                while (true) {
                    int count = in.read(buffer);
                    if (count == -1)
                        break;
                    target.write(buffer, 0, count);
                }
                target.closeEntry();
            }
        } finally {
            if (in != null)
                in.close();
        }
    }

    private void build() throws BuildException {
        File buildClasses = new File(buildDir, "classes");
        if (!buildClasses.exists())
            buildClasses.mkdirs();

        String cmd = "\"" + jdkPath + "\\bin\\javac.exe\"";
        if (jars != null)
            cmd += " -cp \"" + join(jars, ";") + "\"";
        List<String> params = new ArrayList<>();
        buildJavaCFileParams(new File(projectDir, "src"), params);
        cmd += " -d build\\classes " + join(params);
        system(cmd, projectDir);

        File targetDir = new File(buildDir, "target");
        try {
            FileUtils.forceMkdir(targetDir);
        } catch (IOException e) {
            throw (BuildException) new BuildException(
                    "Cannot create the directory " + targetDir + ": "
                            + e.getMessage()).initCause(e);
        }

        File jarFile = new File(targetDir, projectName + ".jar");
        OutputStream os;
        try {
            os = new FileOutputStream(jarFile);
            String cp = "";
            for (String s : jars) {
                if (cp.length() != 0)
                    cp += ";";
                cp += new File(s).getName();
            }

            Manifest manifest = new Manifest();
            manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION,
                    "1.0");
            manifest.getMainAttributes().put(Attributes.Name.MAIN_CLASS,
                    mainClass);
            if (cp.length() != 0)
                manifest.getMainAttributes().put(Attributes.Name.CLASS_PATH,
                        cp);

            JarOutputStream jar = new JarOutputStream(os, manifest);
            try {
                jar.setMethod(JarOutputStream.DEFLATED);
                jar.setLevel(Deflater.BEST_COMPRESSION);
                addToJar(new File(buildDir, "classes"), new File(buildDir,
                        "classes"), jar);
            } finally {
                jar.close();
            }
        } catch (IOException e) {
            throw (BuildException) new BuildException(
                    "Cannot create the .jar file: " + e.getMessage())
                    .initCause(e);
        }

        try {
            FileUtils.copyDirectory(new File(projectDir, "install"), targetDir);
        } catch (IOException e) {
            throw (BuildException) new BuildException(
                    "Cannot copy \"install\" directory: " + e.getMessage())
                    .initCause(e);
        }

        if (this.jars != null) {
            for (String s : jars) {
                File from = new File(s);
                File to = new File(buildDir, "target\\" + from.getName());
                try {
                    FileUtils.copyFile(from, to);
                } catch (IOException e) {
                    throw (BuildException) new BuildException("Cannot copy "
                            + from + " to " + to + ": " + e.getMessage())
                            .initCause(e);
                }
            }
        }
    }

    private String join(String[] parts, String delimiter) {
        StringBuilder sb = new StringBuilder();
        for (String s: parts) {
            if (sb.length() != 0)
                sb.append(delimiter);
            sb.append(s);
        }
        return sb.toString();
    }

    private static void throwInternal(Exception e) {
        throw (InternalError) new InternalError(e.getMessage()).initCause(e);
    }

    private static String join(List<String> params) {
        StringBuilder sb = new StringBuilder();
        for (String p : params) {
            if (sb.length() != 0)
                sb.append(" ");
            sb.append(p);
        }
        return sb.toString();
    }

    private void buildJavaCFileParams(File dir, List<String> params) {
        File[] files = dir.listFiles();
        boolean use = false;
        for (File f : files) {
            if (f.isDirectory())
                buildJavaCFileParams(f, params);
            else if (!use && f.getName().toLowerCase().endsWith(".java"))
                use = true;
        }
        if (use)
            params.add(dir.getAbsolutePath() + "\\*.java");
    }

    private static void system(String line, File workingDirectory)
            throws BuildException {
        System.out.println(line);
        CommandLine cmdLine = CommandLine.parse(line);
        DefaultExecutor executor = new DefaultExecutor();
        executor.setWorkingDirectory(workingDirectory);
        executor.setExitValue(0);
        ExecuteWatchdog watchdog = new ExecuteWatchdog(60000);
        executor.setWatchdog(watchdog);
        int code;
        try {
            code = executor.execute(cmdLine);
        } catch (IOException e) {
            System.err.println(e.getMessage());
            code = -1;
        }

        if (code != 0)
            throw new BuildException("Process returned the code" + code);
    }

    private Main() {
    }
}
