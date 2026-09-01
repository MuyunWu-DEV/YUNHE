package com.yunhe.website.crm.service;

import com.yunhe.website.common.exception.BusinessException;
import com.yunhe.website.crm.dto.QuotationDto;
import com.yunhe.website.crm.dto.request.QuotationForm;
import com.yunhe.website.crm.entity.Customer;
import com.yunhe.website.crm.entity.DocumentStatus;
import com.yunhe.website.crm.entity.ProformaInvoice;
import com.yunhe.website.crm.entity.Quotation;
import com.yunhe.website.crm.entity.QuoteDetailGroup;
import com.yunhe.website.crm.entity.QuoteDetailItem;
import com.yunhe.website.crm.entity.QuotationLog;
import com.yunhe.website.crm.entity.QuotationStatus;
import com.yunhe.website.crm.mapper.QuotationMapper;
import com.yunhe.website.crm.repository.CommercialInvoiceRepository;
import com.yunhe.website.crm.repository.CustomerRepository;
import com.yunhe.website.crm.repository.PackingListRepository;
import com.yunhe.website.crm.repository.ProformaInvoiceRepository;
import com.yunhe.website.crm.repository.QuotationLogRepository;
import com.yunhe.website.crm.repository.QuotationRepository;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * 报价管理服务。
 */
@Service
@RequiredArgsConstructor
public class QuotationService {

    private final QuotationRepository quotationRepository;
    private final CustomerRepository customerRepository;
    private final ProformaInvoiceRepository proformaInvoiceRepository;
    private final QuotationMapper quotationMapper;
    private final QuotationLogRepository quotationLogRepository;
    private final CommercialInvoiceRepository commercialInvoiceRepository;
    private final PackingListRepository packingListRepository;
    private final ProformaInvoiceService proformaInvoiceService;
    private final CommercialInvoiceService commercialInvoiceService;
    private final PackingListService packingListService;

    /** 分页查询报价单（附带 PI 关联，避免 N+1） */
    @Transactional(readOnly = true)
    public Page<QuotationDto> list(Pageable pageable) {
        Page<Quotation> page = quotationRepository.findAll(pageable);
        List<Long> quotationIds = page.getContent().stream().map(Quotation::getId).toList();
        Map<Long, Long> piIdByQuotationId = proformaInvoiceRepository.findByQuotationIdIn(quotationIds).stream()
                .collect(Collectors.toMap(p -> p.getQuotation().getId(), ProformaInvoice::getId, (a, b) -> a));
        return page.map(q -> quotationMapper.toDto(q).withProformaInvoiceId(piIdByQuotationId.get(q.getId())));
    }

    /** 查询单个报价单 */
    @Transactional(readOnly = true)
    public QuotationDto getById(Long id) {
        Quotation quotation = quotationRepository.findById(id)
                .orElseThrow(() -> BusinessException.notFound("报价单", id));
        return quotationMapper.toDto(quotation);
    }

    /** 查询报价单变更历史（仅记录「发起变更」，按时间倒序） */
    @Transactional(readOnly = true)
    public List<QuotationLog> listLogs(Long quotationId) {
        return quotationLogRepository.findByQuotationIdOrderByCreatedAtDesc(quotationId);
    }

    /** 创建报价单 */
    @Transactional
    public void create(QuotationForm form) {
        Quotation quotation = new Quotation();
        applyForm(quotation, form);
        quotation.setStatus(QuotationStatus.DRAFT);
        quotationRepository.save(quotation);
    }

    /** 更新报价单（普通编辑，保存后重置为草稿） */
    @Transactional
    public void update(Long id, QuotationForm form) {
        Quotation quotation = quotationRepository.findById(id)
                .orElseThrow(() -> BusinessException.notFound("报价单", id));
        applyForm(quotation, form);
        quotation.setStatus(QuotationStatus.DRAFT);
    }

