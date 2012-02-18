/* Copyright 2004-2005 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.codehaus.griffon.launcher;

import java.io.File;
import java.net.URLClassLoader;
import java.util.List;
import java.util.Locale;
import java.util.Date;
import java.text.DateFormat;

/**
 * Helper class that allows a client to bootstrap the Griffon build system
 * in its own class loader. It basically uses reflection to handle the
 * entry points to the build system: {@link griffon.util.BuildSettings}
 * and {@link org.codehaus.griffon.cli.GriffonScriptRunner}. This
 * ensures class loader isolation for Griffon.
 *
 * @author Peter Ledbrook
 */
public class GriffonLauncher {
    private ClassLoader classLoader;
    private Object settings;

    /**
     * Creates a helper that loads the Griffon build system with the given
     * class loader. Ideally, the class loader should be an instance of
     * {@link RootLoader}.
     * You can try other class loaders, but you may run into problems.
     *
     * @param classLoader The class loader that will be used to load Griffon.
     */
    public GriffonLauncher(ClassLoader classLoader) {
        this(classLoader, null);
    }

    /**
     * Creates a helper that loads the Griffon build system with the given
     * class loader. Ideally, the class loader should be an instance of
     * {@link GriffonRootLoader}.
     * You can try other class loaders, but you may run into problems.
     *
     * @param classLoader The class loader that will be used to load Griffon.
     * @param griffonHome Location of a local Griffon installation.
     */
    public GriffonLauncher(ClassLoader classLoader, String griffonHome) {
        this(classLoader, griffonHome, null);
    }

    /**
     * Creates a helper that loads the Griffon build system with the given
     * class loader. Ideally, the class loader should be an instance of
     * {@link GriffonRootLoader}.
     * You can try other class loaders, but you may run into problems.
     *
     * @param classLoader The class loader that will be used to load Griffon.
     * @param griffonHome Location of a local Griffon installation.
     * @param baseDir     The path to the Griffon project to launch the command on
     */
    public GriffonLauncher(ClassLoader classLoader, String griffonHome, String baseDir) {
        try {
            this.classLoader = classLoader;
            Class<?> clazz = classLoader.loadClass("griffon.util.BuildSettings");

            // Use the BuildSettings(File griffonHome, File baseDir) constructor.
            File griffonHomeFile = griffonHome == null ? null : new File(griffonHome);
            File baseDirFile = baseDir == null ? null : new File(baseDir);
            settings = clazz.getConstructor(File.class, File.class).newInstance(griffonHomeFile, baseDirFile);

            Class<?> settingsHolder = classLoader.loadClass("griffon.util.BuildSettingsHolder");
            invokeMethod(settingsHolder, "setSettings",
                    new Class[]{clazz},
                    new Object[]{settings});

            // Initialise the root loader for the BuildSettings.
            invokeMethod(settings, "setRootLoader",
                    new Class[]{URLClassLoader.class},
                    new Object[]{classLoader});

            callGriffonSetup();
        } catch (Exception ex) {
            // ex.printStackTrace();
            throw new RuntimeException(ex);
        }
    }

    /**
     * Executes the named Griffon script with no arguments.
     *
     * @param script The name of the script to launch, such as "Compile".
     * @return The value returned by the build system (notionally the
     *         exit code).
     */
    public int launch(String script) {
        return launch(script, null);
    }

    /**
     * Executes the named Griffon script with the given arguments.
     *
     * @param script The name of the script to launch, such as "Compile".
     * @param args   A single string containing the arguments for the
     *               script, each argument separated by whitespace.
     * @return The value returned by the build system (notionally the
     *         exit code).
     */
    public int launch(String script, String args) {
        try {
            debug("Launching "+script+" with args "+args);
            Object scriptRunner = createScriptRunner();
            Object retval = scriptRunner.getClass().
                    getMethod("executeCommand", new Class[]{String.class, String.class}).
                    invoke(scriptRunner,script, args);
            return (Integer) retval;
        } catch (Exception ex) {
            // ex.printStackTrace();
            throw new RuntimeException(ex);
        }
    }

