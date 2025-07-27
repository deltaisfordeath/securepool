import express from 'express';
import cors from 'cors';

const app = express();
app.use(cors());
app.use(express.json());

app.use((req, res, next) => {
  console.log(`HTTP request received: ${req.method} ${req.url} ${JSON.stringify(req.body)}`);
  next();
});

app.get('/', (req, res) => {
  res.json({ message: 'HTTP test server is running', timestamp: new Date().toISOString() });
});

app.get('/api/test', (req, res) => {
  res.json({ 
    message: 'HTTP connectivity test successful!', 
    timestamp: new Date().toISOString()
  });
});

app.post('/api/register', (req, res) => {
  console.log('HTTP Registration request received:', req.body);
  res.json({
    success: true,
    message: 'HTTP registration test successful',
    timestamp: new Date().toISOString()
  });
});

const PORT = 8080;
app.listen(PORT, '0.0.0.0', () => {
  console.log(`HTTP test server running on port ${PORT}`);
  console.log(`Test URL: http://localhost:${PORT}/api/test`);
  console.log(`Emulator URL: http://10.0.2.2:${PORT}/api/test`);
});
