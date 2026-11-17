const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_123";
const BOB_UID = "bob_456";

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

test("Unauthenticated user: cannot read or write any collection", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection(`users/${ALICE_UID}/subjects`).get());
  await assertFails(
    unauthDb.collection(`users/${ALICE_UID}/subjects`).doc("subj_history").set({
      userId: ALICE_UID,
      nameEn: "History",
      iconKey: "history",
      colorHex: "#F6AD55",
      sortOrder: 0,
      createdAt: new Date(Date.now() - 1000),
      updatedAt: new Date(Date.now() - 1000),
    })
  );
});

test("Authenticated owner: valid Kotlin payloads succeed across subjects, topics, subtopics, notes, videos, mcqs, testResults, studyLogs", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const past = new Date(Date.now() - 10000);

  // UserProfile
  await assertSucceeds(
    aliceDb.doc(`users/${ALICE_UID}`).set({
      userId: ALICE_UID,
      displayName: "Aarav",
      examName: "UPSC CSE",
      examDateMillis: Date.now() + 86400000 * 60,
      themeId: "midnight_teal",
      createdAt: past,
      updatedAt: past,
    })
  );

  // Subject
  await assertSucceeds(
    aliceDb.doc(`users/${ALICE_UID}/subjects/subj_history`).set({
      userId: ALICE_UID,
      nameEn: "History",
      nameHi: "इतिहास",
      iconKey: "history",
      colorHex: "#F6AD55",
      sortOrder: 0,
      createdAt: past,
      updatedAt: past,
    })
  );

  // Topic
  await assertSucceeds(
    aliceDb.doc(`users/${ALICE_UID}/topics/topic_ancient`).set({
      userId: ALICE_UID,
      subjectId: "subj_history",
      name: "Ancient India",
      description: "Indus Valley to Gupta Empire",
      createdAt: past,
      updatedAt: past,
    })
  );

  // Subtopic
  await assertSucceeds(
    aliceDb.doc(`users/${ALICE_UID}/subtopics/sub_ivc`).set({
      userId: ALICE_UID,
      subjectId: "subj_history",
      topicId: "topic_ancient",
      name: "Indus Valley Civilization",
      lastStudiedDate: "2026-11-16",
      createdAt: past,
      updatedAt: past,
    })
  );

  // StudyNote (HTML test)
  await assertSucceeds(
    aliceDb.doc(`users/${ALICE_UID}/notes/note_1`).set({
      userId: ALICE_UID,
      subjectId: "subj_history",
      topicId: "topic_ancient",
      subtopicId: "sub_ivc",
      folderName: "NCERT Notes",
      title: "IVC Mock Test 1",
      noteType: "HTML",
      localFileName: "ivc_test.html",
      htmlContent: "<html><body><h1>Test</h1></body></html>",
      createdAt: past,
      updatedAt: past,
    })
  );

  // Partial note update (rename / move folder)
  await assertSucceeds(
    aliceDb.doc(`users/${ALICE_UID}/notes/note_1`).update({
      folderName: "Mock Tests",
      title: "IVC Mock Test Updated",
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // VideoLink
  await assertSucceeds(
    aliceDb.doc(`users/${ALICE_UID}/videos/vid_1`).set({
      userId: ALICE_UID,
      subjectId: "subj_history",
      topicId: "topic_ancient",
      subtopicId: "sub_ivc",
      title: "IVC Complete Lecture",
      youtubeUrl: "https://www.youtube.com/watch?v=dQw4w9WgXcQ",
      createdAt: past,
      updatedAt: past,
    })
  );

  // McqQuestion
  await assertSucceeds(
    aliceDb.doc(`users/${ALICE_UID}/mcqs/mcq_1`).set({
      userId: ALICE_UID,
      subjectId: "subj_history",
      topicId: "topic_ancient",
      subtopicId: "sub_ivc",
      question: "Where was the Great Bath discovered?",
      options: ["Harappa", "Mohenjo-daro", "Lothal", "Kalibangan"],
      correctIndex: 1,
      explanation: "Great Bath is in Mohenjo-daro.",
      createdAt: past,
      updatedAt: past,
    })
  );

  // TestResult
  await assertSucceeds(
    aliceDb.doc(`users/${ALICE_UID}/testResults/res_1`).set({
      userId: ALICE_UID,
      subjectId: "subj_history",
      topicId: "topic_ancient",
      subtopicId: "sub_ivc",
      noteId: "note_1",
      testTitle: "IVC Mock Test 1",
      scoreObtained: 18,
      totalMarks: 20,
      percentage: 90,
      summaryText: "18 / 20 marks",
      takenDate: "2026-11-16",
      createdAt: past,
      updatedAt: past,
    })
  );

  // StudyLog
  await assertSucceeds(
    aliceDb.doc(`users/${ALICE_UID}/studyLogs/log_1`).set({
      userId: ALICE_UID,
      dateKey: "2026-11-16",
      entryType: "STUDIED",
      title: "Indus Valley Civilization",
      subjectName: "History",
      subtopicId: "sub_ivc",
      completed: true,
      createdAt: past,
      updatedAt: past,
    })
  );

  // RevisionDiary with PDF attachment
  await assertSucceeds(
    aliceDb.doc(`users/${ALICE_UID}/revisionDiary/diary_1`).set({
      userId: ALICE_UID,
      dateKey: "2026-11-16",
      title: "Fundamental Rights Summary",
      subjectName: "Polity",
      sourceName: "Laxmikant Ch-7",
      content: "Articles 12-35 key points revised.",
      pdfTitle: "FR_Notes.pdf",
      pdfLocalFileName: "diary_1_FR_Notes.pdf",
      createdAt: past,
      updatedAt: past,
    })
  );
  await assertSucceeds(
    aliceDb.doc(`users/${ALICE_UID}/revisionDiary/diary_1`).update({
      sourceName: "Laxmikant Ch-7 + NCERT",
      pdfTitle: "FR_Notes_v2.pdf",
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Admin-only appConfig/support rule
  const adminDb = testEnv.authenticatedContext("admin_uid", { email: "lovely.bunts@gmail.com" }).firestore();
  await assertSucceeds(
    adminDb.doc("appConfig/support").set({
      whatsappDigits: "917049126747",
      updatedAt: past,
    })
  );
  await assertSucceeds(aliceDb.doc("appConfig/support").get());
  await assertFails(
    aliceDb.doc("appConfig/support").set({
      whatsappDigits: "919999999999",
      updatedAt: past,
    })
  );

  // Query list on empty or populated subcollections
  await assertSucceeds(aliceDb.collection(`users/${ALICE_UID}/subjects`).get());
  await assertSucceeds(aliceDb.collection(`users/${ALICE_UID}/revisionDiary`).get());
  await assertSucceeds(
    aliceDb.collection(`users/${ALICE_UID}/notes`).where("subtopicId", "==", "sub_ivc").get()
  );
});

test("Security bounds: reject cross-user access, ghost fields, @DocumentId 'id' field, and future timestamps", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const past = new Date(Date.now() - 10000);

  await assertSucceeds(
    aliceDb.doc(`users/${ALICE_UID}/subjects/subj_1`).set({
      userId: ALICE_UID,
      nameEn: "Polity",
      iconKey: "polity",
      colorHex: "#4FD1C5",
      sortOrder: 1,
      createdAt: past,
      updatedAt: past,
    })
  );

  // Cross-user read & write must fail
  await assertFails(bobDb.doc(`users/${ALICE_UID}/subjects/subj_1`).get());
  await assertFails(bobDb.collection(`users/${ALICE_UID}/subjects`).get());
  await assertFails(
    bobDb.doc(`users/${ALICE_UID}/subjects/subj_1`).update({
      nameEn: "Hacked",
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Reject 'id' field inside payload (since @DocumentId is used in Kotlin)
  await assertFails(
    aliceDb.doc(`users/${ALICE_UID}/subjects/subj_2`).set({
      id: "subj_2",
      userId: ALICE_UID,
      nameEn: "Geography",
      iconKey: "geography",
      colorHex: "#68D391",
      sortOrder: 2,
      createdAt: past,
      updatedAt: past,
    })
  );

  // Reject ghost field
  await assertFails(
    aliceDb.doc(`users/${ALICE_UID}/subjects/subj_3`).set({
      userId: ALICE_UID,
      nameEn: "Maths",
      iconKey: "maths",
      colorHex: "#818CF8",
      sortOrder: 3,
      isAdmin: true,
      createdAt: past,
      updatedAt: past,
    })
  );

  // Reject future timestamp
  await assertFails(
    aliceDb.doc(`users/${ALICE_UID}/subjects/subj_4`).set({
      userId: ALICE_UID,
      nameEn: "Maths",
      iconKey: "maths",
      colorHex: "#818CF8",
      sortOrder: 4,
      createdAt: new Date(Date.now() + 60000),
      updatedAt: new Date(Date.now() + 60000),
    })
  );
});

test("Shared catalog, student summaries, whatsappRequests, personalNotes, pdfAnnotations & bookmarks RBAC", async () => {
  const adminDb = testEnv.authenticatedContext("admin_uid", { email: "lovely.bunts@gmail.com" }).firestore();
  const aliceDb = testEnv.authenticatedContext(ALICE_UID, { email: "student1@gmail.com" }).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID, { email: "student2@gmail.com" }).firestore();
  const past = new Date(Date.now() - 10000);

  // Admin uploads shared content; non-admin students cannot write shared catalog
  await assertFails(
    aliceDb.doc("sharedSubjects/shared_subj_1").set({
      userId: ALICE_UID,
      nameEn: "Computer",
      published: true,
      updatedAt: past,
    })
  );

  await assertSucceeds(
    adminDb.doc("sharedSubjects/shared_subj_1").set({
      userId: "admin_uid",
      nameEn: "Computer",
      published: true,
      updatedAt: past,
    })
  );

  await assertSucceeds(
    adminDb.doc("sharedContent/shared_doc_1").set({
      userId: "admin_uid",
      subjectId: "subj_computer",
      topicId: "topic_7_0",
      subtopicId: "sub_7_0_0",
      folderName: "Notes",
      title: "Computer Basics PDF",
      noteType: "PDF",
      version: 1,
      published: true,
      downloadsCount: 0,
      createdAt: past,
      updatedAt: past,
    })
  );

  // Students can read published sharedContent & increment downloadsCount by +1 only
  await assertSucceeds(aliceDb.doc("sharedContent/shared_doc_1").get());
  await assertSucceeds(
    aliceDb.doc("sharedContent/shared_doc_1").update({
      downloadsCount: 1,
      updatedAt: new Date(Date.now() - 1000),
    })
  );
  await assertFails(
    aliceDb.doc("sharedContent/shared_doc_1").update({
      title: "Tampered",
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Student personalNotes, pdfAnnotations & bookmarks isolated by UID
  await assertSucceeds(
    aliceDb.doc(`users/${ALICE_UID}/personalNotes/pnote_1`).set({
      userId: ALICE_UID,
      subjectId: "subj_polity",
      subjectName: "Polity",
      topicName: "Articles",
      title: "Right to Equality",
      contentSummary: "Art 14-18",
      blocksJson: "[]",
      updatedAtMillis: Date.now(),
      createdAt: past,
      updatedAt: past,
    })
  );
  await assertFails(bobDb.doc(`users/${ALICE_UID}/personalNotes/pnote_1`).get());

  await assertSucceeds(
    aliceDb.doc(`users/${ALICE_UID}/pdfAnnotations/shared_doc_1`).set({
      userId: ALICE_UID,
      noteId: "shared_doc_1",
      annotationsJson: "{}",
      updatedAtMillis: Date.now(),
      createdAt: past,
      updatedAt: past,
    })
  );
  await assertFails(bobDb.doc(`users/${ALICE_UID}/pdfAnnotations/shared_doc_1`).get());

  await assertSucceeds(
    aliceDb.doc(`users/${ALICE_UID}/bookmarks/bmk_1`).set({
      userId: ALICE_UID,
      bookmarkType: "FAVORITE_NOTE",
      title: "Computer Basics PDF",
      refId: "shared_doc_1",
      createdAtMillis: Date.now(),
      createdAt: past,
      updatedAt: past,
    })
  );
  await assertFails(bobDb.doc(`users/${ALICE_UID}/bookmarks/bmk_1`).get());

  // Student summary + Admin viewing student testResults
  await assertSucceeds(
    aliceDb.doc(`studentSummaries/${ALICE_UID}`).set({
      userId: ALICE_UID,
      displayName: "Aarav",
      email: "student1@gmail.com",
      downloadsCount: 1,
      testAttemptsCount: 2,
      averageScorePct: 85.0,
      lastActiveMillis: Date.now(),
      updatedAt: past,
    })
  );
  await assertFails(bobDb.collection("studentSummaries").get());
  await assertSucceeds(adminDb.collection("studentSummaries").get());

  // WhatsApp request tracking: student creates, admin reads & updates status
  await assertSucceeds(
    aliceDb.doc("whatsappRequests/req_1").set({
      userId: ALICE_UID,
      studentName: "Aarav",
      requestType: "Notes Request",
      subject: "MP GK",
      status: "PENDING",
      createdAt: past,
      updatedAt: past,
    })
  );
  await assertFails(bobDb.collection("whatsappRequests").get());
  await assertSucceeds(adminDb.collection("whatsappRequests").get());
  await assertSucceeds(
    adminDb.doc("whatsappRequests/req_1").update({
      status: "FULFILLED",
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // FullLengthExams: user-owned custom exam + admin shared exam
  await assertSucceeds(
    aliceDb.doc(`users/${ALICE_UID}/fullLengthExams/fl_exam_custom_1`).set({
      userId: ALICE_UID,
      name: "MPPSC Mains Mock Series",
      subtitle: "Full Length Papers",
      createdAt: past,
      updatedAt: past,
    })
  );
  await assertSucceeds(
    aliceDb.doc(`users/${ALICE_UID}/fullLengthExams/fl_exam_custom_1`).update({
      name: "MPPSC Mains Mock Series Updated",
      updatedAt: new Date(Date.now() - 1000),
    })
  );
  await assertFails(bobDb.doc(`users/${ALICE_UID}/fullLengthExams/fl_exam_custom_1`).get());
  await assertFails(
    aliceDb.doc(`users/${ALICE_UID}/fullLengthExams/fl_exam_bad`).set({
      userId: ALICE_UID,
      name: "Bad",
      extraGhost: true,
      createdAt: past,
      updatedAt: past,
    })
  );

  // Full length HTML test note linked under full_length_exam
  await assertSucceeds(
    aliceDb.doc(`users/${ALICE_UID}/notes/fl_note_1`).set({
      userId: ALICE_UID,
      subjectId: "full_length_exam",
      topicId: "exam_mppsc_prelims",
      subtopicId: "full_length_html",
      folderName: "MPPSC Prelims",
      title: "Full Length Test #1",
      noteType: "HTML",
      localFileName: "fl_test_1.html",
      htmlContent: "<html><body><h1>Full Length</h1></body></html>",
      isMockTest: true,
      createdAt: past,
      updatedAt: past,
    })
  );

  // Admin sharedFullLengthExams
  await assertFails(
    aliceDb.doc("sharedFullLengthExams/shared_fl_1").set({
      userId: ALICE_UID,
      name: "Student Unauthorized Exam",
      published: true,
      updatedAt: past,
    })
  );
  await assertSucceeds(
    adminDb.doc("sharedFullLengthExams/shared_fl_1").set({
      userId: "admin_uid",
      name: "UPSC Full Length Series",
      subtitle: "2026 Prelims Mock Tests",
      published: true,
      updatedAt: past,
    })
  );
  await assertSucceeds(aliceDb.doc("sharedFullLengthExams/shared_fl_1").get());
});


