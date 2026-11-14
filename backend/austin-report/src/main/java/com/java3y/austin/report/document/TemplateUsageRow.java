package com.java3y.austin.report.document;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class TemplateUsageRow {
    private String templateId;
    private Long usageAmount;
}
