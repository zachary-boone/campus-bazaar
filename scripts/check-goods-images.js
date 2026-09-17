/**
 * 商品图文一致性校验
 * 用法： node scripts/check-goods-images.js
 * 检查：数据库 tb_goods.name/price 与 imgs/goods/*.svg 内印刷的文字是否一致
 */
const fs = require('fs');
const path = require('path');
const { execFileSync } = require('child_process');

const MYSQL = process.env.MYSQL_BIN || 'C:\\Program Files\\MySQL\\MySQL Server 8.0\\bin\\mysql.exe';
const IMG_DIR = path.join(__dirname, '..', 'frontend', 'imgs', 'goods');

const sql = 'SELECT id, name, images, price FROM campus_bazaar.tb_goods ORDER BY id;';
const out = execFileSync(MYSQL, ['-uroot', '-p13579zhangbo', '--default-character-set=utf8mb4', '-N', '-B', '-e', sql],
  { encoding: 'utf8' });

const rows = out.split('\n').filter(l => l.trim()).map(l => {
  const [id, name, images, price] = l.replace(/\r$/, '').split('\t');
  return { id: Number(id), name: (name || '').trim(), images: (images || '').trim(), price: Number(price) };
});

let ok = 0; const problems = [];
for (const r of rows) {
  const file = r.images.split(',')[0].replace(/^\/?imgs\/goods\//, '');
  const p = path.join(IMG_DIR, file);
  if (!fs.existsSync(p)) { problems.push(`id=${r.id} 「${r.name}」图片缺失: ${file}`); continue; }
  const s = fs.readFileSync(p, 'utf8');
  const title = (s.match(/<text x="24" y="325"[^>]*>([^<]*)<\/text>/) || [])[1] || '';
  const price = (s.match(/<text x="24" y="370"[^>]*>([^<]*)<\/text>/) || [])[1] || '';
  const wellFormed = (s.match(/<text /g) || []).length === (s.match(/<\/text>/g) || []).length
                  && (s.match(/<svg /g) || []).length === (s.match(/<\/svg>/g) || []).length;
  const nameOk = title.replace(/\s+/g, '') === r.name.replace(/\s+/g, '');
  const priceOk = Number(price.replace(/[^\d.]/g, '')) === r.price;
  if (nameOk && priceOk && wellFormed) ok++;
  else problems.push(`id=${r.id} 「${r.name}」¥${r.price} ← 图内「${title}」${price}${wellFormed ? '' : ' [XML损坏]'}`);
}

console.log(`商品图文一致性: ${ok}/${rows.length}`);
if (problems.length) { problems.forEach(x => console.log('  ✗ ' + x)); process.exit(1); }
console.log('  全部一致 ✓');
