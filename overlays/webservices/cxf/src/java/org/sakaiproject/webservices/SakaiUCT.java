/**
 * Copyright (c) 2016 University of Cape Town
 *
 * Licensed under the Educational Community License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *             http://opensource.org/licenses/ecl2
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.sakaiproject.webservices;

import javax.jws.WebMethod;
import javax.jws.WebParam;
import javax.jws.WebService;
import javax.jws.soap.SOAPBinding;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.FormParam;
import javax.ws.rs.QueryParam;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

//import org.sakaiproject.assignment.api.Assignment;
//import org.sakaiproject.assignment.api.AssignmentContent;
//import org.sakaiproject.assignment.api.AssignmentContentEdit;
//import org.sakaiproject.assignment.api.AssignmentEdit;
//import org.sakaiproject.assignment.api.AssignmentService;
//import org.sakaiproject.assignment.api.AssignmentSubmission;
//import org.sakaiproject.assignment.api.AssignmentSubmissionEdit;
import org.sakaiproject.api.common.edu.person.SakaiPerson;
import org.sakaiproject.api.common.edu.person.SakaiPersonManager;
import org.sakaiproject.api.common.type.Type;
import org.sakaiproject.authz.api.AuthzGroup;
import org.sakaiproject.authz.api.GroupProvider;
import org.sakaiproject.authz.api.Member;
import org.sakaiproject.authz.api.Role;
import org.sakaiproject.coursemanagement.api.CourseManagementService;
import org.sakaiproject.coursemanagement.api.exception.IdNotFoundException;
import org.sakaiproject.event.api.UsageSession;
import org.sakaiproject.exception.InUseException;
import org.sakaiproject.exception.PermissionException;
import org.sakaiproject.service.gradebook.shared.AssignmentHasIllegalPointsException;
import org.sakaiproject.service.gradebook.shared.ConflictingAssignmentNameException;
import org.sakaiproject.service.gradebook.shared.ConflictingExternalIdException;
import org.sakaiproject.service.gradebook.shared.GradebookNotFoundException;
import org.sakaiproject.site.api.Site;
import org.sakaiproject.site.api.SitePage;
import org.sakaiproject.site.api.ToolConfiguration; 
import org.sakaiproject.time.api.Time;
import org.sakaiproject.tool.api.Session;
import org.sakaiproject.tool.api.Tool;
import org.sakaiproject.tool.cover.ToolManager;
import org.sakaiproject.user.api.User;
import org.sakaiproject.user.api.UserNotDefinedException;
import org.sakaiproject.util.StringUtil;
import org.sakaiproject.util.Xml;

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.lang.StringUtils;

import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import java.io.*;
import java.lang.management.RuntimeMXBean;
import java.lang.management.ManagementFactory;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

/**
 * SakaiUCT.java
 * <p/>
 * UCT-specific webservices for Sakai
 */

@WebService
@SOAPBinding(style = SOAPBinding.Style.RPC, use = SOAPBinding.Use.LITERAL)
public class SakaiUCT extends AbstractWebService {

    private static final Logger LOG = LoggerFactory.getLogger(SakaiUCT.class);

    private static final String LTI_TOOL_ID = "sakai.basiclti";

    /** The maximum trial number to get an uniq assignment title in gradebook */
    private static final int MAXIMUM_ATTEMPTS_FOR_UNIQUENESS = 100;
    private static final String NEW_ASSIGNMENT_ADD_TO_GRADEBOOK = "new_assignment_add_to_gradebook";

    protected GroupProvider groupProvider;
    protected CourseManagementService courseManagementService;
    protected SakaiPersonManager sakaiPersonManager;

    private static Base64 base64 = new Base64();

    // ####### Service injection ##########

    @WebMethod(exclude = true)
    public void setGroupProvider(GroupProvider groupProvider) {
        this.groupProvider = groupProvider;
    }

    @WebMethod(exclude = true)
    public void setCourseManagementService(CourseManagementService courseManagementService) {
        this.courseManagementService = courseManagementService;
    }

