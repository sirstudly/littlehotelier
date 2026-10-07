import ExcelJS from "exceljs";

export const MAX_SHEETS = 10;
export const MAX_ROWS_PER_SHEET = 5000;
export const MAX_COLUMNS = 50;

export const COLUMN_TYPES = ["text", "integer", "number", "currency", "percent", "date"] as const;
export type ColumnType = (typeof COLUMN_TYPES)[number];
export type CellValue = string | number | null;

export interface SheetColumn {
  header: string;
  type?: ColumnType;
  /** Decimal places for number / currency / percent columns. */
  decimals?: number;
}

export interface SheetSpec {
  name: string;
  title?: string;
  subtitle?: string;
  columns: SheetColumn[];
  rows: CellValue[][];
  /** Zero-based column indexes summed in a bold totals row. */
  totals?: number[];
  notes?: string[];
}

export interface WorkbookSpec {
  sheets: SheetSpec[];
  creator?: string;
}

const NUMERIC_TYPES: ReadonlySet<ColumnType> = new Set(["integer", "number", "currency", "percent"]);
const HEADER_FILL = "FFD9E1F2";
const MIN_WIDTH = 8;
const MAX_WIDTH = 50;

function decimalsSuffix(decimals: number): string {
  return decimals > 0 ? `.${"0".repeat(decimals)}` : "";
}

function numberFormat(col: SheetColumn): string | undefined {
  switch (col.type ?? "text") {
    case "integer":
      return "#,##0";
    case "number":
      return `#,##0${decimalsSuffix(col.decimals ?? 2)}`;
    case "currency":
      return `"£"#,##0${decimalsSuffix(col.decimals ?? 2)}`;
    case "percent":
      return `0${decimalsSuffix(col.decimals ?? 1)}%`;
    case "date":
      return "yyyy-mm-dd";
    default:
      return undefined;
  }
}

/** Accepts numbers or numeric strings such as "£1,234.50", "85.2%", "(12.00)". */
function toNumber(value: CellValue): number | null {
  if (typeof value === "number") return Number.isFinite(value) ? value : null;
  if (typeof value !== "string") return null;
  let s = value.trim().replace(/[£$€,\s%]/g, "");
  let negative = false;
  if (/^\(.*\)$/.test(s)) {
    negative = true;
    s = s.slice(1, -1);
  }
  if (s === "" || !/^[-+]?\d*\.?\d+(e[-+]?\d+)?$/i.test(s)) return null;
  const n = Number(s);
  return negative ? -n : n;
}

const ISO_DATE = /^(\d{4})-(\d{2})-(\d{2})(?:[T ](\d{2}):(\d{2})(?::(\d{2}))?)?/;

/** Parses ISO dates as wall-clock UTC so Excel shows the date exactly as given. */
function toDate(value: CellValue): { date: Date; hasTime: boolean } | null {
  if (typeof value !== "string") return null;
  const m = ISO_DATE.exec(value.trim());
  if (!m) return null;
  const [, y, mo, d, h, mi, s] = m;
  const date = new Date(
    Date.UTC(Number(y), Number(mo) - 1, Number(d), Number(h ?? 0), Number(mi ?? 0), Number(s ?? 0)),
  );
  if (Number.isNaN(date.getTime())) return null;
  return { date, hasTime: h !== undefined };
}

function percentToFraction(n: number): number {
  return Number((n / 100).toPrecision(15));
}

function sanitizeSheetName(name: string, used: Set<string>): string {
  const base = (name.replace(/[[\]:*?/\\]/g, " ").replace(/^'+|'+$/g, "").trim() || "Sheet").slice(0, 31);
  let candidate = base;
  for (let i = 2; used.has(candidate.toLowerCase()); i++) {
    const suffix = ` (${i})`;
    candidate = base.slice(0, 31 - suffix.length) + suffix;
  }
  used.add(candidate.toLowerCase());
  return candidate;
}

function columnLetter(index: number): string {
  let n = index + 1;
  let letters = "";
  while (n > 0) {
    const rem = (n - 1) % 26;
    letters = String.fromCharCode(65 + rem) + letters;
    n = Math.floor((n - 1) / 26);
  }
  return letters;
}

/** Rough on-screen width of a value under its column format, for auto-sizing. */
function displayLength(value: unknown, col: SheetColumn): number {
  if (value == null) return 0;
  if (value instanceof Date) return 16;
  if (typeof value === "number") {
    const type = col.type ?? "text";
    const shown = type === "percent" ? value * 100 : value;
    const decimals = type === "integer" ? 0 : (col.decimals ?? (type === "percent" ? 1 : 2));
    const text = shown.toLocaleString("en-GB", {
      minimumFractionDigits: decimals,
      maximumFractionDigits: decimals,
    });
    return text.length + (type === "currency" || type === "percent" ? 1 : 0);
  }
  return String(value).length;
}

function validateSheet(sheet: SheetSpec): void {
  if (sheet.columns.length === 0) throw new Error(`Sheet '${sheet.name}' has no columns`);
  if (sheet.columns.length > MAX_COLUMNS) {
    throw new Error(`Sheet '${sheet.name}' has ${sheet.columns.length} columns (max ${MAX_COLUMNS})`);
  }
  if (sheet.rows.length > MAX_ROWS_PER_SHEET) {
    throw new Error(`Sheet '${sheet.name}' has ${sheet.rows.length} rows (max ${MAX_ROWS_PER_SHEET})`);
  }
  sheet.rows.forEach((row, i) => {
    if (row.length > sheet.columns.length) {
      throw new Error(
        `Sheet '${sheet.name}' row ${i + 1} has ${row.length} values but only ${sheet.columns.length} columns`,
      );
    }
  });
  for (const idx of sheet.totals ?? []) {
    const col = sheet.columns[idx];
    if (!col) throw new Error(`Sheet '${sheet.name}' totals index ${idx} is out of range`);
    if (!NUMERIC_TYPES.has(col.type ?? "text")) {
      throw new Error(`Sheet '${sheet.name}' totals column '${col.header}' is not numeric`);
    }
  }
}

