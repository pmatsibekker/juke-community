package com.example.todo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

/**
 * REST CRUD sample. The {@link ToDoController} exposes
 * GET / POST / PUT / DELETE on {@code /todos}. The {@link ToDoDAO}
 * routes every call through a {@code @Juke("juke")}-wrapped service
 * field so every verb's request/response is captured into the active
 * track and replayed on subsequent runs without invoking
 * {@link ToDoServiceImpl}.
 */
@SpringBootApplication
@ComponentScan(basePackages = {"org.juke.framework", "org.juke.remix", "com.example.todo"})
public class TodoApplication {

    public static void main(String[] args) {
        SpringApplication.run(TodoApplication.class, args);
    }
}
