package com.java3y.austin.report.document;

import org.apache.poi.xwpf.usermodel.ParagraphAlignment;
import org.apache.poi.xwpf.usermodel.TableRowAlign;
import org.apache.poi.xwpf.usermodel.TextAlignment;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFFooter;
import org.apache.poi.xwpf.usermodel.XWPFHeader;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFStyle;
import org.apache.poi.xwpf.usermodel.XWPFStyles;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTColor;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTFldChar;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTFonts;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTPageMar;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTPageSz;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTPPrGeneral;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTRPr;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTShd;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTSpacing;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTStyle;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTTblCellMar;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTTblGrid;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTTblPr;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTTblWidth;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTTcPr;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTTrPr;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STFldCharType;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STLineSpacingRule;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STStyleType;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STTblWidth;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Currency;
import java.util.List;

/**
 * 使用 standard_business_brief 设计令牌和 compact editorial_cover 标题区生成月报。
 */
@Component
public class MonthlyReportDocumentGenerator {
    public static final String DOCX_CONTENT_TYPE =
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
    private static final int CONTENT_WIDTH_DXA = 9360;
    private static final int TABLE_INDENT_DXA = 120;
    private static final String BLUE = "2E74B5";
    private static final String DARK_BLUE = "1F4D78";
    private static final String HEADER_FILL = "F2F4F7";
    private static final String MUTED = "666666";
    private static final String REPORT_FONT = "Heiti SC";
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public byte[] generate(MonthlyReportData data) {
        validate(data);
        try (XWPFDocument document = new XWPFDocument();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            configurePage(document);
            configureStyles(document);
            addHeaderFooter(document, data);
            addCover(document, data);
            addSummary(document, data);
            addUsage(document, data);
            addFees(document, data);
            addQuotaLedger(document, data);
            addMethodology(document);
            document.write(output);
            return output.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("生成Word月报失败", exception);
        }
    }

    private void configurePage(XWPFDocument document) {
        CTPageSz size = document.getDocument().getBody().addNewSectPr().addNewPgSz();
        size.setW(BigInteger.valueOf(12240));
        size.setH(BigInteger.valueOf(15840));
        CTPageMar margins = document.getDocument().getBody().getSectPr().addNewPgMar();
        margins.setTop(BigInteger.valueOf(1440));
        margins.setRight(BigInteger.valueOf(1440));
        margins.setBottom(BigInteger.valueOf(1440));
        margins.setLeft(BigInteger.valueOf(1440));
        margins.setHeader(BigInteger.valueOf(708));
        margins.setFooter(BigInteger.valueOf(708));
    }

    private void configureStyles(XWPFDocument document) {
        XWPFStyles styles = document.createStyles();
        addParagraphStyle(styles, "AustinBody", "Austin Body", null,
                11, null, false, 0, 120, 264);
        addParagraphStyle(styles, "AustinH1", "Austin Heading 1", "AustinBody",
                16, BLUE, true, 320, 160, 264);
        addParagraphStyle(styles, "AustinH2", "Austin Heading 2", "AustinBody",
                13, BLUE, true, 240, 120, 264);
        addParagraphStyle(styles, "AustinH3", "Austin Heading 3", "AustinBody",
                12, DARK_BLUE, true, 160, 80, 264);
        addParagraphStyle(styles, "AustinTable", "Austin Table", "AustinBody",
                9, null, false, 0, 0, 240);
        addParagraphStyle(styles, "AustinTableCompact", "Austin Table Compact", "AustinBody",
                8, null, false, 0, 0, 220);
    }

    private void addParagraphStyle(XWPFStyles styles, String id, String name, String basedOn,
                                   int fontSize, String color, boolean bold,
                                   int before, int after, int line) {
        CTStyle ctStyle = CTStyle.Factory.newInstance();
        ctStyle.setStyleId(id);
        ctStyle.setType(STStyleType.PARAGRAPH);
        ctStyle.addNewName().setVal(name);
        if (basedOn != null) {
            ctStyle.addNewBasedOn().setVal(basedOn);
        }
        CTPPrGeneral pPr = ctStyle.addNewPPr();
        CTSpacing spacing = pPr.addNewSpacing();
        spacing.setBefore(BigInteger.valueOf(before));
        spacing.setAfter(BigInteger.valueOf(after));
        spacing.setLine(BigInteger.valueOf(line));
        spacing.setLineRule(STLineSpacingRule.AUTO);
        CTRPr rPr = ctStyle.addNewRPr();
        CTFonts fonts = rPr.addNewRFonts();
        fonts.setAscii(REPORT_FONT);
        fonts.setHAnsi(REPORT_FONT);
        fonts.setEastAsia(REPORT_FONT);
        fonts.setCs(REPORT_FONT);
        rPr.addNewSz().setVal(BigInteger.valueOf(fontSize * 2L));
        rPr.addNewSzCs().setVal(BigInteger.valueOf(fontSize * 2L));
        if (color != null) {
            CTColor styleColor = rPr.addNewColor();
            styleColor.setVal(color);
        }
        if (bold) {
            rPr.addNewB().setVal(Boolean.TRUE);
        }
        styles.addStyle(new XWPFStyle(ctStyle));
    }

