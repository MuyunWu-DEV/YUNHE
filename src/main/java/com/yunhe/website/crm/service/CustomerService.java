package com.yunhe.website.crm.service;

import com.yunhe.website.common.exception.BusinessException;
import com.yunhe.website.crm.dto.CustomerDto;
import com.yunhe.website.crm.dto.CustomerFileDto;
import com.yunhe.website.crm.dto.request.CustomerForm;
import com.yunhe.website.crm.entity.Customer;
import com.yunhe.website.crm.entity.CustomerFile;
import com.yunhe.website.crm.entity.CustomerTag;
import com.yunhe.website.crm.mapper.CustomerMapper;
import com.yunhe.website.crm.repository.CustomerFileRepository;
import com.yunhe.website.crm.repository.CustomerRepository;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

/**
 * 客户档案服务。
 */
@Service
@RequiredArgsConstructor
public class CustomerService {

    private final CustomerRepository customerRepository;
    private final CustomerFileRepository customerFileRepository;
    private final CustomerMapper customerMapper;

    /** 分页查询客户 */
    @Transactional(readOnly = true)
    public Page<CustomerDto> list(String keyword, Pageable pageable) {
        Page<Customer> page = StringUtils.hasText(keyword)
                ? customerRepository.searchByKeyword(keyword, pageable)
                : customerRepository.findAll(pageable);
        return page.map(customerMapper::toDto);
    }

    /** 查询全部客户（用于下拉选择等） */
    @Transactional(readOnly = true)
    public List<CustomerDto> listForSelect() {
        return customerRepository.findAll().stream()
                .map(customerMapper::toDto)
                .toList();
    }

    /** 查询单个客户（含附件元数据） */
    @Transactional(readOnly = true)
    public CustomerDto getById(Long id) {
        Customer customer = customerRepository.findById(id)
                .orElseThrow(() -> BusinessException.notFound("客户", id));
        return customerMapper.toDetailDto(customer, listFileMetadata(id));
    }

    /** 查询客户附件元数据 */
    @Transactional(readOnly = true)
    public List<CustomerFileDto> listFileMetadata(Long customerId) {
        return customerFileRepository.findMetadataByCustomerId(customerId).stream()
                .map(m -> new CustomerFileDto(
                        m.getId(), m.getOriginalName(), m.getContentType(), m.getSize(), m.getCreatedAt()))
                .toList();
    }

    /** 创建客户 */
    @Transactional
    public void create(CustomerForm form, List<MultipartFile> files) {
        if (customerRepository.existsByName(form.getName())) {
            throw BusinessException.of("客户姓名已存在");
        }
        Customer customer = new Customer();
        applyForm(customer, form);
        customer.getFiles().addAll(toFiles(customer, files));
        customerRepository.save(customer);
    }

    /** 更新客户 */
    @Transactional
    public void update(Long id, CustomerForm form, List<MultipartFile> files) {
        Customer customer = customerRepository.findById(id)
                .orElseThrow(() -> BusinessException.notFound("客户", id));
        if (customerRepository.existsByNameAndIdNot(form.getName(), id)) {
            throw BusinessException.of("客户姓名已存在");
        }
        applyForm(customer, form);
        customer.getFiles().addAll(toFiles(customer, files));
    }

    /** 删除客户（级联删除附件） */
    @Transactional
    public void delete(Long id) {
        Customer customer = customerRepository.findById(id)
                .orElseThrow(() -> BusinessException.notFound("客户", id));
        customerRepository.delete(customer);
    }

    /** 取附件完整内容（用于下载） */
    @Transactional(readOnly = true)
    public CustomerFile getFileForDownload(Long customerId, Long fileId) {
        return customerFileRepository.findByIdAndCustomerId(fileId, customerId)
                .orElseThrow(() -> BusinessException.notFound("附件", fileId));
    }

    /** 删除单个附件 */
    @Transactional
    public void deleteFile(Long customerId, Long fileId) {
        CustomerFile file = customerFileRepository.findByIdAndCustomerId(fileId, customerId)
                .orElseThrow(() -> BusinessException.notFound("附件", fileId));
        customerFileRepository.delete(file);
    }

    private void applyForm(Customer customer, CustomerForm form) {
        customer.setName(form.getName());
        customer.setPhone(form.getPhone());
        customer.setCompany(form.getCompany());
        customer.setRegistrationNo(form.getRegistrationNo());
        customer.setAddress(form.getAddress());
        customer.setTags(resolveTags(form.getTags()));
        customer.setPriority(form.getPriority());
        customer.setNextFollowUpAt(form.getNextFollowUpAt());
    }

    private Set<CustomerTag> resolveTags(List<CustomerTag> tags) {
        if (tags == null || tags.isEmpty()) {
            return new LinkedHashSet<>();
        }
        return new LinkedHashSet<>(tags);
    }

    private List<CustomerFile> toFiles(Customer customer, List<MultipartFile> files) {
        List<CustomerFile> result = new ArrayList<>();
        if (files == null) {
            return result;
        }
        for (MultipartFile file : files) {
            if (file.isEmpty()) {
                continue;
            }
            CustomerFile entity = new CustomerFile();
            entity.setCustomer(customer);
            entity.setOriginalName(file.getOriginalFilename());
            entity.setContentType(file.getContentType());
            entity.setSize(file.getSize());
            try {
                entity.setContent(file.getBytes());
            } catch (IOException e) {
                throw BusinessException.of("读取上传文件失败：" + file.getOriginalFilename());
            }
            result.add(entity);
        }
        return result;
    }
}
