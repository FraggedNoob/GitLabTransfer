# GitLabTransfer
Transfers a single [Gitlab](https://gitlab.org) project between servers (via v3 API).

GitLabTransfer is a Java-based command-line driver to transfer a single GitLab 
project data set from one GitLab instance to another using the Java GitlabAPI.

Transfer occurs via JSON file serialization because both servers may not be visible 
on the same network.  This also gives the user an option to modify the exported
JSON data before importing.

## Status and Limitations
Seeing little else out on the internet this was a hack.  It did what I wanted 
it to, but that's about it.  Testing was limited.

There are limitations with the GitLab API, so this won't give you a 
perfect project reproduction.  However, it will bring over milestones, issues,
and issue notes - which was enough for the original needs.  It currently does not
perform user mapping between systems.  (It shouldn't be difficult to add.)

Please look at the GitLabTransfer javadoc for details.

## Warnings
Git users beware!  This hack version hardcodes secret server info - so don't commit
without sanitizing your code first!  

## Dependencies
GitLabTransfer uses the [timols/java-gitlab-api](https://github.com/timols/java-gitlab-api).

Other dependencies you'll need:
* [Jackson Core](https://github.com/FasterXML/jackson-core.git)
* [Jackson Databind](https://github.com/FasterXML/jackson-databind)
* [Jackson Annotations](https://github.com/FasterXML/jackson-annotations)
* Apache Commons 2.4
* JUnit 4.12

Tested in Java 1.7 and 1.8.

## Usage
The quick way to transfer a project... 

(Assume we're starting on a machine that can see the source GitLab server OR that your
client can see both machines.)
1. Get the dependencies & build 'em
2. In GitLabTransfer: set the source server URL
3. In GitLabTransfer: set your secret GitLab API tokens for the source server
4. In GitLabTransfer: set the project name for the source server
5. Compile your .java as you wish... (.class or put into a .jar) 
6. Test that you can access the source server via "GitLabTransfer sprojlist" (to see available projects)
7. Pull down the source data via "GitLabTransfer pullsave" (now you have .json files)

(If required, move all files to a machine that can see the destination server.)
 
1. In GitLabTransfer: set the destination server URL
2. In GitLabTransfer: set your secret GitLab API tokens for the destination server
3. Create an empty project on your destination GitLab server
4. In GitLabTransfer: set the project name for the destination server
5. Compile your .java as you wish... (.class or put into a .jar)
6. Test that you can access the destination server via "GitLabTransfer dprojlist (to see available projects)
7. Find your user ID on the destination server via "GitLabTransfer duserlist"
8. In GitLabTransfer: set dstUserId and recompile
9. Push up all the data into the new project on the destination server via "GitLabTransfer readsrc setdestproj putmile putissues putissuenotes"