    /**
     * Executes the named Griffon script with the given arguments in the
     * specified environment. Normally the script is run in the default
     * environment for that script.
     *
     * @param script The name of the script to launch, such as "Compile".
     * @param args   A single string containing the arguments for the
     *               script, each argument separated by whitespace.
     * @param env    The name of the environment to run in, e.g. "development"
     *               or "production".
     * @return The value returned by the build system (notionally the
     *         exit code).
     */
    public int launch(String script, String args, String env) {
        try {
            debug("Launching "+script+" with env "+env+" and args "+args);
            // script = getScriptName(script);
            Object scriptRunner = createScriptRunner();
            Object retval = scriptRunner.getClass().
                    getMethod("executeCommand", new Class[]{String.class, String.class, String.class}).
                    invoke(scriptRunner,script, args, env);
            return (Integer) retval;
        } catch (Exception ex) {
            // ex.printStackTrace();
            throw new RuntimeException(ex);
        }
    }

    private String getScriptName(String name) {
        // Handle null and empty strings.
        if (isBlank(name)) return name;

        if (name.indexOf('-') > -1) {
            StringBuilder buf = new StringBuilder();
            String[] tokens = name.split("-");
            for (String token : tokens) {
                if (token == null || token.length() == 0) continue;
                buf.append(capitalize(token));
            }
            return buf.toString();
        }

        return capitalize(name);
    }

    private boolean isBlank(String str) {
        return str == null || str.trim().length() == 0;
    }

    private String capitalize(String str) {
        if (isBlank(str)) return str;
        if (str.length() == 1) return str.toUpperCase();
        return str.substring(0, 1).toUpperCase(Locale.ENGLISH) + str.substring(1);
    }

    public File getGriffonWorkDir() {
        return (File) invokeMethod(settings, "getGriffonWorkDir", new Object[0]);
    }

    public void setGriffonWorkDir(File dir) {
        invokeMethod(settings, "setGriffonWorkDir", new Object[]{dir});
    }

    public File getProjectWorkDir() {
        return (File) invokeMethod(settings, "getProjectWorkDir", new Object[0]);
    }

    public void setProjectWorkDir(File dir) {
        invokeMethod(settings, "setProjectWorkDir", new Object[]{dir});
    }

    public File getClassesDir() {
        return (File) invokeMethod(settings, "getClassesDir", new Object[0]);
    }

    public void setClassesDir(File dir) {
        invokeMethod(settings, "setClassesDir", new Object[]{dir});
    }

    public File getTestClassesDir() {
        return (File) invokeMethod(settings, "getTestClassesDir", new Object[0]);
    }

    public void setTestClassesDir(File dir) {
        invokeMethod(settings, "setTestClassesDir", new Object[]{dir});
    }

    public File getResourcesDir() {
        return (File) invokeMethod(settings, "getResourcesDir", new Object[0]);
    }

    public void setResourcesDir(File dir) {
        invokeMethod(settings, "setResourcesDir", new Object[]{dir});
    }

    public File getProjectPluginsDir() {
        return (File) invokeMethod(settings, "getProjectPluginsDir", new Object[0]);
    }

    public void setProjectPluginsDir(File dir) {
        invokeMethod(settings, "setProjectPluginsDir", new Object[]{dir});
    }

    public File getTestReportsDir() {
        return (File) invokeMethod(settings, "getTestReportsDir", new Object[0]);
    }

    public void setTestReportsDir(File dir) {
        invokeMethod(settings, "setTestReportsDir", new Object[]{dir});
    }

    @SuppressWarnings("rawtypes")
    public List getCompileDependencies() {
        return (List) invokeMethod(settings, "getCompileDependencies", new Object[0]);
    }

    @SuppressWarnings("rawtypes")
    public void setCompileDependencies(List dependencies) {
        invokeMethod(settings, "setCompileDependencies", new Class[]{List.class}, new Object[]{dependencies});
    }

    public void setDependenciesExternallyConfigured(boolean b) {
        invokeMethod(settings, "setDependenciesExternallyConfigured", new Class[] { boolean.class }, new Object[] { b });
    }

    @SuppressWarnings("rawtypes")
    public List getTestDependencies() {
        return (List) invokeMethod(settings, "getTestDependencies", new Object[0]);
    }

    @SuppressWarnings("rawtypes")
    public void setTestDependencies(List dependencies) {
        invokeMethod(settings, "setTestDependencies", new Class[]{List.class}, new Object[]{dependencies});
    }

    @SuppressWarnings("rawtypes")
    public List getRuntimeDependencies() {
        return (List) invokeMethod(settings, "getRuntimeDependencies", new Object[0]);
    }

