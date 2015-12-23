/**
 * GitLabTransfer
 *
 * Transfers GitLab project data from one GitLab instance to another
 * using the Java GitlabAPI.
 * 
 * Transfer occurs via JSON file serialization because both may not be visible 
 * on the same network.
 * 
 * 
 *   Copyright 2015 FraggedNoob on GitHub.com
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 */

package com.github.FraggedNoob.GitLabTransfer;

/**
 * The top-level driver to transfer a single GitLab project data set
 * from one GitLab instance to another using the Java GitlabAPI.
 * Transfer occurs via JSON file serialization because both may not be visible 
 * on the same network.  This also gives the user an option to modify the exported
 * JSON data before importing.
 * 
 * <br>
 * The following assumptions are made:
 * <ul>
 * <li>The same instance may, or may not, be used so all data to be transferred
 * are held in JSON files.</li>
 * <li>There is a source project and an empty destination project, and the user
 * of this code can properly access both (hint: test via the pull/display commands).</li>
 * <li>The source and destination host URLs, project names, and user API tokens
 * are different.</li>
 * <li>Due to limitations in the Gitlab API, capturing the original metadata of 
 * every data item wasn't attempted (e.g. no original creation date preserved or noted
 * in the destination project).  So the destination project can't look exactly like
 * the source project. </li>
 * <li>There is some error-checking but it isn't exhaustively tested.  If you have
 * a problem halfway through, then you'll probabaly need to delete your Gitlab destination 
 * project and re-create it for another import attempt (and probabaly some bug fixing). </li>
 * </ul>
 * <br>
 * Important notes on data handling:
 * <ul>
 * <li>No user mapping occurs.  (It isn't difficult but we didn't need it.  The
 * query commands are useful for getting the user data from both systems to make
 * your own mapping.) </li>
 * <li>A single user ID is used to import the data into the destination project.</li>
 * <li>Project namespaces are ignored (but can be set up by the user when they
 * create the destination project)</li>
 * <li>Access level is ignored (assumed set up for the destination import)</li>
 * <li>Project access level is ignored (assumed set up for the destination import)</li>
 * <li>Project labels are not directly imported (but issues' labels are, so you
 * could end up with a subset)</li>
 * <li>Milestones are imported.  Some attempt is made to skip matching (IID) 
 * milestones in the destination project.</li>
 * <li>Closed milestones are transferred but not closed.</li>
 * <li>Issues are imported.  Destination issues are mapped to destination 
 * milestones.  Some attempt is made to skip matching (IID) issues in the 
 * destination project.</li>
 * <li>Closed issues are transferred and they are closed after import.</li>
 * <li>Issue notes are transferred, but no attempt is made to skip matches.
 * Don't run the note transfer more than once.</li>
 * </ul>
 * 
 * @author FraggedNoob (github.com)
 * 
 */
public class GitLabTransfer {

	/**
	 * The source URL to pull from (server only)
	 */
	static String srcHostUrl = "https://mysourceserver.somewhere";

	/**
	 * The API token to use with the source server
	 */
	static private String srcApiToken = "SECRET!";
	
	/**
	 * The source project name
	 * Use the sprojlist command to get the projects you can see
	 * from the source server.
	 */
	static String srcProjectName = "Source_Project";
	
	/**
	 * The destination URL to pull from and push to (server only)
	 */
	static String dstHostUrl = "https://mydestserver.somewhere";

	/**
	 * The API token to use with the destination server
	 */
	static private String dstApiToken = "ANOTHERSECRET!";
	
	/**
	 * The destination project name
	 * Use the dprojlist command to get the projects you can see
	 * from the destination server.
	 */
	static String dstProjectName = "Dest_Project";
	
	/**
	 * The destination user ID.  Use the duserlist command to query
	 * the destination server and select the correct ID.
	 */
	static private Integer dstUserId = 42;
	
	/**
	 * The file prefix & path to save the gathered source data to.
	 * Note, an _<data>.json suffix will be appended to the filename 
	 * for each set of data.
	 */
	static String srcJsonFilePrefix = "sourceProjectData";
	
