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

const [emulatorHost, emulatorPortStr] = (
  process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085"
).split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

function validDossier(overrides = {}) {
  return {
    nome: "Maria Silva",
    cpf: "123.***.***-09",
    nascimento: "Não localizado em fontes públicas (Protegido por LGPD)",
    score: "Protegido por sigilo bancário / LGPD (consulte Serasa ou Registrato)",
    nomeMae: "Protegido por LGPD",
    nomePai: "Protegido por LGPD",
    sexo: "Não localizado em fontes públicas",
    estadoCivil: "Não localizado em fontes públicas",
    escolaridade: "Não localizado em fontes públicas",
    rendaMensal: "Protegido por sigilo fiscal (LGPD)",
    rg: "Protegido por LGPD",
    enderecoPrincipal: "São Paulo - SP (Região DDD 11)",
    enderecoSecundario: "Não localizado em fontes públicas",
    telefone: "(11) 99999-0000",
    email: "Não localizado em fontes públicas",
    profissao: "Empresária (MEI encontrado em registro público)",
    veiculo: "Não localizado em fontes públicas (consulte Detran/Senatran)",
    parentes: "Protegido por LGPD",
    vizinhos: "Protegido por LGPD",
    ...overrides,
  };
}

function validConsultaPayload(uid, overrides = {}) {
  const now = new Date(Date.now() - 1000);
  return {
    userId: uid,
    searchMode: "COMBINED",
    queryName: "Maria Silva",
    queryCpf: "123.456.789-09",
    queryPhone: "(11) 99999-0000",
    cpfValid: true,
    cpfRegion: "8ª Região Fiscal — São Paulo (SP)",
    dddRegion: "SP — São Paulo, Guarulhos, Campinas e região",
    ibgeFrequency: 11734129,
    summary: "Consulta pública concluída com fontes oficiais.",
    thinkingAnalysis: "Análise aprofundada concluída.",
    dossier: validDossier(),
    sourceEntries: ["Portal BrasilAPI | https://brasilapi.com.br"],
    pinned: false,
    createdAt: now,
    updatedAt: now,
    ...overrides,
  };
}

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

test("Unauthenticated user: denied read and write on users and consultas", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).get());
  await assertFails(
    unauthDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("consultas")
      .get()
  );
  await assertFails(
    unauthDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("consultas")
      .doc("c1")
      .set(validConsultaPayload(ALICE_UID))
  );
});

test("Authenticated owner: valid UserProfile create & update succeed", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const created = new Date(Date.now() - 5000);

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      displayName: "Alice Souza",
      email: "alice@gmail.com",
      createdAt: created,
      updatedAt: created,
    })
  );

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).update({
      displayName: "Alice M. Souza",
      updatedAt: new Date(Date.now() - 1000),
    })
  );
});

test("Authenticated owner: valid ConsultaRecord create, list, partial update, and delete succeed", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const colRef = aliceDb
    .collection("users")
    .doc(ALICE_UID)
    .collection("consultas");

  // Empty subcollection list must succeed
  await assertSucceeds(colRef.get());

  // Create without @DocumentId 'id' field
  await assertSucceeds(colRef.doc("consulta_1").set(validConsultaPayload(ALICE_UID)));

  // List non-empty subcollection
  await assertSucceeds(colRef.get());

  // Partial update: pin toggle
  await assertSucceeds(
    colRef.doc("consulta_1").update({
      pinned: true,
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Partial update: thinkingAnalysis enrichment
  await assertSucceeds(
    colRef.doc("consulta_1").update({
      thinkingAnalysis: "Análise atualizada com Gemini ThinkingLevel.HIGH.",
      summary: "Resumo atualizado.",
      updatedAt: new Date(Date.now() - 300),
    })
  );

  // Delete own consulta
  await assertSucceeds(colRef.doc("consulta_1").delete());
});

test("Security bounds: reject @DocumentId field, ghost keys, future timestamp, and invalid searchMode", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const colRef = aliceDb
    .collection("users")
    .doc(ALICE_UID)
    .collection("consultas");

  // Reject 'id' key inside document body
  await assertFails(
    colRef.doc("bad_id").set(validConsultaPayload(ALICE_UID, { id: "bad_id" }))
  );

  // Reject ghost key
  await assertFails(
    colRef
      .doc("ghost_key")
      .set(validConsultaPayload(ALICE_UID, { isAdmin: true }))
  );

  // Reject future timestamp
  await assertFails(
    colRef
      .doc("future_ts")
      .set(
        validConsultaPayload(ALICE_UID, {
          createdAt: new Date(Date.now() + 60000),
          updatedAt: new Date(Date.now() + 60000),
        })
      )
  );

  // Reject invalid searchMode
  await assertFails(
    colRef
      .doc("bad_mode")
      .set(validConsultaPayload(ALICE_UID, { searchMode: "HACK" }))
  );
});

test("Cross-user isolation: Alice cannot read, write, update, or delete Bob's records", async () => {
  await testEnv.withSecurityRulesDisabled(async (context) => {
    const bobRef = context
      .firestore()
      .collection("users")
      .doc(BOB_UID)
      .collection("consultas")
      .doc("bob_c1");
    await bobRef.set(validConsultaPayload(BOB_UID));
  });

  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobCol = aliceDb
    .collection("users")
    .doc(BOB_UID)
    .collection("consultas");

  await assertFails(bobCol.get());
  await assertFails(bobCol.doc("bob_c1").get());
  await assertFails(
    bobCol.doc("alice_inject").set(validConsultaPayload(ALICE_UID))
  );
  await assertFails(
    bobCol.doc("bob_c1").update({
      pinned: true,
      updatedAt: new Date(Date.now() - 500),
    })
  );
  await assertFails(bobCol.doc("bob_c1").delete());
});
