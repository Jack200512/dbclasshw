package com.dbclasshw.document;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface DocumentMapper {
    String COLUMNS = "id, title, content, version, created_at, updated_at";

    @Insert("INSERT INTO documents (title, content) VALUES (#{title}, #{content})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(Document document);

    @Select("SELECT " + COLUMNS + " FROM documents ORDER BY id")
    List<Document> findAll();

    @Select("SELECT " + COLUMNS + " FROM documents WHERE id = #{id}")
    Document findById(long id);
}
