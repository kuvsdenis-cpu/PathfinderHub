const fs = require('fs');
const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require('@firebase/rules-unit-testing');
const {
  doc, getDoc, setDoc, updateDoc, deleteDoc, collection, getDocs,
} = require('firebase/firestore');

let testEnv;

const USERS = {
  teen1:       { role: 'teen',        clubId: 'clubA', clubIds: ['clubA'], conferenceId: 'conf1', onboardingCompleted: true },
  teen2:       { role: 'teen',        clubId: 'clubA', clubIds: ['clubA'], conferenceId: 'conf1', onboardingCompleted: true },
  director1:   { role: 'director',    clubId: 'clubA', clubIds: ['clubA'], conferenceId: 'conf1', isOwnerClub: true, directorPermissions: ['members.edit','content.edit','events.create','tasks.create','reports.generate','moderation.review','invites.create','settings.edit','learning.approve'] },
  instructor1: { role: 'instructor',  clubId: 'clubA', clubIds: ['clubA'], conferenceId: 'conf1' },
  secretary1:  { role: 'secretary',   clubId: 'clubA', clubIds: ['clubA'], conferenceId: 'conf1' },
  parent1:     { role: 'parent',      clubId: 'clubA', clubIds: ['clubA'], conferenceId: 'conf1' },
  confAdmin1:  { role: 'conference_admin', clubId: null, clubIds: [], conferenceId: 'conf1' },
  outsider:    { role: 'teen',        clubId: 'clubB', clubIds: ['clubB'], conferenceId: 'conf2', onboardingCompleted: true },
};

async function seedUsers() {
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    for (const [uid, data] of Object.entries(USERS)) {
      await setDoc(doc(db, 'users', uid), { id: uid, ...data });
    }
  });
}

function ctx(uid) { return testEnv.authenticatedContext(uid).firestore(); }
function anon()    { return testEnv.unauthenticatedContext().firestore(); }

beforeAll(async () => {
  testEnv = await initializeTestEnvironment({
    projectId: 'pathfinder-hub-c495d',
    firestore: {
      rules: fs.readFileSync('firestore.rules', 'utf8'),
      host: 'localhost',
      port: 8080,
    },
  });
});

beforeEach(async () => {
  await testEnv.clearFirestore();
  await seedUsers();
});

afterAll(async () => {
  await testEnv.cleanup();
});

describe('users', () => {
  test('teen читает свой профиль', async () => {
    await assertSucceeds(getDoc(doc(ctx('teen1'), 'users', 'teen1')));
  });
  test('teen НЕ читает чужой профиль', async () => {
    await assertFails(getDoc(doc(ctx('teen1'), 'users', 'teen2')));
  });
  test('director читает teen своего клуба', async () => {
    await assertSucceeds(getDoc(doc(ctx('director1'), 'users', 'teen1')));
  });
  test('director НЕ читает teen чужого клуба', async () => {
    await assertFails(getDoc(doc(ctx('director1'), 'users', 'outsider')));
  });
  test('teen меняет своё имя', async () => {
    await assertSucceeds(updateDoc(doc(ctx('teen1'), 'users', 'teen1'), { firstName: 'Новое' }));
  });
  test('teen НЕ меняет свою роль', async () => {
    await assertFails(updateDoc(doc(ctx('teen1'), 'users', 'teen1'), { role: 'director' }));
  });
  test('teen НЕ меняет чужую роль', async () => {
    await assertFails(updateDoc(doc(ctx('teen1'), 'users', 'teen2'), { role: 'director' }));
  });
  test('director меняет роль teen своего клуба', async () => {
    await assertSucceeds(updateDoc(doc(ctx('director1'), 'users', 'teen1'), { role: 'instructor' }));
  });
  test('teen НЕ удаляет аккаунт', async () => {
    await assertFails(deleteDoc(doc(ctx('teen1'), 'users', 'teen1')));
  });
  test('confAdmin удаляет аккаунт', async () => {
    await assertSucceeds(deleteDoc(doc(ctx('confAdmin1'), 'users', 'teen1')));
  });
});

