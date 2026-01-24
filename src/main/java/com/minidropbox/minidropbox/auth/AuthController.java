package com.minidropbox.minidropbox.auth;

import java.util.Map;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.minidropbox.minidropbox.util.JwtUtil;
    
@RestController
@RequestMapping("/auth")
public class AuthController {

    private final UserRepository userRepo;
    private final PasswordEncoder encoder;
    private final JwtUtil jwtUtil;

    public AuthController(UserRepository userRepo,
                          PasswordEncoder encoder,
                          JwtUtil jwtUtil) {
        this.userRepo = userRepo;
        this.encoder = encoder;
        this.jwtUtil = jwtUtil;
    }

    @PostMapping("/register")
    public String register(@RequestBody Map<String, String> req) {
        User user = new User();
        user.setEmail(req.get("email"));
        user.setPassword(encoder.encode(req.get("password")));
        user.setRole("USER");
        userRepo.save(user);
        return "User registered";
    }

    @PostMapping("/login")
    public String login(@RequestBody Map<String, String> req) {
        User user = userRepo.findByEmail(req.get("email"))
                .orElseThrow(() -> new RuntimeException("User not found"));

        if (!encoder.matches(req.get("password"), user.getPassword())) {
            throw new RuntimeException("Invalid password");
        }

        return jwtUtil.generateToken(user.getEmail());
    }
}

