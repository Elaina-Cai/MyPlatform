import { useState, useEffect, useCallback, useRef } from "react";
import { xiaoxiaoleApi } from "../api/xiaoxiaole";

const GRID_SIZE = 8;

const BLOCK_TYPES = ["🔴", "🟢", "🔵", "🟡", "🟣", "🟤"];
const RAINBOW_TYPE = "🌈";
const BLOCK_COLORS: Record<string, string> = {
  "🔴": "#ff4444",
  "🟢": "#44dd44",
  "🔵": "#4488ff",
  "🟡": "#ffdd44",
  "🟣": "#dd44ff",
  "🟤": "#8B5A2B",
  "🌈": "linear-gradient(135deg, #ff6b6b, #ffd93d, #6bcb77, #4d96ff, #9b59b6)",
};
const LEVELS = [
  { targetScore: 600, moves: 30 },
  { targetScore: 1200, moves: 30 },
  { targetScore: 1800, moves: 30 },
  { targetScore: 2400, moves: 30 },
  { targetScore: 3000, moves: 30 },
  { targetScore: 3600, moves: 30 },
  { targetScore: 4200, moves: 30 },
  { targetScore: 4800, moves: 30 },
  { targetScore: 5200, moves: 35 },
  { targetScore: 5800, moves: 35 },
];

const audioContext = typeof AudioContext !== "undefined" ? new AudioContext() : null;

function playSound(type: "match" | "combo" | "win" | "lose") {
  if (!audioContext) return;
  
  const oscillator = audioContext.createOscillator();
  const gainNode = audioContext.createGain();
  
  oscillator.connect(gainNode);
  gainNode.connect(audioContext.destination);
  
  switch (type) {
    case "match":
      oscillator.frequency.value = 800;
      oscillator.type = "sine";
      gainNode.gain.setValueAtTime(0.15, audioContext.currentTime);
      gainNode.gain.exponentialRampToValueAtTime(0.01, audioContext.currentTime + 0.15);
      oscillator.start();
      oscillator.stop(audioContext.currentTime + 0.15);
      break;
    case "combo":
      oscillator.frequency.value = 600;
      oscillator.type = "square";
      gainNode.gain.setValueAtTime(0.1, audioContext.currentTime);
      gainNode.gain.exponentialRampToValueAtTime(0.01, audioContext.currentTime + 0.3);
      oscillator.start();
      oscillator.stop(audioContext.currentTime + 0.3);
      break;
    case "win":
      const osc1 = audioContext.createOscillator();
      const osc2 = audioContext.createOscillator();
      const gain = audioContext.createGain();
      osc1.connect(gain);
      osc2.connect(gain);
      gain.connect(audioContext.destination);
      osc1.frequency.value = 523;
      osc2.frequency.value = 659;
      gain.gain.setValueAtTime(0.15, audioContext.currentTime);
      gain.gain.exponentialRampToValueAtTime(0.01, audioContext.currentTime + 0.5);
      osc1.start();
      osc2.start();
      osc1.stop(audioContext.currentTime + 0.5);
      osc2.stop(audioContext.currentTime + 0.5);
      break;
    case "lose":
      oscillator.frequency.value = 200;
      oscillator.type = "sawtooth";
      gainNode.gain.setValueAtTime(0.1, audioContext.currentTime);
      gainNode.gain.exponentialRampToValueAtTime(0.01, audioContext.currentTime + 0.4);
      oscillator.start();
      oscillator.stop(audioContext.currentTime + 0.4);
      break;
  }
}

type CellType = string;
type SpecialType = "horizontal" | "vertical" | "bomb" | "rainbow" | null;

type Cell = {
  type: CellType;
  special: SpecialType;
  id: number;
  row: number;
  col: number;
  offsetY?: number;
};

type Particle = {
  id: number;
  x: number;
  y: number;
  color: string;
  angle: number;
  speed: number;
  size: number;
};

type MatchResult = {
  row: number;
  col: number;
  type: CellType;
  isHorizontal?: boolean;
  isVertical?: boolean;
};

type Grid = (Cell | null)[][];

let cellIdCounter = 0;
let particleIdCounter = 0;

function createCell(type: CellType, row: number, col: number, special: SpecialType = null): Cell {
  return { type, special, id: cellIdCounter++, row, col };
}

function createSpecialCell(type: CellType, row: number, col: number, special: SpecialType): Cell {
  return createCell(type, row, col, special);
}

function getCellDisplay(cell: Cell | null): string {
  if (!cell) return "";
  if (cell.special === "rainbow") return "🌈";
  if (cell.special === "bomb") return "";
  if (cell.special === "horizontal") return "⇄";
  if (cell.special === "vertical") return "⇅";
  return cell.type;
}

function getSpecialColor(special: SpecialType): string {
  if (special === "horizontal") return "#4a90d9";
  if (special === "vertical") return "#4a90d9";
  if (special === "bomb") return "#f5a623";
  if (special === "rainbow") return "#ff6b6b";
  return "";
}

function getLaserCells(row: number, col: number, dir: "horizontal" | "vertical"): string[] {
  const cells: string[] = [];
  if (dir === "horizontal") {
    for (let c = 0; c < GRID_SIZE; c++) {
      cells.push(`${row},${c}`);
    }
  } else {
    for (let r = 0; r < GRID_SIZE; r++) {
      cells.push(`${r},${col}`);
    }
  }
  return cells;
}

function createParticles(matches: { row: number; col: number; type: string }[]): Particle[] {
  const particles: Particle[] = [];
  matches.forEach(({ row, col, type }) => {
    const x = col * 42 + 21;
    const y = row * 42 + 21;
    const color = BLOCK_COLORS[type] || "#ffffff";
    for (let i = 0; i < 8; i++) {
      particles.push({
        id: particleIdCounter++,
        x,
        y,
        color,
        angle: (Math.PI * 2 * i) / 8,
        speed: 2 + Math.random() * 2,
        size: 4 + Math.random() * 4,
      });
    }
  });
  return particles;
}

function createGrid(): Grid {
  const grid: Grid = [];
  for (let row = 0; row < GRID_SIZE; row++) {
    grid[row] = [];
    for (let col = 0; col < GRID_SIZE; col++) {
      let type: string;
      do {
        type = BLOCK_TYPES[Math.floor(Math.random() * BLOCK_TYPES.length)];
      } while (wouldMatch(row, col, grid, type));
      grid[row][col] = createCell(type, row, col);
    }
  }
  return grid;
}

function wouldMatch(row: number, col: number, grid: Grid, type: string): boolean {
  if (col >= 2) {
    const c1 = grid[row][col - 1]?.type;
    const c2 = grid[row][col - 2]?.type;
    if (c1 === type && c2 === type) return true;
  }
  if (row >= 2) {
    const r1 = grid[row - 1]?.[col]?.type;
    const r2 = grid[row - 2]?.[col]?.type;
    if (r1 === type && r2 === type) return true;
  }
  return false;
}

function findMatches(grid: Grid): MatchResult[] {
  const matches: Map<string, MatchResult> = new Map();

  for (let row = 0; row < GRID_SIZE; row++) {
    for (let col = 0; col < GRID_SIZE - 2; col++) {
      const type = grid[row][col]?.type;
      if (type && type === grid[row][col + 1]?.type && type === grid[row][col + 2]?.type) {
        for (let c = col; c < col + 3; c++) {
          const key = `${row},${c}`;
          if (!matches.has(key)) {
            matches.set(key, { row, col: c, type, isHorizontal: true });
          } else {
            matches.get(key)!.isHorizontal = true;
          }
        }
        if (col + 3 < GRID_SIZE && grid[row][col]?.type === grid[row][col + 3]?.type) {
          const key = `${row},${col + 3}`;
          if (!matches.has(key)) {
            matches.set(key, { row, col: col + 3, type, isHorizontal: true });
          } else {
            matches.get(key)!.isHorizontal = true;
          }
          if (col + 4 < GRID_SIZE && grid[row][col]?.type === grid[row][col + 4]?.type) {
            const key = `${row},${col + 4}`;
            if (!matches.has(key)) {
              matches.set(key, { row, col: col + 4, type, isHorizontal: true });
            } else {
              matches.get(key)!.isHorizontal = true;
            }
          }
        }
      }
    }
  }

  for (let col = 0; col < GRID_SIZE; col++) {
    for (let row = 0; row < GRID_SIZE - 2; row++) {
      const type = grid[row][col]?.type;
      if (type && type === grid[row + 1]?.[col]?.type && type === grid[row + 2]?.[col]?.type) {
        for (let r = row; r < row + 3; r++) {
          const key = `${r},${col}`;
          if (!matches.has(key)) {
            matches.set(key, { row: r, col, type, isVertical: true });
          } else {
            matches.get(key)!.isVertical = true;
          }
        }
        if (row + 3 < GRID_SIZE && grid[row][col]?.type === grid[row + 3]?.[col]?.type) {
          const key = `${row + 3},${col}`;
          if (!matches.has(key)) {
            matches.set(key, { row: row + 3, col, type, isVertical: true });
          } else {
            matches.get(key)!.isVertical = true;
          }
          if (row + 4 < GRID_SIZE && grid[row][col]?.type === grid[row + 4]?.[col]?.type) {
            const key = `${row + 4},${col}`;
            if (!matches.has(key)) {
              matches.set(key, { row: row + 4, col, type, isVertical: true });
            } else {
              matches.get(key)!.isVertical = true;
            }
          }
        }
      }
    }
  }

  return Array.from(matches.values());
}

