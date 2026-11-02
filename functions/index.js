const functions = require('firebase-functions');
const admin = require('firebase-admin');

if (!admin.apps.length) admin.initializeApp();
const db = admin.firestore();

/**
 * Удаляет clubId из users/{uid}.clubIds и обнуляет users/{uid}.clubId,
 * если он указывал на удалённый клуб. Батчи по 500.
 */
async function removeClubFromAllUsers(clubId) {
  const usersRef = db.collection('users');
  const primarySnap = await usersRef.where('clubId', '==', clubId).get();
  const arraySnap   = await usersRef.where('clubIds', 'array-contains', clubId).get();

  const affected = new Map();
  primarySnap.docs.forEach(d => affected.set(d.id, d.ref));
  arraySnap.docs.forEach(d => affected.set(d.id, d.ref));

  if (affected.size === 0) {
    functions.logger.info(`cleanupClub ${clubId}: no affected users`);
    return;
  }

  const refs = Array.from(affected.values());
  let batch = db.batch();
  let inBatch = 0;
  for (const ref of refs) {
    batch.update(ref, { clubIds: admin.firestore.FieldValue.arrayRemove(clubId) });
    inBatch++;
    if (inBatch === 500) { await batch.commit(); batch = db.batch(); inBatch = 0; }
  }
  if (inBatch > 0) await batch.commit();

  let batch2 = db.batch();
  let inBatch2 = 0;
  for (const doc of primarySnap.docs) {
    batch2.update(doc.ref, { clubId: admin.firestore.FieldValue.delete() });
    inBatch2++;
    if (inBatch2 === 500) { await batch2.commit(); batch2 = db.batch(); inBatch2 = 0; }
  }
  if (inBatch2 > 0) await batch2.commit();

  functions.logger.info(
    `cleanupClub ${clubId}: cleaned ${refs.length} user(s), ` +
    `primary-reset ${primarySnap.size}`
  );
}

exports.cleanupClubOnDelete = functions.firestore
  .document('clubs/{clubId}')
  .onDelete(async (_snap, ctx) => {
    await removeClubFromAllUsers(ctx.params.clubId);
  });

exports.cleanupClubOnSoftDelete = functions.firestore
  .document('clubs/{clubId}')
  .onUpdate(async (change, ctx) => {
    const before = change.before.data() || {};
    const after  = change.after.data()  || {};
    if (!before.deletedAt && after.deletedAt) {
      await removeClubFromAllUsers(ctx.params.clubId);
    }
  });
