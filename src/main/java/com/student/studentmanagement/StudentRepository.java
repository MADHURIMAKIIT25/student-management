package com.student.studentmanagement;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface StudentRepository
        extends JpaRepository<Student, Long> {

    @Query("""
        SELECT s FROM Student s
        WHERE LOWER(s.name) LIKE LOWER(CONCAT('%', :keyword, '%'))
           OR LOWER(s.email) LIKE LOWER(CONCAT('%', :keyword, '%'))
           OR LOWER(s.course) LIKE LOWER(CONCAT('%', :keyword, '%'))
        """)
    Page<Student> searchStudents(
            @Param("keyword") String keyword,
            Pageable pageable
    );

    // Find a student's profile using the owner's username
    Optional<Student> findByOwner_Username(String username);

    // Prevent duplicate student email registrations
    boolean existsByEmailIgnoreCase(String email);
}