    @WebMethod(exclude = true)
    public void setSakaiPersonManager(SakaiPersonManager sakaiPersonManager) {
        this.sakaiPersonManager = sakaiPersonManager;
    }

    // ######## Used by mrtg active users graphing ########

    /**
     * Get the number of session active within the last elapsed seconds
     * @param sessionId 	valid admin session id
     * @param elapsed		maximum idle time for users to count
     * @return
     * @
     */
    @WebMethod
    @Path("/getActiveUserCount")
    @Produces("text/plain")
    @GET
    public Integer getActiveUserCount(
            @WebParam(name = "sessionId", partName = "sessionId") @QueryParam("sessionId") String sessionId,
            @WebParam(name = "elapsed", partName = "elapsed") @QueryParam("elapsed") int elapsed) {

        Session session = establishSession(sessionId);
        if (!securityService.isSuperUser()) {
            LOG.warn("NonSuperUser trying to collect configuration: " + session.getUserId());
            return new Integer(-1);
        }

        return new Integer(sessionManager.getActiveUserCount(elapsed));
    }

    // ######## These methods replace the .jsp files used with Axis ########

    /**
     * Return JVM uptime
     * @return Uptime in seconds
     * @
     */
    @WebMethod
    @Path("/uptime")
    @Produces("text/plain")
    @GET
    public long uptime() {
	RuntimeMXBean mx = ManagementFactory.getRuntimeMXBean();
        return (mx.getUptime() / 1000);
    }

    /**
     * Return free memory
     * @return Free memory in bytes
     * @
     */
    @WebMethod
    @Path("/freeMemory")
    @Produces("text/plain")
    @GET
    public long freeMemory() {
	Runtime rt = Runtime.getRuntime();
	return rt.freeMemory();
    }

    /**
     * Return total memory
     * @return Total memory in bytes
     * @
     */
    @WebMethod
    @Path("/totalMemory")
    @Produces("text/plain")
    @GET
    public long totalMemory() {
	Runtime rt = Runtime.getRuntime();
	return rt.totalMemory();
    }

    /**
     * Return system encoding
     * @return Encoding properties (comma-separated)
     * @
     */
    @WebMethod
    @Path("/encoding")
    @Produces("text/plain")
    @GET
    public String encoding() {
	return  "file.encoding=" + System.getProperty("file.encoding") + "," +
		"sun.jnu.encoding=" + System.getProperty("sun.jnu.encoding") + "," +
		"sun.io.unicode.encoding=" + System.getProperty("sun.io.unicode.encoding") + "," +
		"file.encoding.pkg=" + System.getProperty("file.encoding.pkg");
    }

    // ######## Migrated from JiraUtils.jws and MatterhornPortalUtils.jws ########

    /**
     * Add tool to site
     * @return success or failure
     * @
     */
    @WebMethod
    @Path("/addToolToSite")
    @Produces("text/plain")
    @GET
    public String addToolToSite(
	@WebParam(name = "sessionid", partName = "sessionid") @QueryParam("sessionid") String sessionid,
	@WebParam(name = "siteid", partName = "siteid") @QueryParam("siteid") String siteid,
	@WebParam(name = "toolid", partName = "toolid") @QueryParam("toolid") String toolid) {
		
		try {
			// Establish the session. SiteService will enforce security for changes.
			Session s = establishSession(sessionid);
			
			// get info for this tool
			Tool newTool = toolManager.getTool(toolid);
			String tooltitle = newTool.getTitle();
			
			Site siteEdit = siteService.getSite(siteid);

			if (siteEdit.getToolForCommonId(toolid) == null) {
				// Add a page and tool to the page
				SitePage sitePageEdit = siteEdit.addPage();
				sitePageEdit.setTitle(tooltitle);

				ToolConfiguration tool = sitePageEdit.addTool();
				
				tool.setTool(toolid, newTool);
				tool.setTitle(tooltitle);

				siteService.save(siteEdit);
			}
			
			return "success";
		}
		catch (Exception e) {
			LOG.warn("Error adding tool " + toolid + " to site " + siteid + ": " + e);
		}
		
		return "failure";
    }

