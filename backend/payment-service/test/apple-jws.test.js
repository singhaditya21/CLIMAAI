/**
 * Tests for the Apple App Store Server Notification signature check.
 *
 * Run with:  node --test backend/payment-service/test/
 *
 * The chain is minted with openssl at test time rather than committed as a
 * fixture, so nothing here expires and no private key lives in the repo.
 */
const assert = require('node:assert/strict');
const crypto = require('node:crypto');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const { execFileSync } = require('node:child_process');
const { before, test } = require('node:test');

const {
    JwsVerificationError,
    loadTrustAnchors,
    verifyAppleJws,
} = require('../src/security/apple-jws');

const OPENSSL_CNF = `
[req]
distinguished_name = dn
prompt = no

[dn]
CN = ClimaAI Test

[v3_ca]
basicConstraints = critical,CA:TRUE
keyUsage = critical,keyCertSign,cRLSign
subjectKeyIdentifier = hash

[v3_leaf]
basicConstraints = critical,CA:FALSE
keyUsage = critical,digitalSignature
subjectKeyIdentifier = hash
`;

let dir;
let cnf;
let apple;      // root -> intermediate -> leaf, standing in for Apple's chain
let impostor;   // an unrelated chain, i.e. what a forger can always produce

function openssl(...args) {
    execFileSync('openssl', args, { cwd: dir, stdio: ['ignore', 'pipe', 'pipe'] });
}

function generateKey(name) {
    openssl('ecparam', '-name', 'prime256v1', '-genkey', '-noout', '-out', `${name}.key`);
    return crypto.createPrivateKey(fs.readFileSync(path.join(dir, `${name}.key`)));
}

function certificate(name) {
    return new crypto.X509Certificate(fs.readFileSync(path.join(dir, `${name}.pem`)));
}

/** Mint root -> intermediate -> leaf, all P-256, under the given name prefix. */
function generateChain(prefix) {
    const rootKey = generateKey(`${prefix}-root`);
    openssl(
        'req', '-new', '-x509', '-key', `${prefix}-root.key`, '-sha256', '-days', '3650',
        '-subj', `/CN=${prefix} root`, '-config', cnf, '-extensions', 'v3_ca',
        '-out', `${prefix}-root.pem`
    );

    const intermediateKey = generateKey(`${prefix}-intermediate`);
    openssl(
        'req', '-new', '-key', `${prefix}-intermediate.key`,
        '-subj', `/CN=${prefix} intermediate`, '-config', cnf, '-out', `${prefix}-intermediate.csr`
    );
    openssl(
        'x509', '-req', '-in', `${prefix}-intermediate.csr`, '-CA', `${prefix}-root.pem`,
        '-CAkey', `${prefix}-root.key`, '-CAcreateserial', '-days', '3650', '-sha256',
        '-extfile', cnf, '-extensions', 'v3_ca', '-out', `${prefix}-intermediate.pem`
    );

    const leafKey = generateKey(`${prefix}-leaf`);
    openssl(
        'req', '-new', '-key', `${prefix}-leaf.key`,
        '-subj', `/CN=${prefix} leaf`, '-config', cnf, '-out', `${prefix}-leaf.csr`
    );
    openssl(
        'x509', '-req', '-in', `${prefix}-leaf.csr`, '-CA', `${prefix}-intermediate.pem`,
        '-CAkey', `${prefix}-intermediate.key`, '-CAcreateserial', '-days', '3650', '-sha256',
        '-extfile', cnf, '-extensions', 'v3_leaf', '-out', `${prefix}-leaf.pem`
    );

    const root = certificate(`${prefix}-root`);
    const intermediate = certificate(`${prefix}-intermediate`);
    const leaf = certificate(`${prefix}-leaf`);

    return {
        rootKey,
        intermediateKey,
        leafKey,
        root,
        intermediate,
        leaf,
        // Apple orders x5c leaf-first and includes its own root.
        x5c: [leaf, intermediate, root].map((cert) => cert.raw.toString('base64')),
    };
}

function encode(value) {
    return Buffer.from(JSON.stringify(value)).toString('base64url');
}

function signJws(header, payload, key) {
    const signingInput = `${encode(header)}.${encode(payload)}`;
    const signature = crypto.sign(
        'sha256',
        Buffer.from(signingInput),
        { key, dsaEncoding: 'ieee-p1363' }
    );
    return `${signingInput}.${signature.toString('base64url')}`;
}

const NOTIFICATION = {
    notificationType: 'SUBSCRIBED',
    notificationUUID: '00000000-0000-0000-0000-000000000001',
    data: { bundleId: 'com.climaai.app', environment: 'Production' },
};

before(() => {
    dir = fs.mkdtempSync(path.join(os.tmpdir(), 'climaai-jws-'));
    cnf = path.join(dir, 'openssl.cnf');
    fs.writeFileSync(cnf, OPENSSL_CNF);
    apple = generateChain('apple');
    impostor = generateChain('impostor');
});

