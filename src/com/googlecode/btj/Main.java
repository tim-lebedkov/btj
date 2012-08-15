package com.googlecode.btj;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.Console;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.MissingResourceException;
import java.util.Properties;
import java.util.ResourceBundle;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import java.util.zip.Deflater;
import java.util.zip.ZipOutputStream;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactoryConfigurationError;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.cli.PosixParser;
import org.apache.commons.exec.DefaultExecutor;
import org.apache.commons.exec.ExecuteWatchdog;
import org.apache.commons.io.FileUtils;
import org.boris.winrun4j.INI;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

/**
 * Build tool for Java
 */
public class Main {
    private File projectDir;
    private Properties p;
    private File buildDir;
    private String projectName;
    private String jdkPath;
    private String mainClass;
    private List<File> jars = new ArrayList<>();
    private File btjDir;
    private String version;

    /**
     * @param f a file or a directory
     * @return newest modification time for a directory, lastModified() for a
     *         file
     * 
     *         private static long recursiveLastModified(File f) { long res =
     *         f.lastModified(); if (f.isDirectory()) { for (File e :
     *         f.listFiles()) { long m = recursiveLastModified(e); if (m > res)
     *         res = m; } } return res; }
     */

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

    private void loadSettings(File btjProperties) throws BuildException {
        this.p = new Properties();
        InputStream is;
        try {
            is = new FileInputStream(btjProperties);
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

        version = p.getProperty("version");
        if (version == null || version.isEmpty())
            throw new BuildException("version setting is not defined");

        String jars_ = p.getProperty("jars");
        if (jars_ == null)
            jars_ = "";

        this.jars.clear();
        for (String v : Arrays.asList(jars_.split(";"))) {
            if (!v.isEmpty()) {
                File f = resolvePath(v);
                this.jars.add(f);
            }
        }

        this.buildDir = new File(projectDir, "build");
    }

    private File resolvePath(String v) throws BuildException {
        File res = new File(v);
        if (!res.isAbsolute()) {
            res = new File(this.projectDir, v);
        }
        try {
            res = res.getCanonicalFile();
        } catch (IOException e) {
            throw (BuildException) new BuildException(
                    "Cannot create the canonical version of the path: " +
                            v + ": " + e.getMessage()).initCause(e);
        }
        return res;
    }

    private static void throwBuild(Exception e) throws BuildException {
        throw (BuildException) new BuildException(e.getMessage()).initCause(e);
    }

    private void run(String[] params) throws BuildException {
        String msg = "BTJ";
        try {
            ResourceBundle rb = ResourceBundle
                    .getBundle("com.googlecode.btj.Version");
            msg += " " + rb.getString("version");
        } catch (MissingResourceException e) {
            // ignore
        }
        msg += " - build tool for Java projects";
        System.out.println(msg);

        Options options = new Options();
        Option project = new Option("project", "path to btj.properties");
        project.setArgs(1);
        options.addOption(project);

        CommandLineParser parser = new PosixParser();
        org.apache.commons.cli.CommandLine cmd;
        try {
            cmd = parser.parse(options, params);
        } catch (ParseException e) {
            throw (BuildException) new BuildException(
                    "Cannot parse the command line: " + e.getMessage())
                    .initCause(e);
        }

        String[] freeArgs = cmd.getArgs();

        if (freeArgs.length == 1 && freeArgs[0].equals("help")) {
            help(options);
        } else {
            File btjProperties;
            if (cmd.hasOption("project")) {
                btjProperties = new File(cmd.getOptionValue("project"))
                        .getAbsoluteFile();
                projectDir = btjProperties.getParentFile();
            } else {
                btjProperties = new File("btj.properties").getAbsoluteFile();
                try {
                    projectDir = new File(".").getAbsoluteFile()
                            .getCanonicalFile();
                } catch (IOException e) {
                    throwInternal(e);
                }
            }

            try {
                btjDir = new File(INI.getProperty(INI.MODULE_DIR));
            } catch (UnsatisfiedLinkError e) {
                btjDir = new File("").getAbsoluteFile();
            }
            System.out.println(btjDir);

            loadSettings(btjProperties);

            if (freeArgs.length == 0)
                build();
            else if (freeArgs.length == 1) {
                String arg = freeArgs[0];
                if (arg.equals("package"))
                    build();
                else if (arg.equals("run"))
                    run_();
                else if (arg.equals("profile"))
                    profile();
                else if (arg.equals("clean"))
                    clean();
                else if (arg.equals("create"))
                    create();
                else if (arg.equals("eclipse"))
                    eclipse();
                else
                    throw new BuildException("Unknown command: " + arg);
            } else
                throw new BuildException("Wrong usage");
        }
    }

    private void eclipse() throws BuildException {
        Document d = null;
        try {
            d = javax.xml.parsers.DocumentBuilderFactory.newInstance()
                    .newDocumentBuilder().newDocument();
        } catch (ParserConfigurationException e) {
            throwInternal(e);
        }
        Element classpath = d.createElement("classpath");
        d.appendChild(classpath);

        Element cpe = d.createElement("classpathentry");
        cpe.setAttribute("kind", "src");
        cpe.setAttribute("path", "src");
        classpath.appendChild(cpe);

        cpe = d.createElement("classpathentry");
        cpe.setAttribute("kind", "con");
        cpe.setAttribute(
                "path",
                "org.eclipse.jdt.launching.JRE_CONTAINER/org.eclipse.jdt.internal.debug.ui.launcher.StandardVMType/JDK 1.7.0.4");
        classpath.appendChild(cpe);

        for (File f : jars) {
            cpe = d.createElement("classpathentry");
            cpe.setAttribute("kind", "lib");
            cpe.setAttribute("path", "build/target/lib/" + f.getName());

            /*
             * if (src != null) { cpe.setAttribute("sourcepath", "build/libsrc/"
             * + src.getLocalFile().getName()); } ArtifactDownloadReport javadoc
             * = javadocArtifacts.get(key); if (javadoc != null) { Element
             * attributes = d.createElement("attributes");
             * cpe.appendChild(attributes); Element attribute =
             * d.createElement("attribute"); attributes.appendChild(attribute);
             * attribute.setAttribute("name", "javadoc_location");
             * attribute.setAttribute("value", "jar:platform:/resource/" +
             * this.projectDir.getName() + "/build/libjavadoc/" +
             * src.getLocalFile().getName() + "!/"); }
             */
            classpath.appendChild(cpe);
        }

        cpe = d.createElement("classpathentry");
        cpe.setAttribute("kind", "output");
        cpe.setAttribute("path", "build/classes");
        classpath.appendChild(cpe);

        File classpathFile = new File(projectDir, ".classpath");
        try {
            Transformer t = javax.xml.transform.TransformerFactory
                    .newInstance().newTransformer();
            t.transform(new DOMSource(d), new StreamResult(classpathFile));
        } catch (TransformerConfigurationException
                | TransformerFactoryConfigurationError e) {
            throwInternal(e);
        } catch (TransformerException e) {
            throw (BuildException) new BuildException("Cannot save " +
                    classpathFile + ": " + e.getMessage()).initCause(e);
        }

        String txt = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\r\n" +
                "<projectDescription>\r\n" +
                "    <name>" +
                this.projectName +
                "</name>\r\n" +
                "    <comment></comment>\r\n" +
                "    <projects>\r\n" +
                "    </projects>\r\n" +
                "    <buildSpec>\r\n" +
                "        <buildCommand>\r\n" +
                "            <name>org.eclipse.jdt.core.javabuilder</name>\r\n" +
                "            <arguments>\r\n" + "            </arguments>\r\n" +
                "        </buildCommand>\r\n" + "    </buildSpec>\r\n" +
                "    <natures>\r\n" +
                "        <nature>org.eclipse.jdt.core.javanature</nature>\r\n" +
                "    </natures>\r\n" + "</projectDescription>\r\n";
        File project = new File(this.projectDir, ".project");
        write(project, txt);
    }

    private void create() throws BuildException {
        this.projectName = inputText("Enter name for the new project");
        if (this.projectName != null) {
            this.projectDir = new File(projectName).getAbsoluteFile();
            try {
                FileUtils.forceMkdir(new File(projectDir, "src\\" +
                        projectName.toLowerCase()));
                Properties p = new Properties();
                p.put("project.name", this.projectName);
                p.put("version", "0.1");
                p.put("jdk", "<please enter the path to the JDK here>");
                p.put("main.class", this.projectName.toLowerCase() + ".Main");
                OutputStream os = new FileOutputStream(new File(projectDir,
                        "btj.properties"));
                try {
                    p.store(os, "");
                } finally {
                    os.close();
                }
                String source = "package " + this.projectName.toLowerCase() +
                        ";\r\n\r\n" + "public class Main {\r\n" +
                        "    public static void main(String[] params) {\r\n" +
                        "        System.out.println(\"Hello, world!\");\r\n" +
                        "    }\r\n" + "}\r\n";
                FileUtils.write(
                        new File(projectDir, "src\\" +
                                projectName.toLowerCase() + "\\Main.java"),
                        source);
            } catch (IOException e) {
                throw (BuildException) new BuildException(
                        "Cannot create the project: " + e.getMessage())
                        .initCause(e);
            }
        }
    }

    private void help(Options options) {
        HelpFormatter hf = new HelpFormatter();
        hf.setLeftPadding(2);
        hf.printHelp("btj <command> [options]", options);
        System.out
                .println("Commands:\r\n"
                        + "  package - compiles and packages the program. This command is run by default if none is specified.\r\n"
                        + "  create - creates a new empty project\r\n"
                        + "  eclipse - creates/overwrites the .classpath and .project files for Eclipse 4.2 Juno\r\n"
                        + "  run - runs the program\r\n"
                        + "  profile - profiles the CPU usage\r\n"
                        + "  clean - deletes all created files\r\n"
                        + "  help - shows help");
    }

    private void clean() throws BuildException {
        try {
            File buildDir = new File(projectDir, "build");
            FileUtils.deleteDirectory(buildDir);
        } catch (IOException e) {
            throw (BuildException) new BuildException(
                    "Cannot delete the directory " + buildDir + ": " +
                            e.getMessage()).initCause(e);
        }
    }

    private void profile() throws BuildException {
        build();

        String args = inputText("Enter command line arguments for " +
                this.projectName);
        if (args != null) {
            String cmd = "\"" + jdkPath + "\\bin\\java.exe\"";
            cmd += " -agentlib:hprof=cpu=samples,file=..\\java.hprof.txt";
            cmd += " -jar " + projectName + ".jar " + args;

            system(cmd, new File(buildDir, "target"));
        }
    }

    /**
     * Inputs some text in the console.
     * 
     * @param prompt prompt without :
     * @return null if the EOF has been reached
     */
    private String inputText(String prompt) {
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

    private void run_() throws BuildException {
        build();

        String args = inputText("Enter command line arguments for " +
                this.projectName);
        if (args != null) {
            String cmd = "\"" + jdkPath + "\\bin\\java.exe\"";
            cmd += " -jar " + projectName + ".jar " + args;

            system(cmd, new File(buildDir, "target"));
        }
    }

    private void addToJar(File root, File source, ZipOutputStream target)
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

        List<String> jars_ = new ArrayList<>();
        for (File f: this.jars)
            jars_.add(f.getAbsolutePath());
            
        String cmd = "\"" + jdkPath + "\\bin\\javac.exe\"";
        if (jars.size() != 0)
            cmd += " -cp \"" + join(jars_, ";") + "\"";
        List<String> params = new ArrayList<>();
        buildJavaCFileParams(new File(projectDir, "src"), params);
        cmd += " -d build\\classes " + join(params, " ");
        system(cmd, projectDir);

        File targetDir = new File(buildDir, "target");
        try {
            FileUtils.forceMkdir(targetDir);
        } catch (IOException e) {
            throw (BuildException) new BuildException(
                    "Cannot create the directory " + targetDir + ": " +
                            e.getMessage()).initCause(e);
        }

        String path = mainClass.replace('.', '\\');
        File versionFile = new File(
                new File(buildDir, "classes\\" + path).getParentFile(),
                "Version.properties");
        try {
            FileUtils.write(versionFile, "version=" + version + "\r\n");
        } catch (IOException e) {
            throw (BuildException) new BuildException(
                    "Cannot create the file " + versionFile + ": " +
                            e.getMessage()).initCause(e);
        }

        File libDir = new File(targetDir, "lib");
        try {
            FileUtils.forceMkdir(libDir);
        } catch (IOException e) {
            throw (BuildException) new BuildException(
                    "Cannot create the directory " + libDir + ": " +
                            e.getMessage()).initCause(e);
        }

        File jarFile = new File(libDir, projectName + ".jar");
        OutputStream os;
        try {
            os = new FileOutputStream(jarFile);
            String cp = "";
            for (File f : jars) {
                cp += f.getName();
            }

            Manifest manifest = new Manifest();
            manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION,
                    "1.0");
            manifest.getMainAttributes().put(Attributes.Name.MAIN_CLASS,
                    mainClass);
            if (cp.length() != 0)
                manifest.getMainAttributes().put(Attributes.Name.CLASS_PATH,
                        cp.replace(';', ' '));

            JarOutputStream jar = new JarOutputStream(os, manifest);
            try {
                jar.setMethod(JarOutputStream.DEFLATED);
                jar.setLevel(Deflater.BEST_COMPRESSION);
                addToJar(new File(buildDir, "classes"), new File(buildDir,
                        "classes"), jar);
                File resources = new File(projectDir, "resources");
                if (resources.exists())
                    addToJar(resources, resources, jar);
            } finally {
                jar.close();
            }
        } catch (IOException e) {
            throw (BuildException) new BuildException(
                    "Cannot create the .jar file: " + e.getMessage())
                    .initCause(e);
        }

        try {
            File installDir = new File(projectDir, "install");
            if (installDir.exists())
                FileUtils.copyDirectory(installDir, targetDir);
        } catch (IOException e) {
            throw (BuildException) new BuildException(
                    "Cannot copy \"install\" directory: " + e.getMessage())
                    .initCause(e);
        }

        if (this.jars != null) {
            for (File from : jars) {
                File to = new File(libDir, from.getName());
                try {
                    FileUtils.copyFile(from, to);
                } catch (IOException e) {
                    throw (BuildException) new BuildException("Cannot copy " +
                            from + " to " + to + ": " + e.getMessage())
                            .initCause(e);
                }
            }
        }

        File libSrcDir = new File(projectDir, "build\\libsrc");
        try {
            FileUtils.forceMkdir(libSrcDir);
        } catch (IOException e) {
            throw (BuildException) new BuildException(
                    "Cannot create the directory " + libSrcDir + ": " +
                            e.getMessage()).initCause(e);
        }

        File libJavadocDir = new File(projectDir, "build\\libjavadoc");
        try {
            FileUtils.forceMkdir(libJavadocDir);
        } catch (IOException e) {
            throw (BuildException) new BuildException(
                    "Cannot create the directory " + libJavadocDir + ": " +
                            e.getMessage()).initCause(e);
        }

        String ini = "main.class=" + this.mainClass + "\r\n" +
                "classpath.1=lib\\*.jar\r\n" + "log.level=error\r\n";

        copyFile(new File(this.btjDir, "winrun4j\\bin\\WinRun4Jc.exe"),
                new File(targetDir, projectName + "32.exe"));
        write(new File(buildDir, "target\\" + projectName + "32.ini"), ini);

        copyFile(new File(this.btjDir, "winrun4j\\bin\\WinRun4J64c.exe"),
                new File(targetDir, projectName + ".exe"));
        write(new File(buildDir, "target\\" + projectName + ".ini"), ini);

        File zipFile = new File(buildDir, projectName + ".zip");
        try {
            os = new FileOutputStream(zipFile);
            ZipOutputStream jar = new ZipOutputStream(os);
            try {
                jar.setMethod(JarOutputStream.DEFLATED);
                jar.setLevel(Deflater.BEST_COMPRESSION);
                addToJar(targetDir, targetDir, jar);
            } finally {
                jar.close();
            }
        } catch (IOException e) {
            throw (BuildException) new BuildException(
                    "Cannot create the .zip file: " + e.getMessage())
                    .initCause(e);
        }
    }

