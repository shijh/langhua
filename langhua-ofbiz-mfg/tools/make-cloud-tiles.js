/*
 * make-cloud-tiles.js — 画登录卡片那道云纹，并生成四张可平铺的带子 + 四个角花。
 *
 * 为什么自己画：原来那两张云纹是 iconfont.cn 下载后改的，iconfont 没有统一许可证、
 * 版权归上传者，也没法确认那套图标是“可商用”还是“仅学习交流”；而我们是把它切碎、镜像、
 * 加渐变遮罩后随产品分发（改编 + 再分发），比“在页面里显示原图”更依赖授权范围。
 * 这里的路径是程序生成的，版权归本公司，`THIRD-PARTY-NOTICES.md` 第 6 节可以直接写“自有”。
 *
 * 画法（卷云）：给缎带的**曲率**一个正弦，再积分出中心线
 *     κ(s) = CURL · sin(2π s / (LENGTH / 2))
 * 曲率大的地方自然卷成云头，曲率反过来就卷向另一侧；整条线只有一个参数要调（CURL）。
 * κ 在两端都是 0 且整段积分为 0，所以中心线两头**水平**、高度相同 —— 平铺接缝天然对得上，
 * 平铺块的宽度就是这条线自己的水平跨度（不是写死的数）。
 *
 * 用法（仓库根）：node langhua-ofbiz-mfg/tools/make-cloud-tiles.js
 * 输出：两个组件 `webapp/<组件>/images/` 下的 cloud-{top,bottom,left,right}.svg 与
 *      corner-{tl,tr,bl,br}.svg，外加一份预览 `%TEMP%\jup\cloud-preview.html`（只看不提交）。
 *
 * 结构沿用原来那套：图形只当遮罩（白色，`fill` 不能漏，漏了默认黑色就整块不露），
 * 颜色由渐变矩形给（内框朱砂 → 外沿纸色）；四个方向靠 transform 复用同一份图形，
 * 所以四面朝向天然一致。
 */
'use strict';

const fs = require('fs');
const os = require('os');
const path = require('path');

const ROOTS = [
    path.resolve(__dirname, '..', 'webapp', 'mfg', 'images'),
    path.resolve(__dirname, '..', '..', 'langhua-ofbiz-learningcurve', 'webapp', 'learningcurve', 'images')
];

const BAND = 1.8;                  // mm，带子的厚度（卡片上就是 1.8mm）
const INNER_COLOUR = '#c3272b';    // --cinnabar，靠卡片内侧那一端
const OUTER_COLOUR = '#dcc69c';    // --paper-edge，卡片外沿那一端

const LENGTH = 8.4;                // mm，一条带子的弧长
const CURL = 3.15;                 // 1/mm，最大曲率；最小卷半径 = 1 / CURL
const THICKNESS = 0.22;            // mm，缎带的粗细

function num(value) {
    return String(Math.round(value * 1000) / 1000);
}

/** 一段轮廓数据包成 <path>（遮罩里的图形必须是元素，不能是裸的 d 字符串）。 */
function pathOf(d) {
    return '<path d="' + d + '"/>';
}

/**
 * 把中心线画成一条等宽（或渐细）的缎带，返回 path 的 d。
 * @param {{x:number,y:number}[]} line 中心线（采样点）
 * @param {(t:number)=>number} halfWidth 半宽，t 是 0..1 的进度
 */
function ribbon(line, halfWidth) {
    const half = line.map((_, i) => halfWidth(i / Math.max(1, line.length - 1)));
    const left = [];
    const right = [];
    for (let i = 0; i < line.length; i++) {
        const prev = line[Math.max(0, i - 1)];
        const next = line[Math.min(line.length - 1, i + 1)];
        let tx = next.x - prev.x;
        let ty = next.y - prev.y;
        const length = Math.hypot(tx, ty) || 1;
        tx /= length;
        ty /= length;
        left.push({ x: line[i].x + ty * half[i], y: line[i].y - tx * half[i] });
        right.push({ x: line[i].x - ty * half[i], y: line[i].y + tx * half[i] });
    }
    const points = left.concat(right.slice().reverse());
    const d = ['M ' + num(points[0].x) + ',' + num(points[0].y)];
    for (let i = 1; i < points.length; i++) {
        d.push('L ' + num(points[i].x) + ',' + num(points[i].y));
    }
    d.push('Z');
    return d.join(' ');
}