    /**
     * Remove a tool and its enclosing page from a site
     * @return removed, failure (with reason)
     * @
     */
    @WebMethod
    @Path("/removeTool")
    @Produces("text/plain")
    @GET
        public String removeTool(
        @WebParam(name = "sessionid", partName = "sessionid") @QueryParam("sessionid") String sessionid,
        @WebParam(name = "siteid", partName = "siteid") @QueryParam("siteid") String siteid,
        @WebParam(name = "toolreg", partName = "toolreg") @QueryParam("toolreg") String toolreg) {

                LOG.info("Removing tool " + toolreg + " from site " + siteid);

                try {
                        // Establish the session. SiteService will enforce security for changes.
                        Session s = establishSession(sessionid);

                        Site siteEdit = siteService.getSite(siteid);
                        Collection<ToolConfiguration> tools = siteEdit.getTools(toolreg);

                        if (tools.isEmpty())
                                return "failure: no tool found in site with registration " + toolreg;

                        // First matching tool
                        ToolConfiguration tool = tools.iterator().next();

			siteEdit.removePage(tool.getContainingPage());
                        siteService.save(siteEdit);

                        return "removed";

                } catch (Exception e) {
                        LOG.warn("Error updating tool in site " + siteid + ": " + e);
                }

                return "failure";
	}


    /**
     * Update an external tool in a site (change tool registration and properties)
     * @return updated, failure (with reason)
     * @
     */
    @WebMethod
    @Path("/updateExternalTool")
    @Produces("text/plain")
    @GET
        public String updateExternalTool(
        @WebParam(name = "sessionid", partName = "sessionid") @QueryParam("sessionid") String sessionid,
	@WebParam(name = "siteid", partName = "siteid") @QueryParam("siteid") String siteid,
        @WebParam(name = "oldtoolreg", partName = "oldtoolreg") @QueryParam("oldtoolreg") String oldtoolreg,
        @WebParam(name = "newtoolreg", partName = "newtoolreg") @QueryParam("newtoolreg") String newtoolreg,
        @WebParam(name = "ltilaunchurl", partName = "ltilaunchurl") @QueryParam("tooltitle") String ltiLaunchUrl,
        @WebParam(name = "lticustomparams", partName = "lticustomparams") @QueryParam("tooltitle") String ltiCustomParams) {

                LOG.info("Updating tool " + oldtoolreg + " in site " + siteid + " to tool registration " + newtoolreg);

		try {
			// Establish the session. SiteService will enforce security for changes.
			Session s = establishSession(sessionid);
			
			Site siteEdit = siteService.getSite(siteid);
			Collection<ToolConfiguration> ltiTools = siteEdit.getTools(oldtoolreg);

			if (ltiTools.isEmpty())
				return "failure: no tool found in site with registration " + oldtoolreg;

			// First matching tool
			ToolConfiguration tool = ltiTools.iterator().next();

			Tool newTool = toolManager.getTool(newtoolreg);
			tool.setTool(newtoolreg, newTool);

			Properties propsedit = tool.getPlacementConfig();
			propsedit.setProperty("imsti.launch", ltiLaunchUrl);
			propsedit.setProperty("imsti.custom", ltiCustomParams);

			// Workaround for Organize Tools setting this to true when a tool is removed
			siteEdit.setCustomPageOrdered(false);

			siteService.save(siteEdit);

			return "updated";

		} catch (Exception e) {
			LOG.warn("Error updating tool in site " + siteid + ": " + e);
		}
		
		return "failure";
	}

