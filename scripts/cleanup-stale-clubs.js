// Одноразовый скрипт: почистить stale clubIds в users/{uid}.
//
// Запуск:
//   1) Firebase Console → Project Settings → Service accounts → Generate new private key
//   2) положить JSON рядом: scripts/serviceAccountKey.json
//   3) cd scripts && npm init -y && npm i firebase-admin
//   4) node cleanup-stale-clubs.js            # dry-run
//      node cleanup-stale-clubs.js --apply    # применить
//
// serviceAccountKey.json — полный админ-доступ. Не коммить, не пересылать.

const admin = require('firebase-admin');
const serviceAccount = require('./serviceAccountKey.json');

admin.initializeApp({
  credential: admin.credential.cert(serviceAccount),
  projectId: serviceAccount.project_id,
});
const db = admin.firestore();

async function main() {
  const dryRun = !process.argv.includes('--apply');

  const clubsSnap = await db.collection('clubs').get();
  const live = new Set();
  for (const d of clubsSnap.docs) {
    const data = d.data() || {};
    if (!data.deletedAt) live.add(d.id);
  }
  console.log(`live clubs: ${live.size}`, Array.from(live));

  const usersSnap = await db.collection('users').get();
  let fixed = 0;
  let batch = db.batch();
  let inBatch = 0;

  for (const doc of usersSnap.docs) {
    const data = doc.data() || {};
    const clubIds = Array.isArray(data.clubIds) ? data.clubIds : [];
    const cleaned = clubIds.filter(id => live.has(id));
    const primary = data.clubId;
    const needPrimaryFix = primary && !live.has(primary);
    const needArrayFix = cleaned.length !== clubIds.length;
    if (!needPrimaryFix && !needArrayFix) continue;

    console.log(
      `${doc.id}: clubIds ${JSON.stringify(clubIds)} -> ${JSON.stringify(cleaned)}` +
      (needPrimaryFix ? `, clubId ${primary} -> null` : '')
    );
    if (dryRun) { fixed++; continue; }

    const upd = {};
    if (needArrayFix)  upd.clubIds = cleaned;
    if (needPrimaryFix) upd.clubId = admin.firestore.FieldValue.delete();
    batch.update(doc.ref, upd);
    inBatch++;
    if (inBatch === 500) { await batch.commit(); batch = db.batch(); inBatch = 0; }
    fixed++;
  }
  if (!dryRun && inBatch > 0) await batch.commit();

  console.log(
    dryRun
      ? `DRY-RUN: ${fixed} user(s) would be fixed. Запусти с --apply.`
      : `Applied: ${fixed} user(s) fixed.`
  );
}

main().catch(e => { console.error(e); process.exit(1); });
