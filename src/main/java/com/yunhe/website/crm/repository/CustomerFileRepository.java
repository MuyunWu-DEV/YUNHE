package com.yunhe.website.crm.repository;

import com.yunhe.website.crm.entity.CustomerFile;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * 客户附件仓储。
 */
public interface CustomerFileRepository extends JpaRepository<CustomerFile, Long> {

    /**
     * 附件元数据投影（不含 BLOB 内容，避免加载大文件）。
     */
    interface CustomerFileMetadata {

        Long getId();

        String getOriginalName();

        String getContentType();

        long getSize();

        Instant getCreatedAt();
    }

    /** 查询某客户的附件元数据（不含 BLOB 内容，避免加载大文件） */
    @Query("""
            select f.id as id, f.originalName as originalName, f.contentType as contentType,
                   f.size as size, f.createdAt as createdAt
            from CustomerFile f where f.customer.id = :customerId
            """)
    List<CustomerFileMetadata> findMetadataByCustomerId(@Param("customerId") Long customerId);

    /** 按客户和附件 id 加载完整文件（含 BLOB，用于下载） */
    Optional<CustomerFile> findByIdAndCustomerId(Long id, Long customerId);
}
