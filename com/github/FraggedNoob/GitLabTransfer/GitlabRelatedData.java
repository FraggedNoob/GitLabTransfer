/**
 * GitLabRelatedData 
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

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;

import org.gitlab.api.GitlabAPI;
import org.gitlab.api.models.GitlabIssue;
import org.gitlab.api.models.GitlabMilestone;
import org.gitlab.api.models.GitlabNote;
import org.gitlab.api.models.GitlabProject;
import org.gitlab.api.models.GitlabUser;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;


/**
 * An instance of all data related to a GitLab project's issues,
 * milestones, and notes.  It supports direct queries to GitLab
 * via the v3 API and JSON serialization to files.  It also supports
 * creating or updating project data.
 * 
 * @author FraggedNoob (github.com)
 *
 */
public class GitlabRelatedData {

	/**
	 * The host URL
	 */
	String hostUrl = "";
	
	/**
	 * The project name
	 */
	String projectName = "";

	/**
	 * The API token to use to pull with
	 */
	private String apiToken = "";

	/**
	 * A comparator to order GitlabMilestone by IID.
	 */
	class MilestoneOrderByIID implements Comparator<GitlabMilestone> {

		@Override
		public int compare(GitlabMilestone o1, GitlabMilestone o2) {
			return Integer.compare(o1.getIid(), o2.getIid());
		}
	}

	/**
	 * A comparator to order GitlabIssue by IID.
	 */
	class IssueOrderByIID implements Comparator<GitlabIssue> {

		@Override
		public int compare(GitlabIssue o1, GitlabIssue o2) {
			return Integer.compare(o1.getIid(), o2.getIid());
		}
	}

	/**
	 * A comparator to order GitlabNote by ID.
	 */
	class NoteOrderByID implements Comparator<GitlabNote> {

		@Override
		public int compare(GitlabNote o1, GitlabNote o2) {
			return Integer.compare(o1.getId(), o2.getId());
		}
	}

	/**
	 * The project (source or destination)
	 */
	GitlabProject project = new GitlabProject();

	/**
	 * All users in the GitLab repo
	 */
	List<GitlabUser> users = new ArrayList<GitlabUser>();

	/**
	 * A sorted set of project milestones, sorted by IID.
	 */
	SortedSet<GitlabMilestone> milestones = new TreeSet<GitlabMilestone>(
			new MilestoneOrderByIID());

	/**
	 * A map of updated project milestones, by IID.
	 * key = the (old and new) IID of a project milestone
	 * value = the new (in destination) milestone ID
	 */
	Map<Integer, Integer> newMilestoneIDs = new HashMap<Integer, Integer>();

	/**
	 * The project issues, sorted by issue ID (iid). iid is unique to the
	 * scope of the project, while issue id is globally unique.
	 */
	SortedSet<GitlabIssue> issues = new TreeSet<GitlabIssue>(new IssueOrderByIID());

	/**
	 * A map of updated project issues, by IID.
	 * key = the (old and new) IID of a project issue
	 * value = the new (in destination) issue ID
	 */
	Map<Integer, Integer> newIssueIDs = new HashMap<Integer, Integer>();

	/**
	 * The notes for each issue, in a map. key = the issue IID value = a sorted
	 * set of notes by note ID(assuming that the note ID always increases in
	 * time order)
	 */
	Map<Integer, SortedSet<GitlabNote>> issueNotes = new TreeMap<Integer, SortedSet<GitlabNote>>();

	/**
	 * The API instance
	 */
	private GitlabAPI api;

	/**
	 * Constructor
	 * @param hostUrl
	 * @param projectName
	 * @param apiToken
	 */
	public GitlabRelatedData(String hostUrl, String projectName, String apiToken) {
		super();
		this.hostUrl = hostUrl;
		this.projectName = projectName;
		this.apiToken = apiToken;
	}

	/**
	 * @param apiToken the apiToken to update
	 */
	public void updateApiToken(String apiToken) {
		this.apiToken = apiToken;
	}
	
