BTJ
===
BTJ is a simple build tool for Java programs.

Currently only Windows is supported.

Expected directory structure:
    src - Java sources. All files must be encoded using UTF-8.
    btj.properties - build settings
        project.type=command-line|service|library|war - type of the project. 
            The default value is "command-line". 
            command-line - command line based application
            service - Windows service
            library - a library
            war - JEE web application
        project.name=<name of the project>
        jdk=<path to the JDK> - defines the JDK that should be used
        jars=<list of paths to the .jar files> - libraries for this program.
            See also ivy.xml for Ivy dependencies.
        top.package=<Java package name> - full name of the top package where
            all classes reside. The class "Main" in this package should
            have the "public static void main(String[])" (only for command line
            based projects).
        version=<program version> - current program version
    (optional) resources/ - resources (images, .properties, etc.) that will
        be stored in the .jar file
    (optional) install/ - the contents of this directory will be distributed 
        along the program.
    (optional) po/ - directory with message translations. Every file with the
        .po extension is a GNU gettext translation of the messages used in the
        program. The name of the file is used as the name of Java locale.
        Example: de.po is used to create Messages_de.properties
        The file i18n.properties will be automatically created and placed in the
        same package as the main class. See gettext-commons 
        (http://code.google.com/p/gettext-commons/) for more details. You can
        use the following code in your program to translate a message:
            I18n i18n = I18nFactory.getI18n(Main.class);
            System.out.println(i18n.tr("Hello, world!"));
        Build your project once and copy the file "\build\po\keys.pot" to the
        "po" directory to create a new translation.
    web/ - this directory corresponds to the contents of the .war file and is
        only used for the projects of the type "war".
    
    build/ - temporary directory for the build (created automatically by BTJ)
    build/classes/ - the .class files will be stored here
    build/target/ - the target application is stored here
    build/target/<project name>.exe - 64 bit executable
    build/target/<project name>.ini - WinRun4j settings for the 64 bit 
        executable
    build/target/<project name>32.exe - 32 bit executable
    build/target/<project name>32.ini - WinRun4j settings for the 32 bit 
        executable
    build/<project name>.zip - zipped distribution
    
The file "Version.properties" will be created in the same package as the program
main class and will contain the only property "version" with the value from
"btj.properties".

Please run "btj help" for the list of supported commands and options.
