/**
 * Copyright (c) 2016 The Apereo Foundation
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

import java.io.FileInputStream;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import javax.jws.WebMethod;
import javax.jws.WebParam;
import javax.jws.WebService;
import javax.jws.soap.SOAPBinding;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;

import org.apache.commons.lang.StringUtils;
import org.apache.cxf.message.Message;
import org.apache.cxf.phase.PhaseInterceptorChain;
import org.apache.cxf.transport.http.AbstractHTTPDestination;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sakaiproject.component.api.ServerConfigurationService;
import org.sakaiproject.component.cover.ComponentManager;
import org.sakaiproject.event.api.EventTrackingService;
import org.sakaiproject.event.api.UsageSessionService;
import org.sakaiproject.tool.api.Session;
import org.sakaiproject.tool.api.SessionManager;
import org.sakaiproject.user.api.User;
import org.sakaiproject.user.api.UserDirectoryService;

/**
 * SakaiSigning.java
 * <p/>
 * Linktool webservices
 */

@WebService
@SOAPBinding(style = SOAPBinding.Style.RPC, use = SOAPBinding.Use.LITERAL)
public class SakaiSigning extends AbstractWebService {

    private static final Logger LOG = LoggerFactory.getLogger(SakaiSigning.class);

    private static String EVENT_TESTSIGN = "linktool.testsign";
    private static String EVENT_LOGIN_OBJECT = "user.login.linktool";
	
    private static SecretKey salt;
    private static SecretKey privateKey;

    // Linktool methods

    @WebMethod
    @GET
    public String testsign(@WebParam(name = "data", partName = "data") String data) {
		
		if (!"true".equals(serverConfigurationService.getString("linktool.enabled", "false")))
			return "not enabled";
		
		int i = data.indexOf("&time=");
		int j = data.indexOf("&", i+6);
		String time = data.substring(i+6,j);
		long mstime = java.lang.Long.parseLong(time);
		
		if (java.lang.Math.abs(mstime - System.currentTimeMillis()) > 30000)
			return "stale value";
		
		// post the event
		LOG.debug("testsign request: " + data);
		eventTrackingService.post(eventTrackingService.newEvent(EVENT_TESTSIGN, null, true));
		
		return verifysign(data);
    }
	
    @WebMethod
    @GET
    public String verifysign(@WebParam(name = "data", partName = "data") String data) {
		
		if (!"true".equals(serverConfigurationService.getString("linktool.enabled", "false")))
			return "false";
		
		int i = data.lastIndexOf("&");
		String sign = data.substring(i+6);
		data = data.substring(0, i);
		
		boolean result = false;
		
		try {
			result = verify(data, sign);
		} catch (Exception e) {	 
			return e.getClass().getName() + " : " + e.getMessage();
		}
		
		return result ? "true" : "false";
	}

    @WebMethod
    @GET
    public String getSessionToServer(@WebParam(name = "object", partName = "object") String object) {
		return getSessionForUser(null, object) + "," + 
			serverConfigurationService.getString("webservices.directurl", serverConfigurationService.getString("serverUrl"));
    }

    @WebMethod
    @GET
    public String getSessionToServerForUser(@WebParam(name = "data", partName = "data") String data, @WebParam(name = "object", partName = "object") String object) {
		return getSessionForUser(data, object) + "," + 
			serverConfigurationService.getString("webservices.directurl", serverConfigurationService.getString("serverUrl"));
    }

    @WebMethod
    @GET
    public String getSession(@WebParam(name = "object", partName = "object") String object) {
		return getSessionForUser(null, object);
    }

    @WebMethod
    @GET
    public String getSessionForUser(@WebParam(name = "data", partName = "data") String data, @WebParam(name = "object", partName = "object") String object) {
		
		if (!"true".equals(serverConfigurationService.getString("linktool.enabled", "false")))
			throw new RuntimeException("not enabled");
		
		LOG.debug("getsession request");
	
		// Get user IP
		Message message = PhaseInterceptorChain.getCurrentMessage();
	        HttpServletRequest request = (HttpServletRequest) message.get(AbstractHTTPDestination.HTTP_REQUEST);
	        String ipAddress = request.getRemoteAddr();
                String userAgent = request.getHeader("User-Agent");
	
		// Check user data
		
		if (data != null) {
			String sresult = verifysign(data);
			if (!sresult.equals("true"))
				throw new RuntimeException("failed on user data");
		}

		// Check session object
		
		int i = object.lastIndexOf("&");
		String sign = object.substring(i+6);
		object = object.substring(0, i);
		
		boolean result = false;
		
		try {
			result = verify(object, sign);
		} catch (Exception e) {	 
			throw new RuntimeException(e.getClass().getName() + " : " + e.getMessage());
		}

		if (!result)
			throw new RuntimeException("failed on session object");
		
		Session s = null;
		String sessionid = null;
		String userref = null;
		
		if (object.equals("currentuser") && data != null)
			s = makesession(data, "internaluser=");
		else 
			s = makesession(object, "user=");
		
		if (s != null) {
			sessionid = s.getId();
			String userid = s.getUserId(); 
			userref = (userid != null) ? userDirectoryService.userReference(userid) : null;
	
			// Create session (and post login event)
               		usageSessionService.login(userid, s.getUserEid(), ipAddress, userAgent, EVENT_LOGIN_OBJECT);

			LOG.info("Created Linktool signed object session for " + s.getUserEid());
		
		return sessionid;
		}

		// No session
		return null;
    }
	
