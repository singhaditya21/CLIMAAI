const request = require('supertest');

// Mock dependencies
jest.mock('pg', () => {
  const mPool = {
    connect: jest.fn(),
    query: jest.fn().mockResolvedValue({ rows: [] }),
    end: jest.fn(),
  };
  return { Pool: jest.fn(() => mPool) };
});

const app = require('../src/index');

describe('Google Webhook API', () => {

    it('should handle valid PURCHASED event', async () => {
        const payload = {
            message: {
                data: Buffer.from(JSON.stringify({
                    subscriptionNotification: {
                        notificationType: 4, // SUBSCRIPTION_PURCHASED
                        purchaseToken: 'test-token',
                        subscriptionId: 'monthly'
                    },
                    packageName: 'com.climaai.app'
                })).toString('base64')
            }
        };

        const res = await request(app)
            .post('/webhooks/google')
            .send(payload);

        // Assuming implementation returns 200 even for mocks
        expect(res.statusCode).toBe(200);
    });

    it('should return 400 for invalid payload', async () => {
        const res = await request(app)
            .post('/webhooks/google')
            .send({});

        expect(res.statusCode).toBe(400);
    });
});
