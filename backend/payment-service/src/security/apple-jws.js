/**
 * JWS verification for Apple App Store Server Notifications V2.
 *
 * Apple signs `signedPayload` (and the nested `signedTransactionInfo` /
 * `signedRenewalInfo`) with a leaf certificate whose chain terminates at the
 * Apple Root CA - G3. Decoding the JWS without checking that chain accepts any
 * payload anyone cares to POST, which is why this module exists.
 *
 * Deliberately built on `node:crypto` alone — no npm dependency — so the
 * security-critical half of the webhook can be unit tested with `node --test`
 * without installing express/pg.
 */
const crypto = require('node:crypto');
const fs = require('node:fs');

/**
 * Thrown for every rejected payload. A caller that catches this must treat the
 * notification as forged: never fall back to decoding it unverified.
 */
class JwsVerificationError extends Error {
    constructor(message) {
        super(message);
        this.name = 'JwsVerificationError';
    }
}

// Apple signs with ES256 over P-256. Pinning the algorithm here means "alg":
// "none" and algorithm-confusion attacks are rejected before a key is loaded.
const REQUIRED_ALG = 'ES256';
const REQUIRED_CURVE = 'prime256v1';

// Apple sends [leaf, intermediate, root]. Allow a slightly longer chain in case
// Apple lengthens it, but not an unbounded one — each element costs a signature
// verification, so an attacker-supplied x5c is a cheap CPU amplifier.
const MAX_CHAIN_LENGTH = 5;

const PEM_BLOCK = /-----BEGIN CERTIFICATE-----[\s\S]+?-----END CERTIFICATE-----/g;


function decodeBase64Url(segment) {
    if (typeof segment !== 'string' || !/^[A-Za-z0-9_-]*$/.test(segment)) {
        throw new JwsVerificationError('JWS segment is not base64url');
    }
    return Buffer.from(segment, 'base64url');
}

function parseJson(buffer, what) {
    let value;
    try {
        value = JSON.parse(buffer.toString('utf8'));
    } catch (error) {
        throw new JwsVerificationError(`JWS ${what} is not valid JSON`);
    }
    if (value === null || typeof value !== 'object' || Array.isArray(value)) {
        throw new JwsVerificationError(`JWS ${what} is not a JSON object`);
    }
    return value;
}

/**
 * Parse one or more certificates from PEM text or a single DER blob.
 * Apple publishes its roots as DER (.cer); secret stores usually hold PEM.
 */
function parseCertificates(material) {
    const text = Buffer.isBuffer(material) ? material.toString('binary') : String(material);
    const blocks = text.match(PEM_BLOCK);

    if (blocks) {
        return blocks.map((block) => new crypto.X509Certificate(block));
    }

    // No PEM armour: treat the bytes as a single DER certificate.
    const der = Buffer.isBuffer(material) ? material : Buffer.from(material, 'binary');
    return [new crypto.X509Certificate(der)];
}

/**
 * Load the pinned trust anchors.
 *
 * Nothing is hardcoded: shipping a fingerprint this code cannot check against
 * the real certificate would be a guess, and a wrong guess silently rejects
 * every genuine notification. The operator supplies Apple's root, downloaded
 * from https://www.apple.com/certificateauthority/ (AppleRootCA-G3), via:
 *
 *   APPLE_ROOT_CA_PEM   inline PEM (one or more certificates)
 *   APPLE_ROOT_CA_PATH  path to a .pem or .cer file
 *
 * Returns an empty array when neither is set. Callers must fail closed on that.
 */
function loadTrustAnchors(env = process.env) {
    if (env.APPLE_ROOT_CA_PEM) {
        return parseCertificates(env.APPLE_ROOT_CA_PEM);
    }
    if (env.APPLE_ROOT_CA_PATH) {
        return parseCertificates(fs.readFileSync(env.APPLE_ROOT_CA_PATH));
    }
    return [];
}

function validityWindow(cert) {
    // validFromDate/validToDate landed in Node 18.13; fall back to the string
    // form ("Mar 12 00:00:00 2024 GMT"), which Date.parse handles.
    const from = cert.validFromDate ?? new Date(cert.validFrom);
    const to = cert.validToDate ?? new Date(cert.validTo);
    return { from, to };
}

function assertWithinValidity(cert, at, label) {
    const { from, to } = validityWindow(cert);
    if (Number.isNaN(from.getTime()) || Number.isNaN(to.getTime())) {
        throw new JwsVerificationError(`${label} certificate has an unreadable validity period`);
    }
    if (at < from || at > to) {
        throw new JwsVerificationError(
            `${label} certificate is outside its validity period (${cert.validFrom} - ${cert.validTo})`
        );
    }
}

