const jwt = require('jsonwebtoken');
const { X509Certificate } = require('crypto');
const fs = require('fs');
const path = require('path');

// Load Apple Root CA
const APPLE_ROOT_CA_PATH = path.join(__dirname, '../certs/AppleRootCA-G3.pem');
let APPLE_ROOT_CA_CERT;

try {
    if (fs.existsSync(APPLE_ROOT_CA_PATH)) {
        const fileContent = fs.readFileSync(APPLE_ROOT_CA_PATH);
        APPLE_ROOT_CA_CERT = new X509Certificate(fileContent);
    } else {
        console.warn(`Apple Root CA not found at ${APPLE_ROOT_CA_PATH}`);
    }
} catch (error) {
    console.error('Failed to load Apple Root CA:', error);
}

/**
 * Verify Apple JWS signature and certificate chain
 * @param {string} token - The JWS token (signedPayload)
 * @param {X509Certificate} [trustedRoot] - Optional trusted root certificate (defaults to Apple Root CA)
 * @returns {object} - The decoded payload
 * @throws {Error} - If verification fails
 */
function verifyAppleJWS(token, trustedRoot = APPLE_ROOT_CA_CERT) {
    if (!token) {
        throw new Error('Missing token');
    }

    if (!trustedRoot) {
        throw new Error('Trusted Root CA is not loaded or provided');
    }

    // Decode header to get x5c
    const decoded = jwt.decode(token, { complete: true });

    if (!decoded || !decoded.header || !decoded.header.x5c) {
        throw new Error('Invalid JWS: Missing header or x5c');
    }

    const { x5c, alg } = decoded.header;

    if (alg !== 'ES256') {
        throw new Error(`Invalid algorithm: ${alg}. Expected ES256.`);
    }

    if (!Array.isArray(x5c) || x5c.length === 0) {
        throw new Error('Invalid x5c: Empty or not an array');
    }

    // Parse certificates
    let certs;
    try {
        certs = x5c.map(c => new X509Certificate(Buffer.from(c, 'base64')));
    } catch (e) {
        throw new Error('Failed to parse x5c certificates: ' + e.message);
    }

    // Verify certificate chain
    const now = new Date();

    for (let i = 0; i < certs.length; i++) {
        const cert = certs[i];

        // Check validity period
        if (new Date(cert.validFrom) > now || new Date(cert.validTo) < now) {
            throw new Error(`Certificate at index ${i} is expired or not yet valid`);
        }

        // Verify chain link
        if (i < certs.length - 1) {
            const issuer = certs[i + 1];
            if (!cert.checkIssued(issuer)) {
                throw new Error(`Certificate at index ${i} is not issued by certificate at index ${i + 1}`);
            }
            if (!cert.verify(issuer.publicKey)) {
                throw new Error(`Certificate signature verification failed at index ${i}`);
            }
        } else {
            // Verify the last certificate against the trusted root
            if (!cert.checkIssued(trustedRoot)) {
                throw new Error('Certificate chain is not trusted by the Root CA');
            }
            if (!cert.verify(trustedRoot.publicKey)) {
                throw new Error('Certificate chain signature verification failed against Root CA');
            }
        }
    }

    // Verify JWS signature using the leaf certificate (first one)
    const leafCert = certs[0];
    const publicKey = leafCert.publicKey;

    try {
        // verify function returns the payload if successful
        return jwt.verify(token, publicKey, { algorithms: ['ES256'] });
    } catch (err) {
        throw new Error('JWS signature verification failed: ' + err.message);
    }
}

module.exports = {
    verifyAppleJWS,
    // Export for testing purposes if needed
    getAppleRootCA: () => APPLE_ROOT_CA_CERT
};
