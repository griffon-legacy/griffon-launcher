/*
 * Copyright 2011 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.codehaus.griffon.launcher.ant;

import org.codehaus.griffon.launcher.*;

import java.io.File;
import java.io.FilenameFilter;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.Task;
import org.apache.tools.ant.types.Path;
import org.apache.tools.ant.types.Reference;

/**
 * <p>Ant task for executing Griffon scripts. To use it, first create a
 * task definition for it:
 * <pre>
 *    &lt;path id="griffon.classpath"&gt;
 *       &lt;fileset dir="${griffon.home}/dist"&gt;
 *          &lt;include name="griffon-*.jar"/&gt;
 *       &lt;/fileset&gt;
 *       &lt;fileset dir="${griffon.home}/lib"&gt;
 *          &lt;include name="groovy-all*.jar"/&gt;
 *          &lt;include name="ivy*.jar"/&gt;
 *          &lt;include name="gpars*.jar"/&gt;
 *          &lt;include name="gant_groovy*.jar"/&gt;
 *       &lt;/fileset&gt;
 *    &lt;/path&gt;
 *
 *    &lt;taskdef name="griffon"
 *             classname="griffon.ant.GriffonTask"
 *             classpathref="griffon.classpath"/&gt;
 * </pre>
 * You must have the "griffon-rt", "griffon-cli", "griffon-resources", 
 * "griffon-scripts", "groovy-all", Ivy, and GPars JARs on
 * the <code>taskdef<code>'s classpath, otherwise the task won't load.
 * </p>
 * <p>Once the task is defined, you can use it like this:
 * <pre>
 *   &lt;griffon home="${griffon.home}" script="Clean"/&gt;
 * </pre>
 * The <code>home</code> attribute contains the location of a local
 * Griffon installation, while <code>script</code> is the name of the
 * Griffon script to run. Note that it's the <em>script</em> name not
 * the equivalent command name.
 * </p>
 * <p>If you want to use the Ant task without a Griffon installation,
 * then you can use the <code>classpathref</code> attribute or
 * <code>classpath</code> nested element instead of <code>home</code>.
 * This allows you to control precisely which JARs and versions are
 * used to execute the Griffon scripts. Typically you would use this
 * option in conjunction with something like Ivy.
 * </p>
 */
public class GriffonTask extends Task {

    private File home;
    private String script;
    private String args;
    private String environment;
    private boolean includeRuntimeClasspath = true;
    private Path classpath;

    private Path compileClasspath;
    private Path testClasspath;
    private Path runtimeClasspath;

    @Override
    public void execute() throws BuildException {
        // The "script" must be specified.
        if (script == null) throw new BuildException("'script' must be provided.");

        // Check that one, and only one, of Griffon home and classpath are set.
        if (home == null && classpath == null) {
            throw new BuildException("One of 'home' or 'classpath' must be provided.");
        }

        if (home != null && classpath != null) {
            throw new BuildException("You cannot use both 'home' and 'classpath' with the Griffon task.");
        }

        runGriffon(script, args);
    }

    protected void runGriffon(String targetName, @SuppressWarnings("hiding") String args) {
        // First get the dependencies from the classpath.
        List<URL> urls = new ArrayList<URL>();
        if (classpath != null) {
            urls.addAll(pathsToUrls(classpath));
        }
        else {
            urls.addAll(getRequiredLibsFromHome());
        }

        try {
            URL[] loaderUrls = urls.toArray(new URL[urls.size()]);
            RootLoader rootLoader = new RootLoader(loaderUrls, getClass().getClassLoader());

            GriffonLauncher launcher;
            if (getProject().getBaseDir() != null) {
                launcher = new GriffonLauncher(rootLoader, home == null ? null :
                    home.getCanonicalPath(), getProject().getBaseDir().getCanonicalPath());
            }
            else {
                launcher = new GriffonLauncher(rootLoader, home == null ? null : home.getCanonicalPath());
            }

            int retval;
            if (environment == null) {
                retval = launcher.launch(targetName, args);
            }
            else {
                retval = launcher.launch(targetName, args, environment);
            }

            if (retval != 0) {
                throw new BuildException("Griffon returned non-zero value: " + retval);
            }
        }
        catch (Exception ex) {
            throw new BuildException("Unable to start Griffon: " + ex.getMessage(), ex);
        }
    }