    /**
     * Add External (LTI) tool to site
     * @return added, updated, failure
     * @
     */
    @WebMethod
    @Path("/addExternalToolToSite")
    @Produces("text/plain")
    @GET
	public String addExternalToolToSite(
	@WebParam(name = "sessionid", partName = "sessionid") @QueryParam("sessionid") String sessionid,
	@WebParam(name = "siteid", partName = "siteid") @QueryParam("siteid") String siteid,
	@WebParam(name = "tooltitle", partName = "tooltitle") @QueryParam("tooltitle") String tooltitle,
	@WebParam(name = "ltilaunchurl", partName = "ltilaunchurl") @QueryParam("tooltitle") String ltiLaunchUrl,
	@WebParam(name = "lticustomparams", partName = "lticustomparams") @QueryParam("tooltitle") String ltiCustomParams) {

		LOG.info("Adding external tool to " + siteid + " with title: " + tooltitle);
	
		try {
			// Establish the session. SiteService will enforce security for changes.
			Session s = establishSession(sessionid);
			
			// get info for this tool
			Tool newTool = toolManager.getTool(LTI_TOOL_ID);
			
			Site siteEdit = siteService.getSite(siteid);

			Collection<ToolConfiguration> ltiTools = siteEdit.getTools(LTI_TOOL_ID);
			boolean addTool = true;

			ToolConfiguration tool = null;

			for (ToolConfiguration tc : ltiTools) {
				if (tc.getTitle().equals(tooltitle)) {
					addTool = false;
					tool = tc;
				}
			}

			if (addTool) {
				// Add a page and tool to the page
				LOG.info("Adding new page to site " + siteid + " for tool title: " + tooltitle);

				SitePage sitePageEdit = siteEdit.addPage();
				sitePageEdit.setTitle(tooltitle);
				sitePageEdit.setTitleCustom(true);

				tool = sitePageEdit.addTool();
				tool.setTool(LTI_TOOL_ID, newTool);
				tool.setTitle(tooltitle);

   				Properties propsedit = tool.getPlacementConfig();

				propsedit.setProperty("imsti.launch", ltiLaunchUrl);
				propsedit.setProperty("imsti.custom", ltiCustomParams);
				propsedit.setProperty("imsti.pagetitle", tooltitle);
				propsedit.setProperty("imsti.tooltitle", tooltitle);
				propsedit.setProperty("imsti.releaseemail", "on");
				propsedit.setProperty("imsti.releasename", "on");

                                siteService.save(siteEdit);

				return "added";

			} else {
				LOG.info("Updating existing page in site " + siteid + " for tool title: " + tooltitle);

   				Properties propsedit = tool.getPlacementConfig();

				propsedit.setProperty("imsti.launch", ltiLaunchUrl);
				propsedit.setProperty("imsti.custom", ltiCustomParams);
				propsedit.setProperty("imsti.pagetitle", tooltitle);
				propsedit.setProperty("imsti.tooltitle", tooltitle);
				propsedit.setProperty("imsti.releaseemail", "on");
				propsedit.setProperty("imsti.releasename", "on");

                                siteService.save(siteEdit);

				return "updated";
			}
			
		}

		catch (Exception e) {
			LOG.warn("Error adding tool " + LTI_TOOL_ID + " to site " + siteid + ": " + e);
		}
		
		return "failure";
	}

    /**
     * Get Provider ID for site
     * @return provider ID
     * @
     */
    @WebMethod
    @Path("/getProviderIdForSite")
    @Produces("text/plain")
    @GET
	public String getProviderIdForSite(
	@WebParam(name = "sessionid", partName = "sessionid") @QueryParam("sessionid") String sessionid,
	@WebParam(name = "siteid", partName = "siteid") @QueryParam("siteid") String siteid) {
		
		try {
			// Establish the session
			Session s = establishSession(sessionid);
			
			String realmid = "/site/" + siteid;
			
			AuthzGroup realm = authzGroupService.getAuthzGroup(realmid);
			
			if (realm == null) {
				throw new RuntimeException("Error getting provider ID");
			}

			return realm.getProviderGroupId();
		}
		catch (Exception e) {
			LOG.warn("Error getting provider ID for site " + siteid + ": " + e);
			throw new RuntimeException("Error getting provider ID");
		}				
	}

	// Adapted from RealmsAction.readRealmForm()
	
