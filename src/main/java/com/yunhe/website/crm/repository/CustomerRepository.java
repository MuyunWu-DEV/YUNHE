package com.yunhe.website.crm.repository;

import com.yunhe.website.crm.entity.Customer;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * 客户仓储。
 */
public interface CustomerRepository extends JpaRepository<Customer, Long> {

    boolean existsByName(String name);

    boolean existsByNameAndIdNot(String name, Long id);

    /** 按关键词（姓名 / 手机号 / 公司）模糊分页查询 */
    @Query("""
            select c from Customer c
            where lower(c.name) like lower(concat('%', :keyword, '%'))
               or lower(c.phone) like lower(concat('%', :keyword, '%'))
               or lower(c.company) like lower(concat('%', :keyword, '%'))
            """)
    Page<Customer> searchByKeyword(@Param("keyword") String keyword, Pageable pageable);
}
