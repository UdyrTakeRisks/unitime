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
package org.unitime.timetable.reports.exam;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.Date;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.TreeSet;


import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.cpsolver.coursett.model.TimeLocation;
import org.unitime.commons.Email;
import org.unitime.commons.hibernate.util.HibernateUtil;
import org.unitime.localization.impl.Localization;
import org.unitime.localization.messages.ExaminationMessages;
import org.unitime.timetable.ApplicationProperties;
import org.unitime.timetable.defaults.ApplicationProperty;
import org.unitime.timetable.gwt.resources.GwtConstants;
import org.unitime.timetable.gwt.resources.StudentSectioningConstants;
import org.unitime.timetable.model.Assignment;
import org.unitime.timetable.model.ClassEvent;
import org.unitime.timetable.model.Class_;
import org.unitime.timetable.model.CourseOffering;
import org.unitime.timetable.model.DatePattern;
import org.unitime.timetable.model.DepartmentalInstructor;
import org.unitime.timetable.model.Exam;
import org.unitime.timetable.model.ExamOwner;
import org.unitime.timetable.model.ExamPeriod;
import org.unitime.timetable.model.ExamType;
import org.unitime.timetable.model.InstrOfferingConfig;
import org.unitime.timetable.model.InstructionalOffering;
import org.unitime.timetable.model.Location;
import org.unitime.timetable.model.Meeting;
import org.unitime.timetable.model.Session;
import org.unitime.timetable.model.Student;
import org.unitime.timetable.model.SubjectArea;
import org.unitime.timetable.model.TimetableManager;
import org.unitime.timetable.model.Event.MultiMeeting;
import org.unitime.timetable.model.dao.ExamDAO;
import org.unitime.timetable.model.dao.ExamTypeDAO;
import org.unitime.timetable.model.dao._RootDAO;
import org.unitime.timetable.reports.AbstractReport;
import org.unitime.timetable.solver.exam.ui.ExamAssignment;
import org.unitime.timetable.solver.exam.ui.ExamAssignmentInfo;
import org.unitime.timetable.solver.exam.ui.ExamInfo;
import org.unitime.timetable.solver.exam.ui.ExamRoomInfo;
import org.unitime.timetable.solver.exam.ui.ExamAssignmentInfo.Parameters;
import org.unitime.timetable.solver.exam.ui.ExamInfo.ExamInstructorInfo;
import org.unitime.timetable.solver.exam.ui.ExamInfo.ExamSectionInfo;
import org.unitime.timetable.util.Constants;
import org.unitime.timetable.util.DateUtils;

import com.lowagie.text.DocumentException;

/**
 * @author Tomas Muller
 */
public abstract class PdfLegacyExamReport extends AbstractReport {
	protected static ExaminationMessages MSG = Localization.create(ExaminationMessages.class);
	protected static GwtConstants CONSTANTS = Localization.create(GwtConstants.class);
	protected static StudentSectioningConstants STD_CONST = Localization.create(StudentSectioningConstants.class);
	private static Log sLog = LogFactory.getLog(PdfLegacyExamReport.class);
    
    public static Hashtable<String,Class> sRegisteredReports = new Hashtable();
    public static String sAllRegisteredReports = "";
    private Collection<ExamAssignmentInfo> iExams = null;
    private Session iSession = null;
    private Collection<SubjectArea> iSubjectAreas = null;
    private ExamType iExamType = null;
    
    protected boolean iDispRooms = true;
    protected String iNoRoom = "";
    protected boolean iDirect = true;
    protected boolean iM2d = true;
    protected boolean iBtb = false;
    protected int iLimit = -1;
    protected boolean iItype = false;
    protected boolean iClassSchedule = false;
    protected Hashtable<String,String> iRoomCodes = new Hashtable();
    protected String iRC = null;
    protected boolean iTotals = true;
    protected boolean iUseClassSuffix = false;
    protected boolean iDispLimits = true;
    protected Date iSince = null;
    protected boolean iExternal = false;
    protected boolean iDispFullTermDates = false;
    protected boolean iFullTermCheckDatePattern = true;
    protected boolean iMeetingTimeUseEvents = false;
    protected boolean iDispNote = false;
    protected boolean iCompact = false;
    protected boolean iRoomDisplayNames = false;
    
    protected static DecimalFormat sDF = new DecimalFormat("0.0");
    
    static {
        sRegisteredReports.put("crsn", ScheduleByCourseReport.class);
        sRegisteredReports.put("conf", ConflictsByCourseAndStudentReport.class);
        sRegisteredReports.put("iconf", ConflictsByCourseAndInstructorReport.class);
        sRegisteredReports.put("pern", ScheduleByPeriodReport.class);
        sRegisteredReports.put("xpern", ExamScheduleByPeriodReport.class);
        sRegisteredReports.put("room", ScheduleByRoomReport.class);
        sRegisteredReports.put("chart", PeriodChartReport.class);
        sRegisteredReports.put("xchart", ExamPeriodChartReport.class);
        sRegisteredReports.put("ver", ExamVerificationReport.class);
        sRegisteredReports.put("abbv", AbbvScheduleByCourseReport.class);
        sRegisteredReports.put("xabbv", AbbvExamScheduleByCourseReport.class);
        sRegisteredReports.put("instr", InstructorExamReport.class);
        sRegisteredReports.put("stud", StudentExamReport.class);
        for (String report : sRegisteredReports.keySet())
            sAllRegisteredReports += (sAllRegisteredReports.length()>0?",":"") + report;
    }
    
    public PdfLegacyExamReport(int mode, File file, String title, Session session, ExamType examType, Collection<SubjectArea> subjectAreas, Collection<ExamAssignmentInfo> exams) throws DocumentException, IOException {
    	this(mode, (file == null ? null : new FileOutputStream(file)), title, session, examType, subjectAreas, exams);
    }
    
    public PdfLegacyExamReport(int mode, OutputStream out, String title, Session session, ExamType examType, Collection<SubjectArea> subjectAreas, Collection<ExamAssignmentInfo> exams) throws DocumentException, IOException {
        super(Mode.values()[mode], out, title,
        		ApplicationProperty.ExaminationPdfReportTitle.value(examType == null ? "all" : examType.getReference(), examType == null ? MSG.legacyReportExaminations() : MSG.legacyReportExaminationsOfType(examType.getLabel().toUpperCase())),
                title + " -- " + session.getLabel(), session.getLabel());
        if (subjectAreas!=null && subjectAreas.size() == 1) setFooter(subjectAreas.iterator().next().getSubjectAreaAbbreviation());
        iExams = exams;
        iSession = session;
        iExamType = examType;
        iSubjectAreas = subjectAreas;
        iDispRooms = "true".equals(System.getProperty("room","true"));
        iDispNote = "true".equals(System.getProperty("note","false"));
        iCompact = "true".equals(System.getProperty("compact", "false"));
        iNoRoom = System.getProperty("noroom", ApplicationProperty.ExaminationsNoRoomText.value());
        iDirect = "true".equals(System.getProperty("direct","true"));
        iM2d = "true".equals(System.getProperty("m2d",(examType == null || examType.getType() == ExamType.sExamTypeFinal?"true":"false")));
        iBtb = "true".equals(System.getProperty("btb","false"));
        iLimit = Integer.parseInt(System.getProperty("limit", "-1"));
        iItype = "true".equals(System.getProperty("itype", ApplicationProperty.ExaminationReportsShowInstructionalType.value()));
        iTotals = "true".equals(System.getProperty("totals","true"));
        iUseClassSuffix = "true".equals(System.getProperty("suffix", ApplicationProperty.ExaminationReportsClassSufix.value()));
        iExternal = ApplicationProperty.ExaminationReportsExternalId.isTrue();
        iDispLimits = "true".equals(System.getProperty("verlimit","true"));
        iClassSchedule = "true".equals(System.getProperty("cschedule", ApplicationProperty.ExaminationPdfReportsIncludeClassSchedule.value()));
        iDispFullTermDates = "true".equals(System.getProperty("fullterm","false"));
        iRoomDisplayNames = "true".equals(System.getProperty("roomDispNames", "true"));
        iFullTermCheckDatePattern = ApplicationProperty.ExaminationPdfReportsFullTermCheckDatePattern.isTrue();
        iMeetingTimeUseEvents = ApplicationProperty.ExaminationPdfReportsUseEventsForMeetingTimes.isTrue();
        if (System.getProperty("since")!=null) {
            try {
                iSince = new SimpleDateFormat(System.getProperty("sinceFormat","MM/dd/yy")).parse(System.getProperty("since"));
            } catch (Exception e) {
                sLog.error("Unable to parse date "+System.getProperty("since")+", reason: "+e.getMessage());
            }
        }
        setRoomCode(System.getProperty("roomcode", ApplicationProperty.ExaminationRoomCode.value()));
    }
    