	/**
	 * Create the api instance
	 * @return T=good to go
	 */
	public boolean createApi() {

		try {
			api = GitlabAPI.connect(hostUrl, apiToken);
			api.ignoreCertificateErrors(true);
		} catch (Exception e) {
			System.out.println("Connect failed.");
			e.printStackTrace();
			return false;
		}
		
		return true;
	}

	/**
	 * Peer into a repo and get every single user.
	 * @return
	 */
	public boolean getAllUsers() {

		if (!createApi()) {
			return false;
		}

		try {
			users = api.getUsers();
			listAllUsers();
		} catch (Exception e) {
			System.out.println("No users retrieved.");
			e.printStackTrace();
			return false;
		}		

		return true;
	}
	
	/**
	 * Write the current list of users to the console.
	 */
	public void listAllUsers() {
		if (!users.isEmpty()) {
			System.out.println("Users:");
			for (GitlabUser u : users) {
				System.out.printf("ID=%d, Name=%s\n", u.getId(), u.getName());
			}
		}
	}

	/**
	 * Return a list all projects in the GitLab instance, or null, if error.
	 */
	public List<GitlabProject> getProjectList() {

		if (!createApi()) {
			return null;
		}

		List<GitlabProject> projs = new ArrayList<GitlabProject>();
		try {
			projs = api.getProjects();
		} catch (Exception e) {
			System.out.println("Connection error, no projects found.");
			e.printStackTrace();
			return null;
		}

		return projs;
	}

	/**
	 * List all projects in the GitLab instance, by name
	 * @return T if successful
	 */
	public boolean listAllProjects() {

		List<GitlabProject> projs = getProjectList();

		if (projs == null) {
			return false;
		}		
		
		if (projs.size() > 0) {
			for (GitlabProject p : projs) {
				System.out.printf("Found Project: %s, ID=%d.\n",
						p.getName(), p.getId());
			}
		}
		else {
			System.out.println("Zero projects found.");
		}

		return true;
	}

	/**
	 * Obtain all the project information.
	 * @return T if successful
	 */
	public boolean getProject() {

		List<GitlabProject> projs = getProjectList();

		if (projs == null) {
			return false;
		}

		Boolean projFound = false;
		Integer foundID = -1;
		
		for (GitlabProject p : projs) {
			if (p.getName().matches(projectName)) {
					project = p;
				projFound = true;
				foundID = p.getId();
				break;
			}
		}
		if (!projFound) {
			System.out.println("Can't find Project.");
			return false;
		} else {
			System.out.printf("Found Project: %s, ID=%d.\n",
					projectName, foundID);
		}

		return true;
	}

	/**
	 * Obtains the milestones for the project.
	 * 
	 * @return F indicates a problem, beware T may mean there are no milestones.
	 */
	public boolean getProjMilestones() {

		if (!createApi()) {
			return false;
		}

		milestones.clear();
		List<GitlabMilestone> milestoneList;

		try {
			milestoneList = api.getMilestones(project);
		} catch (IOException e) {
			System.out.println("Error getting project milestones list.");
			e.printStackTrace();
			return false;
		}

		/*
		 * Uniquely sort the list of milestones by IID
		 */
		try {
			milestones.addAll(milestoneList);
		}
		catch (Exception e) {
			System.out.println("Error parsing project milestones list - bad data.");
			e.printStackTrace();
			return false;			
		}

		if (milestones.size() == 0) {
			System.out.println("Query successful, no milestones in Project!");
			return true;
		}

		return true;
	}
	
	/**
	 * List the known milestones to console.
	 */
	public void listAllMilestones() {
		if (!milestones.isEmpty()) {
			System.out.println("Project Milestones:");
			for (GitlabMilestone m : milestones) {
				System.out.printf("IID=%d, Title=%s\n", m.getIid(),
						m.getTitle());
			}
		}
	}
	
