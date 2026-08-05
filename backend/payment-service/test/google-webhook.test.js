/**
 * Tests for the Google Play RTDN webhook's authentication.
 *
 * Run with:  node --test backend/payment-service/test/
 * (needs the service's dependencies installed: express is required to mount
 * the router.)
 *
 * An RTDN body carries no signature at all — the only proof of origin is the
 * Cloud Pub/Sub OIDC token on the request. So "a forged notification" is any
 * request whose token Google's verifier would not vouch for, or one vouched
 * for on behalf of the wrong account. Every one of those must bounce off
 * without touching the database, because a request that reaches the UPDATE is
 * a request that grants entitlements.
 *
 * The OAuth2Client is stubbed: the real one can only accept tokens Google
 * actually signed, which is precisely what a forgery test cannot produce.
 * What is under test is the handler's reaction to each verifier verdict.
 */
const assert = require('node:assert/strict');
const express = require('express');
const { test } = require('node:test');

const { createGoogleWebhookRouter } = require('../src/routes/google-webhook');

const ENV = {
    PUBSUB_AUDIENCE: 'https://payments.climaai.app/webhooks/google',
    PUBSUB_SERVICE_ACCOUNT_EMAIL: 'rtdn-push@climaai.iam.gserviceaccount.com',
    GOOGLE_PACKAGE_NAME: 'com.climaai.app',
};

/** Claims for a token our real Pub/Sub subscription would carry. */
function genuineClaims(overrides = {}) {
    return {
        email: ENV.PUBSUB_SERVICE_ACCOUNT_EMAIL,
        email_verified: true,
        aud: ENV.PUBSUB_AUDIENCE,
        iss: 'https://accounts.google.com',
        ...overrides,
    };
}

/**
 * Mimics OAuth2Client.verifyIdToken: resolves with a ticket when Google's
 * checks would pass, rejects when they would not.
 */
function stubAuthClient(outcome) {
    return {
        calls: [],
        async verifyIdToken(options) {
            this.calls.push(options);
            if (outcome instanceof Error) throw outcome;
            return { getPayload: () => outcome };
        },
    };
}

/** Records queries; answering one row, as a matched UPDATE ... RETURNING does. */
function recordingDb({ rows = [{ id: 'subscription-row' }], fail = null } = {}) {
    return {
        queries: [],
        async query(text, params) {
            this.queries.push({ text, params });
            if (fail) throw fail;
            return { rows };
        },
    };
}

function rtdnBody(dataOverrides = {}) {
    const data = {
        version: '1.0',
        packageName: ENV.GOOGLE_PACKAGE_NAME,
        eventTimeMillis: '1700000000000',
        subscriptionNotification: {
            version: '1.0',
            notificationType: 4, // SUBSCRIPTION_PURCHASED
            purchaseToken: 'purchase-token-1',
            subscriptionId: 'climaai_pro_monthly',
        },
        ...dataOverrides,
    };
    return {
        message: {
            data: Buffer.from(JSON.stringify(data)).toString('base64'),
            messageId: '1357924680',
        },
        subscription: 'projects/climaai/subscriptions/play-rtdn-push',
    };
}

/** Mount the router in a throwaway app and POST one notification at it. */
async function post(router, db, { headers = {}, body = rtdnBody() } = {}) {
    const app = express();
    app.use(express.json());
    app.locals.db = db;
    app.use('/webhooks/google', router);

    const server = await new Promise((resolve) => {
        const s = app.listen(0, '127.0.0.1', () => resolve(s));
    });

    try {
        const response = await fetch(
            `http://127.0.0.1:${server.address().port}/webhooks/google`,
            {
                method: 'POST',
                headers: { 'content-type': 'application/json', ...headers },
                body: JSON.stringify(body),
            }
        );
        return { status: response.status, body: await response.json() };
    } finally {
        server.close();
    }
}

const BEARER = { authorization: 'Bearer whatever-the-attacker-sends' };

