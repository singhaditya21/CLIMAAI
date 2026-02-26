const request = require('supertest');
const jwt = require('jsonwebtoken');

// Mock PostgreSQL pool
jest.mock('pg', () => {
  const mPool = {
    connect: jest.fn(),
    query: jest.fn().mockResolvedValue({ rows: [] }),
    end: jest.fn(),
  };
  return { Pool: jest.fn(() => mPool) };
});

jest.mock('jsonwebtoken', () => ({
  decode: jest.fn(),
}));

const app = require('../src/index');

describe('Payment Service API', () => {

    describe('GET /health', () => {
        it('should return 200 and healthy status', async () => {
            const res = await request(app).get('/health');
            expect(res.statusCode).toEqual(200);
            expect(res.body).toHaveProperty('status', 'healthy');
        });
    });

    describe('POST /webhooks/apple', () => {
        it('should handle valid SUBSCRIBED event', async () => {
            const fakePayload = {
                notificationType: 'SUBSCRIBED',
                data: {
                    transactionInfo: {
                        originalTransactionId: '10000001'
                    }
                }
            };

            jwt.decode.mockReturnValue({ payload: fakePayload });

            const res = await request(app)
                .post('/webhooks/apple')
                .send({ signedPayload: 'fake_jwt_string' });

            expect(res.statusCode).toEqual(200);
            expect(res.body.status).toEqual('received');
        });

        it('should return 400 if signedPayload is missing', async () => {
            const res = await request(app)
                .post('/webhooks/apple')
                .send({});

            expect(res.statusCode).toEqual(400);
            expect(res.body.error).toBe('Missing signedPayload');
        });

        it('should return 400 if signature invalid', async () => {
            jwt.decode.mockReturnValue(null);

            const res = await request(app)
                .post('/webhooks/apple')
                .send({ signedPayload: 'bad_jwt' });

            expect(res.statusCode).toEqual(400);
            expect(res.body.error).toBe('Invalid signature');
        });
    });
});