    public void setDispRooms(boolean dispRooms) { iDispRooms = dispRooms; }
    public void setNoRoom(String noRoom) { iNoRoom = noRoom; }
    public void setDirect(boolean direct) { iDirect = direct; }
    public void setM2d(boolean m2d) { iM2d = m2d; }
    public void setBtb(boolean btb) { iBtb = btb; }
    public void setLimit(int limit) { iLimit = limit; }
    public void setItype(boolean itype) { iItype = itype; }
    public void setTotals(boolean totals) { iTotals = totals; }
    public void setUseClassSuffix(boolean useClassSuffix) { iUseClassSuffix = true; }
    public void setDispLimits(boolean dispLimits) { iDispLimits = dispLimits; }
    public void setClassSchedule(boolean classSchedule) { iClassSchedule = classSchedule; }
    public void setSince(Date since) { iSince = since; }
    public void setDispFullTermDates(boolean dispFullTermDates) { iDispFullTermDates = dispFullTermDates; }
    public void setUseRoomDisplayNames(boolean roomDispNames) { iRoomDisplayNames = roomDispNames; }
    public void setRoomCode(String roomCode) {
        if (roomCode==null || roomCode.length()==0) {
            iRoomCodes = null;
            iRC = null;
            return;
        }
        iRoomCodes = new Hashtable<String, String>();
        iRC = "";
        for (StringTokenizer s = new StringTokenizer(roomCode,":;,=");s.hasMoreTokens();) {
            String room = s.nextToken(), code = (s.hasMoreTokens() ? s.nextToken() : "#");
            iRoomCodes.put(room, code);
            if (iRC.length()>0) iRC += ", ";
            iRC += code+":"+room;
        }
    }
    public void setDispNote(boolean dispNote) { iDispNote = dispNote; }
    
    public void setCompact(boolean compact) { iCompact = compact; }


    public Collection<ExamAssignmentInfo> getExams() {
        return iExams;
    }
    
    public Session getSession() {
        return iSession; 
    }
    
    public ExamType getExamType() {
        return iExamType;
    }
    
    public boolean hasSubjectArea(String abbv) {
    	if (iSubjectAreas == null) return true;
    	for (SubjectArea area: iSubjectAreas)
    		if (area.getSubjectAreaAbbreviation().equals(abbv)) return true;
    	return false;
    }
    
    public boolean hasSubjectArea(SubjectArea subject) {
    	return iSubjectAreas == null || iSubjectAreas.contains(subject);
    }
    
    public boolean hasSubjectArea(ExamInfo exam) {
    	for (ExamSectionInfo section: exam.getSections())
    		if (hasSubjectArea(section)) return true;
    	return false;
    }
    
    public boolean hasSubjectArea(ExamSectionInfo section) {
    	return hasSubjectArea(section.getSubject());
    }
    
    public boolean hasSubjectAreas() {
    	return iSubjectAreas != null;
    }
    
    public Collection<SubjectArea> getSubjectAreas() {
    	return iSubjectAreas;
    }
    
    public abstract void printReport() throws DocumentException; 
    
    protected boolean iSubjectPrinted = false;
    protected boolean iITypePrinted = false;
    protected boolean iConfigPrinted = false;
    protected boolean iCoursePrinted = false;
    protected boolean iStudentPrinted = false;
    protected boolean iPeriodPrinted = false;
    protected boolean iNewPage = false;
    
    @Override
    public void headerPrinted() {
        iSubjectPrinted = false;
        iCoursePrinted = false;
        iStudentPrinted = false;
        iPeriodPrinted = false;
        iITypePrinted = false;
        iConfigPrinted = false;
        iNewPage = true;
    }
    
    @Override
    protected void printLine(Line line) throws DocumentException {
        iNewPage = false;
        super.printLine(line);
    }
    
    public int getDaysCode(Set meetings) {
        int daysCode = 0;
        for (Iterator i=meetings.iterator();i.hasNext();) {
            Meeting meeting = (Meeting)i.next();
            Calendar date = Calendar.getInstance(Locale.US);
            date.setTime(meeting.getMeetingDate());
            switch (date.get(Calendar.DAY_OF_WEEK)) {
            case Calendar.MONDAY : daysCode |= Constants.DAY_CODES[Constants.DAY_MON]; break;
            case Calendar.TUESDAY : daysCode |= Constants.DAY_CODES[Constants.DAY_TUE]; break;
            case Calendar.WEDNESDAY : daysCode |= Constants.DAY_CODES[Constants.DAY_WED]; break;
            case Calendar.THURSDAY : daysCode |= Constants.DAY_CODES[Constants.DAY_THU]; break;
            case Calendar.FRIDAY : daysCode |= Constants.DAY_CODES[Constants.DAY_FRI]; break;
            case Calendar.SATURDAY : daysCode |= Constants.DAY_CODES[Constants.DAY_SAT]; break;
            case Calendar.SUNDAY : daysCode |= Constants.DAY_CODES[Constants.DAY_SUN]; break;
            }
        }
        return daysCode;
    }
    
    public String getMeetingDate(MultiMeeting m) {
        if (m.getMeetings().isEmpty()) return MSG.lrArrangedHours();
        SimpleDateFormat df = new SimpleDateFormat(MSG.lrDateFormat());
        return 
            df.format(m.getMeetings().first().getMeetingDate())+" - "+
            df.format(m.getMeetings().last().getMeetingDate())+" "+m.getDays(STD_CONST.shortDays(),STD_CONST.shortDays());
    }
    
    public boolean isFullTerm(DatePattern dp, Date[] firstLast) {
        if (iFullTermCheckDatePattern) {
            if (dp!=null) return dp.isDefault();
        }
        if (firstLast != null) {
            Date first = firstLast[0], last = firstLast[1];
            Calendar c = Calendar.getInstance(Locale.US);
            c.setTime(getSession().getSessionBeginDateTime());
            c.add(Calendar.WEEK_OF_YEAR, 2);
            if (first.compareTo(c.getTime())>=0) return false;  
            c.setTime(getSession().getClassesEndDateTime());
            c.add(Calendar.WEEK_OF_YEAR, -2);
            if (last.compareTo(c.getTime())<=0) return false;
            return true;
        }
        return false;
    }
    
    public boolean isFullTerm(ClassEvent classEvent) {
        if (iFullTermCheckDatePattern) {
            DatePattern dp = classEvent.getClazz().effectiveDatePattern();
            if (dp!=null) return dp.isDefault();
        }
        if (classEvent!=null && !classEvent.getMeetings().isEmpty()) {
            Date first = null, last = null;
            for (Iterator i=classEvent.getMeetings().iterator();i.hasNext();) {
                Meeting m = (Meeting)i.next();
                if (first==null || first.compareTo(m.getMeetingDate())>0) first = m.getMeetingDate();
                if (last==null || last.compareTo(m.getMeetingDate())<0) last = m.getMeetingDate();
            }
            Calendar c = Calendar.getInstance(Locale.US);
            c.setTime(getSession().getSessionBeginDateTime());
            c.add(Calendar.WEEK_OF_YEAR, 2);
            if (first.compareTo(c.getTime())>=0) return false;  
            c.setTime(getSession().getClassesEndDateTime());
            c.add(Calendar.WEEK_OF_YEAR, -2);
            if (last.compareTo(c.getTime())<=0) return false;
            return true;
        }
        return false;
    }
    
