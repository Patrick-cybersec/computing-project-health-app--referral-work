const express = require('express');
const cors = require('cors');
const bodyParser = require('body-parser');
const fs = require('fs');  // For file I/O

const app = express();
const PORT = 8080;
const DB_FILE = 'users.json';
const RECORDS_FILE = 'activityRecords.json';

// Load users from file (or default)
let users = [];
let activityRecords = [];
let nextRecordId = 1;

function loadData() {
  if (fs.existsSync(DB_FILE)) {
    try {
      users = JSON.parse(fs.readFileSync(DB_FILE, 'utf8'));
      console.log(`Loaded ${users.length} users from ${DB_FILE}`);
    } catch (e) {
      console.error('Error loading users:', e.message);
      users = [];
    }
  } else {
    console.log('No users.json found, using default admin');
    users = [{ id: 'admin', password: 'password', name: 'Admin User', age: 30, sex: 'Male', isAdmin: true }];
    saveUsers();
  }

  if (fs.existsSync(RECORDS_FILE)) {
    try {
      activityRecords = JSON.parse(fs.readFileSync(RECORDS_FILE, 'utf8'));
      if (activityRecords.length > 0) nextRecordId = Math.max(...activityRecords.map(r => r.id)) + 1;
      console.log(`Loaded ${activityRecords.length} records from ${RECORDS_FILE}`);
    } catch (e) {
      console.error('Error loading records:', e.message);
      activityRecords = [];
    }
  }
}

function saveUsers() {
  try {
    fs.writeFileSync(DB_FILE, JSON.stringify(users, null, 2));
    console.log(`Saved ${users.length} users to ${DB_FILE}`);
  } catch (e) {
    console.error('Error saving users:', e.message);
  }
}

function saveRecords() {
  try {
    fs.writeFileSync(RECORDS_FILE, JSON.stringify(activityRecords, null, 2));
    console.log(`Saved ${activityRecords.length} records to ${RECORDS_FILE}`);
  } catch (e) {
    console.error('Error saving records:', e.message);
  }
}

// Load on startup
loadData();

// Auth helpers
function findUser(id, password) {
  return users.find(u => u.id === id && u.password === password);
}
function authenticateAdmin(adminId, adminPassword) {
  const user = findUser(adminId, adminPassword);
  return user && user.isAdmin;
}
function authenticateUser(userId, userPassword) {
  return findUser(userId, userPassword);
}

// Middleware
app.use(cors());
app.use(bodyParser.json());

// Health check
app.get('/api/health', (req, res) => res.json({ status: 'OK' }));

// POST /api/Users/login
app.post('/api/Users/login', (req, res) => {
  const { id, password } = req.body;
  console.log(`Login: ${id}`);
  const user = findUser(id, password);
  if (!user) return res.status(401).json({ error: 'Invalid credentials' });
  res.json({ id: user.id, isAdmin: user.isAdmin });
});

// POST /api/Users/public-register
app.post('/api/Users/public-register', (req, res) => {
  const { Id: id, Password: password, Name: name, Age: age, Sex: sex, IsAdmin: isAdmin = false } = req.body;
  console.log(`Public register: ${id}`);
  if (users.find(u => u.id === id)) return res.status(409).json({ status_code: 409, msg: 'User exists' });
  if (!id || !password || !name) return res.status(400).json({ status_code: 400, msg: 'Missing required fields' });
  const newUser = { id, password, name, age: parseInt(age) || 0, sex, isAdmin: false };
  users.push(newUser);
  saveUsers();
  console.log(`Saved new user: ${id}`);
  res.status(201).json(newUser);
});

// GET /api/Users (admin all; user self)
app.get('/api/Users', (req, res) => {
  const { adminId, adminPassword, requestingUserId, requestingUserPassword } = req.query;
  if (adminId && adminPassword) {
    if (!authenticateAdmin(adminId, adminPassword)) return res.status(401).json({ status_code: 401, msg: 'Unauthorized' });
    return res.json(users.filter(u => !u.isAdmin));
  }
  if (requestingUserId && requestingUserPassword) {
    if (!authenticateUser(requestingUserId, requestingUserPassword)) return res.status(401).json({ status_code: 401, msg: 'Unauthorized' });
    const user = users.find(u => u.id === requestingUserId);
    return res.json([user]);
  }
  res.status(400).json({ status_code: 400, msg: 'Missing auth' });
});