	private String verifyProviderIds(String sessionid, String provider)  {	
		Session s = establishSession(sessionid);
		return _verifyProviderIds(provider);
	}

	private String _verifyProviderIds(String provider) {
		Set<String> notfound = new HashSet<String>();
		
		// verify provider information
	    if (StringUtil.trimToNull(provider) != null)
	    {
	            String[] providers = groupProvider.unpackId(provider);
	            for (int i = 0; i<providers.length; i++)
	            {
	                    try
	                    {
	                            courseManagementService.getSection(providers[i]);
	                    }
	                    catch (IdNotFoundException e)
	                    {
	                    	notfound.add(providers[i]);
	                    }
	            }
	    }
	    
	    return notfound.isEmpty() ? "success" : join(notfound, "+");	
	}

    /**
     * Set Provider ID for site
     * @return success, failure or list of invalid provider IDs separated by +
     * @
     */
    @WebMethod
    @Path("/setProviderIdForSite")
    @Produces("text/plain")
    @GET
	public String setProviderIdForSite(
	@WebParam(name = "sessionid", partName = "sessionid") @QueryParam("sessionid") String sessionid,
	@WebParam(name = "siteid", partName = "siteid") @QueryParam("siteid") String siteid,
	@WebParam(name = "providerid", partName = "providerid") @QueryParam("siteid") String providerId) {
		
		try {
			// Establish the session. SiteService will enforce security for changes.
			Session s = establishSession(sessionid);

			String realmid = "/site/" + siteid;
			
			AuthzGroup realm = authzGroupService.getAuthzGroup(realmid);
			
			if (realm == null) {
				return "failure";
			}

			String verify = _verifyProviderIds(providerId);
			
			if (!"success".equals(verify)) {
				// One or more provider IDs was invalid (not found in CM)
				return verify;
			}
			
			// Update provider IDs
			realm.setProviderGroupId(providerId);
			
			 // commit the change
			authzGroupService.save(realm);

			return "success";
		} 
		catch (Exception e) {
			LOG.warn("Error setting providers for site " + siteid + ": " + e);
		}
		
		return "failure";
	}
	
	// http://snippets.dzone.com/posts/show/91
	private String join(Set<String> s, String delimiter) {
	    if (s.isEmpty()) return "";
	    Iterator<String> iter = s.iterator();
	    StringBuffer buffer = new StringBuffer(iter.next());
	    while (iter.hasNext()) buffer.append(delimiter).append(iter.next());
	    return buffer.toString();
	}

    /**
     * Get membership list for site
     * @return Site membership as an XML document
     * @
     */
    @WebMethod
    @Path("/getSiteMembers")
    @Produces("text/plain")
    @GET
	public String getSiteMembers(
	@WebParam(name = "sessionid", partName = "sessionid") @QueryParam("sessionid") String sessionid,
	@WebParam(name = "siteid", partName = "siteid") @QueryParam("siteid") String siteid) {
		
		Document dom = Xml.createDocument();
		Node all = dom.createElement("Members");
		try {

                        // establish the session
                        Session s = establishSession(sessionid);

			if ("!admin".equalsIgnoreCase(siteid) && !securityService.isSuperUser(s.getUserId())) {
				LOG.warn("WS getSiteMembers(): Permission denied. Restricted to super users.");
				throw new RuntimeException("WS getSiteMembers(): Permission denied. Restricted to super users.");
			}

                        AuthzGroup gp = authzGroupService.getAuthzGroup("/site/" + siteid);

			if (gp == null) {
				return "failure";
			}

                        dom.appendChild(all);

                        Set members = gp.getMembers();
                        Iterator i = members.iterator();
                        while (i.hasNext()) {
                                Member member = (Member) i.next();
                                Element uElement = dom.createElement("Members");
                                uElement.setAttribute("id", member.getUserEid() );
                                User user = userDirectoryService.getUserByEid(member.getUserEid());
                                uElement.setAttribute("FirstName", user.getFirstName());
                                uElement.setAttribute("LastName", user.getLastName());
                                uElement.setAttribute("role", member.getRole().getId());
                                all.appendChild(uElement);
                        }
                }
		catch (Exception e) {
			return "failure: " + e.getMessage();
		}

            return Xml.writeDocumentToString(dom);
        }

