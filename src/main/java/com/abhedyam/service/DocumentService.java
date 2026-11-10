package com.abhedyam.service;

import com.abhedyam.dto.DocumentCreateRequest;
import com.abhedyam.dto.DocumentOrderItem;
import com.abhedyam.dto.DocumentOrderUpdateRequest;
import com.abhedyam.dto.DocumentResponse;
import com.abhedyam.exception.BusinessException;
import com.abhedyam.exception.ResourceNotFoundException;
import com.abhedyam.model.Document;
import com.abhedyam.repository.DocumentRepository;
import com.abhedyam.service.interfaces.IDocumentService;
import com.abhedyam.util.SecurityUtil;
import com.abhedyam.constants.ErrorCodes;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DocumentService implements IDocumentService {
    
    private final DocumentRepository documentRepository;
    
    @Override
    @Transactional
    public DocumentResponse create(DocumentCreateRequest request) {
        UUID ownerId = SecurityUtil.getCurrentUserId();
        
        List<Document> existingDocuments = documentRepository.findActiveDocumentsByOwnerId(ownerId);
        int maxOrderIndex = existingDocuments.stream()
                .mapToInt(Document::getOrderIndex)
                .max()
                .orElse(-1);
        int newOrderIndex = maxOrderIndex + 1;
        
        Document document = new Document();
        document.setName(request.getName());
        document.setMimeType(request.getMimeType());
        document.setUploadedUrl(request.getUploadedUrl());
        document.setOwnerId(ownerId);
        document.setOrderIndex(newOrderIndex);
        document.setIsActive(true);
        document.setVisibleToCustomers(true);
        
        Document saved = documentRepository.save(document);
        return toResponse(saved);
    }
    
    @Override
    @Transactional(readOnly = true)
    public List<DocumentResponse> getByOwnerId(UUID ownerId) {
        UUID currentUserId = SecurityUtil.getCurrentUserId();
        if (ownerId != null && !ownerId.equals(currentUserId)) {
            throw new BusinessException(ErrorCodes.UNAUTHORIZED, "You can only see your own documents");
        }
        List<Document> documents = documentRepository.findActiveDocumentsByOwnerId(currentUserId);
        
        return documents.stream()
                .sorted((d1, d2) -> Integer.compare(d1.getOrderIndex(), d2.getOrderIndex()))
                .map(this::toResponse)
                .collect(Collectors.toList());
    }
    
    @Override
    @Transactional
    public List<DocumentResponse> updateOrderIndexes(DocumentOrderUpdateRequest request) {
        UUID ownerId = SecurityUtil.getCurrentUserId();
        
        List<DocumentResponse> updatedDocuments = new ArrayList<>();
        
        for (DocumentOrderItem item : request.getDocuments()) {
            Document document = documentRepository.findById(item.getDocumentId())
                    .orElseThrow(() -> new ResourceNotFoundException("Document could not be found"));
            
            if (!document.getOwnerId().equals(ownerId)) {
                throw new BusinessException(ErrorCodes.UNAUTHORIZED, "You don't have permission to access this document");
            }
            
            document.setOrderIndex(item.getOrderIndex());
            Document saved = documentRepository.save(document);
            updatedDocuments.add(toResponse(saved));
        }
        
        return updatedDocuments;
    }
    
    @Override
    @Transactional
    public void delete(UUID documentId) {
        UUID ownerId = SecurityUtil.getCurrentUserId();
        
        Document document = documentRepository.findById(documentId)
                .orElseThrow(() -> new ResourceNotFoundException("Document could not be found"));
        
        if (!document.getOwnerId().equals(ownerId)) {
            throw new BusinessException(ErrorCodes.UNAUTHORIZED, "You don't have permission to access this document");
        }
        
        document.setIsActive(false);
        document.setVisibleToCustomers(false);
        document.setDeletedAt(Instant.now());
        documentRepository.save(document);
    }
    
    private DocumentResponse toResponse(Document document) {
        return new DocumentResponse(
                document.getId(),
                document.getName(),
                document.getMimeType(),
                document.getUploadedUrl(),
                document.getOwnerId(),
                document.getOrderIndex(),
                document.getIsActive(),
                document.getVisibleToCustomers(),
                document.getCreatedAt(),
                document.getUpdatedAt()
        );
    }
}

