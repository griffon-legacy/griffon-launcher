## griffon-launcher 

A thin jar with **no dependencies** for launching Griffon (with an isolated classpath) programatically (e.g from Maven or Gradle) in the same JVM.

    import org.codehaus.griffon.launcher.GriffonLauncher
    import org.codehaus.griffon.launcher.RootLoader

    // Setup the classpath for Griffon
    def classpath = []

    griffonJars.each { path ->
        classpath << new URL(path)
    }

    // Create a root class loader
    def classloader = new RootLoader(classpath)

    def launcher = new GriffonLauncher(classloader, null, "/a/griffon/project")
    launcher.launch("test-app", "integration some.package.*")

This is a port of **grails-launcher** original from Peter Ledbrook and Luke Daley.