// GET /api/Users/:id
app.get('/api/Users/:id', (req, res) => {
  const { id } = req.params;
  const { requestingUserId, requestingUserPassword } = req.query;
  const user = users.find(u => u.id === id);
  if (!user) return res.status(404).json({ status_code: 404, msg: 'Not Found' });
  if (!authenticateUser(requestingUserId, requestingUserPassword) || (requestingUserId !== id && !users.find(u => u.id === requestingUserId && u.isAdmin))) {
    return res.status(403).json({ status_code: 403, msg: 'Forbidden' });
  }
  res.json(user);
});

// POST /api/Users/register (admin add)
app.post('/api/Users/register', (req, res) => {
  const { AdminId: adminId, AdminPassword: adminPassword, user } = req.body;
  if (!authenticateAdmin(adminId, adminPassword)) return res.status(401).json({ status_code: 401, msg: 'Unauthorized' });
  if (users.find(u => u.id === user.id)) return res.status(409).json({ status_code: 409, msg: 'User exists' });
  if (!user.id || !user.name) return res.status(400).json({ status_code: 400, msg: 'Invalid user data' });
  user.password = user.password || 'defaultpass';
  users.push(user);
  saveUsers();
  res.status(201).json({ msg: 'User created', user });
});

// PUT /api/Users/:id (admin edit)
app.put('/api/Users/:id', (req, res) => {
  const { id } = req.params;
  const { AdminId: adminId, AdminPassword: adminPassword, user } = req.body;
  if (!authenticateAdmin(adminId, adminPassword)) return res.status(401).json({ status_code: 401, msg: 'Unauthorized' });
  const index = users.findIndex(u => u.id === id);
  if (index === -1) return res.status(404).json({ status_code: 404, msg: 'Not Found' });
  users[index] = { ...users[index], ...user };
  saveUsers();
  res.json({ msg: 'User updated', user: users[index] });
});

// POST /api/Users/reset-password (admin)
app.post('/api/Users/reset-password', (req, res) => {
  const { AdminId: adminId, AdminPassword: adminPassword, userId, newPassword } = req.body;
  if (!authenticateAdmin(adminId, adminPassword)) return res.status(401).json({ status_code: 401, msg: 'Unauthorized' });
  const user = users.find(u => u.id === userId);
  if (!user) return res.status(404).json({ status_code: 404, msg: 'User Not Found' });
  user.password = newPassword;
  saveUsers();
  res.json({ msg: 'Password reset' });
});

// DELETE /api/Users/:id (admin)
app.delete('/api/Users/:id', (req, res) => {
  const { id } = req.params;
  const { AdminId: adminId, AdminPassword: adminPassword } = req.body;
  if (!authenticateAdmin(adminId, adminPassword)) return res.status(401).json({ status_code: 401, msg: 'Unauthorized' });
  const index = users.findIndex(u => u.id === id);
  if (index === -1) return res.status(404).json({ status_code: 404, msg: 'Not Found' });
  users.splice(index, 1);
  saveUsers();
  res.json({ msg: 'User deleted' });
});

// GET /api/Users/stars (leaderboard)
app.get('/api/Users/stars', (req, res) => {
  const userStars = users
    .map(user => ({
      username: user.name,
      starCount: new Set(activityRecords.filter(r => r.userId === user.id).map(r => r.activityType)).size
    }))
    .filter(s => s.starCount > 0)
    .sort((a, b) => b.starCount - a.starCount);
  res.json(userStars);
});

// GET /api/ActivityRecords (admin all; user self)
app.get('/api/ActivityRecords', (req, res) => {
  const { adminId, adminPassword, requestingUserId, requestingUserPassword, user: userId } = req.query;
  let records = [];
  const targetUserId = userId || requestingUserId;
  if (adminId && adminPassword) {
    if (!authenticateAdmin(adminId, adminPassword)) return res.status(401).json({ status_code: 401, msg: 'Unauthorized' });
    records = activityRecords;
  } else if (targetUserId && requestingUserPassword) {
    if (!authenticateUser(targetUserId, requestingUserPassword)) return res.status(401).json({ status_code: 401, msg: 'Unauthorized' });
    records = activityRecords.filter(r => r.userId === targetUserId);
  } else {
    return res.status(400).json({ status_code: 400, msg: 'Missing auth params' });
  }
  res.json(records);
});

// GET /api/ActivityRecords/user/:id (for Awards/Billboard)
app.get('/api/ActivityRecords/user/:id', (req, res) => {
  const { id } = req.params;
  const { requestingUserId, requestingUserPassword } = req.query;
  if (!authenticateUser(id, requestingUserPassword) || id !== requestingUserId) {
    return res.status(401).json({ status_code: 401, msg: 'Unauthorized' });
  }
  const records = activityRecords.filter(r => r.userId === id);
  res.json(records);
});

