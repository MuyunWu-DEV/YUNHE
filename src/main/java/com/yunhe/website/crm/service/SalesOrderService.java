package com.yunhe.website.crm.service;

import com.yunhe.website.common.exception.BusinessException;
import com.yunhe.website.common.sequence.SequenceStore;
import com.yunhe.website.crm.dto.SalesOrderDto;
import com.yunhe.website.crm.dto.request.SalesOrderForm;
import com.yunhe.website.crm.entity.Customer;
import com.yunhe.website.crm.entity.DocumentStatus;
import com.yunhe.website.crm.entity.OrderStatus;
import com.yunhe.website.crm.entity.ProformaInvoice;
import com.yunhe.website.crm.entity.SalesOrder;
import com.yunhe.website.crm.repository.ProformaInvoiceRepository;
import com.yunhe.website.crm.repository.SalesOrderRepository;
import com.yunhe.website.crm.support.DocNumberGenerator;
import java.time.LocalDate;
import java.util.ArrayList;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 销售订单服务。
 */
@Service
@RequiredArgsConstructor
public class SalesOrderService {

    private final SalesOrderRepository orderRepository;
    private final ProformaInvoiceRepository invoiceRepository;
    private final SequenceStore sequenceStore;

    /** 分页查询订单 */
    @Transactional(readOnly = true)
    public Page<SalesOrderDto> list(Pageable pageable) {
        return orderRepository.findAll(pageable).map(this::toDto);
    }

    /** 查询单个订单 */
    @Transactional(readOnly = true)
    public SalesOrderDto getById(Long id) {
        return orderRepository.findById(id)
                .map(this::toDto)
                .orElseThrow(() -> BusinessException.notFound("订单", id));
    }

    /** 按根报价单查询订单（1:1，可空，用于全链追溯） */
    @Transactional(readOnly = true)
    public SalesOrderDto getByRootQuotationId(Long rootQuotationId) {
        return orderRepository.findByRootQuotationId(rootQuotationId)
                .map(this::toDto)
                .orElse(null);
    }

    /** 从 PI 转订单：快照来源报价单的货物明细与客户（1:1） */
    @Transactional
    public SalesOrderDto convertFromPi(Long piId) {
        ProformaInvoice pi = invoiceRepository.findById(piId)
                .orElseThrow(() -> BusinessException.notFound("形式发票", piId));
        if (pi.getStatus() != DocumentStatus.GENERATED) {
            throw BusinessException.of("该形式发票尚未生成 PDF，不能转订单");
        }
        if (pi.getQuotation() == null) {
            throw BusinessException.of("该形式发票未关联报价单，无法转订单");
        }
        if (orderRepository.existsByProformaInvoiceId(piId)) {
            throw BusinessException.of("该形式发票已生成订单，不能重复转换");
        }
        SalesOrder order = new SalesOrder();
        order.setOrderDate(LocalDate.now());
        order.setStatus(OrderStatus.DRAFT);
        order.setDetails(new ArrayList<>(pi.getQuotation().getDetails()));
        order.setCustomer(pi.getCustomer());
        order.setProformaInvoice(pi);
        order.setRootQuotationId(pi.getQuotation().getId());
        assignOrderNo(order, LocalDate.now().getYear());
        orderRepository.save(order);
        return toDto(order);
    }

    /** 更新订单（日期/状态/备注） */
    @Transactional
    public SalesOrderDto update(Long id, SalesOrderForm form) {
        SalesOrder order = orderRepository.findById(id)
                .orElseThrow(() -> BusinessException.notFound("订单", id));
        order.setOrderDate(form.getOrderDate());
        order.setStatus(form.getStatus());
        order.setRemark(form.getRemark());
        return toDto(order);
    }

    /** 删除订单 */
    @Transactional
    public void delete(Long id) {
        SalesOrder order = orderRepository.findById(id)
                .orElseThrow(() -> BusinessException.notFound("订单", id));
        orderRepository.delete(order);
    }

    /** 生成订单号：YHSO-YYYY-00X，序号由序列表原子递增（数据库端并发安全） */
    private void assignOrderNo(SalesOrder order, int year) {
        int seq = sequenceStore.next(SalesOrder.NO_PREFIX, year);
        order.setOrderYear(year);
        order.setSeq(seq);
        order.setOrderNo(DocNumberGenerator.generate(SalesOrder.NO_PREFIX, year, seq));
    }

    private SalesOrderDto toDto(SalesOrder order) {
        Customer customer = order.getCustomer();
        SalesOrderDto.CustomerSummary summary = customer == null ? null
                : new SalesOrderDto.CustomerSummary(customer.getId(), customer.getName(), customer.getCompany());
        Long piId = order.getProformaInvoice() == null ? null : order.getProformaInvoice().getId();
        return new SalesOrderDto(
                order.getId(),
                order.getOrderNo(),
                order.getOrderDate(),
                order.getStatus(),
                order.getRemark(),
                order.getDetails(),
                summary,
                piId,
                order.getRootQuotationId(),
                order.getCreatedAt());
    }
}
