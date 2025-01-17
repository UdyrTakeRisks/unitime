/*
 * Licensed to The Apereo Foundation under one or more contributor license
 * agreements. See the NOTICE file distributed with this work for
 * additional information regarding copyright ownership.
 *
 * The Apereo Foundation licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except in
 * compliance with the License. You may obtain a copy of the License at:
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * 
*/
package org.unitime.timetable.solver.jgroups;

import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Properties;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.config.Configurator;
import org.hibernate.SessionFactory;
import org.jgroups.Address;
import org.unitime.commons.hibernate.util.HibernateUtil;
import org.unitime.timetable.ApplicationProperties;
import org.unitime.timetable.defaults.ApplicationProperty;
import org.unitime.timetable.interfaces.RoomAvailabilityInterface;
import org.unitime.timetable.model.Class_;
import org.unitime.timetable.model.DepartmentalInstructor;
import org.unitime.timetable.model.ExamType;
import org.unitime.timetable.model.InstructionalOffering;
import org.unitime.timetable.model.Solution;
import org.unitime.timetable.model.TeachingRequest;
import org.unitime.timetable.model.dao._RootDAO;
import org.unitime.timetable.util.Constants;
import org.unitime.timetable.util.RoomAvailability;
import org.unitime.timetable.util.queue.LocalQueueProcessor;
import org.unitime.timetable.util.queue.QueueProcessor;

/**
 * @author Tomas Muller
 */
public abstract class AbstractSolverServer implements SolverServer {
	protected static Log sLog = LogFactory.getLog(AbstractSolverServer.class);
	
	protected int iUsageBase = 0;
	protected Date iStartTime = new Date();
	protected boolean iActive = false;

	public AbstractSolverServer() {
	}
	
	@Override
	public void start() throws Exception {
		iActive = true;
		sLog.info("Solver server is up and running.");
	}
	
	@Override
	public void stop() throws Exception {
		sLog.info("Solver server is going down...");
		iActive = false;
	}

	@Override
	public boolean isLocal() {
		return true;
	}

	@Override
	public boolean isCoordinator() {
		return true;
	}
	
	@Override
	public boolean isLocalCoordinator() {
		return isLocal() && isCoordinator();
	}

	@Override
	public Address getAddress() {
		return null;
	}

	@Override
	public Address getLocalAddress() {
		return getAddress();
	}

	@Override
	public String getHost() {
		return "local";
	}

	@Override
	public int getUsage() {
		int ret = iUsageBase;
		String baseUsage = ApplicationProperty.SolverBaseUsage.value();
		if (baseUsage == null || baseUsage.isEmpty()) {
			ret += (isLocal() ? 500 : 0);
		} else {
			try {
				ret += Integer.parseInt(baseUsage);
			} catch (NumberFormatException e) {
				ret += (isLocal() ? 500 : 0);
			}
		}
		return ret;
	}
	
	@Override
	public void setUsageBase(int base) {
		iUsageBase = base;
	}

	@Override
	public long getAvailableMemory() {
		return Runtime.getRuntime().maxMemory() - Runtime.getRuntime().totalMemory() + Runtime.getRuntime().freeMemory();
	}
	
	@Override
	public int getAvailableProcessors() {
		return Runtime.getRuntime().availableProcessors();
	}
	
	@Override
	public long getMemoryLimit() {
		return 1024l * 1024l * Long.parseLong(ApplicationProperties.getProperty(ApplicationProperty.SolverMemoryLimit));
	}
	
	@Override
	public String getVersion() {
		return Constants.getVersion();
	}
	
	public Date getStartTime() {
		return iStartTime;
	}
	
	@Override
	public boolean isActive() {
		return iActive;
	}
	
	@Override
	public boolean isAvailable() {
		if (!isActive()) return false;
		if (getMemoryLimit() > getAvailableMemory()) System.gc();
		return getMemoryLimit() <= getAvailableMemory();
	}
	
	@Override
	public RoomAvailabilityInterface getRoomAvailability() {
		return RoomAvailability.getInstance();
	}
	
	@Override
	public void refreshCourseSolution(Long... solutionIds) {
		try {
			for (Long solutionId: solutionIds)
				Solution.refreshSolution(solutionId);
		} finally {
			HibernateUtil.closeCurrentThreadSessions();
		}
	}
	