function writeSheet(wb: ExcelJS.Workbook, sheet: SheetSpec, name: string): void {
  const ws = wb.addWorksheet(name);
  const widths = sheet.columns.map((c) => Math.min(MAX_WIDTH, c.header.length + 2));
  let rowNum = 1;

  if (sheet.title) {
    const cell = ws.getCell(rowNum++, 1);
    cell.value = sheet.title;
    cell.font = { bold: true, size: 14 };
  }
  if (sheet.subtitle) {
    const cell = ws.getCell(rowNum++, 1);
    cell.value = sheet.subtitle;
    cell.font = { italic: true, color: { argb: "FF595959" } };
  }
  if (sheet.title || sheet.subtitle) rowNum++;

  const headerRowNum = rowNum++;
  const headerRow = ws.getRow(headerRowNum);
  sheet.columns.forEach((col, i) => {
    const cell = headerRow.getCell(i + 1);
    cell.value = col.header;
    cell.font = { bold: true };
    cell.fill = { type: "pattern", pattern: "solid", fgColor: { argb: HEADER_FILL } };
    cell.border = { bottom: { style: "thin" } };
    cell.alignment = {
      vertical: "middle",
      horizontal: NUMERIC_TYPES.has(col.type ?? "text") ? "right" : "left",
    };
  });

  const firstDataRow = rowNum;
  const sums = sheet.columns.map(() => 0);
  for (const values of sheet.rows) {
    const row = ws.getRow(rowNum++);
    sheet.columns.forEach((col, i) => {
      const raw = values[i] ?? null;
      if (raw === null || raw === "") return;
      const type = col.type ?? "text";
      const cell = row.getCell(i + 1);
      let value: string | number | Date = typeof raw === "number" ? raw : String(raw);
      let format = numberFormat(col);

      if (NUMERIC_TYPES.has(type)) {
        const n = toNumber(raw);
        if (n !== null) {
          sums[i] += n;
          value = type === "percent" ? percentToFraction(n) : n;
        } else {
          format = undefined;
        }
      } else if (type === "date") {
        const parsed = toDate(raw);
        if (parsed) {
          value = parsed.date;
          if (parsed.hasTime) format = "yyyy-mm-dd hh:mm";
        } else {
          format = undefined;
        }
      }

      cell.value = value;
      if (format) cell.numFmt = format;
      widths[i] = Math.max(widths[i], displayLength(value, col));
    });
  }
  const lastDataRow = rowNum - 1;

  if (sheet.totals && sheet.totals.length > 0) {
    const totalsRow = ws.getRow(rowNum++);
    const totalsSet = new Set(sheet.totals);
    sheet.columns.forEach((col, i) => {
      const cell = totalsRow.getCell(i + 1);
      cell.font = { bold: true };
      cell.border = { top: { style: "thin" } };
      if (!totalsSet.has(i)) return;
      const letter = columnLetter(i);
      const result = Number(
        (col.type === "percent" ? percentToFraction(sums[i]) : sums[i]).toPrecision(15),
      );
      cell.value =
        lastDataRow >= firstDataRow
          ? { formula: `SUM(${letter}${firstDataRow}:${letter}${lastDataRow})`, result }
          : 0;
      const format = numberFormat(col);
      if (format) cell.numFmt = format;
      widths[i] = Math.max(widths[i], displayLength(result, col));
    });
    if (!totalsSet.has(0)) {
      totalsRow.getCell(1).value = "Total";
    }
  }

  if (sheet.notes && sheet.notes.length > 0) {
    rowNum++;
    for (const note of sheet.notes) {
      const cell = ws.getCell(rowNum++, 1);
      cell.value = note;
      cell.font = { italic: true, color: { argb: "FF595959" } };
    }
  }

  ws.views = [{ state: "frozen", ySplit: headerRowNum, xSplit: 0 }];
  if (lastDataRow >= firstDataRow) {
    ws.autoFilter = {
      from: { row: headerRowNum, column: 1 },
      to: { row: lastDataRow, column: sheet.columns.length },
    };
  }
  widths.forEach((w, i) => {
    ws.getColumn(i + 1).width = Math.min(MAX_WIDTH, Math.max(MIN_WIDTH, w + 2));
  });
}

export async function buildWorkbook(spec: WorkbookSpec): Promise<Buffer> {
  if (spec.sheets.length === 0) throw new Error("At least one sheet is required");
  if (spec.sheets.length > MAX_SHEETS) {
    throw new Error(`${spec.sheets.length} sheets requested (max ${MAX_SHEETS})`);
  }
  spec.sheets.forEach(validateSheet);

  const wb = new ExcelJS.Workbook();
  wb.creator = spec.creator ?? "Ronbot";
  wb.created = new Date();
  const used = new Set<string>();
  for (const sheet of spec.sheets) {
    writeSheet(wb, sheet, sanitizeSheetName(sheet.name, used));
  }
  return Buffer.from(await wb.xlsx.writeBuffer());
}
