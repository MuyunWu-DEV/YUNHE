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
        String warranty
) {

    /**
     * 卖方信息（对应系统设置 seller 对象的 companyName / address / phone / email）。
     */
    public record SellerInfo(
            String companyName,
            String address,
            String phone,
            String email
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
}
