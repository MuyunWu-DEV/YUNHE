package com.yunhe.website.crm.service;

import com.yunhe.website.common.exception.BusinessException;
import com.yunhe.website.common.sequence.SequenceStore;
import com.yunhe.website.crm.dto.PackingListDto;
import com.yunhe.website.crm.dto.PackingLineDto;
import com.yunhe.website.crm.dto.VersionFileDto;
import com.yunhe.website.crm.dto.request.PackingLineForm;
import com.yunhe.website.crm.dto.request.PackingListForm;
import com.yunhe.website.crm.entity.Customer;
import com.yunhe.website.crm.entity.DocumentStatus;
import com.yunhe.website.crm.entity.PackingLine;
import com.yunhe.website.crm.entity.PackingList;
import com.yunhe.website.crm.entity.PackingListVersion;
import com.yunhe.website.crm.entity.ProformaInvoice;
import com.yunhe.website.crm.entity.QuoteDetailGroup;
import com.yunhe.website.crm.entity.QuoteDetailItem;
import com.yunhe.website.crm.entity.Quotation;
import com.yunhe.website.crm.repository.PackingListRepository;
import com.yunhe.website.crm.repository.PackingListVersionRepository;
import com.yunhe.website.crm.repository.ProformaInvoiceRepository;
import com.yunhe.website.crm.repository.QuotationRepository;
import com.yunhe.website.crm.support.DocNumberGenerator;
import com.yunhe.website.crm.support.PlPdfRenderer;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.util.ArrayList;
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
    private final QuotationRepository quotationRepository;
    private final PackingListVersionRepository versionRepository;
    private final ObjectMapper objectMapper;
    private final SequenceStore sequenceStore;
    private final PlPdfRenderer plPdfRenderer;

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
        // 由报价单项播种装箱行：每行关联 quoteLineKey，装箱字段留空待用户填写（单一数据源 = 报价单）
        packingList.setLines(seedLinesFromQuotation(pi.getQuotation()));
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

    /** 将表单字段写入实体（整单合计由 lines 派生，不在此冗余存储） */
    private void applyForm(PackingList packingList, PackingListForm form) {
        packingList.setPackingDate(form.getPackingDate());
        packingList.setMarks(form.getMarks());
        // 逐货物项装箱信息：经 quoteLineKey 对齐报价单项，装箱字段覆盖写入
        if (form.getLines() != null) {
            List<PackingLine> lines = form.getLines().stream()
                    .map(f -> new PackingLine(
                            f.getQuoteLineKey(),
                            f.getPackages(),
                            f.getNetWeight(),
                            f.getGrossWeight(),
                            f.getMeasurement()))
                    .toList();
            packingList.setLines(lines);
        }
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

    /** 报价单变更后自动重新生成：先按位置重锚装箱行 key 对齐当前报价单，再重生成 PDF */
    @Transactional
    public void regenerateAfterQuotationChange(Long id, String changeReason) {
        packingListRepository.findById(id).ifPresent(pl -> {
            resyncLinesToQuotation(pl, resolveQuotation(pl));
            packingListRepository.save(pl);
        });
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
        snapshot.put("lines", packingList.getLines());
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

    /** PDF 生成（OpenPDF，经 PlPdfRenderer 渲染；货物字段按 quoteLineKey JOIN 根报价单） */
    private byte[] generatePdf(PackingList packingList) {
        return plPdfRenderer.render(packingList, resolveQuotation(packingList));
    }

    /** 解析装箱单对应的根报价单（优先 rootQuotationId，回退 PI 关联的报价单） */
    private Quotation resolveQuotation(PackingList packingList) {
        Quotation quotation = null;
        if (packingList.getRootQuotationId() != null) {
            quotation = quotationRepository.findById(packingList.getRootQuotationId()).orElse(null);
        }
        if (quotation == null && packingList.getProformaInvoice() != null) {
            quotation = packingList.getProformaInvoice().getQuotation();
        }
        return quotation;
    }

    /**
     * 按报价单项严格 key 对齐装箱行（取代历史的位置重锚）。
     * <ul>
     *   <li>报价单项在 PL 中存在同 key 装箱行 → 保留其已填装箱值（key 命中，直接更新/复用）；</li>
     *   <li>报价单项为 PL 新增（key 不在 PL 中）→ 插入空装箱行（packages/净重/毛重/体积 = null）；</li>
     *   <li>PL 中存在、但报价单已无该 key 的孤儿行 → 移除（不写入新集合）。</li>
     * </ul>
     * 最终装箱行顺序与数量与当前报价单完全一致，单一来源 = 报价单；无位置兜底。
     * <p>声明为 {@code static} + 包可见，便于纯单元测试（无需构造服务实例）。</p>
     */
    static void resyncLinesToQuotation(PackingList packingList, Quotation quotation) {
        if (quotation == null || quotation.getDetails() == null) {
            return;
        }
        if (packingList.getLines() == null) {
            packingList.setLines(new ArrayList<>());
        }
        Map<String, PackingLine> byKey = new LinkedHashMap<>();
        for (PackingLine l : packingList.getLines()) {
            if (l.quoteLineKey() != null && !l.quoteLineKey().isBlank()) {
                byKey.putIfAbsent(l.quoteLineKey(), l);
            }
        }
        List<QuoteDetailItem> items = quotation.getDetails().stream()
                .filter(g -> g.items() != null)
                .flatMap(g -> g.items().stream())
                .toList();
        List<PackingLine> resynced = new ArrayList<>(items.size());
        for (QuoteDetailItem it : items) {
            if (it.key() == null) continue;
            PackingLine existing = byKey.get(it.key());
            if (existing != null) {
                resynced.add(existing); // key 命中：保留已填装箱值
            } else {
                resynced.add(new PackingLine(it.key(), null, null, null, null)); // 报价单新增项：空行
            }
        }
        packingList.setLines(resynced);
    }

    /** 由报价单项播种装箱行：每行绑定 item.key()，装箱字段留空（null）待填 */
    private List<PackingLine> seedLinesFromQuotation(Quotation quotation) {
        List<PackingLine> lines = new ArrayList<>();
        if (quotation == null || quotation.getDetails() == null) {
            return lines;
        }
        for (QuoteDetailGroup g : quotation.getDetails()) {
            if (g.items() == null) continue;
            for (QuoteDetailItem it : g.items()) {
                lines.add(new PackingLine(it.key(), null, null, null, null));
            }
        }
        return lines;
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
        List<PackingLineDto> lineDtos = packingList.getLines() == null ? List.of()
                : packingList.getLines().stream().map(l -> new PackingLineDto(
                        l.quoteLineKey(), l.packages(), l.netWeight(), l.grossWeight(), l.measurement()))
                .toList();
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
                lineDtos,
                packingList.getRemark(),
                summary,
                piId,
                packingList.getRootQuotationId(),
                packingList.getCreatedAt());
    }
}
