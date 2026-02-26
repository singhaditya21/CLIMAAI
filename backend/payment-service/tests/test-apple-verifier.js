const { execSync } = require('child_process');
const fs = require('fs');
const path = require('path');
const jwt = require('jsonwebtoken');
const { X509Certificate } = require('crypto');
const { verifyAppleJWS, getAppleRootCA } = require('../src/utils/apple-cert-verifier');

const TEMP_DIR = path.join(__dirname, 'temp_certs');

function generateKeysAndCerts() {
    console.log('Generating test keys and certificates...');

    if (fs.existsSync(TEMP_DIR)) {
        fs.rmSync(TEMP_DIR, { recursive: true, force: true });
    }
    fs.mkdirSync(TEMP_DIR);

    try {
        // 1. Generate Root CA Key and Cert
        // Apple uses ECDSA P-256
        execSync(`openssl req -x509 -newkey ec -pkeyopt ec_paramgen_curve:prime256v1 -keyout "${TEMP_DIR}/root.key" -out "${TEMP_DIR}/root.pem" -days 365 -nodes -subj "/CN=Test Root CA"`);

        // 2. Generate Intermediate Key and CSR
        execSync(`openssl req -newkey ec -pkeyopt ec_paramgen_curve:prime256v1 -keyout "${TEMP_DIR}/intermediate.key" -out "${TEMP_DIR}/intermediate.csr" -nodes -subj "/CN=Test Intermediate CA"`);

        // 3. Sign Intermediate with Root
        // Create extensions file for CA usage
        const v3Config = `
basicConstraints = CA:TRUE
keyUsage = digitalSignature, keyCertSign, cRLSign
        `;
        const extPath = path.join(TEMP_DIR, 'v3.ext');
        fs.writeFileSync(extPath, v3Config);

        // Sign intermediate
        execSync(`openssl x509 -req -in "${TEMP_DIR}/intermediate.csr" -CA "${TEMP_DIR}/root.pem" -CAkey "${TEMP_DIR}/root.key" -CAcreateserial -out "${TEMP_DIR}/intermediate.pem" -days 365 -extfile "${extPath}"`);

        // 4. Generate Leaf Key and CSR
        execSync(`openssl req -newkey ec -pkeyopt ec_paramgen_curve:prime256v1 -keyout "${TEMP_DIR}/leaf.key" -out "${TEMP_DIR}/leaf.csr" -nodes -subj "/CN=Test Leaf"`);

        // 5. Sign Leaf with Intermediate
        // Leaf extensions
        const leafConfig = `
basicConstraints = CA:FALSE
keyUsage = digitalSignature
        `;
        const leafExtPath = path.join(TEMP_DIR, 'leaf.ext');
        fs.writeFileSync(leafExtPath, leafConfig);

        execSync(`openssl x509 -req -in "${TEMP_DIR}/leaf.csr" -CA "${TEMP_DIR}/intermediate.pem" -CAkey "${TEMP_DIR}/intermediate.key" -CAcreateserial -out "${TEMP_DIR}/leaf.pem" -days 365 -extfile "${leafExtPath}"`);

        // Read files
        const rootCert = new X509Certificate(fs.readFileSync(path.join(TEMP_DIR, 'root.pem')));
        const intermediateCertDer = fs.readFileSync(path.join(TEMP_DIR, 'intermediate.pem'));
        const leafCertDer = fs.readFileSync(path.join(TEMP_DIR, 'leaf.pem'));
        const leafKey = fs.readFileSync(path.join(TEMP_DIR, 'leaf.key'));

        // Helper to convert PEM to base64 string (strip header/footer/newlines)
        // Actually, openssl output is PEM (base64 with headers).
        // JWS x5c expects base64 string of DER.
        // Wait, PEM is base64 of DER but with headers.
        // So I can just strip headers/footers and newlines.

        const pemToDerBase64 = (pemBuffer) => {
            const pem = pemBuffer.toString();
            return pem
                .replace(/-----BEGIN CERTIFICATE-----/g, '')
                .replace(/-----END CERTIFICATE-----/g, '')
                .replace(/[\r\n\s]/g, '');
        };

        const x5c = [pemToDerBase64(leafCertDer), pemToDerBase64(intermediateCertDer)];

        return { rootCert, x5c, leafKey };

    } catch (e) {
        console.error('Error generating certificates:', e.message);
        if (e.stderr) console.error('OpenSSL stderr:', e.stderr.toString());
        throw e;
    }
}

