package com.chauhan.authservice.service;

import com.chauhan.authservice.dto.UserDto;

public interface AuthService {
    UserDto registerUser(UserDto userDto);

}