package com.student.studentmanagement;

public class StudentNotFoundException extends RuntimeException {

public StudentNotFoundException(Long id) {
    super("Student not found with ID: " + id);
}

}