    private void write(File file, String txt) throws BuildException {
        try {
            FileUtils.write(file, txt);
        } catch (IOException e) {
            throw (BuildException) new BuildException("Cannot save " + file +
                    ": " + e.getMessage()).initCause(e);
        }
    }

    private void copyFile(File from, File to) throws BuildException {
        try {
            FileUtils.copyFile(from, to);
        } catch (IOException e) {
            throw (BuildException) new BuildException("Cannot copy " + from +
                    " to " + to + ": " + e.getMessage()).initCause(e);
        }
    }

    private static void throwInternal(Throwable e) {
        throw (InternalError) new InternalError(e.getMessage()).initCause(e);
    }

    private static String join(List<String> params, String delimiter) {
        StringBuilder sb = new StringBuilder();
        for (String p : params) {
            if (sb.length() != 0)
                sb.append(delimiter);
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
        org.apache.commons.exec.CommandLine cmdLine = org.apache.commons.exec.CommandLine
                .parse(line);
        DefaultExecutor executor = new DefaultExecutor();
        executor.setWorkingDirectory(workingDirectory);
        executor.setExitValue(0);
        ExecuteWatchdog watchdog = new ExecuteWatchdog(60000);
        executor.setWatchdog(watchdog);
        int code;
        try {
            code = executor.execute(cmdLine);
        } catch (IOException e) {
            throw (BuildException) new BuildException("Execution failed: " +
                    e.getMessage()).initCause(e);
        }

        if (code != 0)
            throw new BuildException("Process returned the code" + code);
    }

    private Main() {
    }
}
