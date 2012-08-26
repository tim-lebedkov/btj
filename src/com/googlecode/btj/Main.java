package com.googlecode.btj;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.MissingResourceException;
import java.util.Properties;
import java.util.ResourceBundle;

import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.cli.PosixParser;
import org.boris.winrun4j.INI;

/**
 * Build tool for Java
 */
public class Main {
    private static final String[] COMMANDS = { "help", "create", "package",
            "run", "profile", "clean", "eclipse" };

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

    private void run(String[] params) throws BuildException {
        File btjDir;
        try {
            btjDir = new File(INI.getProperty(INI.MODULE_DIR));
        } catch (UnsatisfiedLinkError e) {
            btjDir = new File("").getAbsoluteFile();
        }

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
        String command;
        if (freeArgs.length == 0)
            command = "package";
        else
            command = freeArgs[0].toLowerCase().trim();

        String matchingCommands = null;
        for (String c : COMMANDS) {
            if (c.startsWith(command)) {
                if (matchingCommands == null)
                    matchingCommands = c;
                else
                    matchingCommands += ", " + c;
            }
        }

        if (matchingCommands == null) {
            throw new BuildException("Unknown command: " + command);
        } else if (matchingCommands.contains(",")) {
            throw new BuildException("More than one command match the prefix " +
                    command + ": " + matchingCommands);
        } else {
            if (matchingCommands.equals("help")) {
                help(options);
            } else if (matchingCommands.equals("create")) {
                create();
            } else {
                File btjProperties = null;
                File projectDir = null;
                if (cmd.hasOption("project")) {
                    try {
                        btjProperties = new File(cmd.getOptionValue("project"))
                                .getAbsoluteFile().getCanonicalFile();
                    } catch (IOException e) {
                        BTJUtils.throwInternal(e);
                    }
                    projectDir = btjProperties.getParentFile();
                } else {
                    btjProperties = new File("btj.properties")
                            .getAbsoluteFile();
                    try {
                        projectDir = new File(".").getAbsoluteFile()
                                .getCanonicalFile();
                    } catch (IOException e) {
                        BTJUtils.throwInternal(e);
                    }
                }

                System.out.println("Using project file " + btjProperties);
                Properties props = loadSettings(btjProperties);
                String type = props.getProperty("project.type", "command-line");
                ProjectType t = BTJUtils.parseProjectType(type);
                if (t == null)
                    throw new BuildException("_Unknown project type: " + type);

                BasicBuild bb = createBuild(t);
                System.out.println("Build " + bb);

                bb.setProjectDir(projectDir);
                bb.setBTJDir(btjDir);
                bb.loadSettings(props);
                if (matchingCommands.equals("package"))
                    bb.build();
                else if (matchingCommands.equals("run"))
                    bb.run_();
                else if (matchingCommands.equals("profile"))
                    bb.profile();
                else if (matchingCommands.equals("clean"))
                    bb.clean();
                else if (matchingCommands.equals("eclipse"))
                    bb.eclipse();
                else
                    throw new InternalError("Unknown command");
            }
        }
    }

    private BasicBuild createBuild(ProjectType t) {
        BasicBuild bb;
        switch (t) {
        case WAR:
            bb = new WebAppBuild();
            break;
        case COMMAND_LINE:
            bb = new CommandLineAppBuild();
            break;
        case SERVICE:
            bb = new ServiceBuild();
            break;
        case LIBRARY:
            bb = new LibraryBuild();
            break;
        default:
            throw new InternalError("Unknown build type");
        }
        return bb;
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
                        + "  help - shows help\r\n"
                        + "You can enter the first few letters for the "
                        + "command name, just enough to find the right one.");
    }

    private void create() throws BuildException {
        boolean c = true;
        String projectName = BTJUtils
                .inputText("Enter name for the new project");
        if (projectName == null)
            c = false;

        if (c) {
            projectName = projectName.trim();
            if (projectName.isEmpty())
                throw new BuildException("Project name is empty");

            if (!Character.isJavaIdentifierStart(projectName.charAt(0)))
                throw new BuildException(
                        "Project name should be a valid Java identifier");

            for (int i = 1; i < projectName.length(); i++) {
                if (!Character.isJavaIdentifierPart(projectName.charAt(i)))
                    throw new BuildException(
                            "Project name should be a valid Java identifier");
            }
        }

        String t = null;
        if (c) {
            t = BTJUtils.inputText("Enter the project type "
                    + "(command-line, service, library or web)");
            if (t == null)
                c = false;
        }

        ProjectType type = null;
        if (c) {
            type = BTJUtils.parseProjectType(t);
            if (type == null)
                throw new BuildException("Unknown project type: " + t);
        }

        if (c) {
            BasicBuild bb = createBuild(type);
            bb.setProjectName(projectName);
            bb.setProjectDir(new File(projectName).getAbsoluteFile());
            bb.create();
        }
    }

    private Properties loadSettings(File btjProperties) throws BuildException {
        Properties p = new Properties();
        InputStream is;
        try {
            is = new FileInputStream(btjProperties);
            p.load(is);
        } catch (IOException e) {
            BTJUtils.throwBuild(e);
        }
        return p;
    }

    private Main() {
    }
}
