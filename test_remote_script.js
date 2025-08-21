// Test JavaScript file for remote execution in QuickJS
// You can host this file on any web server and use its URL in the app

console.log("Starting remote JavaScript execution test...");

// Test 1: Basic JavaScript functionality
const greeting = "Hello from Remote JavaScript!";
const timestamp = new Date().toISOString();

// Test 2: Modern JavaScript features (ES2023)
const numbers = [1, 2, 3, 4, 5];
const doubled = numbers.map(n => n * 2);
const sum = numbers.reduce((acc, n) => acc + n, 0);

// Test 3: Object and JSON handling
const testResult = {
    message: greeting,
    timestamp: timestamp,
    engine: "QuickJS",
    version: "2025-04-26",
    tests: {
        basicArithmetic: 2 + 3 * 4,
        arrayOperations: doubled,
        arraySum: sum,
        stringTemplate: `Sum of ${numbers.join(', ')} = ${sum}`,
        objectDestructuring: (() => {
            const {length} = numbers;
            return `Array has ${length} elements`;
        })()
    },
    features: [
        "Arrow Functions",
        "Template Literals", 
        "Destructuring",
        "Array Methods",
        "JSON Support"
    ],
    status: "Remote execution successful!",
    deviceInfo: {
        userAgent: typeof navigator !== 'undefined' ? navigator.userAgent : 'QuickJS Engine',
        platform: 'Android'
    }
};

// Test 4: Function definitions and calls
function calculateFactorial(n) {
    if (n <= 1) return 1;
    return n * calculateFactorial(n - 1);
}

testResult.tests.factorial5 = calculateFactorial(5);

// Test 5: Advanced JavaScript features
try {
    // BigInt test (if supported)
    const bigNumber = BigInt(123456789012345678901234567890n);
    testResult.tests.bigInt = bigNumber.toString();
} catch (e) {
    testResult.tests.bigInt = "BigInt not supported: " + e.message;
}

// Test 6: Math operations
testResult.tests.mathOperations = {
    sqrt16: Math.sqrt(16),
    pow2_8: Math.pow(2, 8),
    random: Math.random(),
    pi: Math.PI,
    e: Math.E
};

// Test 7: String operations
const testString = "QuickJS Remote Execution";
testResult.tests.stringOperations = {
    uppercase: testString.toUpperCase(),
    lowercase: testString.toLowerCase(),
    length: testString.length,
    reversed: testString.split('').reverse().join(''),
    words: testString.split(' ')
};

// Final result - return as JSON string
const finalResult = JSON.stringify(testResult, null, 2);

console.log("Remote JavaScript test completed successfully!");
console.log(finalResult);

// Return the result (this will be captured by QuickJS)
finalResult;
