
export async function logAudit(username, action, req, db) {
  try {
    const ip = req.headers['x-forwarded-for'] || req.connection.remoteAddress || 'unknown';
    const userAgent = req.headers['user-agent'] || 'unknown';

    const sql = `
      INSERT INTO audit_logs (username, action, ip_address, user_agent, timestamp)
      VALUES (?, ?, ?, ?, NOW())
    `;
    const params = [username, action, ip, userAgent];

    const [result] = await db.query(sql, params);
    console.log('✅ Audit log inserted:', result);
  } catch (error) {
    console.error('❌ Error inserting audit log:', error);
  }
}
