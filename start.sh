#!/bin/bash

# ClimaAI Quick Start Script
# Starts the entire backend stack with one command

set -e

echo "🌤️  ClimaAI Quick Start"
echo "====================="
echo ""

# Check for required commands
command -v docker >/dev/null 2>&1 || { echo "❌ Docker is required but not installed. Aborting." >&2; exit 1; }
command -v docker-compose >/dev/null 2>&1 || { echo "❌ Docker Compose is required but not installed. Aborting." >&2; exit 1; }

cd "$(dirname "$0")/backend"

# Check if .env files exist
if [ ! -f "api/.env" ]; then
    echo "📝 Creating API .env file..."
    cp api/.env.example api/.env
    echo "⚠️  Please edit backend/api/.env with your OpenAI API key"
fi

if [ ! -f "payment-service/.env" ]; then
    echo "📝 Creating payment service .env file..."
    cp payment-service/.env.example payment-service/.env
fi

echo ""
echo "🚀 Starting ClimaAI backend services..."
echo ""

# Start services
docker-compose up -d

echo ""
echo "⏳ Waiting for services to be ready..."
sleep 10

# Check service health
echo ""
echo "🏥 Health checks:"
echo ""

API_HEALTH=$(curl -s http://localhost:8000/health | grep -o '"status":"healthy"' || echo "")
if [ -n "$API_HEALTH" ]; then
    echo "✅ API Service: Healthy (http://localhost:8000)"
else
    echo "❌ API Service: Not responding"
fi

PAYMENT_HEALTH=$(curl -s http://localhost:3000/health | grep -o '"status":"healthy"' || echo "")
if [ -n "$PAYMENT_HEALTH" ]; then
    echo "✅ Payment Service: Healthy (http://localhost:3000)"
else
    echo "❌ Payment Service: Not responding"
fi

# Check database
DB_STATUS=$(docker-compose exec -T postgres pg_isready -U climaai 2>&1 | grep -o "accepting connections" || echo "")
if [ -n "$DB_STATUS" ]; then
    echo "✅ PostgreSQL: Accepting connections"
else
    echo "❌ PostgreSQL: Not ready"
fi

# Check Redis
REDIS_STATUS=$(docker-compose exec -T redis redis-cli ping 2>&1 | grep -o "PONG" || echo "")
if [ -n "$REDIS_STATUS" ]; then
    echo "✅ Redis: Running"
else
    echo "❌ Redis: Not responding"
fi

echo ""
echo "📋 Service URLs:"
echo ""
echo "   API Documentation: http://localhost:8000/docs"
echo "   API ReDoc:         http://localhost:8000/redoc"
echo "   Health Check:      http://localhost:8000/health"
echo "   Payment Webhooks:  http://localhost:3000/health"
echo ""
echo "🔧 Management:"
echo ""
echo "   View logs:    docker-compose logs -f"
echo "   Stop all:     docker-compose down"
echo "   Restart:      docker-compose restart"
echo ""
echo "👤 Demo Account:"
echo ""
echo "   Email:    demo@climaai.com"
echo "   Password: Test1234"
echo ""
echo "✨ ClimaAI backend is ready!"
echo ""
echo "📱 Next steps:"
echo "   1. Open mobile/ios/ClimaAI.xcodeproj in Xcode (iOS)"
echo "   2. Open mobile/android in Android Studio (Android)"
echo "   3. Update API base URL in mobile apps to http://localhost:8000"
echo "   4. Run the app on simulator/emulator"
echo ""
echo "📚 Documentation:"
echo "   - README.md"
echo "   - docs/API.md"
echo "   - docs/DEPLOYMENT.md"
echo ""