test('accepts a payload signed by a leaf chaining to the pinned root', () => {
    const jws = signJws({ alg: 'ES256', x5c: apple.x5c }, NOTIFICATION, apple.leafKey);

    assert.deepEqual(verifyAppleJws(jws, { roots: [apple.root] }), NOTIFICATION);
});

test('accepts a chain that stops below the pinned root', () => {
    // Apple sends the root inside x5c today; the anchor must still hold if it
    // ever stops doing so.
    const x5c = apple.x5c.slice(0, 2);
    const jws = signJws({ alg: 'ES256', x5c }, NOTIFICATION, apple.leafKey);

    assert.deepEqual(verifyAppleJws(jws, { roots: [apple.root] }), NOTIFICATION);
});

test('rejects a chain anchored to an attacker-controlled root', () => {
    // The whole attack: mint your own CA, sign whatever entitlement you want.
    const jws = signJws({ alg: 'ES256', x5c: impostor.x5c }, NOTIFICATION, impostor.leafKey);

    assert.throws(
        () => verifyAppleJws(jws, { roots: [apple.root] }),
        (error) => error instanceof JwsVerificationError && /pinned Apple root/.test(error.message)
    );
});

test('rejects a payload edited after signing', () => {
    const jws = signJws({ alg: 'ES256', x5c: apple.x5c }, NOTIFICATION, apple.leafKey);
    const [header, , signature] = jws.split('.');
    const tampered = [
        header,
        encode({ ...NOTIFICATION, notificationType: 'DID_RENEW' }),
        signature,
    ].join('.');

    assert.throws(
        () => verifyAppleJws(tampered, { roots: [apple.root] }),
        (error) => error instanceof JwsVerificationError && /signature does not match/.test(error.message)
    );
});

test('rejects a genuine Apple chain signed with a key that is not the leaf', () => {
    const jws = signJws({ alg: 'ES256', x5c: apple.x5c }, NOTIFICATION, impostor.leafKey);

    assert.throws(
        () => verifyAppleJws(jws, { roots: [apple.root] }),
        (error) => error instanceof JwsVerificationError && /signature does not match/.test(error.message)
    );
});

test('rejects alg none', () => {
    const header = encode({ alg: 'none', x5c: apple.x5c });
    const jws = `${header}.${encode(NOTIFICATION)}.`;

    assert.throws(
        () => verifyAppleJws(jws, { roots: [apple.root] }),
        (error) => error instanceof JwsVerificationError && /unsupported JWS algorithm/.test(error.message)
    );
});

test('rejects a header with no x5c chain', () => {
    const jws = signJws({ alg: 'ES256' }, NOTIFICATION, apple.leafKey);

    assert.throws(
        () => verifyAppleJws(jws, { roots: [apple.root] }),
        (error) => error instanceof JwsVerificationError && /no x5c certificate chain/.test(error.message)
    );
});

test('rejects an intermediate that is not a CA', () => {
    // A leaf certificate presented as an issuer: legal DER, illegal chain.
    const x5c = [apple.x5c[0], apple.x5c[0], apple.x5c[2]];
    const jws = signJws({ alg: 'ES256', x5c }, NOTIFICATION, apple.leafKey);

    assert.throws(
        () => verifyAppleJws(jws, { roots: [apple.root] }),
        (error) => error instanceof JwsVerificationError && /not a CA certificate/.test(error.message)
    );
});

test('rejects everything when no root is configured', () => {
    const jws = signJws({ alg: 'ES256', x5c: apple.x5c }, NOTIFICATION, apple.leafKey);

    assert.throws(
        () => verifyAppleJws(jws, { roots: [] }),
        (error) => error instanceof JwsVerificationError && /no Apple root certificate/.test(error.message)
    );
});

test('rejects a certificate outside its validity period', () => {
    const jws = signJws({ alg: 'ES256', x5c: apple.x5c }, NOTIFICATION, apple.leafKey);
    const longAfterExpiry = new Date(Date.now() + 20 * 365 * 24 * 60 * 60 * 1000);

    assert.throws(
        () => verifyAppleJws(jws, { roots: [apple.root], at: longAfterExpiry }),
        (error) => error instanceof JwsVerificationError && /validity period/.test(error.message)
    );
});

test('loadTrustAnchors reads inline PEM and returns nothing when unset', () => {
    const pem = apple.root.toString();

    assert.equal(loadTrustAnchors({ APPLE_ROOT_CA_PEM: pem })[0].fingerprint256, apple.root.fingerprint256);
    assert.equal(loadTrustAnchors({}).length, 0);
});

test('loadTrustAnchors reads a DER .cer file, which is how Apple publishes roots', () => {
    const der = path.join(dir, 'root.cer');
    fs.writeFileSync(der, apple.root.raw);

    assert.equal(loadTrustAnchors({ APPLE_ROOT_CA_PATH: der })[0].fingerprint256, apple.root.fingerprint256);
});