function createSpecialFromMatch(matches: MatchResult[], _grid: Grid, swapDirection?: "horizontal" | "vertical", swapRow?: number, swapCol?: number): { row: number; col: number; special: SpecialType; type: CellType } | null {
  console.log("createSpecial:", swapDirection, "matches:", matches.length, matches.map(m => `(${m.row},${m.col},${m.type})`), "swap:", swapRow, swapCol);
  
  const matchByRow: Record<number, number[]> = {};
  const matchByCol: Record<number, number[]> = {};
  const cellTypes: Record<string, string> = {};
  matches.forEach(m => {
    if (!matchByRow[m.row]) matchByRow[m.row] = [];
    if (!matchByCol[m.col]) matchByCol[m.col] = [];
    matchByRow[m.row].push(m.col);
    matchByCol[m.col].push(m.row);
    cellTypes[`${m.row},${m.col}`] = m.type;
  });
  
  console.log("cellTypes:", cellTypes);
  
  const specialType = swapDirection === "horizontal" ? "horizontal" : "vertical";
  
  const isUserSwap = swapRow !== undefined && swapCol !== undefined;
  
  // 先检查L/T形炸弹（优先级最高）
  for (const row in matchByRow) {
    const cols = (matchByRow[Number(row)] || []).sort((a, b) => a - b);
    for (let i = 0; i <= cols.length - 3; i++) {
      if (cols[i + 2] - cols[i] !== 2) continue;
      const hColor = cellTypes[`${row},${cols[i + 1]}`];
      const hAllSame = cols.slice(i, i + 3).every(c => cellTypes[`${row},${c}`] === hColor);
      if (!hColor || !hAllSame) continue;
      for (let j = i; j <= i + 2; j++) {
        const intersectionCol = cols[j];
        const colRows = (matchByCol[intersectionCol] || []).sort((a, b) => a - b);
        for (let k = 0; k <= colRows.length - 3; k++) {
          if (colRows[k + 2] - colRows[k] !== 2) continue;
          const vColor = cellTypes[`${colRows[k + 1]},${intersectionCol}`];
          const vAllSame = colRows.slice(k, k + 3).every(r => cellTypes[`${r},${intersectionCol}`] === vColor);
          if (!vColor || !vAllSame) continue;
          if (vColor !== hColor) continue;
          const intersectionRow = Number(row);
          // 检查交叉点是否在连续的垂直方向中
          if (colRows.slice(k, k + 3).includes(intersectionRow)) {
            console.log("bomb, gen pos:", intersectionRow, intersectionCol, "color:", vColor);
            return { row: intersectionRow, col: intersectionCol, special: "bomb", type: vColor };
          }
        }
      }
    }
  }
  
  // 再检查5连（彩虹球）
  for (const row in matchByRow) {
    const cols = (matchByRow[Number(row)] || []).sort((a, b) => a - b);
    for (let i = 0; i <= cols.length - 5; i++) {
      if (cols[i + 4] - cols[i] === 4) {
        const allSameColor = cols.slice(i, i + 5).every(c => cellTypes[`${row},${c}`] === cellTypes[`${row},${cols[i + 2]}`]);
        if (!allSameColor) continue;
        const genRow = Number(row);
        const genCol = cols[i + 2];
        console.log("rainbow, gen pos:", genRow, genCol, "color:", RAINBOW_TYPE);
        return { row: genRow, col: genCol, special: "rainbow", type: RAINBOW_TYPE };
      }
    }
  }
  
  for (const col in matchByCol) {
    const rows = (matchByCol[Number(col)] || []).sort((a, b) => a - b);
    for (let i = 0; i <= rows.length - 5; i++) {
      if (rows[i + 4] - rows[i] === 4) {
        const allSameColor = rows.slice(i, i + 5).every(r => cellTypes[`${r},${col}`] === cellTypes[`${rows[i + 2]},${col}`]);
        if (!allSameColor) continue;
        const genRow = rows[i + 2];
        const genCol = Number(col);
        console.log("rainbow, gen pos:", genRow, genCol, "color:", RAINBOW_TYPE);
        return { row: genRow, col: genCol, special: "rainbow", type: RAINBOW_TYPE };
      }
    }
  }
  
  // 最后检查4连（激光）
  for (const row in matchByRow) {
    const cols = (matchByRow[Number(row)] || []).sort((a, b) => a - b);
    for (let i = 0; i <= cols.length - 4; i++) {
      if (cols[i + 3] - cols[i] === 3) {
        const color = cellTypes[`${row},${cols[i + 1]}`];
        const allSameColor = cols.slice(i, i + 4).every(c => cellTypes[`${row},${c}`] === color);
        if (!allSameColor) continue;
        let genRow, genCol;
        if (isUserSwap) {
          if (Number(row) === swapRow && cols.includes(swapCol)) {
            genRow = swapRow;
            genCol = swapCol;
          } else {
            genRow = Number(row);
            genCol = cols[i + 1];
          }
        } else {
          genRow = Number(row);
          genCol = cols[i + 1];
        }
        console.log("H special, gen pos:", genRow, genCol, "color:", color);
        return { row: genRow, col: genCol, special: specialType, type: color || "" };
      }
    }
  }
  
  for (const col in matchByCol) {
    const rows = (matchByCol[Number(col)] || []).sort((a, b) => a - b);
    for (let i = 0; i <= rows.length - 4; i++) {
      if (rows[i + 3] - rows[i] === 3) {
        const color = cellTypes[`${rows[i + 1]},${col}`];
        const allSameColor = rows.slice(i, i + 4).every(r => cellTypes[`${r},${col}`] === color);
        if (!allSameColor) continue;
        let genRow, genCol;
        if (isUserSwap) {
          if (Number(col) === swapCol && rows.includes(swapRow)) {
            genRow = swapRow;
            genCol = swapCol;
          } else {
            genRow = rows[i + 1];
            genCol = Number(col);
          }
        } else {
          genRow = rows[i + 1];
          genCol = Number(col);
        }
        console.log("V special, gen pos:", genRow, genCol, "color:", color);
        return { row: genRow, col: genCol, special: specialType, type: color || "" };
      }
    }
  }
  
  return null;
}

// 检查炸弹是否应该爆炸（周围有同色球形成一行或一列>=3）
function shouldBombExplode(grid: Grid, bombRow: number, bombCol: number): boolean {
  const bombCell = grid[bombRow]?.[bombCol];
  if (!bombCell || bombCell.special !== "bomb") return false;
  
  const bombColor = bombCell.type;
  
  // 检查横向同色球数量（包含炸弹自身）
  let hCount = 1;
  for (let c = bombCol - 1; c >= 0; c--) {
    if (grid[bombRow][c]?.type === bombColor) hCount++;
    else break;
  }
  for (let c = bombCol + 1; c < GRID_SIZE; c++) {
    if (grid[bombRow][c]?.type === bombColor) hCount++;
    else break;
  }
  
  // 检查纵向同色球数量（包含炸弹自身）
  let vCount = 1;
  for (let r = bombRow - 1; r >= 0; r--) {
    if (grid[r][bombCol]?.type === bombColor) vCount++;
    else break;
  }
  for (let r = bombRow + 1; r < GRID_SIZE; r++) {
    if (grid[r][bombCol]?.type === bombColor) vCount++;
    else break;
  }
  
  // 横向或纵向有3个或以上同色球（包括炸弹自身）
  return hCount >= 3 || vCount >= 3;
}

function dropBlocks(grid: Grid): void {
  for (let col = 0; col < GRID_SIZE; col++) {
    const columnCells: Cell[] = [];

    for (let row = GRID_SIZE - 1; row >= 0; row--) {
      if (grid[row][col] !== null) {
        columnCells.push(grid[row][col]!);
      }
    }

    for (let row = GRID_SIZE - 1; row >= 0; row--) {
      grid[row][col] = null;
    }

    for (let i = 0; i < columnCells.length; i++) {
      const toRow = GRID_SIZE - 1 - i;
      const cell = columnCells[i];
      const offset = toRow - cell.row;
      if (offset !== 0) {
        cell.offsetY = offset;
      }
      cell.row = toRow;
      grid[toRow][col] = cell;
    }

    const newCellsCount = GRID_SIZE - columnCells.length;
    for (let i = 0; i < newCellsCount; i++) {
      const toRow = i;
      const type = BLOCK_TYPES[Math.floor(Math.random() * BLOCK_TYPES.length)];
      const newCell = createCell(type, toRow, col);
      newCell.offsetY = newCellsCount;
      grid[toRow][col] = newCell;
    }
  }
}