    private void addHeaderFooter(XWPFDocument document, MonthlyReportData data) {
        XWPFHeader header = document.createHeader(org.apache.poi.wp.usermodel.HeaderFooterType.DEFAULT);
        XWPFParagraph headerParagraph = header.getParagraphs().isEmpty()
                ? header.createParagraph() : header.getParagraphs().get(0);
        headerParagraph.setStyle("AustinTable");
        headerParagraph.setAlignment(ParagraphAlignment.LEFT);
        XWPFRun headerRun = headerParagraph.createRun();
        applyRunFont(headerRun, 9);
        headerRun.setText("Austin  |  月度消息用量与账务报告  |  " + data.getTenantName());
        headerRun.setColor(MUTED);

        XWPFFooter footer = document.createFooter(org.apache.poi.wp.usermodel.HeaderFooterType.DEFAULT);
        XWPFParagraph footerParagraph = footer.getParagraphs().isEmpty()
                ? footer.createParagraph() : footer.getParagraphs().get(0);
        footerParagraph.setAlignment(ParagraphAlignment.RIGHT);
        footerParagraph.setStyle("AustinTable");
        XWPFRun label = footerParagraph.createRun();
        applyRunFont(label, 9);
        label.setText("第 ");
        addPageField(footerParagraph);
        XWPFRun pageSuffix = footerParagraph.createRun();
        applyRunFont(pageSuffix, 9);
        pageSuffix.setText(" 页");
    }

    private void addPageField(XWPFParagraph paragraph) {
        XWPFRun run = paragraph.createRun();
        CTFldChar begin = run.getCTR().addNewFldChar();
        begin.setFldCharType(STFldCharType.BEGIN);
        XWPFRun instruction = paragraph.createRun();
        instruction.getCTR().addNewInstrText().setStringValue(" PAGE ");
        XWPFRun endRun = paragraph.createRun();
        CTFldChar end = endRun.getCTR().addNewFldChar();
        end.setFldCharType(STFldCharType.END);
    }

    private void addCover(XWPFDocument document, MonthlyReportData data) {
        XWPFParagraph spacer = document.createParagraph();
        spacer.setSpacingAfter(480);
        XWPFParagraph kicker = document.createParagraph();
        kicker.setAlignment(ParagraphAlignment.CENTER);
        kicker.setSpacingAfter(120);
        XWPFRun kickerRun = kicker.createRun();
        applyRunFont(kickerRun, 10);
        kickerRun.setText("MONTHLY REPORT");
        kickerRun.setBold(true);
        kickerRun.setColor(BLUE);
        kickerRun.setFontSize(10);

        XWPFParagraph title = document.createParagraph();
        title.setAlignment(ParagraphAlignment.CENTER);
        title.setSpacingAfter(120);
        XWPFRun titleRun = title.createRun();
        applyRunFont(titleRun, 24);
        titleRun.setText("Austin 月度消息用量与账务报告");
        titleRun.setBold(true);
        titleRun.setFontSize(24);
        titleRun.setColor(DARK_BLUE);

        XWPFParagraph subtitle = document.createParagraph();
        subtitle.setAlignment(ParagraphAlignment.CENTER);
        subtitle.setSpacingAfter(360);
        XWPFRun subtitleRun = subtitle.createRun();
        applyRunFont(subtitleRun, 12);
        subtitleRun.setText(data.getTenantName() + "  |  " + data.getPeriodStart().format(DATE)
                + " 至 " + data.getPeriodEnd().minusDays(1).format(DATE));
        subtitleRun.setFontSize(12);
        subtitleRun.setColor(MUTED);

        XWPFTable metadata = table(document, new int[]{1800, 7560}, false);
        row(metadata, new String[]{"租户 ID", String.valueOf(data.getTenantId())}, false, false);
        row(metadata, new String[]{"统计时区", data.getTenantTimezone()}, false, false);
        row(metadata, new String[]{"生成口径", "自然月；期末余额为报告生成时账户快照"}, false, false);
    }

