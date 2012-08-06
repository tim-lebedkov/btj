btj
===
btj is a simple build tool for Java programs.

Currently only command line programs (for example, btj itself) 
and only on Windows are supported.

Expected directory structure:
    src - Java sources
    btj.properties - build settings
        project.name=<name of the project>
        jdk=<path to the JDK> - defines the JDK that should be used
        jars=<list of paths to the .jar files> - libraries for this program
        main.class=<name of the class with main(String[])>
        version=<program version> - current program version
    install/ - the contents of this directory will be distributed along the 
        program.
    build/ - temporary directory for the build
    build/classes/ - the .class files will be stored here
    build/target/ - the target application is stored here
    build/<project name>.zip - zipped distribution
    
The file "Version.properties" will be created in the same package as the program
main class and will contain the only property "version" with the value from
"btj.properties".