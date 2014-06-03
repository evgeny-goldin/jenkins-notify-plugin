jenkins-notify-plugin
=====================

Jenkins plugin sending POST request as a post-build action with configurable JSON payload.

By default payload submitted contains the details of the build (build number, build result, job and log URLs), 
URLs of artifacts generated, Git branch and commit SHA.
 
But being a configurable Groovy template it can contain any Jenkins, job or build details you may think of!

![Post-build action - configurable JSON payload is submitted as POST request](https://raw.githubusercontent.com/cloudnative/jenkins-notify-plugin/master/screenshots/jenkins-notify-plugin.png "Post-build action - configurable JSON payload is submitted as POST request")

JSON payload is rendered as a Groovy template, having the following variables in scope:
 
* **`jenkins`** - instance of [`jenkins.model.Jenkins`](http://javadoc.jenkins-ci.org/jenkins/model/Jenkins.html)
* **`build`** - instance of [`hudson.model.AbstractBuild`](http://javadoc.jenkins-ci.org/hudson/model/AbstractBuild.html)
* **`env`** - instance of [`hudson.EnvVars`](http://javadoc.jenkins-ci.org/hudson/EnvVars.html) corresponding to the current build process

Here's a [RequestBin](http://requestb.in/) of submitting a default payload:

![RequestBin for the default JSON payload](https://raw.githubusercontent.com/cloudnative/jenkins-notify-plugin/master/screenshots/request-bin.png "RequestBin for the default JSON payload")

In addition, **`json( Object )`** helper function is available, rendering any `Object` provided as JSON.

For example:

    {
      "items":       ${ json( jenkins.allItems ) },
      "computers":   ${ json( jenkins.computers.collect{ it.displayName }) },
      "moduleRoots": ${ json( build.moduleRoots )},
      "artifacts":   ${ json( build.artifacts )},
      "env":         ${ json( env ) },
      "properties":  ${ json( [ system: System.properties.keySet(), env: env.keySet() ]) }
    }