    // ######## Migrated from WSProfile.jws ########

    @WebMethod
    @Path("/updateProfilePhoto")
    @Produces("text/plain")
    @GET
    public String updateProfilePhoto(
            @WebParam(name = "sessionId", partName = "sessionId") @QueryParam("sessionId") String sessionId,
            @WebParam(name = "userId", partName = "userId") @QueryParam("userId") String userId,
            @WebParam(name = "photo", partName = "photo") @QueryParam("photo") String photo) {

        Session session = establishSession(sessionId);

        if (!securityService.isSuperUser()) {
            LOG.warn("Non-admin trying to update user photo: " + session.getUserId());
            return "failed: user is not admin";
        }
	
        if (userId == null || userId.equals("")) {
            LOG.warn("Failed to update profile for user: (null or empty).");
            return "failed: userid is empty";
        }
   
        // userId here is an EID 
        userId = userId.toLowerCase();
        SakaiPerson sakaiPerson = getUserProfile(userId, "SystemMutableType");
	
        if (sakaiPerson != null) {
            try {
		byte[] photoData = base64.decode(photo);
		sakaiPerson.setJpegPhoto(photoData);
		// sakaiPerson.setSystemPicturePreferred(Boolean.TRUE);
		// sakaiPerson.setHidePrivateInfo(Boolean.TRUE);
		sakaiPersonManager.save(sakaiPerson);
                LOG.info("Updated profile photo for user " + userId);
            } catch (Exception e) {
		LOG.warn("Failed to update profile for user " + userId + ": " + e.getMessage());
		return "failed: " + e.getMessage();
            }
        } else {
		return "failed: no profile found";
	}

	return "ok";
    }

    /**
     * Get a user profile for a user
     */
    private SakaiPerson getUserProfile(String userId, String type) {
	
	// Uid's must be lower case
	userId = userId.toLowerCase();
	
        SakaiPerson sakaiPerson = null;
    
        try {
        	Type _type = sakaiPersonManager.getSystemMutableType();
        	User user = userDirectoryService.getUserByEid(userId);
        	LOG.info("Found profile for user " + user.getId() + " with eid of " + user.getEid());
                sakaiPerson = sakaiPersonManager.getSakaiPerson(user.getId(), _type);

                // create profile if it doesn't exist
                if (sakaiPerson == null){
                   sakaiPerson = sakaiPersonManager.create(user.getId(), _type);
                   LOG.info("Creating profile for user " + userId + " eid " + user.getEid() + " of type " + _type);
                }
        }
        catch (UserNotDefinedException und) {
        	LOG.info("getUserProfile: User does not exist - userId " + userId);
        }
        catch(Exception e){
            LOG.info("Unknown error occurred in getUserProfile(" + userId + "): " + e.getMessage());
            e.printStackTrace();
        }
    
        return sakaiPerson;
    }

    // Assignments method - moved from Assignments.java