// GET /api/ActivityRecords/:id
app.get('/api/ActivityRecords/:id', (req, res) => {
  const { id } = req.params;
  const { requestingUserId, requestingUserPassword } = req.query;
  const record = activityRecords.find(r => r.id === parseInt(id));
  if (!record) return res.status(404).json({ status_code: 404, msg: 'Not Found' });
  if (!authenticateUser(requestingUserId, requestingUserPassword) || (requestingUserId !== record.userId && !users.find(u => u.id === requestingUserId && u.isAdmin))) {
    return res.status(403).json({ status_code: 403, msg: 'Forbidden' });
  }
  res.json(record);
});

// POST /api/ActivityRecords (user self-add or admin)
app.post('/api/ActivityRecords', (req, res) => {
  const { AdminId: adminId, AdminPassword: adminPassword, Record: record, userId, userPassword } = req.body;
  const targetUserId = record.UserId || record.userId || userId;
  if (adminId && adminPassword) {
    if (!authenticateAdmin(adminId, adminPassword)) return res.status(401).json({ status_code: 401, msg: 'Unauthorized' });
  } else if (userId && userPassword) {
    if (!authenticateUser(userId, userPassword) || userId !== targetUserId) return res.status(401).json({ status_code: 401, msg: 'Unauthorized for self-add' });
  } else {
    return res.status(400).json({ status_code: 400, msg: 'Missing auth' });
  }
  if (!targetUserId || !record.ActivityType && !record.activityType) return res.status(400).json({ status_code: 400, msg: 'Invalid record data' });
  const newRecord = {
    ...record,
    id: nextRecordId++,
    created_At: new Date().toISOString(),
    userId: targetUserId,
    activityType: record.ActivityType || record.activityType,
    mood: record.Mood || record.mood
  };
  activityRecords.push(newRecord);
  saveRecords();
  res.status(201).json(newRecord);
});

// PUT /api/ActivityRecords/:id (admin or self)
app.put('/api/ActivityRecords/:id', (req, res) => {
  const { id } = req.params;
  const { AdminId: adminId, AdminPassword: adminPassword, Record: record, userId, userPassword } = req.body;
  const targetUserId = record.UserId || record.userId || userId;
  if (adminId && adminPassword) {
    if (!authenticateAdmin(adminId, adminPassword)) return res.status(401).json({ status_code: 401, msg: 'Unauthorized' });
  } else if (userId && userPassword) {
    if (!authenticateUser(userId, userPassword) || userId !== targetUserId) return res.status(401).json({ status_code: 401, msg: 'Unauthorized for self-edit' });
  } else {
    return res.status(400).json({ status_code: 400, msg: 'Missing auth' });
  }
  const index = activityRecords.findIndex(r => r.id === parseInt(id));
  if (index === -1) return res.status(404).json({ status_code: 404, msg: 'Not Found' });
  activityRecords[index] = { ...activityRecords[index], ...record };
  saveRecords();
  res.json(activityRecords[index]);
});

// DELETE /api/ActivityRecords/:id (admin or self)
app.delete('/api/ActivityRecords/:id', (req, res) => {
  const { id } = req.params;
  const { AdminId: adminId, AdminPassword: adminPassword, adminId: adminIdLower, adminPassword: adminPasswordLower, userId, userPassword } = req.body;
  const record = activityRecords.find(r => r.id === parseInt(id));
  if (!record) return res.status(404).json({ status_code: 404, msg: 'Not Found' });
  const effectiveAdminId = adminId || adminIdLower;
  const effectiveAdminPassword = adminPassword || adminPasswordLower;
  if (effectiveAdminId && effectiveAdminPassword) {
    if (!authenticateAdmin(effectiveAdminId, effectiveAdminPassword)) return res.status(401).json({ status_code: 401, msg: 'Unauthorized' });
  } else if (userId && userPassword) {
    if (!authenticateUser(userId, userPassword) || userId !== record.userId) return res.status(401).json({ status_code: 401, msg: 'Unauthorized for self-delete' });
  } else {
    return res.status(400).json({ status_code: 400, msg: 'Missing auth' });
  }
  const index = activityRecords.findIndex(r => r.id === parseInt(id));
  activityRecords.splice(index, 1);
  saveRecords();
  res.json({ msg: 'Record deleted' });
});

// 404 Catch-all
app.use((req, res) => {
  console.log(`${new Date().toISOString()} - 404: ${req.method} ${req.originalUrl}`);
  res.status(404).json({ status_code: 404, msg: 'Not Found', details: { path: req.path } });
});

// Save on shutdown
process.on('SIGINT', () => {
  saveUsers();
  saveRecords();
  process.exit();
});

app.listen(PORT, () => console.log(`Server on http://localhost:${PORT}`));