	@Override
	public void refreshExamSolution(Long sessionId, Long examTypeId) {
		try {
			ExamType.refreshSolution(sessionId, examTypeId);
		} finally {
			HibernateUtil.closeCurrentThreadSessions();
		}
	}
	
	@Override
	public void refreshInstructorSolution(Collection<Long> solverGroupIds) {
		org.hibernate.Session hibSession = new _RootDAO().createNewSession();
		try {
			SessionFactory hibSessionFactory = hibSession.getSessionFactory();
	    	List<Long> classIds = hibSession.createQuery(
	    			"select distinct c.uniqueId from Class_ c inner join c.teachingRequests r where c.controllingDept.solverGroup.uniqueId in :solverGroupId and c.cancelled = false", Long.class)
	    			.setParameterList("solverGroupId", solverGroupIds).list();
	    	for (Long classId: classIds) {
	            hibSessionFactory.getCache().evictEntityData(Class_.class, classId);
	            hibSessionFactory.getCache().evictCollectionData(Class_.class.getName()+".classInstructors", classId);
	    	}
	    	List<Long> instructorIds = hibSession.createQuery(
	    			"select i.uniqueId from DepartmentalInstructor i, SolverGroup g inner join g.departments d where " +
	    			"g.uniqueId in :solverGroupId and i.department = d", Long.class
	    			).setParameterList("solverGroupId", solverGroupIds).list();
	    	for (Long instructorId: instructorIds) {
	            hibSessionFactory.getCache().evictEntityData(DepartmentalInstructor.class, instructorId);
	            hibSessionFactory.getCache().evictCollectionData(DepartmentalInstructor.class.getName()+".classes", instructorId);
	    	}
	    	List<Long> requestIds = hibSession.createQuery(
	    			"select distinct r.uniqueId from Class_ c inner join c.teachingRequests r where c.controllingDept.solverGroup.uniqueId in :solverGroupId and c.cancelled = false", Long.class)
	    			.setParameterList("solverGroupId", solverGroupIds).list();
	    	for (Long requestId: requestIds) {
	            hibSessionFactory.getCache().evictEntityData(TeachingRequest.class, requestId);
	            hibSessionFactory.getCache().evictCollectionData(TeachingRequest.class.getName()+".assignedInstructors", requestId);
	    	}
	    	List<Long> offeringIds = hibSession.createQuery(
	    			"select distinct c.schedulingSubpart.instrOfferingConfig.instructionalOffering.uniqueId from " +
	    			"Class_ c inner join c.teachingRequests r where c.controllingDept.solverGroup.uniqueId in :solverGroupId and c.cancelled = false", Long.class)
	    			.setParameterList("solverGroupId", solverGroupIds).list();
	    	for (Long offeringId: offeringIds) {
	            hibSessionFactory.getCache().evictEntityData(InstructionalOffering.class, offeringId);
	            hibSessionFactory.getCache().evictCollectionData(InstructionalOffering.class.getName()+".offeringCoordinators", offeringId);
	    	}
		} finally {
			hibSession.close();
		}
	}


	@Override
	public void setApplicationProperty(Long sessionId, String key, String value) {
		sLog.info("Set " + key + " to " + value + (sessionId == null ? "" : " (for session " + sessionId + ")"));
		Properties properties = (sessionId == null ? ApplicationProperties.getConfigProperties() : ApplicationProperties.getSessionProperties(sessionId));
		if (properties == null) return;
		if (value == null)
			properties.remove(key);
		else
			properties.setProperty(key, value);
	}

	@Override
	public void setLoggingLevel(String name, String level) {
		sLog.info("Set logging level for " + (name == null ? "root" : name) + " to " + (level == null ? "null" : level));
		if (level == null) {
			if (name == null || name.isEmpty())
				Configurator.setRootLevel(Level.INFO);
			else
				Configurator.setLevel(name, (Level)null);
		} else {
			if (name == null || name.isEmpty())
				Configurator.setRootLevel(Level.getLevel(level));
			else
				Configurator.setLevel(name, Level.getLevel(level));
		}
	}

	@Override
	public void reset() {
	}
	
	@Override
	public QueueProcessor getQueueProcessor() {
		return LocalQueueProcessor.getInstance();
	}
}