describe('fcmTokens', () => {
  test('teen пишет свой токен', async () => {
    await assertSucceeds(setDoc(doc(ctx('teen1'), 'users', 'teen1', 'fcmTokens', 'tok1'), { token: 'tok1' }));
  });
  test('teen НЕ пишет чужой токен', async () => {
    await assertFails(setDoc(doc(ctx('teen1'), 'users', 'teen2', 'fcmTokens', 'tok1'), { token: 'tok1' }));
  });
  test('teen НЕ читает чужие токены', async () => {
    await assertFails(getDoc(doc(ctx('teen1'), 'users', 'teen2', 'fcmTokens', 'tok1')));
  });
});

describe('comments', () => {
  test('teen создаёт комментарий в своём клубе', async () => {
    await assertSucceeds(setDoc(
      doc(ctx('teen1'), 'clubs', 'clubA', 'comments', 'c1'),
      { authorId: 'teen1', clubId: 'clubA', text: 'hi', status: 'published' }
    ));
  });
  test('teen НЕ создаёт комментарий с чужим authorId', async () => {
    await assertFails(setDoc(
      doc(ctx('teen1'), 'clubs', 'clubA', 'comments', 'c2'),
      { authorId: 'teen2', clubId: 'clubA', text: 'hi', status: 'published' }
    ));
  });
  test('teen НЕ создаёт комментарий в чужом клубе', async () => {
    await assertFails(setDoc(
      doc(ctx('teen1'), 'clubs', 'clubB', 'comments', 'c3'),
      { authorId: 'teen1', clubId: 'clubB', text: 'hi', status: 'published' }
    ));
  });
  test('parent НЕ может писать в чат клуба', async () => {
    await assertFails(setDoc(
      doc(ctx('parent1'), 'clubs', 'clubA', 'comments', 'c4'),
      { authorId: 'parent1', clubId: 'clubA', text: 'hi', status: 'published' }
    ));
  });
});

describe('groupChats', () => {
  test('director создаёт группу', async () => {
    await assertSucceeds(setDoc(
      doc(ctx('director1'), 'clubs', 'clubA', 'groupChats', 'g1'),
      { createdBy: 'director1', clubId: 'clubA', participants: ['director1','teen1'], name: 'G' }
    ));
  });
  test('instructor создаёт группу', async () => {
    await assertSucceeds(setDoc(
      doc(ctx('instructor1'), 'clubs', 'clubA', 'groupChats', 'g2'),
      { createdBy: 'instructor1', clubId: 'clubA', participants: ['instructor1','teen1'], name: 'G' }
    ));
  });
  test('secretary НЕ создаёт группу', async () => {
    await assertFails(setDoc(
      doc(ctx('secretary1'), 'clubs', 'clubA', 'groupChats', 'g3'),
      { createdBy: 'secretary1', clubId: 'clubA', participants: ['secretary1','teen1'], name: 'G' }
    ));
  });
  test('teen НЕ создаёт группу', async () => {
    await assertFails(setDoc(
      doc(ctx('teen1'), 'clubs', 'clubA', 'groupChats', 'g4'),
      { createdBy: 'teen1', clubId: 'clubA', participants: ['teen1','teen2'], name: 'G' }
    ));
  });
});

describe('directMessages', () => {
  test('teen создаёт DM с собой в участниках', async () => {
    await assertSucceeds(setDoc(
      doc(ctx('teen1'), 'clubs', 'clubA', 'directMessages', 'dm1'),
      { participants: ['teen1','director1'], clubId: 'clubA' }
    ));
  });
  test('teen НЕ создаёт DM без себя', async () => {
    await assertFails(setDoc(
      doc(ctx('teen1'), 'clubs', 'clubA', 'directMessages', 'dm2'),
      { participants: ['teen2','director1'], clubId: 'clubA' }
    ));
  });
  test('parent НЕ создаёт DM', async () => {
    await assertFails(setDoc(
      doc(ctx('parent1'), 'clubs', 'clubA', 'directMessages', 'dm3'),
      { participants: ['parent1','director1'], clubId: 'clubA' }
    ));
  });
});