test('rejects a notification with no token at all', async () => {
    const authClient = stubAuthClient(genuineClaims());
    const db = recordingDb();
    const router = createGoogleWebhookRouter({ env: ENV, authClient });

    const response = await post(router, db, { headers: {} });

    assert.equal(response.status, 401);
    assert.equal(db.queries.length, 0);
});

test('rejects a forged token that fails Google verification', async () => {
    // What google-auth-library does for a token Google never signed, an
    // expired one, or one minted for a different audience.
    const authClient = stubAuthClient(new Error('Invalid token signature'));
    const db = recordingDb();
    const router = createGoogleWebhookRouter({ env: ENV, authClient });

    const response = await post(router, db, { headers: BEARER });

    assert.equal(response.status, 401);
    assert.equal(db.queries.length, 0, 'a forged notification must never reach the database');
});

test('rejects a genuine Google token from an account that is not our push service account', async () => {
    // Anyone with a Google account can mint a valid OIDC token for our
    // audience; the chain check passes and only the email check stands.
    const authClient = stubAuthClient(genuineClaims({ email: 'attacker@gmail.com' }));
    const db = recordingDb();
    const router = createGoogleWebhookRouter({ env: ENV, authClient });

    const response = await post(router, db, { headers: BEARER });

    assert.equal(response.status, 401);
    assert.equal(db.queries.length, 0);
});

test('rejects a token whose email Google has not verified', async () => {
    const authClient = stubAuthClient(genuineClaims({ email_verified: false }));
    const db = recordingDb();
    const router = createGoogleWebhookRouter({ env: ENV, authClient });

    const response = await post(router, db, { headers: BEARER });

    assert.equal(response.status, 401);
    assert.equal(db.queries.length, 0);
});

test('rejects everything when verification is not configured', async () => {
    // Fail closed: an unset audience or service account must not mean
    // "skip the check", it must mean "the endpoint is off".
    const authClient = stubAuthClient(genuineClaims());
    const db = recordingDb();
    const router = createGoogleWebhookRouter({ env: {}, authClient });

    const response = await post(router, db, { headers: BEARER });

    assert.equal(response.status, 503);
    assert.equal(authClient.calls.length, 0);
    assert.equal(db.queries.length, 0);
});

test('rejects an authenticated notification about a different package', async () => {
    const authClient = stubAuthClient(genuineClaims());
    const db = recordingDb();
    const router = createGoogleWebhookRouter({ env: ENV, authClient });

    const response = await post(router, db, {
        headers: BEARER,
        body: rtdnBody({ packageName: 'com.attacker.app' }),
    });

    assert.equal(response.status, 400);
    assert.equal(db.queries.length, 0);
});

test('applies a verified purchase notification', async () => {
    const authClient = stubAuthClient(genuineClaims());
    const db = recordingDb();
    const router = createGoogleWebhookRouter({ env: ENV, authClient });

    const response = await post(router, db, { headers: BEARER });

    assert.equal(response.status, 200);
    assert.equal(db.queries.length, 1);
    assert.deepEqual(db.queries[0].params, ['active', true, 'purchase-token-1']);
});

test('marks a verified revocation as cancelled', async () => {
    const authClient = stubAuthClient(genuineClaims());
    const db = recordingDb();
    const router = createGoogleWebhookRouter({ env: ENV, authClient });

    const body = rtdnBody({
        subscriptionNotification: {
            version: '1.0',
            notificationType: 12, // SUBSCRIPTION_REVOKED
            purchaseToken: 'purchase-token-1',
            subscriptionId: 'climaai_pro_monthly',
        },
    });
    const response = await post(router, db, { headers: BEARER, body });

    assert.equal(response.status, 200);
    assert.deepEqual(db.queries[0].params, ['cancelled', false, 'purchase-token-1']);
});

test('does not acknowledge a verified notification it could not apply', async () => {
    // 200 would stop Pub/Sub retrying and leave the subscription row stale.
    const authClient = stubAuthClient(genuineClaims());
    const db = recordingDb({ fail: new Error('connection reset') });
    const router = createGoogleWebhookRouter({ env: ENV, authClient });

    const response = await post(router, db, { headers: BEARER });

    assert.equal(response.status, 500);
});
