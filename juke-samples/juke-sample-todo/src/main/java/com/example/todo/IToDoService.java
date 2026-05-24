package com.example.todo;

import java.util.List;
import java.util.Optional;

/**
 * Service interface for ToDo CRUD. The Juke proxy intercepts calls
 * through this interface — methods on it are what get recorded and
 * replayed.
 */
public interface IToDoService {
    List<ToDo> getAllTodos();
    Optional<ToDo> getTodoById(long id);
    ToDo createTodo(String title);
    ToDo updateTodo(long id, String title, boolean completed);
    boolean deleteTodo(long id);
    List<ToDo> getCompletedTodos();
    List<ToDo> getPendingTodos();
}
