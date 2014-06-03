jenkins-notify-plugin
=====================

Jenkins plugin sending POST request as a post-build action with configurable JSON payload. 
By default payload submitted contains the details of the build (build number, build result, job and log URLs), 
URLs of artifacts generated, Git branch and commit SHA but it can contain any Jenkins, job or build details.

![Post-build action - configurable JSON payload is submitted as POST request](https://raw.githubusercontent.com/cloudnative/jenkins-notify-plugin/master/screenshots/jenkins-notify-plugin.png "jenkins-notify-plugin")

JSON payload is rendered as a Groovy template, having the following variables in scope:
 
* **`jenkins`** - instance of [`jenkins.model.Jenkins`](http://javadoc.jenkins-ci.org/jenkins/model/Jenkins.html)
* **`build`** - instance of [`hudson.model.AbstractBuild`](http://javadoc.jenkins-ci.org/hudson/model/AbstractBuild.html)
* **`env`** - instance of [`hudson.EnvVars`](http://javadoc.jenkins-ci.org/hudson/EnvVars.html) corresponding to the current build process

In addition, `json( Object )` helper function is available, rendering Object provided as JSON.

For example:

    {
      "items":       ${ json( jenkins.allItems ) },
      "computers":   ${ json( jenkins.computers.collect{ it.displayName }) },
      "moduleRoots": ${ json( build.moduleRoots )},
      "artifacts":   ${ json( build.artifacts )},
      "env":         ${ json( env ) },
      "properties":  ${ json( [ system: System.properties.keySet(), env: env.keySet() ]) }
    }