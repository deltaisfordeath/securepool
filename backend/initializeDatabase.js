import dotenv from 'dotenv';
dotenv.config();

import { createConnection } from 'mysql2/promise';
import bcrypt from 'bcryptjs';

const database_name = process.env.DB_NAME;

const connectionProperties = {
  host: process.env.DB_HOST,
  port: process.env.DB_PORT,
  user: process.env.DB_USER,
  password: process.env.DB_PASSWORD,
  multipleStatements: true
};

// ─── Table Queries ───────────────────────────────────────────────

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
    AND COLUMN_NAME = 'publicKey';
`;

const createChallengeTableQuery = `
  CREATE TABLE challenges (
    username VARCHAR(100) PRIMARY KEY,
    challenge VARCHAR(255),
    expiration DATETIME
  );
`;

const createAuditLogsTableQuery = `
  CREATE TABLE audit_logs (
    id INT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(100),
    action VARCHAR(255),
    ip_address VARCHAR(45),
    user_agent TEXT,
    timestamp DATETIME DEFAULT CURRENT_TIMESTAMP
  );
`;

// ─── Initialization Function ─────────────────────────────────────

async function initializeDatabase() {
  try {
    const connection = await createConnection(connectionProperties);
    console.log('✅ Connected to MySQL server.');

    await connection.execute(`CREATE DATABASE IF NOT EXISTS \`${database_name}\``);
    await connection.query(`USE \`${database_name}\``);

    // Seed users
    const salt = await bcrypt.genSalt(10);
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

    // USERS TABLE
    const [userTable] = await connection.execute(`SHOW TABLES LIKE 'users';`);
    if (userTable.length === 0) {
      console.log('🆕 Creating table "users"...');
      await connection.execute(createUserTableQuery);
      console.log('✅ Table "users" created.');

      console.log('🌱 Seeding "users" table...');
      await connection.execute(seedUsersQuery);
      console.log('✅ Seeding complete.');
    } else {
      console.log('✅ Table "users" already exists.');

      const [columnCheck] = await connection.execute(checkColumnQuery, [database_name]);
      if (columnCheck.length === 0) {
        console.log("➕ Adding missing column 'publicKey'...");
        await connection.query(`
          ALTER TABLE users
          ADD COLUMN publicKey VARCHAR(255) NULL DEFAULT NULL;
        `);
        console.log("✅ Column 'publicKey' added.");
      } else {
        console.log("✅ Column 'publicKey' already exists.");
      }

      const [userCountRows] = await connection.execute(`SELECT COUNT(*) AS count FROM users;`);
      const userCount = userCountRows[0].count;
      if (userCount === 0) {
        console.log('🌱 Seeding empty "users" table...');
        await connection.execute(seedUsersQuery);
        console.log('✅ Seeding complete.');
      } else {
        console.log(`ℹ️ "users" table already has ${userCount} user(s).`);
      }
    }

    // CHALLENGES TABLE
    const [challengeRows] = await connection.execute(`SHOW TABLES LIKE 'challenges';`);
    if (challengeRows.length === 0) {
      console.log('🆕 Creating table "challenges"...');
      await connection.execute(createChallengeTableQuery);
      console.log('✅ Table "challenges" created.');
    } else {
      console.log('✅ Table "challenges" already exists.');
    }

    // AUDIT LOGS TABLE
    const [auditLogRows] = await connection.execute(`SHOW TABLES LIKE 'audit_logs';`);
    if (auditLogRows.length === 0) {
      console.log('🆕 Creating table "audit_logs"...');
      await connection.execute(createAuditLogsTableQuery);
      console.log('✅ Table "audit_logs" created.');
    } else {
      console.log('✅ Table "audit_logs" already exists.');
    }

    return connection;
  } catch (error) {
    console.error('❌ Database initialization failed:', error.message);
    return null;
  }
}

// ─── Export Connection ──────────────────────────────────────────

const db = await initializeDatabase();
export default db;
