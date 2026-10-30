package com.student.studentmanagement;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

@Entity
@Table(name = "students")
public class Student {

@Id
@GeneratedValue(strategy = GenerationType.IDENTITY)
private Long id;

@NotBlank(message = "Name is required")
@Size(min = 2, max = 100, message = "Name must be between 2 and 100 characters")
private String name;

@NotBlank(message = "Email is required")
@Email(message = "Please enter a valid email address")
private String email;

@NotBlank(message = "Course is required")
private String course;

@PositiveOrZero(message = "Age cannot be negative")
private Integer age;

public Student() {
}

public Student(String name, String email, String course, Integer age) {
    this.name = name;
    this.email = email;
    this.course = course;
    this.age = age;
}

public Long getId() {
    return id;
}

public void setId(Long id) {
    this.id = id;
}

public String getName() {
    return name;
}

public void setName(String name) {
    this.name = name;
}

public String getEmail() {
    return email;
}

public void setEmail(String email) {
    this.email = email;
}

public String getCourse() {
    return course;
}

public void setCourse(String course) {
    this.course = course;
}

public Integer getAge() {
    return age;
}

public void setAge(Integer age) {
    this.age = age;
}

}