    @WebMethod
    @GET
    public String touchsession(@WebParam(name = "esessions", partName = "esessions") String esessions) {
		
		// no security; you can't do anything damaging with this
		// you just prevent a session from timing out
		
		if (!"true".equals(serverConfigurationService.getString("linktool.enabled", "false")))
			return "not enabled";
		
		int i;
		String esession;
		
		while (esessions != null) {
			i = esessions.indexOf(",");
			if (i > 0) {
				esession = esessions.substring(0, i);
				esessions = esessions.substring(i+1);
			} else {
				esession = esessions;
				esessions = null;
			}
			esession = esession.trim();
			
			Session s = null;
			String sessionid = decrypt(esession);
			
			if (sessionid != null)
				s = sessionManager.getSession(sessionid);
			
			if (s == null)
				throw new RuntimeException("Session "+esession+" is not active");
			
			s.setActive();
		}
		
		return "success";
    }


    // Private methods. Some of these are now also in basiclti/basiclti-common/src/java/org/sakaiproject/linktool/LinkToolUtil.java

	private boolean verify(String data, String sign) throws Exception {
		if (salt == null)
			salt = readSecretKey("salt", "HmacSHA1");
		Mac sig = Mac.getInstance("HmacSHA1");
		sig.init(salt);
		return sign.equals(byteArray2Hex(sig.doFinal(data.getBytes())));
	}

	private String decrypt (String enc) {
		if (privateKey == null)
			privateKey = readSecretKey("privkey", "Blowfish");
		
		try {
			Cipher dcipher = Cipher.getInstance("Blowfish");
			dcipher.init(Cipher.DECRYPT_MODE, privateKey);
			byte[] dec = hex2byte(enc);
			// Decrypt
			byte[] utf8 = dcipher.doFinal(dec);
			// Decode using utf-8
			return new String(utf8, "UTF8");
		} catch (Exception ignore) {
			LOG.warn("SakaiSigning decrypt failed", ignore);
		}
		return null;
	}
	
	private SecretKey readSecretKey(String name, String alg) {
		try {
			String homedir = serverConfigurationService.getString("linktool.home", serverConfigurationService.getSakaiHomePath());
			if (homedir == null)
				homedir = "/etc/";
			if (!homedir.endsWith("/"))
			{
				homedir = homedir + "/";
			}
			
			String filename = homedir + "sakai.rutgers.linktool." + name;
			FileInputStream file = new FileInputStream(filename);
			byte[] bytes = new byte[file.available()];
			file.read(bytes);
			file.close();
			SecretKey privkey = new SecretKeySpec(bytes, alg);
			return privkey;
		} catch (Exception ignore) {
			return null;
		}
	}
	
	private static char[] hexChars = {
		'0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'a', 'b', 'c', 'd', 'e', 'f'
	};
	
	/**
	 * Convert byte array to hex string
	 * 
	 * @param ba
	 *			 array of bytes
	 * @throws Exception.
	 */
	
	private String byteArray2Hex(byte[] ba){
		StringBuffer sb = new StringBuffer();
		for (int i = 0; i < ba.length; i++){
			int hbits = (ba[i] & 0x000000f0) >> 4;
			int lbits = ba[i] & 0x0000000f;
			sb.append("" + hexChars[hbits] + hexChars[lbits]);
		}
		return sb.toString();
	}
	
	private byte[] hex2byte(String strhex) {
		if(strhex==null) return null;
		int l = strhex.length();
		
		if(l %2 ==1) return null;
		byte[] b = new byte[l/2];
		for(int i = 0 ; i < l/2 ;i++){
			b[i] = (byte)Integer.parseInt(strhex.substring(i *2,i*2 +2),16);
		}
		return b;
	}

	private Session makesession(String spec, String attr) {
		
		int i;
		if (!spec.startsWith(attr)) {
			i = spec.indexOf("&"+ attr);
			if (i > 0)
				spec = spec.substring(i+1);
			else
				throw new RuntimeException("unabled to find "+attr);
		}
		i = spec.indexOf("&");
		if (i > 0)
			spec = spec.substring(0, i);
		i = spec.indexOf("=");
		// has to be there
		spec = spec.substring(i+1);
		
		User user = null;
		try {
			user = userDirectoryService.getUser(spec);
		} catch (Exception e) {
			throw new RuntimeException(e.getClass().getName() + " : " + e.getMessage());
		}

                // User disabled?
                if (!StringUtils.isBlank(user.getProperties().getProperty("disabled"))) {
			throw new RuntimeException("user " + user.getId() + " not enabled");
                }

                if (!StringUtils.isBlank(user.getProperties().getProperty("signed-object-disabled"))) {
			throw new RuntimeException("user " + user.getId() + " signed objects disabled");
                }

                // Session object disabled?
		
		Session s = sessionManager.startSession();
		if (s == null)
			throw new RuntimeException("Unable to establish session");
		
		s.setUserId(user.getId());
		s.setUserEid(user.getEid());
		
		return s;
	}

}
