package com.yunhe.website.crm.controller;

import com.yunhe.website.common.exception.BusinessException;
import com.yunhe.website.common.validation.ReviseGroup;
import com.yunhe.website.common.web.PageUtil;
import com.yunhe.website.crm.dto.PackingLineDto;
import com.yunhe.website.crm.dto.PackingListDto;
import com.yunhe.website.crm.dto.PlFormItemView;
import com.yunhe.website.crm.dto.VersionFileDto;
import com.yunhe.website.crm.dto.DocumentChainDto;
import com.yunhe.website.crm.dto.request.PackingLineForm;
import com.yunhe.website.crm.dto.request.PackingListForm;
import com.yunhe.website.crm.entity.QuoteDetailItem;
import com.yunhe.website.crm.service.DocumentChainService;
import com.yunhe.website.crm.service.PackingListService;
import com.yunhe.website.crm.support.PlJoins;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import jakarta.validation.Valid;
import jakarta.validation.groups.Default;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * 装箱单控制器。
 */
@Controller
@RequestMapping("/crm/packing-lists")
@RequiredArgsConstructor
public class PackingListController {

    private final PackingListService packingListService;
    private final DocumentChainService documentChainService;

    @GetMapping
    @PreAuthorize("hasAuthority(T(com.yunhe.website.security.Permissions).PACKING_LIST_LIST)")
    public String list(@RequestParam(defaultValue = "0") int page,
                       @RequestParam(defaultValue = "10") int size,
                       Model model) {
        Page<PackingListDto> packingLists = packingListService.list(PageUtil.of(page, size));
        model.addAttribute("packingLists", packingLists);
        model.addAttribute("pageNumbers", PageUtil.pageNumbers(page, packingLists.getTotalPages()));
        return "packing-list/list";
    }

    @PostMapping("/generate")
    @PreAuthorize("hasAuthority(T(com.yunhe.website.security.Permissions).PACKING_LIST_CREATE)")
    public String generateFromPi(@RequestParam Long piId, RedirectAttributes redirectAttributes) {
        try {
            PackingListDto dto = packingListService.generateFromPi(piId);
            redirectAttributes.addFlashAttribute("successMessage", "装箱单创建成功：" + dto.packingNo());
            return "redirect:/crm/packing-lists/" + dto.id() + "/edit";
        } catch (BusinessException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
            return "redirect:/crm/proforma-invoices/" + piId + "/view";
        }
    }

    @GetMapping("/{id}/view")
    @PreAuthorize("hasAuthority(T(com.yunhe.website.security.Permissions).PACKING_LIST_LIST)")
    public String view(@PathVariable Long id, Model model) {
        PackingListDto packingList = packingListService.getById(id);
        model.addAttribute("packingList", packingList);
        model.addAttribute("versions", packingListService.listVersions(id));
        // 合计默认值（无数据 / 未关联报价单 → 0），与件数口径一致，避免合计行混合显示 0 / -
        int totalPkgs = 0;
        java.math.BigDecimal totalNet = java.math.BigDecimal.ZERO;
        java.math.BigDecimal totalGross = java.math.BigDecimal.ZERO;
        java.math.BigDecimal totalVol = java.math.BigDecimal.ZERO;
        int totalQty = 0;
        // 详情页以报价单项为基准遍历（与发起变更页 / PDF 一致）：plItems = 有序报价单项；
        // plLineByKey = 装箱行按 key 索引，用于按 key 命中回填件数 / 净重 / 毛重 / 体积。
        List<PlFormItemView> plItems = new ArrayList<>();
        Map<String, PackingLineDto> plLineByKey = new LinkedHashMap<>();
        if (packingList.lines() != null) {
            for (PackingLineDto l : packingList.lines()) {
                if (l.quoteLineKey() != null) plLineByKey.putIfAbsent(l.quoteLineKey(), l);
            }
        }
        if (packingList.rootQuotationId() != null) {
            var chain = documentChainService.buildChain(packingList.rootQuotationId());
            model.addAttribute("chain", chain);
            // 报价单项按 quoteLineKey 索引（详情页顶部汇总 / 兼容用）
            var plItemByKey = toItemByKey(chain);
            model.addAttribute("plItemByKey", plItemByKey);
            // 详情页货物表以报价单项为基准（有序），每个报价单项按其 key 关联装箱行
            plItems = new ArrayList<>(plItemByKey.values());
            // 合计（key 命中行，单一口径）：复用 PlJoins，与 PDF 渲染完全一致（消重合并、去重后求和）
            var plRows = PlJoins.join(packingList.lines(), chain.quotation().details());
            PlJoins.PlTotals plTotals = PlJoins.totals(plRows);
            totalPkgs = plTotals.packages();
            totalNet = plTotals.net();
            totalGross = plTotals.gross();
            totalVol = plTotals.vol();
            totalQty = plTotals.quantity();
        } else if (packingList.lines() != null) {
            // 无报价单关联（legacy）：无货物名称可取，品名回退为 key；装箱数据仍按 key 命中自身行展示
            for (PackingLineDto l : packingList.lines()) {
                plItems.add(new PlFormItemView(l.quoteLineKey(), l.quoteLineKey(), null, null, null, null));
            }
        }
        model.addAttribute("plItems", plItems);
        model.addAttribute("plLineByKey", plLineByKey);
        // 合计始终以 0 为缺省（BigDecimal.ZERO / int 0），不再以 null 退化为 "-"，与件数一致
        model.addAttribute("plTotalPkgs", totalPkgs);
        model.addAttribute("plTotalNet", totalNet);
        model.addAttribute("plTotalGross", totalGross);
        model.addAttribute("plTotalVol", totalVol);
        model.addAttribute("plTotalQty", totalQty);
        return "packing-list/detail";
    }

