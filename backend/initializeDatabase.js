import { createConnection } from 'mysql2/promise';
import bcrypt from 'bcryptjs';

// TODO: update table to allow for biometric registration
const createUserTableQuery = `
    CREATE TABLE users (
        username VARCHAR(100) PRIMARY KEY,
        password VARCHAR(100),
        score INT DEFAULT 100,
        lastZeroTimestamp DATETIME NULL DEFAULT NULL,
        publicKey VARCHAR(255) NULL DEFAULT NULL
    );
`;

const checkColumnQuery = `
      SELECT * FROM INFORMATION_SCHEMA.COLUMNS
      WHERE TABLE_SCHEMA = ? 
      AND TABLE_NAME = 'users' 
      AND COLUMN_NAME = 'publicKey'
    `;

const checkLoginColumnQuery = `
      SELECT * FROM INFORMATION_SCHEMA.COLUMNS
      WHERE TABLE_SCHEMA = ? 
      AND TABLE_NAME = 'users' 
      AND COLUMN_NAME = 'failedLoginAttempts'
    `;

const createChallengeTableQuery = `
    CREATE TABLE challenges (
        username VARCHAR(100) PRIMARY KEY,
        challenge VARCHAR(255),
        expiration DATETIME
    );
`;

let salt = await bcrypt.genSalt(10);
const gamer1pw = await bcrypt.hash('a123', salt);
const gamer2pw = await bcrypt.hash('b123', salt);
const gamer3pw = await bcrypt.hash('c123', salt);
const gamer4pw = await bcrypt.hash('d123', salt);
const gamer5pw = await bcrypt.hash('e123', salt);

const seedUsersQuery = `
    INSERT INTO users (username, password, score) VALUES
    ('gamerA', '${gamer1pw}', 100),
    ('gamerB', '${gamer2pw}', 100),
    ('gamerC', '${gamer3pw}', 100),
    ('gamerD', '${gamer4pw}', 100),
    ('gamerE', '${gamer5pw}', 100);
`;

/**
 * Initializes the database by checking for the 'users' table and seeding it if empty.
 */
export default async function initializeDatabase() {
    // Read environment variables at runtime
    const database_name = process.env.DB_NAME;
    const connectionProperties = {
        host: process.env.DB_HOST,
        port: process.env.DB_PORT,
        user: process.env.DB_USER,
        password: process.env.DB_PASSWORD,
    };

    // Debug logging
    console.log('Database connection properties:', {
        host: connectionProperties.host,
        port: connectionProperties.port,
        user: connectionProperties.user,
        password: connectionProperties.password ? '[SET]' : '[NOT SET]',
        database_name: database_name
    });

    let connection;
    try {
        // Establish a connection to the database
        connection = await createConnection(connectionProperties);
        console.log('Connected to MySQL database.');

        await connection.execute(`CREATE DATABASE IF NOT EXISTS ${database_name}`);

        await connection.query(`USE \`${database_name}\`;`);

        await connection.connect();

        const [rows] = await connection.execute(
            `SHOW TABLES LIKE 'users';`
        );

        if (rows.length === 0) {
            console.log('Table "users" does not exist. Creating table...');
            connection.execute(createUserTableQuery);
            console.log('Table "users" created successfully.');

            console.log('Seeding "users" table with initial data...');
            connection.execute(seedUsersQuery);
            console.log('Users seeded successfully.');
        } else {
            console.log('Table "users" already exists.');

            const [rows] = await connection.execute(checkColumnQuery, [database_name]);

            if (rows.length === 0) {
                console.log("Column 'publicKey' does not exist. Adding it now... 🏃");
                const addColumnQuery = `
                    ALTER TABLE users 
                    ADD COLUMN publicKey VARCHAR(255) NULL DEFAULT NULL
                `;
                await connection.query(addColumnQuery);
                console.log("Column 'publicKey' added successfully! ✨");
            } else {
                console.log("Column 'publicKey' already exists. No action taken. 👍");
            }

            const [loginRows] = await connection.execute(checkLoginColumnQuery, [database_name]);

            if (loginRows.length === 0) {
                console.log("Column 'failedLoginAttempts' does not exist. Adding it now... 🏃");
                const addColumnQuery = `
                    ALTER TABLE users 
                    ADD COLUMN failedLoginAttempts INT DEFAULT 0
                `;
                await connection.query(addColumnQuery);
                const fillColumnQuery = `UPDATE users SET failedLoginAttempts = 0`
                await connection.query(fillColumnQuery);
                console.log("Column 'failedLoginAttempts' added successfully! ✨");
            } else {
                console.log("Column 'failedLoginAttempts' already exists. No action taken. 👍");
            }

            const [userCountRows] = await connection.execute(
                `SELECT COUNT(*) AS count FROM users;`
            );
            const userCount = userCountRows[0].count;

            if (userCount === 0) {
                // Table exists but is empty, so seed it
                console.log('Table "users" is empty. Seeding with initial data...');
                connection.execute(seedUsersQuery);
                console.log('Users seeded successfully.');
            } else {
                console.log(`Table "users" already contains ${userCount} users. No seeding required.`);
            }
        }
        const [challengeRows] = await connection.execute(
            `SHOW TABLES LIKE 'challenges';`
        );

        if (challengeRows.length === 0) {
            console.log('Table "challenges" does not exist. Creating table...');
            connection.execute(createChallengeTableQuery);
            console.log('Table "challenges" created successfully.');

        } else {
            console.log('Table "challenges" already exists.');
        }

        return connection;
    } catch (error) {
        console.error('Database initialization failed:', error);
        if (connection) {
            connection.end();
            console.log('Database connection closed.');
        }
    }
}