    protected Cell getMeetingTime(ExamSectionInfo section) {
        if (section.getOwner().getOwnerObject() instanceof Class_) {
            SimpleDateFormat dpf = new SimpleDateFormat(MSG.lrDateFormat());
            Class_ clazz = (Class_)section.getOwner().getOwnerObject();
            if (iMeetingTimeUseEvents) {
                Set meetings = (clazz.getCachedEvent() == null ? null : clazz.getCachedEvent().getMeetings());
                if (meetings!=null && !meetings.isEmpty()) {
                    int dayCode = getDaysCode(meetings);
                    String days = "";
                    for (int i=0;i<Constants.DAY_CODES.length;i++)
                        if ((dayCode & Constants.DAY_CODES[i])!=0) days += STD_CONST.shortDays()[i];
                    Cell dayOfWeek = rpad(days,5);
                    Meeting[] firstLastMeeting = firstLastMeeting(clazz.getCachedEvent());
                    Cell startTime = lpad(firstLastMeeting[0].startTime(),6).withSeparator(" - ");
                    Cell endTime = lpad(firstLastMeeting[0].stopTime(),6);
                    Date first = firstLastMeeting[0].getMeetingDate();
                    Date last = firstLastMeeting[1].getMeetingDate();
                    Cell firstDate = null, lastDate = null;
                    if (!iDispFullTermDates && isFullTerm(clazz.getEvent())) {
                    	firstDate = rpad("", 5).withSeparator("   ");
                    	lastDate = rpad("", 5);
                    } else {
                    	firstDate = new Cell(dpf.format(first)).withSeparator(" - ");
                    	lastDate = new Cell(dpf.format(last));
                    }
                    return new Cell(dayOfWeek, startTime, endTime, firstDate, lastDate);
                }
            }
            Assignment assignment = clazz.getCommittedAssignment();
            Date[] firstLast = (assignment == null ? null : firstLastDate(assignment.getTimeLocation()));
            if (assignment != null) {
                TimeLocation t = assignment.getTimeLocation();
                Cell dayOfWeek = rpad(t.getDayHeader(),5);
                Cell startTime = lpad(t.getStartTimeHeader(CONSTANTS.useAmPm()),6).withSeparator(" - ");
                Cell endTime = lpad(t.getEndTimeHeader(CONSTANTS.useAmPm()),6);
                Cell firstDate = null, lastDate = null;
                if (!iDispFullTermDates && isFullTerm(assignment.getDatePattern(), firstLast)) {
                	firstDate = rpad("", 5).withSeparator("   ");
                	lastDate = rpad("", 5);
                } else if (firstLast != null) {
                	firstDate = new Cell(dpf.format(firstLast[0])).withSeparator(" - ");
                	lastDate = new Cell(dpf.format(firstLast[1]));
                } else {
                	firstDate = rpad(t.getDatePatternName(), 14);
                	lastDate = NULL;
                }
                return new Cell(dayOfWeek, startTime, endTime, firstDate, lastDate);
            }
        }
        return new Cell(rpad("",5), lpad("", 6).withSeparator("   "), lpad("", 6), rpad("", 5).withSeparator("   "), rpad("", 5));
    }
    
    private Meeting[] firstLastMeeting(ClassEvent event) {
    	if (event == null) return null;
    	Meeting first = null, last = null;
    	for (Iterator i = event.getMeetings().iterator(); i.hasNext();) {
    		Meeting m = (Meeting)i.next();
    		if (first == null || first.getMeetingDate().after(m.getMeetingDate())) first = m;
    		if (last == null || last.getMeetingDate().before(m.getMeetingDate())) last = m;
    	}
    	if (first == null) return null;
    	return new Meeting[] { first, last };
    }

    private Date iSessionFirstDate = null;
    private Date[] firstLastDate(TimeLocation time) {
    	if (time == null || time.getWeekCode().isEmpty()) return null;
    	Calendar cal = Calendar.getInstance(Locale.US); cal.setLenient(true);
    	if (iSessionFirstDate == null)
    		iSessionFirstDate = DateUtils.getDate(1, iSession.getPatternStartMonth(), iSession.getSessionStartYear());
    	cal.setTime(iSessionFirstDate);
    	int idx = time.getWeekCode().nextSetBit(0);
    	cal.add(Calendar.DAY_OF_YEAR, idx);
    	Date first = null;
    	while (idx < time.getWeekCode().size() && first == null) {
    		if (time.getWeekCode().get(idx)) {
        		int dow = cal.get(Calendar.DAY_OF_WEEK);
        		switch (dow) {
        		case Calendar.MONDAY:
        			if ((time.getDayCode() & Constants.DAY_CODES[Constants.DAY_MON]) != 0) first = cal.getTime();
        			break;
        		case Calendar.TUESDAY:
        			if ((time.getDayCode() & Constants.DAY_CODES[Constants.DAY_TUE]) != 0) first = cal.getTime();
        			break;
        		case Calendar.WEDNESDAY:
        			if ((time.getDayCode() & Constants.DAY_CODES[Constants.DAY_WED]) != 0) first = cal.getTime();
        			break;
        		case Calendar.THURSDAY:
        			if ((time.getDayCode() & Constants.DAY_CODES[Constants.DAY_THU]) != 0) first = cal.getTime();
        			break;
        		case Calendar.FRIDAY:
        			if ((time.getDayCode() & Constants.DAY_CODES[Constants.DAY_FRI]) != 0) first = cal.getTime();
        			break;
        		case Calendar.SATURDAY:
        			if ((time.getDayCode() & Constants.DAY_CODES[Constants.DAY_SAT]) != 0) first = cal.getTime();
        			break;
        		case Calendar.SUNDAY:
        			if ((time.getDayCode() & Constants.DAY_CODES[Constants.DAY_SUN]) != 0) first = cal.getTime();
        			break;
        		}
        	}
    		cal.add(Calendar.DAY_OF_YEAR, 1); idx++;
    	}
    	if (first == null) return null;
    	cal.setTime(iSessionFirstDate);
    	idx = time.getWeekCode().length() - 1;
    	cal.add(Calendar.DAY_OF_YEAR, idx);
    	Date last = null;
    	while (idx >= 0 && last == null) {
    		if (time.getWeekCode().get(idx)) {
        		int dow = cal.get(Calendar.DAY_OF_WEEK);
        		switch (dow) {
        		case Calendar.MONDAY:
        			if ((time.getDayCode() & Constants.DAY_CODES[Constants.DAY_MON]) != 0) last = cal.getTime();
        			break;
        		case Calendar.TUESDAY:
        			if ((time.getDayCode() & Constants.DAY_CODES[Constants.DAY_TUE]) != 0) last = cal.getTime();
        			break;
        		case Calendar.WEDNESDAY:
        			if ((time.getDayCode() & Constants.DAY_CODES[Constants.DAY_WED]) != 0) last = cal.getTime();
        			break;
        		case Calendar.THURSDAY:
        			if ((time.getDayCode() & Constants.DAY_CODES[Constants.DAY_THU]) != 0) last = cal.getTime();
        			break;
        		case Calendar.FRIDAY:
        			if ((time.getDayCode() & Constants.DAY_CODES[Constants.DAY_FRI]) != 0) last = cal.getTime();
        			break;
        		case Calendar.SATURDAY:
        			if ((time.getDayCode() & Constants.DAY_CODES[Constants.DAY_SAT]) != 0) last = cal.getTime();
        			break;
        		case Calendar.SUNDAY:
        			if ((time.getDayCode() & Constants.DAY_CODES[Constants.DAY_SUN]) != 0) last = cal.getTime();
        			break;
        		}
        	}
    		cal.add(Calendar.DAY_OF_YEAR, -1); idx--;
    	}
    	if (last == null) return null;
    	return new Date[] { first, last };
    }
    
    protected String getMeetingTime(Meeting meeting) {
        return lpad(meeting.startTime(),6)+" - "+lpad(meeting.stopTime(),6);
    }
    
    protected Cell getMeetingTime(String time) {
        int idx = time.indexOf('-');
        if (idx<0) return lpad(time,15);
        String start = time.substring(0,idx).trim();
        String stop = time.substring(idx+1).trim();
        return new Cell(lpad(start,'0',6).withSeparator(" - "), lpad(stop,'0',6));
    }
    
    public Cell formatRoom(String room) {
        String r = room.trim();
        int idx = r.lastIndexOf(' '); 
        if (idx>=0 && idx<=5 && r.length()-idx-1<=5)
            return new Cell(rpad(r.substring(0, idx),5), rpad(r.substring(idx+1),5));
        return rpad(r, 11);
    }
    
    public Cell formatRoom(ExamRoomInfo roomInfo) {
    	if (roomInfo == null) return rpad("",11);
    	if (iRoomDisplayNames) {
    		Location location = roomInfo.getLocation();
    		String dispName = (location == null ? null : location.getDisplayName());
    		if (dispName != null && !dispName.isEmpty())
    			return rpad(dispName, 11);
    	}
    	return formatRoom(roomInfo.getName());
    }
    
