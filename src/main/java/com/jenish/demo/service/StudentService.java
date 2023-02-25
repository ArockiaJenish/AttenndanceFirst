package com.jenish.demo.service;

import java.io.File;
import java.io.IOException;
import java.sql.Date;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.jenish.demo.dto.LogDto;
import com.jenish.demo.dto.StudentDTO;
import com.jenish.demo.dto.TotalTime;
import com.jenish.demo.model.Attendance;
import com.jenish.demo.model.Student;
import com.jenish.demo.model.TimeLogs;
import com.jenish.demo.repository.AttendanceRepository;
import com.jenish.demo.repository.StudentRepository;
import com.jenish.demo.repository.TimeLogRepository;

@Service
public class StudentService implements StuServiceInterface {

	@Autowired
	StudentRepository stuRepo;

	@Autowired
	TimeLogRepository timeRepo;

	@Autowired
	AttendanceRepository atnRepo;
	
	private static final String FOLDER_PATH = "C:/Jenish/ProjectsPractice/Attendance/src/files/profile/";

	// -----------------Register Student -----------------
	public ResponseEntity<String> registerStudent(Student student) {
		
		try {
			Student exStu = stuRepo.findByEmail(student.getEmail());
			if (exStu == null) {
				if (student.getName() != null && student.getEmail() != null && student.getPassword() != null) {
					Student saved = stuRepo.save(student);
					/*
					 * String filePath = FOLDER_PATH+saved.getId()+".png";
					 * saved.setImagePath(filePath); file.transferTo(new File(filePath).toPath());
					 */
					stuRepo.save(saved);
					return new ResponseEntity<>("Registered Successfully", HttpStatus.OK);
				} else {
					return new ResponseEntity<>("Give required detials", HttpStatus.BAD_REQUEST);
				}
			} else {
				return new ResponseEntity<>("This Student is already Exist", HttpStatus.ALREADY_REPORTED);
			}

		}catch(Exception e) {
			System.out.println(e.getMessage());
			return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Kindly give sutable file..");
		}
	}

	// ------------Login Student-------------
	public StudentDTO login(Student stu) {
		TimeCalculation tc = new TimeCalculation();
		Student exStu = null;
		if (stu.getEmail() != null && stu.getPassword() != null)
			exStu = stuRepo.login(stu.getEmail(), stu.getPassword());
		if (exStu != null) {

			StudentDTO stuDto = new StudentDTO();
			stuDto.setId(exStu.getId());
			stuDto.setEmail(exStu.getEmail());
			stuDto.setName(exStu.getName());

			// ----------For check 'isCheckIn' and get existing login time----------
			List<TimeLogs> logs = timeRepo.findByOrder(getCurrentDate(), exStu.getId());
			Attendance at = atnRepo.stuByDateAndId(getCurrentDate(), exStu.getId());
			TimeLogs l = null;
			if (!logs.isEmpty())
				l = logs.get(0);
			if (l != null) {
				if (l.getCheckOut() == null) {
					stuDto.setCheckIn(true);
					String running = tc.addTimes(at.getWorkedTime(), tc.diffOfTime(l.getCheckIn(), getCurrentTime()));
					stuDto.setLoginTime(running);
				} else {
					stuDto.setCheckIn(false);
					stuDto.setLoginTime(at.getWorkedTime());
				}
			} else
				stuDto.setCheckIn(false);

			return stuDto;
		}
		return new StudentDTO();
	}

