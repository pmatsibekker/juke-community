package com.example.todo;

import org.juke.framework.annotation.JukeController;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * REST controller for the ToDo resource.
 *
 * <p>Endpoints:
 * <pre>
 *   GET    /todos            list all
 *   GET    /todos/{id}       get by id
 *   POST   /todos            create  { "title": "..." }
 *   PUT    /todos/{id}       update  { "title": "...", "completed": true }
 *   DELETE /todos/{id}       delete
 *   GET    /todos/completed  list completed
 *   GET    /todos/pending    list pending
 * </pre>
 */
@JukeController
@RestController
@RequestMapping("/todos")
public class ToDoController {

    @Autowired
    private ToDoDAO toDoDAO;

    @GetMapping
    public List<ToDo> getAll() {
        return toDoDAO.getAll();
    }

    @GetMapping("/{id}")
    public ResponseEntity<ToDo> getById(@PathVariable long id) {
        return toDoDAO.getById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<ToDo> create(@RequestBody Map<String, String> body) {
        String title = body.get("title");
        if (title == null || title.isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        ToDo created = toDoDAO.create(title.trim());
        return ResponseEntity.ok(created);
    }

    @PutMapping("/{id}")
    public ResponseEntity<ToDo> update(@PathVariable long id,
                                       @RequestBody Map<String, Object> body) {
        return toDoDAO.getById(id)
                .map(existing -> {
                    String title = body.containsKey("title")
                            ? (String) body.get("title")
                            : existing.getTitle();
                    boolean completed = body.containsKey("completed")
                            ? Boolean.parseBoolean(body.get("completed").toString())
                            : existing.isCompleted();
                    return ResponseEntity.ok(toDoDAO.update(id, title, completed));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable long id) {
        return toDoDAO.delete(id)
                ? ResponseEntity.noContent().<Void>build()
                : ResponseEntity.notFound().build();
    }

    @GetMapping("/completed")
    public List<ToDo> getCompleted() {
        return toDoDAO.getCompleted();
    }

    @GetMapping("/pending")
    public List<ToDo> getPending() {
        return toDoDAO.getPending();
    }
}