	/**
	 * Put all the milestones into the project.  Note, will skip
	 * milestones with the same IID.
	 * @return T=successfully set milestones, 
	 * F=API failure during attempt
	 */
	public boolean putAllMilestones() {

		if (!createApi()) {
			return false;
		}
		
		// The current list of milestones in gitlab, if any
		List<GitlabMilestone> currmslist = new ArrayList<GitlabMilestone>();
		try {
			currmslist = api.getMilestones(project);
		} catch (IOException e) {
		}
		
		// sort it by IID
		Collections.sort(currmslist,new MilestoneOrderByIID());
		
		// add, or skip, each milestone we have
		for (GitlabMilestone m : milestones) {
			Integer IID = m.getIid();
			
			int currmslistind = -1;
			Integer newID = null;
			if (!currmslist.isEmpty()) {
				currmslistind = Collections.binarySearch(currmslist, m, new MilestoneOrderByIID() );
				newID = currmslist.get(currmslistind).getId();
			}
			
			if (currmslistind >= 0) {
				// the milestone IID exists
				newMilestoneIDs.put(IID, newID); // store the IID vs. new ID mapping
				System.out.printf("Skipping existing project milestone: %s (IID=%d, ID=%d), in project %s\n",
							m.getTitle(),IID, newID, project.getName());
			}
			else {
				// the milestone IID doesn't exist, so add it
				try {
					GitlabMilestone current = 
							api.createMilestone(project.getId(), m);
					System.out.printf("Put project milestone: %s (IID=%d), into project %s\n",
							m.getTitle(),IID,project.getName());
					newMilestoneIDs.put(IID, current.getId()); // store the IID vs. new ID mapping
				} catch (IOException e) {
					System.out.printf("Error while putting project milestone: %s (IID=%d), into project %s\n",
							m.getTitle(),IID,project.getName());
					e.printStackTrace();
					return false;
				}
			}
		}
		
		System.out.println("Final Milestone IID-to-newID mapping:");
		for (Integer IID : newMilestoneIDs.keySet()) {
			System.out.printf("IID: %d\t = ID: %d \n",IID, newMilestoneIDs.get(IID));
		}
		
		return true;
	}

	/**
	 * Obtains the issues for the project.
	 * 
	 * @return F indicates a problem, beware T may mean there are no issues.
	 */
	public boolean getProjIssues() {

		if (!createApi()) {
			return false;
		}

		issues.clear();
		List<GitlabIssue> issueList;

		try {
			issueList = api.getIssues(project);
		} catch (IOException e) {
			System.out.println("Error getting project issues list.");
			e.printStackTrace();
			return false;
		}

		/*
		 * Uniquely sort the list of issues by IID
		 */
		try {
			issues.addAll(issueList);
		}
		catch (Exception e) {
			System.out.println("Error parsing project issues list - bad data.");
			e.printStackTrace();
			return false;			
		}

		if (issues.size() == 0) {
			System.out.println("Query successful, no issues in Project!");
			return true;
		}

		return true;
	}
	
