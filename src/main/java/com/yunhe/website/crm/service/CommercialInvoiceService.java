package com.yunhe.website.crm.service;

import com.yunhe.website.common.exception.BusinessException;
import com.yunhe.website.common.sequence.SequenceStore;
import com.yunhe.website.crm.dto.CommercialInvoiceDto;
import com.yunhe.website.crm.dto.VersionFileDto;
import com.yunhe.website.crm.dto.request.CommercialInvoiceForm;
import com.yunhe.website.crm.entity.CommercialInvoice;
import com.yunhe.website.crm.entity.CommercialInvoiceVersion;
import com.yunhe.website.crm.entity.Customer;
import com.yunhe.website.crm.entity.DocumentStatus;
import com.yunhe.website.crm.entity.ProformaInvoice;
import com.yunhe.website.crm.repository.CommercialInvoiceRepository;
import com.yunhe.website.crm.repository.CommercialInvoiceVersionRepository;
import com.yunhe.website.crm.repository.DocVersionSummary;
import com.yunhe.website.crm.repository.ProformaInvoiceRepository;
import com.yunhe.website.crm.support.CiPdfRenderer;
import com.yunhe.website.crm.support.DocNumberGenerator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 商业发票服务。
 */
@Service
@RequiredArgsConstructor
public class CommercialInvoiceService {

    private final CommercialInvoiceRepository invoiceRepository;
    private final ProformaInvoiceRepository proformaInvoiceRepository;
    private final CommercialInvoiceVersionRepository versionRepository;
    private final ObjectMapper objectMapper;
    private final SequenceStore sequenceStore;
    private final CiPdfRenderer ciPdfRenderer;

    @Transactional(readOnly = true)
    public Page<CommercialInvoiceDto> list(Pageable pageable) {
        return invoiceRepository.findAll(pageable).map(this::toDto);
    }

    @Transactional(readOnly = true)
    public CommercialInvoiceDto getById(Long id) {
        return invoiceRepository.findById(id)
                .map(this::toDto)
                .orElseThrow(() -> BusinessException.notFound("商业发票", id));
    }

    @Transactional(readOnly = true)
    public CommercialInvoiceDto getByRootQuotationId(Long rootQuotationId) {
        return invoiceRepository.findByRootQuotationId(rootQuotationId)
                .map(this::toDto)
                .orElse(null);
    }

    /** 从 PI 生成商业发票：快照货物明细与客户（1:1） */
    @Transactional
    public CommercialInvoiceDto generateFromPi(Long piId) {
        ProformaInvoice pi = proformaInvoiceRepository.findById(piId)
                .orElseThrow(() -> BusinessException.notFound("形式发票", piId));
        if (pi.getStatus() != DocumentStatus.GENERATED) {
            throw BusinessException.of("该形式发票尚未生成 PDF，不能生成商业发票");
        }
        if (pi.getQuotation() == null) {
            throw BusinessException.of("该形式发票未关联报价单，无法生成商业发票");
        }
        if (invoiceRepository.existsByProformaInvoiceId(piId)) {
            throw BusinessException.of("该形式发票已生成商业发票，不能重复生成");
        }
        CommercialInvoice invoice = new CommercialInvoice();
        invoice.setInvoiceDate(LocalDate.now());
        invoice.setCustomer(pi.getCustomer());
        invoice.setProformaInvoice(pi);
        invoice.setRootQuotationId(pi.getQuotation().getId());
        assignInvoiceNo(invoice, LocalDate.now().getYear());
        invoiceRepository.save(invoice);
        return toDto(invoice);
    }

    /**
     * 更新（普通编辑，仅未生成单据可走）。
     * 已生成（GENERATED）单据禁止裸 update 降级：改动必须经 {@link #revise} 留版本日志，
     * 防止绕过版本审计直接改已出 PDF 的单据。
     */
    @Transactional
    public CommercialInvoiceDto update(Long id, CommercialInvoiceForm form) {
        CommercialInvoice invoice = invoiceRepository.findById(id)
                .orElseThrow(() -> BusinessException.notFound("商业发票", id));
        if (invoice.getStatus() == DocumentStatus.GENERATED) {
            throw BusinessException.of("该商业发票已生成 PDF，不能直接编辑；请使用「变更」功能发起变更");
        }
        applyForm(invoice, form);
        return toDto(invoice);
    }

    /** 发起变更：保存改动，自动重新生成自身（商业发票为末端单据，无下游） */
    @Transactional
    public CommercialInvoiceDto revise(Long id, CommercialInvoiceForm form) {
        CommercialInvoice invoice = invoiceRepository.findById(id)
                .orElseThrow(() -> BusinessException.notFound("商业发票", id));
        if (invoice.getStatus() != DocumentStatus.GENERATED) {
            throw BusinessException.of("该商业发票尚未生成，不能发起变更");
        }
        applyForm(invoice, form);
        regenerateAfterChange(id, buildChangeMessage("CI 发起变更", form.getChangeReason()));
        return toDto(invoice);
    }

    /** 将表单字段写入实体 */
    private void applyForm(CommercialInvoice invoice, CommercialInvoiceForm form) {
        invoice.setInvoiceDate(form.getInvoiceDate());
        invoice.setRemark(form.getRemark());
        invoice.setDepositPercentage(form.getDepositPercentage());
        invoice.setDepositPaymentMethod(form.getDepositPaymentMethod());
        invoice.setBalancePaymentMethod(form.getBalancePaymentMethod());
    }

    /** 删除商业发票（叶子单据，无下游）：级联删除其自身版本后删除 */
    @Transactional
    public void delete(Long id) {
        CommercialInvoice invoice = invoiceRepository.findById(id)
                .orElseThrow(() -> BusinessException.notFound("商业发票", id));
        // 级联清理从属版本，避免撞版本 FK 转 500
        versionRepository.deleteByCommercialInvoiceId(id);
        invoiceRepository.delete(invoice);
    }