    @PostMapping("/{id}/generate")
    @PreAuthorize("hasAuthority(T(com.yunhe.website.security.Permissions).PACKING_LIST_UPDATE)")
    public String generate(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        packingListService.generate(id);
        redirectAttributes.addFlashAttribute("successMessage", "装箱单已生成");
        return "redirect:/crm/packing-lists/" + id + "/view";
    }

    @GetMapping("/{id}/versions/{versionId}/download")
    @PreAuthorize("hasAuthority(T(com.yunhe.website.security.Permissions).PACKING_LIST_LIST)")
    public ResponseEntity<byte[]> downloadVersion(@PathVariable Long versionId) {
        VersionFileDto file = packingListService.getVersionFile(versionId);
        if (file.pdf() == null || file.pdf().length == 0) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + file.filename() + "\"")
                .body(file.pdf());
    }

    @GetMapping("/{id}/revise")
    @PreAuthorize("hasAuthority(T(com.yunhe.website.security.Permissions).PACKING_LIST_UPDATE)")
    public String reviseForm(@PathVariable Long id, Model model) {
        model.addAttribute("packingListForm", toForm(packingListService.getById(id)));
        model.addAttribute("isEdit", true);
        model.addAttribute("isChange", true);
        addPlFormContext(model, id);
        return "packing-list/form";
    }

    @PostMapping("/{id}/revise")
    @PreAuthorize("hasAuthority(T(com.yunhe.website.security.Permissions).PACKING_LIST_UPDATE)")
    public String revise(@PathVariable Long id,
                         @Validated({Default.class, ReviseGroup.class}) @ModelAttribute("packingListForm") PackingListForm form,
                         BindingResult bindingResult,
                         Model model,
                         RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            prepareReviseModel(model);
            addPlFormContext(model, id);
            return "packing-list/form";
        }
        try {
            PackingListDto dto = packingListService.revise(id, form);
            redirectAttributes.addFlashAttribute("successMessage", "装箱单变更成功");
            return "redirect:/crm/packing-lists/" + dto.id() + "/view";
        } catch (BusinessException e) {
            model.addAttribute("errorMessage", e.getMessage());
            prepareReviseModel(model);
            return "packing-list/form";
        }
    }

    @GetMapping("/{id}/edit")
    @PreAuthorize("hasAuthority(T(com.yunhe.website.security.Permissions).PACKING_LIST_UPDATE)")
    public String editForm(@PathVariable Long id, Model model) {
        model.addAttribute("packingListForm", toForm(packingListService.getById(id)));
        model.addAttribute("isEdit", true);
        addPlFormContext(model, id);
        return "packing-list/form";
    }

    @PostMapping("/{id}")
    @PreAuthorize("hasAuthority(T(com.yunhe.website.security.Permissions).PACKING_LIST_UPDATE)")
    public String update(@PathVariable Long id,
                         @Valid @ModelAttribute("packingListForm") PackingListForm form,
                         BindingResult bindingResult,
                         Model model,
                         RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            model.addAttribute("isEdit", true);
            addPlFormContext(model, id);
            return "packing-list/form";
        }
        try {
            packingListService.update(id, form);
        } catch (BusinessException e) {
            model.addAttribute("errorMessage", e.getMessage());
            model.addAttribute("isEdit", true);
            return "packing-list/form";
        }
        redirectAttributes.addFlashAttribute("successMessage", "装箱单更新成功");
        return "redirect:/crm/packing-lists/" + id + "/view";
    }

    @PostMapping("/{id}/delete")
    @PreAuthorize("hasAuthority(T(com.yunhe.website.security.Permissions).PACKING_LIST_DELETE)")
    public String delete(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            packingListService.delete(id);
            redirectAttributes.addFlashAttribute("successMessage", "装箱单删除成功");
        } catch (BusinessException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/crm/packing-lists";
    }

    private void prepareReviseModel(Model model) {
        model.addAttribute("isEdit", true);
        model.addAttribute("isChange", true);
    }

    /**
     * 为编辑/变更表单补充「货物上下文」：加载根报价单链，并按下 item.key() 建索引，
     * 供表单按 quoteLineKey 展示每项的品名/HS Code/描述/数量（装箱字段本身在 packingListForm.lines 中）。
     */
    private void addPlFormContext(Model model, Long id) {
        PackingListDto pl = packingListService.getById(id);
        if (pl.rootQuotationId() == null) {
            return;
        }
        var chain = documentChainService.buildChain(pl.rootQuotationId());
        model.addAttribute("chain", chain);
        model.addAttribute("plItemByKey", toItemByKey(chain));
    }

    /** 展平报价单项，按下 key 建索引（用于编辑表单按 quoteLineKey 取货物字段） */
    private java.util.Map<String, PlFormItemView> toItemByKey(DocumentChainDto chain) {
        java.util.Map<String, PlFormItemView> map = new java.util.LinkedHashMap<>();
        if (chain == null || chain.quotation() == null) {
            return map;
        }
        for (var ref : PlJoins.indexByKey(chain.quotation().details()).values()) {
            var it = ref.item();
            map.put(it.key(), new PlFormItemView(
                    it.key(), ref.groupName(), ref.hsCode(), it.description(),
                    it.quantity(), it.unit()));
        }
        return map;
    }

    /** 装箱表单以报价单为基准重建明细行：严格按 quoteLineKey 命中回填装箱值，孤立于报价单的 PL 行丢弃 */
    private PackingListForm toForm(PackingListDto dto) {
        PackingListForm form = new PackingListForm();
        form.setId(dto.id());
        form.setPackingDate(dto.packingDate());
        form.setMarks(dto.marks());
        form.setRemark(dto.remark());
        // 以报价单为基准重建明细行：按 quoteLineKey 命中回填装箱值；
        // 报价单新增项 → 空行；PL 孤儿项（key 不在报价单）→ 丢弃。无报价单关联则沿用已有 PL 行。
        if (dto.rootQuotationId() != null) {
            var chain = documentChainService.buildChain(dto.rootQuotationId());
            if (chain != null && chain.quotation() != null && chain.quotation().details() != null) {
                Map<String, PackingLineDto> lineByKey = new LinkedHashMap<>();
                if (dto.lines() != null) {
                    for (PackingLineDto l : dto.lines()) {
                        if (l.quoteLineKey() != null) {
                            lineByKey.put(l.quoteLineKey(), l);
                        }
                    }
                }
                List<PackingLineForm> lines = new ArrayList<>();
                for (var ref : PlJoins.flatten(chain.quotation().details())) {
                    QuoteDetailItem it = ref.item();
                    if (it.key() == null) continue;
                    PackingLineDto existing = lineByKey.get(it.key());
                    PackingLineForm line = new PackingLineForm();
                    line.setQuoteLineKey(it.key());
                    if (existing != null) {
                        line.setPackages(existing.packages());
                        line.setNetWeight(existing.netWeight());
                        line.setGrossWeight(existing.grossWeight());
                        line.setMeasurement(existing.measurement());
                    }
                    lines.add(line);
                }
                form.setLines(lines);
                return form;
            }
        }
        // 无报价单关联：直接沿用已有 PL 行（legacy）
        if (dto.lines() != null) {
            form.setLines(dto.lines().stream().map(l -> {
                PackingLineForm line = new PackingLineForm();
                line.setQuoteLineKey(l.quoteLineKey());
                line.setPackages(l.packages());
                line.setNetWeight(l.netWeight());
                line.setGrossWeight(l.grossWeight());
                line.setMeasurement(l.measurement());
                return line;
            }).toList());
        }
        return form;
    }
}