    /*
    @WebMethod
    @Path("/setAssignmentGradeCommentforUser")
    @Produces("text/plain")
    @POST
    public String setAssignmentGradeCommentforUser(
            @WebParam(name = "sessionId", partName = "sessionId") @FormParam("sessionId") String sessionId,
            @WebParam(name = "assignmentId", partName = "assignmentId") @FormParam("assignmentId") String assignmentId,
            @WebParam(name = "userId", partName = "userId") @FormParam("userId") String userId,
            @WebParam(name = "comment", partName = "comment") @FormParam("comment") String comment,
            @WebParam(name = "grade", partName = "grade") @FormParam("grade") float grade) {

        // establish the session

        if (StringUtils.isEmpty(sessionId)) {
		LOG.warn("No sessionId");
		return "failure: no-session-id";
        }
    
	Session s = null;

    	try {		
    		s = establishSession(sessionId);
        } catch (RuntimeException e) {
		LOG.warn("Invalid session for setAssignmentGradeCommentforUser (not active)");
		return "failure: invalid-session";
        }

        try {

    		LOG.info("User " + s.getUserEid() + " setting assignment grade/comment for " + userId + " on " + assignmentId + " to grade '" + grade + "'"); 

    		User user = userDirectoryService.getUserByEid(userId);
		if (user == null) 
		{
			LOG.warn("UserEid " + userId + " is invalid");
			return "failure: invalid-user";
		}
			
    		Assignment assign = assignmentService.getAssignment(assignmentId);
		int scaleFactor = assign.getContent().getFactor();

		LOG.info("Scale factor for this assignment: " + scaleFactor);

    		String aReference = assign.getReference();
    		
    		if (!securityService.unlock(AssignmentService.SECURE_GRADE_ASSIGNMENT_SUBMISSION, aReference))
    		{
    			LOG.warn("User " + s.getUserEid() + " does not have permission to set assignment grades");
    			return "failure: no permission";
    		}
    		
    		LOG.info("Setting assignment grade/comment for " + userId + " on " + assignmentId + " to " + grade); 
    		
    		AssignmentSubmission sub = assignmentService.getSubmission(assignmentId, user);
    		AssignmentSubmissionEdit asEdit =  null;
    		String context = assign.getContext();

    		if (sub == null) {
                        // Create a submission if one does not already exist
			String sessionUserId = s.getUserId();
			String sessionUserEid = s.getUserEid();
			s.setUserId(user.getId());
			s.setUserEid(user.getEid());
			try {
				asEdit = assignmentService.addSubmission(context, assignmentId, user.getId());
				long time = System.currentTimeMillis();
				Time subTime = timeService.newTime(time);
				asEdit.setTimeSubmitted(subTime);
			} finally {
				s.setUserId(sessionUserId);
				s.setUserEid(sessionUserEid);
			}
    		} else {
			// Get the submission
    			asEdit = assignmentService.editSubmission(sub.getReference());
    		}

		if (asEdit == null) {
    			LOG.warn("User " + s.getUserEid() + " does not have permission to set assignment grades");
    			return "failure: no submission";
		}

		// Scale the grade according to the assignment scaling factor

		String scaledGrade = String.valueOf(Math.round(grade * scaleFactor));

		LOG.info("Scaled grade is: " + scaledGrade);

    		asEdit.setFeedbackComment(comment);
    		asEdit.setGrade(scaledGrade);
    		asEdit.setGraded(true);
    		asEdit.setGradeReleased(true);
    		assignmentService.commitEdit(asEdit);
    		
    		// If necessary, update the assignment grade in the Gradebook

    		String sReference = asEdit.getReference();

    		String associateGradebookAssignment = StringUtils.trimToNull(assign.getProperties().getProperty(AssignmentService.PROP_ASSIGNMENT_ASSOCIATE_GRADEBOOK_ASSIGNMENT));
    		
    		// update grade in gradebook
    		updateGradebook(aReference, associateGradebookAssignment, sReference, context, String.valueOf(grade));

    	}
	catch (UserNotDefinedException une) 
	{
		LOG.warn("UserEid " + userId + " is invalid");
		return "failure: invalid-user";
	}
	catch (InUseException iue) 
	{
		LOG.warn("Submission record for " + userId + " on " + assignmentId + " is locked");
		return "failure: submission-locked";
	}
	catch (PermissionException pe) {
		LOG.warn("Permission exception while setting assignment grade/comment for " + userId + " on " + assignmentId + " to grade '" + grade + "' : " + pe.getMessage()); 
		return "failure: permission-denied";
	}
    	catch (Exception e) 
    	{
		LOG.error("WS setAssignmentGradeCommentforUser(): Exception while setting assignment grade/comment for " + userId + " on " + assignmentId + " to grade '" + grade + "'", e); 
		return "failure: " + e.getMessage();	
    	}
    	
    	return "success";
    }
    */

    // This is a copy of the code in AssignmentAction.java