    @SuppressWarnings("rawtypes")
    public void setRuntimeDependencies(List dependencies) {
        invokeMethod(settings, "setRuntimeDependencies", new Class[]{List.class}, new Object[]{dependencies});
    }

    @SuppressWarnings("rawtypes")
    public List getBuildDependencies() {
        return (List) invokeMethod(settings, "getBuildDependencies", new Object[0]);
    }

    @SuppressWarnings("rawtypes")
    public void setBuildDependencies(List dependencies) {
        invokeMethod(settings, "setBuildDependencies", new Class[]{List.class}, new Object[]{dependencies});
    }

    private Object createScriptRunner() throws Exception {
        return classLoader.loadClass("org.codehaus.griffon.cli.GriffonScriptRunner").
                getDeclaredConstructor(new Class[]{settings.getClass()}).
                newInstance(settings);
    }

    private void callGriffonSetup() throws Exception {
        Class<?> griffonSetupClass = classLoader.loadClass("org.codehaus.griffon.cli.GriffonSetup");
        invokeMethod(griffonSetupClass, "run", new Class[0], new Object[0]);
    }

    /**
     * Invokes the named method on a target object using reflection.
     * The method signature is determined by the classes of each argument.
     *
     * @param target The object to call the method on.
     * @param name   The name of the method to call.
     * @param args   The arguments to pass to the method (may be an empty array).
     * @return The value returned by the method.
     */
    private Object invokeMethod(Object target, String name, Object[] args) {
        Class<?>[] argTypes = new Class[args.length];
        for (int i = 0; i < args.length; i++) {
            argTypes[i] = args[i].getClass();
        }

        return invokeMethod(target, name, argTypes, args);
    }

    /**
     * Invokes the named method on a target class using reflection.
     * The method signature is determined by the classes of each argument.
     *
     * @param target The class to call the method on.
     * @param name   The name of the method to call.
     * @param args   The arguments to pass to the method (may be an empty array).
     * @return The value returned by the method.
     */
    private Object invokeMethod(Class target, String name, Object[] args) {
        Class<?>[] argTypes = new Class[args.length];
        for (int i = 0; i < args.length; i++) {
            argTypes[i] = args[i].getClass();
        }

        return invokeMethod(target, name, argTypes, args);
    }

    /**
     * Invokes the named method on a target class using reflection.
     * The method signature is determined by given array of classes.
     *
     * @param target   The class to call the method on.
     * @param name     The name of the method to call.
     * @param argTypes The argument types declared by the method we
     *                 want to invoke (may be an empty array for a method that takes
     *                 no arguments).
     * @param args     The arguments to pass to the method (may be an empty
     *                 array).
     * @return The value returned by the method.
     */
    private Object invokeMethod(Class target, String name, Class<?>[] argTypes, Object[] args) {
        try {
            return target.getMethod(name, argTypes).invoke(target, args);
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }
    
    /**
     * Invokes the named method on a target object using reflection.
     * The method signature is determined by given array of classes.
     *
     * @param target   The object to call the method on.
     * @param name     The name of the method to call.
     * @param argTypes The argument types declared by the method we
     *                 want to invoke (may be an empty array for a method that takes
     *                 no arguments).
     * @param args     The arguments to pass to the method (may be an empty
     *                 array).
     * @return The value returned by the method.
     */
    private Object invokeMethod(Object target, String name, Class<?>[] argTypes, Object[] args) {
        try {
            return target.getClass().getMethod(name, argTypes).invoke(target, args);
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    public void debug(String msg) {
        if (isDebugEnabled()) {
            Date now = new Date();
            System.out.println("[" +
                    getDateString(now)
                    + " " +
                    getTimeString(now)
                    + "] " + msg);
        }
    }

    public static final String KEY_CLI_VERBOSE = "griffon.cli.verbose";

    public boolean isDebugEnabled() {
        if (System.getProperty(KEY_CLI_VERBOSE) != null) return Boolean.getBoolean(KEY_CLI_VERBOSE);
        return false;
    }

    private String getDateString(Date self) {
        return DateFormat.getDateInstance(DateFormat.SHORT).format(self);
    }

    private String getTimeString(Date self) {
        return DateFormat.getTimeInstance(DateFormat.MEDIUM).format(self);
    }
}
