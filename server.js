const express = require('express');
const cors = require('cors');
const bodyParser = require('body-parser');
const fs = require('fs');

const app = express();
const PORT = 8080;
const DB_FILE = 'users.json';
const RECORDS_FILE = 'activityRecords.json';

// Load users from file (or default)
let users = [];
let activityRecords = [];
let nextRecordId = 1;

function loadData() {
  console.log('Loading data from files...');
  if (fs.existsSync(DB_FILE)) {
    try {
      const data = fs.readFileSync(DB_FILE, 'utf8');
      console.log('Raw users data:', data.substring(0, 200) + '...');
      users = JSON.parse(data);
      console.log(`Loaded ${users.length} users:`, users.map(u => u.id));
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
      const data = fs.readFileSync(RECORDS_FILE, 'utf8');
      console.log('Raw records data:', data.substring(0, 200) + '...');
      activityRecords = JSON.parse(data);
      if (activityRecords.length > 0) nextRecordId = Math.max(...activityRecords.map(r => r.id || 0)) + 1;
      console.log(`Loaded ${activityRecords.length} records`);
    } catch (e) {
      console.error('Error loading records:', e.message);
      activityRecords = [];
    }
  } else {
    console.log('No activityRecords.json found, starting empty');
    nextRecordId = 1;
  }
}

function saveUsers() {
  console.log('Saving users...');
  try {
    fs.writeFileSync(DB_FILE, JSON.stringify(users, null, 2));
    console.log(`Saved ${users.length} users:`, users.map(u => u.id));
  } catch (e) {
    console.error('Error saving users:', e.message);
  }
}

function saveRecords() {
  console.log('Saving records...');
  try {
    fs.writeFileSync(RECORDS_FILE, JSON.stringify(activityRecords, null, 2));
    console.log(`Saved ${activityRecords.length} records`);
  } catch (e) {
    console.error('Error saving records:', e.message);
  }
}

// Load on startup
loadData();

// Auth helpers
function findUser(id, password) {
  const user = users.find(u => u.id === id && u.password === password);
  return user;
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

// === BILLBOARD: SHOW ALL USERS (even 0 stars) ===
app.get('/api/Users/stars', (req, res) => {
  console.log('HIT: /api/Users/stars');
  try {
    const userStars = users
      .map(user => {
        const userRecords = activityRecords.filter(r => 
          (r.userId || r.UserId) === user.id
        );
        const uniqueActivities = new Set(
          userRecords
            .map(r => r.activityType || r.ActivityType)
            .filter(Boolean)
        );
        return {
          username: user.name || user.id,
          starCount: uniqueActivities.size
        };
      })
      .sort((a, b) => b.starCount - a.starCount); // Sort by stars descending

    console.log('Returning stars:', userStars);
    res.json(userStars);
  } catch (e) {
    console.error('Stars error:', e);
    res.status(500).json({ error: 'Internal server error' });
  }
});

// Health check
app.get('/api/health', (req, res) => {
  console.log('HIT: /api/health');
  res.json({ status: 'OK', time: new Date().toISOString() });
});

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
  const { Id: id, Password: password, Name: name, Age: age, Sex: sex } = req.body;
  console.log(`Public register: ${id}`);
  if (users.find(u => u.id === id)) return res.status(409).json({ status_code: 409, msg: 'User exists' });
  if (!id || !password || !name) return res.status(400).json({ status_code: 400, msg: 'Missing required fields' });
  const newUser = { id, password, name, age: parseInt(age) || 0, sex, isAdmin: false };
  users.push(newUser);
  saveUsers();
  console.log(`Saved new user: ${id}`);
  res.status(201).json(newUser);
});

