package com.yunhe.website.crm.dto;

/**
 * 版本 PDF 下载文件（pdf 二进制 + 下载文件名）。
 */
public record VersionFileDto(byte[] pdf, String filename) {
}