    public Cell formatRoom(Location location) {
    	if (location == null) return rpad("",11);
    	if (iRoomDisplayNames) {
    		String dispName = location.getDisplayName();
    		if (dispName != null && !dispName.isEmpty())
    			return rpad(dispName, 11);
    	}
    	return formatRoom(location.getLabel());
    }
    
    public String formatPeriod(ExamPeriod period) {
        return period.getStartDateLabel()+" "+lpad(period.getStartTimeLabel(),6)+" - "+lpad(period.getEndTimeLabel(),6);
    }

    public String formatPeriod(ExamPeriod period, int length, Integer printOffset) {
        return period.getStartDateLabel()+" "+
            lpad(period.getStartTimeLabel(printOffset==null?0:printOffset.intValue()),6)+" - "+
            lpad(period.getEndTimeLabel(length, (printOffset==null?0:printOffset.intValue())),6);
    }
    
    public String formatPeriod(ExamAssignment assignment) {
        return assignment.getPeriod().getStartDateLabel()+" "+
            lpad(assignment.getPeriod().getStartTimeLabel(assignment.getPrintOffset()),6)+" - "+
            lpad(assignment.getPeriod().getEndTimeLabel(assignment.getLength(), assignment.getPrintOffset()),6);
    }
    
    public Cell formatPeriodDate(ExamAssignment assignment) {
    	return new Cell(assignment.getPeriod().getStartDateLabel());
    }
    
    public Cell formatPeriodTime(ExamAssignment assignment) {
    	return new Cell(
    			lpad(assignment.getPeriod().getStartTimeLabel(assignment.getPrintOffset()),6).withSeparator(" - "),
    			lpad(assignment.getPeriod().getEndTimeLabel(assignment.getLength(), assignment.getPrintOffset()),6)
    			);
    }
    
    public Cell formatSection10(String section) {
    	if (section.length() <= 9)
    		return rpad(lpad(section, 9), 10);
    	else
    		return rpad(section, 10);
    }
    
    public String getShortDate(Date date) {
        Calendar c = Calendar.getInstance(Locale.US);
        c.setTime(date);
        String day = "";
        switch (c.get(Calendar.DAY_OF_WEEK)) {
            case Calendar.MONDAY : day = STD_CONST.shortDays()[Constants.DAY_MON]; break;
            case Calendar.TUESDAY : day = STD_CONST.shortDays()[Constants.DAY_TUE]; break;
            case Calendar.WEDNESDAY : day = STD_CONST.shortDays()[Constants.DAY_WED]; break;
            case Calendar.THURSDAY : day = STD_CONST.shortDays()[Constants.DAY_THU]; break;
            case Calendar.FRIDAY : day = STD_CONST.shortDays()[Constants.DAY_FRI]; break;
            case Calendar.SATURDAY : day = STD_CONST.shortDays()[Constants.DAY_SAT]; break;
            case Calendar.SUNDAY : day = STD_CONST.shortDays()[Constants.DAY_SUN]; break;
        }
        return day+" "+new SimpleDateFormat(MSG.lrDateFormat()).format(date);
    }
    
    public String formatShortPeriod(ExamPeriod period, int length, Integer printOffset) {
        return getShortDate(period.getStartDate())+" "+
            lpad(period.getStartTimeLabel(printOffset==null?0:printOffset.intValue()),6)+"-"+
            lpad(period.getEndTimeLabel(length,printOffset==null?0:printOffset.intValue()),6);
    }
    
    public String formatShortPeriod(ExamAssignment assignment) {
        return getShortDate(assignment.getPeriod().getStartDate())+" "+
            lpad(assignment.getPeriod().getStartTimeLabel(assignment.getPrintOffset()),6)+"-"+
            lpad(assignment.getPeriod().getEndTimeLabel(assignment.getLength(),assignment.getPrintOffset()),6);
    }
    
    public Cell formatShortPeriodDate(ExamAssignment assignment) {
        return new Cell(getShortDate(assignment.getPeriod().getStartDate()));
    }
    
    public Cell formatShortPeriodTime(ExamAssignment assignment) {
        return new Cell(
        		lpad(assignment.getPeriod().getStartTimeLabel(assignment.getPrintOffset()),6).withSeparator("-"),
        		lpad(assignment.getPeriod().getEndTimeLabel(assignment.getLength(),assignment.getPrintOffset()),6)
        		);
    }
    
    public String formatShortPeriodNoEndTime(ExamAssignment assignment) {
        return getShortDate(assignment.getPeriod().getStartDate())+" "+ lpad(assignment.getPeriod().getStartTimeLabel(assignment.getPrintOffset()),6);
    }
    
    public Cell formatShortPeriodNoEndTimeDate(ExamAssignment assignment) {
    	return new Cell(getShortDate(assignment.getPeriod().getStartDate()));
    }
    
    public Cell formatShortPeriodNoEndTimeTime(ExamAssignment assignment) {
    	return new Cell(lpad(assignment.getPeriod().getStartTimeLabel(assignment.getPrintOffset()),6));
    }

    public String getItype(CourseOffering course, Class_ clazz) {
        if (iExternal) {
            String ext = clazz.getExternalId(course);
            return (ext==null?"":ext);
        } else
            return clazz.getSchedulingSubpart().getItypeDesc();
    }
    