	/**
	 * List all known issues to the console
	 */
	public void listAllIssues() {
		if (!issues.isEmpty()) {
			System.out.println("Project Issues:");
			for (GitlabIssue i : issues) {
				System.out.printf("IID=%d, Title=%s\n", i.getIid(),
						i.getTitle());
			}
		}
	}
	

	
	/**
	 * Put all the issues into the project. Note, will skip issues with the same
	 * IID.
	 * 
	 * @param assigneeID
	 *            - the assignee ID for all issues
	 * @return T=successfully set issues, F=API failure during attempt
	 */
	public boolean putAllIssues(Integer assigneeID) {

		if (!createApi()) {
			return false;
		}

		// Get the current project's issues, in case we have duplicate IIDs
		SortedSet<GitlabIssue> currIssues = new TreeSet<GitlabIssue>(
				new IssueOrderByIID());
		try {
			List<GitlabIssue> ci = api.getIssues(project);
			currIssues.addAll(ci);
		} catch (IOException e1) {
			System.out.println("Error while getting current project issues.");
			e1.printStackTrace();
			return true;
		}

		// add, or skip, each issue we have
		for (GitlabIssue i : issues) {
			
			// Note, some issues might not have a milestone:
			GitlabMilestone ims = i.getMilestone();
			Integer mileIID = 0;
			Integer newMileID = 0;
			if (ims != null) {
				mileIID = i.getMilestone().getIid();
				newMileID = newMilestoneIDs.get(mileIID);
			}
			
			// Check if this issue IID exists in this project
			boolean skipIssue = false;
			int currIssueID = 0;
			for (GitlabIssue ci : currIssues) {
				if (ci.getIid() == i.getIid()) {
					currIssueID = ci.getId();
					skipIssue = true;
					break;
				}
			}

			// skip current issues with matching IIDs
			if (skipIssue) {
				// the issue IID exists
				newIssueIDs.put(i.getIid(), currIssueID); // store the IID vs.
															// new ID mapping
				System.out
						.printf("Skipping existing project issue: %s (IID=%d, ID=%d), in project %s\n",
								i.getTitle(), i.getIid(), currIssueID, project.getName());
			} else {
				// the issue IID doesn't exist, so add it
				try {
					
					GitlabIssue current = api.createIssue(project.getId(),
							assigneeID, newMileID, createIssueLabelString(i),
							i.getDescription(), i.getTitle());
					System.out
							.printf("Put project issue: %s (IID=%d), into project %s\n",
									i.getTitle(), i.getIid(), project.getName());
					newIssueIDs.put(i.getIid(), current.getId()); // store the
																	// IID vs.
																	// new ID
																	// mapping

					// edit the issue and set open/closed
					GitlabIssue.Action act = GitlabIssue.Action.LEAVE;
					if (i.getState().contains("lose")) { // closed?
						act = GitlabIssue.Action.CLOSE;
					}

					api.editIssue(project.getId(), current.getId(), assigneeID,
							newMileID, createIssueLabelString(i), i.getDescription(),
							i.getTitle(), act);
				} catch (IOException e) {
					System.out
							.printf("Error while putting project issue: %s (IID=%d), into project %s\n",
									i.getTitle(), i.getIid(), project.getName());
					e.printStackTrace();
					return false;
				}
			}
		}

		System.out.println("Final Issue IID-to-newID mapping:");
		for (Integer IID : newIssueIDs.keySet()) {
			System.out.printf("IID: %d\t = ID: %d \n", IID,
					newIssueIDs.get(IID));
		}

		return true;
	}

	/**
	 * Creates a comma-separated list of issue labels
	 * @param i GitlabIssue to pull labels from
	 */
	protected String createIssueLabelString(GitlabIssue i) {
		String l[] = i.getLabels();
		if (l.length == 0) {
			return new String();
		}
		else if (l.length == 1) {
			return l[0];
		}
		else {
			StringBuilder sb = new StringBuilder(l[0]);
			for (String s:l) {
				sb.append(", ");
				sb.append(s);
			}
			return sb.toString();
		}
	}

	/**
	 * Obtains the notes for a project issue.
	 * @param i The Gitlab Issue to pull from
	 * @param notes A SortedSet of notes by IID.  Empty means error or no
	 * notes in the issue, see return value.
	 * 
	 * @return F indicates a problem, beware T may mean there are no notes.
	 */
	public boolean getIssueNotes(final GitlabIssue i, SortedSet<GitlabNote> notes) {

		if (!createApi()) {
			return false;
		}

		notes.clear();
		List<GitlabNote> noteList;

		try {
			noteList = api.getNotes(i);
		} catch (IOException e) {
			System.out.printf("Error getting notes from issue IID=%d.\n", i.getIid());
			e.printStackTrace();
			return false;
		}

		/*
		 * Uniquely sort the list of issues by IID
		 */
		try {
			notes.addAll(noteList);
		}
		catch (Exception e) {
			System.out.println("Error parsing notes - bad data.");
			e.printStackTrace();
			return false;			
		}

		return true;
	}
	
	/**
	 * Obtains all notes for all project issues.
	 * @return F indicates a problem, T success (beware T may mean no notes exist)
	 */
	public boolean getAllProjNotes() {
		
		SortedSet<GitlabNote> sortedNotes = new TreeSet<GitlabNote>(new NoteOrderByID());
		
		for (GitlabIssue i : issues) {
			if (!getIssueNotes(i, sortedNotes)) {
				return false;
			}
			if (sortedNotes.size() > 0) {
				issueNotes.put(i.getIid(), sortedNotes);
				sortedNotes = new TreeSet<GitlabNote>(new NoteOrderByID());
			}
		}
				
		return true;
	}
	
