package com.chauhan.authservice.auth.services.impl;

import com.chauhan.authservice.auth.payload.UserDto;
import com.chauhan.authservice.auth.services.AuthService;
import com.chauhan.authservice.auth.services.UserService;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;


@Service
@AllArgsConstructor
public class AuthServiceImpl implements AuthService {
    private final UserService userService;

    @Override
    public UserDto registerUser(UserDto userDto) {

        return  userService.createUser(userDto);
    }
}
