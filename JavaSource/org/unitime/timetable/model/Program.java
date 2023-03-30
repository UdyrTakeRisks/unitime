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
package org.unitime.timetable.model;

import java.util.List;

import org.unitime.timetable.model.base.BaseProgram;
import org.unitime.timetable.model.dao.ProgramDAO;

public class Program extends BaseProgram {
	private static final long serialVersionUID = 1L;

	public Program() {
		super();
	}


	public static List<Program> findBySession(org.hibernate.Session hibSession, Long sessionId) {
		return (hibSession == null ? ProgramDAO.getInstance().getSession() : hibSession).createQuery(
				"from Program x where x.session.uniqueId = :sessionId order by x.reference")
				.setParameter("sessionId", sessionId, org.hibernate.type.LongType.INSTANCE).list();
	}
	
    public Object clone() {
    	Program program = new Program();
    	program.setExternalUniqueId(getExternalUniqueId());
    	program.setReference(getReference());
    	program.setLabel(getLabel());
    	return program;
    }
}