function activateCombination(grid: Grid, special1: SpecialType, special2: SpecialType, row1: number, col1: number, row2: number, col2: number): { toRemove: string[]; extraEffects?: { type: string; cells?: string[]; newSpecial?: SpecialType }[] } {
  console.log("combination:", special1, "at", row1, col1, "+", special2, "at", row2, col2);
  const toRemove: string[] = [];
  const extraEffects: { type: string; cells?: string[]; newSpecial?: SpecialType }[] = [];
  
  // 添加两个特殊球本身到消除列表
  toRemove.push(`${row1},${col1}`, `${row2},${col2}`);
  
  // 直线+直线（同向）
  if (special1 === special2 && (special1 === "horizontal" || special1 === "vertical")) {
    toRemove.push(...getLaserCells(row1, col1, special1));
    toRemove.push(...getLaserCells(row2, col2, special1));
    console.log(`${special1}+${special2} combo:`, special1 === "horizontal" ? `rows ${row1},${row2}` : `cols ${col1},${col2}`);
    return { toRemove, extraEffects };
  }
  
  // 直线+直线（异向）- 十字消除
  if ((special1 === "horizontal" && special2 === "vertical") || (special1 === "vertical" && special2 === "horizontal")) {
    const hRow = special1 === "horizontal" ? row1 : row2;
    const hCol = special1 === "horizontal" ? col1 : col2;
    const vRow = special1 === "vertical" ? row1 : row2;
    const vCol = special1 === "vertical" ? col1 : col2;
    toRemove.push(...getLaserCells(hRow, hCol, "horizontal"));
    toRemove.push(...getLaserCells(vRow, vCol, "vertical"));
    console.log("H+V combo: row", hRow, "+ col", vCol);
    return { toRemove, extraEffects };
  }
  
  // 直线+爆炸（两种方向统一处理）
  const lineSpecial = special1 === "horizontal" || special1 === "vertical" ? special1 : 
                     (special2 === "horizontal" || special2 === "vertical" ? special2 : null);
  const isSpecial1Line = special1 === "horizontal" || special1 === "vertical";
  const lineRow = isSpecial1Line ? row1 : row2;
  const lineCol = isSpecial1Line ? col1 : col2;
  
  if (lineSpecial && (special1 === "bomb" || special2 === "bomb")) {
    // 三行/三列消除
    if (lineSpecial === "horizontal") {
      for (let r = lineRow - 1; r <= lineRow + 1; r++) {
        if (r >= 0 && r < GRID_SIZE) {
          toRemove.push(...getLaserCells(r, lineCol, "horizontal"));
        }
      }
      console.log("H+B combo: rows", lineRow - 1, "-", lineRow + 1);
    } else {
      for (let c = lineCol - 1; c <= lineCol + 1; c++) {
        if (c >= 0 && c < GRID_SIZE) {
          toRemove.push(...getLaserCells(lineRow, c, "vertical"));
        }
      }
      console.log("V+B combo: cols", lineCol - 1, "-", lineCol + 1);
    }
    return { toRemove, extraEffects };
  }
  
  // 爆炸+爆炸
  if (special1 === "bomb" && special2 === "bomb") {
    for (let dr = -2; dr <= 2; dr++) {
      for (let dc = -2; dc <= 2; dc++) {
        const nr = row1 + dr;
        const nc = col1 + dc;
        if (nr >= 0 && nr < GRID_SIZE && nc >= 0 && nc < GRID_SIZE) {
          toRemove.push(`${nr},${nc}`);
        }
        const nr2 = row2 + dr;
        const nc2 = col2 + dc;
        if (nr2 >= 0 && nr2 < GRID_SIZE && nc2 >= 0 && nc2 < GRID_SIZE) {
          toRemove.push(`${nr2},${nc2}`);
        }
      }
    }
    // 检查5x5范围内的激光，触发激光效果
    for (let dr = -2; dr <= 2; dr++) {
      for (let dc = -2; dc <= 2; dc++) {
        const nr = row1 + dr;
        const nc = col1 + dc;
        if (nr >= 0 && nr < GRID_SIZE && nc >= 0 && nc < GRID_SIZE) {
          const cell = grid[nr][nc];
          if (cell?.special === "horizontal") {
            toRemove.push(...getLaserCells(nr, nc, "horizontal"));
          } else if (cell?.special === "vertical") {
            toRemove.push(...getLaserCells(nr, nc, "vertical"));
          }
        }
      }
    }
    console.log("B+B combo: 5x5 area with laser trigger");
    return { toRemove, extraEffects };
  }
  
  // 魔力鸟+直线（染色直线）
  if ((special1 === "rainbow" && (special2 === "horizontal" || special2 === "vertical")) ||
      (special2 === "rainbow" && (special1 === "horizontal" || special1 === "vertical"))) {
    const lineType = special1 === "rainbow" ? special2 : special1;
    const lineRow = special1 === "rainbow" ? row2 : row1;
    const lineCol = special1 === "rainbow" ? col2 : col1;
    const lineColor = grid[lineRow]?.[lineCol]?.type || "";
    
    const cellsToTransform: string[] = [];
    for (let r = 0; r < GRID_SIZE; r++) {
      for (let c = 0; c < GRID_SIZE; c++) {
        if (grid[r][c]?.type === lineColor && !grid[r][c]?.special) {
          cellsToTransform.push(`${r},${c}`);
        }
      }
    }
    extraEffects.push({ type: "transform", cells: cellsToTransform, newSpecial: lineType });
    console.log("R+H/V combo: transform", cellsToTransform.length, "cells to", lineType);
    return { toRemove, extraEffects };
  }
  
  // 魔力鸟+爆炸（染色爆炸）
  if ((special1 === "rainbow" && special2 === "bomb") || (special2 === "rainbow" && special1 === "bomb")) {
    const bombRow = special1 === "rainbow" ? row2 : row1;
    const bombCol = special1 === "rainbow" ? col2 : col1;
    const bombColor = grid[bombRow]?.[bombCol]?.type || "";
    
    const cellsToTransform: string[] = [];
    for (let r = 0; r < GRID_SIZE; r++) {
      for (let c = 0; c < GRID_SIZE; c++) {
        if (grid[r][c]?.type === bombColor && !grid[r][c]?.special) {
          cellsToTransform.push(`${r},${c}`);
        }
      }
    }
    extraEffects.push({ type: "transform", cells: cellsToTransform, newSpecial: "bomb" });
    console.log("R+B combo: transform", cellsToTransform.length, "cells to bomb");
    return { toRemove, extraEffects };
  }
  
  // 魔力鸟+魔力鸟（全屏清除）
  if (special1 === "rainbow" && special2 === "rainbow") {
    for (let r = 0; r < GRID_SIZE; r++) {
      for (let c = 0; c < GRID_SIZE; c++) {
        if (grid[r][c]) {
          toRemove.push(`${r},${c}`);
        }
      }
    }
    console.log("R+R combo: clear all");
    return { toRemove, extraEffects };
  }
  
  return { toRemove, extraEffects };
}

function swapCells(grid: Grid, r1: number, c1: number, r2: number, c2: number): Grid {
  const newGrid = grid.map(row => row.map(cell => cell ? { ...cell } : null));
  [newGrid[r1][c1], newGrid[r2][c2]] = [newGrid[r2][c2], newGrid[r1][c1]];
  if (newGrid[r1][c1]) {
    newGrid[r1][c1].row = r1;
    newGrid[r1][c1].col = c1;
  }
  if (newGrid[r2][c2]) {
    newGrid[r2][c2].row = r2;
    newGrid[r2][c2].col = c2;
  }
  return newGrid;
}

