package org.example.aispingboot.enumClass;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class UserStatusTest {

    @Test
    void isValidCode_shouldReturnTrue_whenCodeIs0() {
        // 0 = 禁用，是合法状态码
        assertTrue(UserStatus.isValidCode(0));
    }

    @Test
    void isValidCode_shouldReturnTrue_whenCodeIs1() {
        // 1 = 正常，是合法状态码
        assertTrue(UserStatus.isValidCode(1));
    }

    @Test
    void isValidCode_shouldReturnFalse_whenCodeIs99() {
        // 99 不在枚举里，是非法状态码
        assertFalse(UserStatus.isValidCode(99));
    }
}