    private void addSummary(XWPFDocument document, MonthlyReportData data) {
        heading(document, "1. 核心摘要", 1);
        XWPFTable table = table(document, new int[]{4680, 4680}, true);
        row(table, new String[]{"指标", "数值"}, true, false);
        row(table, new String[]{"成功消息用量", String.valueOf(data.getSettledUsage())}, false, false);
        row(table, new String[]{"当月购买额度", String.valueOf(data.getPurchasedQuota())}, false, false);
        row(table, new String[]{"当前可用额度", String.valueOf(data.getAvailableQuota())}, false, false);
        row(table, new String[]{"当前预占额度", String.valueOf(data.getReservedQuota())}, false, false);
        if (data.getFeeSummaries().isEmpty()) {
            row(table, new String[]{"当月支付费用", "无支付成功订单"}, false, false);
        } else {
            for (FeeSummaryRow fee : data.getFeeSummaries()) {
                row(table, new String[]{"当月支付费用（" + fee.getCurrency() + "）",
                        money(fee.getAmountMinor(), fee.getCurrency()) + " / "
                                + fee.getOrderCount() + " 笔"}, false, false);
            }
        }
    }

    private void addUsage(XWPFDocument document, MonthlyReportData data) {
        heading(document, "2. 消息用量", 1);
        body(document, "成功消息用量按额度流水中的 SETTLE 记录统计；失败、拦截和最终释放的额度不计入用量。");
        XWPFTable table = table(document, new int[]{4680, 4680}, true);
        row(table, new String[]{"消息模板 ID", "成功用量"}, true, false);
        if (data.getTemplateUsage().isEmpty()) {
            row(table, new String[]{"—", "本月无成功用量"}, false, false);
        } else {
            for (TemplateUsageRow usage : data.getTemplateUsage()) {
                row(table, new String[]{usage.getTemplateId(), String.valueOf(usage.getUsageAmount())},
                        false, false);
            }
        }
    }

    private void addFees(XWPFDocument document, MonthlyReportData data) {
        heading(document, "3. 费用与购买记录", 1);
        body(document, "费用按支付成功时间归属月份，并按币种分别汇总；不同币种不会折算后合计。");
        XWPFTable table = table(document, new int[]{2500, 1200, 1200, 1960, 2500}, true);
        row(table, new String[]{"订单号", "提供方", "币种", "金额", "购买额度"}, true, true);
        if (data.getPaymentDetails().isEmpty()) {
            row(table, new String[]{"—", "—", "—", "本月无支付成功订单", "—"}, false, true);
        } else {
            for (PaymentDetailRow payment : data.getPaymentDetails()) {
                row(table, new String[]{payment.getOrderNo(), payment.getProvider(), payment.getCurrency(),
                        money(payment.getAmountMinor(), payment.getCurrency()),
                        String.valueOf(payment.getQuotaAmount())}, false, true);
            }
        }
        if (data.isPaymentDetailsTruncated()) {
            note(document, "支付明细超过展示上限，文档仅保留前 1,000 条；费用汇总仍基于完整数据。", "9B1C1C");
        }
    }

    private void addQuotaLedger(XWPFDocument document, MonthlyReportData data) {
        heading(document, "4. 额度流水", 1);
        body(document, "流水展示可用额度与预占额度的增减及变更后余额，按发生时间升序排列。");
        XWPFTable table = table(document, new int[]{1420, 1120, 1760, 1760, 3300}, true);
        row(table, new String[]{"时间", "类型", "变动", "变更后", "业务标识"}, true, true);
        if (data.getQuotaLedger().isEmpty()) {
            row(table, new String[]{"—", "—", "—", "—", "本月无额度流水"}, false, true);
        } else {
            for (QuotaLedgerRow ledger : data.getQuotaLedger()) {
                row(table, new String[]{formatTime(ledger.getCreated(), data.getTenantTimezone()),
                        ledger.getEntryType(),
                        "可用 " + signed(ledger.getAvailableDelta()) + "\n预占 " + signed(ledger.getReservedDelta()),
                        "可用 " + ledger.getAvailableAfter() + "\n预占 " + ledger.getReservedAfter(),
                        ledger.getBusinessType() + " / " + ledger.getBusinessId()}, false, true);
            }
        }
        if (data.isQuotaLedgerTruncated()) {
            note(document, "额度流水超过展示上限，文档仅保留前 5,000 条；核心摘要仍基于完整聚合数据。", "9B1C1C");
        }
    }

