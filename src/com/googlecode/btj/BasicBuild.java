package com.googlecode.btj;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import java.util.zip.Deflater;
import java.util.zip.ZipOutputStream;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.TransformerFactoryConfigurationError;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.apache.commons.exec.DefaultExecutor;
import org.apache.commons.exec.ExecuteWatchdog;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.fedorahosted.tennera.jgettext.Catalog;
import org.fedorahosted.tennera.jgettext.Message;
import org.fedorahosted.tennera.jgettext.PoParser;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

/**
 * Abstract parent class for builds.
 */
public abstract class BasicBuild {
    private File projectDir;
    private String projectName;
    private String jdkPath;
    private String topPackage = "example";
    private List<File> jars = new ArrayList<>();
    private File btjDir;
    private String version;
    private ProjectType type = ProjectType.COMMAND_LINE;

    /**
     * -
     */
    public BasicBuild() {
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
                    "Cannot create the canonical version of the path: " + v +
                            ": " + e.getMessage()).initCause(e);
        }
        return res;
    }

    public void eclipse() throws BuildException {
        Document d = null;
        try {
            d = javax.xml.parsers.DocumentBuilderFactory.newInstance()
                    .newDocumentBuilder().newDocument();
        } catch (ParserConfigurationException e) {
            BTJUtils.throwInternal(e);
        }
        Element classpath = d.createElement("classpath");
        d.appendChild(classpath);

        Element cpe = d.createElement("classpathentry");
        cpe.setAttribute("kind", "src");
        cpe.setAttribute("path", "src");
        classpath.appendChild(cpe);

        if (new File(this.projectDir, "resources").exists()) {
            cpe = d.createElement("classpathentry");
            cpe.setAttribute("kind", "lib");
            cpe.setAttribute("path", "resources");
            classpath.appendChild(cpe);
        }

        cpe = d.createElement("classpathentry");
        cpe.setAttribute("kind", "lib");
        cpe.setAttribute("path", "build/props");
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
            cpe.setAttribute("path", f.getAbsolutePath().replace('\\', '/'));

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
        cpe.setAttribute("path", "build/eclipseclasses");
        classpath.appendChild(cpe);

        File classpathFile = new File(projectDir, ".classpath");
        try {
            TransformerFactory tf = javax.xml.transform.TransformerFactory
                    .newInstance();
            Transformer t = tf.newTransformer();
            t.setOutputProperty(OutputKeys.INDENT, "yes");
            t.transform(new DOMSource(d), new StreamResult(classpathFile));
        } catch (TransformerConfigurationException
                | TransformerFactoryConfigurationError e) {
            BTJUtils.throwInternal(e);
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

        forceMkDir(new File(this.projectDir, ".settings"));

        try {
            File f = new File(this.projectDir,
                    ".settings\\org.eclipse.core.resources.prefs");
            Properties p = new Properties();
            if (f.exists()) {
                FileInputStream is = new FileInputStream(f);
                p.load(is);
                is.close();
            }
            p.put("eclipse.preferences.version", "1");
            p.put("encoding/<project>", "UTF-8");
            FileOutputStream os = new FileOutputStream(f);
            p.store(os, "");
            os.close();
        } catch (IOException e) {
            BTJUtils.throwBuild(e);
        }
    }

    public void clean() throws BuildException {
        try {
            File buildDir = new File(projectDir, "build");
            FileUtils.deleteDirectory(buildDir);
        } catch (IOException e) {
            throw (BuildException) new BuildException(
                    "Cannot delete the directory " + getBuildDir() + ": " +
                            e.getMessage()).initCause(e);
        }
    }

    public void profile() throws BuildException {
        build();

        String args = BTJUtils.inputText("Enter command line arguments for " +
                this.projectName);
        if (args != null) {
            String cmd = "\"" + jdkPath + "\\bin\\java.exe\"";
            cmd += " -agentlib:hprof=cpu=samples,file=..\\java.hprof.txt";
            cmd += " -jar lib\\" + projectName + ".jar " + args;

            system(cmd, new File(getBuildDir(), "target"), null);
        }
    }

    public void run_() throws BuildException {
        build();

        String args = BTJUtils.inputText("Enter command line arguments for " +
                this.projectName);
        if (args != null) {
            String cmd = "\"" + jdkPath + "\\bin\\java.exe\"";
            cmd += " -jar lib\\" + projectName + ".jar " + args;

            system(cmd, new File(getBuildDir(), "target"), null);
        }
    }

    protected void addToJar(File root, File source, ZipOutputStream target,
            Set<String> entries) throws IOException {
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
                    if (!entries.contains(diff)) {
                        JarEntry entry = new JarEntry(diff);
                        entry.setTime(source.lastModified());
                        target.putNextEntry(entry);
                        target.closeEntry();
                        entries.add(diff);
                    }
                }

                for (File nestedFile : source.listFiles())
                    addToJar(root, nestedFile, target, entries);
            } else {
                if (!entries.contains(diff)) {
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
                } else {
                    System.out.println("Skipping " + diff +
                            ". It is already in the .zip/.jar");
                }
            }
        } finally {
            if (in != null)
                in.close();
        }
    }

    public void build() throws BuildException {
        compile();

        // create Version.properties
        File versionFile = new File(new File(getBuildDir(), "classes\\" +
                topPackage.replace('.', '\\')), "Version.properties");
        write(versionFile, "version=" + version + "\r\n");

        extractTexts();

        File targetDir = new File(getBuildDir(), "target");
        forceMkDir(targetDir);
        File libDir = new File(targetDir, "lib");
        forceMkDir(libDir);

        jar();

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
                copyFile(from, to);
            }
        }

        File libSrcDir = new File(projectDir, "build\\libsrc");
        forceMkDir(libSrcDir);

        File libJavadocDir = new File(projectDir, "build\\libjavadoc");
        forceMkDir(libJavadocDir);

        if (this.type == ProjectType.COMMAND_LINE ||
                this.type == ProjectType.SERVICE) {
            copyWinRun4jFiles();
        }

        zip();
    }

    private void copyWinRun4jFiles() throws BuildException {
        File targetDir = new File(getBuildDir(), "target");
        String ini;
        if (this.type == ProjectType.COMMAND_LINE) {
            ini = "main.class=" + this.topPackage + ".Main\r\n" +
                    "classpath.1=lib\\*.jar\r\n" + "log.level=error\r\n";
        } else {
            ini = "service.class=" + this.topPackage + ".Main\r\n" +
                    "service.id=" + projectName + "\r\n" + "service.name=" +
                    projectName + "\r\n" + "service.description=" +
                    projectName + "\r\n" + "classpath.1=lib\\*.jar\r\n" +
                    "log.level=error\r\n";
        }

        copyFile(new File(this.btjDir, "winrun4j\\bin\\WinRun4Jc.exe"),
                new File(targetDir, projectName + "32.exe"));
        write(new File(getBuildDir(), "target\\" + projectName + "32.ini"), ini);

        copyFile(new File(this.btjDir, "winrun4j\\bin\\WinRun4J64c.exe"),
                new File(targetDir, projectName + ".exe"));
        write(new File(getBuildDir(), "target\\" + projectName + ".ini"), ini);
    }

    protected void zip() throws BuildException {
        File targetDir = new File(getBuildDir(), "target");
        OutputStream os;
        File zipFile = new File(getBuildDir(), projectName + ".zip");
        try {
            os = new FileOutputStream(zipFile);
            ZipOutputStream jar = new ZipOutputStream(os);
            try {
                jar.setMethod(JarOutputStream.DEFLATED);
                jar.setLevel(Deflater.BEST_COMPRESSION);
                addToJar(targetDir, targetDir, jar, new HashSet<String>());
            } finally {
                jar.close();
            }
        } catch (IOException e) {
            throw (BuildException) new BuildException(
                    "Cannot create the .zip file: " + e.getMessage())
                    .initCause(e);
        }
    }

    public void jar() throws BuildException {
        File targetDir = new File(getBuildDir(), "target");
        forceMkDir(targetDir);
        File libDir = new File(targetDir, "lib");

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
            if (this.type != ProjectType.LIBRARY &&
                    this.type != ProjectType.WAR) {
                manifest.getMainAttributes().put(Attributes.Name.MAIN_CLASS,
                        topPackage + ".Main");
            }
            if (cp.length() != 0 && this.type != ProjectType.WAR)
                manifest.getMainAttributes().put(Attributes.Name.CLASS_PATH,
                        cp.replace(';', ' '));

            JarOutputStream jar = new JarOutputStream(os, manifest);
            try {
                jar.setMethod(JarOutputStream.DEFLATED);
                jar.setLevel(Deflater.BEST_COMPRESSION);
                Set<String> entries = new HashSet<>();
                addToJar(new File(getBuildDir(), "classes"), new File(
                        getBuildDir(), "classes"), jar, entries);
                File resources = new File(projectDir, "resources");
                if (resources.exists())
                    addToJar(resources, resources, jar, entries);
                addToJar(new File(getBuildDir(), "props"), new File(
                        getBuildDir(), "props"), jar, entries);
            } finally {
                jar.close();
            }
        } catch (IOException e) {
            throw (BuildException) new BuildException(
                    "Cannot create the .jar file: " + e.getMessage())
                    .initCause(e);
        }
    }

    protected void compile() throws BuildException {
        File buildClasses = new File(getBuildDir(), "classes");
        if (!buildClasses.exists())
            buildClasses.mkdirs();

        List<String> javaFilesParams = new ArrayList<>();
        buildJavaCFileParams(new File(projectDir, "src"), javaFilesParams);

        List<String> jars_ = new ArrayList<>();
        for (File f : this.jars)
            jars_.add(f.getAbsolutePath());

        // compile .java files
        String cmd = "\"" + jdkPath + "\\bin\\javac.exe\"";
        if (jars.size() != 0)
            cmd += " -cp \"" + BTJUtils.join(jars_, ";") + "\"";
        cmd += " -encoding UTF-8 -d build\\classes " +
                BTJUtils.join(javaFilesParams, " ");
        system(cmd, projectDir, null);
    }

    /**
     * Extract texts
     */
    protected void extractTexts() throws BuildException {
        File buildClasses = new File(getBuildDir(), "classes");
        if (!buildClasses.exists())
            buildClasses.mkdirs();

        List<String> params = new ArrayList<>();
        buildJavaCFileParams(new File(projectDir, "src"), params);

        forceMkDir(new File(getBuildDir(), "po"));
        File xgettext = new File(this.btjDir, "gettext\\bin\\xgettext.exe");
        File pot = new File(getBuildDir(), "po\\keys.pot");
        system("\"" + xgettext.getAbsolutePath() +
                "\" -ktrc:1c,2 -ktrnc:1c,2,3 -ktr -kmarktr -ktrn:1,2 " +
                "--from-code=utf-8 " + "-o \"" + pot.getAbsolutePath() + "\" " +
                BTJUtils.join(params, " "), this.projectDir, null);
        File propsDir = new File(getBuildDir(), "props\\" +
                topPackage.replace('.', '\\'));
        forceMkDir(propsDir);
        write(new File(propsDir, "i18n.properties"), "basename=" + topPackage +
                ".Messages\r\n");
        write(new File(propsDir, "Messages.properties"), "");

        // msgmerge
        File projectPODir = new File(this.projectDir, "po");
        if (projectPODir.exists()) {
            Collection<File> pos = FileUtils.listFiles(projectPODir,
                    new String[] { "po" }, false);
            File msgmerge = new File(this.btjDir, "gettext\\bin\\msgmerge.exe");
            for (File po : pos) {
                System.out.println("po: " + po);
                system("\"" + msgmerge.getAbsolutePath() + "\" " + " -o \"" +
                        po.getAbsolutePath() + "\" " + " \"" +
                        po.getAbsolutePath() + "\" " + " \"" +
                        pot.getAbsolutePath() + "\"", this.projectDir, null);
            }

            // .po -> .properties
            PoParser poParser = new PoParser();
            try {
                for (File po : pos) {
                    Catalog c = poParser.parseCatalog(po);
                    Properties p = new Properties();
                    Iterator<Message> it = c.iterator();
                    while (it.hasNext()) {
                        Message m = it.next();
                        if (!m.getMsgid().trim().isEmpty() && !m.isFuzzy() &&
                                !m.getMsgstr().isEmpty())
                            p.put(m.getMsgid(), m.getMsgstr());
                    }
                    FileOutputStream os = new FileOutputStream(new File(
                            propsDir, "Messages_" +
                                    po.getName().substring(0,
                                            po.getName().length() - 3) +
                                    ".properties"));
                    p.store(os, "");
                    os.close();
                }
            } catch (
                    org.fedorahosted.tennera.jgettext.catalog.parse.ParseException
                    | IOException e) {
                throw (BuildException) new BuildException(e.getMessage())
                        .initCause(e);
            }
        }
    }

    protected void forceMkDir(File dir) throws BuildException {
        try {
            FileUtils.forceMkdir(dir);
        } catch (IOException e) {
            throw (BuildException) new BuildException(
                    "Cannot create the directory " + dir + ": " +
                            e.getMessage()).initCause(e);
        }
    }

    protected void write(File file, String txt) throws BuildException {
        try {
            FileUtils.write(file, txt, "UTF-8");
        } catch (IOException e) {
            throw (BuildException) new BuildException("Cannot save " + file +
                    ": " + e.getMessage()).initCause(e);
        }
    }

    protected void copyFile(File from, File to) throws BuildException {
        try {
            FileUtils.copyFile(from, to);
        } catch (IOException e) {
            throw (BuildException) new BuildException("Cannot copy " + from +
                    " to " + to + ": " + e.getMessage()).initCause(e);
        }
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

    /**
     * 
     * @param line
     * @param workingDirectory
     * @param env environment. The environment for this process will be used if
     *            null
     * @throws BuildException
     */
    protected static void system(String line, File workingDirectory,
            Map<String, String> env) throws BuildException {
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
            code = executor.execute(cmdLine, env);
        } catch (IOException e) {
            throw (BuildException) new BuildException("Execution failed: " +
                    e.getMessage()).initCause(e);
        }

        if (code != 0)
            throw new BuildException("Process returned the code" + code);
    }

    private String readResource(String name) throws IOException {
        InputStream is = Main.class.getResourceAsStream(name);
        String r;
        try {
            r = IOUtils.toString(is, "UTF-8");
        } finally {
            is.close();
        }
        return r;
    }

    public void loadSettings(Properties p) throws BuildException {
        jdkPath = p.getProperty("jdk");
        if (jdkPath == null || jdkPath.isEmpty())
            throw new BuildException("jdk setting is not defined");

        projectName = p.getProperty("project.name");
        if (projectName == null || projectName.isEmpty())
            throw new BuildException("project.name setting is not defined");

        topPackage = p.getProperty("top.package");
        if (topPackage == null || topPackage.isEmpty())
            throw new BuildException("top.package setting is not defined");

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
    }

    public void setProjectDir(File projectDir) {
        this.projectDir = projectDir;
    }

    public void setBTJDir(File d) {
        this.btjDir = d;
    }

    public void create() throws BuildException {
        forceMkDir(new File(projectDir, "src\\" + projectName.toLowerCase()));
        try {
            Properties p = new Properties();
            p.put("project.name", this.projectName);
            p.put("version", "0.1");
            p.put("jdk", "<please enter the path to the JDK here>");
            p.put("top.package", this.projectName.toLowerCase());
            p.put("project.type", ProjectType.toString(this.type));
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
        if (this.type == ProjectType.COMMAND_LINE) {
            source = "package " + this.projectName.toLowerCase() + ";\r\n\r\n" +
                    "public class Main {\r\n" +
                    "    public static void main(String[] params) {\r\n" +
                    "        System.out.println(\"Hello, world!\");\r\n" +
                    "    }\r\n" + "}\r\n";
            write(new File(this.projectDir, "src\\" +
                    this.projectName.toLowerCase() + "\\Main.java"), source);
        } else if (this.type == ProjectType.LIBRARY) {
            source = "package " + this.projectName.toLowerCase() + ";\r\n\r\n" +
                    "public class Utils {\r\n" + "}\r\n";
            write(new File(this.projectDir, "src\\" +
                    this.projectName.toLowerCase() + "\\Utils.java"), source);
        } else if (this.type == ProjectType.WAR) {
            forceMkDir(new File(this.projectDir, "web"));
            try {
                source = readResource("index.jsp").replace("%package%",
                        this.projectName.toLowerCase());
            } catch (IOException e) {
                BTJUtils.throwBuild(e);
            }
            write(new File(this.projectDir, "web\\index.jsp"), source);
        } else {
            try {
                source = readResource("Service.txt").replace("%package%",
                        this.projectName.toLowerCase());
            } catch (IOException e) {
                BTJUtils.throwBuild(e);
            }
            write(new File(this.projectDir, "src\\" +
                    this.projectName.toLowerCase() + "\\Main.java"), source);
        }
    }

    public void setProjectName(String projectName) {
        this.projectName = projectName;
    }

    public String getProjectName() {
        return this.projectName;
    }

    public File getProjectDir() {
        return this.projectDir;
    }

    public String getJDKPath() {
        return this.jdkPath;
    }

    public List<File> getJars() {
        return jars;
    }

    public ProjectType getType() {
        return type;
    }

    public String getTopPackage() {
        return topPackage;
    }

    public void setType(ProjectType pt) {
        this.type = pt;
    }

    public File getBuildDir() {
        return new File(this.projectDir, "build");
    }

    public String getVersion() {
        return version;
    }
}
