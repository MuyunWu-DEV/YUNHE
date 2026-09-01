package com.yunhe.website.crm.service;

import com.yunhe.website.common.exception.BusinessException;
import com.yunhe.website.common.sequence.SequenceStore;
import com.yunhe.website.crm.dto.ProformaInvoiceDto;
import com.yunhe.website.crm.dto.VersionFileDto;
import com.yunhe.website.crm.dto.request.ProformaInvoiceForm;
import com.yunhe.website.crm.entity.Customer;
import com.yunhe.website.crm.entity.DocumentStatus;
import com.yunhe.website.crm.entity.ProformaDetails;
import com.yunhe.website.crm.entity.ProformaInvoice;
import com.yunhe.website.crm.entity.ProformaInvoiceVersion;
import com.yunhe.website.crm.entity.Quotation;
import com.yunhe.website.crm.entity.QuotationStatus;
import com.yunhe.website.crm.repository.CommercialInvoiceRepository;
import com.yunhe.website.crm.repository.CustomerRepository;
import com.yunhe.website.crm.repository.PackingListRepository;
import com.yunhe.website.crm.repository.ProformaInvoiceRepository;
import com.yunhe.website.crm.repository.ProformaInvoiceVersionRepository;
import com.yunhe.website.crm.repository.QuotationRepository;
import com.yunhe.website.crm.support.DocNumberGenerator;
import com.yunhe.website.crm.support.PiPdfRenderer;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 形式发票服务。
 */
@Service
@RequiredArgsConstructor
public class ProformaInvoiceService {

    private final ProformaInvoiceRepository invoiceRepository;
    private final CustomerRepository customerRepository;
    private final QuotationRepository quotationRepository;
    private final ProformaInvoiceVersionRepository versionRepository;
    private final CommercialInvoiceRepository commercialInvoiceRepository;
    private final PackingListRepository packingListRepository;
    private final CommercialInvoiceService commercialInvoiceService;
    private final PackingListService packingListService;
    private final ObjectMapper objectMapper;
    private final SequenceStore sequenceStore;
    private final PiPdfRenderer piPdfRenderer;

    /** 分页查询发票 */
    @Transactional(readOnly = true)
    public Page<ProformaInvoiceDto> list(Pageable pageable) {
        return invoiceRepository.findAll(pageable).map(this::toDto);
    }

    /** 查询单个发票 */
    @Transactional(readOnly = true)
    public ProformaInvoiceDto getById(Long id) {
        ProformaInvoice invoice = invoiceRepository.findById(id)
                .orElseThrow(() -> BusinessException.notFound("形式发票", id));
        return toDto(invoice);
    }

    /** 按来源报价单查询发票（1:1，可能为空，用于全链追溯） */
    @Transactional(readOnly = true)
    public ProformaInvoiceDto getByQuotationId(Long quotationId) {
        return invoiceRepository.findByQuotationId(quotationId)
                .map(this::toDto)
                .orElse(null);
    }

    /** 创建发票（自动生成发票号，并把 buyer 信息回写客户档案、报价单状态改为已确认） */
    @Transactional
    public ProformaInvoiceDto create(ProformaInvoiceForm form) {
        // 1:1 防重复：同一报价单只能生成一张 PI
        if (form.getQuotationId() != null && invoiceRepository.existsByQuotationId(form.getQuotationId())) {
            throw BusinessException.of("该报价单已生成 Proforma Invoice，不能重复生成");
        }
        ProformaInvoice invoice = new ProformaInvoice();
        applyForm(invoice, form);
        assignInvoiceNumber(invoice, form.getInvoiceDate().getYear());
        invoiceRepository.save(invoice);
        markQuotationConfirmed(invoice.getQuotation());
        return toDto(invoice);
    }

    /** 更新发票：保存改动，若已生成则打回 PI 自身及下游 CI/PL */
    @Transactional
    public ProformaInvoiceDto update(Long id, ProformaInvoiceForm form) {
        ProformaInvoice invoice = invoiceRepository.findById(id)
                .orElseThrow(() -> BusinessException.notFound("形式发票", id));
        applyForm(invoice, form);
        if (invoice.getStatus() == DocumentStatus.GENERATED) {
            invoice.setStatus(DocumentStatus.PENDING_REGENERATION);
            markDownstreamPendingRegeneration(invoice);
        }
        return toDto(invoice);
    }

    /** PI 变更后，把下游已生成的 CI / PL 置为待重新生成 */
    private void markDownstreamPendingRegeneration(ProformaInvoice invoice) {
        if (invoice.getQuotation() == null) {
            return;
        }
        Long rootId = invoice.getQuotation().getId();
        commercialInvoiceRepository.findByRootQuotationId(rootId)
                .ifPresent(ci -> {
                    if (ci.getStatus() == DocumentStatus.GENERATED) {
                        ci.setStatus(DocumentStatus.PENDING_REGENERATION);
                    }
                });
        packingListRepository.findByRootQuotationId(rootId)
                .ifPresent(pl -> {
                    if (pl.getStatus() == DocumentStatus.GENERATED) {
                        pl.setStatus(DocumentStatus.PENDING_REGENERATION);
                    }
                });
    }