// GET /api/Users
app.get('/api/Users', (req, res) => {
  const { adminId, adminPassword, requestingUserId, requestingUserPassword } = req.query;
  if (adminId && adminPassword) {
    if (!authenticateAdmin(adminId, adminPassword)) return res.status(401).json({ status_code: 401, msg: 'Unauthorized' });
    return res.json(users);
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

// POST /api/Users/register
app.post('/api/Users/register', (req, res) => {
  const { AdminId: adminId, AdminPassword: adminPassword, adminId: adminIdLower, adminPassword: adminPasswordLower, user } = req.body;
  const effectiveAdminId = adminId || adminIdLower;
  const effectiveAdminPassword = adminPassword || adminPasswordLower;
  if (!authenticateAdmin(effectiveAdminId, effectiveAdminPassword)) return res.status(401).json({ status_code: 401, msg: 'Unauthorized' });
  if (users.find(u => u.id === user.id)) return res.status(409).json({ status_code: 409, msg: 'User exists' });
  if (!user.id || !user.name) return res.status(400).json({ status_code: 400, msg: 'Invalid user data' });
  user.password = user.password || 'defaultpass';
  users.push(user);
  saveUsers();
  res.status(201).json({ msg: 'User created', user });
});

// PUT /api/Users/:id
app.put('/api/Users/:id', (req, res) => {
  const { id } = req.params;
  const { AdminId: adminId, AdminPassword: adminPassword, adminId: adminIdLower, adminPassword: adminPasswordLower, user } = req.body;
  const effectiveAdminId = adminId || adminIdLower;
  const effectiveAdminPassword = adminPassword || adminPasswordLower;
  if (!authenticateAdmin(effectiveAdminId, effectiveAdminPassword)) return res.status(401).json({ status_code: 401, msg: 'Unauthorized' });
  const index = users.findIndex(u => u.id === id);
  if (index === -1) return res.status(404).json({ status_code: 404, msg: 'Not Found' });
  users[index] = { ...users[index], ...user };
  saveUsers();
  res.json({ msg: 'User updated', user: users[index] });
});

// POST /api/Users/reset-password
app.post('/api/Users/reset-password', (req, res) => {
  const { AdminId: adminId, AdminPassword: adminPassword, adminId: adminIdLower, adminPassword: adminPasswordLower, userId, newPassword } = req.body;
  const effectiveAdminId = adminId || adminIdLower;
  const effectiveAdminPassword = adminPassword || adminPasswordLower;
  if (!authenticateAdmin(effectiveAdminId, effectiveAdminPassword)) return res.status(401).json({ status_code: 401, msg: 'Unauthorized' });
  const user = users.find(u => u.id === userId);
  if (!user) return res.status(404).json({ status_code: 404, msg: 'User Not Found' });
  user.password = newPassword;
  saveUsers();
  res.json({ msg: 'Password reset' });
});

// DELETE /api/Users/:id
app.delete('/api/Users/:id', (req, res) => {
  const { id } = req.params;
  const { AdminId: adminId, AdminPassword: adminPassword, adminId: adminIdLower, adminPassword: adminPasswordLower } = req.body;
  const effectiveAdminId = adminId || adminIdLower;
  const effectiveAdminPassword = adminPassword || adminPasswordLower;
  if (!authenticateAdmin(effectiveAdminId, effectiveAdminPassword)) return res.status(401).json({ status_code: 401, msg: 'Unauthorized' });
  const index = users.findIndex(u => u.id === id);
  if (index === -1) return res.status(404).json({ status_code: 404, msg: 'Not Found' });
  users.splice(index, 1);
  saveUsers();
  res.json({ msg: 'User deleted' });
});

// GET /api/ActivityRecords
app.get('/api/ActivityRecords', (req, res) => {
  const { adminId, adminPassword, requestingUserId, requestingUserPassword, user: userId } = req.query;
  let records = [];
  const targetUserId = userId || requestingUserId;
  if (adminId && adminPassword) {
    if (!authenticateAdmin(adminId, adminPassword)) return res.status(401).json({ status_code: 401, msg: 'Unauthorized' });
    records = activityRecords;
  } else if (targetUserId && requestingUserPassword) {
    if (!authenticateUser(targetUserId, requestingUserPassword)) return res.status(401).json({ status_code: 401, msg: 'Unauthorized' });
    records = activityRecords.filter(r => (r.userId || r.UserId) === targetUserId);
  } else {
    return res.status(400).json({ status_code: 400, msg: 'Missing auth' });
  }
  res.json(records);
});

// GET /api/ActivityRecords/user/:id
app.get('/api/ActivityRecords/user/:id', (req, res) => {
  const { id } = req.params;
  const { requestingUserId, requestingUserPassword } = req.query;
  if (!authenticateUser(id, requestingUserPassword) || id !== requestingUserId) {
    return res.status(401).json({ status_code: 401, msg: 'Unauthorized' });
  }
  const records = activityRecords.filter(r => (r.userId || r.UserId) === id);
  res.json(records);
});

// GET /api/ActivityRecords/:id
app.get('/api/ActivityRecords/:id', (req, res) => {
  const { id } = req.params;
  const { requestingUserId, requestingUserPassword } = req.query;
  const record = activityRecords.find(r => r.id === parseInt(id));
  if (!record) return res.status(404).json({ status_code: 404, msg: 'Not Found' });
  const recordUserId = record.userId || record.UserId;
  if (!authenticateUser(requestingUserId, requestingUserPassword) || (requestingUserId !== recordUserId && !users.find(u => u.id === requestingUserId && u.isAdmin))) {
    return res.status(403).json({ status_code: 403, msg: 'Forbidden' });
  }
  res.json(record);
});

// POST /api/ActivityRecords
app.post('/api/ActivityRecords', (req, res) => {
  const { AdminId: adminId, AdminPassword: adminPassword, Record: bodyRecord, record: bodyRecordLower, userId, userPassword } = req.body;
  const record = bodyRecord || bodyRecordLower || req.body;
  if (!record || typeof record !== 'object') {
    return res.status(400).json({ status_code: 400, msg: 'Missing or invalid Record in request body' });
  }

  const targetUserId = record.UserId || record.userId || userId;
  if (!targetUserId) return res.status(400).json({ status_code: 400, msg: 'Missing UserId' });

  const activityType = record.ActivityType || record.activityType;
  if (!activityType) return res.status(400).json({ status_code: 400, msg: 'Missing ActivityType' });

  if (adminId && adminPassword) {
    if (!authenticateAdmin(adminId, adminPassword)) return res.status(401).json({ status_code: 401, msg: 'Unauthorized' });
  } else if (userId && userPassword) {
    if (!authenticateUser(userId, userPassword) || userId !== targetUserId) return res.status(401).json({ status_code: 401, msg: 'Unauthorized for self-add' });
  } else {
    console.log(`Self-add trusted for userId: ${targetUserId}`);
  }

  const newRecord = {
    id: nextRecordId++,
    userId: targetUserId,
    activityType: activityType,
    mood: record.Mood || record.mood,
    duration: record.Duration || record.duration,
    exercises: record.Exercises || record.exercises,
    heartRate: record.HeartRate || record.heartRate || 0,
    created_At: new Date().toISOString()
  };
  activityRecords.push(newRecord);
  saveRecords();
  res.status(201).json(newRecord);
});

// PUT /api/ActivityRecords/:id
app.put('/api/ActivityRecords/:id', (req, res) => {
  const recordId = parseInt(req.params.id);
  const { AdminId: adminId, AdminPassword: adminPassword, RecordId, UpdatedRecord } = req.body;

  if (!authenticateAdmin(adminId, adminPassword)) {
    return res.status(401).json({ status_code: 401, msg: 'Unauthorized' });
  }

  if (!UpdatedRecord || typeof UpdatedRecord !== 'object') {
    return res.status(400).json({ status_code: 400, msg: 'UpdatedRecord is required' });
  }

  if (RecordId !== recordId) {
    return res.status(400).json({ status_code: 400, msg: 'RecordId mismatch' });
  }

  const index = activityRecords.findIndex(r => r.id === recordId);
  if (index === -1) {
    return res.status(404).json({ status_code: 404, msg: 'Record not found' });
  }

  const oldRecord = activityRecords[index];
  const updated = {
    ...oldRecord,
    userId: UpdatedRecord.UserId || oldRecord.userId || oldRecord.UserId,
    activityType: UpdatedRecord.ActivityType || oldRecord.activityType,
    mood: UpdatedRecord.Mood ?? oldRecord.mood,
    duration: UpdatedRecord.Duration || oldRecord.duration,
    exercises: UpdatedRecord.Exercises || oldRecord.exercises,
    heartRate: UpdatedRecord.HeartRate !== undefined ? UpdatedRecord.HeartRate : oldRecord.heartRate
  };

  activityRecords[index] = updated;
  saveRecords();

  res.json({ message: 'Record updated', record: updated });
});

// DELETE /api/ActivityRecords/:id
app.delete('/api/ActivityRecords/:id', (req, res) => {
  const recordId = parseInt(req.params.id);
  const { AdminId: adminId, AdminPassword: adminPassword, userId, userPassword } = req.body;

  const record = activityRecords.find(r => r.id === recordId);
  if (!record) return res.status(404).json({ status_code: 404, msg: "Not Found" });

  const recordUserId = record.userId || record.UserId;

  if (adminId && adminPassword) {
    if (!authenticateAdmin(adminId, adminPassword)) return res.status(401).json({ status_code: 401, msg: 'Unauthorized' });
  } else if (userId && userPassword) {
    if (!authenticateUser(userId, userPassword) || userId !== recordUserId) return res.status(401).json({ status_code: 401, msg: 'Unauthorized for self-delete' });
  } else {
    return res.status(400).json({ status_code: 400, msg: 'Missing auth' });
  }

  const index = activityRecords.findIndex(r => r.id === recordId);
  activityRecords.splice(index, 1);
  saveRecords();
  res.json({ msg: 'Record deleted' });
});

// === 404 MUST BE LAST ===
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

app.listen(PORT, '0.0.0.0', () => {
  console.log(`Server running on http://localhost:${PORT}`);
  console.log(`ngrok: ngrok http ${PORT}`);
});