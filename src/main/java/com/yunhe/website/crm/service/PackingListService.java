package com.yunhe.website.crm.service;

import com.yunhe.website.common.exception.BusinessException;
import com.yunhe.website.common.sequence.SequenceStore;
import com.yunhe.website.crm.dto.PackingListDto;
import com.yunhe.website.crm.dto.VersionFileDto;
import com.yunhe.website.crm.dto.request.PackingListForm;
import com.yunhe.website.crm.entity.Customer;
import com.yunhe.website.crm.entity.DocumentStatus;
import com.yunhe.website.crm.entity.PackingList;
import com.yunhe.website.crm.entity.PackingListVersion;
import com.yunhe.website.crm.entity.ProformaInvoice;
import com.yunhe.website.crm.repository.PackingListRepository;
import com.yunhe.website.crm.repository.PackingListVersionRepository;
import com.yunhe.website.crm.repository.ProformaInvoiceRepository;
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
 * 装箱单服务。
 */
@Service
@RequiredArgsConstructor
public class PackingListService {

    private final PackingListRepository packingListRepository;
    private final ProformaInvoiceRepository proformaInvoiceRepository;
    private final PackingListVersionRepository versionRepository;
    private final ObjectMapper objectMapper;
    private final SequenceStore sequenceStore;

    @Transactional(readOnly = true)
    public Page<PackingListDto> list(Pageable pageable) {
        return packingListRepository.findAll(pageable).map(this::toDto);
    }

    @Transactional(readOnly = true)
    public PackingListDto getById(Long id) {
        return packingListRepository.findById(id)
                .map(this::toDto)
                .orElseThrow(() -> BusinessException.notFound("装箱单", id));
    }

    @Transactional(readOnly = true)
    public PackingListDto getByRootQuotationId(Long rootQuotationId) {
        return packingListRepository.findByRootQuotationId(rootQuotationId)
                .map(this::toDto)
                .orElse(null);
    }

    /** 从 PI 生成装箱单：快照货物明细与客户，装箱信息留待编辑填写（1:1） */
    @Transactional
    public PackingListDto generateFromPi(Long piId) {
        ProformaInvoice pi = proformaInvoiceRepository.findById(piId)
                .orElseThrow(() -> BusinessException.notFound("形式发票", piId));
        if (pi.getStatus() != DocumentStatus.GENERATED) {
            throw BusinessException.of("该形式发票尚未生成 PDF，不能生成装箱单");
        }
        if (pi.getQuotation() == null) {
            throw BusinessException.of("该形式发票未关联报价单，无法生成装箱单");
        }
        if (packingListRepository.existsByProformaInvoiceId(piId)) {
            throw BusinessException.of("该形式发票已生成装箱单，不能重复生成");
        }
        PackingList packingList = new PackingList();
        packingList.setPackingDate(LocalDate.now());
        packingList.setCustomer(pi.getCustomer());
        packingList.setProformaInvoice(pi);
        packingList.setRootQuotationId(pi.getQuotation().getId());
        assignPackingNo(packingList, LocalDate.now().getYear());
        packingListRepository.save(packingList);
        return toDto(packingList);
    }

    @Transactional
    public PackingListDto update(Long id, PackingListForm form) {
        PackingList packingList = packingListRepository.findById(id)
                .orElseThrow(() -> BusinessException.notFound("装箱单", id));
        applyForm(packingList, form);
        if (packingList.getStatus() == DocumentStatus.GENERATED) {
            packingList.setStatus(DocumentStatus.PENDING_REGENERATION);
        }
        return toDto(packingList);
    }

    /** 发起变更：保存改动，自动重新生成自身（装箱单为末端单据，无下游） */
    @Transactional
    public PackingListDto revise(Long id, PackingListForm form) {
        PackingList packingList = packingListRepository.findById(id)
                .orElseThrow(() -> BusinessException.notFound("装箱单", id));
        if (packingList.getStatus() != DocumentStatus.GENERATED) {
            throw BusinessException.of("该装箱单尚未生成，不能发起变更");
        }
        applyForm(packingList, form);
        regenerateAfterChange(id, buildChangeMessage("PL 发起变更", form.getChangeReason()));
        return toDto(packingList);
    }

    /** 将表单字段写入实体 */
    private void applyForm(PackingList packingList, PackingListForm form) {
        packingList.setPackingDate(form.getPackingDate());
        packingList.setMarks(form.getMarks());
        packingList.setNumberOfPackages(form.getNumberOfPackages());
        packingList.setGrossWeight(form.getGrossWeight());
        packingList.setNetWeight(form.getNetWeight());
        packingList.setVolume(form.getVolume());
        packingList.setRemark(form.getRemark());
    }

