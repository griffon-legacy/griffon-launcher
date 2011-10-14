package org.codehaus.griffon.launcher.ant

import org.apache.tools.ant.BuildException
import org.apache.tools.ant.types.Path

/**
 * Test case for {@link GriffonTask}.
 */
public class GriffonTaskTests extends GroovyTestCase {
    void testWithClasspath() {
//        def task = new GriffonTask()
//        task.script = "Compile"
//        task.addClasspath(new Path(task.project))
//
//        task.execute()
    }

    void testNoScript() {
        def task = new GriffonTask()
        task.home = new File(".")

        shouldFail(BuildException) {
            task.execute()
        }
    }

    void testNoHomeAndNoClasspath() {
        def task = new GriffonTask()
        task.script = "Compile"

        shouldFail(BuildException) {
            task.execute()
        }
    }

    void testHomeAndClasspath() {
        def task = new GriffonTask()
        task.script = "Compile"
        task.home = new File(".")
        task.addClasspath(new Path(task.project))

        shouldFail(BuildException) {
            task.execute()
        }
    }

    void testGetCommand() {
        def task = new GriffonTask()
        task.script = "TestApp"
        assertEquals "test-app", task.command

        task.script = "Compile"
        assertEquals "compile", task.command

        task.script = ""
        assertEquals "", task.command

        task.script = null
        assertNull task.command
    }

    void testSetCommand() {
        def task = new GriffonTask()
        task.command = "test-app"
        assertEquals "TestApp", task.script

        task.command = "compile"
        assertEquals "Compile", task.script

        shouldFail(BuildException) {
            task.command = ""
        }
        
        shouldFail(BuildException) {
            task.command = null
        }
    }
}