    /** 删除发票 */
    @Transactional
    public void delete(Long id) {
        ProformaInvoice invoice = invoiceRepository.findById(id)
                .orElseThrow(() -> BusinessException.notFound("形式发票", id));
        invoiceRepository.delete(invoice);
    }

    /** 生成 PDF（占位：暂不产出真实 PDF），状态置已生成并留存一条版本 */
    @Transactional
    public void generate(Long id) {
        ProformaInvoice invoice = invoiceRepository.findById(id)
                .orElseThrow(() -> BusinessException.notFound("形式发票", id));
        if (invoice.getStatus() == DocumentStatus.GENERATED) {
            throw BusinessException.of("该 Proforma Invoice 已生成 PDF，不能重复生成");
        }
        byte[] pdf = generatePdf(invoice);
        int versionNo = versionRepository.findMaxVersionNo(id) + 1;
        String data = buildSnapshot(invoice);
        versionRepository.save(new ProformaInvoiceVersion(invoice, versionNo, pdf, data,
                "Manual generation"));
        invoice.setStatus(DocumentStatus.GENERATED);
    }

    /** 报价单变更后自动重新生成 */
    @Transactional
    public void regenerateAfterQuotationChange(Long id, String changeReason) {
        regenerateAfterChange(id, buildChangeMessage("报价单发起变更", changeReason));
    }

    /** 通用自动重新生成（message 为完整变更描述）：成功记录 Automatically generated；失败新增独立记录记录真实错误并回退待重新生成。
     * <p>本方法会被同 Bean 内部（revise / regenerateAfterQuotationChange）调用，{@code @Transactional} 注解不生效，
     * 事务由外层 {@code @Transactional} 调用方保证，故此处不加事务注解。</p> */
    public void regenerateAfterChange(Long id, String message) {
        ProformaInvoice invoice = invoiceRepository.findById(id)
                .orElseThrow(() -> BusinessException.notFound("形式发票", id));
        if (invoice.getStatus() != DocumentStatus.GENERATED) {
            return;
        }
        int versionNo = versionRepository.findMaxVersionNo(id) + 1;
        try {
            byte[] pdf = generatePdf(invoice);
            String data = buildSnapshot(invoice);
            versionRepository.save(new ProformaInvoiceVersion(invoice, versionNo, pdf, data,
                    "Automatically generated - " + message));
            // 状态保持已生成
        } catch (Exception e) {
            // 自动生成失败：新增独立记录（版本号递增）记录真实错误，状态回退待重新生成
            invoice.setStatus(DocumentStatus.PENDING_REGENERATION);
            versionRepository.save(new ProformaInvoiceVersion(invoice, versionNo, null, null,
                    "自动重新生成失败：" + e.getMessage()));
        }
    }

    /** 发起变更：保存 PI 改动，自动重新生成 PI 自身及已生成的下游 CI/PL */
    @Transactional
    public ProformaInvoiceDto revise(Long id, ProformaInvoiceForm form) {
        ProformaInvoice invoice = invoiceRepository.findById(id)
                .orElseThrow(() -> BusinessException.notFound("形式发票", id));
        if (invoice.getStatus() != DocumentStatus.GENERATED) {
            throw BusinessException.of("该形式发票尚未生成，不能发起变更");
        }
        applyForm(invoice, form);
        String message = buildChangeMessage("PI 发起变更", form.getChangeReason());
        // 重新生成 PI 自身
        regenerateAfterChange(id, message);
        // 重新生成已生成的下游 CI/PL
        regenerateDownstreamAfterPiChange(invoice, message);
        return toDto(invoice);
    }

    /** PI 变更后，自动重新生成已生成的下游 CI/PL */
    private void regenerateDownstreamAfterPiChange(ProformaInvoice invoice, String message) {
        if (invoice.getQuotation() == null) {
            return;
        }
        Long rootId = invoice.getQuotation().getId();
        commercialInvoiceRepository.findByRootQuotationId(rootId).ifPresent(ci -> {
            if (ci.getStatus() == DocumentStatus.GENERATED) {
                commercialInvoiceService.regenerateAfterChange(ci.getId(), message);
            }
        });
        packingListRepository.findByRootQuotationId(rootId).ifPresent(pl -> {
            if (pl.getStatus() == DocumentStatus.GENERATED) {
                packingListService.regenerateAfterChange(pl.getId(), message);
            }
        });
    }

    /** 拼装变更说明：前缀(变更原因) */
    private String buildChangeMessage(String prefix, String changeReason) {
        if (changeReason == null || changeReason.isBlank()) {
            return prefix;
        }
        return prefix + "(" + changeReason + ")";
    }

