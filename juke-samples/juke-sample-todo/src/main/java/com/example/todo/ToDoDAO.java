package com.example.todo;

import org.juke.framework.annotation.Juke;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * DAO / service-boundary layer for ToDo operations.
 *
 * <p>The {@code @Juke} annotation on the {@code todoService}
 * field tells the framework to intercept every call made through this
 * reference:
 * <ul>
 *   <li><b>record</b> mode — calls pass through to {@link ToDoServiceImpl}
 *       and every request/response pair is captured to a ZIP track</li>
 *   <li><b>replay</b> mode — the real service is never invoked; Juke
 *       returns recorded responses</li>
 *   <li><b>passthrough</b> mode — Juke is transparent; calls go straight
 *       to the real service with no recording or replay</li>
 * </ul>
 *
 * <p>This class is the single integration point between the REST layer
 * and the service layer — easy to reason about what Juke is recording.
 */
@Service
public class ToDoDAO {

    @Juke
    IToDoService todoService;

    @Autowired
    public ToDoDAO(IToDoService todoService) {
        this.todoService = todoService;
    }

    public List<ToDo> getAll() {
        return todoService.getAllTodos();
    }

    public Optional<ToDo> getById(long id) {
        return todoService.getTodoById(id);
    }

    public ToDo create(String title) {
        return todoService.createTodo(title);
    }

    public ToDo update(long id, String title, boolean completed) {
        return todoService.updateTodo(id, title, completed);
    }

    public boolean delete(long id) {
        return todoService.deleteTodo(id);
    }

    public List<ToDo> getCompleted() {
        return todoService.getCompletedTodos();
    }

    public List<ToDo> getPending() {
        return todoService.getPendingTodos();
    }
}
