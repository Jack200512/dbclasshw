package com.dbclasshw.document;

import org.apache.ibatis.annotations.*;
import java.util.List;

@Mapper
public interface PermissionMapper {
    @Select("SELECT role FROM document_permissions WHERE document_id=#{documentId} AND user_id=#{userId} FOR UPDATE")
    String lockRole(@Param("documentId") long documentId, @Param("userId") long userId);

    @Select("SELECT p.id,p.user_id,u.username,u.email,p.role,p.created_at,p.updated_at FROM document_permissions p JOIN users u ON u.id=p.user_id WHERE p.document_id=#{documentId} ORDER BY p.id")
    List<Permission> findAll(long documentId);

    @Insert("INSERT INTO document_permissions(document_id,user_id,role) VALUES(#{documentId},#{userId},#{role})")
    int insert(@Param("documentId") long documentId,@Param("userId") long userId,@Param("role") String role);

    @Update("UPDATE document_permissions SET role=#{role} WHERE document_id=#{documentId} AND user_id=#{userId}")
    int update(@Param("documentId") long documentId,@Param("userId") long userId,@Param("role") String role);

    @Delete("DELETE FROM document_permissions WHERE document_id=#{documentId} AND user_id=#{userId}")
    int delete(@Param("documentId") long documentId,@Param("userId") long userId);
}