/** 缎带端点的圆头（四段贝塞尔近似），免得卷的收尾是个平口。 */
function cap(centre, half) {
    const k = 0.5522847498 * half;
    const at = (dx, dy) => num(centre.x + dx) + ',' + num(centre.y + dy);
    return 'M ' + at(0, -half)
        + ' C ' + at(k, -half) + ' ' + at(half, -k) + ' ' + at(half, 0)
        + ' C ' + at(half, k) + ' ' + at(k, half) + ' ' + at(0, half)
        + ' C ' + at(-k, half) + ' ' + at(-half, k) + ' ' + at(-half, 0)
        + ' C ' + at(-half, -k) + ' ' + at(-k, -half) + ' ' + at(0, -half)
        + ' Z';
}

/**
 * 按曲率积分出一条中心线（中点法）。
 * @param {(s:number)=>number} curvature s 处的曲率（1/mm）
 * @param {number} length 弧长
 * @param {{x:number,y:number}} start 起点（切线水平）
 * @param {number} steps 采样段数
 */
function integrate(curvature, length, start, steps) {
    const ds = length / steps;
    const points = [{ x: start.x, y: start.y }];
    let theta = 0;
    let x = start.x;
    let y = start.y;
    for (let i = 0; i < steps; i++) {
        theta += curvature((i + 0.5) * ds) * ds;
        x += Math.cos(theta) * ds;
        y += Math.sin(theta) * ds;
        points.push({ x, y });
    }
    return points;
}

function bounds(points) {
    const xs = points.map((p) => p.x);
    const ys = points.map((p) => p.y);
    return {
        minX: Math.min.apply(null, xs), maxX: Math.max.apply(null, xs),
        minY: Math.min.apply(null, ys), maxY: Math.max.apply(null, ys),
        width: Math.max.apply(null, xs) - Math.min.apply(null, xs),
        height: Math.max.apply(null, ys) - Math.min.apply(null, ys)
    };
}

/**
 * 云头：从 start 长出去的一段半径递减螺旋。`dir` 为 +1 时圆心在 start 下方（向下卷），
 * -1 时在上方（向上卷）；起点切线水平，正好接在浪峰 / 浪谷上。
 * 粗细从 thickness0 收到 thickness1，收尾配一个圆头（否则是个平口）。
 */
function spiralAt(start, dir, radius0, radius1, turns, steps) {
    radius0 = radius0 || 0.34;
    radius1 = radius1 || 0.12;
    turns = turns || 1.15;
    steps = steps || 200;
    const centre = { x: start.x, y: start.y + dir * radius0 };
    const total = turns * 2 * Math.PI;
    const points = [];
    for (let i = 0; i <= steps; i++) {
        const t = i / steps;
        const angle = -dir * (Math.PI / 2) + dir * total * t;
        const radius = radius0 + (radius1 - radius0) * t;
        points.push({
            x: centre.x + radius * Math.cos(angle),
            y: centre.y + radius * Math.sin(angle)
        });
    }
    const halfAt = (t) => (THICKNESS + (0.05 - THICKNESS) * t) / 2;
    return { points, halfAt };
}

function bounds(points) {
    let minX = Infinity;
    let maxX = -Infinity;
    let minY = Infinity;
    let maxY = -Infinity;
    for (const p of points) {
        minX = Math.min(minX, p.x);
        maxX = Math.max(maxX, p.x);
        minY = Math.min(minY, p.y);
        maxY = Math.max(maxY, p.y);
    }
    return { minX, maxX, minY, maxY, width: maxX - minX, height: maxY - minY };
}

/**
 * 云带：一条平缓的浪 + 浪峰/浪谷上各长一个云头（递增半径收小的螺旋）。
 * 浪是整数个周期，两端 y 与斜率相同 —— 平铺接缝天然对得上。
 */