    /**
     *
     * @param assignmentRef
     * @param associateGradebookAssignment
     * @param addUpdateRemoveAssignment
     * @param newAssignment_title
     * @param newAssignment_maxPoints
     * @param newAssignment_dueTime
     * @param submissionRef
     * @param updateRemoveSubmission
     * @param context
     */

    /*
    protected void updateGradebook(String assignmentRef, String associateGradebookAssignment, String submissionRef, String context, String grade)
    {
    	//add or remove external grades to gradebook
    	// a. if Gradebook does not exists, do nothing, 'cos setting should have been hidden
    	// b. if Gradebook exists, just call addExternal and removeExternal and swallow any exception. The
    	//    exception are indication that the assessment is already in the Gradebook or there is nothing
    	//    to remove.

    	String gradebookUid = context;
    	boolean gradebookExists = isGradebookDefined(context);
    	
    	String assignmentToolTitle = "Assignments";
    	
    	if (gradebookExists)
    	{
    		boolean isExternalAssignmentDefined=gradebookExternalAssessmentService.isExternalAssignmentDefined(gradebookUid, assignmentRef);
    		boolean isExternalAssociateAssignmentDefined = gradebookExternalAssessmentService.isExternalAssignmentDefined(gradebookUid, associateGradebookAssignment);
    		boolean isAssignmentDefined = gradebookService.isAssignmentDefined(gradebookUid, associateGradebookAssignment);

		try
		{
			Assignment a = assignmentService.getAssignment(assignmentRef);

			if ( a.getProperties().getProperty(NEW_ASSIGNMENT_ADD_TO_GRADEBOOK) != null
				&& !a.getProperties().getProperty(NEW_ASSIGNMENT_ADD_TO_GRADEBOOK).equals(AssignmentService.GRADEBOOK_INTEGRATION_NO)
				&& a.getContent().getTypeOfGrade() == Assignment.SCORE_GRADE_TYPE)
			{
				try
				{
					// only update one submission
					AssignmentSubmission aSubmission = (AssignmentSubmission) assignmentService.getSubmission(submissionRef);
					User[] submitters = aSubmission.getSubmitters();

					if (associateGradebookAssignment != null)
					{
						if (gradebookExternalAssessmentService.isExternalAssignmentDefined(gradebookUid, associateGradebookAssignment))
						{
							// the associated assignment is externally maintained
							gradebookExternalAssessmentService.updateExternalAssessmentScore(gradebookUid, associateGradebookAssignment, submitters[0].getId(),
									(grade != null && aSubmission.getGradeReleased()) ? grade : null);
						}
						else if (gradebookService.isAssignmentDefined(gradebookUid, associateGradebookAssignment))
						{
							// the associated assignment is internal one, update records
							gradebookService.setAssignmentScoreString(gradebookUid, associateGradebookAssignment, submitters[0].getId(),
									(grade != null && aSubmission.getGradeReleased()) ? grade : null, assignmentToolTitle);
						}
					}
					else
					{
						gradebookExternalAssessmentService.updateExternalAssessmentScore(gradebookUid, assignmentRef, submitters[0].getId(),
								(grade != null && aSubmission.getGradeReleased()) ? grade : null);
					}
				}
				catch (Exception e)
				{
						LOG.warn("Cannot find submission " + submissionRef + ": " + e.getMessage());
				}
			}
		}
		catch (Exception e)
		{
			LOG.warn("Cannot find assignment: " + assignmentRef + ": " + e.getMessage());
		}
    		
    	}	// if gradebook exists
    	
    }	// updateGradebook

    protected boolean isGradebookDefined(String context)
    {
    	boolean  rv = false;
    	try
    	{
    		if (gradebookService.isGradebookDefined(context))
    		{
    			return true;
    		}
    	}
    	catch (Exception e)
    	{
    		//LOG.debug("chef", this + rb.getString("addtogradebook.alertMessage") + "\n" + e.getMessage());
    	}
    	
    	return false;
    	
    }	// isGradebookDefined()

    */
}
