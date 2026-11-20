import assert from "node:assert/strict";
import { randomUUID } from "node:crypto";

const project = "demo-seluna";
const database = `projects/${project}/databases/(default)`;
const auth = "http://127.0.0.1:9099/identitytoolkit.googleapis.com/v1/accounts:signUp?key=test";
const firestore = `http://127.0.0.1:8080/v1/${database}/documents`;
const headers = token => ({
  "Content-Type": "application/json",
  ...(token ? { Authorization: `Bearer ${token}` } : {}),
});

async function account() {
  const response = await fetch(auth, {
    method: "POST",
    headers: headers(),
    body: JSON.stringify({
      email: `${randomUUID()}@example.invalid`,
      password: "ExampleQA26!",
      returnSecureToken: true,
    }),
  });
  assert.equal(response.status, 200);
  return response.json();
}

function write(path, token, fields) {
  return fetch(`${firestore}:commit`, {
    method: "POST",
    headers: headers(token),
    body: JSON.stringify({
      writes: [{
        update: { name: `${database}/documents/${path}`, fields },
        updateTransforms: [{ fieldPath: "updatedAt", setToServerValue: "REQUEST_TIME" }],
      }],
    }),
  });
}

function get(path, token) {
  return fetch(`${firestore}/${path}`, { headers: headers(token) });
}

const owner = await account();
const other = await account();
const profile = `users/${owner.localId}`;
const backup = `${profile}/backups/current`;
const name = { displayName: { stringValue: "Nombre elegido QA" } };
const history = {
  schemaVersion: { integerValue: "1" },
  cycleStarts: { arrayValue: {} },
  entries: { arrayValue: {} },
};

assert.equal((await write(profile, owner.idToken, name)).status, 200, "Crear perfil propio");
const readProfile = await get(profile, owner.idToken);
assert.equal(readProfile.status, 200, "Leer perfil propio");
assert.deepEqual(Object.keys((await readProfile.json()).fields).sort(), ["displayName", "updatedAt"]);
assert.equal((await write(profile, owner.idToken, name)).status, 200, "Actualizar perfil propio");
assert.equal((await get(profile, other.idToken)).status, 403, "Bloquear lectura de otro UID");
assert.equal((await get(profile)).status, 403, "Bloquear lectura anónima");
assert.equal((await get("users", owner.idToken)).status, 403, "Bloquear listados de perfiles");
assert.equal((await write(profile, other.idToken, name)).status, 403, "Bloquear cambios de otro UID");
assert.equal((await write(profile, owner.idToken, { ...name, email: { stringValue: "a@example.invalid" } })).status, 403, "Rechazar campos extra");
assert.equal((await write(profile, owner.idToken, { displayName: { stringValue: "" } })).status, 403, "Rechazar nombres vacíos");

assert.equal((await write(backup, owner.idToken, history)).status, 200, "Guardar copia propia");
assert.equal((await get(backup, owner.idToken)).status, 200, "Leer copia propia");
assert.equal((await get(backup, other.idToken)).status, 403, "Bloquear copia de otro UID");
assert.equal((await get(backup)).status, 403, "Bloquear copia anónima");
assert.equal((await write(backup, other.idToken, history)).status, 403, "Bloquear cambios de otro UID");
console.log("PASS: 14 comprobaciones de Firestore para perfiles, copias y permisos.");