	/**
	 * List all the issue notes in this instance.
	 */
	public void listAllIssuesNotes() {
		if (!issues.isEmpty()) {
			// issues and notes
			System.out.println("Project Issues:");
			for (GitlabIssue i : issues) {
				System.out.printf("IID=%d, Title=%s\n", i.getIid(),
						i.getTitle());
				SortedSet<GitlabNote> noteset = issueNotes.get(i.getIid());
				if ((noteset == null ) || (noteset.isEmpty())) {
					System.out.printf("\t (no notes)\n");
				}
				else {
					System.out.printf("\t [%d notes]\n",noteset.size());
				}
			}
		}
	}
	
	
	/**
	 * Put all of the notes onto all of the issues 
	 * @return F=fail, T=success
	 */
	public boolean putAllIssuesNotes() {

		if (!createApi()) {
			return false;
		}
		
		for (Integer issueIID : issueNotes.keySet()) {
			GitlabIssue issue;
			Integer issueID = newIssueIDs.get(issueIID);
			try {
				issue = api.getIssue(project.getId(), issueID);
			} catch (IOException e) {
				System.out.printf("Error getting destination issue, Issue IID=%d, ID=%d.\n", 
						issueIID,issueID);
				e.printStackTrace();
				return false;
			}
			putIssueNotes(api, issue, issueNotes.get(issueIID));
		}
		
		return true;
	}
	
	
	/**
	 * Put in all the notes for a single issue
	 * Warning: Will simply add to any existing issue - duplicates will occur 
	 * if run more than once.
	 * @param api The api instance to use
	 * @param iss Issue from the current project
	 * @param ns A sorted set of notes from the source project
	 * @return F=fail, T=success
	 */
	public boolean putIssueNotes(GitlabAPI api, GitlabIssue iss, SortedSet<GitlabNote> ns) {
		
		// TODO putIssueNotes() blindly adds notes, instead of skipping existing matches.
		
		int count = 1;
		for (GitlabNote n : ns) {
			try {
				api.createNote(iss, n.getBody());
				System.out.printf("Setting issue note %d/%d, Issue IID=%d.\n", 
						count, ns.size(), iss.getIid());
				count++;
			} catch (IOException e) {
				System.out.printf("Error setting issue note, Issue IID=%d (ID=%d).\n", 
						iss.getIid(), iss.getId());
				e.printStackTrace();
				return false;
			}
		}
		
		return true;
	}
	
	/**
	 * The workhorse function to pull everything we this class can pull
	 * from the repo.
	 * @return T=success, F=a failure
	 */
	public boolean pullAllFromRepo() {

		// Get all the project data
		if (!getProject()) {
			return false;
		}

		// Get the set of owners and IDs
		if (!getAllUsers()) {
			return false;
		}

		// Get the project milestones
		if (!getProjMilestones()) {
			System.out.println("Problem retrieving project milestones.");
			return false;
		} else {
			listAllMilestones();
		}

		// Get the project issues
		if (!getProjIssues()) {
			System.out.println("Problem retrieving project issues.");
			return false;
		} else {
			listAllIssues();
		}
		
		// Get all project issue notes
		if (!getAllProjNotes()) {
			System.out.println("Problem retrieving project issue notes.");
			return false;
		} else {
			listAllIssuesNotes();
		}
		
		return true;
	}
	
	/**
	 * Creates a File for writing the JSON data.  The ".json" extension is added.
	 * @param filepath the path/filename prefix
	 * @param suffix the data suffix (without extension, e.g. ".json")
	 * @return A File ready for writing, or null.
	 */
	protected static File createFileForWriting(String filepath, String dataSuffix) {
		
		File w = new File(filepath+dataSuffix+".json");
		if (!w.exists()) {
			try {
				w.createNewFile();
			} catch (IOException e) {
				System.out.printf("Error, can't create file:%s for writing.\n",filepath);
				e.printStackTrace();
				return null;
			}
		}
		
		if (!w.canWrite()) {
			System.out.printf("Error, file:%s not writable.\n",filepath);
			return null;
		}
		
		return w;
	}
	
