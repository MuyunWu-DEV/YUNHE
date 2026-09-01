package com.yunhe.website.crm.entity;

/**
 * 形式发票详情（JSON 存储）。
 */
public record ProformaDetails(
        SellerInfo seller,
        BuyerInfo buyer,
        String incoterms,
        String terms,
        String bankAccountInformation,
        /** 质保条款（单行短文本，用于 PI PDF 的 WARRANTY 行；老数据为 null 视为空） */
        String warranty,
        /** 运输路线（装货港 / 目的港），用于 PI/CI/PL 的 META 盒；老数据为 null 视为未填 */
        RouteInfo route
) {

    /**
     * 卖方信息（对应系统设置 seller 对象的 companyName / address / phone / email / chineseName）。
     */
    public record SellerInfo(
            String companyName,
            String address,
            String phone,
            String email,
            String chineseName
    ) {
    }

    /**
     * 买方信息（对应客户档案的 company / registrationNo / address）。
     */
    public record BuyerInfo(
            String companyName,
            String registrationNo,
            String address
    ) {
    }

    /**
     * 运输路线（装货港 / 目的港）。
     * <p>PI/CI/PL 的 META 盒优先展示此处；为 null 时回退到 seller/buyer 地址拼接。</p>
     */
    public record RouteInfo(
            String portOfLoading,
            String portOfDestination
    ) {
    }

    /** 取装货港（route 优先，缺省回退卖方地址） */
    public String portOfLoading() {
        if (route != null && nonBlank(route.portOfLoading())) {
            return route.portOfLoading();
        }
        return seller != null ? seller.address() : null;
    }

    /** 取目的港（route 优先，缺省回退买方地址） */
    public String portOfDestination() {
        if (route != null && nonBlank(route.portOfDestination())) {
            return route.portOfDestination();
        }
        return buyer != null ? buyer.address() : null;
    }

    private static boolean nonBlank(String s) {
        return s != null && !s.isBlank();
    }
}
