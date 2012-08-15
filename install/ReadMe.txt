BTJ
===
BTJ is a simple build tool for Java programs.

Currently only command line programs (for example, BTJ itself) 
and only on Windows are supported.

Expected directory structure:
    src - Java sources
    btj.properties - build settings
        project.name=<name of the project>
        jdk=<path to the JDK> - defines the JDK that should be used
        jars=<list of paths to the .jar files> - libraries for this program.
            See also ivy.xml for Ivy dependencies.
        main.class=<name of the class with main(String[])>
        version=<program version> - current program version
    (optional) install/ - the contents of this directory will be distributed 
        along the program.
    build/ - temporary directory for the build
    build/classes/ - the .class files will be stored here
    build/target/ - the target application is stored here
    build/target/<project name>.exe - 64 bit executable
    build/target/<project name>.ini - WinRun4j settings for the 64 bit 
        executable
    build/target/<project name>32.exe - 32 bit executable
    build/target/<project name>32.ini - WinRun4j settings for the 32 bit 
        executable
    build/<project name>.zip - zipped distribution
    (optional) resources/ - resource files will be stored in the .jar file
    
The file "Version.properties" will be created in the same package as the program
main class and will contain the only property "version" with the value from
"btj.properties".

Please run "btj help" for the list of supported commands and options.