	/**
	 * The file prefix & path to read the gathered destination data from.
	 * Note, an _<data>.json suffix will be appended to the filename 
	 * for each set of data.
	 */
	static String dstJsonFilePrefix = "sourceProjectData";
	
	/**
	 * Main entry point.  Arguments are a sequence of commands. 
	 * @param args A whitespace-delimited sequence of commands, they are:
	 * <br>
	 * Commands for interrogating the repo(s), can be used standalone:
	 * <ul>
	 * <li> suserlist = Pull (and display) the source repo users. </li>
	 * <li> duserlist = Pull (and display) the destination repo users. </li>
	 * <li> sprojlist = Pull (and display) the source projects. </li>
	 * <li> dprojlist = Pull (and display) the destination projects. </li>
	 * </ul>
	 * 
	 * Command for pulling all project data from the source repo:
	 * <ul>
	 * <li> pullsave  = Pull from the source and save to files. </li>
	 * </ul>
	 * 
	 * Commands for data manipulation (used after a pullsave command):
	 * <ul>
	 * <li> readsrc   = Read data from source files (must be run before any
	 * of these other commands) </li>
	 * <li> setdestproj  = Set the destination project with the destination data </li>
	 * <li> putmile   = Put the milestones into the destination project (after setdestproj) </li>
	 * <li> putissues = Put the issues into the destination project (after putmile) </li>
	 * <li> putissuenotes = Put in all the notes for every issue into the destination project (after putissues) </li>
	 * </ul>
	 * 
	 * Example: <br>
	 *          (on 1st machine) GitLabTransfer pullsave <br>
	 *          (on 2nd machine) GitLabTransfer readsrc setdestproj putmile putissues putissuenotes
	 */
	public static void main(String[] args) {

		GitlabRelatedData source = new GitlabRelatedData(srcHostUrl, srcProjectName, srcApiToken);
		GitlabRelatedData dst = new GitlabRelatedData(dstHostUrl, dstProjectName, dstApiToken);
				
		if (args.length == 0) {
			System.out.println("No commands given, exiting.");
		}
		
		System.out.printf("Invoked with: ");
		for (String cmd : args) {
			System.out.printf("%s ",cmd);
		}
		System.out.printf("\n");
		
		for (String cmd : args) {
			
			boolean go = false;
			
			switch (cmd) {
			case ("pullsave"):
				System.out.println("Pulling from source repo and saving to files.");
				go = source.pullAllFromRepo();
				if (go) {
					go = source.saveAllData(srcJsonFilePrefix);
				}
				break;
			case ("suserlist"):
				System.out.println("Source users are:");
				go = source.getAllUsers();
				break;
			case ("duserlist"):
				System.out.println("Destination users are:");
				go = dst.getAllUsers();
				break;
			case ("sprojlist"):
				System.out.println("Source projects are:");
				go = source.listAllProjects();
				break;
			case ("dprojlist"):
				System.out.println("Destination projects are:");
				go = dst.listAllProjects();
				break;
			case ("readsrc"):
				System.out.println("Data read from files:");
				go = dst.readAllData(dstJsonFilePrefix);
				if (go) {
					dst.listAllUsers();
					dst.listAllMilestones();
					dst.listAllIssuesNotes();
				}
				break;
			case ("setdestproj"):
				System.out.println("Getting/using destination project:");
				go = dst.getProject();
				break;
			case ("putmile"):
				System.out.println("Putting milestones into the destination project:");
				go = dst.putAllMilestones();
				break;
			case ("putissues"):
				System.out.println("Putting issues into the destination project:");
				go = dst.putAllIssues(dstUserId);
				break;
			case ("putissuenotes"):
				System.out.println("Putting issue notes into the destination project:");
				go = dst.putAllIssuesNotes();
				break;
			default:
				System.out.printf("Unknown argument: %s.",cmd);
				go = false;
			}
			if (!go) {
				System.out.println("Quitting on failure.");
				return;
			}
		}

	} // main

	
}