    /** 序列化生成那一刻的完整数据快照（用于回看历史版本） */
    private String buildSnapshot(ProformaInvoice invoice) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("invoiceNumber", invoice.getInvoiceNumber());
        snapshot.put("invoiceDate", invoice.getInvoiceDate());
        snapshot.put("details", invoice.getDetails());
        if (invoice.getQuotation() != null) {
            snapshot.put("items", invoice.getQuotation().getDetails());
        }
        try {
            return objectMapper.writeValueAsString(snapshot);
        } catch (JsonProcessingException e) {
            throw BusinessException.of("序列化版本快照失败");
        }
    }

    /** 版本历史（按版本号降序） */
    @Transactional(readOnly = true)
    public List<ProformaInvoiceVersion> listVersions(Long id) {
        return versionRepository.findByProformaInvoiceIdOrderByVersionNoDesc(id);
    }

    /** 获取版本 PDF 及下载文件名（生成功能暂未实现，pdf 可能为空） */
    @Transactional(readOnly = true)
    public VersionFileDto getVersionFile(Long versionId) {
        ProformaInvoiceVersion version = versionRepository.findById(versionId)
                .orElseThrow(() -> BusinessException.notFound("版本", versionId));
        String filename = version.getProformaInvoice().getInvoiceNumber() + "-v" + version.getVersionNo() + ".pdf";
        return new VersionFileDto(version.getPdf(), filename);
    }

    /** 生成 PI PDF（OpenPDF 渲染：按参考模板「整体相同 + 灰度版合理优化」） */
    private byte[] generatePdf(ProformaInvoice invoice) {
        return piPdfRenderer.render(invoice);
    }

    private void applyForm(ProformaInvoice invoice, ProformaInvoiceForm form) {
        invoice.setInvoiceDate(form.getInvoiceDate());
        invoice.setCustomer(resolveCustomer(form.getCustomerId()));
        invoice.setQuotation(resolveQuotation(form.getQuotationId()));

        ProformaDetails details = new ProformaDetails(
                new ProformaDetails.SellerInfo(
                        form.getSellerCompanyName(),
                        form.getSellerAddress(),
                        form.getSellerPhone(),
                        form.getSellerEmail(),
                        form.getSellerChineseName()),
                new ProformaDetails.BuyerInfo(
                        form.getBuyerCompanyName(),
                        form.getBuyerRegistrationNo(),
                        form.getBuyerAddress()),
                form.getIncoterms(),
                form.getTerms(),
                form.getBankAccountInformation(),
                form.getWarranty(),
                new ProformaDetails.RouteInfo(
                        form.getPortOfLoading(),
                        form.getPortOfDestination()));
        invoice.setDetails(details);

        // 保存时把 buyer 信息回写到客户档案
        updateCustomerFromBuyer(invoice.getCustomer(), details.buyer());
    }

    /** 生成发票号：YHPI-YYYY-00X，序号由序列表原子递增（数据库端并发安全） */
    private void assignInvoiceNumber(ProformaInvoice invoice, int year) {
        int seq = sequenceStore.next(ProformaInvoice.NO_PREFIX, year);
        invoice.setInvoiceYear(year);
        invoice.setSeq(seq);
        invoice.setInvoiceNumber(DocNumberGenerator.generate(ProformaInvoice.NO_PREFIX, year, seq));
    }

    private void updateCustomerFromBuyer(Customer customer, ProformaDetails.BuyerInfo buyer) {
        if (customer == null || buyer == null) {
            return;
        }
        customer.setCompany(buyer.companyName());
        customer.setRegistrationNo(buyer.registrationNo());
        customer.setAddress(buyer.address());
    }

    /** 生成 PI 后，把来源报价单状态置为「已确认」 */
    private void markQuotationConfirmed(Quotation quotation) {
        if (quotation != null) {
            quotation.setStatus(QuotationStatus.CONFIRMED);
        }
    }

    private Customer resolveCustomer(Long customerId) {
        if (customerId == null) {
            return null;
        }
        return customerRepository.findById(customerId)
                .orElseThrow(() -> BusinessException.notFound("客户", customerId));
    }

    private Quotation resolveQuotation(Long quotationId) {
        if (quotationId == null) {
            return null;
        }
        return quotationRepository.findById(quotationId)
                .orElseThrow(() -> BusinessException.notFound("报价单", quotationId));
    }

    private ProformaInvoiceDto toDto(ProformaInvoice invoice) {
        Customer customer = invoice.getCustomer();
        ProformaInvoiceDto.CustomerSummary summary = customer == null ? null
                : new ProformaInvoiceDto.CustomerSummary(customer.getId(), customer.getName(), customer.getCompany());
        Long quotationId = invoice.getQuotation() == null ? null : invoice.getQuotation().getId();
        return new ProformaInvoiceDto(
                invoice.getId(),
                invoice.getInvoiceNumber(),
                invoice.getInvoiceDate(),
                invoice.getStatus(),
                invoice.getDetails(),
                summary,
                quotationId,
                invoice.getCreatedAt());
    }
}