    private void addMethodology(XWPFDocument document) {
        heading(document, "5. 数据口径", 1);
        labeledBody(document, "用量：", "仅统计实际投递成功并完成额度结算的数量。");
        labeledBody(document, "费用：", "统计期内状态为支付成功的订单金额，金额使用最小货币单位存储并按币种展示。");
        labeledBody(document, "余额：", "可用额度和预占额度为报告生成时快照，不代表月末历史快照。");
    }

    private XWPFTable table(XWPFDocument document, int[] widths, boolean grid) {
        int sum = 0;
        for (int width : widths) {
            sum += width;
        }
        if (sum != CONTENT_WIDTH_DXA) {
            throw new IllegalArgumentException("Word表格列宽之和必须为9360 DXA");
        }
        XWPFTable table = document.createTable(1, widths.length);
        table.setTableAlignment(TableRowAlign.LEFT);
        table.setCellMargins(80, 120, 80, 120);
        CTTblPr properties = table.getCTTbl().getTblPr();
        CTTblWidth width = properties.isSetTblW() ? properties.getTblW() : properties.addNewTblW();
        width.setType(STTblWidth.DXA);
        width.setW(BigInteger.valueOf(CONTENT_WIDTH_DXA));
        CTTblWidth indent = properties.isSetTblInd() ? properties.getTblInd() : properties.addNewTblInd();
        indent.setType(STTblWidth.DXA);
        indent.setW(BigInteger.valueOf(TABLE_INDENT_DXA));
        CTTblCellMar margins = properties.isSetTblCellMar()
                ? properties.getTblCellMar() : properties.addNewTblCellMar();
        setWidth(margins.isSetTop() ? margins.getTop() : margins.addNewTop(), 80);
        setWidth(margins.isSetBottom() ? margins.getBottom() : margins.addNewBottom(), 80);
        setWidth(margins.isSetStart() ? margins.getStart() : margins.addNewStart(), 120);
        setWidth(margins.isSetEnd() ? margins.getEnd() : margins.addNewEnd(), 120);
        if (grid) {
            table.setStyleID("TableGrid");
        }
        CTTblGrid tableGrid = table.getCTTbl().getTblGrid() == null
                ? table.getCTTbl().addNewTblGrid() : table.getCTTbl().getTblGrid();
        while (tableGrid.sizeOfGridColArray() > 0) {
            tableGrid.removeGridCol(0);
        }
        for (int columnWidth : widths) {
            tableGrid.addNewGridCol().setW(BigInteger.valueOf(columnWidth));
        }
        table.setWidth(CONTENT_WIDTH_DXA);
        org.openxmlformats.schemas.wordprocessingml.x2006.main.CTTblLayoutType layout =
                properties.isSetTblLayout() ? properties.getTblLayout() : properties.addNewTblLayout();
        layout.setType(org.openxmlformats.schemas.wordprocessingml.x2006.main.STTblLayoutType.FIXED);
        return table;
    }

    private void row(XWPFTable table, String[] values, boolean header, boolean compact) {
        XWPFTableRow row = table.getNumberOfRows() == 1 && placeholder(table.getRow(0))
                ? table.getRow(0) : table.createRow();
        while (row.getTableCells().size() < values.length) {
            row.createCell();
        }
        CTTrPr rowProperties = row.getCtRow().isSetTrPr()
                ? row.getCtRow().getTrPr() : row.getCtRow().addNewTrPr();
        rowProperties.addNewCantSplit().setVal(Boolean.TRUE);
        if (header) {
            rowProperties.addNewTblHeader().setVal(Boolean.TRUE);
        }
        for (int index = 0; index < values.length; index++) {
            XWPFTableCell cell = row.getCell(index);
            CTTcPr cellProperties = cell.getCTTc().isSetTcPr()
                    ? cell.getCTTc().getTcPr() : cell.getCTTc().addNewTcPr();
            CTTblWidth cellWidth = cellProperties.isSetTcW()
                    ? cellProperties.getTcW() : cellProperties.addNewTcW();
            cellWidth.setType(STTblWidth.DXA);
            cellWidth.setW(table.getCTTbl().getTblGrid().getGridColArray(index).getW());
            cell.setVerticalAlignment(XWPFTableCell.XWPFVertAlign.CENTER);
            XWPFParagraph paragraph = cell.getParagraphs().get(0);
            paragraph.setStyle(compact ? "AustinTableCompact" : "AustinTable");
            paragraph.setAlignment(index == 0 && !header
                    ? ParagraphAlignment.LEFT : ParagraphAlignment.CENTER);
            paragraph.setVerticalAlignment(TextAlignment.CENTER);
            XWPFRun run = paragraph.createRun();
            applyRunFont(run, compact ? 8 : 9);
            String[] lines = safe(values[index]).split("\\n", -1);
            for (int line = 0; line < lines.length; line++) {
                if (line > 0) {
                    run.addBreak();
                }
                run.setText(lines[line]);
            }
            if (header) {
                run.setBold(true);
                CTShd shading = cellProperties.isSetShd()
                        ? cellProperties.getShd() : cellProperties.addNewShd();
                shading.setFill(HEADER_FILL);
            }
        }
    }

