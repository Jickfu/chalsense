import { createServer } from "node:http";
import { execFile } from "node:child_process";
import { readFile } from "node:fs/promises";
import { extname, resolve, sep } from "node:path";
import { deflateSync } from "node:zlib";

const root = resolve(import.meta.dirname, "..");
const port = Number(process.env.CHALLSENSE_WIDGET_PORT ?? 4173);
const crcTable = Array.from({ length: 256 }, (_, value) => {
  let crc = value;
  for (let bit = 0; bit < 8; bit += 1) crc = (crc & 1) ? 0xedb88320 ^ (crc >>> 1) : crc >>> 1;
  return crc >>> 0;
});

function crc32(buffer) {
  let crc = 0xffffffff;
  for (const byte of buffer) crc = (crc >>> 8) ^ crcTable[(crc ^ byte) & 0xff];
  return (crc ^ 0xffffffff) >>> 0;
}

function chunk(type, data) {
  const typeBytes = Buffer.from(type, "ascii");
  const output = Buffer.alloc(12 + data.length);
  output.writeUInt32BE(data.length, 0);
  typeBytes.copy(output, 4);
  data.copy(output, 8);
  output.writeUInt32BE(crc32(Buffer.concat([typeBytes, data])), 8 + data.length);
  return output;
}

function png(width, height, pixel) {
  const raw = Buffer.alloc((width * 4 + 1) * height);
  for (let y = 0; y < height; y += 1) {
    const row = y * (width * 4 + 1);
    raw[row] = 0;
    for (let x = 0; x < width; x += 1) {
      const [r, g, b, a] = pixel(x, y);
      const offset = row + 1 + x * 4;
      raw[offset] = r; raw[offset + 1] = g; raw[offset + 2] = b; raw[offset + 3] = a;
    }
  }
  const header = Buffer.alloc(13);
  header.writeUInt32BE(width, 0); header.writeUInt32BE(height, 4);
  header[8] = 8; header[9] = 6;
  return Buffer.concat([
    Buffer.from([137, 80, 78, 71, 13, 10, 26, 10]),
    chunk("IHDR", header), chunk("IDAT", deflateSync(raw)), chunk("IEND", Buffer.alloc(0)),
  ]);
}

const background = png(320, 180, (x, y) => {
  const hole = x >= 190 && x < 240 && y >= 70 && y < 120;
  const border = hole && (x < 194 || x >= 236 || y < 74 || y >= 116);
  if (border) return [38, 72, 108, 255];
  if (hole) return [210, 220, 230, 255];
  if (y > 130) return [63, 139, 104, 255];
  return [150 + Math.floor(x / 8), 198 + Math.floor(y / 7), 235, 255];
});
const piece = png(50, 50, (x, y) => {
  const transparentCorner = (x < 5 && y < 5 && (x - 5) ** 2 + (y - 5) ** 2 > 25)
    || (x > 44 && y < 5 && (x - 44) ** 2 + (y - 5) ** 2 > 25)
    || (x < 5 && y > 44 && (x - 5) ** 2 + (y - 44) ** 2 > 25)
    || (x > 44 && y > 44 && (x - 44) ** 2 + (y - 44) ** 2 > 25);
  if (transparentCorner) return [0, 0, 0, 0];
  const edge = x < 3 || x > 46 || y < 3 || y > 46;
  return edge ? [16, 76, 139, 255] : [35 + x, 115 + Math.floor(y / 2), 211, 255];
});

const contentTypes = new Map([
  [".html", "text/html; charset=utf-8"], [".js", "text/javascript; charset=utf-8"],
  [".mjs", "text/javascript; charset=utf-8"], [".map", "application/json; charset=utf-8"],
  [".css", "text/css; charset=utf-8"],
]);

const server = createServer(async (request, response) => {
  const url = new URL(request.url ?? "/", `http://${request.headers.host ?? "127.0.0.1"}`);
  response.setHeader("X-Content-Type-Options", "nosniff");
  response.setHeader("Referrer-Policy", "no-referrer");
  response.setHeader("Cache-Control", "no-store");
  response.setHeader("Content-Security-Policy", "default-src 'self'; script-src 'self'; style-src 'self'; img-src 'self'; connect-src 'none'; object-src 'none'; base-uri 'none'; frame-ancestors 'none'");
  if (url.pathname === "/") {
    response.writeHead(302, { Location: `/demo/${url.search}` });
    response.end();
    return;
  }
  if (url.pathname === "/fixture/background.png" || url.pathname === "/fixture/piece.png") {
    response.writeHead(200, { "Content-Type": "image/png" });
    response.end(url.pathname.endsWith("piece.png") ? piece : background);
    return;
  }
  const relative = url.pathname === "/demo/" ? "demo/index.html" : url.pathname.slice(1);
  const file = resolve(root, relative);
  if (!file.startsWith(`${root}${sep}`)) { response.writeHead(404).end(); return; }
  try {
    const body = await readFile(file);
    response.writeHead(200, { "Content-Type": contentTypes.get(extname(file)) ?? "application/octet-stream" });
    response.end(body);
  } catch {
    response.writeHead(404, { "Content-Type": "text/plain; charset=utf-8" });
    response.end("Not found");
  }
});

server.listen(port, "127.0.0.1", () => {
  const url = `http://127.0.0.1:${port}`;
  process.stdout.write(`ChalSense widget fixture listening on ${url}\n`);
  process.stdout.write("Press Ctrl+C to stop.\n");

  if (!process.argv.includes("--open")) return;
  const command = process.platform === "win32"
    ? ["rundll32.exe", ["url.dll,FileProtocolHandler", url]]
    : process.platform === "darwin"
      ? ["open", [url]]
      : ["xdg-open", [url]];
  execFile(command[0], command[1], { windowsHide: true }, (error) => {
    if (error) process.stderr.write(`Unable to open the browser automatically: ${error.message}\nOpen ${url} manually.\n`);
  });
});
