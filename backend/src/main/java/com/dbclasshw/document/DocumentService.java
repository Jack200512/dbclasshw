package com.dbclasshw.document;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class DocumentService {
    private final DocumentMapper mapper;

    public DocumentService(DocumentMapper mapper) {
        this.mapper = mapper;
    }

    @Transactional
    public Document create(CreateDocumentRequest request) {
        Document document = new Document();
        document.setTitle(request.title());
        document.setContent(request.content());
        mapper.insert(document);
        return findById(document.getId());
    }

    public List<Document> findAll() {
        return mapper.findAll();
    }

    public Document findById(long id) {
        Document document = mapper.findById(id);
        if (document == null) throw new DocumentNotFoundException();
        return document;
    }
}
