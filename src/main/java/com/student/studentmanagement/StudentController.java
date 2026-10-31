package com.student.studentmanagement;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

import org.springframework.data.web.PagedModel;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/students")
public class StudentController {

    private final StudentRepository studentRepository;

    private static final Set<String> ALLOWED_SORT_FIELDS =
            Set.of("id", "name", "email", "course", "age");

    public StudentController(StudentRepository studentRepository) {
        this.studentRepository = studentRepository;
    }

    // ADMIN: Create a student record
    @PostMapping
    public ResponseEntity<Student> createStudent(
            @Valid @RequestBody Student student) {

        Student savedStudent = studentRepository.save(student);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(savedStudent);
    }

    // ADMIN: Get all student records
    @GetMapping
    public ResponseEntity<List<Student>> getAllStudents() {

        return ResponseEntity.ok(studentRepository.findAll());
    }

    // ADMIN: Search, sort and paginate student records
    @GetMapping("/search")
    public ResponseEntity<?> searchStudents(
            @RequestParam(defaultValue = "") String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "5") int size,
            @RequestParam(defaultValue = "id") String sortBy,
            @RequestParam(defaultValue = "asc") String direction,
            HttpServletRequest request) {

        if (page < 0 || size < 1 || size > 100) {
            return badRequest(
                    "Page must be non-negative and size must be between 1 and 100.",
                    request
            );
        }

        if (!ALLOWED_SORT_FIELDS.contains(sortBy)) {
            return badRequest(
                    "Invalid sortBy field. Allowed fields: id, name, email, course, age.",
                    request
            );
        }

        if (!direction.equalsIgnoreCase("asc")
                && !direction.equalsIgnoreCase("desc")) {
            return badRequest(
                    "Invalid sort direction. Use asc or desc.",
                    request
            );
        }

        Sort sort = direction.equalsIgnoreCase("desc")
                ? Sort.by(sortBy).descending()
                : Sort.by(sortBy).ascending();

        Pageable pageable = PageRequest.of(page, size, sort);

        Page<Student> students =
                studentRepository.searchStudents(keyword, pageable);

        return ResponseEntity.ok(new PagedModel<>(students));
    }

    // ADMIN: Get a student by ID
    @GetMapping("/{id}")
    public ResponseEntity<Student> getStudentById(
            @PathVariable Long id) {

        Student student = studentRepository.findById(id)
                .orElseThrow(() -> new StudentNotFoundException(id));

        return ResponseEntity.ok(student);
    }

    // ADMIN: Update any student record
    @PutMapping("/{id}")
    public ResponseEntity<Student> updateStudent(
            @PathVariable Long id,
            @Valid @RequestBody Student updatedStudent) {

        Student existingStudent = studentRepository.findById(id)
                .orElseThrow(() -> new StudentNotFoundException(id));

        copyStudentDetails(existingStudent, updatedStudent);

        Student savedStudent = studentRepository.save(existingStudent);

        return ResponseEntity.ok(savedStudent);
    }

    // ADMIN: Delete any student record
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteStudent(
            @PathVariable Long id) {

        Student student = studentRepository.findById(id)
                .orElseThrow(() -> new StudentNotFoundException(id));

        studentRepository.delete(student);

        return ResponseEntity.noContent().build();
    }

    // STUDENT: View only their own profile
    @GetMapping("/me")
    public ResponseEntity<Student> getMyProfile(
            @AuthenticationPrincipal UserDetails userDetails) {

        return studentRepository.findByOwner_Username(
                        userDetails.getUsername())
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    // STUDENT: Update only their own profile
    @PutMapping("/me")
    public ResponseEntity<?> updateMyProfile(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody Student updatedStudent) {

        return studentRepository.findByOwner_Username(
                        userDetails.getUsername())
                .map(existingStudent -> {

                    copyStudentDetails(existingStudent, updatedStudent);

                    Student savedStudent =
                            studentRepository.save(existingStudent);

                    return ResponseEntity.ok(savedStudent);
                })
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    // Copy editable student information without changing ownership.
    private void copyStudentDetails(
            Student existingStudent,
            Student updatedStudent) {

        existingStudent.setName(updatedStudent.getName());
        existingStudent.setEmail(updatedStudent.getEmail());
        existingStudent.setCourse(updatedStudent.getCourse());
        existingStudent.setAge(updatedStudent.getAge());
        existingStudent.setGender(updatedStudent.getGender());
    }

    // Standard JSON error for invalid search parameters
    private ResponseEntity<Map<String, Object>> badRequest(
            String message,
            HttpServletRequest request) {

        Map<String, Object> error = new LinkedHashMap<>();

        error.put("timestamp", LocalDateTime.now());
        error.put("status", HttpStatus.BAD_REQUEST.value());
        error.put("error", HttpStatus.BAD_REQUEST.getReasonPhrase());
        error.put("message", message);
        error.put("path", request.getRequestURI());

        return ResponseEntity.badRequest().body(error);
    }
}