describe('requirementTaskAnswers', () => {
  test('teen создаёт свой ответ', async () => {
    await assertSucceeds(setDoc(
      doc(ctx('teen1'), 'clubs', 'clubA', 'requirementTaskAnswers', 'a1'),
      { userId: 'teen1', clubId: 'clubA', status: 'draft', answer: 'x' }
    ));
  });
  test('teen НЕ создаёт ответ от чужого имени', async () => {
    await assertFails(setDoc(
      doc(ctx('teen1'), 'clubs', 'clubA', 'requirementTaskAnswers', 'a2'),
      { userId: 'teen2', clubId: 'clubA', status: 'draft', answer: 'x' }
    ));
  });
  test('instructor одобряет ответ teen', async () => {
    await testEnv.withSecurityRulesDisabled(async (c) => {
      await setDoc(doc(c.firestore(), 'clubs', 'clubA', 'requirementTaskAnswers', 'a3'),
        { userId: 'teen1', clubId: 'clubA', status: 'submitted', answer: 'x' });
    });
    await assertSucceeds(updateDoc(
      doc(ctx('instructor1'), 'clubs', 'clubA', 'requirementTaskAnswers', 'a3'),
      { status: 'approved', reviewedBy: 'instructor1', reviewedAt: new Date() }
    ));
  });
  test('teen НЕ одобряет свой ответ', async () => {
    await testEnv.withSecurityRulesDisabled(async (c) => {
      await setDoc(doc(c.firestore(), 'clubs', 'clubA', 'requirementTaskAnswers', 'a4'),
        { userId: 'teen1', clubId: 'clubA', status: 'submitted', answer: 'x' });
    });
    await assertFails(updateDoc(
      doc(ctx('teen1'), 'clubs', 'clubA', 'requirementTaskAnswers', 'a4'),
      { status: 'approved' }
    ));
  });
});

describe('tests', () => {
  test('teen читает тест своего клуба', async () => {
    await testEnv.withSecurityRulesDisabled(async (c) => {
      await setDoc(doc(c.firestore(), 'clubs', 'clubA', 'tests', 't1'),
        { scope: 'honor', scopeId: 'honor_023', title: 'Основы выпечки', passScore: 80 });
    });
    await assertSucceeds(getDoc(doc(ctx('teen1'), 'clubs', 'clubA', 'tests', 't1')));
  });

  test('teen НЕ читает тест чужого клуба', async () => {
    await testEnv.withSecurityRulesDisabled(async (c) => {
      await setDoc(doc(c.firestore(), 'clubs', 'clubB', 'tests', 't2'),
        { scope: 'honor', scopeId: 'honor_023', title: 'X', passScore: 80 });
    });
    await assertFails(getDoc(doc(ctx('teen1'), 'clubs', 'clubB', 'tests', 't2')));
  });

  test('director создаёт тест своего клуба', async () => {
    await assertSucceeds(setDoc(
      doc(ctx('director1'), 'clubs', 'clubA', 'tests', 't3'),
      { scope: 'level', scopeId: 'level_3', title: 'Испытание 3', passScore: 80, clubId: 'clubA' }
    ));
  });

  test('instructor создаёт тест своего клуба', async () => {
    await assertSucceeds(setDoc(
      doc(ctx('instructor1'), 'clubs', 'clubA', 'tests', 't4'),
      { scope: 'quest', scopeId: 'quest_x', title: 'Квест', passScore: 80, clubId: 'clubA' }
    ));
  });

  test('secretary НЕ создаёт тест (нет content.edit)', async () => {
    await assertFails(setDoc(
      doc(ctx('secretary1'), 'clubs', 'clubA', 'tests', 't5'),
      { scope: 'honor', scopeId: 'honor_023', title: 'X', passScore: 80, clubId: 'clubA' }
    ));
  });

  test('teen НЕ создаёт тест', async () => {
    await assertFails(setDoc(
      doc(ctx('teen1'), 'clubs', 'clubA', 'tests', 't6'),
      { scope: 'honor', scopeId: 'honor_023', title: 'X', passScore: 80, clubId: 'clubA' }
    ));
  });

  test('teen читает вопрос теста своего клуба', async () => {
    await testEnv.withSecurityRulesDisabled(async (c) => {
      await setDoc(doc(c.firestore(), 'clubs', 'clubA', 'tests', 't7'), { title: 'T', scope: 'honor', scopeId: 'h', passScore: 80 });
      await setDoc(doc(c.firestore(), 'clubs', 'clubA', 'tests', 't7', 'questions', 'q1'),
        { type: 'choice', text: 'Q', correctAnswer: 'A', points: 1 });
    });
    await assertSucceeds(getDoc(doc(ctx('teen1'), 'clubs', 'clubA', 'tests', 't7', 'questions', 'q1')));
  });

  test('teen НЕ читает вопрос чужого клуба', async () => {
    await testEnv.withSecurityRulesDisabled(async (c) => {
      await setDoc(doc(c.firestore(), 'clubs', 'clubB', 'tests', 't8'), { title: 'T', scope: 'honor', scopeId: 'h', passScore: 80 });
      await setDoc(doc(c.firestore(), 'clubs', 'clubB', 'tests', 't8', 'questions', 'q1'),
        { type: 'choice', text: 'Q', correctAnswer: 'A', points: 1 });
    });
    await assertFails(getDoc(doc(ctx('teen1'), 'clubs', 'clubB', 'tests', 't8', 'questions', 'q1')));
  });

  test('director создаёт вопрос в тесте своего клуба', async () => {
    await testEnv.withSecurityRulesDisabled(async (c) => {
      await setDoc(doc(c.firestore(), 'clubs', 'clubA', 'tests', 't9'), { title: 'T', scope: 'honor', scopeId: 'h', passScore: 80 });
    });
    await assertSucceeds(setDoc(
      doc(ctx('director1'), 'clubs', 'clubA', 'tests', 't9', 'questions', 'q1'),
      { type: 'choice', text: 'Q', correctAnswer: 'A', points: 1 }
    ));
  });
});