function readChain(x5c) {
    if (!Array.isArray(x5c) || x5c.length < 2) {
        throw new JwsVerificationError('JWS header has no x5c certificate chain');
    }
    if (x5c.length > MAX_CHAIN_LENGTH) {
        throw new JwsVerificationError(`x5c chain is longer than ${MAX_CHAIN_LENGTH} certificates`);
    }
    return x5c.map((entry, index) => {
        if (typeof entry !== 'string') {
            throw new JwsVerificationError(`x5c entry ${index} is not a string`);
        }
        try {
            return new crypto.X509Certificate(Buffer.from(entry, 'base64'));
        } catch (error) {
            throw new JwsVerificationError(`x5c entry ${index} is not a DER certificate`);
        }
    });
}

/**
 * Validate the presented chain and anchor it to one of the pinned roots.
 * Returns the leaf certificate, whose key is the only one allowed to have
 * signed the payload.
 */
function verifyChain(chain, roots, at) {
    for (let i = 0; i < chain.length; i += 1) {
        assertWithinValidity(chain[i], at, i === 0 ? 'Leaf' : `Chain[${i}]`);
    }

    for (let i = 0; i < chain.length - 1; i += 1) {
        const child = chain[i];
        const issuer = chain[i + 1];

        // Without the CA check, a leaf certificate could be presented as the
        // issuer of a second certificate the attacker minted themselves.
        if (!issuer.ca) {
            throw new JwsVerificationError(`x5c entry ${i + 1} is not a CA certificate`);
        }
        if (!child.checkIssued(issuer) || !child.verify(issuer.publicKey)) {
            throw new JwsVerificationError(`x5c entry ${i} was not issued by entry ${i + 1}`);
        }
    }

    const top = chain[chain.length - 1];
    const anchored = roots.some((root) => {
        // Apple includes its root in x5c, so the usual case is byte equality
        // with the pinned copy. Also accept a chain that stops one short of it.
        if (top.fingerprint256 === root.fingerprint256) {
            return true;
        }
        return root.ca && top.checkIssued(root) && top.verify(root.publicKey);
    });

    if (!anchored) {
        throw new JwsVerificationError('x5c chain does not terminate at a pinned Apple root certificate');
    }

    return chain[0];
}

/**
 * Verify an Apple JWS and return its decoded payload.
 *
 * @param {string} signedPayload compact JWS from Apple
 * @param {object} options
 * @param {crypto.X509Certificate[]} options.roots pinned trust anchors
 * @param {Date} [options.at] evaluation time, for certificate validity
 * @throws {JwsVerificationError} on any failure — there is no partial success
 */
function verifyAppleJws(signedPayload, { roots, at = new Date() } = {}) {
    if (!Array.isArray(roots) || roots.length === 0) {
        throw new JwsVerificationError('no Apple root certificate is configured');
    }
    if (typeof signedPayload !== 'string') {
        throw new JwsVerificationError('signedPayload is not a string');
    }

    const parts = signedPayload.split('.');
    if (parts.length !== 3) {
        throw new JwsVerificationError('signedPayload is not a compact JWS');
    }
    const [encodedHeader, encodedPayload, encodedSignature] = parts;

    const header = parseJson(decodeBase64Url(encodedHeader), 'header');
    if (header.alg !== REQUIRED_ALG) {
        throw new JwsVerificationError(`unsupported JWS algorithm ${JSON.stringify(header.alg)}`);
    }

    const leaf = verifyChain(readChain(header.x5c), roots, at);

    const details = leaf.publicKey.asymmetricKeyDetails ?? {};
    if (leaf.publicKey.asymmetricKeyType !== 'ec' || details.namedCurve !== REQUIRED_CURVE) {
        throw new JwsVerificationError('leaf certificate does not hold a P-256 key');
    }

    const signature = decodeBase64Url(encodedSignature);
    const signed = crypto.verify(
        'sha256',
        Buffer.from(`${encodedHeader}.${encodedPayload}`),
        // JWS carries the raw r||s pair, not the DER sequence crypto defaults to.
        { key: leaf.publicKey, dsaEncoding: 'ieee-p1363' },
        signature
    );

    if (!signed) {
        throw new JwsVerificationError('JWS signature does not match the leaf certificate');
    }

    return parseJson(decodeBase64Url(encodedPayload), 'payload');
}


module.exports = {
    JwsVerificationError,
    loadTrustAnchors,
    parseCertificates,
    verifyAppleJws,
};
