The program is a simple Java program.
Directory structure:
    src - Java sources
    btj.properties - build settings
        project.name=<name of the project>
        jdk=<path to the JDK> - defines the JDK that should be used
        jars=<list of paths to the .jar files> - libraries for this program
        main.class=<name of the class with main(String[])>
    install/ - the contents of this directory will be distributed along the 
        program.
    build/ - temporary directory for the build
    build/classes/ - the .class files will be stored here
    build/target/ - the target application is stored here
