package com.dbclasshw.document;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface DocumentMapper {
    String COLUMNS = "id, owner_id, title, content, version, created_at, updated_at";

    @Insert("INSERT INTO documents (owner_id, title, content) VALUES (#{ownerId}, #{title}, #{content})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(Document document);

    @Select("SELECT " + COLUMNS + " FROM documents WHERE owner_id=#{ownerId} ORDER BY id")
    List<Document> findAll(long ownerId);

    @Select("SELECT " + COLUMNS + " FROM documents WHERE id = #{id} AND owner_id=#{ownerId}")
    Document findById(@org.apache.ibatis.annotations.Param("id") long id,
                      @org.apache.ibatis.annotations.Param("ownerId") long ownerId);
}
