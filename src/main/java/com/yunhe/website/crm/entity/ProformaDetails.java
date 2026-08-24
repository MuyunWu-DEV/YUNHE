package com.yunhe.website.crm.entity;

/**
 * 形式发票详情（JSON 存储）。
 */
public record ProformaDetails(
        String seller,
        BuyerInfo buyer,
        String incoterms,
        String terms,
        String bankAccountInformation
) {

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