describe('invites', () => {
  test('аноним читает конкретный инвайт по коду', async () => {
    await testEnv.withSecurityRulesDisabled(async (c) => {
      await setDoc(doc(c.firestore(), 'invites', 'ABCD-1234'),
        { code: 'ABCD-1234', status: 'active', createdBy: 'director1' });
    });
    await assertSucceeds(getDoc(doc(anon(), 'invites', 'ABCD-1234')));
  });
  test('аноним НЕ перебирает коллекцию invites', async () => {
    await assertFails(getDocs(collection(anon(), 'invites')));
  });
  test('director создаёт инвайт', async () => {
    await assertSucceeds(setDoc(
      doc(ctx('director1'), 'invites', 'NEW-0001'),
      { code: 'NEW-0001', createdBy: 'director1', status: 'active', clubId: 'clubA', role: 'teen' }
    ));
  });
  test('teen НЕ создаёт инвайт', async () => {
    await assertFails(setDoc(
      doc(ctx('teen1'), 'invites', 'NEW-0002'),
      { code: 'NEW-0002', createdBy: 'teen1', status: 'active', clubId: 'clubA', role: 'teen' }
    ));
  });
});

describe('auditLog', () => {
  test('director читает auditLog своего клуба', async () => {
    await testEnv.withSecurityRulesDisabled(async (c) => {
      await setDoc(doc(c.firestore(), 'auditLog', 'l1'),
        { clubId: 'clubA', actorId: 'director1', action: 'x' });
    });
    await assertSucceeds(getDoc(doc(ctx('director1'), 'auditLog', 'l1')));
  });
  test('teen НЕ пишет в auditLog', async () => {
    await assertFails(setDoc(
      doc(ctx('teen1'), 'auditLog', 'l2'),
      { clubId: 'clubA', actorId: 'teen1', action: 'x' }
    ));
  });
  test('director НЕ пишет в auditLog', async () => {
    await assertFails(setDoc(
      doc(ctx('director1'), 'auditLog', 'l3'),
      { clubId: 'clubA', actorId: 'director1', action: 'x' }
    ));
  });
});

describe('permissions', () => {
  test('director НЕ пишет в чужой клуб', async () => {
    await assertFails(setDoc(
      doc(ctx('director1'), 'clubs', 'clubB', 'comments', 'x'),
      { authorId: 'director1', clubId: 'clubB', text: 'x', status: 'published' }
    ));
  });
  test('все НЕ пишут в securityLog', async () => {
    await assertFails(setDoc(
      doc(ctx('director1'), 'securityLog', 's1'),
      { actorId: 'director1', action: 'x' }
    ));
    await assertFails(setDoc(
      doc(ctx('teen1'), 'securityLog', 's2'),
      { actorId: 'teen1', action: 'x' }
    ));
  });
});