function bandShape() {
    const mid = BAND / 2;
    const amplitude = 0.27;
    const steps = 240;
    const line = [];
    for (let i = 0; i <= steps; i++) {
        const x = (LENGTH * i) / steps;
        line.push({ x, y: mid + amplitude * Math.sin((2 * Math.PI * x) / LENGTH) });
    }
    const half = THICKNESS / 2;
    const art = [pathOf(ribbon(line, () => half))];

    // 浪峰（x = 1/4）处向下卷、浪谷（x = 3/4）处向上卷，卷向中线
    const crest = { x: LENGTH * 0.25, y: mid + amplitude };
    const trough = { x: LENGTH * 0.75, y: mid - amplitude };
    for (const one of [spiralAt(crest, 1, 0.42, 0.10, 0.92, 220), spiralAt(trough, -1, 0.42, 0.10, 0.92, 220)]) {
        art.push(pathOf(ribbon(one.points, one.halfAt)));
        art.push(pathOf(cap(one.points[one.points.length - 1], 0.028)));
    }

    return { width: LENGTH, height: BAND, art: art.join('') };
}

/**
 * 角花（Step 264 起不用了，留着这段是因为配色/接缝的取舍还有可能回到它）：
 * 一个压在对角线上的云头，外端朝着卡片外角。1.8mm 见方时它读起来是一块糊在一起的点，
 * 而且两侧的带子会被 1.8mm 的 clip 切成断头，所以现在让四条带子直接铺到角上。
 */
function cornerArt() {
    const centre = { x: BAND * 0.56, y: BAND * 0.56 };
    const outer = 0.70;
    const innerR = 0.13;
    const turns = 1.45;
    const steps = 260;
    const points = [];
    for (let i = 0; i <= steps; i++) {
        const t = i / steps;
        // 从卡片外角那个方向（左上）开始，向内扫
        const angle = -Math.PI * 0.75 + turns * 2 * Math.PI * t;
        const radius = outer + (innerR - outer) * t;
        points.push({
            x: centre.x + radius * Math.cos(angle),
            y: centre.y + radius * Math.sin(angle)
        });
    }
    const halfAt = (t) => (0.22 + (0.06 - 0.22) * t) / 2;
    return pathOf(ribbon(points, halfAt))
        + pathOf(cap(points[0], 0.11))
        + pathOf(cap(points[points.length - 1], 0.03));
}

// ---- 输出 -------------------------------------------------------------------

const OPEN = '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 ';
const shape = bandShape();

function gradient(id, y1, y2) {
    return '<linearGradient id="' + id + '" gradientUnits="userSpaceOnUse" x1="0" y1="' + num(y1)
        + '" x2="0" y2="' + num(y2) + '">'
        + '<stop offset="0" stop-color="' + OUTER_COLOUR + '"/>'
        + '<stop offset="1" stop-color="' + INNER_COLOUR + '"/>'
        + '</linearGradient>';
}

/**
 * 一张带子。`wrap` 作用在渐变矩形上（不是遮罩上）：渐变是 userSpaceOnUse，
 * 跟着矩形一起转，所以四条边都是“外沿纸色 → 内侧朱砂”。
 */
function bandTile(width, height, wrap) {
    const art = '<g id="art" fill="#ffffff">' + shape.art + '</g>'
        + '<use href="#art" transform="translate(-' + num(shape.width) + ',0)"/>'
        + '<use href="#art" transform="translate(' + num(shape.width) + ',0)"/>';
    const rect = '<rect x="0" y="0" width="' + num(shape.width) + '" height="' + num(BAND)
        + '" fill="url(#bandGradient)" mask="url(#bandMask)"/>';
    return OPEN + num(width) + ' ' + num(height) + '">'
        + '<defs>' + gradient('bandGradient', 0, BAND)
        + '<mask id="bandMask" maskUnits="userSpaceOnUse" x="0" y="0" width="' + num(shape.width)
        + '" height="' + num(BAND) + '">' + art + '</mask>'
        + '</defs>'
        + (wrap ? '<g transform="' + wrap + '">' + rect + '</g>' : rect)
        + '</svg>';
}