    @Transactional
    public void delete(Long id) {
        PackingList packingList = packingListRepository.findById(id)
                .orElseThrow(() -> BusinessException.notFound("装箱单", id));
        packingListRepository.delete(packingList);
    }

    /** 生成 PDF（占位：暂不产出真实 PDF），状态置已生成并留存一条版本 */
    @Transactional
    public void generate(Long id) {
        PackingList packingList = packingListRepository.findById(id)
                .orElseThrow(() -> BusinessException.notFound("装箱单", id));
        if (packingList.getStatus() == DocumentStatus.GENERATED) {
            throw BusinessException.of("该装箱单已生成 PDF，不能重复生成");
        }
        byte[] pdf = generatePdf(packingList);
        int versionNo = versionRepository.findMaxVersionNo(id) + 1;
        String data = buildSnapshot(packingList);
        versionRepository.save(new PackingListVersion(packingList, versionNo, pdf, data,
                "Manual generation"));
        packingList.setStatus(DocumentStatus.GENERATED);
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
        PackingList packingList = packingListRepository.findById(id)
                .orElseThrow(() -> BusinessException.notFound("装箱单", id));
        if (packingList.getStatus() != DocumentStatus.GENERATED) {
            return;
        }
        int versionNo = versionRepository.findMaxVersionNo(id) + 1;
        try {
            byte[] pdf = generatePdf(packingList);
            String data = buildSnapshot(packingList);
            versionRepository.save(new PackingListVersion(packingList, versionNo, pdf, data,
                    "Automatically generated - " + message));
            // 状态保持已生成
        } catch (Exception e) {
            // 自动生成失败：新增独立记录（版本号递增）记录真实错误，状态回退待重新生成
            packingList.setStatus(DocumentStatus.PENDING_REGENERATION);
            versionRepository.save(new PackingListVersion(packingList, versionNo, null, null,
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
    private String buildSnapshot(PackingList packingList) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("packingNo", packingList.getPackingNo());
        snapshot.put("packingDate", packingList.getPackingDate());
        snapshot.put("marks", packingList.getMarks());
        snapshot.put("numberOfPackages", packingList.getNumberOfPackages());
        snapshot.put("grossWeight", packingList.getGrossWeight());
        snapshot.put("netWeight", packingList.getNetWeight());
        snapshot.put("volume", packingList.getVolume());
        snapshot.put("remark", packingList.getRemark());
        ProformaInvoice pi = packingList.getProformaInvoice();
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

    /** 版本历史（按版本号降序） */
    @Transactional(readOnly = true)
    public List<PackingListVersion> listVersions(Long id) {
        return versionRepository.findByPackingListIdOrderByVersionNoDesc(id);
    }

    /** 获取版本 PDF 及下载文件名（生成功能暂未实现，pdf 可能为空） */
    @Transactional(readOnly = true)
    public VersionFileDto getVersionFile(Long versionId) {
        PackingListVersion version = versionRepository.findById(versionId)
                .orElseThrow(() -> BusinessException.notFound("版本", versionId));
        String filename = version.getPackingList().getPackingNo() + "-v" + version.getVersionNo() + ".pdf";
        return new VersionFileDto(version.getPdf(), filename);
    }

    /** PDF 生成（OpenPDF，暂未实现，留空函数） */
    private byte[] generatePdf(PackingList packingList) {
        return null;
    }

    private void assignPackingNo(PackingList packingList, int year) {
        int seq = sequenceStore.next(PackingList.NO_PREFIX, year);
        packingList.setPackingYear(year);
        packingList.setSeq(seq);
        packingList.setPackingNo(DocNumberGenerator.generate(PackingList.NO_PREFIX, year, seq));
    }

    private PackingListDto toDto(PackingList packingList) {
        Customer customer = packingList.getCustomer();
        PackingListDto.CustomerSummary summary = customer == null ? null
                : new PackingListDto.CustomerSummary(customer.getId(), customer.getName(), customer.getCompany());
        Long piId = packingList.getProformaInvoice() == null ? null : packingList.getProformaInvoice().getId();
        return new PackingListDto(
                packingList.getId(),
                packingList.getPackingNo(),
                packingList.getPackingDate(),
                packingList.getStatus(),
                packingList.getMarks(),
                packingList.getNumberOfPackages(),
                packingList.getGrossWeight(),
                packingList.getNetWeight(),
                packingList.getVolume(),
                packingList.getRemark(),
                summary,
                piId,
                packingList.getRootQuotationId(),
                packingList.getCreatedAt());
    }
}