    public static void sendEmails(String prefix, Hashtable<String,File> output, Hashtable<SubjectArea,Hashtable<String,File>> outputPerSubject, Hashtable<ExamInstructorInfo,File> ireports, Hashtable<Student,File> sreports) {
        sLog.info("Sending email(s)...");
        if (!outputPerSubject.isEmpty() && "true".equals(System.getProperty("email.deputies","false"))) {
                Hashtable<TimetableManager,Hashtable<String,File>> files2send = new Hashtable();
                for (Map.Entry<SubjectArea, Hashtable<String,File>> entry : outputPerSubject.entrySet()) {
                    if (entry.getKey().getDepartment().getTimetableManagers().isEmpty())
                        sLog.warn("No manager associated with subject area "+entry.getKey().getSubjectAreaAbbreviation()+" ("+entry.getKey().getDepartment().getLabel()+")</font>");
                    for (Iterator i=entry.getKey().getDepartment().getTimetableManagers().iterator();i.hasNext();) {
                        TimetableManager g = (TimetableManager)i.next();
                        if (g.getEmailAddress()==null || g.getEmailAddress().length()==0) {
                            sLog.warn("Manager "+g.getName()+" has no email address.");
                        } else {
                            Hashtable<String,File> files = files2send.get(g);
                            if (files==null) { files = new Hashtable<String,File>(); files2send.put(g, files); }
                            files.putAll(entry.getValue());
                        }
                    }
                }
                if (files2send.isEmpty()) {
                    sLog.error("Nothing to send.");
                } else {
                    Set<TimetableManager> managers = files2send.keySet();
                    while (!managers.isEmpty()) {
                        TimetableManager manager = managers.iterator().next();
                        Hashtable<String,File> files = files2send.get(manager);
                        managers.remove(manager);
                        sLog.info("Sending email to "+manager.getName()+" ("+manager.getEmailAddress()+")...");
                        try {
                            Email mail = Email.createEmail();
                            mail.setSubject(System.getProperty("email.subject","Examination Report"));
                            String message = System.getProperty("email.body");
                            String url = System.getProperty("email.url");
                            mail.setText((message==null?"":message+"\r\n\r\n")+
                                    (url==null?"":"For an up-to-date examination report, please visit "+url+"/\r\n\r\n")+
                                    "This email was automatically generated by "+
                                    "UniTime "+Constants.getVersion()+
                                    " (Univesity Timetabling Application, http://www.unitime.org).");
                            mail.addRecipient(manager.getEmailAddress(),manager.getName());
                            for (Iterator<TimetableManager> i=managers.iterator();i.hasNext();) {
                                TimetableManager m = (TimetableManager)i.next();
                                if (files.equals(files2send.get(m))) {
                                    sLog.info("  Including "+m.getName()+" ("+m.getEmailAddress()+")");
                                    mail.addRecipient(m.getEmailAddress(), m.getName());
                                    i.remove();
                                }
                            }
                            if (System.getProperty("email.to")!=null) for (StringTokenizer s=new StringTokenizer(System.getProperty("email.to"),";,\n\r ");s.hasMoreTokens();) 
                                mail.addRecipient(s.nextToken(), null);
                            if (System.getProperty("email.cc")!=null) for (StringTokenizer s=new StringTokenizer(System.getProperty("email.cc"),";,\n\r ");s.hasMoreTokens();) 
                                mail.addRecipientCC(s.nextToken(), null);
                            if (System.getProperty("email.bcc")!=null) for (StringTokenizer s=new StringTokenizer(System.getProperty("email.bcc"),";,\n\r ");s.hasMoreTokens();) 
                                mail.addRecipientBCC(s.nextToken(), null);
                            for (Map.Entry<String, File> entry : files.entrySet()) {
                            	mail.addAttachment(entry.getValue(), prefix+"_"+entry.getKey());
                                sLog.info("  Attaching <a href='temp/"+entry.getValue().getName()+"'>"+entry.getKey()+"</a>");
                            }
                            mail.send();
                            sLog.info("Email sent.");
                        } catch (Exception e) {
                            sLog.error("Unable to send email: "+e.getMessage());
                        }
                    }
                }
            } else {
                try {
                    Email mail = Email.createEmail();
                    mail.setSubject(System.getProperty("email.subject","Examination Report"));
                    String message = System.getProperty("email.body");
                    String url = System.getProperty("email.url");
                    mail.setText((message==null?"":message+"\r\n\r\n")+
                            (url==null?"":"For an up-to-date examination report, please visit "+url+"/\r\n\r\n")+
                            "This email was automatically generated by "+
                            "UniTime "+Constants.getVersion()+
                            " (Univesity Timetabling Application, http://www.unitime.org).");
                    if (System.getProperty("email.to")!=null) for (StringTokenizer s=new StringTokenizer(System.getProperty("email.to"),";,\n\r ");s.hasMoreTokens();) 
                        mail.addRecipient(s.nextToken(), null);
                    if (System.getProperty("email.cc")!=null) for (StringTokenizer s=new StringTokenizer(System.getProperty("email.cc"),";,\n\r ");s.hasMoreTokens();) 
                        mail.addRecipientCC(s.nextToken(), null);
                    if (System.getProperty("email.bcc")!=null) for (StringTokenizer s=new StringTokenizer(System.getProperty("email.bcc"),";,\n\r ");s.hasMoreTokens();) 
                        mail.addRecipientBCC(s.nextToken(), null);
                    for (Map.Entry<String, File> entry : output.entrySet()) {
                    	mail.addAttachment(entry.getValue(), prefix+"_"+entry.getKey());
                    }
                	mail.send();
                    sLog.info("Email sent.");
                } catch (Exception e) {
                    sLog.error("Unable to send email: "+e.getMessage());
                }
            }
            if ("true".equals(System.getProperty("email.instructors","false")) && ireports!=null && !ireports.isEmpty()) {
                sLog.info("Emailing instructors...");
                for (ExamInstructorInfo instructor : new TreeSet<ExamInstructorInfo>(ireports.keySet())) {
                    File report = ireports.get(instructor);
                    String email = instructor.getInstructor().getEmail();
                    if (email==null || email.length()==0) {
                        sLog.warn("Unable to email <a href='temp/"+report.getName()+"'>"+instructor.getName()+"</a> -- instructor has no email address.");
                        continue;
                    }
                    try {
                        Email mail = Email.createEmail();
                        mail.setSubject(System.getProperty("email.subject","Examination Report"));
                        String message = System.getProperty("email.body");
                        String url = System.getProperty("email.url");
                        mail.setText((message==null?"":message+"\r\n\r\n")+
                                (url==null?"":"For an up-to-date examination report, please visit "+url+"/\r\n\r\n")+
                                "This email was automatically generated by "+
                                "UniTime "+Constants.getVersion()+
                                " (Univesity Timetabling Application, http://www.unitime.org).");
                        mail.addRecipient(email, null);
                        if (System.getProperty("email.cc")!=null) for (StringTokenizer s=new StringTokenizer(System.getProperty("email.cc"),";,\n\r ");s.hasMoreTokens();) 
                            mail.addRecipientCC(s.nextToken(), null);
                        if (System.getProperty("email.bcc")!=null) for (StringTokenizer s=new StringTokenizer(System.getProperty("email.bcc"),";,\n\r ");s.hasMoreTokens();) 
                            mail.addRecipientBCC(s.nextToken(), null);
                        mail.addAttachment(report, prefix + report.getName().substring(report.getName().lastIndexOf('.')));
                    	mail.send();
                        sLog.info("&nbsp;&nbsp;An email was sent to <a href='temp/"+report.getName()+"'>"+instructor.getName()+"</a>.");
                    } catch (Exception e) {
                        sLog.error("Unable to email <a href='temp/"+report.getName()+"'>"+instructor.getName()+"</a> -- "+e.getMessage());
                    }
                }
                sLog.info("Emails sent.");
            }
            if ("true".equals(System.getProperty("email.students","false")) && sreports!=null && !sreports.isEmpty()) {
                sLog.info("Emailing instructors...");
                for (Student student : new TreeSet<Student>(sreports.keySet())) {
                    File report = sreports.get(student);
                    String email = student.getEmail();
                    if (email==null || email.length()==0) {
                        sLog.warn("  Unable to email <a href='temp/"+report.getName()+"'>"+student.getName(DepartmentalInstructor.sNameFormatLastFist)+"</a> -- student has no email address.");
                        continue;
                    }
                    try {
                        Email mail = Email.createEmail();
                        mail.setSubject(System.getProperty("email.subject","Examination Report"));
                        String message = System.getProperty("email.body");
                        String url = System.getProperty("email.url");
                        mail.setText((message==null?"":message+"\r\n\r\n")+
                                (url==null?"":"For an up-to-date examination report, please visit "+url+"/\r\n\r\n")+
                                "This email was automatically generated by "+
                                "UniTime "+Constants.getVersion()+
                                " (Univesity Timetabling Application, http://www.unitime.org).");
                        mail.addRecipient(email, null);
                        if (System.getProperty("email.cc")!=null) for (StringTokenizer s=new StringTokenizer(System.getProperty("email.cc"),";,\n\r ");s.hasMoreTokens();) 
                            mail.addRecipientCC(s.nextToken(), null);
                        if (System.getProperty("email.bcc")!=null) for (StringTokenizer s=new StringTokenizer(System.getProperty("email.bcc"),";,\n\r ");s.hasMoreTokens();) 
                            mail.addRecipientBCC(s.nextToken(), null);
                        mail.addAttachment(report, prefix + report.getName().substring(report.getName().lastIndexOf('.')));
                    	mail.send();
                        sLog.info(" An email was sent to <a href='temp/"+report.getName()+"'>"+student.getName(DepartmentalInstructor.sNameFormatLastFist)+"</a>.");
                    } catch (Exception e) {
                        sLog.error("Unable to email <a href='temp/"+report.getName()+"'>"+student.getName(DepartmentalInstructor.sNameFormatLastFist)+"</a> -- "+e.getMessage()+".");
                    }
                }
                sLog.info("Emails sent.");
            }
    }
    