function cornerTile(mirror, bottom) {
    return OPEN + num(BAND) + ' ' + num(BAND) + '">'
        + '<defs>' + gradient('cornerGradient', bottom ? BAND : 0, bottom ? 0 : BAND)
        + '<mask id="cornerMask" maskUnits="userSpaceOnUse" x="0" y="0" width="' + num(BAND)
        + '" height="' + num(BAND) + '">'
        + '<g transform="' + mirror + '" fill="#ffffff">' + cornerArt() + '</g>'
        + '</mask>'
        + '</defs>'
        + '<rect x="0" y="0" width="' + num(BAND) + '" height="' + num(BAND)
        + '" fill="url(#cornerGradient)" mask="url(#cornerMask)"/>'
        + '</svg>';
}

const tiles = {
    'cloud-top.svg': bandTile(shape.width, BAND, ''),
    'cloud-bottom.svg': bandTile(shape.width, BAND, 'translate(0,' + num(BAND) + ') scale(1,-1)'),
    'cloud-left.svg': bandTile(BAND, shape.width, 'translate(0,' + num(shape.width) + ') rotate(-90)'),
    'cloud-right.svg': bandTile(BAND, shape.width, 'translate(' + num(BAND) + ',0) rotate(90)')
};

for (const root of ROOTS) {
    if (!fs.existsSync(root)) {
        console.log('skip (no such folder): ' + root);
        continue;
    }
    for (const name of Object.keys(tiles)) {
        fs.writeFileSync(path.join(root, name), tiles[name], 'utf8');
    }
    console.log('wrote ' + Object.keys(tiles).length + ' tiles to ' + root);
}
console.log('band tile: ' + num(shape.width) + ' x ' + BAND + ' mm, scroll height ' + num(shape.height));

// ---- 预览（不进仓库） --------------------------------------------------------

const url = (name) => 'file:///' + path.join(ROOTS[0], name).replace(/\\/g, '/');
const preview = [
    '<!doctype html><meta charset="utf-8"><title>cloud preview</title>',
    '<body style="margin:0;background:#f6efe2;font:13px sans-serif">',
    '<p style="margin:8px">放大（1mm = 100px）：曲线与平铺接缝</p>',
    '<div style="background:url(' + url('cloud-top.svg') + ') repeat-x;background-size:'
    + num(shape.width * 100) + 'px 180px;height:180px;width:1700px;'
    + 'border-top:1px solid #ccc;border-bottom:1px solid #ccc"></div>',
    '<p style="margin:8px">真实尺寸（1.8mm ≈ 6.8px）与 4 倍：</p>',
    '<div style="background:url(' + url('cloud-top.svg') + ') repeat-x;background-size:auto 1.8mm;'
    + 'height:1.8mm;width:600px;border:1px solid #ccc"></div>',
    '<div style="margin-top:6px;background:url(' + url('cloud-top.svg') + ') repeat-x;'
    + 'background-size:auto 7.2mm;height:7.2mm;width:600px;border:1px solid #ccc"></div>',
    '<p style="margin:8px">左右两条（转 90° 用的同一张图）与四条边拼起来的样子：</p>',
    '<div style="display:flex;gap:16px;align-items:flex-start">',
    '<div style="width:180px;height:420px;background:url(' + url('cloud-left.svg') + ') repeat-y;'
    + 'background-size:180px auto;outline:1px solid #ccc"></div>',
    '<div style="width:420px;height:150px;outline:1px solid #ccc;position:relative;background:#f6efe2;'
    + 'border-radius:9px;overflow:hidden">',
    '<div style="position:absolute;inset:0;background:'
    + 'url(' + url('cloud-top.svg') + ') repeat-x left top/auto 20px,'
    + 'url(' + url('cloud-bottom.svg') + ') repeat-x left bottom/auto 20px,'
    + 'url(' + url('cloud-left.svg') + ') repeat-y left top/20px auto,'
    + 'url(' + url('cloud-right.svg') + ') repeat-y right top/20px auto"></div>',
    '</div>',
    '</div>'
].join('\n');

const previewDir = path.join(os.tmpdir(), 'jup');
fs.mkdirSync(previewDir, { recursive: true });
fs.writeFileSync(path.join(previewDir, 'cloud-preview.html'), preview, 'utf8');
console.log('preview: ' + path.join(previewDir, 'cloud-preview.html'));
