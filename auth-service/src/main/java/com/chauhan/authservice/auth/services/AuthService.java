package com.chauhan.authservice.auth.services;

import com.chauhan.authservice.auth.payload.UserDto;

public interface AuthService {
    UserDto registerUser(UserDto userDto);

}
