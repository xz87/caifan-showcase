package com.caifan.admin.support;

import com.caifan.business.entity.SalOrder;
import com.caifan.business.vo.MonthlyStatementVO;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;

/**
 * 客户月度对账单 xlsx：汇总 + 订单明细。
 */
public final class StatementExcelWriter {

    private static final String MONEY = "#,##0.00";

    private StatementExcelWriter() {
    }

    public static byte[] write(MonthlyStatementVO vo) throws IOException {
        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet("月度对账单");
            Styles styles = new Styles(wb);

            Row title = sheet.createRow(0);
            Cell titleCell = title.createCell(0);
            titleCell.setCellValue("月度对账单 " + vo.getMonth());
            titleCell.setCellStyle(styles.title);

            Row customer = sheet.createRow(1);
            put(customer, 0, "客户：", styles.text);
            put(customer, 1, vo.getCustomerName(), styles.text);

            String[][] summary = {
                    {"订单数", String.valueOf(vo.getOrderCount())},
                    {"商品金额", vo.getGoodsAmount().toPlainString()},
                    {"运费", vo.getFreightAmount().toPlainString()},
                    {"应付合计", vo.getPayable().toPlainString()},
                    {"已付", vo.getSettled().toPlainString()},
                    {"未付", vo.getBalance().toPlainString()}
            };
            int rowIndex = 3;
            Row head = sheet.createRow(rowIndex++);
            put(head, 0, "指标", styles.header);
            put(head, 1, "数值", styles.header);
            for (String[] row : summary) {
                Row r = sheet.createRow(rowIndex++);
                put(r, 0, row[0], styles.text);
                put(r, 1, row[1], styles.text);
            }

            rowIndex++;
            String[] headers = {"销售单号", "日期", "商品金额", "运费", "应付", "已付", "未付", "状态"};
            Row tableHead = sheet.createRow(rowIndex++);
            for (int i = 0; i < headers.length; i++) {
                put(tableHead, i, headers[i], styles.header);
            }
            for (SalOrder order : vo.getOrders()) {
                Row r = sheet.createRow(rowIndex++);
                put(r, 0, order.getSaleNo(), styles.text);
                put(r, 1, order.getSaleDate() == null ? "-" : order.getSaleDate().toLocalDate().toString(), styles.text);
                putMoney(r, 2, order.getTotalAmount(), styles.money);
                putMoney(r, 3, order.getFreightAmount(), styles.money);
                putMoney(r, 4, order.getPayable(), styles.money);
                put(r, 5, "2".equals(order.getPayStatus()) ? order.getPayable().toPlainString() : "0.00", styles.text);
                put(r, 6, "2".equals(order.getPayStatus()) ? "0.00" : order.getPayable().toPlainString(), styles.text);
                put(r, 7, "2".equals(order.getPayStatus()) ? "已付清" : "未付清", styles.text);
            }

            sheet.setColumnWidth(0, 20 * 256);
            sheet.setColumnWidth(1, 14 * 256);
            for (int i = 2; i <= 7; i++) {
                sheet.setColumnWidth(i, 14 * 256);
            }
            sheet.createFreezePane(0, 0);
            try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                wb.write(out);
                return out.toByteArray();
            }
        }
    }

    private static void put(Row row, int col, String value, CellStyle style) {
        Cell cell = row.createCell(col);
        cell.setCellValue(value == null ? "" : value);
        cell.setCellStyle(style);
    }

    private static void putMoney(Row row, int col, BigDecimal value, CellStyle style) {
        Cell cell = row.createCell(col);
        cell.setCellValue(value == null ? 0D : value.doubleValue());
        cell.setCellStyle(style);
    }

    private static final class Styles {
        private final CellStyle title;
        private final CellStyle header;
        private final CellStyle text;
        private final CellStyle money;

        private Styles(XSSFWorkbook wb) {
            title = wb.createCellStyle();
            Font bold = wb.createFont();
            bold.setBold(true);
            bold.setFontHeightInPoints((short) 14);
            title.setFont(bold);

            header = wb.createCellStyle();
            Font headFont = wb.createFont();
            headFont.setBold(true);
            header.setFont(headFont);
            header.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            header.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            borders(header);

            text = wb.createCellStyle();
            borders(text);

            money = wb.createCellStyle();
            borders(money);
            money.setDataFormat(wb.createDataFormat().getFormat(MONEY));
        }

        private static void borders(CellStyle style) {
            style.setBorderTop(BorderStyle.THIN);
            style.setBorderBottom(BorderStyle.THIN);
            style.setBorderLeft(BorderStyle.THIN);
            style.setBorderRight(BorderStyle.THIN);
        }
    }
}
