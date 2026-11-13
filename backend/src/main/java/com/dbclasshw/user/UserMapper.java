package com.dbclasshw.user;

import org.apache.ibatis.annotations.*;

@Mapper
public interface UserMapper {
    @Select("SELECT * FROM users WHERE username=#{login} OR email=#{login}")
    UserAccount findByLogin(String login);

    @Select("SELECT * FROM users WHERE id=#{id}")
    UserAccount findById(long id);

    @Insert("INSERT INTO users(username,email,password_hash) VALUES(#{username},#{email},#{passwordHash})")
    @Options(useGeneratedKeys=true, keyProperty="id")
    int insert(UserAccount user);

    @Update("UPDATE users SET email=#{email} WHERE id=#{id}")
    int updateEmail(@Param("id") long id, @Param("email") String email);
}