export default function Match3Game() {
  const [grid, setGrid] = useState<Grid>(() => createGrid());
  const [score, setScore] = useState(0);
  const [level, setLevel] = useState(1);
  const [moves, setMoves] = useState(LEVELS[0].moves);
  const [selected, setSelected] = useState<{ row: number; col: number } | null>(null);
  const [isProcessing, setIsProcessing] = useState(false);
  const [gameStatus, setGameStatus] = useState<"playing" | "won" | "lost">("playing");
  const [showLevelComplete, setShowLevelComplete] = useState(false);
  const [fallingCells, setFallingCells] = useState<Set<number>>(new Set());
  const [particles, setParticles] = useState<Particle[]>([]);
  const [combo, setCombo] = useState(0);
  const [showCombo, setShowCombo] = useState(false);
  const [clearingCells, setClearingCells] = useState<Set<string>>(new Set());
  const [timeSpent, setTimeSpent] = useState(0);
  const gridRef = useRef<HTMLDivElement>(null);
  const comboTimeoutRef = useRef<number>();
  const animationRef = useRef<number>();
  const timerRef = useRef<number>();

  useEffect(() => {
    if (gameStatus === "playing") {
      timerRef.current = window.setInterval(() => {
        setTimeSpent(prev => prev + 1);
      }, 1000);
    } else {
      if (timerRef.current) {
        clearInterval(timerRef.current);
      }
    }

    return () => {
      if (timerRef.current) {
        clearInterval(timerRef.current);
      }
    };
  }, [gameStatus]);

  const currentLevel = LEVELS[Math.min(level - 1, LEVELS.length - 1)];

  useEffect(() => {
    if (particles.length === 0) return;

    const startTime = Date.now();
    const duration = 300;

    const animate = () => {
      const elapsed = Date.now() - startTime;
      const progress = Math.min(elapsed / duration, 1);

      setParticles(prev => prev.map(p => ({
        ...p,
        x: p.x + Math.cos(p.angle) * p.speed,
        y: p.y + Math.sin(p.angle) * p.speed + progress * 2,
      })).filter(() => progress < 1));

      if (progress < 1) {
        animationRef.current = requestAnimationFrame(animate);
      }
    };

    animationRef.current = requestAnimationFrame(animate);

    return () => {
      if (animationRef.current) {
        cancelAnimationFrame(animationRef.current);
      }
    };
  }, [particles.length]);

  // 处理特殊效果动画的统一函数
  const playSpecialEffectAnimation = async (
    gridState: Grid,
    laserEffects: { row: number; col: number; dir: "horizontal" | "vertical" }[],
    bombEffects: { row: number; col: number }[],
    initialCells: string[] = []
  ): Promise<{ removedCells: string[]; newGrid: Grid }> => {
    const resultGrid = gridState.map(r => r.map(c => c ? { ...c } : null));
    const removedCells: string[] = [];
    
    // 创建一个状态网格来跟踪哪些特殊球已经被处理
    const processedLasers = new Set<string>();
    const processedBombs = new Set<string>();
    const processedRainbows = new Set<string>();
    
    // 初始化已处理集合
    laserEffects.forEach(l => processedLasers.add(`${l.row},${l.col}`));
    bombEffects.forEach(b => processedBombs.add(`${b.row},${b.col}`));
    
    console.log("Playing special effect animation:", { laserEffects, bombEffects });
    
    // 设置初始的消除格子
    if (initialCells.length > 0) {
      const initialRemove = new Set<string>(initialCells);
      setClearingCells(initialRemove);
      playSound("match");
      await new Promise(resolve => setTimeout(resolve, 100));
    }
    
    // 使用队列来处理连锁反应
    const laserQueue = [...laserEffects];
    const bombQueue = [...bombEffects];
    const rainbowQueue: { row: number; col: number; targetColor: string }[] = [];
    
    // 处理激光队列
    while (laserQueue.length > 0 || bombQueue.length > 0 || rainbowQueue.length > 0) {
      // 处理所有当前激光
      while (laserQueue.length > 0) {
        const laser = laserQueue.shift()!;
        
        console.log("Laser animation:", laser.dir, "at", laser.row, laser.col);
        let step = 0;
        const totalSteps = GRID_SIZE;
        const stepDuration = 500 / totalSteps;
        
        while (step <= totalSteps) {
          const cells = new Set<string>();
          if (laser.dir === "horizontal") {
            const left = Math.max(0, laser.col - step);
            const right = Math.min(GRID_SIZE - 1, laser.col + step);
            for (let c = left; c <= right; c++) {
              cells.add(`${laser.row},${c}`);
              if (!removedCells.includes(`${laser.row},${c}`)) {
                removedCells.push(`${laser.row},${c}`);
                
                // 检查是否扫到了其他特殊球
                const cell = resultGrid[laser.row]?.[c];
                if (cell?.special === "bomb" && !processedBombs.has(`${laser.row},${c}`)) {
                  bombQueue.push({ row: laser.row, col: c });
                  processedBombs.add(`${laser.row},${c}`);
                } else if (cell?.special === "horizontal" && !processedLasers.has(`${laser.row},${c}`)) {
                  laserQueue.push({ row: laser.row, col: c, dir: "horizontal" });
                  processedLasers.add(`${laser.row},${c}`);
                } else if (cell?.special === "vertical" && !processedLasers.has(`${laser.row},${c}`)) {
                  laserQueue.push({ row: laser.row, col: c, dir: "vertical" });
                  processedLasers.add(`${laser.row},${c}`);
                } else if (cell?.special === "rainbow" && !processedRainbows.has(`${laser.row},${c}`)) {
                  const laserCell = resultGrid[laser.row]?.[laser.col];
                  rainbowQueue.push({ row: laser.row, col: c, targetColor: laserCell?.type || "" });
                  processedRainbows.add(`${laser.row},${c}`);
                }
              }
            }
          } else {
            const top = Math.max(0, laser.row - step);
            const bottom = Math.min(GRID_SIZE - 1, laser.row + step);
            for (let r = top; r <= bottom; r++) {
              cells.add(`${r},${laser.col}`);
              if (!removedCells.includes(`${r},${laser.col}`)) {
                removedCells.push(`${r},${laser.col}`);
                
                // 检查是否扫到了其他特殊球
                const cell = resultGrid[r]?.[laser.col];
                if (cell?.special === "bomb" && !processedBombs.has(`${r},${laser.col}`)) {
                  bombQueue.push({ row: r, col: laser.col });
                  processedBombs.add(`${r},${laser.col}`);
                } else if (cell?.special === "horizontal" && !processedLasers.has(`${r},${laser.col}`)) {
                  laserQueue.push({ row: r, col: laser.col, dir: "horizontal" });
                  processedLasers.add(`${r},${laser.col}`);
                } else if (cell?.special === "vertical" && !processedLasers.has(`${r},${laser.col}`)) {
                  laserQueue.push({ row: r, col: laser.col, dir: "vertical" });
                  processedLasers.add(`${r},${laser.col}`);
                } else if (cell?.special === "rainbow" && !processedRainbows.has(`${r},${laser.col}`)) {
                  const laserCell = resultGrid[laser.row]?.[laser.col];
                  rainbowQueue.push({ row: r, col: laser.col, targetColor: laserCell?.type || "" });
                  processedRainbows.add(`${r},${laser.col}`);
                }
              }
            }
          }
          setClearingCells(cells);
          await new Promise(resolve => setTimeout(resolve, stepDuration));
          step++;
        }
      }
      
      // 处理所有当前炸弹
      while (bombQueue.length > 0) {
        const bomb = bombQueue.shift()!;
        
        console.log("Bomb animation:", bomb.row, bomb.col);
        
        // 炸弹爆炸闪烁效果（闪烁3次）
        for (let flash = 0; flash < 3; flash++) {
          const cells = new Set<string>();
          for (let dr = -1; dr <= 1; dr++) {
            for (let dc = -1; dc <= 1; dc++) {
              const nr = bomb.row + dr, nc = bomb.col + dc;
              if (nr >= 0 && nr < GRID_SIZE && nc >= 0 && nc < GRID_SIZE) {
                cells.add(`${nr},${nc}`);
                if (!removedCells.includes(`${nr},${nc}`)) {
                  removedCells.push(`${nr},${nc}`);
                  
                  // 检查是否炸到了其他特殊球
                  const cell = resultGrid[nr]?.[nc];
                  if (cell?.special === "bomb" && !processedBombs.has(`${nr},${nc}`)) {
                    bombQueue.push({ row: nr, col: nc });
                    processedBombs.add(`${nr},${nc}`);
                  } else if (cell?.special === "horizontal" && !processedLasers.has(`${nr},${nc}`)) {
                    laserQueue.push({ row: nr, col: nc, dir: "horizontal" });
                    processedLasers.add(`${nr},${nc}`);
                  } else if (cell?.special === "vertical" && !processedLasers.has(`${nr},${nc}`)) {
                    laserQueue.push({ row: nr, col: nc, dir: "vertical" });
                    processedLasers.add(`${nr},${nc}`);
                  } else if (cell?.special === "rainbow" && !processedRainbows.has(`${nr},${nc}`)) {
                    const bombCell = resultGrid[bomb.row]?.[bomb.col];
                    rainbowQueue.push({ row: nr, col: nc, targetColor: bombCell?.type || "" });
                    processedRainbows.add(`${nr},${nc}`);
                  }
                }
              }
            }
          }
          setClearingCells(cells);
          await new Promise(resolve => setTimeout(resolve, 80));
          setClearingCells(new Set());
          await new Promise(resolve => setTimeout(resolve, 80));
        }
      }
      
      // 处理所有当前彩虹球
      while (rainbowQueue.length > 0) {
        const rainbow = rainbowQueue.shift()!;
        
        console.log("Rainbow animation:", rainbow.row, rainbow.col, "targetColor:", rainbow.targetColor);
        
        // 使用触发彩虹球的球的颜色
        const targetColor = rainbow.targetColor;
        
        if (targetColor) {
          // 消除所有该颜色的球，并触发特殊效果
          const rainbowCells = new Set<string>();
          for (let r = 0; r < GRID_SIZE; r++) {
            for (let c = 0; c < GRID_SIZE; c++) {
              const cell = resultGrid[r][c];
              if (cell?.type === targetColor && !removedCells.includes(`${r},${c}`)) {
                removedCells.push(`${r},${c}`);
                rainbowCells.add(`${r},${c}`);
                
                // 如果是特殊球（非彩虹球），触发它们的效果
                // 彩虹球没有固定颜色，不会被颜色匹配消除，只有被激光/炸弹直接命中才会触发
                if (cell.special === "horizontal" && !processedLasers.has(`${r},${c}`)) {
                  laserQueue.push({ row: r, col: c, dir: "horizontal" });
                  processedLasers.add(`${r},${c}`);
                } else if (cell.special === "vertical" && !processedLasers.has(`${r},${c}`)) {
                  laserQueue.push({ row: r, col: c, dir: "vertical" });
                  processedLasers.add(`${r},${c}`);
                } else if (cell.special === "bomb" && !processedBombs.has(`${r},${c}`)) {
                  bombQueue.push({ row: r, col: c });
                  processedBombs.add(`${r},${c}`);
                }
              }
            }
          }
          
          // 播放彩虹球动画（闪烁效果）
          for (let flash = 0; flash < 2; flash++) {
            setClearingCells(rainbowCells);
            await new Promise(resolve => setTimeout(resolve, 100));
            setClearingCells(new Set());
            await new Promise(resolve => setTimeout(resolve, 100));
          }
          setClearingCells(rainbowCells);
          await new Promise(resolve => setTimeout(resolve, 200));
          setClearingCells(new Set());
        }
      }
    }
    
    // 动画完成后实际移除格子
    const finalRemove = new Set<string>(removedCells);
    finalRemove.forEach(key => {
      const [r, c] = key.split(",").map(Number);
      if (resultGrid[r]?.[c]) {
        resultGrid[r][c] = null;
      }
    });
    setClearingCells(finalRemove);
    
    await new Promise(resolve => setTimeout(resolve, 150));
    setClearingCells(new Set());
    
    return { removedCells, newGrid: resultGrid };
  };

  const checkGameOver = useCallback(() => {
    if (gameStatus !== "playing") return;
    if (moves === 0 && score < currentLevel.targetScore) {
      setGameStatus("lost");
      saveProgress();
      playSound("lose");
    }
  }, [moves, score, currentLevel, gameStatus]);

  const processMatches = useCallback(async (currentGrid: Grid, initialCombo: number = 0, swapDirection?: "horizontal" | "vertical", swapRow?: number, swapCol?: number) => {
    let newGrid = currentGrid.map(row => row.map(cell => cell ? { ...cell } : null));
    let matches = findMatches(newGrid);
    let comboCount = initialCombo;
    
    while (matches.length > 0) {
      comboCount++;
      setCombo(comboCount);

      if (comboCount >= 2) {
        setShowCombo(true);
        if (comboTimeoutRef.current) {
          clearTimeout(comboTimeoutRef.current);
        }
        comboTimeoutRef.current = setTimeout(() => setShowCombo(false), 800) as unknown as number;
        playSound("combo");
      }

      const removed = matches.length;
      const multiplier = 1 + (comboCount - 1) * 0.5;
      setScore(prev => prev + Math.floor(removed * 10 * multiplier));

      setParticles(prev => [...prev, ...createParticles(matches)]);
      playSound("match");
      await new Promise(resolve => setTimeout(resolve, 100));

      const specialCell = createSpecialFromMatch(matches, newGrid, swapDirection, swapRow, swapCol);
      
      // 收集匹配中的特殊球信息
      const specialsInMatch: { row: number; col: number; special: SpecialType }[] = [];
      matches.forEach(({ row, col }) => {
        if (newGrid[row][col]?.special) {
          specialsInMatch.push({
            row,
            col,
            special: newGrid[row][col]!.special!
          });
        }
      });
      
      // 收集需要动画的特殊效果
      const laserEffects: { row: number; col: number; dir: "horizontal" | "vertical" }[] = [];
      const bombEffects: { row: number; col: number }[] = [];
      
      // 收集所有要消除的格子（普通匹配）
      const initialCells: string[] = [];
      matches.forEach(({ row, col }) => {
        initialCells.push(`${row},${col}`);
        if (newGrid[row][col]) {
          newGrid[row][col] = null;
        }
      });
      
      // 处理特殊球触发效果
      specialsInMatch.forEach(({ row, col, special }) => {
        console.log("Triggering special from match:", special, "at", row, col);
        if (special === "horizontal") {
          laserEffects.push({ row, col, dir: "horizontal" });
        } else if (special === "vertical") {
          laserEffects.push({ row, col, dir: "vertical" });
        } else if (special === "bomb") {
          bombEffects.push({ row, col });
        }
      });
      
      // 处理连锁反应：激光触发炸弹
      const tempGridForChain = newGrid.map(r => r.map(c => c ? { ...c } : null));
      const checkChainReactions = () => {
        let changed = true;
        while (changed) {
          changed = false;
          
          // 检查激光路径上的炸弹
          const lasersToCheck = [...laserEffects];
          lasersToCheck.forEach(({ row, col, dir }) => {
            if (dir === "horizontal") {
              for (let c = 0; c < GRID_SIZE; c++) {
                if (tempGridForChain[row]?.[c]?.special === "bomb" && !bombEffects.some(b => b.row === row && b.col === c)) {
                  bombEffects.push({ row, col: c });
                  changed = true;
                }
              }
            } else {
              for (let r = 0; r < GRID_SIZE; r++) {
                if (tempGridForChain[r]?.[col]?.special === "bomb" && !bombEffects.some(b => b.row === r && b.col === col)) {
                  bombEffects.push({ row: r, col });
                  changed = true;
                }
              }
            }
          });
          
          // 检查炸弹范围内的激光和其他炸弹
          const bombsToCheck = [...bombEffects];
          bombsToCheck.forEach(({ row, col }) => {
            for (let dr = -1; dr <= 1; dr++) {
              for (let dc = -1; dc <= 1; dc++) {
                const nr = row + dr, nc = col + dc;
                if (nr >= 0 && nr < GRID_SIZE && nc >= 0 && nc < GRID_SIZE) {
                  const cell = tempGridForChain[nr][nc];
                  if (cell?.special === "horizontal" && !laserEffects.some(l => l.row === nr && l.col === nc)) {
                    laserEffects.push({ row: nr, col: nc, dir: "horizontal" });
                    changed = true;
                  } else if (cell?.special === "vertical" && !laserEffects.some(l => l.row === nr && l.col === nc)) {
                    laserEffects.push({ row: nr, col: nc, dir: "vertical" });
                    changed = true;
                  } else if (cell?.special === "bomb" && !bombEffects.some(b => b.row === nr && b.col === nc)) {
                    bombEffects.push({ row: nr, col: nc });
                    changed = true;
                  }
                }
              }
            }
          });
        }
      };
      
      checkChainReactions();
      
      // 如果有特殊效果，播放统一动画
      if (laserEffects.length > 0 || bombEffects.length > 0) {
        const { newGrid: gridAfterAnimation } = await playSpecialEffectAnimation(
          newGrid,
          laserEffects,
          bombEffects,
          initialCells
        );
        newGrid = gridAfterAnimation;
      } else {
        // 普通消除动画
        const initialRemove = new Set<string>(initialCells);
        setClearingCells(initialRemove);
        await new Promise(resolve => setTimeout(resolve, 150));
        setClearingCells(new Set());
      }
      
      // 添加特殊方块（如果生成了的话）
      if (specialCell) {
        newGrid[specialCell.row][specialCell.col] = createSpecialCell(
          specialCell.type,
          specialCell.row,
          specialCell.col,
          specialCell.special
        );
      }
      
      // 处理下落
      dropBlocks(newGrid);
      const fallingSet = new Set<number>();
      newGrid.forEach(row => row.forEach(cell => {
        if (cell?.offsetY !== undefined) fallingSet.add(cell.id);
      }));
      setFallingCells(fallingSet);
      setGrid([...newGrid.map(r => [...r])]);
      
      await new Promise(resolve => setTimeout(resolve, 300));
      
      newGrid.forEach(row => row.forEach(cell => { if (cell) cell.offsetY = undefined; }));
      setFallingCells(new Set());
      
      // 检查新匹配
      matches = findMatches(newGrid);
    }
    
    setIsProcessing(false);
    
    // 所有效果执行完后检查游戏是否结束
    checkGameOver();
  }, [checkGameOver]);

  const handleClick = (row: number, col: number) => {
    if (isProcessing || gameStatus !== "playing") return;

    if (!selected) {
      setSelected({ row, col });
      return;
    }

    const { row: r1, col: c1 } = selected;
    const isAdjacent = 
      (Math.abs(row - r1) === 1 && col === c1) ||
      (Math.abs(col - c1) === 1 && row === r1);

    if (isAdjacent) {
      setIsProcessing(true);
      setCombo(0);
      
      const swapDirection: "horizontal" | "vertical" = 
        Math.abs(col - c1) === 1 ? "horizontal" : "vertical";
      const swapRow = row;
      const swapCol = col;
      
      const newGrid = swapCells(grid, r1, c1, row, col);
      setGrid(newGrid);
      setSelected(null);
      setMoves(prev => prev - 1);
      
      setTimeout(() => {
        const cell1 = newGrid[r1]?.[c1];
        const cell2 = newGrid[row]?.[col];
        console.log("Swap check:", "cell1:", cell1?.type, cell1?.special, "cell2:", cell2?.type, cell2?.special, "swapDir:", swapDirection);
        
        // 先检查交换后是否形成三消
        const matchesAfterSwap = findMatches(newGrid);
        console.log("Matches after swap:", matchesAfterSwap.length, matchesAfterSwap.map(m => `(${m.row},${m.col},${m.type})`));
        
        // 检查特殊元素效果触发
        const hasSpecialEffect = cell1?.special || cell2?.special;
        const isCombo = cell1?.special && cell2?.special;
        
        // 检查是否有彩虹球（彩虹球不需要三消即可激活）
        const hasRainbow = cell1?.special === "rainbow" || cell2?.special === "rainbow";
        
        console.log("hasSpecial:", hasSpecialEffect, "isCombo:", isCombo, "hasMatch:", matchesAfterSwap.length > 0, "hasRainbow:", hasRainbow);
        
        // 形成三消或组合效果或彩虹球交换时触发特殊效果
        if (hasSpecialEffect && (matchesAfterSwap.length > 0 || isCombo || hasRainbow)) {
          console.log("Special effect triggered!", cell1?.special, "+", cell2?.special);
          let toRemove: string[] = [];
          let laserPos = { row: -1, col: -1 };
          let laserDir: "horizontal" | "vertical" | null = null;
          let isBomb = false;
          let isRainbow = false;
          let rainbowTargetColor = "";
          let laserPositions: { row: number; col: number; dir: "horizontal" | "vertical" }[] = [];
          
          // 组合效果
          if (isCombo) {
            const result = activateCombination(
              newGrid,
              cell1.special!, cell2.special!,
              r1, c1, row, col
            );
            toRemove = result.toRemove;
            
            // 检查是否有染色转换效果
            const transformEffect = result.extraEffects?.find(e => e.type === "transform");
            
            if (transformEffect?.cells && transformEffect.newSpecial) {
              // 染色效果：先把同色球变成对应特效，然后激活
              const cellsToTransform = transformEffect.cells;
              const newSpecial = transformEffect.newSpecial;
              const comboGrid = newGrid.map(r => r.map(c => c ? { ...c } : null));
              
              // 收集所有要激活的特效位置
              const specialsToActivate: { row: number; col: number; special: SpecialType }[] = [];
              
              // 先转换
              cellsToTransform.forEach(key => {
                const [r, c] = key.split(",").map(Number);
                if (comboGrid[r]?.[c]) {
                  const color = comboGrid[r][c]!.type;
                  comboGrid[r][c] = createSpecialCell(color, r, c, newSpecial);
                  specialsToActivate.push({ row: r, col: c, special: newSpecial });
                }
              });
              
              // 移除原彩虹球和直线/炸弹
              toRemove.forEach(key => {
                const [r, c] = key.split(",").map(Number);
                if (comboGrid[r]?.[c]) comboGrid[r][c] = null;
              });
              setGrid([...comboGrid.map(r => [...r])]);
              
              // 立即逐一激活新生成的特效
              const activateAllSpecials = async () => {
                for (const spec of specialsToActivate) {
                  await new Promise(resolve => setTimeout(resolve, 150));
                  const newGridCopy = grid.map(r => r.map(c => c ? { ...c } : null));
                  const toRemoveSet: string[] = [];
                  
                  if (spec.special === "horizontal") {
                    for (let c = 0; c < GRID_SIZE; c++) {
                      if (newGridCopy[spec.row][c]) toRemoveSet.push(`${spec.row},${c}`);
                    }
                  } else if (spec.special === "vertical") {
                    for (let r = 0; r < GRID_SIZE; r++) {
                      if (newGridCopy[r][spec.col]) toRemoveSet.push(`${r},${spec.col}`);
                    }
                  } else if (spec.special === "bomb") {
                    for (let dr = -1; dr <= 1; dr++) {
                      for (let dc = -1; dc <= 1; dc++) {
                        const nr = spec.row + dr, nc = spec.col + dc;
                        if (nr >= 0 && nr < GRID_SIZE && nc >= 0 && nc < GRID_SIZE) {
                          toRemoveSet.push(`${nr},${nc}`);
                        }
                      }
                    }
                  }
                  
                  const toRemoveSetFinal = new Set(toRemoveSet);
                  setClearingCells(toRemoveSetFinal);
                  
                  toRemoveSet.forEach(key => {
                    const [r, c] = key.split(",").map(Number);
                    if (newGridCopy[r]?.[c]) newGridCopy[r][c] = null;
                  });
                  
                  setGrid([...newGridCopy]);
                  setScore(prev => prev + toRemoveSet.length * 15);
                }
                
                // 最后移除激活后的特效位置
                await new Promise(resolve => setTimeout(resolve, 150));
                const finalGrid = grid.map(r => r.map(c => c ? { ...c } : null));
                specialsToActivate.forEach(spec => {
                  if (finalGrid[spec.row]?.[spec.col]) {
                    finalGrid[spec.row][spec.col] = null;
                  }
                });
                setClearingCells(new Set());
                dropBlocks(finalGrid);
                setGrid([...finalGrid.map(r => [...r])]);
                setTimeout(() => setIsProcessing(false), 300);
              };
              
              activateAllSpecials();
              return;
            }
            
            // 收集所有激光位置
            if (cell1.special === "horizontal") { laserPositions.push({ row: r1, col: c1, dir: "horizontal" }); }
            else if (cell1.special === "vertical") { laserPositions.push({ row: r1, col: c1, dir: "vertical" }); }
            if (cell2.special === "horizontal") { laserPositions.push({ row, col, dir: "horizontal" }); }
            else if (cell2.special === "vertical") { laserPositions.push({ row, col, dir: "vertical" }); }
            
            // 设置主激光位置用于动画
            if (laserPositions.length > 0) {
              laserPos = laserPositions[0];
              laserDir = laserPositions[0].dir;
            }
            
            if (cell1.special === "bomb") { isBomb = true; laserPos = { row: r1, col: c1 }; }
            else if (cell1.special === "rainbow") { 
              isRainbow = true; 
              laserPos = { row: r1, col: c1 }; 
              rainbowTargetColor = cell2?.type || ""; 
            }
            if (cell2.special === "bomb") { isBomb = true; laserPos = { row, col }; }
            else if (cell2.special === "rainbow") { 
              isRainbow = true; 
              laserPos = { row, col }; 
              rainbowTargetColor = cell1?.type || ""; 
            }
            console.log("Combo effect:", { laserDir, isBomb, isRainbow, laserPos, laserPositions });
          } else {
            // 处理单个特殊元素效果
            if (cell1?.special) {
              if (cell1.special === "horizontal") {
                laserPos = { row: r1, col: c1 };
                laserDir = "horizontal";
                laserPositions.push({ row: r1, col: c1, dir: "horizontal" });
                // 检查激光路径上的炸弹
                for (let c = 0; c < GRID_SIZE; c++) {
                  toRemove.push(`${r1},${c}`);
                  if (shouldBombExplode(newGrid, r1, c)) {
                    isBomb = true;
                    for (let dr = -1; dr <= 1; dr++) {
                      for (let dc = -1; dc <= 1; dc++) {
                        const nr = r1 + dr, nc = c + dc;
                        if (nr >= 0 && nr < GRID_SIZE && nc >= 0 && nc < GRID_SIZE) {
                          toRemove.push(`${nr},${nc}`);
                        }
                      }
                    }
                  }
                }
              } else if (cell1.special === "vertical") {
                laserPos = { row: r1, col: c1 };
                laserDir = "vertical";
                laserPositions.push({ row: r1, col: c1, dir: "vertical" });
                // 检查激光路径上的炸弹
                for (let r = 0; r < GRID_SIZE; r++) {
                  toRemove.push(`${r},${c1}`);
                  if (shouldBombExplode(newGrid, r, c1)) {
                    isBomb = true;
                    for (let dr = -1; dr <= 1; dr++) {
                      for (let dc = -1; dc <= 1; dc++) {
                        const nr = r + dr, nc = c1 + dc;
                        if (nr >= 0 && nr < GRID_SIZE && nc >= 0 && nc < GRID_SIZE) {
                          toRemove.push(`${nr},${nc}`);
                        }
                      }
                    }
                  }
                }
              } else if (cell1.special === "bomb") {
                // 炸弹+普通球：检查是否应该爆炸
                if (shouldBombExplode(newGrid, r1, c1)) {
                  isBomb = true;
                  laserPos = { row: r1, col: c1 };
                  for (let dr = -1; dr <= 1; dr++) {
                    for (let dc = -1; dc <= 1; dc++) {
                      const nr = r1 + dr, nc = c1 + dc;
                      if (nr >= 0 && nr < GRID_SIZE && nc >= 0 && nc < GRID_SIZE) {
                        toRemove.push(`${nr},${nc}`);
                        // 检查爆炸范围内的激光，触发激光效果
                        const cell = newGrid[nr][nc];
                        if (cell?.special === "horizontal") {
                          laserDir = "horizontal";
                          for (let c = 0; c < GRID_SIZE; c++) toRemove.push(`${nr},${c}`);
                        } else if (cell?.special === "vertical") {
                          laserDir = "vertical";
                          for (let r = 0; r < GRID_SIZE; r++) toRemove.push(`${r},${nc}`);
                        }
                      }
                    }
                  }
                }
              } else if (cell1.special === "rainbow") {
                isRainbow = true;
                rainbowTargetColor = cell2?.type || "";
                toRemove.push(`${r1},${c1}`); // 彩虹球自身也要消除
                // 收集同色特殊球的位置，触发它们的效果
                if (rainbowTargetColor) {
                  for (let rr = 0; rr < GRID_SIZE; rr++) {
                    for (let cc = 0; cc < GRID_SIZE; cc++) {
                      const cell = newGrid[rr][cc];
                      if (cell?.type === rainbowTargetColor) {
                        if (cell.special === "horizontal") {
                          for (let c = 0; c < GRID_SIZE; c++) toRemove.push(`${rr},${c}`);
                        } else if (cell.special === "vertical") {
                          for (let r = 0; r < GRID_SIZE; r++) toRemove.push(`${r},${cc}`);
                        } else if (cell.special === "bomb") {
                          for (let dr = -1; dr <= 1; dr++) {
                            for (let dc = -1; dc <= 1; dc++) {
                              const nr = rr + dr, nc = cc + dc;
                              if (nr >= 0 && nr < GRID_SIZE && nc >= 0 && nc < GRID_SIZE) {
                                toRemove.push(`${nr},${nc}`);
                              }
                            }
                          }
                        } else {
                          toRemove.push(`${rr},${cc}`);
                        }
                      }
                    }
                  }
                }
              }
            }
            
            if (cell2?.special) {
              if (cell2.special === "horizontal") {
                laserPos = { row, col };
                laserDir = "horizontal";
                laserPositions.push({ row, col, dir: "horizontal" });
                // 检查激光路径上的炸弹
                for (let c = 0; c < GRID_SIZE; c++) {
                  toRemove.push(`${row},${c}`);
                  if (shouldBombExplode(newGrid, row, c)) {
                    isBomb = true;
                    for (let dr = -1; dr <= 1; dr++) {
                      for (let dc = -1; dc <= 1; dc++) {
                        const nr = row + dr, nc = c + dc;
                        if (nr >= 0 && nr < GRID_SIZE && nc >= 0 && nc < GRID_SIZE) {
                          toRemove.push(`${nr},${nc}`);
                        }
                      }
                    }
                  }
                }
              } else if (cell2.special === "vertical") {
                laserPos = { row, col };
                laserDir = "vertical";
                laserPositions.push({ row, col, dir: "vertical" });
                // 检查激光路径上的炸弹
                for (let r = 0; r < GRID_SIZE; r++) {
                  toRemove.push(`${r},${col}`);
                  if (shouldBombExplode(newGrid, r, col)) {
                    isBomb = true;
                    for (let dr = -1; dr <= 1; dr++) {
                      for (let dc = -1; dc <= 1; dc++) {
                        const nr = r + dr, nc = col + dc;
                        if (nr >= 0 && nr < GRID_SIZE && nc >= 0 && nc < GRID_SIZE) {
                          toRemove.push(`${nr},${nc}`);
                        }
                      }
                    }
                  }
                }
              } else if (cell2.special === "bomb") {
                // 炸弹+普通球：检查是否应该爆炸
                if (shouldBombExplode(newGrid, row, col)) {
                  isBomb = true;
                  laserPos = { row, col };
                  for (let dr = -1; dr <= 1; dr++) {
                    for (let dc = -1; dc <= 1; dc++) {
                      const nr = row + dr, nc = col + dc;
                      if (nr >= 0 && nr < GRID_SIZE && nc >= 0 && nc < GRID_SIZE) {
                        toRemove.push(`${nr},${nc}`);
                        // 检查爆炸范围内的激光，触发激光效果
                        const cell = newGrid[nr][nc];
                        if (cell?.special === "horizontal") {
                          laserDir = "horizontal";
                          for (let c = 0; c < GRID_SIZE; c++) toRemove.push(`${nr},${c}`);
                        } else if (cell?.special === "vertical") {
                          laserDir = "vertical";
                          for (let r = 0; r < GRID_SIZE; r++) toRemove.push(`${r},${nc}`);
                        }
                      }
                    }
                  }
                }
              } else if (cell2.special === "rainbow") {
                isRainbow = true;
                rainbowTargetColor = cell1?.type || "";
                toRemove.push(`${row},${col}`); // 彩虹球自身也要消除
                // 收集同色特殊球的位置，触发它们的效果
                if (rainbowTargetColor) {
                  for (let rr = 0; rr < GRID_SIZE; rr++) {
                    for (let cc = 0; cc < GRID_SIZE; cc++) {
                      const cell = newGrid[rr][cc];
                      if (cell?.type === rainbowTargetColor) {
                        if (cell.special === "horizontal") {
                          for (let c = 0; c < GRID_SIZE; c++) toRemove.push(`${rr},${c}`);
                        } else if (cell.special === "vertical") {
                          for (let r = 0; r < GRID_SIZE; r++) toRemove.push(`${r},${cc}`);
                        } else if (cell.special === "bomb") {
                          for (let dr = -1; dr <= 1; dr++) {
                            for (let dc = -1; dc <= 1; dc++) {
                              const nr = rr + dr, nc = cc + dc;
                              if (nr >= 0 && nr < GRID_SIZE && nc >= 0 && nc < GRID_SIZE) {
                                toRemove.push(`${nr},${nc}`);
                              }
                            }
                          }
                        } else {
                          toRemove.push(`${rr},${cc}`);
                        }
                      }
                    }
                  }
                }
              }
            }
          }
          
          // 使用统一的动画函数处理特殊效果
          const handleSpecialEffects = async () => {
            const localGrid = newGrid.map(r => r.map(c => c ? { ...c } : null));
            const laserEffects: { row: number; col: number; dir: "horizontal" | "vertical" }[] = [];
            const bombEffects: { row: number; col: number }[] = [];
            
            // 收集激光效果
            laserPositions.forEach(lp => {
              laserEffects.push({ row: lp.row, col: lp.col, dir: lp.dir });
            });
            
            // 收集炸弹效果
            if (isBomb && laserPos.row >= 0) {
              bombEffects.push({ row: laserPos.row, col: laserPos.col });
              
              // 检查炸弹范围内的其他特殊球，触发连锁反应
              for (let dr = -1; dr <= 1; dr++) {
                for (let dc = -1; dc <= 1; dc++) {
                  const nr = laserPos.row + dr, nc = laserPos.col + dc;
                  if (nr >= 0 && nr < GRID_SIZE && nc >= 0 && nc < GRID_SIZE) {
                    const cell = localGrid[nr][nc];
                    if (cell?.special === "horizontal") {
                      laserEffects.push({ row: nr, col: nc, dir: "horizontal" });
                    } else if (cell?.special === "vertical") {
                      laserEffects.push({ row: nr, col: nc, dir: "vertical" });
                    } else if (cell?.special === "bomb" && !bombEffects.some(b => b.row === nr && b.col === nc)) {
                      bombEffects.push({ row: nr, col: nc });
                    }
                  }
                }
              }
            }
            
            // 执行统一动画
            if (laserEffects.length > 0 || bombEffects.length > 0) {
              const { newGrid: gridAfterAnimation } = await playSpecialEffectAnimation(localGrid, laserEffects, bombEffects);
              Object.assign(localGrid, gridAfterAnimation);
              
              // 移除组合效果中的两个特殊球（如果它们还存在）
              toRemove.forEach(key => {
                const [r, c] = key.split(",").map(Number);
                if (localGrid[r]?.[c]) {
                  localGrid[r][c] = null;
                }
              });
              
              // 处理下落
              dropBlocks(localGrid);
              const fallingSet = new Set<number>();
              localGrid.forEach(row => row.forEach(cell => {
                if (cell?.offsetY !== undefined) fallingSet.add(cell.id);
              }));
              setFallingCells(fallingSet);
              setGrid([...localGrid.map(r => [...r])]);
              
              await new Promise(resolve => setTimeout(resolve, 300));
              
              localGrid.forEach(row => row.forEach(cell => { if (cell) cell.offsetY = undefined; }));
              setFallingCells(new Set());
            } else if (isRainbow) {
              // 彩虹球效果单独处理
              const toRemoveSet = new Set(toRemove);
              let flashCount = 0;
              const totalFlashes = 3;
              
              const animateRainbow = () => {
                if (flashCount < totalFlashes) {
                  if (flashCount % 2 === 0) {
                    setClearingCells(toRemoveSet);
                  } else {
                    setClearingCells(new Set());
                  }
                  flashCount++;
                  setTimeout(animateRainbow, 80);
                } else {
                  toRemove.forEach(key => {
                    const [r, c] = key.split(",").map(Number);
                    if (localGrid[r]?.[c]) {
                      localGrid[r][c] = null;
                    }
                  });
                  setClearingCells(new Set());
                  dropBlocks(localGrid);
                  setGrid([...localGrid.map(r => [...r])]);
                  setTimeout(() => setIsProcessing(false), 300);
                }
              };
              
              animateRainbow();
              return;
            } else if (toRemove.length > 0) {
              // 处理纯组合效果（没有激光/炸弹动画）
              const toRemoveSet = new Set(toRemove);
              setClearingCells(toRemoveSet);
              playSound("combo");
              
              await new Promise(resolve => setTimeout(resolve, 150));
              
              toRemove.forEach(key => {
                const [r, c] = key.split(",").map(Number);
                if (localGrid[r]?.[c]) {
                  localGrid[r][c] = null;
                }
              });
              setClearingCells(new Set());
              
              dropBlocks(localGrid);
              setGrid([...localGrid.map(r => [...r])]);
              setTimeout(() => setIsProcessing(false), 300);
              return;
            }
            
            setScore(prev => prev + toRemove.length * 15);
            playSound("combo");
            setIsProcessing(false);
          };
          
          handleSpecialEffects();
        }
        
        const matches = findMatches(newGrid);
        if (matches.length > 0) {
          processMatches(newGrid, 0, swapDirection, swapRow, swapCol);
        } else {
          const revertedGrid = swapCells(newGrid, r1, c1, row, col);
          setGrid(revertedGrid);
          setMoves(prev => prev + 1);
          setIsProcessing(false);
        }
      }, 200);
    } else {
      setSelected({ row, col });
    }
  };

  useEffect(() => {
    if (gameStatus !== "playing") return;

    if (score >= currentLevel.targetScore) {
      setGameStatus("won");
      setShowLevelComplete(true);
      saveProgress();
      playSound("win");
    }
  }, [score, currentLevel, gameStatus]);

  const saveProgress = useCallback(async () => {
    try {
      const response = await xiaoxiaoleApi.saveRecord({
        currentLevel: level,
        score: score,
        moves: currentLevel.moves - moves,
        timeSpent: timeSpent,
      });
      if (response.code === 200 && response.data) {
        console.log("Progress saved successfully:", response.data);
      }
    } catch (error) {
      console.error("Failed to save progress:", error);
    }
  }, [level, score, currentLevel.moves, moves, timeSpent]);

  const nextLevel = () => {
    if (level < LEVELS.length) {
      setLevel(prev => prev + 1);
      setMoves(LEVELS[level].moves);
      setScore(0);
      setTimeSpent(0);
      setGrid(createGrid());
      setGameStatus("playing");
      setShowLevelComplete(false);
      setSelected(null);
    }
  };

  const restartLevel = () => {
    setScore(0);
    setMoves(currentLevel.moves);
    setTimeSpent(0);
    setGrid(createGrid());
    setGameStatus("playing");
    setSelected(null);
    setIsProcessing(false);
    setFallingCells(new Set());
  };

  const restartFromBeginning = useCallback(async () => {
    try {
      const response = await xiaoxiaoleApi.resetRecord();
      if (response.code === 200) {
        setLevel(1);
        setScore(0);
        setMoves(LEVELS[0].moves);
        setTimeSpent(0);
        setGrid(createGrid());
        setGameStatus("playing");
        setSelected(null);
        setIsProcessing(false);
        setFallingCells(new Set());
      }
    } catch (error) {
      console.error("Failed to reset progress:", error);
    }
  }, []);

  const loadProgress = useCallback(async () => {
    try {
      const response = await xiaoxiaoleApi.getRecord();
      if (response.code === 200 && response.data) {
        return response.data.currentLevel || 1;
      }
    } catch (error) {
      console.error("Failed to load progress:", error);
    }
    return 1;
  }, []);

  useEffect(() => {
    loadProgress().then(level => setLevel(level));
  }, [loadProgress]);

  return (
    <div className="match3-game">
      <div className="game-info">
        <div className="info-item">
          <span className="label">关卡</span>
          <span className="value">{level}</span>
        </div>
        <div className="info-item">
          <span className="label">分数</span>
          <span className="value">{score} / {currentLevel.targetScore}</span>
        </div>
        <div className="info-item">
          <span className="label">步数</span>
          <span className="value">{moves}</span>
        </div>
        <button className="btn-secondary" onClick={restartFromBeginning}>重新开始</button>
      </div>

      <div className="grid-container" ref={gridRef}>
        <div className="grid">
          {grid.map((row, rowIndex) =>
            row.map((cell, colIndex) => {
              const isFalling = cell ? fallingCells.has(cell.id) : false;
              const display = getCellDisplay(cell);
              return (
                <div
                  key={cell?.id ?? `empty-${rowIndex}-${colIndex}`}
                  className={`cell ${selected?.row === rowIndex && selected?.col === colIndex ? "selected" : ""} ${isFalling ? "falling" : ""} ${cell?.special ? `special ${cell.special}` : ""} ${clearingCells.has(`${rowIndex},${colIndex}`) ? "clearing" : ""}`}
                  style={{
                    "--offset": cell?.offsetY ?? 0,
                    "--special-color": getSpecialColor(cell?.special || null),
                    "--cell-bg": cell?.special ? (BLOCK_COLORS[cell.type] || "#fff") : undefined,
                  } as React.CSSProperties}
                  onClick={() => handleClick(rowIndex, colIndex)}
                >
                  {cell?.special ? <span className="special-icon">{display}</span> : display}
                </div>
              );
            })
          )}
        </div>
        <canvas className="particle-canvas" width={350} height={350} />
      </div>

      {particles.length > 0 && (
        <div className="particle-layer">
          {particles.map(p => (
            <div
              key={p.id}
              className="particle"
              style={{
                left: p.x + 10,
                top: p.y + 130,
                width: p.size,
                height: p.size,
                backgroundColor: p.color,
              }}
            />
          ))}
        </div>
      )}

      {showCombo && (
        <div className="combo-display">
          Combo x{combo}!
        </div>
      )}

      {gameStatus === "lost" && (
        <div className="modal">
          <div className="modal-content">
            <h3>游戏结束</h3>
            <p>分数：{score}</p>
            <button className="btn-primary" onClick={restartLevel}>重试</button>
          </div>
        </div>
      )}

      {showLevelComplete && (
        <div className="modal">
          <div className="modal-content">
            <h3>🎉 恭喜过关！</h3>
            <p>关卡 {level} 完成！</p>
            <p>得分：{score}</p>
            <div className="modal-buttons">
              {level < LEVELS.length ? (
                <button className="btn-primary" onClick={nextLevel}>下一关</button>
              ) : (
                <div>
                  <p>🎉 你已通关所有关卡！</p>
                  <button className="btn-primary" onClick={restartFromBeginning}>重新开始</button>
                </div>
              )}
            </div>
          </div>
        </div>
      )}

      <style>{`
        .match3-game {
          display: flex;
          flex-direction: column;
          align-items: center;
          gap: 16px;
          padding: 16px;
          position: relative;
        }

        .game-info {
          display: flex;
          gap: 24px;
          background: var(--surface);
          padding: 12px 24px;
          border-radius: 12px;
        }

        .info-item {
          display: flex;
          flex-direction: column;
          align-items: center;
        }

        .info-item .label {
          font-size: 12px;
          color: var(--text-secondary);
        }

        .info-item .value {
          font-size: 18px;
          font-weight: 600;
          color: var(--text);
        }

        .grid-container {
          background: var(--surface);
          padding: 8px;
          border-radius: 12px;
          box-shadow: 0 4px 24px rgba(0, 0, 0, 0.3);
          position: relative;
        }

        .grid {
          display: grid;
          grid-template-columns: repeat(8, 40px);
          grid-template-rows: repeat(8, 40px);
          gap: 2px;
        }

        .cell {
          width: 40px;
          height: 40px;
          display: flex;
          align-items: center;
          justify-content: center;
          font-size: 28px;
          background: var(--bg);
          border-radius: 6px;
          cursor: pointer;
          transition: transform 0.1s, background 0.1s;
          user-select: none;
        }

        .cell.falling {
          animation: fall 0.3s ease-out forwards;
        }

        @keyframes fall {
          from {
            transform: translateY(calc(var(--offset) * -42px));
          }
          to {
            transform: translateY(0);
          }
        }

        .cell:hover {
          transform: scale(1.05);
          background: rgba(255, 255, 255, 0.1);
        }

        .cell.selected {
          background: rgba(255, 255, 255, 0.2);
          transform: scale(1.1);
        }

        .cell.special {
          background: var(--cell-bg) !important;
          border-radius: 50% !important;
          position: relative;
        }

        .cell.special.horizontal {
          border: 3px solid #4488ff;
          box-shadow: 
            0 0 0 2px rgba(68, 136, 255, 0.4),
            inset 0 0 10px rgba(68, 136, 255, 0.3);
          animation: laserPulse 1s ease-in-out infinite;
        }

        .cell.special.vertical {
          border: 3px solid #4488ff;
          box-shadow: 
            0 0 0 2px rgba(68, 136, 255, 0.4),
            inset 0 0 10px rgba(68, 136, 255, 0.3);
          animation: laserPulse 1s ease-in-out infinite;
        }

        @keyframes laserPulse {
          0%, 100% {
            box-shadow: 
              0 0 0 2px rgba(68, 136, 255, 0.4),
              0 0 0 5px rgba(68, 136, 255, 0.2),
              inset 0 0 10px rgba(68, 136, 255, 0.3);
          }
          50% {
            box-shadow: 
              0 0 0 3px rgba(68, 136, 255, 0.6),
              0 0 0 7px rgba(68, 136, 255, 0.3),
              inset 0 0 15px rgba(68, 136, 255, 0.4);
          }
        }

        .cell.special.bomb {
          background: var(--cell-bg) !important;
          border: none !important;
          border-radius: 50% !important;
          box-shadow: 
            0 0 0 3px rgba(255, 193, 7, 0.6),
            0 0 0 6px rgba(255, 193, 7, 0.3),
            inset 0 0 15px rgba(0, 0, 0, 0.2);
          animation: bombPulse 1s ease-in-out infinite;
        }

        @keyframes bombPulse {
          0%, 100% {
            box-shadow: 
              0 0 0 3px rgba(255, 193, 7, 0.6),
              0 0 0 6px rgba(255, 193, 7, 0.3),
              inset 0 0 15px rgba(0, 0, 0, 0.2);
          }
          50% {
            box-shadow: 
              0 0 0 4px rgba(255, 193, 7, 0.9),
              0 0 0 8px rgba(255, 193, 7, 0.4),
              inset 0 0 15px rgba(0, 0, 0, 0.2);
          }
        }

        .cell.special.rainbow {
          background: linear-gradient(135deg, #ff6b6b, #ffd93d, #6bcb77, #4d96ff, #9b59b6) !important;
          border: none !important;
          border-radius: 50% !important;
          box-shadow: 
            0 0 0 3px rgba(255, 255, 255, 0.8),
            0 0 0 6px rgba(255, 255, 255, 0.4),
            0 0 15px rgba(255, 107, 107, 0.5),
            0 0 30px rgba(78, 205, 196, 0.3);
          animation: rainbowPulse 1.5s ease-in-out infinite;
        }

        @keyframes rainbowPulse {
          0%, 100% {
            box-shadow: 
              0 0 0 3px rgba(255, 255, 255, 0.8),
              0 0 0 6px rgba(255, 255, 255, 0.4),
              0 0 15px rgba(255, 107, 107, 0.5),
              0 0 30px rgba(78, 205, 196, 0.3);
            transform: scale(1);
          }
          50% {
            box-shadow: 
              0 0 0 4px rgba(255, 255, 255, 1),
              0 0 0 8px rgba(255, 255, 255, 0.5),
              0 0 20px rgba(255, 107, 107, 0.7),
              0 0 40px rgba(78, 205, 196, 0.5);
            transform: scale(1.05);
          }
        }

        .cell.special .special-icon {
          font-size: 24px;
          z-index: 1;
          position: absolute;
          top: 50%;
          left: 50%;
          transform: translate(-50%, -50%);
        }

        @keyframes iconFloat {
          0%, 100% { transform: translate(-50%, -50%) scale(1); }
          50% { transform: translate(-50%, -50%) scale(1.1); }
        }

        .cell.clearing {
          animation: clearCell 0.2s ease-out forwards;
        }

        @keyframes clearCell {
          0% {
            transform: scale(1);
            opacity: 1;
          }
          50% {
            transform: scale(1.2);
            opacity: 0.8;
            background: rgba(255, 255, 255, 0.5) !important;
          }
          100% {
            transform: scale(0);
            opacity: 0;
          }
        }

        .laser-beam {
          position: absolute;
          background: linear-gradient(90deg, rgba(68, 136, 255, 0.8), rgba(68, 136, 255, 1), rgba(68, 136, 255, 0.8));
          z-index: 10;
          pointer-events: none;
          box-shadow: 0 0 10px rgba(68, 136, 255, 0.8), 0 0 20px rgba(68, 136, 255, 0.5);
        }

        .laser-beam.horizontal {
          height: 4px;
          top: 50%;
          transform: translateY(-50%);
          animation: laserPulseBeam 0.1s ease-in-out infinite;
        }

        .laser-beam.vertical {
          width: 4px;
          left: 50%;
          transform: translateX(-50%);
          animation: laserPulseBeam 0.1s ease-in-out infinite;
        }

        @keyframes laserPulseBeam {
          0%, 100% {
            box-shadow: 0 0 10px rgba(68, 136, 255, 0.8), 0 0 20px rgba(68, 136, 255, 0.5);
            opacity: 1;
          }
          50% {
            box-shadow: 0 0 15px rgba(68, 136, 255, 1), 0 0 30px rgba(68, 136, 255, 0.8);
            opacity: 0.9;
          }
        }

        .bomb-explosion {
          position: absolute;
          border-radius: 50%;
          background: radial-gradient(circle, rgba(255, 193, 7, 0.8), rgba(255, 100, 0, 0.6), transparent);
          z-index: 10;
          pointer-events: none;
          animation: explodeRing 0.2s ease-out forwards;
        }

        @keyframes explodeRing {
          0% {
            transform: translate(-50%, -50%) scale(0);
            opacity: 1;
          }
          100% {
            transform: translate(-50%, -50%) scale(2);
            opacity: 0;
          }
        }

        .particle-layer {
          position: absolute;
          top: 8px;
          left: 8px;
          width: 350px;
          height: 350px;
          pointer-events: none;
          overflow: hidden;
        }

        .particle {
          position: absolute;
          border-radius: 50%;
          pointer-events: none;
        }

        .particle-canvas {
          position: absolute;
          top: 0;
          left: 0;
          pointer-events: none;
        }

        .combo-display {
          position: absolute;
          top: 50%;
          left: 50%;
          transform: translate(-50%, -50%);
          font-size: 36px;
          font-weight: bold;
          color: #ffcc00;
          text-shadow: 2px 2px 4px rgba(0, 0, 0, 0.5);
          animation: comboPopup 0.8s ease-out forwards;
          pointer-events: none;
          z-index: 50;
        }

        @keyframes comboPopup {
          0% {
            transform: translate(-50%, -50%) scale(0.5);
            opacity: 0;
          }
          20% {
            transform: translate(-50%, -50%) scale(1.2);
            opacity: 1;
          }
          80% {
            transform: translate(-50%, -50%) scale(1);
            opacity: 1;
          }
          100% {
            transform: translate(-50%, -50%) scale(1.5);
            opacity: 0;
          }
        }

        .modal {
          position: fixed;
          top: 0;
          left: 0;
          right: 0;
          bottom: 0;
          background: rgba(0, 0, 0, 0.8);
          display: flex;
          align-items: center;
          justify-content: center;
          z-index: 100;
        }

        .modal-content {
          background: var(--surface);
          padding: 32px;
          border-radius: 16px;
          text-align: center;
          min-width: 280px;
        }

        .modal-content h3 {
          font-size: 24px;
          margin-bottom: 16px;
        }

        .modal-content p {
          color: var(--text-secondary);
          margin-bottom: 12px;
        }

        .modal-buttons {
          margin-top: 16px;
        }

        .btn-primary {
          background: var(--accent);
          color: white;
          border: none;
          padding: 12px 32px;
          border-radius: 8px;
          font-size: 16px;
          cursor: pointer;
        }

        .btn-primary:hover {
          background: var(--accent-hover);
        }
      `}</style>
    </div>
  );
}