    /** 发起变更：保存改动、记录变更原因，并把下游单据批量置为待重新生成 */
    @Transactional
    public void revise(Long id, QuotationForm form, String changedBy) {
        Quotation quotation = quotationRepository.findById(id)
                .orElseThrow(() -> BusinessException.notFound("报价单", id));
        applyForm(quotation, form);
        // 保持「已确认」状态不变
        quotation.setStatus(QuotationStatus.CONFIRMED);
        quotationLogRepository.save(new QuotationLog(id, form.getChangeReason(), changedBy));
        regenerateDownstream(id, form.getChangeReason());
    }

    /** 删除报价单 */
    @Transactional
    public void delete(Long id) {
        quotationRepository.deleteById(id);
    }

    private void applyForm(Quotation quotation, QuotationForm form) {
        quotation.setQuoteDate(form.getQuoteDate());
        quotation.setRemark(form.getRemark());
        quotation.setDetails(toDetailGroups(form.getDetails()));
        quotation.setCustomer(resolveCustomer(form.getCustomerId()));
    }

    /** 报价单变更后，对「已生成」的 PI / CI / PL 尝试自动重新生成；失败由各 Service 回退待重新生成并记录错误 */
    private void regenerateDownstream(Long quotationId, String changeReason) {
        proformaInvoiceRepository.findByQuotationId(quotationId).ifPresent(pi -> {
            if (pi.getStatus() == DocumentStatus.GENERATED) {
                proformaInvoiceService.regenerateAfterQuotationChange(pi.getId(), changeReason);
            }
        });
        commercialInvoiceRepository.findByRootQuotationId(quotationId).ifPresent(ci -> {
            if (ci.getStatus() == DocumentStatus.GENERATED) {
                commercialInvoiceService.regenerateAfterQuotationChange(ci.getId(), changeReason);
            }
        });
        packingListRepository.findByRootQuotationId(quotationId).ifPresent(pl -> {
            if (pl.getStatus() == DocumentStatus.GENERATED) {
                packingListService.regenerateAfterQuotationChange(pl.getId(), changeReason);
            }
        });
    }

    /** 发送报价单：草稿 → 已发送；非草稿返回 false（下载功能暂未实现） */
    @Transactional
    public boolean markSent(Long id) {
        Quotation quotation = quotationRepository.findById(id)
                .orElseThrow(() -> BusinessException.notFound("报价单", id));
        if (quotation.getStatus() == QuotationStatus.DRAFT) {
            quotation.setStatus(QuotationStatus.SENT);
            return true;
        }
        return false;
    }

    /** 将表单的分组结构转换为 QuoteDetailGroup 列表 */
    private List<QuoteDetailGroup> toDetailGroups(List<QuotationForm.DetailGroupForm> groups) {
        if (groups == null) {
            return new ArrayList<>();
        }
        List<QuoteDetailGroup> result = new ArrayList<>();
        for (QuotationForm.DetailGroupForm group : groups) {
            String productName = group.getProductName();
            if (!StringUtils.hasText(productName)) {
                continue;
            }
            List<QuoteDetailItem> items = group.getItems() == null ? List.of()
                    : group.getItems().stream()
                            .filter(item -> StringUtils.hasText(item.getDescription()))
                            .map(item -> new QuoteDetailItem(
                                    resolveItemKey(item.getKey()),
                                    item.getDescription(),
                                    item.getUnitPrice() == null ? BigDecimal.ZERO : item.getUnitPrice(),
                                    item.getQuantity() == null ? 0 : item.getQuantity(),
                                    item.getUnit(),
                                    item.getCurrency()))
                            .toList();
            result.add(new QuoteDetailGroup(productName.trim(), group.getHsCode(), items));
        }
        return result;
    }

    private Customer resolveCustomer(Long customerId) {
        if (customerId == null) {
            return null;
        }
        return customerRepository.findById(customerId)
                .orElseThrow(() -> BusinessException.notFound("客户", customerId));
    }

    /**
     * 解析明细项稳定 key：表单带回的 key 原样沿用（保证 PL JOIN 不因重排而断）；
     * 表单未带（新建/历史数据）则生成 UUID，写入后随报价单持久化。
     */
    private String resolveItemKey(String incoming) {
        if (incoming != null && !incoming.isBlank()) {
            return incoming;
        }
        return UUID.randomUUID().toString();
    }
}