    private List<URL> getRequiredLibsFromHome() {
        List<URL> urls = new ArrayList<URL>();

        try {
            // Make sure Groovy, Gant, Ivy and GPars are on the classpath if we are using "griffonHome".
            File[] files = new File(home, "lib").listFiles(new FilenameFilter() {
                public boolean accept(File dir, String name) {
                    return name.startsWith("gant_") || name.startsWith("groovy-all") ||
                           name.startsWith("ivy") || name.startsWith("gpars");
                }
            });

            for (File file : files) {
                urls.add(file.toURI().toURL());
            }

            // Also make sure the bootstrap JAR is on the classpath.
            files = new File(home, "dist").listFiles(new FilenameFilter() {
                public boolean accept(File dir, String name) {
                    return name.startsWith("griffon-rt") ||
                           name.startsWith("griffon-cli") ||
                           name.startsWith("griffon-scripts") ||
                           name.startsWith("griffon-resources");
                }
            });

            for (File file : files) {
                urls.add(file.toURI().toURL());
            }

            return urls;
        }
        catch (MalformedURLException e) {
            throw new RuntimeException(e);
        }
    }

    private List<URL> pathsToUrls(Path path) {
        if (path == null) return Collections.emptyList();

        List<URL> urls = new ArrayList<URL>(path.size());
        for (String filePath : path.list()) {
            try { urls.add(new File(filePath).toURI().toURL()); }
            catch (MalformedURLException ex) { throw new RuntimeException(ex); }
        }

        return urls;
    }

    public String getCommand() {
        return script == null ? null : NameUtils.toCommandName(script);
    }

    public void setCommand(String command) {
        if (command == null) {
            throw new BuildException("'command' cannot be null");
        } else if (command.trim().length() == 0) {
            throw new BuildException("'command' cannot be a blank string");
        }
        
        script = NameUtils.toScriptName(command);
    }

    public File getHome() {
        return home;
    }

    public void setHome(File home) {
        this.home = home;
    }

    public String getScript() {
        return script;
    }

    public void setScript(String script) {
        this.script = script;
    }

    public String getArgs() {
        return args;
    }

    public void setArgs(String args) {
        this.args = args;
    }

    public String getEnvironment() {
        return environment;
    }

    public void setEnvironment(String environment) {
        this.environment = environment;
    }

    public boolean isIncludeRuntimeClasspath() {
        return includeRuntimeClasspath;
    }

    public void setIncludeRuntimeClasspath(boolean includeRuntimeClasspath) {
        this.includeRuntimeClasspath = includeRuntimeClasspath;
    }

    public Path getClasspath() {
        return classpath;
    }

    public void addClasspath(@SuppressWarnings("hiding") Path classpath) {
        this.classpath = classpath;
    }

    public void setClasspathRef(Reference ref) {
        addClasspath((Path) ref.getReferencedObject());
    }

    @Deprecated
    public Path getCompileClasspath() {
        return compileClasspath;
    }

    @Deprecated
    public void addCompileClasspath(@SuppressWarnings("hiding") Path compileClasspath) {
        this.compileClasspath = compileClasspath;
    }

    @Deprecated
    public Path getTestClasspath() {
        return testClasspath;
    }

    @Deprecated
    public void addTestClasspath(@SuppressWarnings("hiding") Path testClasspath) {
        this.testClasspath = testClasspath;
    }

    @Deprecated
    public Path getRuntimeClasspath() {
        return runtimeClasspath;
    }

    @Deprecated
    public void addRuntimeClasspath(@SuppressWarnings("hiding") Path runtimeClasspath) {
        this.runtimeClasspath = runtimeClasspath;
    }
}