    public static TreeSet<ExamAssignmentInfo> loadExams(Long sessionId, Long examTypeId, boolean assgn, boolean ignNoEnrl, boolean eventConf) throws Exception {
        sLog.info("Loading exams...");
        long t0 = System.currentTimeMillis();
        Hashtable<Long, Exam> exams = new Hashtable();
        for (Exam exam: ExamDAO.getInstance().getSession().createQuery(
                "select x from Exam x where x.session.uniqueId=:sessionId and x.examType.uniqueId=:examTypeId", Exam.class
                ).setParameter("sessionId", sessionId).setParameter("examTypeId", examTypeId).setCacheable(true).list()) {
            exams.put(exam.getUniqueId(), exam);
        }
        
		sLog.info("  Fetching related objects (class)...");
        ExamDAO.getInstance().getSession().createQuery(
                "select c from Class_ c, ExamOwner o where o.exam.session.uniqueId=:sessionId and o.exam.examType.uniqueId=:examTypeId and o.ownerType=:classType and c.uniqueId=o.ownerId", Class_.class)
                .setParameter("sessionId", sessionId)
                .setParameter("examTypeId", examTypeId)
                .setParameter("classType", ExamOwner.sOwnerTypeClass).setCacheable(true).list();
        sLog.info("  Fetching related objects (config)...");
        ExamDAO.getInstance().getSession().createQuery(
                "select c from InstrOfferingConfig c, ExamOwner o where o.exam.session.uniqueId=:sessionId and o.exam.examType.uniqueId=:examTypeId and o.ownerType=:configType and c.uniqueId=o.ownerId", InstrOfferingConfig.class)
                .setParameter("sessionId", sessionId)
                .setParameter("examTypeId", examTypeId)
                .setParameter("configType", ExamOwner.sOwnerTypeConfig).setCacheable(true).list();
        sLog.info("  Fetching related objects (course)...");
        ExamDAO.getInstance().getSession().createQuery(
                "select c from CourseOffering c, ExamOwner o where o.exam.session.uniqueId=:sessionId and o.exam.examType.uniqueId=:examTypeId and o.ownerType=:courseType and c.uniqueId=o.ownerId", CourseOffering.class)
                .setParameter("sessionId", sessionId)
                .setParameter("examTypeId", examTypeId)
                .setParameter("courseType", ExamOwner.sOwnerTypeCourse).setCacheable(true).list();
        sLog.info("  Fetching related objects (offering)...");
        ExamDAO.getInstance().getSession().createQuery(
                "select c from InstructionalOffering c, ExamOwner o where o.exam.session.uniqueId=:sessionId and o.exam.examType.uniqueId=:examTypeId and o.ownerType=:offeringType and c.uniqueId=o.ownerId", InstructionalOffering.class)
                .setParameter("sessionId", sessionId)
                .setParameter("examTypeId", examTypeId)
                .setParameter("offeringType", ExamOwner.sOwnerTypeOffering).setCacheable(true).list();
        
		sLog.info("  Fetching related class events...");
        Hashtable<Long, ClassEvent> classEvents = new Hashtable();
        for (ClassEvent ce: ExamDAO.getInstance().getSession().createQuery(
        			"select c from ClassEvent c left join fetch c.meetings m, ExamOwner o where o.exam.session.uniqueId=:sessionId and o.exam.examType.uniqueId=:examTypeId and o.ownerType=:classType and c.clazz.uniqueId=o.ownerId",
        			ClassEvent.class)
                .setParameter("sessionId", sessionId)
                .setParameter("examTypeId", examTypeId)
                .setParameter("classType", ExamOwner.sOwnerTypeClass).setCacheable(true).list()) {
        	classEvents.put(ce.getClazz().getUniqueId(), ce);
        }
        
        Hashtable<Long,Set<Long>> owner2students = new Hashtable();
        Hashtable<Long,Set<Exam>> student2exams = new Hashtable();
        Hashtable<Long,Hashtable<Long,Set<Long>>> owner2course2students = new Hashtable();
        if (assgn) {
            sLog.info("  Loading students (class)...");
            for (Object[] o: ExamDAO.getInstance().getSession().createQuery(
                "select x.uniqueId, o.uniqueId, e.student.uniqueId, e.courseOffering.uniqueId from "+
                "Exam x inner join x.owners o, "+
                "StudentClassEnrollment e inner join e.clazz c "+
                "where x.session.uniqueId=:sessionId and x.examType.uniqueId=:examTypeId and "+
                "o.ownerType="+org.unitime.timetable.model.ExamOwner.sOwnerTypeClass+" and "+
                "o.ownerId=c.uniqueId", Object[].class)
            	.setParameter("sessionId", sessionId)
            	.setParameter("examTypeId", examTypeId).setCacheable(true).list()) {
                    Long examId = (Long)o[0];
                    Long ownerId = (Long)o[1];
                    Long studentId = (Long)o[2];
                    Set<Long> studentsOfOwner = owner2students.get(ownerId);
                    if (studentsOfOwner==null) {
                        studentsOfOwner = new HashSet<Long>();
                        owner2students.put(ownerId, studentsOfOwner);
                    }
                    studentsOfOwner.add(studentId);
                    Set<Exam> examsOfStudent = student2exams.get(studentId);
                    if (examsOfStudent==null) { 
                        examsOfStudent = new HashSet<Exam>();
                        student2exams.put(studentId, examsOfStudent);
                    }
                    examsOfStudent.add(exams.get(examId));
                    Long courseId = (Long)o[3];
                    Hashtable<Long, Set<Long>> course2students = owner2course2students.get(ownerId);
                    if (course2students == null) {
                    	course2students = new Hashtable<Long, Set<Long>>();
                    	owner2course2students.put(ownerId, course2students);
                    }
                    Set<Long> studentsOfCourse = course2students.get(courseId);
                    if (studentsOfCourse == null) {
                    	studentsOfCourse = new HashSet<Long>();
                    	course2students.put(courseId, studentsOfCourse);
                    }
                    studentsOfCourse.add(studentId);
                }
            sLog.info("  Loading students (config)...");
            for (Object[] o: ExamDAO.getInstance().getSession().createQuery(
                        "select x.uniqueId, o.uniqueId, e.student.uniqueId, e.courseOffering.uniqueId from "+
                        "Exam x inner join x.owners o, "+
                        "StudentClassEnrollment e inner join e.clazz c " +
                        "inner join c.schedulingSubpart.instrOfferingConfig ioc " +
                        "where x.session.uniqueId=:sessionId and x.examType.uniqueId=:examTypeId and "+
                        "o.ownerType="+org.unitime.timetable.model.ExamOwner.sOwnerTypeConfig+" and "+
                        "o.ownerId=ioc.uniqueId", Object[].class)
            		.setParameter("sessionId", sessionId)
            		.setParameter("examTypeId", examTypeId).setCacheable(true).list()) {
                Long examId = (Long)o[0];
                Long ownerId = (Long)o[1];
                Long studentId = (Long)o[2];
                Set<Long> studentsOfOwner = owner2students.get(ownerId);
                if (studentsOfOwner==null) {
                    studentsOfOwner = new HashSet<Long>();
                    owner2students.put(ownerId, studentsOfOwner);
                }
                studentsOfOwner.add(studentId);
                Set<Exam> examsOfStudent = student2exams.get(studentId);
                if (examsOfStudent==null) { 
                    examsOfStudent = new HashSet<Exam>();
                    student2exams.put(studentId, examsOfStudent);
                }
                examsOfStudent.add(exams.get(examId));
                Long courseId = (Long)o[3];
                Hashtable<Long, Set<Long>> course2students = owner2course2students.get(ownerId);
                if (course2students == null) {
                	course2students = new Hashtable<Long, Set<Long>>();
                	owner2course2students.put(ownerId, course2students);
                }
                Set<Long> studentsOfCourse = course2students.get(courseId);
                if (studentsOfCourse == null) {
                	studentsOfCourse = new HashSet<Long>();
                	course2students.put(courseId, studentsOfCourse);
                }
                studentsOfCourse.add(studentId);
            }
            sLog.info("  Loading students (course)...");
            for (Object[] o: ExamDAO.getInstance().getSession().createQuery(
                        "select x.uniqueId, o.uniqueId, e.student.uniqueId, e.courseOffering.uniqueId from "+
                        "Exam x inner join x.owners o, "+
                        "StudentClassEnrollment e inner join e.courseOffering co " +
                        "where x.session.uniqueId=:sessionId and x.examType.uniqueId=:examTypeId and "+
                        "o.ownerType="+org.unitime.timetable.model.ExamOwner.sOwnerTypeCourse+" and "+
                        "o.ownerId=co.uniqueId", Object[].class)
            		.setParameter("sessionId", sessionId)
            		.setParameter("examTypeId", examTypeId).setCacheable(true).list()) {
                Long examId = (Long)o[0];
                Long ownerId = (Long)o[1];
                Long studentId = (Long)o[2];
                Set<Long> studentsOfOwner = owner2students.get(ownerId);
                if (studentsOfOwner==null) {
                    studentsOfOwner = new HashSet<Long>();
                    owner2students.put(ownerId, studentsOfOwner);
                }
                studentsOfOwner.add(studentId);
                Set<Exam> examsOfStudent = student2exams.get(studentId);
                if (examsOfStudent==null) { 
                    examsOfStudent = new HashSet<Exam>();
                    student2exams.put(studentId, examsOfStudent);
                }
                examsOfStudent.add(exams.get(examId));
                Long courseId = (Long)o[3];
                Hashtable<Long, Set<Long>> course2students = owner2course2students.get(ownerId);
                if (course2students == null) {
                	course2students = new Hashtable<Long, Set<Long>>();
                	owner2course2students.put(ownerId, course2students);
                }
                Set<Long> studentsOfCourse = course2students.get(courseId);
                if (studentsOfCourse == null) {
                	studentsOfCourse = new HashSet<Long>();
                	course2students.put(courseId, studentsOfCourse);
                }
                studentsOfCourse.add(studentId);
            }
            sLog.info("  Loading students (offering)...");
            for (Object[] o: ExamDAO.getInstance().getSession().createQuery(
                        "select x.uniqueId, o.uniqueId, e.student.uniqueId, e.courseOffering.uniqueId from "+
                        "Exam x inner join x.owners o, "+
                        "StudentClassEnrollment e inner join e.courseOffering.instructionalOffering io " +
                        "where x.session.uniqueId=:sessionId and x.examType.uniqueId=:examTypeId and "+
                        "o.ownerType="+org.unitime.timetable.model.ExamOwner.sOwnerTypeOffering+" and "+
                        "o.ownerId=io.uniqueId", Object[].class)
            		.setParameter("sessionId", sessionId)
            		.setParameter("examTypeId", examTypeId).setCacheable(true).list()) {
                Long examId = (Long)o[0];
                Long ownerId = (Long)o[1];
                Long studentId = (Long)o[2];
                Set<Long> studentsOfOwner = owner2students.get(ownerId);
                if (studentsOfOwner==null) {
                    studentsOfOwner = new HashSet<Long>();
                    owner2students.put(ownerId, studentsOfOwner);
                }
                studentsOfOwner.add(studentId);
                Set<Exam> examsOfStudent = student2exams.get(studentId);
                if (examsOfStudent==null) { 
                    examsOfStudent = new HashSet<Exam>();
                    student2exams.put(studentId, examsOfStudent);
                }
                examsOfStudent.add(exams.get(examId));
                Long courseId = (Long)o[3];
                Hashtable<Long, Set<Long>> course2students = owner2course2students.get(ownerId);
                if (course2students == null) {
                	course2students = new Hashtable<Long, Set<Long>>();
                	owner2course2students.put(ownerId, course2students);
                }
                Set<Long> studentsOfCourse = course2students.get(courseId);
                if (studentsOfCourse == null) {
                	studentsOfCourse = new HashSet<Long>();
                	course2students.put(courseId, studentsOfCourse);
                }
                studentsOfCourse.add(studentId);
            }
        }
        Hashtable<Long, Set<Meeting>> period2meetings = new Hashtable();
        ExamType type = ExamTypeDAO.getInstance().get(examTypeId);
        if (assgn && eventConf && ApplicationProperty.ExaminationConsiderEventConflicts.isTrue(type.getReference())) {
            sLog.info("  Loading overlapping class meetings...");
            for (Object[] o: ExamDAO.getInstance().getSession().createQuery(
                    "select p.uniqueId, m from ClassEvent ce inner join ce.meetings m, ExamPeriod p " +
                    "where p.startSlot - :travelTime < m.stopPeriod and m.startPeriod < p.startSlot + p.length + :travelTime and "+
                    HibernateUtil.addDate("p.session.examBeginDate","p.dateOffset")+" = m.meetingDate and p.session.uniqueId=:sessionId and p.examType.uniqueId=:examTypeId", Object[].class)
                    .setParameter("travelTime", ApplicationProperty.ExaminationTravelTimeClass.intValue())
                    .setParameter("sessionId", sessionId).setParameter("examTypeId", examTypeId)
                    .setCacheable(true).list()) {
                Long periodId = (Long)o[0];
                Meeting meeting = (Meeting)o[1];
                Set<Meeting> meetings  = period2meetings.get(periodId);
                if (meetings==null) {
                    meetings = new HashSet(); period2meetings.put(periodId, meetings);
                }
                meetings.add(meeting);
            }
            sLog.info("  Loading overlapping course meetings...");
            for (Object[] o: ExamDAO.getInstance().getSession().createQuery(
                    "select p.uniqueId, m from CourseEvent ce inner join ce.meetings m, ExamPeriod p " +
                    "where ce.reqAttendance=true and m.approvalStatus = 1 and p.startSlot - :travelTime < m.stopPeriod and m.startPeriod < p.startSlot + p.length + :travelTime and "+
                    HibernateUtil.addDate("p.session.examBeginDate","p.dateOffset")+" = m.meetingDate and p.session.uniqueId=:sessionId and p.examType.uniqueId=:examTypeId", Object[].class)
                    .setParameter("travelTime", ApplicationProperty.ExaminationTravelTimeCourse.intValue())
                    .setParameter("sessionId", sessionId).setParameter("examTypeId", examTypeId)
                    .setCacheable(true).list()) {
                Long periodId = (Long)o[0];
                Meeting meeting = (Meeting)o[1];
                Set<Meeting> meetings  = period2meetings.get(periodId);
                if (meetings==null) {
                    meetings = new HashSet(); period2meetings.put(periodId, meetings);
                }
                meetings.add(meeting);
            }
            sLog.info("  Loading overlapping examinations of different problems...");
            for (Object[] o: ExamDAO.getInstance().getSession().createQuery(
                    "select p.uniqueId, m from ExamEvent ce inner join ce.meetings m, ExamPeriod p " +
                    "where ce.exam.examType.uniqueId != :examTypeId and m.approvalStatus = 1 and p.startSlot - :travelTime < m.stopPeriod and m.startPeriod < p.startSlot + p.length + :travelTime and "+
                    HibernateUtil.addDate("p.session.examBeginDate","p.dateOffset")+" = m.meetingDate and p.session.uniqueId=:sessionId and p.examType.uniqueId=:examTypeId", Object[].class)
                    .setParameter("travelTime", ApplicationProperty.ExaminationTravelTimeCourse.intValue())
                    .setParameter("sessionId", sessionId).setParameter("examTypeId", examTypeId)
                    .setCacheable(true).list()) {
                Long periodId = (Long)o[0];
                Meeting meeting = (Meeting)o[1];
                Set<Meeting> meetings  = period2meetings.get(periodId);
                if (meetings==null) {
                    meetings = new HashSet(); period2meetings.put(periodId, meetings);
                }
                meetings.add(meeting);
            }
        }
        Parameters p = new Parameters(sessionId, examTypeId);
        sLog.info("  Creating exam assignments...");
        TreeSet<ExamAssignmentInfo> ret = new TreeSet();
        for (Enumeration<Exam> e = exams.elements(); e.hasMoreElements();) {
            Exam exam = (Exam)e.nextElement();
            ExamAssignmentInfo info = (assgn?new ExamAssignmentInfo(exam, owner2students, owner2course2students, student2exams, period2meetings, p):new ExamAssignmentInfo(exam, (ExamPeriod)null, null));
            for (ExamSectionInfo section: info.getSections()) {
            	if (section.getOwnerType() != ExamOwner.sOwnerTypeClass) continue;
            	ClassEvent evt = classEvents.get(section.getOwnerId());
            	if (evt != null) ((Class_)section.getOwner().getOwnerObject()).setEvent(evt);
            }
        	if (ignNoEnrl && info.getStudentIds().isEmpty()) continue;
            ret.add(info);
        }
        long t1 = System.currentTimeMillis();
        sLog.info("Exams loaded in "+sDF.format((t1-t0)/1000.0)+"s.");
        return ret;
    }
    
