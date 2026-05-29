package com.example.Backend_Service;

import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api")
@CrossOrigin
public class UserController {
    private final UserService service;

    public UserController(UserService service) {
        this.service = service;
    }

    @PostMapping("/save")
    public User save(@RequestBody User user) {
        return service.save(user);
    }

    @GetMapping("/fetch")
    public List<User> fetch() {
        return service.getAll();
    }
}