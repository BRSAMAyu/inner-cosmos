#!/bin/bash

# Start server in background
cd "C:\inner cosmos"
export JAVA_HOME="/c/Program Files/Java/jdk-21.0.10"
export M2_HOME="$HOME/.m2"

# Start server in background
"$JAVA_HOME/bin/java.exe" -jar "C:\inner cosmos\tag\classes" -jar target/lib/* spring-boot:run > /dev/null 2>&1 &
SPRING_PID=$!
echo "Server starting with PID: $SPRING_PID"

# Wait for server to start
echo "Waiting for server to start..."
for i in {1..30}; do
    if curl -s http://localhost:8080/actuator/health > /dev/null 2>&1; then
        echo "Server is up!"
        break
    fi
    sleep 1
done

# Test login
echo "Testing login..."
LOGIN_RESPONSE=$(curl -s -X POST "http://localhost:8080/api/auth/login" \
  -H "Content-Type: application/json" \
  -d '{"username":"demo","password":"demo123"}')

echo "Login response: $LOGIN_RESPONSE"

# Extract session cookie more reliably
SESSION_COOKIE=$(echo "$LOGIN_RESPONSE" | grep -o 'Set-Cookie: JSESSIONID=[^;]*' | sed 's/Set-Cookie: JSESSIONID=//' | sed 's/;.*//')

echo "Session cookie: $SESSION_COOKIE"

# Test plaza capsules
echo "Testing plaza capsules..."
PLAZA_RESPONSE=$(curl -s -X GET "http://localhost:8080/api/plaza/capsules" \
  -H "Cookie: JSESSIONID=$SESSION_COOKIE")

echo "Plaza response: $PLAZA_RESPONSE"

# Test letter draft with English content
echo "Testing letter draft..."
LETTER_RESPONSE=$(curl -s -X POST "http://localhost:8080/api/letters/draft" \
  -H "Content-Type: application/json" \
  -H "Cookie: JSESSIONID=$SESSION_COOKIE" \
  -d '{"receiverUserId":1,"receiverCapsuleId":1,"title":"Test","letterBody":"Test content"}')

echo "Letter draft response: $LETTER_RESPONSE"

# Extract letter ID if response is JSON
LETTER_ID=$(echo "$LETTER_RESPONSE" | grep -o '"id":[0-9]*' | sed 's/"id"://' | head -1)

echo "Letter ID: $LETTER_ID"

if [ ! -z "$LETTER_ID" ] && [ "$LETTER_ID" != "" ]; then
    # Test letter send
    echo "Testing letter send..."
    SEND_RESPONSE=$(curl -s -X POST "http://localhost:8080/api/letters/$LETTER_ID/send" \
      -H "Cookie: JSESSIONID=$SESSION_COOKIE")

    echo "Letter send response: $SEND_RESPONSE"
else
    echo "Failed to get letter ID"
fi

# Cleanup
kill $SPRING_PID 2>/dev/null
echo "Server stopped"