	/**
	 * Creates a File for reading the JSON data.  The ".json" extension is added.
	 * @param filepath the path/filename prefix
	 * @param suffix the data suffix (without extension, e.g. ".json")
	 * @return A File ready for reading, or null.
	 */
	protected static File createFileForReading(String filepath, String dataSuffix) {
		
		File w = new File(filepath+dataSuffix+".json");
		if (!w.exists()) {
			return null;
		}
		
		if (!w.canRead()) {
			System.out.printf("Error, file:%s not readable.\n",filepath);
			return null;
		}
		
		return w;
	}
	
	/**
	 * Saves all the data to JSON files except for the hostUrl, projectName,
	 * and apiToken.
	 * @param filepath The filepath prefix
	 * @return T=success, F=fail
	 */
	public boolean saveAllData(String filepath) {
		
		ObjectMapper mapper = new ObjectMapper();
		
		// Always the output pretty.
		mapper.enable(SerializationFeature.INDENT_OUTPUT);
		
		File w;
		String wname = "(none)";
		
		w = createFileForWriting(filepath, "_project");
		if (w == null) {
			return false;
		}
		
		// Project 
		try {
			mapper.writeValue(w, project);
			wname = w.getCanonicalPath();
		} catch (IOException e) {
			System.out.printf("Error writing to project to %s.\n",wname);
			e.printStackTrace();
			return false;
		}
		
		System.out.printf("Wrote project (%s) to %s.\n", projectName, wname);
		
		// Users
		w = createFileForWriting(filepath, "_users");
		if (w == null) {
			return false;
		}
		try {
			mapper.writeValue(w, users);
			wname = w.getCanonicalPath();
		} catch (IOException e) {
			System.out.printf("Error writing to users to %s.\n",wname);
			e.printStackTrace();
			return false;
		}
		
		System.out.printf("Wrote users to %s.\n", wname);
		
		// Milestones
		w = createFileForWriting(filepath, "_milestones");
		if (w == null) {
			return false;
		}
		try {
			// Set to List
			mapper.writeValue(w, new ArrayList<GitlabMilestone>(milestones));
			wname = w.getCanonicalPath();
		} catch (IOException e) {
			System.out.printf("Error writing to milestones to %s.\n",wname);
			e.printStackTrace();
			return false;
		}
		
		System.out.printf("Wrote milestones to %s.\n", wname);
		
		// Issues
		w = createFileForWriting(filepath, "_issues");
		if (w == null) {
			return false;
		}
		try {
			// Set to List
			mapper.writeValue(w, new ArrayList<GitlabIssue>(issues));
			wname = w.getCanonicalPath();
		} catch (IOException e) {
			System.out.printf("Error writing to issues to %s.\n",wname);
			e.printStackTrace();
			return false;
		}
		
		System.out.printf("Wrote issues to %s.\n", wname);
		
		// Issue Notes
		w = createFileForWriting(filepath, "_inotes");
		if (w == null) {
			return false;
		}
		try {
			// Map of Integer,Set to Integer,List
			Map<Integer, List<GitlabNote>> mapList = new TreeMap<Integer, List<GitlabNote>>();
			for (Integer k : issueNotes.keySet()) {
				mapList.put(k, new ArrayList<GitlabNote>(issueNotes.get(k)));
			}
			
			mapper.writeValue(w, mapList);
			wname = w.getCanonicalPath();
		} catch (IOException e) {
			System.out.printf("Error writing to issues to %s.\n",wname);
			e.printStackTrace();
			return false;
		}
		
		System.out.printf("Wrote issue notes to %s.\n", wname);
		
		System.out.printf("Writing to files complete.\n");
		
		return true;
	}
	