	// -------------For check in student----------
	public String checkIn(int id) {
		String totTime = null;
		try {
			stuRepo.findById(id).get();
			Attendance attn = atnRepo.stuByDateAndId(getCurrentDate(), id);// -----------------

			List<TimeLogs> logs = timeRepo.findByOrder(getCurrentDate(), id);// --------------------
			if (!logs.isEmpty()) {
				TimeLogs log = logs.get(0);
				if (log.getCheckOut() == null)
					return "You are already checked in!";
			}

			if (attn == null) {
				Attendance att = new Attendance();
				att.setStuId(id);
				att.setDate(getCurrentDate());// --------------------
				att.setStatus("P");
				att.setLogIn(getCurrentTime());
				att.setWorkedTime("00:00:00");
				updateCheckin(att.getLogIn(), id);
				totTime = atnRepo.save(att).getWorkedTime();
			} else {
				updateCheckin(getCurrentTime(), id);
				totTime = attn.getWorkedTime();
			}

		} catch (NoSuchElementException nse) {
			return "No Student available";
		}
		return totTime;
	}

	private String updateCheckin(String checkInTime, int id) {

		TimeLogs log = new TimeLogs();
		log.setCheckIn(checkInTime);
		log.setCheckInTime("00:00:00");
		log.setDate(getCurrentDate());// --------------------------
		log.setStuId(id);

		return timeRepo.save(log).getCheckIn();
	}

	/*
	 * private Date getDummyDate() {// ---------Just for checking------------ return
	 * Date.valueOf("2022-10-19"); }
	 */

	private Date getCurrentDate() {

		DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy-MM-dd");
		LocalDateTime now = LocalDateTime.now();
		System.out.println(dtf.format(now));
		return Date.valueOf(dtf.format(now));
	}

	private String getCurrentTime() {
		DateTimeFormatter dtf = DateTimeFormatter.ofPattern("HH:mm:ss");
		LocalDateTime now = LocalDateTime.now();
		System.out.println(dtf.format(now));
		return dtf.format(now);
	}

	// -----------For checkOut student-------------
	public String checkOut(int id) {
		String checkOutTime = null;
		try {
			stuRepo.findById(id);
			Attendance att = atnRepo.stuByDateAndId(getCurrentDate(), id);
			if (att != null) {
				List<TimeLogs> tl = timeRepo.findByOrder(getCurrentDate(), id);
				TimeLogs t = tl.get(0);
				if (t.getCheckOut() == null) {
					checkOutTime = updateLogOut(id, att, t);
				} else {
					checkOutTime = "You have already checked out!";
				}
			} else
				checkOutTime = "You have not checked in yet..";
		} catch (NoSuchElementException nse) {
			return "No Student available";
		}
		return checkOutTime;
	}

	private String updateLogOut(int id, Attendance att, TimeLogs t) {

		TimeCalculation tc = new TimeCalculation();

		String workedTime = null;
		String checkinTime = null;
		String currentTime = getCurrentTime();

		String totDayTime = null;
		if (t.getCheckOut() == null) {
			checkinTime = tc.diffOfTime(t.getCheckIn(), currentTime);
			t.setCheckOut(currentTime);
			t.setCheckInTime(checkinTime);
			timeRepo.save(t).getCheckOut();
			workedTime = tc.addTimes(checkinTime, att.getWorkedTime());
			att.setLogOut(getCurrentTime());
			att.setWorkedTime(workedTime);
			totDayTime = atnRepo.save(att).getWorkedTime();
		} else {
			totDayTime = "You have already checked out";
		}
		// }

		return totDayTime;
	}

	// -------------For Total time logs------------
	public List<LogDto> getTimeLogs(int id) {
		TimeCalculation tc = new TimeCalculation();

		List<TimeLogs> logs = timeRepo.findByStuId(id);
		List<LogDto> dto = new ArrayList<LogDto>();

		logs.forEach(t -> {
			LogDto l = new LogDto();
			l.setCheckIn(t.getCheckIn());
			l.setCheckOut(t.getCheckOut());
			l.setDate(t.getDate());
			if (t.getCheckIn() != null && t.getCheckOut() != null)
				l.setTotTime(tc.diffOfTime(t.getCheckIn(), t.getCheckOut()));// getting difference
			dto.add(l);
		});

		return dto;
	}