async function runTests() {
    try {
        // Test 0: Verify Apple Root CA is loaded
        console.log('Test 0: Verifying Apple Root CA is loaded...');
        const appleRoot = getAppleRootCA();
        if (appleRoot && appleRoot.subject && appleRoot.subject.includes('Apple Root CA - G3')) {
            console.log('✅ Passed: Apple Root CA loaded successfully');
        } else {
             console.error('❌ Failed: Apple Root CA not loaded or incorrect');
             process.exit(1);
        }

        const { rootCert, x5c, leafKey } = generateKeysAndCerts();

        console.log('Running verifyAppleJWS tests...');

        // 1. Valid Signature
        const payload = {
            notificationType: 'TEST',
            subtype: 'UNIT_TEST',
            data: { transactionId: '123' },
            exp: Math.floor(Date.now() / 1000) + 3600
        };

        const token = jwt.sign(payload, leafKey, {
            algorithm: 'ES256',
            header: { x5c, alg: 'ES256' }
        });

        console.log('Test 1: Valid signature and chain');
        const decoded = verifyAppleJWS(token, rootCert);
        if (decoded.notificationType === 'TEST') {
            console.log('✅ Passed');
        } else {
            console.error('❌ Failed: Payload mismatch');
            process.exit(1);
        }

        // 2. Invalid Signature (Tampered Payload)
        console.log('Test 2: Tampered payload');
        const parts = token.split('.');
        // Tamper with payload
        const tamperedPayload = Buffer.from(JSON.stringify({ ...payload, notificationType: 'TAMPERED' })).toString('base64').replace(/=/g, '');
        const tamperedToken = `${parts[0]}.${tamperedPayload}.${parts[2]}`;

        try {
            verifyAppleJWS(tamperedToken, rootCert);
            console.error('❌ Failed: Should have thrown error for invalid signature');
            process.exit(1);
        } catch (e) {
            console.log('✅ Passed: Caught error:', e.message);
        }

        // 3. Untrusted Root
        console.log('Test 3: Untrusted root');
        // Create another random root
        const otherRootKeyPath = path.join(TEMP_DIR, 'other.key');
        const otherRootCertPath = path.join(TEMP_DIR, 'other.pem');
        execSync(`openssl req -x509 -newkey ec -pkeyopt ec_paramgen_curve:prime256v1 -keyout "${otherRootKeyPath}" -out "${otherRootCertPath}" -days 365 -nodes -subj "/CN=Other Root"`);
        const otherRootCert = new X509Certificate(fs.readFileSync(otherRootCertPath));

        try {
            verifyAppleJWS(token, otherRootCert); // Verify valid token against WRONG root
            console.error('❌ Failed: Should have thrown error for untrusted root');
            process.exit(1);
        } catch (e) {
            console.log('✅ Passed: Caught error:', e.message);
        }

        // 4. Broken Chain (Leaf signed by someone else, not intermediate)
        console.log('Test 4: Broken chain');
        // Generate a new intermediate that didn't sign the leaf
        const badInterPath = path.join(TEMP_DIR, 'bad_inter.pem');
        const badInterKey = path.join(TEMP_DIR, 'bad_inter.key');
        execSync(`openssl req -x509 -newkey ec -pkeyopt ec_paramgen_curve:prime256v1 -keyout "${badInterKey}" -out "${badInterPath}" -days 365 -nodes -subj "/CN=Bad Intermediate"`);
        const badInterDer = fs.readFileSync(badInterPath);

        // Construct token with original leaf but swapped intermediate in x5c
        const badX5c = [x5c[0], pemToDerBase64(badInterDer)];
        const badChainToken = jwt.sign(payload, leafKey, {
            algorithm: 'ES256',
            header: { x5c: badX5c, alg: 'ES256' }
        });

        try {
            verifyAppleJWS(badChainToken, rootCert);
            console.error('❌ Failed: Should have thrown error for broken chain');
            process.exit(1);
        } catch (e) {
             console.log('✅ Passed: Caught error:', e.message);
        }

        console.log('All tests passed!');

    } catch (error) {
        console.error('Test script failed:', error);
        process.exit(1);
    } finally {
        // cleanup
        if (fs.existsSync(TEMP_DIR)) {
            fs.rmSync(TEMP_DIR, { recursive: true, force: true });
        }
    }
}

// Helper needed inside runTests
const pemToDerBase64 = (pemBuffer) => {
    const pem = pemBuffer.toString();
    return pem
        .replace(/-----BEGIN CERTIFICATE-----/g, '')
        .replace(/-----END CERTIFICATE-----/g, '')
        .replace(/[\r\n\s]/g, '');
};

runTests();
