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

import org.unitime.timetable.defaults.ApplicationProperty;
import org.unitime.timetable.model.base.BaseCurriculumReservation;

/**
 * @author Tomas Muller
 */
public class CurriculumReservation extends BaseCurriculumReservation {
	private static final long serialVersionUID = -261396109078027984L;

	public CurriculumReservation() {
		super();
	}

	@Override
	public boolean isApplicable(Student student, CourseRequest request) {
		for (AcademicAreaClassification aac: student.getAcademicAreaClassifications()) {
			if (aac.getAcademicArea().equals(getArea())) {
				if (getClassifications().isEmpty() || getClassifications().contains(aac.getAcademicClassification())) {
					if (getMajors().isEmpty()) return true;
					else for (PosMajor major: getMajors()) {
						if (student.getPosMajors().contains(major)) return true;
					}
				}
			}
		}
		return false;
	}
	
	@Override
	public int getPriority() {
		return ApplicationProperty.ReservationPriorityCurriculum.intValue();
	}

	@Override
	public boolean isCanAssignOverLimit() {
		return ApplicationProperty.ReservationCanOverLimitCurriculum.isTrue();
	}

	@Override
	public boolean isMustBeUsed() {
		return ApplicationProperty.ReservationMustBeUsedCurriculum.isTrue();
	}

	@Override
	public boolean isAllowOverlap() {
		return ApplicationProperty.ReservationAllowOverlapCurriculum.isTrue();
	}
}