	// ---------------Calculating group by date logs----------------
	public List<TotalTime> getTotLogTime(int id) {
		TimeCalculation tc = new TimeCalculation();

		List<TotalTime> attnLogs = new ArrayList<TotalTime>();// Empty list of object to store the dto.
		List<Attendance> atns = atnRepo.findByStuId(id);

		for (Attendance a : atns) {
			String totTime = "00:00:00";
			TotalTime tt = new TotalTime();
			List<TimeLogs> logs = timeRepo.findByDateAndId(a.getDate(), id);
			for (TimeLogs l : logs) {
				if (l.getCheckIn() != null && l.getCheckOut() != null)
					totTime = tc.addTimes(totTime, l.getCheckInTime());
			}
			tt.setTimeLog(logs);
			tt.setDate(a.getDate());
			tt.setLoginTime(a.getLogIn());
			tt.setLogoutTime(a.getLogOut());
			tt.setStatus(a.getStatus());
			tt.setWorkedTime(totTime);
			if (a.getLogOut() != null && a.getLogOut().contains(":"))
				tt.setLoggedInTime(tc.diffOfTime(a.getLogIn(), a.getLogOut()));
			else
				tt.setLoggedInTime("00:00:00");
			/*
			 * overAllTime = tc.addTimes(a.getTotLoginTime(), overAllTime);
			 * tt.setOverAllTime(overAllTime);
			 */
			attnLogs.add(tt);
		}

		return attnLogs;
	}

	// ------This is for updating the table------------
	// @Scheduled(cron = "0 58 23 * * ?")
	public String updateRecord() {
		List<Student> stdns = stuRepo.findAll();
		String zeroTime = "00:00:00";
		for (Student s : stdns) {
			List<TimeLogs> logs = timeRepo.findByOrder(getCurrentDate(), s.getId());// To get last one
			TimeLogs l = null;
			if (!logs.isEmpty()) {
				l = logs.get(0);
			} else {
				updateAttendance(s.getId(), getCurrentDate(), "A");// Set as absent
			}
			if (l != null) {
				if (l.getCheckOut() == null) {
					l.setCheckOut("********");
					l.setCheckInTime(zeroTime);
					timeRepo.save(l);
					updateAttendance(s.getId(), l.getDate(), "A");// Set as absent
				} else {
					// updateAttendance(s.getId(), l.getDate());// Set total time in attendance
					// table
					System.out.println("No need to update already updated");
				}
			} else {
				l = new TimeLogs();
				l.setDate(getCurrentDate());
				l.setStuId(s.getId());
				l.setCheckIn(zeroTime);
				l.setCheckOut(zeroTime);
				l.setCheckInTime(zeroTime);
				timeRepo.save(l);
			}
		}

		return "Updated";
	}

	private void updateAttendance(int id, Date logDate, String status) {// Mark as absent

		String emptyTime = "00:00:00";
		Attendance at = atnRepo.stuByDateAndId(logDate, id);

		if (at != null) {
			System.out.println("Status = " + at.getStatus());
			at.setStatus(status);
			at.setLogOut("********");
			at.setWorkedTime(emptyTime);
			atnRepo.save(at);
		} else {
			Attendance a = new Attendance();
			a.setDate(logDate);
			a.setStatus(status);
			a.setStuId(id);
			a.setLogIn(emptyTime);
			a.setLogOut(emptyTime);
			a.setWorkedTime(emptyTime);
			atnRepo.save(a);
		}

	}
	/*
	 * private void updateAttendance(int id, Date logDate) { // calculate total time
	 * for a day
	 * 
	 * String totTime = "00:00:00"; TimeCalculation tc = new TimeCalculation();
	 * 
	 * List<TimeLogs> logs = timeRepo.findByDateAndId(logDate, id);
	 * 
	 * Attendance a = atnRepo.stuByDateAndId(logDate, id);
	 * 
	 * for (TimeLogs log : logs) { totTime = tc.addTimes(log.getCheckInTime(),
	 * totTime); }
	 * 
	 * a.setTotLoginTime(totTime); atnRepo.save(a); }
	 */
}