	/**
	 * Reads all the data from JSON files except for the hostUrl, projectName,
	 * and apiToken.
	 * @param filepath The filepath prefix
	 * @return T=success, F=fail
	 */
	public boolean readAllData(String filepath) {
		
		ObjectMapper mapper = new ObjectMapper();
				
		File w;
		String wname = "(none)";
		
		w = createFileForReading(filepath, "_project");
		if (w == null) {
			return false;
		}
		
		// Project 
		try {
			this.project = mapper.readValue(w, GitlabProject.class);
			wname = w.getCanonicalPath();
		} catch (IOException e) {
			System.out.printf("Error reading project from %s.\n",wname);
			e.printStackTrace();
			return false;
		}
		
		System.out.printf("Read project (%s) from %s.\n", this.project.getName(), wname);
		
		// Users
		w = createFileForReading(filepath, "_users");
		if (w == null) {
			return false;
		}
		try {
			this.users = mapper.readValue(w, new TypeReference<List<GitlabUser>>() { });
			wname = w.getCanonicalPath();
		} catch (IOException e) {
			System.out.printf("Error reading from users to %s.\n",wname);
			e.printStackTrace();
			return false;
		}
		
		System.out.printf("Read %d users from %s.\n", this.users.size(), wname);
		
		// Milestones
		w = createFileForReading(filepath, "_milestones");
		if (w == null) {
			return false;
		}
		try {
			// Read in as a List first, matches output
			List<GitlabMilestone> ml = 
					mapper.readValue(w, new TypeReference<List<GitlabMilestone>>() { });
			this.milestones.addAll(ml);
			// Read directly as Set:
			//this.milestones.clear();
			//JavaType type = mapper.getTypeFactory().constructCollectionType(Set.class, GitlabMilestone.class);
			//this.milestones = mapper.readValue(w,  type);
			wname = w.getCanonicalPath();
		} catch (IOException e) {
			System.out.printf("Error reading milestones from %s.\n",wname);
			e.printStackTrace();
			return false;
		} catch (Exception e) {
			System.out.printf("Error reading milestones from %s - incorrect data.\n",wname);
			e.printStackTrace();
			return false;
		}
		
		System.out.printf("Read %d milestones from %s.\n", this.milestones.size(), wname);
		
		// Issues
		w = createFileForReading(filepath, "_issues");
		if (w == null) {
			return false;
		}
		try {
			// Read in as a List first, matches output
			List<GitlabIssue> mi = 
					mapper.readValue(w,  new TypeReference<List<GitlabIssue>>() { });
			this.issues.addAll(mi);
			wname = w.getCanonicalPath();
		} catch (IOException e) {
			System.out.printf("Error writing to issues to %s.\n",wname);
			e.printStackTrace();
			return false;
		} catch (Exception e) {
			System.out.printf("Error reading issues from %s - incorrect data.\n",wname);
			e.printStackTrace();
			return false;
		}
		
		System.out.printf("Read %d issues to %s.\n", this.issues.size(), wname);
		
		// Issue Notes
		w = createFileForReading(filepath, "_inotes");
		if (w == null) {
			return false;
		}
		try {
			this.issueNotes.clear();
			// Read in as a Map of Integer vs. List<GitlabNote>
			JavaType valtype = mapper.getTypeFactory().constructCollectionType(List.class, GitlabNote.class);
			JavaType keytype = mapper.getTypeFactory().constructType(Integer.class);
			JavaType maptype = mapper.getTypeFactory()
					.constructMapType(Map.class, keytype, valtype);
			Map<Integer, List<GitlabNote>> mapList =
					mapper.readValue(w, maptype);
			
			for (Integer k : mapList.keySet()) {
				SortedSet<GitlabNote> s = new TreeSet<GitlabNote>(new NoteOrderByID());
				s.addAll(mapList.get(k));
				issueNotes.put(k, s);
			}
			
			wname = w.getCanonicalPath();
		} catch (IOException e) {
			System.out.printf("Error reading issue notes from %s.\n",wname);
			e.printStackTrace();
			return false;
		} catch (Exception e) {
			System.out.printf("Error reading issues notes from %s - incorrect data.\n",wname);
			e.printStackTrace();
			return false;
		}
		
		System.out.printf("Read %d sets of issue notes to %s.\n", issueNotes.size(), wname);
		
		System.out.printf("Reading from files complete.\n");
		
		return true;
	}
	
}