    private void setWidth(CTTblWidth width, int dxa) {
        width.setType(STTblWidth.DXA);
        width.setW(BigInteger.valueOf(dxa));
    }

    private void heading(XWPFDocument document, String text, int level) {
        XWPFParagraph paragraph = document.createParagraph();
        paragraph.setStyle(level == 1 ? "AustinH1" : level == 2 ? "AustinH2" : "AustinH3");
        XWPFRun run = paragraph.createRun();
        applyRunFont(run, level == 1 ? 16 : level == 2 ? 13 : 12);
        run.setText(text);
    }

    private void body(XWPFDocument document, String text) {
        XWPFParagraph paragraph = document.createParagraph();
        paragraph.setStyle("AustinBody");
        XWPFRun run = paragraph.createRun();
        applyRunFont(run, 11);
        run.setText(text);
    }

    private void labeledBody(XWPFDocument document, String label, String text) {
        XWPFParagraph paragraph = document.createParagraph();
        paragraph.setStyle("AustinBody");
        XWPFRun labelRun = paragraph.createRun();
        applyRunFont(labelRun, 11);
        labelRun.setBold(true);
        labelRun.setText(label);
        XWPFRun bodyRun = paragraph.createRun();
        applyRunFont(bodyRun, 11);
        bodyRun.setText(text);
    }

    private void note(XWPFDocument document, String text, String color) {
        XWPFParagraph paragraph = document.createParagraph();
        paragraph.setStyle("AustinBody");
        XWPFRun run = paragraph.createRun();
        applyRunFont(run, 11);
        run.setBold(true);
        run.setColor(color);
        run.setText(text);
    }

    private String money(long amountMinor, String currencyCode) {
        Currency currency = Currency.getInstance(currencyCode);
        int scale = Math.max(currency.getDefaultFractionDigits(), 0);
        return currencyCode + " " + BigDecimal.valueOf(amountMinor, scale).toPlainString();
    }

    private String formatTime(Integer epochSecond, String timezone) {
        if (epochSecond == null) {
            return "—";
        }
        return Instant.ofEpochSecond(epochSecond).atZone(ZoneId.of(timezone)).format(DATE_TIME);
    }

    private String signed(Long value) {
        long number = value == null ? 0L : value;
        return number > 0 ? "+" + number : String.valueOf(number);
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private boolean placeholder(XWPFTableRow row) {
        for (XWPFTableCell cell : row.getTableCells()) {
            if (!cell.getText().isEmpty()) {
                return false;
            }
        }
        return true;
    }

    private void applyRunFont(XWPFRun run, int size) {
        run.setFontFamily(REPORT_FONT);
        run.setFontSize(size);
        CTRPr properties = run.getCTR().isSetRPr()
                ? run.getCTR().getRPr() : run.getCTR().addNewRPr();
        CTFonts fonts = properties.sizeOfRFontsArray() > 0
                ? properties.getRFontsArray(0) : properties.addNewRFonts();
        fonts.setAscii(REPORT_FONT);
        fonts.setHAnsi(REPORT_FONT);
        fonts.setEastAsia(REPORT_FONT);
        fonts.setCs(REPORT_FONT);
    }

    private void validate(MonthlyReportData data) {
        if (data == null || data.getTenantId() == null || data.getTenantName() == null
                || data.getTenantTimezone() == null || data.getPeriodStart() == null
                || data.getPeriodEnd() == null || !data.getPeriodStart().isBefore(data.getPeriodEnd())) {
            throw new IllegalArgumentException("月报数据不完整");
        }
        requireList(data.getTemplateUsage(), "模板用量");
        requireList(data.getFeeSummaries(), "费用汇总");
        requireList(data.getPaymentDetails(), "支付明细");
        requireList(data.getQuotaLedger(), "额度流水");
    }

    private void requireList(List<?> values, String name) {
        if (values == null) {
            throw new IllegalArgumentException(name + "不能为空");
        }
    }
}