    /** 生成 PDF（占位：暂不产出真实 PDF），状态置已生成并留存一条版本 */
    @Transactional
    public void generate(Long id) {
        CommercialInvoice invoice = invoiceRepository.findById(id)
                .orElseThrow(() -> BusinessException.notFound("商业发票", id));
        if (invoice.getStatus() == DocumentStatus.GENERATED) {
            throw BusinessException.of("该商业发票已生成 PDF，不能重复生成");
        }
        byte[] pdf = generatePdf(invoice);
        int versionNo = versionRepository.findMaxVersionNo(id) + 1;
        String data = buildSnapshot(invoice);
        versionRepository.save(new CommercialInvoiceVersion(invoice, versionNo, pdf, data,
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
        CommercialInvoice invoice = invoiceRepository.findById(id)
                .orElseThrow(() -> BusinessException.notFound("商业发票", id));
        if (invoice.getStatus() != DocumentStatus.GENERATED) {
            return;
        }
        int versionNo = versionRepository.findMaxVersionNo(id) + 1;
        try {
            byte[] pdf = generatePdf(invoice);
            String data = buildSnapshot(invoice);
            versionRepository.save(new CommercialInvoiceVersion(invoice, versionNo, pdf, data,
                    "Automatically generated - " + message));
            // 状态保持已生成
        } catch (Exception e) {
            // 自动生成失败：新增独立记录（版本号递增）记录真实错误，状态回退待重新生成
            invoice.setStatus(DocumentStatus.PENDING_REGENERATION);
            versionRepository.save(new CommercialInvoiceVersion(invoice, versionNo, null, null,
                    "自动重新生成失败：" + e.getMessage()));
        }
    }

    /** 拼装变更说明：前缀(变更原因) */
    private String buildChangeMessage(String prefix, String changeReason) {
        if (changeReason == null || changeReason.isBlank()) {
            return prefix;
        }
        return prefix + "(" + changeReason + ")";
    }

    /** 序列化生成那一刻的完整数据快照（用于回看历史版本） */
    private String buildSnapshot(CommercialInvoice invoice) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("invoiceNo", invoice.getInvoiceNo());
        snapshot.put("invoiceDate", invoice.getInvoiceDate());
        snapshot.put("remark", invoice.getRemark());
        snapshot.put("depositPercentage", invoice.getDepositPercentage());
        snapshot.put("depositPaymentMethod", invoice.getDepositPaymentMethod());
        snapshot.put("balancePaymentMethod", invoice.getBalancePaymentMethod());
        ProformaInvoice pi = invoice.getProformaInvoice();
        if (pi != null) {
            snapshot.put("piDetails", pi.getDetails());
            if (pi.getQuotation() != null) {
                snapshot.put("items", pi.getQuotation().getDetails());
            }
        }
        try {
            return objectMapper.writeValueAsString(snapshot);
        } catch (JsonProcessingException e) {
            throw BusinessException.of("序列化版本快照失败");
        }
    }

    /** 版本历史（按版本号降序，轻量投影，不加载 pdf/data 大字段） */
    @Transactional(readOnly = true)
    public List<DocVersionSummary> listVersions(Long id) {
        return versionRepository.findByCommercialInvoiceIdOrderByVersionNoDesc(id);
    }

    /** 获取版本 PDF 及下载文件名。校验版本归属于指定商业发票，防止用任意 versionId 越权下载他单 PDF */
    @Transactional(readOnly = true)
    public VersionFileDto getVersionFile(Long ciId, Long versionId) {
        if (!versionRepository.existsByIdAndCommercialInvoiceId(versionId, ciId)) {
            throw BusinessException.notFound("版本", versionId);
        }
        CommercialInvoiceVersion version = versionRepository.findById(versionId)
                .orElseThrow(() -> BusinessException.notFound("版本", versionId));
        String filename = version.getCommercialInvoice().getInvoiceNo() + "-v" + version.getVersionNo() + ".pdf";
        return new VersionFileDto(version.getPdf(), filename);
    }

    /** PDF 生成（OpenPDF 渲染：调用 CiPdfRenderer 产出纯黑白商业发票） */
    private byte[] generatePdf(CommercialInvoice invoice) {
        return ciPdfRenderer.render(invoice);
    }

    private void assignInvoiceNo(CommercialInvoice invoice, int year) {
        int seq = sequenceStore.next(CommercialInvoice.NO_PREFIX, year);
        invoice.setInvoiceYear(year);
        invoice.setSeq(seq);
        invoice.setInvoiceNo(DocNumberGenerator.generate(CommercialInvoice.NO_PREFIX, year, seq));
    }

    private CommercialInvoiceDto toDto(CommercialInvoice invoice) {
        Customer customer = invoice.getCustomer();
        CommercialInvoiceDto.CustomerSummary summary = customer == null ? null
                : new CommercialInvoiceDto.CustomerSummary(customer.getId(), customer.getName(), customer.getCompany());
        Long piId = invoice.getProformaInvoice() == null ? null : invoice.getProformaInvoice().getId();
        return new CommercialInvoiceDto(
                invoice.getId(),
                invoice.getInvoiceNo(),
                invoice.getInvoiceDate(),
                invoice.getStatus(),
                invoice.getRemark(),
                invoice.getDepositPercentage(),
                invoice.getDepositPaymentMethod(),
                invoice.getBalancePaymentMethod(),
                summary,
                piId,
                invoice.getRootQuotationId(),
                invoice.getCreatedAt());
    }
}
