package edu.ccrm.service;

import edu.ccrm.domain.Student;
import edu.ccrm.domain.Course;
import edu.ccrm.domain.Enrollment;
import edu.ccrm.domain.Grade;
import edu.ccrm.exception.DuplicateEnrollmentException;
import edu.ccrm.exception.MaxCreditLimitExceededException;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Service class for student management
 * Demonstrates service layer pattern and Stream API usage
 */
public class StudentService {
    private final Map<String, Student> students;
    private final Map<String, Course> courses;
    private final Map<String, List<Enrollment>> enrollments;
    private final int maxCreditsPerSemester;

    public StudentService(int maxCreditsPerSemester) {
        this.students = new HashMap<>();
        this.courses = new HashMap<>();
        this.enrollments = new HashMap<>();
        this.maxCreditsPerSemester = maxCreditsPerSemester;
    }

    public void addStudent(Student student) {
        students.put(student.getId(), student);
        enrollments.put(student.getId(), new ArrayList<>());
    }

    public Student getStudent(String studentId) {
        return students.get(studentId);
    }

    public List<Student> getAllStudents() {
        return new ArrayList<>(students.values());
    }

    public List<Student> getActiveStudents() {
        return students.values().stream()
                .filter(Student::isActive)
                .collect(Collectors.toList());
    }

    public void enrollStudent(String studentId, String courseCode) 
            throws DuplicateEnrollmentException, MaxCreditLimitExceededException {
        
        Student student = students.get(studentId);
        Course course = courses.get(courseCode);
        
        if (student == null) {
            throw new IllegalArgumentException("Student not found: " + studentId);
        }
        if (course == null) {
            throw new IllegalArgumentException("Course not found: " + courseCode);
        }

        // Check for duplicate enrollment
        List<Enrollment> studentEnrollments = enrollments.get(studentId);
        boolean alreadyEnrolled = studentEnrollments.stream()
                .anyMatch(e -> e.getCourseCode().equals(courseCode) && e.isActive());
        
        if (alreadyEnrolled) {
            throw new DuplicateEnrollmentException("Student already enrolled in course: " + courseCode);
        }

        // Check credit limit
        int currentCredits = studentEnrollments.stream()
                .filter(Enrollment::isActive)
                .mapToInt(e -> courses.get(e.getCourseCode()).getCredits())
                .sum();
        
        if (currentCredits + course.getCredits() > maxCreditsPerSemester) {
            throw new MaxCreditLimitExceededException(
                String.format("Credit limit exceeded. Current: %d, Adding: %d, Max: %d", 
                             currentCredits, course.getCredits(), maxCreditsPerSemester));
        }

        // Create enrollment
        Enrollment enrollment = new Enrollment(studentId, courseCode);
        studentEnrollments.add(enrollment);
        student.enrollInCourse(courseCode);
    }

    public void unenrollStudent(String studentId, String courseCode) {
        List<Enrollment> studentEnrollments = enrollments.get(studentId);
        if (studentEnrollments != null) {
            studentEnrollments.stream()
                    .filter(e -> e.getCourseCode().equals(courseCode) && e.isActive())
                    .findFirst()
                    .ifPresent(e -> {
                        e.setActive(false);
                        students.get(studentId).unenrollFromCourse(courseCode);
                    });
        }
    }

    public void recordGrade(String studentId, String courseCode, double marks) {
        List<Enrollment> studentEnrollments = enrollments.get(studentId);
        if (studentEnrollments != null) {
            studentEnrollments.stream()
                    .filter(e -> e.getCourseCode().equals(courseCode) && e.isActive())
                    .findFirst()
                    .ifPresent(e -> e.recordGrade(marks));
            
            // Update student GPA
            updateStudentGPA(studentId);
        }
    }

    private void updateStudentGPA(String studentId) {
        List<Enrollment> studentEnrollments = enrollments.get(studentId);
        if (studentEnrollments != null) {
            double totalPoints = studentEnrollments.stream()
                    .filter(Enrollment::isActive)
                    .filter(e -> e.getGrade() != null)
                    .mapToDouble(e -> {
                        Course course = courses.get(e.getCourseCode());
                        return e.getGrade().getPoints() * course.getCredits();
                    })
                    .sum();
            
            int totalCredits = studentEnrollments.stream()
                    .filter(Enrollment::isActive)
                    .filter(e -> e.getGrade() != null)
                    .mapToInt(e -> courses.get(e.getCourseCode()).getCredits())
                    .sum();
            
            if (totalCredits > 0) {
                double gpa = totalPoints / totalCredits;
                students.get(studentId).setGpa(gpa);
            }
        }
    }

    public String generateTranscript(String studentId) {
        Student student = students.get(studentId);
        if (student == null) {
            return "Student not found: " + studentId;
        }

        StringBuilder transcript = new StringBuilder();
        transcript.append("=== TRANSCRIPT ===\n");
        transcript.append("Student: ").append(student.getFullName()).append("\n");
        transcript.append("Registration No: ").append(student.getRegNo()).append("\n");
        transcript.append("GPA: ").append(String.format("%.2f", student.getGpa())).append("\n\n");
        
        transcript.append("Course Records:\n");
        transcript.append("Code\tTitle\t\t\tCredits\tGrade\tMarks\n");
        transcript.append("----\t-----\t\t\t-------\t-----\t-----\n");
        
        List<Enrollment> studentEnrollments = enrollments.get(studentId);
        if (studentEnrollments != null) {
            studentEnrollments.stream()
                    .filter(Enrollment::isActive)
                    .forEach(e -> {
                        Course course = courses.get(e.getCourseCode());
                        transcript.append(String.format("%s\t%-20s\t%d\t%s\t%.1f\n",
                                course.getCode(),
                                course.getTitle(),
                                course.getCredits(),
                                e.getGrade() != null ? e.getGrade().getLetter() : "N/A",
                                e.getMarks()));
                    });
        }
        
        return transcript.toString();
    }

    public void addCourse(Course course) {
        courses.put(course.getCode(), course);
    }

    public Course getCourse(String courseCode) {
        return courses.get(courseCode);
    }

    public List<Course> getAllCourses() {
        return new ArrayList<>(courses.values());
    }

    public List<Course> searchCoursesByInstructor(String instructorId) {
        return courses.values().stream()
                .filter(c -> c.getInstructorId().equals(instructorId))
                .collect(Collectors.toList());
    }

    public List<Course> searchCoursesByDepartment(String department) {
        return courses.values().stream()
                .filter(c -> c.getDepartment().equalsIgnoreCase(department))
                .collect(Collectors.toList());
    }

    public List<Course> searchCoursesBySemester(edu.ccrm.domain.Semester semester) {
        return courses.values().stream()
                .filter(c -> c.getSemester().equals(semester))
                .collect(Collectors.toList());
    }
}