	public static void main(String[] args) {
        try {
            HibernateUtil.configureHibernate(ApplicationProperties.getProperties());
            
            Session session = Session.getSessionUsingInitiativeYearTerm(
                    ApplicationProperties.getProperty("initiative", "PWL"),
                    ApplicationProperties.getProperty("year","2021"),
                    ApplicationProperties.getProperty("term","Spring")
                    );
            if (session==null) {
                sLog.error("Academic session not found, use properties initiative, year, and term to set academic session.");
                System.exit(0);
            } else {
                sLog.info("Session: "+session);
            }
            ExamType examType = ExamType.findByReference(ApplicationProperties.getProperty("type","final"));
            boolean assgn = "true".equals(System.getProperty("assgn","true"));
            boolean ignempty = "true".equals(System.getProperty("ignempty","true"));
            int mode = Mode.LegacyPdfLetter.ordinal();
            if ("text".equals(System.getProperty("mode"))) mode = Mode.LegacyText.ordinal();
            if ("ledger".equals(System.getProperty("mode"))) mode = Mode.LegacyPdfLedger.ordinal();
            if ("csv".equals(System.getProperty("mode"))) mode = Mode.CSV.ordinal();
            if ("pdf".equals(System.getProperty("mode"))) mode = Mode.PDF.ordinal();
            if ("xls".equals(System.getProperty("mode"))) mode = Mode.XLS.ordinal();
            sLog.info("Exam type: " + examType.getLabel());
            boolean perSubject = "true".equals(System.getProperty("persubject","false"));
            TreeSet<SubjectArea> subjects = null;
            if (System.getProperty("subject")!=null) {
                sLog.info("Loading subjects...");
                subjects = new TreeSet();
                String inSubjects = "";
                for (StringTokenizer s=new StringTokenizer(System.getProperty("subject"),",");s.hasMoreTokens();)
                    inSubjects += "'"+s.nextToken()+"'"+(s.hasMoreTokens()?",":"");
                subjects.addAll(new _RootDAO().getSession().createQuery(
                        "select sa from SubjectArea sa where sa.session.uniqueId=:sessionId and sa.subjectAreaAbbreviation in ("+inSubjects+")", SubjectArea.class
                        ).setParameter("sessionId", session.getUniqueId()).list());
            }
            TreeSet<ExamAssignmentInfo> exams = loadExams(session.getUniqueId(), examType.getUniqueId(), assgn, ignempty, true);
            if (subjects==null) {
                subjects = new TreeSet();
                for (ExamAssignmentInfo exam: exams)
                    for (ExamSectionInfo section: exam.getSections())
                        subjects.add(section.getOwner().getCourse().getSubjectArea());
            }
            /*
            if (subjects==null) {
                if (perSubject) examsPerSubj = new Hashtable();
                for (Iterator i=Exam.findAll(session.getUniqueId(),examType).iterator();i.hasNext();) {
                    ExamAssignmentInfo exam = (assgn?new ExamAssignmentInfo((Exam)i.next()):new ExamAssignmentInfo((Exam)i.next(),null,null,null,null));
                    exams.add(exam);
                    if (perSubject) {
                        HashSet<SubjectArea> sas = new HashSet<SubjectArea>();
                        for (Iterator j=exam.getExam().getOwners().iterator();j.hasNext();) {
                            ExamOwner owner = (ExamOwner)j.next();
                            SubjectArea sa = owner.getCourse().getSubjectArea();
                            if (!sas.add(sa)) continue;
                            Vector<ExamAssignmentInfo> x = examsPerSubj.get(sa);
                            if (x==null) { x = new Vector(); examsPerSubj.put(sa,x); }
                            x.add(exam);
                        }
                    }
                }
            } else for (SubjectArea subject : subjects) {
                Vector<ExamAssignmentInfo> examsOfThisSubject = new Vector();
                for (Iterator i=Exam.findExamsOfSubjectArea(subject.getUniqueId(),examType).iterator();i.hasNext();) {
                    ExamAssignmentInfo exam = (assgn?new ExamAssignmentInfo((Exam)i.next()):new ExamAssignmentInfo((Exam)i.next(),null,null,null,null)); 
                    exams.add(exam);
                    examsOfThisSubject.add(exam);
                }
                examsPerSubj.put(subject, examsOfThisSubject);
            }
            */
            Hashtable<String,File> output = new Hashtable();
            Hashtable<SubjectArea,Hashtable<String,File>> outputPerSubject = new Hashtable();
            Hashtable<ExamInstructorInfo,File> ireports = null;
            Hashtable<Student,File> sreports = null;
            for (StringTokenizer stk=new StringTokenizer(ApplicationProperties.getProperty("report",sAllRegisteredReports),",");stk.hasMoreTokens();) {
                String reportName = stk.nextToken();
                Class reportClass = sRegisteredReports.get(reportName);
                if (reportClass==null) continue;
                sLog.info("Report: "+reportClass.getName().substring(reportClass.getName().lastIndexOf('.')+1));
                if (perSubject) {
                    for (SubjectArea subject : subjects) {
                        File file = new File(new File(ApplicationProperties.getProperty("output",".")),
                            session.getAcademicTerm()+session.getSessionStartYear()+examType.getReference()+"_"+reportName+"_"+subject.getSubjectAreaAbbreviation()+getExtension(mode));
                        long t0 = System.currentTimeMillis();
                        sLog.info("Generating report "+file+" ("+subject.getSubjectAreaAbbreviation()+") ...");
                        List<SubjectArea> subjectList = new ArrayList<SubjectArea>(); subjectList.add(subject);
                        PdfLegacyExamReport report = (PdfLegacyExamReport)reportClass.getConstructor(int.class, File.class, Session.class, ExamType.class, Collection.class, Collection.class).newInstance(mode, file, session, examType, subjectList, exams);
                        report.printReport();
                        report.close();
                        output.put(subject.getSubjectAreaAbbreviation()+"_"+reportName+getExtension(mode),file);
                        Hashtable<String,File> files = outputPerSubject.get(subject);
                        if (files==null) {
                            files = new Hashtable(); outputPerSubject.put(subject,files);
                        }
                        files.put(subject.getSubjectAreaAbbreviation()+"_"+reportName+getExtension(mode),file);
                        long t1 = System.currentTimeMillis();
                        sLog.info("Report "+file+" generated in "+sDF.format((t1-t0)/1000.0)+"s.");
                        if (report instanceof InstructorExamReport && "true".equals(System.getProperty("email.instructors","false"))) {
                            ireports = ((InstructorExamReport)report).printInstructorReports(
                                    session.getAcademicTerm()+session.getSessionStartYear()+examType.getReference(), new InstructorExamReport.FileGenerator() {
                                        public File generate(String prefix, String ext) {
                                            int idx = 0;
                                            File file = new File(prefix+"."+ext);
                                            while (file.exists()) {
                                                idx++;
                                                file = new File(prefix+"_"+idx+"."+ext);
                                            }
                                            return file;
                                        }
                                    });
                        } else if (report instanceof StudentExamReport && "true".equals(System.getProperty("email.students","false"))) {
                            sreports = ((StudentExamReport)report).printStudentReports(
                                    session.getAcademicTerm()+session.getSessionStartYear()+examType.getReference(), new InstructorExamReport.FileGenerator() {
                                        public File generate(String prefix, String ext) {
                                            int idx = 0;
                                            File file = new File(prefix+"."+ext);
                                            while (file.exists()) {
                                                idx++;
                                                file = new File(prefix+"_"+idx+"."+ext);
                                            }
                                            return file;
                                        }
                                    });
                        }
                    }
                } else {
                    File file = new File(new File(ApplicationProperties.getProperty("output",".")),
                            session.getAcademicTerm()+session.getSessionStartYear()+examType.getReference()+"_"+reportName+getExtension(mode));
                    long t0 = System.currentTimeMillis();
                    sLog.info("Generating report "+file+" ...");
                    PdfLegacyExamReport report = (PdfLegacyExamReport)reportClass.getConstructor(int.class, File.class, Session.class, ExamType.class, Collection.class, Collection.class).newInstance(mode, file, session, examType, subjects, exams);
                    report.printReport();
                    report.close();
                    output.put(reportName+getExtension(mode),file);
                    long t1 = System.currentTimeMillis();
                    sLog.info("Report "+file.getName()+" generated in "+sDF.format((t1-t0)/1000.0)+"s.");
                    if (report instanceof InstructorExamReport && "true".equals(System.getProperty("email.instructors","false"))) {
                        ireports = ((InstructorExamReport)report).printInstructorReports(
                               session.getAcademicTerm()+session.getSessionStartYear()+examType.getReference(), new InstructorExamReport.FileGenerator() {
                                    public File generate(String prefix, String ext) {
                                        int idx = 0;
                                        File file = new File(prefix+"."+ext);
                                        while (file.exists()) {
                                            idx++;
                                            file = new File(prefix+"_"+idx+"."+ext);
                                        }
                                        return file;
                                    }
                                });
                    } else if (report instanceof StudentExamReport && "true".equals(System.getProperty("email.students","false"))) {
                        sreports = ((StudentExamReport)report).printStudentReports(
                                session.getAcademicTerm()+session.getSessionStartYear()+examType.getReference(), new InstructorExamReport.FileGenerator() {
                                    public File generate(String prefix, String ext) {
                                        int idx = 0;
                                        File file = new File(prefix+"."+ext);
                                        while (file.exists()) {
                                            idx++;
                                            file = new File(prefix+"_"+idx+"."+ext);
                                        }
                                        return file;
                                    }
                                });
                    }
                }
            }
            if ("true".equals(System.getProperty("email","false"))) {
                sendEmails(session.getAcademicTerm()+session.getSessionStartYear()+examType.getReference(), output, outputPerSubject, ireports, sreports);
            }
            sLog.info("All done.");
        } catch (Exception e) {
            sLog.error(e.getMessage(),e);
        }
    }

}
