package com.example.todo;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * In-memory implementation of {@link IToDoService}. In production this
 * would delegate to a repository / database — the point of the sample
 * is that Juke records the calls regardless of what's underneath.
 */
@Service
public class ToDoServiceImpl implements IToDoService {

    private final Map<Long, ToDo> store = new ConcurrentHashMap<>();
    private final AtomicLong idSequence = new AtomicLong(1);

    @Override
    public List<ToDo> getAllTodos() {
        return new ArrayList<>(store.values());
    }

    @Override
    public Optional<ToDo> getTodoById(long id) {
        return Optional.ofNullable(store.get(id));
    }

    @Override
    public ToDo createTodo(String title) {
        long id = idSequence.getAndIncrement();
        ToDo todo = new ToDo(id, title, false);
        store.put(id, todo);
        return todo;
    }

    @Override
    public ToDo updateTodo(long id, String title, boolean completed) {
        ToDo todo = store.get(id);
        if (todo == null) {
            throw new NoSuchElementException("ToDo not found: " + id);
        }
        todo.setTitle(title);
        todo.setCompleted(completed);
        return todo;
    }

    @Override
    public boolean deleteTodo(long id) {
        return store.remove(id) != null;
    }

    @Override
    public List<ToDo> getCompletedTodos() {
        return store.values().stream()
                .filter(ToDo::isCompleted)
                .collect(Collectors.toList());
    }

    @Override
    public List<ToDo> getPendingTodos() {
        return store.values().stream()
                .filter(t -> !t.isCompleted())
                .collect(Collectors.toList());
    }
}
